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
  for (const kind of ["grades", "searches", "daf"]) {
    p[kind] ??= [];
    if (!Array.isArray(p[kind])) return `${kind} is not a list`;
  }
  if (p.grades.length + p.searches.length + p.daf.length === 0) return "nothing in the package";
  if (!p.grades.every(isGrade)) return "a grade is not one";
  if (!p.searches.every(isSearch)) return "a search is not one";
  if (!p.daf.every(isReview)) return "a daf review is not one";
  return null;
}

const object = (o) => typeof o === "object" && o !== null && !Array.isArray(o);
const filled = (s, max) => typeof s === "string" && s.length > 0 && s.length <= max;
const optional = (s, max) => s === undefined || s === null || (typeof s === "string" && s.length <= max);
const count = (n) => Number.isSafeInteger(n) && n >= 0;
const maybeId = (n) => n === undefined || n === null || Number.isSafeInteger(n);
const maybeBoolean = (b) => b === undefined || b === null || typeof b === "boolean";

function isGrade(g) {
  return object(g) && filled(g.query, 1000) && filled(g.search, 2000) && count(g.lineId) && count(g.bookId) &&
    GRADES.has(g.grade) && count(g.rank) && count(g.gradedAt) && maybeId(g.categoryId) && maybeId(g.scopeBookId) &&
    maybeId(g.tocId) && maybeBoolean(g.extended) && optional(g.model, 200);
}

function isSearch(s) {
  return object(s) && filled(s.query, 1000) && count(s.lastAt) && (s.count === undefined || count(s.count)) &&
    maybeId(s.categoryId) && maybeId(s.bookId) && maybeId(s.tocId) && maybeBoolean(s.extended);
}

function isReview(r) {
  return object(r) && (r.format === undefined || r.format === 1) && filled(r.tractate, 100) && optional(r.tractateHe, 100) &&
    optional(r.library, 64) && Array.isArray(r.marks) && r.marks.every(isMark);
}

function isMark(m) {
  return object(m) && filled(m.amud, 16) && filled(m.c, 16) && count(m.i) && typeof m.t === "string" &&
    m.t.length <= 2000 && maybeBoolean(m.ok) && (m.cut === undefined || Number.isSafeInteger(m.cut)) && optional(m.at, 64);
}
