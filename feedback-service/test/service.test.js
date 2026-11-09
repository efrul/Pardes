// The feedback service in Miniflare (Cloudflare's runtime, here): receiving packages, the maintainer's list and
// downloads, everyone's list and the site, deleting a sender's packages, and the limits. `npm test`.
import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import { after, before, describe, test } from "node:test";
import { fileURLToPath } from "node:url";
import { convertV4MiniflareOptions, Miniflare } from "miniflare";
import { MAX_BYTES, MAX_PER_DAY, MAX_QUERY } from "../src/package.js";

const SRC = fileURLToPath(new URL("../src/", import.meta.url));
const MIGRATIONS = new URL("../migrations/", import.meta.url);
const TOKEN = "test-admin-token";
const A = "aaaaaaaa-1111-4111-8111-111111111111";
const B = "bbbbbbbb-2222-4222-8222-222222222222";

async function service(limit = 1000, reads = 1000) {
  // Miniflare 5 (wrangler's) still takes the options of 4, its documented ones, converted
  const mf = new Miniflare(
    convertV4MiniflareOptions({
      modulesRoot: SRC,
      modules: [
        ...["index.js", "package.js", "site.js"].map((name) => ({ type: "ESModule", path: `${SRC}${name}` })),
        // As wrangler.toml's rules upload them: text, never run, and the font as data
        ...["site/index.html", "site/app.js", "site/style.css"].map((name) => ({ type: "Text", path: `${SRC}${name}` })),
        { type: "Data", path: `${SRC}site/display.woff2` },
      ],
      compatibilityDate: "2026-10-01",
      d1Databases: { DB: "test" },
      bindings: { ADMIN_TOKEN: TOKEN },
      ratelimits: {
        LIMITER: { namespace_id: "1601", simple: { limit, period: 60 } },
        READ_LIMITER: { namespace_id: "1602", simple: { limit: reads, period: 60 } },
      },
    }),
  );
  try {
    const db = await mf.getD1Database("DB");
    // Every migration, in order, as wrangler applies them
    for (const name of (await readdir(MIGRATIONS)).filter((n) => n.endsWith(".sql")).sort()) {
      const sql = (await readFile(new URL(name, MIGRATIONS), "utf8")).replace(/--.*$/gm, "");
      for (const statement of sql.split(";").map((s) => s.trim()).filter(Boolean)) await db.prepare(statement).run();
    }
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
const everyones = (mf, query = "", headers = {}) =>
  mf.dispatchFetch(`http://feedback.test/v1/public/packages${query}`, { headers });
const site = (mf, path, init) => mf.dispatchFetch(`http://feedback.test${path}`, init);

describe("the feedback service", () => {
  let mf;
  before(async () => {
    mf = await service();
  });
  after(() => mf?.dispose());

  test("shows its site at its root, and nothing else without a route", async () => {
    const home = await site(mf, "/");
    assert.equal(home.status, 200);
    assert.match(home.headers.get("content-type"), /^text\/html/);
    assert.match(await home.text(), /מאגר החיפושים הפתוח/);
    assert.equal((await site(mf, "/v1/other")).status, 404);
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

  test("serves the site, with no data in it, and runs no script but its own", async () => {
    await post(mf, pkg(A, { searches: [{ query: "סוד שלא בדף", lastAt: 1793800000002 }] }));
    const page = await site(mf, "/");
    const csp = page.headers.get("content-security-policy");
    assert.match(csp, /script-src 'self';/);
    assert.match(csp, /default-src 'none'/);
    assert.match(csp, /font-src 'self'/);
    assert.match(csp, /connect-src 'self'/);
    assert.match(csp, /frame-ancestors 'none'/);
    assert.equal(page.headers.get("cache-control"), "no-store");
    const html = await page.text();
    assert.doesNotMatch(html, /סוד שלא בדף/);
    assert.doesNotMatch(html, /<script>/);
    const source = await readFile(new URL("../src/site/app.js", import.meta.url), "utf8");
    // Everyone's view reads the public route; only the maintainer, signed in, reads the packages as they came
    assert.match(source, /\/v1\/public\/packages/);
    // What is uploaded on the site is published: every package it posts carries the consent, and the page says so
    assert.match(source, /packagesIn\([^)]*\)\)\.map\(\(p\) => \(\{ \.\.\.p, publish: true \}\)\)/);
    assert.match(source, /מה שמעלים כאן מתפרסם לכולם/);
    // No sender on the page, not even its alias: no column of one, and none in a download
    assert.doesNotMatch(source, /title: "שולח"|"sender"\]|\.sender\)\]/);
    const script = await site(mf, "/site/app.js");
    assert.match(script.headers.get("content-type"), /^text\/javascript/);
    assert.equal(await script.text(), source);
    // Every text of a package reaches the page as text: the script builds no markup from strings
    assert.doesNotMatch(source, /innerHTML|outerHTML|insertAdjacentHTML|document\.write/);
    const style = await site(mf, "/site/style.css");
    assert.match(style.headers.get("content-type"), /^text\/css/);
    const font = await site(mf, "/site/display.woff2");
    assert.equal(font.headers.get("content-type"), "font/woff2");
    assert.deepEqual(Buffer.from(await font.arrayBuffer()), await readFile(new URL("../src/site/display.woff2", import.meta.url)));
    // The maintainer's old page leads to the site
    const old = await site(mf, "/admin", { redirect: "manual" });
    assert.equal(old.status, 301);
    assert.equal(old.headers.get("location"), "/");
    assert.equal((await site(mf, "/site/other")).status, 404);
    assert.equal((await site(mf, "/", { method: "POST" })).status, 404);
  });

  test("gives everyone the packages without a search's words, each sender an alias that does not give its id away", async () => {
    const words = "חיפוש אישי שלא יתפרסם";
    const sent = pkg(A, {
      searches: [{ query: words, extended: false, count: 3, lastAt: 1793800000003, scope: "/תיקייה פרטית" }],
      grades: [{ ...pkg(A).grades[0], query: words, search: `search:g:${words}`, heRef: "מקום פרטי", bookTitle: "ספר" }],
      note: "טקסט שהוסיף השולח",
    });
    const { id } = await (await post(mf, sent)).json();
    const res = await everyones(mf, `?after=${id - 1}`);
    assert.equal(res.status, 200);
    const raw = await res.text();
    assert.ok(!raw.includes(A), "a sender's id would let anyone delete what it sent");
    for (const secret of [words, "מקום פרטי", "/תיקייה פרטית", "טקסט שהוסיף השולח", "26579"]) {
      assert.ok(!raw.includes(secret), `everyone's list carries ${secret}`);
    }
    const listed = JSON.parse(raw);
    const row = listed.packages[0];
    assert.equal(row.id, id);
    assert.match(row.sender, /^[0-9a-f]{12}$/);
    assert.equal(row.package.sender, row.sender);
    assert.equal(row.body, undefined);
    // What counts a search and a grade comes through; their words are opaque ids
    const [search] = row.package.searches;
    assert.match(search.query, /^[0-9a-f]{12}$/);
    assert.equal(search.queryId, search.query);
    assert.deepEqual({ ...search, query: "", queryId: "" }, { query: "", queryId: "", extended: false, count: 3, lastAt: 1793800000003 });
    const [grade] = row.package.grades;
    assert.match(grade.search, /^[0-9a-f]{12}$/);
    assert.deepEqual({ grade: grade.grade, rank: grade.rank, model: grade.model, gradedAt: grade.gradedAt },
      { grade: 3, rank: 2, model: "student@695cbe2/pardes-clean-1", gradedAt: 1793800000002 });
    assert.equal(row.package.daf[0].marks[0].t, "שורה");
    // The same words in the same scope, the same id, whoever sent them; elsewhere, another
    const again = await (await post(mf, pkg(B, { searches: [{ query: words, scope: "/תיקייה פרטית", lastAt: 1793800000004 },
      { query: words, lastAt: 1793800000005 }] }))).json();
    const later = await (await everyones(mf, `?after=${again.id - 1}`)).json();
    assert.equal(later.packages[0].package.searches[0].query, search.query);
    assert.notEqual(later.packages[0].package.searches[1].query, search.query);
    assert.notEqual(later.packages[0].sender, row.sender);
    // The maintainer, with the token, reads the words
    const theirs = await (await admin(mf, `/v1/packages/${id}`)).json();
    assert.equal(theirs.searches[0].query, words);
    // No token needed, and at most 50 a request
    for (let i = 0; i < 51; i++) await post(mf, pkg(B));
    const capped = await (await everyones(mf, "?limit=1000")).json();
    assert.equal(capped.packages.length, 50);
    assert.equal(capped.more, true);
    assert.ok(capped.packages.every((p) => p.package && !p.package.sender.includes("-")));
  });

  test("shows everyone a package's words only when its sender agreed to publish it", async () => {
    const words = "חיפוש שהשולח הסכים לפרסם";
    const searches = [{ query: words, extended: false, count: 2, lastAt: 1793800000010, scope: "/תלמוד בבלי", ref: "חיפוש חכם",
      timeUnknown: true, note: "שדה שהשולח הוסיף" }];
    const grades = [{ ...pkg(A).grades[0], query: words, search: `search:g:${words}`, heRef: "בבא בתרא כט, א", bookTitle: "בבא בתרא",
      docId: "17" }];
    const open = await (await post(mf, pkg(A, { publish: true, searches, grades }))).json();
    const closed = await (await post(mf, pkg(A, { searches, grades }))).json();
    const listed = await (await everyones(mf, `?after=${open.id - 1}`)).json();
    const [pub, priv] = listed.packages;
    assert.equal(pub.id, open.id);
    assert.equal(pub.public, true);
    assert.equal(priv.public, false);
    // The published one: its words, scope, options and place, the sender's alias, and nothing else the sender added
    assert.equal(pub.package.searches[0].query, words);
    assert.equal(pub.package.searches[0].scope, "/תלמוד בבלי");
    assert.equal(pub.package.searches[0].ref, "חיפוש חכם");
    assert.equal(pub.package.searches[0].timeUnknown, true);
    assert.equal(pub.package.searches[0].note, undefined);
    assert.deepEqual([pub.package.grades[0].query, pub.package.grades[0].heRef, pub.package.grades[0].bookTitle, pub.package.grades[0].docId,
      pub.package.grades[0].lineId], [words, "בבא בתרא כט, א", "בבא בתרא", "17", 26579]);
    assert.equal(pub.package.sender, pub.sender);
    assert.match(pub.sender, /^[0-9a-f]{12}$/);
    // The other, from the same sender: as before, opaque, with the same ids the published rows carry
    const privText = JSON.stringify(priv);
    assert.ok(!privText.includes(words) && !privText.includes("בבא בתרא כט"), "a package not published shows no words");
    assert.match(priv.package.searches[0].query, /^[0-9a-f]{12}$/);
    assert.equal(priv.package.searches[0].queryId, pub.package.searches[0].queryId);
    assert.equal(priv.package.grades[0].resultId, pub.package.grades[0].resultId);
    assert.ok(!JSON.stringify(listed).includes(A), "no sender's id, published or not");
    // The maintainer sees which is which
    const theirs = await (await admin(mf, `/v1/packages?after=${open.id - 1}`)).json();
    assert.deepEqual(theirs.packages.slice(0, 2).map((p) => p.public), [true, false]);
    assert.equal(closed.id, open.id + 1);
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
    assert.deepEqual(await refused(pkg(A, { publish: "yes" })), [400, "publish is not true or false"]);
    assert.equal((await post(mf, pkg(A, { publish: false }))).status, 201);
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

  test("deletes what a sender sent, and tells the maintainer, and everyone by its alias", async () => {
    const before = (await (await admin(mf, "/v1/packages")).json()).packages;
    const res = await forget(mf, A);
    assert.equal(res.status, 200);
    assert.equal((await res.json()).deleted, before.filter((p) => p.sender === A).length);
    const listed = await (await admin(mf, "/v1/packages")).json();
    assert.ok(listed.packages.every((p) => p.sender !== A));
    assert.ok(listed.packages.some((p) => p.sender === B));
    assert.deepEqual(listed.deletions.map((d) => d.sender), [A]);
    const shown = await everyones(mf);
    const text = await shown.text();
    assert.ok(!text.includes(A));
    assert.match(JSON.parse(text).deletions[0].sender, /^[0-9a-f]{12}$/);
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
  const mf = await service(2, 3);
  try {
    const statuses = [];
    for (let i = 0; i < 3; i++) statuses.push((await post(mf, pkg(A), { "cf-connecting-ip": "203.0.113.7" })).status);
    assert.deepEqual(statuses, [201, 201, 429]);
    assert.equal((await post(mf, pkg(A), { "cf-connecting-ip": "203.0.113.8" })).status, 201);
    // The site's reads have their own, larger limit
    const reads = [];
    for (let i = 0; i < 4; i++) reads.push((await everyones(mf, "", { "cf-connecting-ip": "203.0.113.7" })).status);
    assert.deepEqual(reads, [200, 200, 200, 429]);
  } finally {
    await mf.dispose();
  }
});
