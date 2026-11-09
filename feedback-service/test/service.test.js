// The feedback service in Miniflare (Cloudflare's runtime, here): receiving packages, the maintainer's list and
// downloads, deleting a sender's packages, and the limits. `npm test`.
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, before, describe, test } from "node:test";
import { fileURLToPath } from "node:url";
import { convertV4MiniflareOptions, Miniflare } from "miniflare";
import { MAX_BYTES, MAX_PER_DAY, MAX_QUERY } from "../src/package.js";

const SRC = fileURLToPath(new URL("../src/", import.meta.url));
const MIGRATION = new URL("../migrations/0001_packages.sql", import.meta.url);
const TOKEN = "test-admin-token";
const A = "aaaaaaaa-1111-4111-8111-111111111111";
const B = "bbbbbbbb-2222-4222-8222-222222222222";

async function service(limit = 1000) {
  // Miniflare 5 (wrangler's) still takes the options of 4, its documented ones, converted
  const mf = new Miniflare(
    convertV4MiniflareOptions({
      modulesRoot: SRC,
      modules: [
        ...["index.js", "package.js", "admin.js"].map((name) => ({ type: "ESModule", path: `${SRC}${name}` })),
        // As wrangler.toml's rules upload them: text, never run
        ...["admin/index.html", "admin/app.js", "admin/style.css"].map((name) => ({ type: "Text", path: `${SRC}${name}` })),
      ],
      compatibilityDate: "2026-10-01",
      d1Databases: { DB: "test" },
      bindings: { ADMIN_TOKEN: TOKEN },
      ratelimits: { LIMITER: { namespace_id: "1601", simple: { limit, period: 60 } } },
    }),
  );
  try {
    const db = await mf.getD1Database("DB");
    const sql = (await readFile(MIGRATION, "utf8")).replace(/--.*$/gm, "");
    for (const statement of sql.split(";").map((s) => s.trim()).filter(Boolean)) await db.prepare(statement).run();
    return mf;
  } catch (e) {
    // Its runtime would keep the tests from ending
    await mf.dispose();
    throw e;
  }
}

function pkg(sender, extra = {}) {
  return {
    format: 1,
    app: "Pardes",
    appVersion: "1.0.7",
    sender,
    sentAt: "2026-11-05T12:00:00Z",
    library: "v3",
    grades: [
      { query: "חזקת הבתים", search: "search:g:חזקת הבתים", lineId: 26579, bookId: 72, grade: 3, rank: 2, extended: false,
        model: "student@695cbe2/pardes-clean-1", gradedAt: 1793800000002 },
    ],
    searches: [{ query: "חזקת הבתים", extended: false, count: 2, lastAt: 1793800000000 }],
    daf: [{ format: 1, tractate: "Berakhot", tractateHe: "ברכות", library: "v3",
      marks: [{ amud: "2a", c: "g", i: 1, t: "שורה", ok: true, cut: 0, at: "2026-11-01T09:00:00.123456789Z" }] }],
    ...extra,
  };
}

const post = (mf, body, headers = {}) =>
  mf.dispatchFetch("http://feedback.test/v1/packages", {
    method: "POST",
    headers: { "content-type": "application/json", ...headers },
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
const admin = (mf, path, token = TOKEN) =>
  mf.dispatchFetch(`http://feedback.test${path}`, { headers: { authorization: `Bearer ${token}` } });
const forget = (mf, sender) => mf.dispatchFetch(`http://feedback.test/v1/senders/${sender}`, { method: "DELETE" });

describe("the feedback service", () => {
  let mf;
  before(async () => {
    mf = await service();
  });
  after(() => mf?.dispose());

  test("says what it is, and nothing else without a route", async () => {
    const home = await mf.dispatchFetch("http://feedback.test/");
    assert.equal(home.status, 200);
    assert.match(await home.text(), /Pardes feedback service/);
    assert.equal((await mf.dispatchFetch("http://feedback.test/v1/other")).status, 404);
  });

  test("keeps a package as it came, and lists it to the maintainer with its counts", async () => {
    const sent = JSON.stringify(pkg(A));
    const res = await post(mf, sent);
    assert.equal(res.status, 201);
    const { id } = await res.json();
    assert.ok(id > 0);

    const listed = await (await admin(mf, "/v1/packages")).json();
    const row = listed.packages.find((p) => p.id === id);
    assert.deepEqual(
      { sender: row.sender, appVersion: row.appVersion, library: row.library, grades: row.grades, searches: row.searches,
        dafMarks: row.dafMarks, bytes: row.bytes },
      { sender: A, appVersion: "1.0.7", library: "v3", grades: 1, searches: 1, dafMarks: 1,
        bytes: Buffer.byteLength(sent) },
    );
    assert.equal(listed.more, false);
    const body = await admin(mf, `/v1/packages/${id}`);
    assert.equal(body.status, 200);
    assert.equal(await body.text(), sent);
  });

  test("gives the maintainer the packages with their bodies, a few at a time", async () => {
    const sent = pkg(A, { searches: [{ query: "<img src=x onerror=alert(1)>", lastAt: 1793800000001 }] });
    const { id } = await (await post(mf, sent)).json();
    assert.equal((await admin(mf, "/v1/packages?bodies=1", "wrong")).status, 401);
    const listed = await (await admin(mf, `/v1/packages?bodies=1&after=${id - 1}`)).json();
    assert.deepEqual(listed.packages[0].package, sent);
    assert.equal(listed.packages[0].id, id);
    assert.equal(listed.packages[0].body, undefined);
    // Without bodies=1 the list holds none
    const plain = await (await admin(mf, `/v1/packages?after=${id - 1}`)).json();
    assert.equal(plain.packages[0].package, undefined);
    for (let i = 0; i < 51; i++) await post(mf, pkg(B));
    const capped = await (await admin(mf, "/v1/packages?bodies=1&limit=1000")).json();
    assert.equal(capped.packages.length, 50);
    assert.equal(capped.more, true);
  });

  test("serves the maintainer's page, with no data in it, and runs no script but its own", async () => {
    await post(mf, pkg(A, { searches: [{ query: "סוד שלא בדף", lastAt: 1793800000002 }] }));
    const page = await mf.dispatchFetch("http://feedback.test/admin");
    assert.equal(page.status, 200);
    assert.match(page.headers.get("content-type"), /^text\/html/);
    const csp = page.headers.get("content-security-policy");
    assert.match(csp, /script-src 'self';/);
    assert.match(csp, /default-src 'none'/);
    assert.match(csp, /frame-ancestors 'none'/);
    assert.equal(page.headers.get("cache-control"), "no-store");
    const html = await page.text();
    assert.match(html, /קוד המנהל/);
    assert.doesNotMatch(html, /סוד שלא בדף/);
    assert.doesNotMatch(html, /<script>/);
    const script = await mf.dispatchFetch("http://feedback.test/admin/app.js");
    assert.match(script.headers.get("content-type"), /^text\/javascript/);
    assert.equal(await script.text(), await readFile(new URL("../src/admin/app.js", import.meta.url), "utf8"));
    // Every text of a package reaches the page as text: the script builds no markup from strings
    assert.doesNotMatch(await readFile(new URL("../src/admin/app.js", import.meta.url), "utf8"), /innerHTML|outerHTML|insertAdjacentHTML|document\.write/);
    const style = await mf.dispatchFetch("http://feedback.test/admin/style.css");
    assert.match(style.headers.get("content-type"), /^text\/css/);
    assert.equal((await mf.dispatchFetch("http://feedback.test/admin/other")).status, 404);
    assert.equal((await mf.dispatchFetch("http://feedback.test/admin", { method: "POST" })).status, 404);
  });

  test("pages the list after an id", async () => {
    for (let i = 0; i < 3; i++) assert.equal((await post(mf, pkg(B))).status, 201);
    const first = await (await admin(mf, "/v1/packages?limit=2")).json();
    assert.equal(first.packages.length, 2);
    assert.equal(first.more, true);
    const rest = await (await admin(mf, `/v1/packages?after=${first.packages.at(-1).id}`)).json();
    assert.ok(rest.packages.every((p) => p.id > first.packages.at(-1).id));
    assert.equal(rest.more, false);
  });

  test("refuses what is not a package of Pardes", async () => {
    const refused = async (body) => {
      const res = await post(mf, body);
      return [res.status, (await res.json()).error];
    };
    assert.deepEqual(await refused("{"), [400, "not JSON"]);
    assert.deepEqual(await refused([]), [400, "not a package"]);
    assert.deepEqual(await refused(pkg(A, { app: "Zayit" })), [400, "not a package of Pardes"]);
    assert.deepEqual(await refused(pkg(A, { format: 2 })), [400, "unknown format"]);
    assert.deepEqual(await refused(pkg("not-a-uuid")), [400, "no sender id"]);
    assert.deepEqual(await refused(pkg(A, { grades: [], searches: [], daf: [] })), [400, "nothing in the package"]);
    assert.deepEqual(await refused(pkg(A, { grades: [{ query: "x", grade: 5 }] })), [400, "grade 1 is not one (search)"]);
    // A passage pasted in to find its source is a search too, up to MAX_QUERY
    const passage = "א".repeat(1500);
    assert.equal((await post(mf, pkg(A, { searches: [{ query: passage, lastAt: 1793800000000 }] }))).status, 201);
    const tooLong = { query: "א".repeat(MAX_QUERY + 1), lastAt: 1793800000000 };
    assert.deepEqual(await refused(pkg(A, { searches: [pkg(A).searches[0], tooLong] })),
      [400, `search 2 is not one (query: ${MAX_QUERY + 1} characters, at most ${MAX_QUERY})`]);
    const badMark = { ...pkg(A).daf[0], marks: [{ amud: "2a", c: "g", i: -1, t: "" }] };
    assert.deepEqual(await refused(pkg(A, { daf: [badMark] })), [400, "daf review 1 is not one (mark 1: i)"]);
    assert.deepEqual(await refused(pkg(A, { searches: "all" })), [400, "searches is not a list"]);
    const big = pkg(A, { searches: [{ query: "א".repeat(900), lastAt: 1 }] });
    big.searches = Array.from({ length: Math.ceil(MAX_BYTES / 1800) + 1 }, () => big.searches[0]);
    assert.equal((await post(mf, big)).status, 413);
  });

  test("gives the maintainer's routes only with the token", async () => {
    assert.equal((await mf.dispatchFetch("http://feedback.test/v1/packages")).status, 401);
    assert.equal((await admin(mf, "/v1/packages", "wrong")).status, 401);
    assert.equal((await admin(mf, "/v1/packages/1", "wrong")).status, 401);
    assert.equal((await admin(mf, "/v1/packages/999999")).status, 404);
  });

  test("deletes what a sender sent, and tells the maintainer", async () => {
    const before = (await (await admin(mf, "/v1/packages")).json()).packages;
    const res = await forget(mf, A);
    assert.equal(res.status, 200);
    assert.equal((await res.json()).deleted, before.filter((p) => p.sender === A).length);
    const listed = await (await admin(mf, "/v1/packages")).json();
    assert.ok(listed.packages.every((p) => p.sender !== A));
    assert.ok(listed.packages.some((p) => p.sender === B));
    assert.deepEqual(listed.deletions.map((d) => d.sender), [A]);
    assert.equal((await forget(mf, "../packages")).status, 404);
    assert.equal((await forget(mf, "not-a-uuid")).status, 400);
  });

  test(`takes at most ${MAX_PER_DAY} packages a day from a sender`, async () => {
    const C = "cccccccc-3333-4333-8333-333333333333";
    for (let i = 0; i < MAX_PER_DAY; i++) assert.equal((await post(mf, pkg(C))).status, 201);
    const res = await post(mf, pkg(C));
    assert.equal(res.status, 429);
    assert.equal((await post(mf, pkg(B))).status, 201);
  });
});

test("limits the requests of one address", async () => {
  const mf = await service(2);
  try {
    const statuses = [];
    for (let i = 0; i < 3; i++) statuses.push((await post(mf, pkg(A), { "cf-connecting-ip": "203.0.113.7" })).status);
    assert.deepEqual(statuses, [201, 201, 429]);
    assert.equal((await post(mf, pkg(A), { "cf-connecting-ip": "203.0.113.8" })).status, 201);
  } finally {
    await mf.dispose();
  }
});
