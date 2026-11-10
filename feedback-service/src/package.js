/**
 * What the feedback service takes: a package of Pardes in format 1 (FeedbackPackage.kt), and its limits. Apart from
 * index.js, whose every export the Workers runtime would take for a handler.
 */

/** A package's JSON at most; the app sends a bigger one in parts (FeedbackPackage.parts). */
export const MAX_BYTES = 1_000_000;
/** Packages a sender may send in a day. */
export const MAX_PER_DAY = 200;
/** The sender's id, a random UUID (AppSettings.getFeedbackSender). */
export const SENDER = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
/** A search's query at most, a grade's too: a passage pasted in to find its source is a search as well. */
export const MAX_QUERY = 20_000;
/** The grades' scale: 3 exactly what was looked for, 2 relevant, 0 not relevant. */
const GRADES = new Set([0, 2, 3]);

/** Why [p] is not a package of Pardes in format 1, or null; its lists, when missing, are empty. */
export function check(p) {
  if (typeof p !== "object" || p === null || Array.isArray(p)) return "not a package";
  if (p.format !== 1) return "unknown format";
  if (p.app !== "Pardes") return "not a package of Pardes";
  if (typeof p.sender !== "string" || !SENDER.test(p.sender)) return "no sender id";
  if (!filled(p.sentAt, 64)) return "no sentAt";
  if (!optional(p.appVersion, 64) || !optional(p.library, 64)) return "a version is not one";
  // The sender's consent to show the package's words to everyone (index.js, published)
  if (!maybeBoolean(p.publish)) return "publish is not true or false";
  for (const kind of ["grades", "searches", "daf"]) {
    p[kind] ??= [];
    if (!Array.isArray(p[kind])) return `${kind} is not a list`;
  }
  if (p.grades.length + p.searches.length + p.daf.length === 0) return "nothing in the package";
  // Which one and what in it, for the app to show and its maintainer to fix
  for (const [kind, name, why] of [["grades", "grade", whyGrade], ["searches", "search", whySearch],
    ["daf", "daf review", whyReview]]) {
    for (let i = 0; i < p[kind].length; i++) {
      const reason = why(p[kind][i]);
      if (reason) return `${name} ${i + 1} is not one (${reason})`;
    }
  }
  return null;
}

const object = (o) => typeof o === "object" && o !== null && !Array.isArray(o);
const filled = (s, max) => typeof s === "string" && s.length > 0 && s.length <= max;
const optional = (s, max) => s === undefined || s === null || (typeof s === "string" && s.length <= max);
const count = (n) => Number.isSafeInteger(n) && n >= 0;
const maybeId = (n) => n === undefined || n === null || Number.isSafeInteger(n);
const maybeCount = (n) => n === undefined || n === null || count(n);
const maybeBoolean = (b) => b === undefined || b === null || typeof b === "boolean";

/** The first of [fields] whose check [o] fails, said with the length of a text too long; null when none. */
function firstBad(o, fields) {
  if (!object(o)) return "not an object";
  for (const [name, ok, max] of fields) {
    const v = o[name];
    if (ok(v)) continue;
    return typeof v === "string" && max !== undefined && v.length > max ? `${name}: ${v.length} characters, at most ${max}` : name;
  }
  return null;
}

const text = (max) => (s) => filled(s, max);
const maybeText = (max) => (s) => optional(s, max);

function whyGrade(g) {
  return firstBad(g, [["query", text(MAX_QUERY), MAX_QUERY], ["search", text(MAX_QUERY + 1000), MAX_QUERY + 1000],
    ["lineId", count], ["bookId", count], ["grade", (n) => GRADES.has(n)], ["rank", count], ["gradedAt", count],
    ["categoryId", maybeId], ["scopeBookId", maybeId], ["tocId", maybeId], ["extended", maybeBoolean],
    ["model", maybeText(200), 200]]);
}

function whySearch(s) {
  return firstBad(s, [["query", text(MAX_QUERY), MAX_QUERY], ["lastAt", count], ["count", (n) => n === undefined || count(n)],
    ["categoryId", maybeId], ["bookId", maybeId], ["tocId", maybeId], ["extended", maybeBoolean]]);
}

function whyReview(r) {
  const bad = firstBad(r, [["format", (n) => n === undefined || n === 1], ["tractate", text(100), 100],
    ["tractateHe", maybeText(100), 100], ["library", maybeText(64), 64], ["marks", Array.isArray]]);
  if (bad) return bad;
  const i = r.marks.findIndex((m) => whyMark(m) !== null);
  return i < 0 ? null : `mark ${i + 1}: ${whyMark(r.marks[i])}`;
}

function whyMark(m) {
  return firstBad(m, [["amud", text(16), 16], ["c", text(16), 16], ["i", count],
    ["t", (s) => typeof s === "string" && s.length <= 2000, 2000], ["ok", maybeBoolean],
    ["cut", (n) => n === undefined || Number.isSafeInteger(n)], ["at", maybeText(64), 64],
    // What the reader said is wrong with the line (DafMark): how it is set on the page, its words, and which of them
    ["layout", maybeBoolean], ["words", maybeBoolean], ["from", maybeCount], ["to", maybeCount],
    // The version of the tractate's layout the line was reviewed on
    ["version", maybeCount]]);
}
