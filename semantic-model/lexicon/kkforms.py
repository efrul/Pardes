# word list (from query files) -> TSV: word \t infl forms \t syn forms \t ketiv forms (Klei Kodesh data), normalized like Pardes
import sqlite3, sys, re, csv
EXP, DIC, out = sys.argv[1], sys.argv[2], sys.argv[3]
queries = sys.argv[4:]
FIN = str.maketrans('ךםןףץ', 'כמנפצ')
def norm(s):
    s = re.sub('[֑-ׇ]', '', s).replace('״','').replace('׳','').replace('"','').replace("'",'')
    return s.translate(FIN)
words = []
seen = set()
for q in queries:
    with open(q, encoding='utf-8') as f:
        r = csv.DictReader(f, delimiter='\t', quoting=csv.QUOTE_NONE)
        for row in r:
            for w in re.findall(r'[א-ת"\'׳״]+', row['query']):
                if '"' in w[1:-1] or "'" in w[1:-1] or '״' in w: continue  # acronyms
                n = norm(w)
                if len(n) >= 2 and n not in seen: seen.add(n); words.append((w, n))
e = sqlite3.connect('file:'+EXP+'?mode=ro', uri=True)
d = sqlite3.connect('file:'+DIC+'?mode=ro', uri=True)
# fold surfaces are stored with finals; build normalized lookup on demand
def fold(n):
    rows = e.execute("select lemma, weight, source from fold where surface in (?,?)", (n, n[:-1]+{'כ':'ך','מ':'ם','נ':'ן','פ':'ף','צ':'ץ'}.get(n[-1], n[-1]))).fetchall()
    return rows
with open(out, 'w', encoding='utf-8') as o:
    for w, n in words:
        lem = fold(n)
        infl, syn = [], []
        for lemma, wt, src in lem:
            for form, ch, s2 in e.execute("select form, channel, source from exp where lemma=? order by rank", (lemma,)):
                f = norm(form)
                if ch in ('infl', 'bridge'): infl.append(f)
                elif ch == 'syn' and s2 in ('tanach', 'word-thesaurus'): syn.append(f)
            infl.append(norm(lemma))
        ket = [norm(h) for (h,) in d.execute("""select b.headword from link l join word a on a.id=l.word_id join word b on b.id=l.target_id
             where l.kind_id=2 and a.headword in (?,?)""", (n, w))]
        dd = lambda xs: ' '.join(dict.fromkeys(x for x in xs if x and x != n))
        o.write(f"{n}\t{dd(infl)}\t{dd(syn)}\t{dd(ket)}\t{'|'.join(f'{l}:{wt}:{s}' for l,wt,s in lem)}\n")
print(len(words))
