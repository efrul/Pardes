/**
 * Pardes's feedback service, a Cloudflare Worker with a D1 database (README.md). It receives the packages users send
 * from Pardes (Settings › ניהול נתונים › שליחת משוב, FeedbackPackage.kt), keeps each as it came, and gives them to the
 * maintainer (semantic-model/feedback/fetch.py). Of a request it keeps nothing but the package: no IP address, user
 * agent or other header is stored or logged. The address only keys the rate limit, in Cloudflare's memory.
 *
 * POST   /v1/packages        a package (JSON, at most MAX_BYTES) -> 201 {"id"}
 * DELETE /v1/senders/:id     everything the sender sent -> {"deleted"}
 * GET    /v1/packages        the maintainer's (Authorization: Bearer ADMIN_TOKEN): ?after=<id>&limit=<n>
 *                            -> {"packages": [{id, sender, receivedAt, ...}], "more", "deletions": [{sender, deletedAt}]}
 * GET    /v1/packages/:id    the maintainer's: the package as it came
 */

import { check, MAX_BYTES, MAX_PER_DAY, SENDER } from "./package.js";

const DAY = 86_400_000;
const NO_STORE = { "cache-control": "no-store" };

export default {
  async fetch(request, env) {
    try {
      return await route(request, env, new URL(request.url));
    } catch {
      // Nothing of the request in a log
      return error(500, "server error");
    }
  },
};

async function route(request, env, url) {
  const { pathname } = url;
  const method = request.method;
  if (pathname === "/" && method === "GET") return text("Pardes feedback service\n");
  if (pathname === "/v1/packages" && method === "POST") return (await limited(request, env)) ?? receive(request, env);
  if (pathname === "/v1/packages" && method === "GET") return (await unauthorized(request, env)) ?? list(url, env);
  const one = pathname.match(/^\/v1\/packages\/(\d{1,15})$/);
  if (one && method === "GET") return (await unauthorized(request, env)) ?? body(Number(one[1]), env);
  const sender = pathname.match(/^\/v1\/senders\/([^/]+)$/);
  if (sender && method === "DELETE") return (await limited(request, env)) ?? forget(sender[1], env);
  return error(404, "not found");
}

/** Too many requests from one address in a minute (the LIMITER binding, which keeps the address only in memory). */
async function limited(request, env) {
  if (!env.LIMITER) return null;
  const { success } = await env.LIMITER.limit({ key: request.headers.get("cf-connecting-ip") ?? "unknown" });
  return success ? null : error(429, "too many requests, try again later");
}

/** The maintainer's requests carry the ADMIN_TOKEN secret. */
async function unauthorized(request, env) {
  if (!env.ADMIN_TOKEN) return error(503, "no admin token set");
  const given = request.headers.get("authorization") ?? "";
  return (await same(given, `Bearer ${env.ADMIN_TOKEN}`)) ? null : error(401, "unauthorized");
}

/** Whether [a] and [b] are the same, in a time that does not tell how much of them is (by their digests). */
async function same(a, b) {
  const encoder = new TextEncoder();
  const [x, y] = await Promise.all([a, b].map((s) => crypto.subtle.digest("SHA-256", encoder.encode(s))));
  return crypto.subtle.timingSafeEqual(x, y);
}

async function receive(request, env) {
  if (Number(request.headers.get("content-length") ?? 0) > MAX_BYTES) return error(413, "package too large");
  const bytes = await request.arrayBuffer();
  if (bytes.byteLength > MAX_BYTES) return error(413, "package too large");
  let json;
  let pkg;
  try {
    json = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
    pkg = JSON.parse(json);
  } catch {
    return error(400, "not JSON");
  }
  const problem = check(pkg);
  if (problem) return error(400, problem);
  const now = Date.now();
  const sent = await env.DB.prepare("SELECT COUNT(*) AS n FROM packages WHERE sender = ? AND received_at > ?")
    .bind(pkg.sender, now - DAY)
    .first();
  if (sent.n >= MAX_PER_DAY) return error(429, "too many packages today");
  const marks = pkg.daf.reduce((n, review) => n + review.marks.length, 0);
  const row = await env.DB.prepare(
    "INSERT INTO packages (sender, received_at, app_version, library, grades, searches, daf_marks, bytes, body) " +
      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
  )
    .bind(pkg.sender, now, pkg.appVersion ?? null, pkg.library ?? null, pkg.grades.length, pkg.searches.length, marks,
      bytes.byteLength, json)
    .first();
  return reply(201, { id: row.id });
}

async function list(url, env) {
  const after = Math.max(0, Number.parseInt(url.searchParams.get("after") ?? "0", 10) || 0);
  const limit = Math.min(1000, Math.max(1, Number.parseInt(url.searchParams.get("limit") ?? "500", 10) || 500));
  const { results } = await env.DB.prepare(
    "SELECT id, sender, received_at AS receivedAt, app_version AS appVersion, library, grades, searches, " +
      "daf_marks AS dafMarks, bytes FROM packages WHERE id > ? ORDER BY id LIMIT ?",
  )
    .bind(after, limit + 1)
    .all();
  const deletions = await env.DB.prepare("SELECT sender, deleted_at AS deletedAt FROM deletions ORDER BY deleted_at").all();
  return reply(200, { packages: results.slice(0, limit), more: results.length > limit, deletions: deletions.results });
}

async function body(id, env) {
  const row = await env.DB.prepare("SELECT body FROM packages WHERE id = ?").bind(id).first();
  if (!row) return error(404, "no such package");
  return new Response(row.body, { headers: { "content-type": "application/json; charset=utf-8", ...NO_STORE } });
}

/** Deletes what [sender] sent, and says so to the maintainer's next fetch, which deletes the copies it made. */
async function forget(sender, env) {
  if (!SENDER.test(sender)) return error(400, "no sender id");
  const { meta } = await env.DB.prepare("DELETE FROM packages WHERE sender = ?").bind(sender).run();
  if (meta.changes > 0) {
    await env.DB.prepare(
      "INSERT INTO deletions (sender, deleted_at) VALUES (?, ?) ON CONFLICT (sender) DO UPDATE SET deleted_at = excluded.deleted_at",
    )
      .bind(sender, Date.now())
      .run();
  }
  return reply(200, { deleted: meta.changes });
}

function reply(status, value) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...NO_STORE },
  });
}

function error(status, message) {
  return reply(status, { error: message });
}

function text(value) {
  return new Response(value, { headers: { "content-type": "text/plain; charset=utf-8", ...NO_STORE } });
}
