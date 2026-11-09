"""The page that shows orderings.json: every search of the feedback, every method's first 10 side by side.

    python make_html.py orderings.json out.html [title]
"""

import json
import sys

data = json.load(open(sys.argv[1], encoding="utf-8"))

# A summary over the searches whose lines were graded in the feedback (biased toward the app: the grades were given on
# what the app showed; a line no one graded counts as unknown, not as bad)
summary = {}
for key, name, _ in data["methods"]:
    s = {"searches": 0, "first_good": 0, "good3": 0, "bad3": 0, "graded10": 0, "same_as_app": 0, "n": 0}
    for x in data["searches"]:
        top = x["tops"][key]
        app = set(x["tops"]["app"])
        s["n"] += 1
        s["same_as_app"] += len(app & set(top))
        if x["graded"] == 0:
            continue
        s["searches"] += 1
        g = [x["lines"][str(d)][2] if str(d) in x["lines"] else x["lines"][d][2] for d in top]
        if g and g[0] >= 2:
            s["first_good"] += 1
        s["good3"] += sum(1 for v in g[:3] if v >= 2)
        s["bad3"] += sum(1 for v in g[:3] if v == 0)
        s["graded10"] += sum(1 for v in g if v >= 0)
    summary[key] = s

payload = json.dumps({**data, "summary": summary}, ensure_ascii=False, separators=(",", ":"))
TITLE = sys.argv[3] if len(sys.argv) > 3 else "השוואת שיטות מיון על חיפושי המשוב"
FEEDBACK = data.get("feedback", len(sys.argv) <= 3)
SUMMARY_TITLE = "סיכום על החיפושים שיש להם ציונים במשוב (מוטה לטובת האפליקציה)" if FEEDBACK else "סיכום: המקור (ציון 3) לפי שיטה"
SUMMARY_NOTE = ("הציונים ניתנו על שורות שהאפליקציה הציגה, ולכן שיטה שמעלה שורות אחרות נראית כאן גרועה מכפי שהיא: שורה שלא נשפטה אינה רעה, רק לא ידועה."
                if FEEDBACK else "כאן לכל שאלה יש מקור אחד ידוע (ושורות עם אותו נוסח בדיוק), והוא מסומן בירוק עם מסגרת. \"ראשונה טובה\" היא המקור במקום הראשון.")

PAGE = r"""<!DOCTYPE html>
<html lang="he" dir="rtl"><head><meta charset="utf-8">
<title>__TITLE__</title>
<style>
:root{--bg:#fafaf8;--fg:#1d1d1b;--mute:#6b6b66;--line:#e2e1dc;--card:#fff;--acc:#2f5d8a;--new:#eef5fc;
--g3:#1f7a3f;--g2:#5fae6e;--g1:#e0b030;--g0:#c8443c}
@media (prefers-color-scheme:dark){:root{--bg:#1b1b1a;--fg:#ecebe6;--mute:#a3a29c;--line:#383835;--card:#242423;--acc:#8fb8e0;--new:#22303d}}
*{box-sizing:border-box}body{margin:0;font:14px/1.5 "Segoe UI",Arial,sans-serif;background:var(--bg);color:var(--fg)}
header{padding:6px 14px;border-bottom:1px solid var(--line)}h1{font-size:16px;margin:0}
.wrap{display:flex;height:calc(100vh - var(--hh,60px))}
aside{width:330px;min-width:260px;border-left:1px solid var(--line);display:flex;flex-direction:column}
aside .tools{padding:8px;border-bottom:1px solid var(--line);display:flex;flex-direction:column;gap:6px}
aside input[type=search]{width:100%;padding:6px;border:1px solid var(--line);border-radius:6px;background:var(--card);color:var(--fg)}
#list{overflow:auto;flex:1}
.item{padding:6px 10px;border-bottom:1px solid var(--line);cursor:pointer}
.item:hover{background:var(--new)}.item.sel{background:var(--new);border-right:3px solid var(--acc)}
.item .q{font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.item .s{color:var(--mute);font-size:12px}
main{flex:1;min-width:0;padding:8px 14px 0;overflow-y:auto}
#view{}
.scroller{height:calc(100vh - 120px);min-height:320px;overflow:auto;border:1px solid var(--line);border-radius:8px;margin-bottom:10px}
table.cols{border-collapse:separate;border-spacing:0;table-layout:fixed}
table.cols th,table.cols td{width:300px;min-width:300px;max-width:300px;vertical-align:top;padding:6px 8px;border-left:1px solid var(--line);border-bottom:1px solid var(--line);background:var(--card)}
table.cols th{position:sticky;top:0;z-index:2;text-align:right;font-weight:normal;box-shadow:0 1px 0 var(--line)}
table.cols th h3{font-size:14px;margin:0 0 2px}
table.cols td.rank,table.cols th.rank{width:34px;min-width:34px;max-width:34px;text-align:center;color:var(--mute);position:sticky;right:0;z-index:1}
table.cols th.rank{z-index:3}
table.cols td.new{background:var(--new)}
.methods{display:flex;flex-wrap:wrap;gap:4px 12px;margin:8px 0 12px;font-size:13px}
.methods label{white-space:nowrap;cursor:pointer}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(310px,1fr));gap:10px}
.card{background:var(--card);border:1px solid var(--line);border-radius:8px;padding:8px}
.card h3{font-size:14px;margin:0 0 2px}.card .d{color:var(--mute);font-size:12px;margin-bottom:6px}
.card .ov{font-size:12px;color:var(--mute)}
ol{margin:0;padding:0 18px 0 0}li{padding:4px 0;border-top:1px solid var(--line)}
li.new{background:var(--new)}
.b{font-weight:600}.ref{color:var(--mute);font-size:12px}
.snip{font-size:13px;display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden;cursor:pointer}
.snip.open{-webkit-line-clamp:unset}
mark{background:#ffe58a;color:inherit;padding:0 1px;border-radius:2px}
@media (prefers-color-scheme:dark){mark{background:#6b5a17}}
.tag{display:inline-block;font-size:11px;padding:0 5px;border-radius:9px;border:1px solid var(--line);color:var(--mute);margin-left:3px}
.gr{display:inline-block;width:10px;height:10px;border-radius:50%;margin-left:4px;vertical-align:middle}
.byc{outline:2px solid var(--acc);outline-offset:1px}
.g3{background:var(--g3)}.g2{background:var(--g2)}.g1{background:var(--g1)}.g0{background:var(--g0)}
table.sum{border-collapse:collapse;font-size:13px;margin:6px 0 14px}table.sum td,table.sum th{border:1px solid var(--line);padding:3px 7px;text-align:center}
table.sum th:first-child,table.sum td:first-child{text-align:right}
details{margin:6px 0}summary{cursor:pointer;color:var(--acc)}
.legend{font-size:12px;color:var(--mute)}
</style></head><body>
<header><h1>__TITLE__</h1>
<details><summary class="legend">מקרא</summary><div class="legend">מנוע סמנטי: התלמיד (student@695cbe2/pardes-clean-1, חבילה semantic-2) · ספרייה 20261012193827 · כל חיפוש בהיקף שבו נעשה ·
<span class="gr g3"></span>3 <span class="gr g2"></span>2 <span class="gr g1"></span>1 <span class="gr g0"></span>0 = הציון שניתן במשוב (עם מסגרת: ציון של Claude) ·
<span class="tag">ר 12</span>מקום ברגיל <span class="tag">ס 3</span>מקום בסמנטי <span class="tag">קישורים 7</span>ספרים מקושרים ·
רקע כחול: לא בעשר הראשונות של האפליקציה</div></details></header>
<div class="wrap"><aside><div class="tools">
<input type="search" id="filter" placeholder="חיפוש בשאילתות…">
<label><input type="checkbox" id="onlyGraded"> רק חיפושים עם ציונים במשוב</label>
<label><input type="checkbox" id="onlyLex"> רק חיפושים שבהם שני הצדדים מצאו</label>
<div class="legend" id="count"></div></div><div id="list"></div></aside>
<main><div id="top"><details><summary>__SUMMARY_TITLE__</summary><div id="sumwrap"></div>
<p class="legend">__SUMMARY_NOTE__
"זהות לאפליקציה" היא כמה מהעשר הראשונות משותפות לעשר של האפליקציה, בממוצע על כל החיפושים.</p></details>
<details id="pick"><summary id="pickSum">בחירת שיטות</summary><div class="methods" id="methods"></div></details><div id="head"></div></div><div id="view"></div></main></div>
<script>
const DATA = __DATA__;
const M = DATA.methods, S = DATA.searches, LINES = DATA.lines, BOOKS = DATA.books;
const DEFAULT = DATA.defaultMethods || ["app","p12wl","p12w","window","dbsf","combo","dense","lex"];
const STORE = DATA.storeKey || "fusion.methods";
let shown = new Set(JSON.parse(localStorage.getItem(STORE)||"null") || DEFAULT);
let current = null;
const esc = s => s.replace(/[&<>"]/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
const strip = w => w.replace(/[֑-ׇ]/g,"").replace(/[^א-ת\w]/g,"").replace(/[ךםןףץ]/g,c=>"כמנפצ"["ךםןףץ".indexOf(c)]);
const stem = w => { for (let i=0;i<2;i++) if (w.length>3 && "והבכלמש".includes(w[0])) w=w.slice(1); return w; };
function highlight(text, q){
  const want = new Set(q.split(/\s+/).map(w=>stem(strip(w))).filter(w=>w.length>1));
  return text.split(/(\s+)/).map(t => { const k = stem(strip(t)); return k.length>1 && want.has(k) ? "<mark>"+esc(t)+"</mark>" : esc(t); }).join("");
}
function renderSummary(){
  let h = "<table class='sum'><tr><th>שיטה</th><th>חיפושים עם ציונים</th><th>ראשונה טובה (2–3)</th><th>טובות בשלוש הראשונות</th><th>רעות (0) בשלוש הראשונות</th><th>שורות שפוטות בעשר</th><th>זהות לאפליקציה (מתוך 10)</th></tr>";
  for (const [k,name] of M){ const s = DATA.summary[k];
    h += `<tr><td>${esc(name)}</td><td>${s.searches}</td><td>${s.first_good}</td><td>${s.good3}</td><td>${s.bad3}</td><td>${s.graded10}</td><td>${(s.same_as_app/s.n).toFixed(1)}</td></tr>`; }
  document.getElementById("sumwrap").innerHTML = h + "</table>";
}
function renderMethods(){
  const el = document.getElementById("methods");
  el.innerHTML = M.map(([k,name,d]) => `<label title="${esc(d)}"><input type="checkbox" data-k="${k}" ${shown.has(k)?"checked":""}> ${esc(name)}</label>`).join("")
    + ` <a href="#" id="all">הכול</a> · <a href="#" id="def">ברירת מחדל</a>`;
  el.querySelectorAll("input").forEach(i => i.onchange = () => { i.checked ? shown.add(i.dataset.k) : shown.delete(i.dataset.k); save(); renderView(); });
  document.getElementById("all").onclick = e => { e.preventDefault(); shown = new Set(M.map(m=>m[0])); save(); renderMethods(); renderView(); };
  document.getElementById("def").onclick = e => { e.preventDefault(); shown = new Set(DEFAULT); save(); renderMethods(); renderView(); };
}
function save(){ localStorage.setItem(STORE, JSON.stringify([...shown])); pickLabel(); }
function pickLabel(){ document.getElementById("pickSum").textContent = `בחירת שיטות (${shown.size} מתוך ${M.length} מוצגות)`; }
function fitHeader(){ document.documentElement.style.setProperty("--hh", document.querySelector("header").offsetHeight + "px"); }
function renderList(){
  const f = document.getElementById("filter").value.trim(), g = document.getElementById("onlyGraded").checked, lx = document.getElementById("onlyLex").checked;
  const items = S.filter(x => (!f || x.q.includes(f)) && (!g || x.graded>0) && (!lx || (x.tops.lex.length && x.tops.dense.length)));
  document.getElementById("count").textContent = `${items.length} מתוך ${S.length} חיפושים`;
  document.getElementById("list").innerHTML = items.map(x => `<div class="item ${current&&current.id===x.id?"sel":""}" data-id="${x.id}"><div class="q">${esc(x.q)}</div><div class="s">${esc(x.scope)}${x.graded?` · ${x.graded} שפוטות`:""}${x.sources.includes("zayit")?" · מזית":""}${x.sources.includes("otzaria")?" · מאוצריא":""}</div></div>`).join("");
  document.querySelectorAll(".item").forEach(el => el.onclick = () => { current = S.find(x => x.id == el.dataset.id); location.hash = current.id; renderList(); renderView(); });
}
function cell(k, id, x, app){
  const L = LINES[id], P = x.lines[id], B = BOOKS[L[0]];
  const gcls = P[4]==="t" ? `<span class="gr g3 byc" title="המקור שהשאלה מחפשת"></span>` :
    P[2]>=0 ? `<span class="gr g${P[2]}${P[4]==="c"?" byc":""}" title="ציון ${P[2]}${P[4]==="c"?" (של Claude)":" (מהמשוב)"}"></span>` : "";
  return `<td class="${k!=="app"&&!app.has(id)?"new":""}">${gcls}<span class="b">${esc(B[0])}</span> <span class="ref">${esc(L[1]||"")} · ${esc(B[1])}${B[2]?" · ספר יסוד":""}</span><br>`
    + `<span class="tag">ר ${P[0]||"—"}</span><span class="tag">ס ${P[1]||"—"}</span><span class="tag">קישורים ${P[3]}</span><span class="tag">${L[3]} מילים</span>`
    + `<div class="snip" onclick="this.classList.toggle('open')">${highlight(L[2], x.q)}</div></td>`;
}
function renderView(){
  const v = document.getElementById("view"), hd = document.getElementById("head");
  if (!current){ hd.innerHTML = ""; v.innerHTML = "<p class='legend'>בחרו חיפוש מהרשימה.</p>"; return; }
  const x = current, app = new Set(x.tops.app);
  hd.innerHTML = `<h2 style="margin:2px 0">${esc(x.q)}</h2><div class="legend" style="margin-bottom:6px">${esc(x.scope)} · החיפוש הרגיל מצא ${x.lexTotal.toLocaleString()} שורות · נעשה ${x.count} פעמים · גוללים את הטבלה למטה ולצדדים; "בחירת שיטות" למעלה מוסיף ומסתיר עמודות; מעבר עם העכבר על שם שיטה מסביר אותה</div>`;
  const cols = M.filter(m => shown.has(m[0]));
  let h = `<div class="scroller"><table class="cols"><thead><tr><th class="rank">#</th>`;
  for (const [k,name,d] of cols){ const same = x.tops[k].filter(id => app.has(id)).length;
    h += `<th title="${esc(d)}"><h3>${esc(name)} <span class="tag" style="cursor:help">?</span></h3>${k!=="app"?`<div class="ov">${same}/10 משותפות לאפליקציה</div>`:`<div class="ov">הסדר שפרדס מציג</div>`}</th>`; }
  h += "</tr></thead><tbody>";
  for (let i = 0; i < 10; i++){
    h += `<tr><td class="rank">${i+1}</td>`;
    for (const [k] of cols){ const id = x.tops[k][i];
      h += id === undefined ? (i===0 && !x.tops[k].length ? "<td class='legend'>אין תוצאות</td>" : "<td></td>") : cell(k, id, x, app); }
    h += "</tr>";
  }
  v.innerHTML = h + "</tbody></table></div>";
  fitTable();
}
function fitTable(){ const sc = document.querySelector(".scroller"); if (!sc) return;
  sc.style.height = Math.max(320, window.innerHeight - sc.getBoundingClientRect().top - 10) + "px"; }
window.addEventListener("resize", fitTable);
document.addEventListener("toggle", fitTable, true);
["filter","onlyGraded","onlyLex"].forEach(id => document.getElementById(id).oninput = renderList);
renderSummary(); renderMethods(); pickLabel(); fitHeader();
new ResizeObserver(fitHeader).observe(document.querySelector("header"));
current = S.find(x => x.id == location.hash.slice(1)) || S[0];
renderList(); renderView();
</script></body></html>"""

open(sys.argv[2], "w", encoding="utf-8").write(PAGE.replace("__TITLE__", TITLE).replace("__SUMMARY_TITLE__", SUMMARY_TITLE)
                                               .replace("__SUMMARY_NOTE__", SUMMARY_NOTE).replace("__DATA__", payload))
print("written", sys.argv[2])
