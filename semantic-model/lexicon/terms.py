import sqlite3, sys, re
FIN = str.maketrans('ךםןףץ', 'כמנפצ')
def norm(s):
    s = re.sub('[֑-ׇ]', '', s).replace('״','').replace('׳','')
    return re.sub(r'\s+',' ',s.translate(FIN)).strip()
lex, exp, dic, out = sys.argv[1:5]
T=set()
l=sqlite3.connect('file:'+lex+'?mode=ro',uri=True)
for t in ('surface','variant','base'):
    T.update(norm(v) for (v,) in l.execute(f'select value from {t}'))
e=sqlite3.connect('file:'+exp+'?mode=ro',uri=True)
T.update(norm(v) for (v,) in e.execute('select form from exp'))
T.update(norm(v) for (v,) in e.execute('select surface from fold'))
d=sqlite3.connect('file:'+dic+'?mode=ro',uri=True)
T.update(norm(v) for (v,) in d.execute('select headword from word'))
T={t for t in T if t and ' ' not in t}
open(out,'w',encoding='utf-8').write('\n'.join(sorted(T))+'\n'); print(len(T))
