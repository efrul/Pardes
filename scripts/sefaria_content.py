"""Packs Sefaria's topics, sage mentions, sugyot and Krupnik's dictionary of the Talmud for Pardes for desktop, from
Sefaria's public database dump: sefaria-data/{topics,sages,sugyot,krupnik}.tsv.gz, from which
.github/workflows/sefaria-release.yml builds the pack the app downloads (SefariaPack).

    curl -sS https://storage.googleapis.com/sefaria-mongo-backup/dump_small.tar.gz | tar -xz --wildcards --no-anchored \
        index.bson topics.bson topic_links.bson passage.bson lexicon_entry.bson
    python3 -I scripts/sefaria_content.py <the dump's sefaria folder> <the library's seforim.db>

Needs pymongo, for its bson module; read as data, nothing of the dump runs. Sefaria states a licence for its links
(CC-BY) and for each text, not for its topics and passages: Pardes uses them crediting Sefaria (sefaria/NOTICE.txt)
while it asks. Krupnik's A Dictionary of the Talmud (London, 1927) is in the public domain, as Sefaria's version says.

A Sefaria ref ("Berakhot 2a:3") becomes the library's line.heRef ("ברכות ב., ג") by the rules of SeforimLibrary's
generator (SefariaBookPayloadReader, SefariaImportRefs): the Hebrew title, the numbers in gematria without geresh, a
daf's amud as "." or ":", separated by ", "; in a book of several parts "<title>, <part>,  " (two spaces) before the
numbers, and the main part without its title. A ref the library has no line for is left out.

Rows, tab separated, by their first field:
  topics.tsv.gz
    T  id  name  other names (|)  description          (a topic with a Hebrew name; ids shared with sages.tsv.gz)
    R  from  kind  to                                   (under, isa, related, similar, contains, precedes, depends)
    O  id  order                                        (a root of the tree, in Sefaria's order)
    A  topic  heRef  rank  first                        (a line a topic is about; first: 1 on the first line of a
                                                         source, whose rank orders the topic's sources, 0 on the rest)
  sages.tsv.gz
    P  id  name  other names (|)  description  generation  heWiki   (a person mentioned in the library's lines)
    W  ambiguous id  name  option ids (|)               (a mention Sefaria could not tell between persons)
    R  from  kind  to                                   (taught, child, member)
    M  heRef  person  text                              (a mention; text: the name as written, empty when Sefaria
                                                         found it only in a translation)
  sugyot.tsv.gz
    S  kind  source  heRefs (|)                         (kind: S sugya, M a Mishnah in the Gemara, B a story; its
                                                         lines in their order)
  krupnik.tsv.gz
    K  headword  other headwords (|)  text
"""

import gzip
import re
import sqlite3
import sys
from collections import defaultdict
from pathlib import Path

import bson

OUT = Path(__file__).resolve().parent.parent / "sefaria-data"
TOPIC_KINDS = {"displays-under": "under", "is-a": "isa", "related-to": "related", "similar-to": "similar",
               "temporally-contains": "contains", "precedes": "precedes", "specifically-dependent-on": "depends"}
PERSON_KINDS = {"taught": "taught", "child-of": "child", "member-of": "member"}
PASSAGE_KINDS = {"Sugya": "S", "Mishnah": "M", "biblical-story": "B"}
SOURCES = {"Steinsaltz": "steinsaltz", "Kehot Chumash": "kehot"}
NIKUD = re.compile(r"[֑-ׇ]")
TAG = re.compile(r"<[^>]+>")
LINK = re.compile(r"\[([^\]]*)\]\([^)]*\)")
# Sefaria writes רבי short without its geresh (ר יהושע): ר' as the library writes it
RABBI = re.compile(r"(^|\s)ר(?=\s)")
HEBREW = re.compile(r"[א-ת]")
NOT_PERSONS = {"jewish-people", "israel", "the-tetragrammaton"}


def gematria(n):
    """SefariaImportRefs.toGematria."""
    if n <= 0:
        return str(n)
    out = []
    thousands, r = divmod(n, 1000)
    if thousands:
        out.append(gematria(thousands) + " ")
    for value, letter in ((400, "ת"), (300, "ש"), (200, "ר"), (100, "ק")):
        while r >= value:
            out.append(letter)
            r -= value
    if r in (15, 16):
        out.append("טו" if r == 15 else "טז")
        r = 0
    for value, letter in ((90, "צ"), (80, "פ"), (70, "ע"), (60, "ס"), (50, "נ"), (40, "מ"), (30, "ל"), (20, "כ"), (10, "י")):
        if r >= value:
            out.append(letter)
            r -= value
    for value, letter in ((9, "ט"), (8, "ח"), (7, "ז"), (6, "ו"), (5, "ה"), (4, "ד"), (3, "ג"), (2, "ב"), (1, "א")):
        if r >= value:
            out.append(letter)
            r -= value
    return "".join(out)


def hebrew_title(node):
    if node.get("heTitle"):
        return node["heTitle"]
    he = [t for t in node.get("titles") or [] if t.get("lang") == "he"]
    primary = [t for t in he if t.get("primary")]
    return (primary or he or [{}])[0].get("text")


class Refs:
    """Sefaria refs to the library's heRefs."""

    def __init__(self, index_path):
        self.books = {}
        for index in bson.decode_file_iter(open(index_path, "rb")):
            self.books[index.get("title")] = index
            for title in (index.get("schema") or {}).get("titles") or []:
                if title.get("lang") == "en":
                    self.books.setdefault(title.get("text"), index)
        self.titles = sorted(self.books, key=len, reverse=True)
        self.cache = {}

    def he_ref(self, ref):
        if ref not in self.cache:
            self.cache[ref] = self._he_ref(ref.strip())
        return self.cache[ref]

    def _he_ref(self, ref):
        title = next((t for t in self.titles if ref.startswith(t) and ref[len(t):len(t) + 1] in (" ", ",")), None)
        if not title:
            return None
        index = self.books[title]
        schema = index.get("schema") or {}
        he_title = hebrew_title(schema) or index.get("heTitle")
        match = re.match(r"^(.*?)\s*(\d[\w:]*)$", ref[len(title):].lstrip(", ").strip())
        if not he_title or not match:
            return None
        part, address = match.group(1).strip(" ,"), match.group(2)
        if schema.get("nodes"):
            node = None
            for candidate in schema["nodes"]:
                names = {candidate.get("title")} | {t.get("text") for t in candidate.get("titles") or [] if t.get("lang") == "en"}
                if (part and part in names) or (not part and (candidate.get("key") or "").lower() == "default"):
                    node = candidate
            if node is None:
                return None
            node_title = hebrew_title(node)
            named = (node.get("key") or "").lower() != "default" and node_title
            prefix = f"{he_title}, " + (f"{node_title}, " if named else "") + " "
            types = node.get("addressTypes") or []
        else:
            if part:
                return None
            prefix = f"{he_title} "
            types = schema.get("addressTypes") or []
        letters = []
        for depth, piece in enumerate(address.split(":")):
            if (types[depth] if depth < len(types) else "Integer") == "Talmud":
                daf = re.match(r"^(\d+)([ab])$", piece)
                if not daf:
                    return None
                letters.append(gematria(int(daf.group(1))) + ("." if daf.group(2) == "a" else ":"))
            elif piece.isdigit():
                letters.append(gematria(int(piece)))
            else:
                return None
        return prefix + ", ".join(letters)


def clean(text):
    """One line of plain text: markdown links and brackets kept as their words, tags and tabs out."""
    text = LINK.sub(r"\1", TAG.sub("", text or ""))
    text = re.sub(r"\[([^\]]*)\]", r"\1", text)
    return re.sub(r"\s+", " ", text).strip()


def definitions(content):
    """An entry's definitions, wherever its content keeps them: a text, senses, or senses within senses."""
    if isinstance(content, str):
        return [content]
    if isinstance(content, list):
        return [d for item in content for d in definitions(item)]
    if isinstance(content, dict):
        own = [content["definition"]] if isinstance(content.get("definition"), str) else []
        return own + definitions(content.get("senses") or [])
    return []


def names(topic):
    # A Hebrew title of no Hebrew letter (an English one marked Hebrew) is left out
    titles = [t for t in topic.get("titles") or [] if t.get("lang") == "he" and HEBREW.search(t.get("text") or "")]
    he = [RABBI.sub(r"\1ר'", t["text"]) for t in titles]
    primary = [RABBI.sub(r"\1ר'", t["text"]) for t in titles if t.get("primary")]
    name = (primary or he or [None])[0]
    others = []
    for other in he:
        if other != name and NIKUD.sub("", other) != NIKUD.sub("", name or "") and other not in others:
            others.append(other)
    return name, others


def rows_out(name, rows):
    OUT.mkdir(parents=True, exist_ok=True)
    with gzip.open(OUT / name, "wt", encoding="utf-8", compresslevel=9) as f:
        for row in rows:
            f.write("\t".join(str(field).replace("\t", " ").replace("\n", " ") for field in row) + "\n")
    print(f"{name}: {len(rows)} rows, {(OUT / name).stat().st_size // 1024} KB")


def main(dump, db):
    dump = Path(dump)
    refs = Refs(dump / "index.bson")
    with sqlite3.connect(f"file:{db}?mode=ro", uri=True) as con:
        library = {row[0] for row in con.execute("select heRef from line where heRef is not null")}

    def line(ref):
        he = refs.he_ref(ref)
        return he if he in library else None

    topics = {t["slug"]: t for t in bson.decode_file_iter(open(dump / "topics.bson", "rb"))}
    about = defaultdict(list)
    mentions = defaultdict(set)
    intra = []
    for link in bson.decode_file_iter(open(dump / "topic_links.bson", "rb")):
        if link.get("class") == "intraTopic":
            intra.append((link.get("fromTopic"), link.get("linkType"), link.get("toTopic")))
            continue
        if link.get("is_sheet"):
            continue
        lines = [line(r) for r in link.get("expandedRefs") or [link.get("ref")]]
        if not lines or not all(lines):
            continue
        if link.get("linkType") == "about":
            order = link.get("order") or {}
            curated = (order.get("curatedPrimacy") or {}).get("he") or 0
            about[link["toTopic"]].append((-curated, -(order.get("pr") or 0), -(order.get("tfidf") or 0), lines))
        elif link.get("linkType") == "mention":
            data = link.get("charLevelData") or {}
            mentions[link["toTopic"]].add((lines[0], (data.get("text") or "") if data.get("language") == "he" else ""))

    # The tree: Sefaria's top level but its authors (Pardes has its own author pages), under the roots what shows under
    # them, and the topics of the library's lines.
    roots = sorted((t for t in topics.values() if t.get("isTopLevelDisplay") and t["slug"] != "authors"),
                   key=lambda t: t.get("displayOrder") or 0)
    under = defaultdict(set)
    for a, kind, b in intra:
        if kind == "displays-under" and a != b:
            under[b].add(a)
    shown = set()
    pending = [r["slug"] for r in roots]
    while pending:
        slug = pending.pop()
        if slug not in shown:
            shown.add(slug)
            pending.extend(under.get(slug, ()))
    keep = [s for s in topics if names(topics[s])[0] and (about.get(s) or s in shown)]
    # The mentions that are no person: the people of Israel, and the Name, a link at each of them in the Talmud
    persons = [s for s in topics if names(topics[s])[0] and mentions.get(s) and "(ambiguous)" not in s and s not in NOT_PERSONS]
    ambiguous = [s for s in topics if "(ambiguous)" in s and mentions.get(s)]
    ids = {slug: n for n, slug in enumerate(dict.fromkeys(keep + persons + ambiguous))}

    t_rows, o_rows, a_rows = [], [], []
    for slug in keep:
        name, others = names(topics[slug])
        t_rows.append(("T", ids[slug], name, "|".join(others[:8]), clean((topics[slug].get("description") or {}).get("he"))))
        seen = set()
        for rank, (*_, lines) in enumerate(sorted(about.get(slug, []))):
            for k, he in enumerate(lines):
                if he not in seen:
                    seen.add(he)
                    a_rows.append(("A", ids[slug], he, rank, 1 if k == 0 else 0))
    for order, root in enumerate(roots):
        if root["slug"] in ids:
            o_rows.append(("O", ids[root["slug"]], order))
    r_rows = sorted({("R", ids[a], TOPIC_KINDS[k], ids[b]) for a, k, b in intra
                     if k in TOPIC_KINDS and a in ids and b in ids and a != b and a in keep and b in keep})
    rows_out("topics.tsv.gz", t_rows + o_rows + r_rows + a_rows)

    p_rows, w_rows, m_rows = [], [], []
    for slug in persons:
        name, others = names(topics[slug])
        props = topics[slug].get("properties") or {}
        p_rows.append(("P", ids[slug], name, "|".join(others[:8]), clean((topics[slug].get("description") or {}).get("he")),
                       (props.get("generation") or {}).get("value") or "", (props.get("heWikiLink") or {}).get("value") or ""))
    options = defaultdict(list)
    for a, kind, b in intra:
        if kind == "possibility-for" and a in ids and b in ids:
            options[b].append(ids[a])
    for slug in ambiguous:
        name, _ = names(topics[slug])
        w_rows.append(("W", ids[slug], name, "|".join(str(i) for i in options.get(slug, []))))
    pr_rows = sorted({("R", ids[a], PERSON_KINDS[k], ids[b]) for a, k, b in intra
                      if k in PERSON_KINDS and a in ids and b in ids and a != b})
    for slug in persons + ambiguous:
        written = {he for he, text in mentions[slug] if text}
        for he, text in sorted(mentions[slug]):
            if text or he not in written:
                m_rows.append(("M", he, ids[slug], text))
    rows_out("sages.tsv.gz", p_rows + w_rows + pr_rows + m_rows)

    s_rows = []
    for passage in bson.decode_file_iter(open(dump / "passage.bson", "rb")):
        lines = [line(r) for r in passage.get("ref_list") or []]
        if lines and all(lines):
            s_rows.append(("S", PASSAGE_KINDS[passage["type"]], SOURCES.get(passage.get("source"), passage.get("source")),
                           "|".join(lines)))
    rows_out("sugyot.tsv.gz", s_rows)

    k_rows = []
    for entry in bson.decode_file_iter(open(dump / "lexicon_entry.bson", "rb")):
        if entry.get("parent_lexicon") != "Krupnik Dictionary":
            continue
        text = clean(" ".join(definitions(entry.get("content"))))
        if entry.get("headword") and text:
            others = [h.get("word") if isinstance(h, dict) else h for h in entry.get("alt_headwords") or []]
            others = [h for h in others if h and h != entry["headword"]]
            k_rows.append(("K", entry["headword"], "|".join(others), text))
    rows_out("krupnik.tsv.gz", k_rows)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
