"""Derived lexical.db for the measurements: Zayit's lexical.db (only the tables MagicDictionaryIndex reads), optionally
  --orphans   without the bases that have no forms (they shadow the real paradigm of a word spelled like them)
  --order     each base's forms ordered by their document frequency in the library index (the 32 kept = the commonest)
  --kk SRC,.. Klei Kodesh expansion-routed.db inflections (exp channels infl+bridge) of these sources added to the base
  --entry     also Klei Kodesh fold surfaces of those sources (words the dictionary lacks become forms of their lemma)
  --newbases  lemmas lexical.db lacks become new bases
  --ketiv     Dictionary.db spelling links (כתיב) added as forms of the word's base
Only forms found in the index (df > 0) are added.
args: lexical.db expansion-routed.db Dictionary.db df.tsv out.db [options]"""
import sqlite3, sys, re, os
lex, expdb, dic, dfp, out = sys.argv[1:6]
opts = sys.argv[6:]
def opt(n): return n in opts
def optv(n):
    return opts[opts.index(n)+1] if n in opts else None
FIN = str.maketrans('ךםןףץ', 'כמנפצ')
def norm(s):
    s = re.sub('[֑-ׇ]', '', s).replace('״','').replace('׳','')
    return re.sub(r'\s+',' ',s.translate(FIN)).strip()
df = {}
for line in open(dfp, encoding='utf-8'):
    w, n = line.rstrip('\n').split('\t'); df[w] = int(n)
L = sqlite3.connect('file:'+lex+'?mode=ro', uri=True)
base = dict(L.execute('select id,value from base'))
surf = {i:(v,b) for i,v,b in L.execute('select id,value,base_id from surface')}
variant = dict(L.execute('select id,value from variant'))
sv = L.execute('select surface_id,variant_id from surface_variant').fetchall()
extra = set(L.execute('select surface_id,base_id from surface_extra_base').fetchall())
stats = {}
if opt('--orphans'):
    used = {b for _,b in surf.values()} | {b for _,b in extra}
    orph = [i for i in base if i not in used]
    for i in orph: del base[i]
    stats['orphans_dropped'] = len(orph)
surf_by_norm = {}
for i,(v,b) in surf.items(): surf_by_norm.setdefault(norm(v), i)
base_by_norm = {}
for i,v in base.items(): base_by_norm.setdefault(norm(v), i)
next_surf = max(surf)+1; next_base = max(base)+1
added = {'surface':0,'extra':0,'base':0}
def base_for(lemma, create):
    global next_base
    n = norm(lemma)
    if n in base_by_norm: return base_by_norm[n]
    if n in surf_by_norm: return surf[surf_by_norm[n]][1]
    if not create or df.get(n,0) == 0: return None
    base[next_base] = lemma; base_by_norm[n] = next_base; next_base += 1; added['base'] += 1
    return next_base-1
def add_form(f, b, foldmap=None, lemma=None):
    global next_surf
    n = norm(f)
    if not n or ' ' in n or df.get(n,0) == 0: return
    s = surf_by_norm.get(n)
    if s is None:
        surf[next_surf] = (f, b); surf_by_norm[n] = next_surf; next_surf += 1; added['surface'] += 1
    elif surf[s][1] != b and (s,b) not in extra:
        if foldmap is not None and lemma not in foldmap.get(n, ()): return
        extra.add((s,b)); added['extra'] += 1
kk = optv('--kk')
if kk:
    srcs = set(kk.split(','))
    E = sqlite3.connect('file:'+expdb+'?mode=ro', uri=True)
    foldmap = {}
    for s_, lemma, src, w in E.execute('select surface,lemma,source,weight from fold'):
        if src in srcs or src == 'library': foldmap.setdefault(norm(s_), set()).add(lemma)
    bylemma = {}
    for lemma, form, ch, src in E.execute("select lemma,form,channel,source from exp where channel in ('infl','bridge') order by lemma,rank"):
        if src in srcs: bylemma.setdefault(lemma, []).append(form)
    if opt('--entry'):
        for s_, lemma, src, w in E.execute('select surface,lemma,source,weight from fold where weight >= 0.5'):
            if src in srcs: bylemma.setdefault(lemma, []).append(s_)
    for lemma, forms in bylemma.items():
        b = base_for(lemma, opt('--newbases'))
        if b is None: continue
        for f in forms: add_form(f, b, foldmap, lemma)
if opt('--ketiv'):
    D = sqlite3.connect('file:'+dic+'?mode=ro', uri=True)
    n0 = added['surface']
    for a, t in D.execute('select a.headword,b.headword from link l join word a on a.id=l.word_id join word b on b.id=l.target_id where l.kind_id=2'):
        na = norm(a)
        s = surf_by_norm.get(na)
        b = surf[s][1] if s is not None else base_by_norm.get(na)
        if b is not None: add_form(t, b)
    stats['ketiv_surfaces'] = added['surface'] - n0
# ids: by base, then df desc (--order) or the original order
items = list(surf.items())
if opt('--order'): items.sort(key=lambda kv: (kv[1][1], -df.get(norm(kv[1][0]),0), kv[0]))
else: items.sort(key=lambda kv: (kv[1][1], kv[0]))
newid = {old:i+1 for i,(old,_) in enumerate(items)}
if os.path.exists(out): os.remove(out)
O = sqlite3.connect(out)
O.executescript('''
CREATE TABLE base (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, value TEXT NOT NULL UNIQUE);
CREATE TABLE surface (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, value TEXT NOT NULL UNIQUE, base_id INTEGER NOT NULL REFERENCES base(id), notes TEXT);
CREATE TABLE variant (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, value TEXT NOT NULL UNIQUE);
CREATE TABLE surface_variant (surface_id INTEGER NOT NULL, variant_id INTEGER NOT NULL, PRIMARY KEY (surface_id, variant_id));
CREATE TABLE surface_extra_base (surface_id INTEGER NOT NULL, base_id INTEGER NOT NULL, PRIMARY KEY (surface_id, base_id));
''')
O.executemany('insert into base values (?,?)', sorted(base.items()))
O.executemany('insert into surface values (?,?,?,null)', [(newid[o], v, b) for o,(v,b) in items])
O.executemany('insert into variant values (?,?)', sorted(variant.items()))
O.executemany('insert into surface_variant values (?,?)', sorted((newid[s],v) for s,v in sv))
O.executemany('insert into surface_extra_base values (?,?)', sorted((newid[s],b) for s,b in extra if b in base))
O.executescript('''CREATE INDEX surface_base_id_index ON surface(base_id);
CREATE INDEX surface_variant_variant_id_index ON surface_variant(variant_id);
CREATE INDEX surface_extra_base_base_id_index ON surface_extra_base(base_id); ANALYZE;''')
O.commit(); O.execute('vacuum'); O.close()
stats.update(added); stats['size_mb'] = round(os.path.getsize(out)/1e6,1)
print(os.path.basename(out), stats)
