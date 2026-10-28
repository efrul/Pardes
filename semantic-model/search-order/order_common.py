"""What the scripts here share: HybridSearchEngine's fusion offline (RRF, verbatimKey, holdsQuote, keepLeaders), the
words of a line as the model's cleaner leaves them, and the grades and expected sources of eval-real and eval-100."""

import re
import sqlite3
import sys
from collections import defaultdict
from pathlib import Path

SM = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SM))
from pardes_semantic.clean import clean  # noqa: E402

RRF_K = 60
DENSE_WEIGHT = 3.0
LEADER_PLACES = (1, 3)  # 0-based: places 2 and 4

_BLOCK, _TAG, _ENT = re.compile(r"<br\s*/?>|</p>|</div>", re.I), re.compile(r"<[^>]*>"), re.compile(r"&#?\w+;")
_MARKS = set(range(0x0591, 0x05BE)) | {0x05BF, 0x05C1, 0x05C2, 0x05C4, 0x05C5, 0x05C7}
_FINALS = str.maketrans("ךםןףץ", "כמנפצ")
_PREFIXES = "והבכלמש"


def verbatim_key(s: str) -> str:
    """HybridSearchEngine.verbatimKey: letters and digits, single spaces; markup (a tag inside a word leaves it whole),
    niqqud, teamim, gershayim and quotes removed, maqaf and punctuation as spaces, final letters as base forms."""
    s = _ENT.sub(" ", _TAG.sub("", _BLOCK.sub(" ", s)))
    out, space = [], True
    for c in s:
        if ord(c) in _MARKS or c in "׳״\"'":
            continue
        if c.isalnum():
            out.append(c.lower().translate(_FINALS))
            space = False
        elif not space:
            out.append(" ")
            space = True
    return "".join(out).rstrip()


def stem(w: str) -> str:
    for _ in range(2):
        if len(w) > 3 and w[0] in _PREFIXES:
            w = w[1:]
    return w


def holds_quote(text: str, query: str) -> bool:
    """HybridSearchEngine.holdsQuote: all the query's words (two at least), prefixes off, within a window of the
    query's length plus 2 words, in any order."""
    k = verbatim_key(query)
    if not k or k.count(" ") + 1 < 2:
        return False
    words = [stem(w) for w in k.split()]
    wanted, line, window = set(words), [stem(w) for w in verbatim_key(text).split()], len(words) + 2
    return any(line[i] in wanted and wanted <= set(line[i:i + window]) for i in range(len(line)))


def holds_verbatim(text: str, query: str) -> bool:
    """The query word for word, in order, in the line (verbatimKey on both)."""
    k = verbatim_key(query)
    return bool(k) and k in verbatim_key(text)


def keep(fused, leaders):
    """HybridSearchEngine.keepLeaders: each (line, 0-based place) moved up to its place, or left where the fusion ranks
    it higher; one whose place an earlier one took comes right after it."""
    place_of = {}
    for d, wanted in leaders:
        at = fused.index(d) if d in fused else -1
        p = at if 0 <= at <= wanted else wanted
        while p in place_of.values():
            p += 1
        place_of[d] = p
    out = [d for d in fused if d not in place_of]
    for d, p in sorted(place_of.items(), key=lambda x: x[1]):
        out.insert(min(p, len(out)), d)
    return out


def rrf(lex, dense, lex_weight=lambda d: 1.0, dense_weight=DENSE_WEIGHT):
    score = defaultdict(float)
    for i, d in enumerate(lex):
        score[d] += lex_weight(d) / (RRF_K + 1 + i)
    for i, d in enumerate(dense):
        score[d] += dense_weight / (RRF_K + 1 + i)
    return [d for d, _ in sorted(score.items(), key=lambda x: -x[1])]


def app_fusion(query, lex, dense, text):
    """The app's page from its candidates: RRF w3, then the first two lexical hits that hold the query as a quote kept
    at places 2 and 4 (a side with no hits leaves the other's order)."""
    fused = rrf(lex, dense) if dense else list(lex)
    return keep(fused, [(d, LEADER_PLACES[j]) for j, d in enumerate(lex[:2]) if holds_quote(text(d), query)])


def ids(hits):
    """A list of lineIds from an Eval.kt list (dicts), a History.kt list ([lineId, score]) or plain lineIds."""
    return [h["lineId"] if isinstance(h, dict) else h[0] if isinstance(h, list) else h for h in hits]


class Lines:
    """Lines of seforim.db, read once: raw HTML, words (the model's cleaner, split on spaces), ref, base book, heading."""

    def __init__(self, db: str):
        self.con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
        self.rows = {}

    def load(self, line_ids):
        new = sorted({d for d in line_ids if d not in self.rows})
        for k in range(0, len(new), 900):
            chunk = new[k:k + 900]
            q = ("SELECT l.id, l.content, l.heRef, b.title, b.isBaseBook, l.bookId FROM line l JOIN book b ON b.id = l.bookId "
                 f"WHERE l.id IN ({','.join('?' * len(chunk))})")
            for lid, content, ref, title, base, book in self.con.execute(q, chunk):
                content = content or ""
                self.rows[lid] = dict(raw=content, words=len(clean(content).split()), ref=ref or title, book=book,
                                      title=title, base=bool(base), heading=bool(re.search(r"<h\d", content)),
                                      key=" ".join(verbatim_key(content).split()))
        return self

    def __getitem__(self, d):
        return self.rows.get(d, dict(raw="", words=0, ref="?", book=None, title="?", base=False, heading=False, key=""))

    def text(self, d):
        return self[d]["raw"]


def load_eval_real():
    sys.path.insert(0, str(SM / "eval-real"))
    from score import NOISE, dcg, load_grades  # noqa: E402
    return NOISE, dcg, load_grades(SM / "eval-real" / "grades.tsv")


def load_expected():
    sys.path.insert(0, str(SM / "eval-100"))
    from expected import E  # noqa: E402
    return E
