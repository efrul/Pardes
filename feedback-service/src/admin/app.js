// The maintainer's page of Pardes's feedback service (GET /admin): what users sent, read with the admin token
// (the FEEDBACK_ADMIN_TOKEN secret) from the service's own routes. Every text that came in a package is untrusted:
// it reaches the page only as text (textContent, attribute values), never as markup, and the page's CSP runs no
// script but this file.
"use strict";

const TOKEN_KEY = "pardes-feedback-admin";
const PAGE = 20; // packages a request, with their bodies
const ROWS = 500; // a table's rows on the page; the downloads hold them all
const DAYS = 30;
const GRADES = [
  { grade: 3, name: "בדיוק זה" },
  { grade: 2, name: "רלוונטי" },
  { grade: 0, name: "לא רלוונטי" },
];
const COLUMNS = { G: "גמרא", R: 'רש"י', T: "תוספות" };

const state = { token: null, packages: [], deletions: [], data: null, view: "overview", loadedAt: 0, ui: {} };

const number = new Intl.NumberFormat("he-IL");
const dateTime = new Intl.DateTimeFormat("he-IL", { dateStyle: "short", timeStyle: "short" });
const dayMonth = new Intl.DateTimeFormat("he-IL", { day: "numeric", month: "numeric" });
const fullDay = new Intl.DateTimeFormat("he-IL", { weekday: "short", day: "numeric", month: "numeric", year: "numeric" });

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

const $ = (id) => document.getElementById(id);
const when = (ms) => (Number.isFinite(ms) && ms > 0 ? dateTime.format(new Date(ms)) : "—");
const short = (id) => String(id ?? "").slice(0, 8);
const sum = (rows, of) => rows.reduce((n, row) => n + of(row), 0);
const size = (n) => (n >= 1e6 ? `${(n / 1e6).toFixed(1)} MB` : n >= 1e3 ? `${Math.round(n / 1e3)} KB` : `${n} B`);

function setStatus(text) {
  $("status").textContent = text;
}

// ---- The token ----

function savedToken() {
  try {
    return sessionStorage.getItem(TOKEN_KEY) ?? localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function keepToken(token, remember) {
  try {
    sessionStorage.setItem(TOKEN_KEY, token);
    if (remember) localStorage.setItem(TOKEN_KEY, token);
  } catch {
    // A private window: the token lasts as long as the page
  }
}

function forgetToken() {
  try {
    sessionStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Nothing kept
  }
}

// ---- The service ----

class Refused extends Error {}

async function api(path) {
  const response = await fetch(path, { headers: { authorization: `Bearer ${state.token}` }, cache: "no-store" });
  if (response.status === 401) throw new Refused("הקוד לא נכון.");
  if (response.status === 503) throw new Refused("לשירות לא נקבע קוד מנהל (הסוד FEEDBACK_ADMIN_TOKEN).");
  if (!response.ok) throw new Error(`השירות ענה ${response.status}.`);
  return response.json();
}

/** Every package with its body, page by page, and the senders who had theirs deleted. */
async function load() {
  setStatus("טוען…");
  const packages = [];
  let deletions = [];
  let after = 0;
  for (;;) {
    const page = await api(`/v1/packages?bodies=1&limit=${PAGE}&after=${after}`);
    packages.push(...page.packages);
    deletions = page.deletions ?? [];
    setStatus(`טוען… ${number.format(packages.length)} חבילות`);
    if (!page.more || page.packages.length === 0) break;
    after = page.packages[page.packages.length - 1].id;
  }
  state.packages = packages;
  state.deletions = deletions;
  state.data = derive(packages);
  state.loadedAt = Date.now();
  setStatus(`עודכן ${when(state.loadedAt)}`);
}

/**
 * What the packages hold, each row once: a search made again, a grade changed or a daf line marked again is sent
 * again in a later package, and stands as it was last sent.
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
      const key = [p.sender, s.query, s.categoryId, s.bookId, s.tocId, Boolean(s.extended)].join("\u0000");
      const row = { ...s, sender: p.sender, count: s.count ?? 1, lastAt: s.lastAt ?? 0 };
      if (!searches.has(key) || row.lastAt >= searches.get(key).lastAt) searches.set(key, row);
    }
    for (const g of body.grades ?? []) {
      const key = [p.sender, g.search, g.lineId].join("\u0000");
      const row = { ...g, sender: p.sender };
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
  // The same words in the same scope, by one sender or more
  const queries = new Map();
  for (const s of searches.values()) {
    const key = [s.query, s.categoryId, s.bookId, s.tocId, Boolean(s.extended)].join("\u0000");
    if (!queries.has(key)) {
      queries.set(key, { query: s.query, categoryId: s.categoryId, bookId: s.bookId, tocId: s.tocId, extended: Boolean(s.extended), count: 0, senders: new Set(), lastAt: 0 });
    }
    const q = queries.get(key);
    q.count += s.count;
    q.senders.add(s.sender);
    q.lastAt = Math.max(q.lastAt, s.lastAt);
  }
  return { senders, queries: [...queries.values()], grades: [...grades.values()], marks: [...marks.values()] };
}

/** Where a search looked: the whole library, or a category, a book or a section of a book's contents. */
function scope(row) {
  const parts = [];
  if (row.tocId != null) parts.push(`פרק ${row.tocId}`);
  else if (row.bookId != null) parts.push(`ספר ${row.bookId}`);
  else if (row.categoryId != null) parts.push(`קטגוריה ${row.categoryId}`);
  else parts.push("כל הספרייה");
  if (row.extended) parts.push("מורחב");
  return parts.join(" · ");
}

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
    tab.setAttribute("aria-selected", String(tab.dataset.view === state.view));
  }
  const views = { overview, searches, grades, daf, packages };
  $("view").replaceChildren(views[state.view]());
}

function tile(label, value, note) {
  return h("div", { class: "tile" },
    h("div", { class: "label" }, label),
    h("div", { class: "value" }, number.format(value)),
    note ? h("div", { class: "note" }, note) : null);
}

function overview() {
  const d = state.data;
  const last = state.packages.at(-1);
  return h("div", {},
    h("div", { class: "tiles" },
      tile("חבילות", state.packages.length),
      tile("שולחים", d.senders.size),
      tile("חיפושים", sum(d.queries, (q) => q.count), `${number.format(d.queries.length)} שונים`),
      tile("דירוגים", d.grades.length),
      tile("שורות שנבדקו בצורת הדף", d.marks.length)),
    h("p", { class: "lastline" }, last ? `החבילה האחרונה התקבלה ${when(last.receivedAt)}.` : "עוד לא התקבלה חבילה."),
    perDayCard(),
    h("div", { class: "grid2" }, gradesCard(), topSearchesCard()));
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
  const top = niceMax(Math.max(...days.map((d) => d.count)));
  const ticks = [0, top / 2, top];
  const chart = h("div", { class: "chart", role: "img", "aria-label": `חבילות בכל יום, ב־${DAYS} הימים האחרונים` },
    ticks.map((t) => [
      h("div", { class: t === 0 ? "gridline base" : "gridline", style: null }),
      h("div", { class: "tick" }, number.format(t)),
    ]),
    h("div", { class: "cols" }, days.map((d) => {
      const label = fullDay.format(d.day);
      const value = `${number.format(d.count)} ${d.count === 1 ? "חבילה" : "חבילות"}`;
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
  const xlabels = h("div", { class: "xlabels" },
    days.map((d, i) => h("span", {}, i % 5 === 0 || i === DAYS - 1 ? dayMonth.format(d.day) : "")));
  const table = h("div", { class: "tablewrap section-gap", hidden: !state.ui.dayTable },
    h("table", {},
      h("thead", {}, h("tr", {}, h("th", {}, "יום"), h("th", { class: "num" }, "חבילות"))),
      h("tbody", {}, [...days].reverse().map((d) =>
        h("tr", {}, h("td", { class: "when" }, fullDay.format(d.day)), h("td", { class: "num" }, number.format(d.count)))))));
  const toggle = h("button", {
    type: "button",
    class: "linkish",
    onclick: () => {
      state.ui.dayTable = !state.ui.dayTable;
      table.hidden = !state.ui.dayTable;
      toggle.textContent = state.ui.dayTable ? "הסתרת הטבלה" : "הצגה כטבלה";
    },
  }, state.ui.dayTable ? "הסתרת הטבלה" : "הצגה כטבלה");
  return h("section", { class: "card" },
    h("h2", {}, "חבילות בכל יום"),
    h("p", { class: "sub" }, `${DAYS} הימים האחרונים, לפי שעון המחשב הזה`),
    chart, xlabels,
    h("div", { class: "chart-foot" }, h("span", { class: "muted" }, ""), toggle),
    table);
}

/** A round top for an axis: 1, 2 or 5 times a power of ten, at least 2 so that the middle tick is whole. */
function niceMax(n) {
  if (n <= 2) return 2;
  const p = 10 ** Math.floor(Math.log10(n));
  return [1, 2, 5, 10].map((m) => m * p).find((m) => m >= n);
}

/** Ranked horizontal bars of [rows] ({name, value, note}), one hue, the value at the tip. */
function hbars(rows, empty) {
  if (rows.length === 0) return h("p", { class: "empty" }, empty);
  const top = Math.max(...rows.map((r) => r.value), 1);
  return h("div", { class: "hbars" }, rows.map((r) => {
    const fill = h("div", { class: "fill" });
    fill.style.width = `${(r.value / top) * 100}%`;
    return h("div", { class: "hbar" },
      h("span", { class: "name", title: r.name }, r.name),
      h("div", { class: "track" }, fill),
      h("span", { class: "num" }, r.note ?? number.format(r.value)));
  }));
}

function gradesCard() {
  const all = state.data.grades;
  const rows = GRADES.map(({ grade, name }) => {
    const n = all.filter((g) => g.grade === grade).length;
    const share = all.length ? Math.round((n / all.length) * 100) : 0;
    return { name, value: n, note: `${number.format(n)} · ${share}%` };
  });
  return h("section", { class: "card" },
    h("h2", {}, "הדירוגים"),
    h("p", { class: "sub" }, "הציון שניתן לכל תוצאה שדורגה"),
    all.length ? hbars(rows) : h("p", { class: "empty" }, "עוד אין דירוגים."));
}

function topSearchesCard() {
  const rows = [...state.data.queries]
    .sort((a, b) => b.count - a.count || b.lastAt - a.lastAt)
    .slice(0, 10)
    .map((q) => ({ name: q.query, value: q.count }));
  return h("section", { class: "card" },
    h("h2", {}, "החיפושים הנפוצים"),
    h("p", { class: "sub" }, "כמה פעמים חיפשו, מכל השולחים"),
    hbars(rows, "עוד אין חיפושים."));
}

/** A toolbar's search box, which keeps its text when the view is drawn again. */
function filterBox(key, placeholder, onInput) {
  const input = h("input", { type: "search", placeholder, value: state.ui[key] ?? "", "aria-label": placeholder });
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
    const mark = c.title === column.title ? (sort.down ? " ▼" : " ▲") : "";
    return h("th", { class: c.class, scope: "col", "aria-sort": mark ? (sort.down ? "descending" : "ascending") : null },
      h("button", {
        type: "button",
        onclick: () => {
          state.ui[`${key}Sort`] = { title: c.title, down: c.title === column.title ? !sort.down : true };
          render();
        },
      }, c.title + mark));
  }));
  const body = h("tbody", {}, sorted.slice(0, ROWS).map((row) =>
    h("tr", {}, columns.map((c) => h("td", { class: c.class }, c.cell ? c.cell(row) : c.value(row))))));
  return h("div", {},
    h("div", { class: "tablewrap" }, h("table", {}, h("thead", {}, head), body)),
    sorted.length > ROWS ? h("p", { class: "more" }, `מוצגות ${number.format(ROWS)} השורות הראשונות מתוך ${number.format(sorted.length)}. החיפוש מצמצם, וההורדה כוללת את כולן.`) : null);
}

function searches() {
  const filter = state.ui.searchesFilter ?? "";
  const rows = state.data.queries.filter((q) => matches(q.query, filter));
  const columns = [
    { title: "החיפוש", value: (q) => q.query, class: "text", cell: (q) => h("div", { class: "clip", title: q.query }, q.query) },
    { title: "איפה", value: scope },
    { title: "פעמים", value: (q) => q.count, class: "num", cell: (q) => number.format(q.count) },
    { title: "שולחים", value: (q) => q.senders.size, class: "num", cell: (q) => number.format(q.senders.size) },
    { title: "לאחרונה", value: (q) => q.lastAt, class: "when", cell: (q) => when(q.lastAt) },
  ];
  return h("div", {},
    toolbarFor("searchesFilter", "חיפוש בחיפושים", rows.length, state.data.queries.length, () =>
      download(`pardes-searches-${today()}.csv`, csv(
        ["query", "scope", "categoryId", "bookId", "tocId", "extended", "count", "senders", "lastAt"],
        state.data.queries.map((q) => [q.query, scope(q), q.categoryId, q.bookId, q.tocId, q.extended, q.count, q.senders.size, new Date(q.lastAt).toISOString()])),
        "text/csv;charset=utf-8")),
    table("searches", columns, rows, { title: "פעמים", down: true }));
}

/** The search box, how many rows show, and the CSV download, above a table. */
function toolbarFor(key, placeholder, shown, total, onDownload) {
  const box = filterBox(key, placeholder, () => {
    render();
    const again = document.querySelector(".toolbar input[type=search]");
    again?.focus();
    again?.setSelectionRange(again.value.length, again.value.length);
  });
  return h("div", { class: "toolbar" },
    box,
    h("span", { class: "count" }, shown === total ? `${number.format(total)} שורות` : `${number.format(shown)} מתוך ${number.format(total)}`),
    h("button", { type: "button", onclick: onDownload }, "הורדה (CSV)"));
}

function grades() {
  const filter = state.ui.gradesFilter ?? "";
  const grade = state.ui.grade ?? "all";
  const rows = state.data.grades.filter((g) => matches(g.query, filter) && (grade === "all" || String(g.grade) === grade));
  const select = h("select", { "aria-label": "הציון" },
    h("option", { value: "all" }, "כל הציונים"),
    GRADES.map((g) => h("option", { value: String(g.grade) }, g.name)));
  select.value = grade;
  select.addEventListener("change", () => {
    state.ui.grade = select.value;
    render();
  });
  const name = (g) => GRADES.find((x) => x.grade === g.grade)?.name ?? String(g.grade);
  const columns = [
    { title: "החיפוש", value: (g) => g.query, class: "text", cell: (g) => h("div", { class: "clip", title: g.query }, g.query) },
    { title: "הציון", value: (g) => g.grade, cell: (g) => h("span", { class: `pill grade-${g.grade}` }, name(g)) },
    { title: "מקום", value: (g) => g.rank, class: "num", cell: (g) => number.format(g.rank) },
    { title: "שורה", value: (g) => g.lineId, class: "num", cell: (g) => String(g.lineId) },
    { title: "ספר", value: (g) => g.bookId, class: "num", cell: (g) => String(g.bookId) },
    { title: "איפה", value: (g) => scope({ ...g, bookId: g.scopeBookId }) },
    { title: "המודל", value: (g) => g.model ?? "", class: "id" },
    { title: "שולח", value: (g) => g.sender, class: "id", cell: (g) => short(g.sender) },
    { title: "מתי", value: (g) => g.gradedAt, class: "when", cell: (g) => when(g.gradedAt) },
  ];
  const bar = toolbarFor("gradesFilter", "חיפוש בדירוגים", rows.length, state.data.grades.length, () =>
    download(`pardes-grades-${today()}.csv`, csv(
      ["query", "search", "grade", "rank", "lineId", "bookId", "categoryId", "scopeBookId", "tocId", "extended", "model", "sender", "gradedAt"],
      state.data.grades.map((g) => [g.query, g.search, g.grade, g.rank, g.lineId, g.bookId, g.categoryId, g.scopeBookId, g.tocId, g.extended, g.model, g.sender, new Date(g.gradedAt).toISOString()])),
      "text/csv;charset=utf-8"));
  bar.insertBefore(select, bar.children[1]);
  return h("div", {}, bar, table("grades", columns, rows, { title: "מתי", down: true }));
}

function daf() {
  const marks = state.data.marks;
  if (marks.length === 0) return h("p", { class: "empty" }, "עוד לא נשלחו בדיקות של צורת הדף.");
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
    { title: "המילים", value: (m) => m.t, class: "text" },
    { title: "השורה", value: ok },
    { title: "הזזה", value: (m) => m.cut ?? 0, class: "num", cell: (m) => (m.cut ? String(m.cut) : "—") },
    { title: "שולח", value: (m) => m.sender, class: "id", cell: (m) => short(m.sender) },
    { title: "מתי", value: (m) => m.atMs, class: "when", cell: (m) => when(m.atMs) },
  ];
  const bar = toolbarFor("dafFilter", "חיפוש בשורות", rows.length, marks.length, () =>
    download(`pardes-daf-${today()}.csv`, csv(
      ["tractate", "tractateHe", "amud", "column", "line", "words", "ok", "cut", "sender", "at"],
      marks.map((m) => [m.tractate, m.tractateHe, m.amud, m.c, m.i, m.t, m.ok, m.cut, m.sender, m.at])),
      "text/csv;charset=utf-8"));
  return h("div", {},
    h("section", { class: "card" }, h("h2", {}, "המסכתות"), h("p", { class: "sub" }, "השורות שנבדקו בכל מסכת"), summary),
    h("div", { class: "section-gap" }, bar, table("marks", columns, rows, { title: "מתי", down: true })));
}

function packages() {
  const open = (p) => {
    $("raw-title").textContent = `חבילה ${p.id} · ${when(p.receivedAt)}`;
    $("raw-body").textContent = JSON.stringify(p.package, null, 2);
    $("raw").showModal();
  };
  const columns = [
    { title: "מספר", value: (p) => p.id, class: "num", cell: (p) => String(p.id) },
    { title: "התקבלה", value: (p) => p.receivedAt, class: "when", cell: (p) => when(p.receivedAt) },
    { title: "שולח", value: (p) => p.sender, class: "id", cell: (p) => short(p.sender) },
    { title: "גרסה", value: (p) => p.appVersion ?? "" },
    { title: "הספרייה", value: (p) => p.library ?? "", class: "id" },
    { title: "דירוגים", value: (p) => p.grades, class: "num", cell: (p) => number.format(p.grades) },
    { title: "חיפושים", value: (p) => p.searches, class: "num", cell: (p) => number.format(p.searches) },
    { title: "צורת הדף", value: (p) => p.dafMarks, class: "num", cell: (p) => number.format(p.dafMarks) },
    { title: "גודל", value: (p) => p.bytes, class: "num", cell: (p) => size(p.bytes) },
    { title: "", value: (p) => p.id, cell: (p) => h("button", { type: "button", onclick: () => open(p) }, "פתיחה") },
  ];
  const deletions = state.deletions.length
    ? h("div", { class: "section-gap" },
      h("h2", {}, "שולחים שמחקו את מה ששלחו"),
      h("p", { class: "sub" }, "החבילות שלהם נמחקו מהשירות, וגם ההעתקים ש־fetch.py הוריד נמחקים בהורדה הבאה"),
      table("deletions", [
        { title: "שולח", value: (d) => d.sender, class: "id", cell: (d) => short(d.sender) },
        { title: "מתי", value: (d) => d.deletedAt, class: "when", cell: (d) => when(d.deletedAt) },
      ], state.deletions, { title: "מתי", down: true }))
    : null;
  return h("div", {}, table("packages", columns, state.packages, { title: "מספר", down: true }), deletions);
}

// ---- Start ----

async function enter(token, remember) {
  state.token = token;
  await load();
  keepToken(token, remember);
  $("login").hidden = true;
  $("app").hidden = false;
  $("actions").hidden = false;
  render();
}

function leave(message) {
  forgetToken();
  state.token = null;
  state.packages = [];
  state.deletions = [];
  state.data = null;
  $("app").hidden = true;
  $("actions").hidden = true;
  $("login").hidden = false;
  $("view").replaceChildren();
  $("token").value = "";
  const error = $("login-error");
  error.textContent = message ?? "";
  error.hidden = !message;
}

document.addEventListener("DOMContentLoaded", () => {
  $("login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const button = event.submitter;
    if (button) button.disabled = true;
    $("login-error").hidden = true;
    try {
      await enter($("token").value.trim(), $("remember").checked);
    } catch (e) {
      leave(e instanceof Refused ? e.message : `לא הצלחתי להגיע לשירות: ${e.message}`);
    } finally {
      if (button) button.disabled = false;
    }
  });
  for (const tab of document.querySelectorAll(".tabs [role=tab]")) {
    tab.addEventListener("click", () => {
      state.view = tab.dataset.view;
      render();
    });
  }
  $("refresh").addEventListener("click", async () => {
    try {
      await load();
      render();
    } catch (e) {
      if (e instanceof Refused) leave(e.message);
      else setStatus(`הרענון נכשל: ${e.message}`);
    }
  });
  $("download").addEventListener("click", () =>
    download(`pardes-feedback-${today()}.json`, JSON.stringify({ packages: state.packages, deletions: state.deletions }, null, 1), "application/json"));
  $("logout").addEventListener("click", () => leave());
  $("raw-close").addEventListener("click", () => $("raw").close());
  window.addEventListener("scroll", hideTip, { passive: true });

  const token = savedToken();
  if (token) {
    enter(token, false).catch((e) => leave(e instanceof Refused ? e.message : `לא הצלחתי להגיע לשירות: ${e.message}`));
  }
});
