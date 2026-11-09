// The open search data site (GET /): what users of the Torah library apps (Pardes, Zayit, Otzaria) shared to improve
// search, open to everyone and in no one app's name, and a form to upload the packages people saved to files. It reads
// the service's public route, where each sender is an alias (a digest of its id, never the id, with which anyone could
// delete what that sender sent). The page shows no sender at all, alias or id, nor puts one in a download: the alias
// only lets it count each sender's rows once, and how many senders there are. Uploads go to POST /v1/packages, as the
// app sends them. Every text that came in a package is untrusted: it reaches the page only as text (textContent,
// attribute values), never as markup, and the page's CSP runs no script but this file.
"use strict";

const PAGE = 50; // packages a request, the public route's most
const ROWS = 500; // a table's rows on the page; the downloads hold them all
const DAYS = 30;
/** A package's part at most, as Pardes and the telemetry tool split theirs; the service takes 1,000,000 bytes. */
const PART_BYTES = 900_000;
const MAX_FILE = 50_000_000;
const SENDER = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
/** Seconds to wait when the service says this address sent too much in a minute, and how many times. */
const RETRY_SECONDS = 20;
const RETRIES = 6;
const GRADES = [
  { grade: 3, name: "בדיוק זה" },
  { grade: 2, name: "רלוונטי" },
  { grade: 0, name: "לא רלוונטי" },
];
/** The app a row came from: Pardes's own packages say nothing, the telemetry tool's say zayit, pardes or otzaria. */
const SOURCES = { pardes: "פרדס", zayit: "זית", otzaria: "אוצריא" };
const COLUMNS = { G: "גמרא", R: 'רש"י', T: "תוספות" };
const VIEWS = ["overview", "searches", "grades", "daf", "packages", "upload"];

const state = {
  packages: [],
  deletions: [],
  data: null,
  view: viewOfHash(),
  loadedAt: 0,
  loading: false,
  again: false,
  failed: null,
  ui: {},
  uploads: [],
  uploading: false,
};

const number = new Intl.NumberFormat("he-IL");
const percent = new Intl.NumberFormat("he-IL", { style: "percent" });
const dateTime = new Intl.DateTimeFormat("he-IL", { dateStyle: "short", timeStyle: "short" });
const dayMonth = new Intl.DateTimeFormat("he-IL", { day: "numeric", month: "numeric" });
const fullDay = new Intl.DateTimeFormat("he-IL", { weekday: "short", day: "numeric", month: "numeric", year: "numeric" });
const encoder = new TextEncoder();

// ---- The page's pieces ----

/** An element of [tag] with [attrs] and [children]: strings become text, never markup. */
function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs ?? {})) {
    if (value === undefined || value === null || value === false) continue;
    if (key === "class") el.className = value;
    else if (key.startsWith("on")) el.addEventListener(key.slice(2), value);
    else el.setAttribute(key, value === true ? "" : String(value));
  }
  for (const child of children.flat(Infinity)) {
    if (child === undefined || child === null || child === false) continue;
    el.append(child instanceof Node ? child : String(child));
  }
  return el;
}

/** An SVG element, built as h builds HTML. */
function svg(tag, attrs, ...children) {
  const el = document.createElementNS("http://www.w3.org/2000/svg", tag);
  for (const [key, value] of Object.entries(attrs ?? {})) el.setAttribute(key, String(value));
  el.append(...children);
  return el;
}

const $ = (id) => document.getElementById(id);
const when = (ms) => (Number.isFinite(ms) && ms > 0 ? dateTime.format(new Date(ms)) : "—");
/** [p] without its sender, which the page never shows. */
const anonymous = ({ sender, ...rest }) => rest;
const sum = (rows, of) => rows.reduce((n, row) => n + of(row), 0);
const size = (n) => (n >= 1e6 ? `${(n / 1e6).toFixed(1)} MB` : n >= 1e3 ? `${Math.round(n / 1e3)} KB` : `${n} B`);
const sourceName = (source) => SOURCES[source] ?? String(source);
const plural = (n, one, many) => `${number.format(n)} ${n === 1 ? one : many}`;

function setStatus(text) {
  $("status").textContent = text;
}

function viewOfHash() {
  const view = location.hash.slice(1);
  return VIEWS.includes(view) ? view : "overview";
}

// ---- Reading ----

async function getJSON(path) {
  const response = await fetch(path, { cache: "no-store" });
  if (response.status === 429) throw new Error("נשלחו יותר מדי בקשות מהכתובת הזאת בדקה האחרונה. אפשר לחכות דקה ולרענן.");
  if (!response.ok) throw new Error(`השירות ענה ${response.status}.`);
  return response.json();
}

/** Every package with its body, page by page, and the senders who had theirs deleted. */
async function load() {
  if (state.loading) {
    // Once this load ends, again: an upload came in while it read
    state.again = true;
    return;
  }
  state.loading = true;
  state.again = false;
  state.failed = null;
  setStatus("טוען…");
  $("refresh").disabled = true;
  if (!state.data) render();
  try {
    const packages = [];
    let deletions = [];
    let after = 0;
    for (;;) {
      const page = await getJSON(`/v1/public/packages?limit=${PAGE}&after=${after}`);
      packages.push(...page.packages);
      deletions = page.deletions ?? [];
      const progress = `טוען… ${plural(packages.length, "חבילה", "חבילות")}`;
      setStatus(progress);
      const counter = $("loading-count");
      if (counter) counter.textContent = progress;
      if (!page.more || page.packages.length === 0) break;
      after = page.packages[page.packages.length - 1].id;
    }
    state.packages = packages;
    state.deletions = deletions;
    state.data = derive(packages);
    state.loadedAt = Date.now();
    setStatus(`עודכן ${when(state.loadedAt)}`);
  } catch (e) {
    state.failed = e instanceof TypeError ? "לא הצלחתי להגיע לשירות. אולי אין חיבור לרשת." : e.message;
    setStatus("הטעינה נכשלה");
  } finally {
    state.loading = false;
    $("refresh").disabled = false;
    render();
  }
  if (state.again) await load();
}

/**
 * What the packages hold, each row once: a search made again, a grade changed or a daf line marked again is sent
 * again in a later package, and stands as it was last sent. A package uploaded twice adds nothing here.
 */
function derive(packages) {
  const searches = new Map();
  const grades = new Map();
  const marks = new Map();
  const senders = new Set();
  for (const p of packages) {
    senders.add(p.sender);
    const body = p.package ?? {};
    for (const s of body.searches ?? []) {
      const source = s.source ?? body.source ?? "pardes";
      const key = [p.sender, source, s.query, s.categoryId, s.bookId, s.tocId, s.scope, Boolean(s.extended)].join("\u0000");
      const row = { ...s, source, sender: p.sender, count: s.count ?? 1, lastAt: s.lastAt ?? 0 };
      if (!searches.has(key) || row.lastAt >= searches.get(key).lastAt) searches.set(key, row);
    }
    for (const g of body.grades ?? []) {
      const source = g.source ?? body.source ?? "pardes";
      const key = [p.sender, source, g.search, g.lineId, g.heRef].join("\u0000");
      const row = { ...g, source, sender: p.sender };
      if (!grades.has(key) || row.gradedAt >= grades.get(key).gradedAt) grades.set(key, row);
    }
    for (const review of body.daf ?? []) {
      for (const m of review.marks ?? []) {
        const key = [p.sender, review.tractate, m.amud, m.c, m.i].join("\u0000");
        const row = { ...m, atMs: Date.parse(m.at ?? "") || 0, tractate: review.tractate, tractateHe: review.tractateHe, sender: p.sender };
        if (!marks.has(key) || row.atMs >= marks.get(key).atMs) marks.set(key, row);
      }
    }
  }
  // The same words in the same scope, by one sender or more, from one app or more
  const queries = new Map();
  for (const s of searches.values()) {
    const key = [s.query, s.categoryId, s.bookId, s.tocId, s.scope, Boolean(s.extended)].join("\u0000");
    if (!queries.has(key)) {
      queries.set(key, { query: s.query, categoryId: s.categoryId, bookId: s.bookId, tocId: s.tocId, scope: s.scope, extended: Boolean(s.extended), count: 0, senders: new Set(), sources: new Set(), lastAt: 0 });
    }
    const q = queries.get(key);
    q.count += s.count;
    q.senders.add(s.sender);
    q.sources.add(s.source);
    q.lastAt = Math.max(q.lastAt, s.lastAt);
  }
  return {
    senders,
    searches: [...searches.values()],
    queries: [...queries.values()],
    grades: [...grades.values()],
    marks: [...marks.values()],
  };
}

/** Where a search looked: the whole library, a category, a book, a section of a book's contents, or Otzaria's path. */
function scope(row) {
  const parts = [];
  if (row.scope) parts.push(String(row.scope).replace(/^\/+/, "").replaceAll("/", " › "));
  else if (row.tocId != null) parts.push(`פרק ${row.tocId}`);
  else if (row.bookId != null) parts.push(`ספר ${row.bookId}`);
  else if (row.categoryId != null) parts.push(`קטגוריה ${row.categoryId}`);
  else parts.push("כל הספרייה");
  if (row.extended) parts.push("מורחב");
  return parts.join(", ");
}

/** The result a grade is of: Otzaria's reference or book, or the library's book and line. */
function result(g) {
  if (g.heRef && (!g.bookTitle || String(g.heRef).startsWith(g.bookTitle))) return g.heRef;
  if (g.heRef || g.bookTitle) return [g.bookTitle, g.heRef].filter(Boolean).join(", ");
  return `ספר ${g.bookId}, שורה ${g.lineId}`;
}

const relevant = (grades) => grades.filter((g) => g.grade >= 2).length;

// ---- Downloads ----

function download(name, text, type) {
  const url = URL.createObjectURL(new Blob([text], { type }));
  const a = h("a", { href: url, download: name });
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

function csv(header, rows) {
  const cell = (v) => {
    const s = v === undefined || v === null ? "" : String(v);
    return /[",\n\r]/.test(s) ? `"${s.replaceAll('"', '""')}"` : s;
  };
  // With a BOM, so that Excel reads the Hebrew
  return `﻿${[header, ...rows].map((r) => r.map(cell).join(",")).join("\r\n")}\r\n`;
}

const today = () => new Date().toISOString().slice(0, 10);
const iso = (ms) => (Number.isFinite(ms) && ms > 0 ? new Date(ms).toISOString() : "");

// ---- The tooltip ----

function showTip(target, value, label) {
  const tip = $("tooltip");
  tip.replaceChildren(h("strong", {}, value), h("span", {}, label));
  tip.hidden = false;
  const box = target.getBoundingClientRect();
  const width = tip.offsetWidth;
  const left = Math.min(window.innerWidth - width - 8, Math.max(8, box.left + box.width / 2 - width / 2));
  tip.style.left = `${left}px`;
  tip.style.top = `${Math.max(8, box.top - tip.offsetHeight - 8)}px`;
}

function hideTip() {
  $("tooltip").hidden = true;
}

// ---- The views ----

function render() {
  hideTip();
  for (const tab of document.querySelectorAll(".tabs [role=tab]")) {
    const selected = tab.dataset.view === state.view;
    tab.setAttribute("aria-selected", String(selected));
    tab.tabIndex = selected ? 0 : -1;
  }
  $("view").setAttribute("aria-labelledby", `tab-${state.view}`);
  const d = state.data;
  const counts = {
    searches: d?.queries.length,
    grades: d?.grades.length,
    daf: d?.marks.length,
    packages: d ? state.packages.length : undefined,
  };
  for (const badge of document.querySelectorAll("[data-count]")) {
    const n = counts[badge.dataset.count];
    badge.textContent = n === undefined ? "" : number.format(n);
  }
  $("download").disabled = !d;
  if (state.view === "upload") {
    $("view").replaceChildren(upload());
    return;
  }
  if (!d) {
    $("view").replaceChildren(state.failed ? failedNotice() : loadingNotice());
    return;
  }
  const views = { overview, searches, grades, daf, packages };
  $("view").replaceChildren(views[state.view]());
}

function loadingNotice() {
  return h("section", { class: "notice", "aria-busy": "true" },
    h("div", { class: "spinner", "aria-hidden": "true" }),
    h("h2", {}, "טוען את מה שנשלח"),
    h("p", { id: "loading-count" }, "טוען…"));
}

function failedNotice() {
  return h("section", { class: "notice failed", role: "alert" },
    h("h2", {}, "לא הצלחתי לטעון את הנתונים"),
    h("p", {}, state.failed),
    h("button", { type: "button", class: "primary", onclick: () => load() }, "לנסות שוב"));
}

function overview() {
  return h("div", { class: "stack" },
    pageOfSearches(),
    summary(),
    perDayCard(),
    h("div", { class: "grid3" }, gradesCard(), modelsCard(), sourcesCard()));
}

/** The searches, the same words once wherever they looked, most searched first, with how their results were graded. */
function words() {
  const all = new Map();
  for (const q of state.data.queries) {
    const w = all.get(q.query) ?? { name: q.query, times: 0, senders: new Set(), lastAt: 0, graded: 0, relevant: 0 };
    w.times += q.count;
    for (const s of q.senders) w.senders.add(s);
    w.lastAt = Math.max(w.lastAt, q.lastAt);
    all.set(q.query, w);
  }
  for (const g of state.data.grades) {
    const w = all.get(g.query);
    if (!w) continue;
    w.graded++;
    if (g.grade >= 2) w.relevant++;
  }
  return [...all.values()].sort((a, b) => b.times - a.times || b.lastAt - a.lastAt);
}

/** How many times [w] was searched, as a mark after it, and in words for a screen reader. */
function times(w) {
  return h("span", { class: "n" }, number.format(w.times), h("span", { class: "visually-hidden" }, w.times === 1 ? " פעם" : " פעמים"));
}

/** What a commentary says of a search: how often, by how many, and how its results were graded. */
function gloss(w) {
  let text = w.times === 1 ? "חיפשו פעם אחת" : `חיפשו ${number.format(w.times)} פעמים`;
  if (w.senders.size > 1) text += ` ${number.format(w.senders.size)} שולחים`;
  if (w.graded === 1) text += w.relevant ? ", תוצאה רלוונטית" : ", תוצאה לא רלוונטית";
  else if (w.graded > 1) text += `, ${number.format(w.relevant)} מ־${number.format(w.graded)} תוצאות רלוונטיות`;
  return text;
}

/**
 * The page of searches, set as a page of Gemara: the most searched in the middle, the first opening with a framed
 * word as a chapter does, and the next ones around it as Rashi and Tosafot, each with what it was.
 */
function pageOfSearches() {
  const all = words();
  if (all.length === 0) {
    return h("section", { class: "notice" },
      h("h2", {}, "עוד אין חיפושים"),
      h("p", {}, "חיפושים שנשלחים מהתוכנות, או שמעלים בלשונית ההעלאה, יופיעו כאן."),
      h("button", { type: "button", class: "primary", onclick: () => show("upload") }, "העלאת חבילות"));
  }
  const main = all.slice(0, 8);
  const inner = all.slice(8, 13);
  const outer = all.slice(13, 18);
  const [first, ...rest] = main[0].name.split(" ");
  const gemara = h("p", { class: "gemara", dir: "rtl" },
    h("span", { class: "opening" }, first),
    rest.length ? ` ${rest.join(" ")}` : "",
    times(main[0]),
    main.slice(1).map((w) => [h("span", { class: "stop", "aria-hidden": "true" }, ": "), w.name, times(w)]),
    h("span", { class: "stop", "aria-hidden": "true" }, ":"));
  const commentary = (list, label) => list.length === 0 ? null
    : h("p", { class: "commentary", "aria-label": label },
      list.map((w, i) => [i ? " " : "", h("span", { class: "dh" }, w.name), ` ${gloss(w)}.`]));
  const sides = [commentary(inner, "החיפושים הבאים"), commentary(outer, "ועוד חיפושים")];
  // A grid's first column is the right one: Rashi beside the binding, the Gemara, then Tosafot
  const body = h("div", { class: sides.some(Boolean) ? "daf-body" : "daf-body alone" },
    sides[0] ?? (sides[1] ? h("span", {}) : null), gemara, sides[1]);
  return h("figure", { class: "daf", "aria-labelledby": "daf-title" },
    h("header", { class: "daf-head" },
      h("h2", { id: "daf-title" }, "החיפושים הנפוצים"),
      h("span", {}, `${plural(all.length, "חיפוש שונה", "חיפושים שונים")} עד עכשיו`)),
    body,
    h("figcaption", { class: "daf-caption" },
      "באמצע, החיפושים שחיפשו הכי הרבה פעמים, והמספר הקטן אחרי כל אחד הוא כמה פעמים. מסביב, כמו רש״י ותוספות, הבאים אחריהם."));
}

/** What the data holds, in a few sentences. */
function summary() {
  const d = state.data;
  const last = state.packages.at(-1);
  const strong = (n) => h("strong", {}, typeof n === "number" ? number.format(n) : n);
  const searched = sum(d.queries, (q) => q.count);
  const good = relevant(d.grades);
  return h("p", { class: "summary" },
    "עד עכשיו שותפו ", strong(searched), " חיפושים (", strong(d.queries.length), " שונים) מ־", strong(d.senders.size),
    d.senders.size === 1 ? " שולח" : " שולחים", ", ב־", strong(state.packages.length), state.packages.length === 1 ? " חבילה. " : " חבילות. ",
    d.grades.length ? ["דורגו ", strong(d.grades.length), " תוצאות, ו־", strong(percent.format(good / d.grades.length)), " מהן נמצאו רלוונטיות. "] : "עוד לא דורגו תוצאות. ",
    d.marks.length ? ["ונבדקו ", strong(d.marks.length), " שורות בצורת הדף. "] : "",
    last ? `החבילה האחרונה התקבלה ${when(last.receivedAt)}.` : "");
}

/** The packages received each day of the last [DAYS] days, as columns, with a table view. */
function perDayCard() {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  start.setDate(start.getDate() - (DAYS - 1));
  const days = Array.from({ length: DAYS }, (_, i) => {
    const day = new Date(start);
    day.setDate(start.getDate() + i);
    return { day, count: 0 };
  });
  for (const p of state.packages) {
    const day = new Date(p.receivedAt);
    day.setHours(0, 0, 0, 0);
    const i = Math.round((day - start) / 86_400_000);
    if (i >= 0 && i < DAYS) days[i].count++;
  }
  const total = sum(days, (d) => d.count);
  const top = niceMax(Math.max(...days.map((d) => d.count)));
  const ticks = [0, top / 2, top];
  const chart = h("div", { class: "chart", role: "img", "aria-label": `חבילות בכל יום, ב־${DAYS} הימים האחרונים: ${number.format(total)} בסך הכול` },
    ticks.map((t) => [
      h("div", { class: t === 0 ? "gridline base" : "gridline" }),
      h("div", { class: "tick" }, number.format(t)),
    ]),
    h("div", { class: "cols" }, days.map((d) => {
      const label = fullDay.format(d.day);
      const value = plural(d.count, "חבילה", "חבילות");
      const col = h("div", { class: "col", tabindex: 0, "aria-label": `${label}: ${value}` },
        h("div", { class: "bar" }));
      col.addEventListener("pointerenter", () => showTip(col, value, label));
      col.addEventListener("pointerleave", hideTip);
      col.addEventListener("focus", () => showTip(col, value, label));
      col.addEventListener("blur", hideTip);
      col.firstChild.style.height = `${(d.count / top) * 100}%`;
      return col;
    })));
  // The gridlines and ticks at their heights (CSSOM, which the CSP allows)
  const lines = chart.querySelectorAll(".gridline");
  const labels = chart.querySelectorAll(".tick");
  ticks.forEach((t, i) => {
    const y = `${100 - (t / top) * 100}%`;
    lines[i].style.top = y;
    labels[i].style.top = y;
  });
  const xlabels = h("div", { class: "xlabels", "aria-hidden": "true" },
    days.map((d, i) => h("span", {}, i % 5 === 0 || i === DAYS - 1 ? dayMonth.format(d.day) : "")));
  const table = h("div", { class: "tablewrap", hidden: !state.ui.dayTable },
    h("table", {},
      h("thead", {}, h("tr", {}, h("th", { scope: "col" }, "יום"), h("th", { class: "num", scope: "col" }, "חבילות"))),
      h("tbody", {}, [...days].reverse().map((d) =>
        h("tr", {}, h("td", { class: "when" }, fullDay.format(d.day)), h("td", { class: "num" }, number.format(d.count)))))));
  const toggle = h("button", {
    type: "button",
    class: "linkish",
    "aria-expanded": String(Boolean(state.ui.dayTable)),
    onclick: () => {
      state.ui.dayTable = !state.ui.dayTable;
      table.hidden = !state.ui.dayTable;
      toggle.setAttribute("aria-expanded", String(state.ui.dayTable));
      toggle.textContent = state.ui.dayTable ? "הסתרת הטבלה" : "הצגה כטבלה";
    },
  }, state.ui.dayTable ? "הסתרת הטבלה" : "הצגה כטבלה");
  return h("section", { class: "card" },
    h("h2", {}, "חבילות בכל יום"),
    h("p", { class: "sub" }, `${DAYS} הימים האחרונים, לפי השעון של המכשיר הזה`),
    chart, xlabels,
    h("div", { class: "chart-foot" }, h("span", { class: "muted" }, `${plural(total, "חבילה", "חבילות")} בתקופה`), toggle),
    table);
}

/** A round top for an axis: 1, 2 or 5 times a power of ten, at least 2 so that the middle tick is whole. */
function niceMax(n) {
  if (n <= 2) return 2;
  const p = 10 ** Math.floor(Math.log10(n));
  return [1, 2, 5, 10].map((m) => m * p).find((m) => m >= n);
}

/** Ranked bars of [rows] ({name, value, note}), one hue: the name and value above, the bar beneath. */
function hbars(rows, empty) {
  if (rows.length === 0) return h("p", { class: "empty" }, empty);
  const top = Math.max(...rows.map((r) => r.value), 1);
  return h("div", { class: "hbars" }, rows.map((r) => {
    const fill = h("div", { class: "fill" });
    fill.style.width = `${(r.value / top) * 100}%`;
    return h("div", { class: "hbar" },
      h("span", { class: "name", title: r.name, dir: "auto" }, r.name),
      h("span", { class: "num" }, r.note ?? number.format(r.value)),
      h("div", { class: "track", "aria-hidden": "true" }, fill));
  }));
}

function gradesCard() {
  const all = state.data.grades;
  const rows = GRADES.map(({ grade, name }) => {
    const n = all.filter((g) => g.grade === grade).length;
    return { name, value: n, note: `${number.format(n)} (${percent.format(all.length ? n / all.length : 0)})` };
  });
  return h("section", { class: "card" },
    h("h2", {}, "הדירוגים"),
    h("p", { class: "sub" }, "הציון שניתן לכל תוצאה שדורגה"),
    all.length ? hbars(rows) : h("p", { class: "empty" }, "עוד אין דירוגים."));
}

/** How each search model's results were graded: how many, and how many of them were relevant. */
function modelsCard() {
  const models = new Map();
  for (const g of state.data.grades) {
    const name = g.model || "בלי שם מודל";
    const m = models.get(name) ?? { name, grades: [] };
    m.grades.push(g);
    models.set(name, m);
  }
  const rows = [...models.values()]
    .sort((a, b) => b.grades.length - a.grades.length)
    .slice(0, 8)
    .map((m) => ({
      name: m.name,
      value: m.grades.length,
      note: `${number.format(m.grades.length)}, ${percent.format(relevant(m.grades) / m.grades.length)} רלוונטיות`,
    }));
  return h("section", { class: "card" },
    h("h2", {}, "לפי מודל החיפוש"),
    h("p", { class: "sub" }, "כמה תוצאות של כל מודל דורגו, וכמה מהן היו רלוונטיות"),
    hbars(rows, "עוד אין דירוגים."));
}

/** Which apps the searches and grades came from. */
function sourcesCard() {
  const totals = new Map();
  const add = (source, key, n) => {
    const t = totals.get(source) ?? { source, searches: 0, grades: 0 };
    t[key] += n;
    totals.set(source, t);
  };
  for (const s of state.data.searches) add(s.source, "searches", s.count);
  for (const g of state.data.grades) add(g.source, "grades", 1);
  const rows = [...totals.values()]
    .sort((a, b) => b.searches + b.grades - (a.searches + a.grades))
    .map((t) => ({
      name: sourceName(t.source),
      value: t.searches + t.grades,
      note: `${plural(t.searches, "חיפוש", "חיפושים")}, ${plural(t.grades, "דירוג", "דירוגים")}`,
    }));
  return h("section", { class: "card" },
    h("h2", {}, "לפי תוכנה"),
    h("p", { class: "sub" }, "התוכנה שבה נעשו החיפושים והדירוגים"),
    hbars(rows, "עוד אין חיפושים או דירוגים."));
}

/** A toolbar's search box, which keeps its text when the view is drawn again. */
function filterBox(key, placeholder, onInput) {
  const input = h("input", { type: "search", id: `filter-${key}`, placeholder, value: state.ui[key] ?? "", "aria-label": placeholder });
  input.addEventListener("input", () => {
    state.ui[key] = input.value;
    onInput();
  });
  return input;
}

function matches(text, filter) {
  return !filter || String(text ?? "").toLowerCase().includes(filter.trim().toLowerCase());
}

/** A table of [rows] by [columns] ({title, value, class, sort}), sorted by the column the user picked. */
function table(key, columns, rows, defaultSort) {
  const sort = state.ui[`${key}Sort`] ?? defaultSort;
  const column = columns.find((c) => c.title === sort.title) ?? columns[0];
  const by = column.sort ?? column.value;
  const sorted = [...rows].sort((a, b) => {
    const x = by(a);
    const y = by(b);
    const order = typeof x === "number" && typeof y === "number" ? x - y : String(x ?? "").localeCompare(String(y ?? ""), "he");
    return sort.down ? -order : order;
  });
  const head = h("tr", {}, columns.map((c) => {
    const mark = c.title && c.title === column.title ? (sort.down ? " ▼" : " ▲") : "";
    return h("th", { class: c.class, scope: "col", "aria-sort": mark ? (sort.down ? "descending" : "ascending") : null },
      c.title ? h("button", {
        type: "button",
        onclick: () => {
          state.ui[`${key}Sort`] = { title: c.title, down: c.title === column.title ? !sort.down : true };
          render();
        },
      }, c.title + mark) : h("span", { class: "visually-hidden" }, c.label ?? ""));
  }));
  const body = h("tbody", {}, sorted.slice(0, ROWS).map((row) =>
    h("tr", {}, columns.map((c) => h("td", { class: c.class }, c.cell ? c.cell(row) : c.value(row))))));
  return h("div", {},
    h("div", { class: "tablewrap" }, h("table", {}, h("thead", {}, head), body)),
    sorted.length > ROWS ? h("p", { class: "more" }, `מוצגות ${number.format(ROWS)} השורות הראשונות מתוך ${number.format(sorted.length)}. החיפוש מצמצם, וההורדה כוללת את כולן.`) : null);
}

/** The search box, how many rows show, and the CSV download, above a table. */
function toolbarFor(key, placeholder, shown, total, onDownload) {
  const box = filterBox(key, placeholder, () => {
    render();
    const again = $(`filter-${key}`);
    again?.focus();
    again?.setSelectionRange(again.value.length, again.value.length);
  });
  return h("div", { class: "toolbar" },
    box,
    h("span", { class: "count" }, shown === total ? plural(total, "שורה", "שורות") : `${number.format(shown)} מתוך ${number.format(total)}`),
    h("button", { type: "button", class: "end", onclick: onDownload }, "הורדה (CSV)"));
}

const text = (value) => h("div", { class: "clip", title: value, dir: "auto" }, value);
const sources = (set) => h("span", { class: "source" }, [...set].map(sourceName).join(", "));

function searches() {
  const filter = state.ui.searchesFilter ?? "";
  const rows = state.data.queries.filter((q) => matches(q.query, filter));
  const columns = [
    { title: "החיפוש", value: (q) => q.query, class: "text", cell: (q) => text(q.query) },
    { title: "איפה", value: scope, class: "scope" },
    { title: "מקור", value: (q) => [...q.sources].map(sourceName).join(", "), cell: (q) => sources(q.sources) },
    { title: "פעמים", value: (q) => q.count, class: "num", cell: (q) => number.format(q.count) },
    { title: "שולחים", value: (q) => q.senders.size, class: "num", cell: (q) => number.format(q.senders.size) },
    { title: "לאחרונה", value: (q) => q.lastAt, class: "when", cell: (q) => when(q.lastAt) },
  ];
  return h("div", {},
    toolbarFor("searchesFilter", "חיפוש בחיפושים", rows.length, state.data.queries.length, () =>
      download(`pardes-searches-${today()}.csv`, csv(
        ["query", "scope", "sources", "categoryId", "bookId", "tocId", "extended", "count", "senders", "lastAt"],
        state.data.queries.map((q) => [q.query, scope(q), [...q.sources].join(" "), q.categoryId, q.bookId, q.tocId, q.extended, q.count, q.senders.size, iso(q.lastAt)])),
      "text/csv;charset=utf-8")),
    table("searches", columns, rows, { title: "פעמים", down: true }));
}

function grades() {
  const filter = state.ui.gradesFilter ?? "";
  const grade = state.ui.grade ?? "all";
  const rows = state.data.grades.filter((g) => (matches(g.query, filter) || matches(g.model, filter)) && (grade === "all" || String(g.grade) === grade));
  const select = h("select", { id: "grade-filter", "aria-label": "הציון" },
    h("option", { value: "all" }, "כל הציונים"),
    GRADES.map((g) => h("option", { value: String(g.grade) }, g.name)));
  select.value = grade;
  select.addEventListener("change", () => {
    state.ui.grade = select.value;
    render();
  });
  const name = (g) => GRADES.find((x) => x.grade === g.grade)?.name ?? String(g.grade);
  const columns = [
    { title: "החיפוש", value: (g) => g.query, class: "text", cell: (g) => text(g.query) },
    { title: "הציון", value: (g) => g.grade, cell: (g) => h("span", { class: `pill grade-${g.grade}` }, h("span", { class: "dot", "aria-hidden": "true" }), name(g)) },
    { title: "מקום", value: (g) => g.rank, class: "num", cell: (g) => number.format(g.rank) },
    { title: "התוצאה", value: result, class: "scope", cell: (g) => h("span", { dir: "auto" }, result(g)) },
    { title: "איפה", value: (g) => scope({ ...g, bookId: g.scopeBookId }), class: "scope" },
    { title: "המודל", value: (g) => g.model ?? "", class: "quiet" },
    { title: "מקור", value: (g) => sourceName(g.source), cell: (g) => h("span", { class: "source" }, sourceName(g.source)) },
    { title: "מתי", value: (g) => g.gradedAt, class: "when", cell: (g) => when(g.gradedAt) },
  ];
  const bar = toolbarFor("gradesFilter", "חיפוש בדירוגים או במודל", rows.length, state.data.grades.length, () =>
    download(`pardes-grades-${today()}.csv`, csv(
      ["query", "search", "grade", "rank", "lineId", "bookId", "bookTitle", "heRef", "categoryId", "scopeBookId", "tocId", "scope", "extended", "model", "source", "gradedAt"],
      state.data.grades.map((g) => [g.query, g.search, g.grade, g.rank, g.lineId, g.bookId, g.bookTitle, g.heRef, g.categoryId, g.scopeBookId, g.tocId, g.scope, g.extended, g.model, g.source, iso(g.gradedAt)])),
    "text/csv;charset=utf-8"));
  bar.insertBefore(select, bar.children[1]);
  return h("div", {}, bar, table("grades", columns, rows, { title: "מתי", down: true }));
}

function daf() {
  const marks = state.data.marks;
  if (marks.length === 0) return h("section", { class: "notice" }, h("h2", {}, "צורת הדף"), h("p", {}, "עוד לא נשלחו בדיקות של צורת הדף."));
  const tractates = new Map();
  for (const m of marks) {
    const t = tractates.get(m.tractate) ?? { tractate: m.tractate, he: m.tractateHe || m.tractate, lines: 0, right: 0, wrong: 0, moved: 0, last: 0 };
    t.lines++;
    if (m.ok === true) t.right++;
    if (m.ok === false) t.wrong++;
    if (m.cut) t.moved++;
    t.last = Math.max(t.last, m.atMs);
    tractates.set(m.tractate, t);
  }
  const summary = table("tractates", [
    { title: "המסכת", value: (t) => t.he },
    { title: "שורות", value: (t) => t.lines, class: "num", cell: (t) => number.format(t.lines) },
    { title: "נכונות", value: (t) => t.right, class: "num", cell: (t) => number.format(t.right) },
    { title: "לא נכונות", value: (t) => t.wrong, class: "num", cell: (t) => number.format(t.wrong) },
    { title: "תחילת שורה הוזזה", value: (t) => t.moved, class: "num", cell: (t) => number.format(t.moved) },
    { title: "לאחרונה", value: (t) => t.last, class: "when", cell: (t) => when(t.last) },
  ], [...tractates.values()], { title: "שורות", down: true });
  const filter = state.ui.dafFilter ?? "";
  const rows = marks.filter((m) => matches(m.t, filter) || matches(m.tractateHe, filter));
  const ok = (m) => (m.ok === true ? "נכונה" : m.ok === false ? "לא נכונה" : "—");
  const columns = [
    { title: "המסכת", value: (m) => m.tractateHe || m.tractate },
    { title: "עמוד", value: (m) => m.amud },
    { title: "טור", value: (m) => COLUMNS[String(m.c).toUpperCase()] ?? m.c },
    { title: "שורה", value: (m) => m.i, class: "num", cell: (m) => String(m.i) },
    { title: "המילים", value: (m) => m.t, class: "text", cell: (m) => h("span", { dir: "auto" }, m.t) },
    { title: "השורה", value: ok },
    { title: "הזזה", value: (m) => m.cut ?? 0, class: "num", cell: (m) => (m.cut ? String(m.cut) : "—") },
    { title: "מתי", value: (m) => m.atMs, class: "when", cell: (m) => when(m.atMs) },
  ];
  const bar = toolbarFor("dafFilter", "חיפוש בשורות", rows.length, marks.length, () =>
    download(`pardes-daf-${today()}.csv`, csv(
      ["tractate", "tractateHe", "amud", "column", "line", "words", "ok", "cut", "at"],
      marks.map((m) => [m.tractate, m.tractateHe, m.amud, m.c, m.i, m.t, m.ok, m.cut, m.at])),
    "text/csv;charset=utf-8"));
  return h("div", { class: "stack" },
    h("section", { class: "card" }, h("h2", {}, "המסכתות"), h("p", { class: "sub" }, "השורות שנבדקו בכל מסכת"), summary),
    h("div", {}, bar, table("marks", columns, rows, { title: "מתי", down: true })));
}

/** The apps a package's rows came from. */
function packageSources(p) {
  const body = p.package ?? {};
  const found = new Set();
  for (const row of [...(body.searches ?? []), ...(body.grades ?? [])]) found.add(row.source ?? body.source ?? "pardes");
  if (found.size === 0) found.add(body.source ?? "pardes");
  return found;
}

function packages() {
  const open = (p) => {
    $("raw-title").textContent = `חבילה ${p.id}, התקבלה ${when(p.receivedAt)}`;
    $("raw-body").textContent = JSON.stringify(anonymous(p.package ?? {}), null, 2);
    $("raw").showModal();
  };
  const columns = [
    { title: "מספר", value: (p) => p.id, class: "num", cell: (p) => String(p.id) },
    { title: "התקבלה", value: (p) => p.receivedAt, class: "when", cell: (p) => when(p.receivedAt) },
    { title: "מקור", value: (p) => [...packageSources(p)].map(sourceName).join(", "), cell: (p) => sources(packageSources(p)) },
    { title: "גרסה", value: (p) => p.appVersion ?? "" },
    { title: "הספרייה", value: (p) => p.library ?? "", class: "quiet" },
    { title: "דירוגים", value: (p) => p.grades, class: "num", cell: (p) => number.format(p.grades) },
    { title: "חיפושים", value: (p) => p.searches, class: "num", cell: (p) => number.format(p.searches) },
    { title: "צורת הדף", value: (p) => p.dafMarks, class: "num", cell: (p) => number.format(p.dafMarks) },
    { title: "גודל", value: (p) => p.bytes, class: "num", cell: (p) => h("bdi", {}, size(p.bytes)) },
    { title: "", label: "התוכן", value: (p) => p.id, cell: (p) => h("button", { type: "button", onclick: () => open(p), "aria-label": `פתיחת חבילה ${p.id}` }, "פתיחה") },
  ];
  const deletions = state.deletions.length
    ? h("section", {},
      h("h2", { class: "section-title" }, "מחיקות"),
      h("p", { class: "section-sub" }, `${plural(state.deletions.length, "שולח מחק", "שולחים מחקו")} את מה ששלחו. החבילות שלהם נמחקו מהשירות, וגם ההעתקים שהמתחזק הוריד נמחקים בהורדה הבאה.`),
      table("deletions", [
        { title: "מתי", value: (d) => d.deletedAt, class: "when", cell: (d) => when(d.deletedAt) },
      ], state.deletions, { title: "מתי", down: true }))
    : null;
  return h("div", { class: "stack" },
    state.packages.length ? table("packages", columns, state.packages, { title: "מספר", down: true })
      : h("section", { class: "notice" }, h("h2", {}, "עוד אין חבילות"), h("p", {}, "חבילות שנשלחות מהתוכנות, או שמעלים בלשונית ההעלאה, יופיעו כאן.")),
    deletions);
}

// ---- Uploading ----

class Refusal extends Error {}

/** Why [p] is not a package the service takes, in words for the page, or null; the service checks the rest. */
function whyNot(p) {
  if (typeof p !== "object" || p === null || Array.isArray(p)) return "זו לא חבילת נתוני חיפוש.";
  if (p.app !== "Pardes" || p.format !== 1) return "זו לא חבילת נתוני חיפוש בפורמט 1, הפורמט שהשירות מקבל.";
  if (typeof p.sender !== "string" || !SENDER.test(p.sender)) return "בחבילה אין מזהה שולח תקין.";
  const rows = ["grades", "searches", "daf"].reduce((n, k) => n + (Array.isArray(p[k]) ? p[k].length : 0), 0);
  if (rows === 0) return "החבילה ריקה: אין בה חיפושים, דירוגים או סימונים בצורת הדף.";
  return null;
}

/** The packages in a file's [text]: one package, or a list of them. */
function packagesIn(text) {
  let value;
  try {
    value = JSON.parse(text.replace(/^﻿/, ""));
  } catch {
    throw new Refusal("הקובץ אינו JSON תקין.");
  }
  if (value && !Array.isArray(value) && Array.isArray(value.packages) && value.format === undefined) {
    throw new Refusal("זה קובץ ההורדה של האתר הזה, והנתונים שבו כבר נמצאים בשירות.");
  }
  const list = Array.isArray(value) ? value : [value];
  if (list.length === 0) throw new Refusal("אין בקובץ חבילות.");
  list.forEach((p, i) => {
    const why = whyNot(p);
    if (why) throw new Refusal(list.length > 1 ? `חבילה ${i + 1} בקובץ: ${why}` : why);
  });
  return list;
}

const bytesOf = (p) => encoder.encode(JSON.stringify(p)).length;

/**
 * [p] in parts of at most PART_BYTES, as Pardes (FeedbackPackage.parts) and the telemetry tool split theirs: whole when
 * it fits, else its grades, searches and each daf review's marks halved until each part fits.
 */
function parts(p) {
  if (bytesOf(p) <= PART_BYTES) return [p];
  const base = { ...p, grades: [], searches: [], daf: [] };
  const split = (rows, make) => {
    if (rows.length === 0) return [];
    const one = make(rows);
    if (rows.length === 1 || bytesOf(one) <= PART_BYTES) return [one];
    const mid = Math.floor(rows.length / 2);
    return [...split(rows.slice(0, mid), make), ...split(rows.slice(mid), make)];
  };
  return [
    ...split(p.grades ?? [], (grades) => ({ ...base, grades })),
    ...split(p.searches ?? [], (searches) => ({ ...base, searches })),
    ...(p.daf ?? []).flatMap((review) => split(review.marks ?? [], (marks) => ({ ...base, daf: [{ ...review, marks }] }))),
  ];
}

/** The service's reason for refusing a package, in words for the page. */
function said(status, reason) {
  if (reason === "too many packages today") return "השולח הזה כבר שלח היום 200 חבילות, המכסה היומית. אפשר לנסות שוב מחר.";
  if (reason === "package too large") return "חלק מהחבילה גדול מ־1,000,000 בתים, הגודל שהשירות מקבל.";
  if (reason === "nothing in the package") return "החבילה ריקה.";
  if (reason === "no sender id") return "בחבילה אין מזהה שולח תקין.";
  if (["not JSON", "not a package", "unknown format", "not a package of Pardes"].includes(reason)) return "זו לא חבילת נתוני חיפוש בפורמט 1, הפורמט שהשירות מקבל.";
  return [`השירות לא קיבל את החבילה (${status}): `, h("code", {}, reason)];
}

/** Posts [part], waiting and trying again while the service says this address sent too much this minute. */
async function post(part, entry) {
  for (let attempt = 0; ; attempt++) {
    const response = await fetch("/v1/packages", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(part),
    });
    if (response.ok) return;
    const reason = (await response.json().catch(() => null))?.error ?? "";
    if (response.status === 429 && reason !== "too many packages today" && attempt < RETRIES) {
      for (let left = RETRY_SECONDS; left > 0; left--) {
        entry.message = `השירות מקבל עד 20 חבילות בדקה מכל כתובת. ממשיך בעוד ${left} שניות.`;
        showUploads();
        await new Promise((resolve) => setTimeout(resolve, 1000));
      }
      entry.message = "";
      continue;
    }
    throw new Refusal("", { cause: said(response.status, reason) });
  }
}

function addFiles(files) {
  for (const file of files) {
    state.uploads.push({ file, name: file.name, size: file.size, state: "queued", sent: 0, parts: 0, message: "" });
  }
  showUploads();
  if (!state.uploading) uploadAll();
}

async function uploadAll() {
  state.uploading = true;
  let received = 0;
  try {
    for (let entry; (entry = state.uploads.find((u) => u.state === "queued"));) {
      entry.state = "busy";
      showUploads();
      try {
        if (entry.size > MAX_FILE) throw new Refusal("הקובץ גדול מ־50MB.");
        const all = packagesIn(await entry.file.text()).flatMap(parts);
        entry.parts = all.length;
        for (const part of all) {
          await post(part, entry);
          entry.sent++;
          received++;
          showUploads();
        }
        entry.state = "done";
      } catch (e) {
        entry.state = "failed";
        entry.message = e instanceof Refusal ? (e.cause ?? e.message)
          : e instanceof TypeError ? "לא הצלחתי להגיע לשירות. אולי אין חיבור לרשת." : String(e.message ?? e);
      }
      entry.file = null;
      showUploads();
    }
  } finally {
    state.uploading = false;
    showUploads();
  }
  if (received > 0) await load();
}

function uploadState(u) {
  if (u.state === "queued") return ["…", "בתור"];
  if (u.state === "busy") return ["…", u.parts > 1 ? `נשלח ${number.format(u.sent)} מתוך ${number.format(u.parts)}` : "נשלח…"];
  if (u.state === "done") return ["✓", u.parts > 1 ? `התקבל, ב־${number.format(u.parts)} חלקים` : "התקבל"];
  return ["✕", "לא התקבל"];
}

function uploadItem(u) {
  const [icon, label] = uploadState(u);
  return h("li", { class: `upload ${u.state}` },
    h("div", {}, h("div", { class: "file", dir: "auto" }, u.name), h("div", { class: "size" }, h("bdi", {}, size(u.size)))),
    h("span", { class: "state" }, h("span", { class: "icon", "aria-hidden": "true" }, icon), label),
    u.message ? h("div", { class: "why" }, u.message) : null);
}

/** The uploads' list, drawn again in place, so that the file chooser keeps its focus. */
function showUploads() {
  const list = $("upload-list");
  if (!list) return;
  list.replaceChildren(...state.uploads.map(uploadItem).reverse());
  list.hidden = state.uploads.length === 0;
  const clear = $("upload-clear");
  if (clear) clear.hidden = state.uploading || !state.uploads.some((u) => u.state === "done" || u.state === "failed");
}

function uploadIcon() {
  return svg("svg", { viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", "stroke-width": "1.8", "stroke-linecap": "round", "stroke-linejoin": "round", "aria-hidden": "true" },
    svg("path", { d: "M12 15V4" }),
    svg("path", { d: "m7.5 8.5 4.5-4.5 4.5 4.5" }),
    svg("path", { d: "M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3" }));
}

function upload() {
  const input = h("input", { id: "upload-files", type: "file", accept: ".json,application/json", multiple: true, class: "visually-hidden" });
  input.addEventListener("change", () => {
    addFiles([...input.files]);
    input.value = "";
  });
  const zone = h("label", { for: "upload-files", class: "drop" },
    uploadIcon(),
    h("strong", {}, "גוררים לכאן קובצי חבילה"),
    h("span", { class: "hint" }, "קובצי JSON, אחד או כמה יחד"),
    h("span", { class: "choose", "aria-hidden": "true" }, "בחירת קבצים"),
    input);
  zone.addEventListener("dragover", (event) => {
    event.preventDefault();
    zone.classList.add("over");
  });
  zone.addEventListener("dragleave", (event) => {
    if (!zone.contains(event.relatedTarget)) zone.classList.remove("over");
  });
  zone.addEventListener("drop", (event) => {
    event.preventDefault();
    zone.classList.remove("over");
    addFiles([...(event.dataTransfer?.files ?? [])]);
  });
  const list = h("ul", { id: "upload-list", class: "uploads", "aria-live": "polite", hidden: state.uploads.length === 0 },
    state.uploads.map(uploadItem).reverse());
  const clear = h("button", {
    type: "button",
    id: "upload-clear",
    class: "linkish",
    hidden: state.uploading || !state.uploads.some((u) => u.state === "done" || u.state === "failed"),
    onclick: () => {
      state.uploads = state.uploads.filter((u) => u.state === "queued" || u.state === "busy");
      showUploads();
    },
  }, "ניקוי הרשימה");
  return h("div", { class: "upload-grid" },
    h("section", { class: "card" },
      h("h2", {}, "העלאת חבילות"),
      h("p", { class: "sub" }, "חבילות נתוני חיפוש ששמרתם לקובץ, מכל תוכנה, נשלחות מכאן לשירות ומצטרפות למאגר. חבילה גדולה מתחלקת לבד לחלקים."),
      zone,
      list,
      h("div", { class: "chart-foot" }, h("span", {}), clear)),
    h("aside", { class: "card" },
      h("h2", {}, "איזה קבצים"),
      h("ul", { class: "steps" },
        h("li", {}, h("strong", {}, "זית ואוצריא: "), "הכלי לשליחת נתוני חיפוש, בכפתור השמירה לקובץ."),
        h("li", {}, h("strong", {}, "פרדס למחשב: "), "הגדרות › ניהול נתונים › שליחת משוב › שמירה לקובץ."),
        h("li", {}, h("strong", {}, "תוכנה אחרת: "), "כל קובץ JSON בפורמט 1 של חבילות נתוני החיפוש."),
        h("li", {}, h("strong", {}, "קובץ שפוצל לכמה חלקים: "), "בוחרים את כל החלקים יחד. שמות הקבצים הם בדרך כלל ", h("code", { dir: "ltr" }, "pardes-feedback-….json"), ".")),
      h("p", { class: "aside-note" },
        "מה שמעלים כאן מוצג לכל מי שנכנס לאתר וזמין להורדה לכולם, בלי מזהה השולח ובלי שום פרט מזהה אחר. העלאה חוזרת של אותו קובץ לא מכפילה את החיפושים והדירוגים בסיכום.")));
}

// ---- Start ----

function show(view, focus) {
  state.view = view;
  if (location.hash.slice(1) !== view) history.replaceState(null, "", view === "overview" ? location.pathname : `#${view}`);
  render();
  if (focus) $(`tab-${view}`).focus();
}

document.addEventListener("DOMContentLoaded", () => {
  const tabs = [...document.querySelectorAll(".tabs [role=tab]")];
  for (const tab of tabs) {
    tab.addEventListener("click", () => show(tab.dataset.view));
    // Arrow keys move between the tabs (a tablist's keys); the reading order is right to left
    tab.addEventListener("keydown", (event) => {
      const i = tabs.indexOf(tab);
      const step = { ArrowLeft: 1, ArrowRight: -1, Home: -i, End: tabs.length - 1 - i }[event.key];
      if (step === undefined) return;
      event.preventDefault();
      show(tabs[(i + step + tabs.length) % tabs.length].dataset.view, true);
    });
  }
  window.addEventListener("hashchange", () => show(viewOfHash()));
  $("refresh").addEventListener("click", () => load());
  $("to-upload").addEventListener("click", () => {
    show("upload");
    $("upload-files")?.focus();
  });
  // Without the senders: the page shows none, and gives none away
  $("download").addEventListener("click", () => download(`pardes-feedback-${today()}.json`, JSON.stringify({
    packages: state.packages.map(({ sender, package: body, ...row }) => ({ ...row, package: anonymous(body ?? {}) })),
    deletions: state.deletions.map(({ deletedAt }) => ({ deletedAt })),
  }, null, 1), "application/json"));
  $("raw-close").addEventListener("click", () => $("raw").close());
  window.addEventListener("scroll", hideTip, { passive: true });
  // A file dropped beside the drop zone is not opened in place of the page
  window.addEventListener("dragover", (event) => event.preventDefault());
  window.addEventListener("drop", (event) => event.preventDefault());

  render();
  load();
});
