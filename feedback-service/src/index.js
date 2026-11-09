/**
 * Pardes's feedback service, a Cloudflare Worker with a D1 database (README.md). It receives the packages users send
 * from Pardes (Settings › ניהול נתונים › שליחת משוב, FeedbackPackage.kt), keeps each as it came, and gives them to the
 * maintainer (pardes-semantic/feedback/fetch.py), and shows everyone on its site what they add up to: users sent
 * them to improve Pardes, not to have them published, so a search's or a grade's words are the maintainer's alone. Of a request it keeps nothing but the package: no IP address, user agent or other header is
 * stored or logged. The address only keys the rate limits, in Cloudflare's memory.
 *
 * POST   /v1/packages          a package (JSON, at most MAX_BYTES) -> 201 {"id"}; the app's, and the site's uploads
 * DELETE /v1/senders/:id       everything the sender sent -> {"deleted"}
 * GET    /v1/public/packages   everyone's: ?after=<id>&limit=<n>, at most BODIES_LIMIT a request -> {"packages":
 *                              [{id, sender, receivedAt, ..., package}], "more", "deletions": [{sender, deletedAt}]},
 *                              each sender (in the package too) its alias, never its id, which lets one delete; and
 *                              each search and graded result an opaque id in place of its words (forEveryone)
 * GET    /v1/packages          the maintainer's (Authorization: Bearer ADMIN_TOKEN): ?after=<id>&limit=<n>
 *                              -> {"packages": [{id, sender, receivedAt, ...}], "more", "deletions": [{sender, deletedAt}]};
 *                              with &bodies=1 each package as it came too ("package"), at most BODIES_LIMIT a request
 * GET    /v1/packages/:id      the maintainer's: the package as it came
 * GET    /                     the site (site.js), which reads the public route and uploads packages; /admin leads to it
 */

import { siteFile } from "./site.js";
import { check, MAX_BYTES, MAX_PER_DAY, SENDER } from "./package.js";

const DAY = 86_400_000;
/** Packages a request with their bodies: each is at most MAX_BYTES, most a few KB. */
const BODIES_LIMIT = 50;
/** A sender's alias, and a search's or graded result's opaque id: hex digits of a digest (alias, opaqueIds). */
const ALIAS_BYTES = 6;
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
  if (method === "GET" && (pathname === "/" || pathname.startsWith("/site/") || pathname.startsWith("/admin"))) {
    return siteFile(pathname) ?? error(404, "not found");
  }
  if (pathname === "/v1/packages" && method === "POST") return (await limited(request, env)) ?? receive(request, env);
  if (pathname === "/v1/public/packages" && method === "GET") {
    return (await limited(request, env, env.READ_LIMITER)) ?? everyones(url, env);
  }
  if (pathname === "/v1/packages" && method === "GET") return (await unauthorized(request, env)) ?? list(url, env);
  const one = pathname.match(/^\/v1\/packages\/(\d{1,15})$/);
  if (one && method === "GET") return (await unauthorized(request, env)) ?? body(Number(one[1]), env);
  const sender = pathname.match(/^\/v1\/senders\/([^/]+)$/);
  if (sender && method === "DELETE") return (await limited(request, env)) ?? forget(sender[1], env);
  return error(404, "not found");
}

/**
 * Too many requests from one address in a minute: by [limiter], the LIMITER binding unless the route has its own; it
 * keeps the address only in memory.
 */
async function limited(request, env, limiter = env.LIMITER) {
  if (!limiter) return null;
  const { success } = await limiter.limit({ key: request.headers.get("cf-connecting-ip") ?? "unknown" });
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
  const bodies = url.searchParams.get("bodies") === "1";
  const { rows, more, deletions } = await page(url, env, bodies ? BODIES_LIMIT : 1000, 500, bodies);
  const packages = rows.map(({ body, ...row }) => (bodies ? { ...row, package: JSON.parse(body) } : row));
  return reply(200, { packages, more, deletions });
}

/**
 * Everyone's list, for the site: the packages, each sender, in the package too, its alias, and of the searches and
 * grades only what counts them (forEveryone). A sender's id is never shown, since with it anyone could delete what
 * the sender sent; a search's words are never shown, since users sent them to improve Pardes, not to publish them.
 */
async function everyones(url, env) {
  if (!env.ADMIN_TOKEN) return error(503, "no admin token set");
  const { rows, more, deletions } = await page(url, env, BODIES_LIMIT, BODIES_LIMIT, true);
  const idOf = await opaqueIds(env.ADMIN_TOKEN);
  const aliases = new Map();
  const of = async (sender) => {
    if (!aliases.has(sender)) aliases.set(sender, await alias(sender));
    return aliases.get(sender);
  };
  const packages = [];
  for (const { body, sender, ...row } of rows) {
    const name = await of(sender);
    packages.push({ ...row, sender: name, package: await forEveryone(JSON.parse(body), name, idOf) });
  }
  const deleted = [];
  for (const d of deletions) deleted.push({ sender: await of(d.sender), deletedAt: d.deletedAt });
  return reply(200, { packages, more, deletions: deleted });
}

/** The packages after the request's ?after=, at most its ?limit= ([fallback] without one) and [most]; and the deletions. */
async function page(url, env, most, fallback, bodies) {
  const after = Math.max(0, Number.parseInt(url.searchParams.get("after") ?? "0", 10) || 0);
  const asked = Number.parseInt(url.searchParams.get("limit") ?? String(fallback), 10) || fallback;
  const limit = Math.min(most, Math.max(1, asked));
  const { results } = await env.DB.prepare(
    "SELECT id, sender, received_at AS receivedAt, app_version AS appVersion, library, grades, searches, " +
      `daf_marks AS dafMarks, bytes${bodies ? ", body" : ""} FROM packages WHERE id > ? ORDER BY id LIMIT ?`,
  )
    .bind(after, limit + 1)
    .all();
  const deletions = await env.DB.prepare("SELECT sender, deleted_at AS deletedAt FROM deletions ORDER BY deleted_at").all();
  return { rows: results.slice(0, limit), more: results.length > limit, deletions: deletions.results };
}

/**
 * What everyone may see of package [p]: its versions and dates; of each search, only an opaque id of its words and
 * scope, how often and when; of each grade, an opaque id of its search and result, the grade, its rank and model, and when; and
 * the daf reviews, words of the Talmud's lines, as they came. Nothing else a sender added comes through.
 */
async function forEveryone(p, sender, idOf) {
  const searches = await Promise.all((p.searches ?? []).map(async (s) => ({
    // The words and where they looked, as one id: the site counts a search in each scope apart, as the maintainer's does
    query: await idOf(`query\u0000${s.query}\u0000${s.categoryId}\u0000${s.bookId}\u0000${s.tocId}\u0000${s.scope}`),
    source: s.source,
    extended: s.extended,
    count: s.count,
    lastAt: s.lastAt,
  })));
  const grades = await Promise.all((p.grades ?? []).map(async (g) => ({
    search: await idOf(`result\u0000${g.search}\u0000${g.lineId}\u0000${g.heRef ?? ""}`),
    grade: g.grade,
    rank: g.rank,
    model: g.model,
    source: g.source,
    extended: g.extended,
    gradedAt: g.gradedAt,
  })));
  const daf = (p.daf ?? []).map((r) => ({
    format: r.format,
    tractate: r.tractate,
    tractateHe: r.tractateHe,
    library: r.library,
    marks: r.marks.map((m) => ({ amud: m.amud, c: m.c, i: m.i, t: m.t, ok: m.ok, cut: m.cut, at: m.at })),
  }));
  return {
    format: p.format,
    app: p.app,
    appVersion: p.appVersion,
    library: p.library,
    source: p.source,
    sentAt: p.sentAt,
    sender,
    grades,
    searches,
    daf,
  };
}

/**
 * A function from a text to its opaque id: the first ALIAS_BYTES of its HMAC-SHA-256 under [secret], in hex. The same
 * text has the same id, so the site can count each search once; without the secret no one can tell which words an id
 * is of, even by trying likely searches. A request's texts are digested once each.
 */
async function opaqueIds(secret) {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey("raw", encoder.encode(`pardes-feedback-ids:${secret}`),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const ids = new Map();
  return (text) => {
    if (!ids.has(text)) {
      ids.set(text, crypto.subtle.sign("HMAC", key, encoder.encode(text)).then((mac) =>
        [...new Uint8Array(mac, 0, ALIAS_BYTES)].map((b) => b.toString(16).padStart(2, "0")).join("")));
    }
    return ids.get(text);
  };
}

/** [sender]'s alias: the first ALIAS_BYTES of a digest of its id, in hex. The same sender has the same alias. */
async function alias(sender) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`pardes-feedback:${sender}`));
  return [...new Uint8Array(digest, 0, ALIAS_BYTES)].map((b) => b.toString(16).padStart(2, "0")).join("");
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
