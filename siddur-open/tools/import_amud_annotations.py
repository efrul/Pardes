"""Export unambiguous Amud conditions for whole paragraphs and balanced HTML fragments."""

import argparse
from collections import defaultdict
import hashlib
import html
import json
from pathlib import Path
import re
import sqlite3


BOOKS = {
    "ashkenaz": ("ASHKENAZ", "סידור אשכנז"),
    "sefard": ("SEFARD", "סידור ספרד"),
    "edot_hamizrach": ("EDOT_HAMIZRACH", "סידור נוסח עדות המזרח"),
}

FLAGS = {
    "aseretYemeiTeshuva": "aseret_yemei_teshuva",
    "chanukah": "chanuka",
    "cholHamoed": "chol_hamoed",
    "diaspora": "!israel",
    "halfHallel": "chatzi_hallel",
    "il": "israel",
    "mashivHaruach": "mashiv_haruach",
    "minyan": "minyan",
    "moridHatal": "morid_hatal",
    "omer": "omer",
    "purim": "purim",
    "roshChodesh": "rosh_chodesh",
    "shabbat": "sat",
    "talUmatar": "tal_umatar",
    "torahReading": "torah_reading",
    "wholeHallel": "hallel_shalem",
    "weekday": "weekday",
    "motzaeiShabbat": "motzaei_shabbat",
    "avinuMalkeinu": "avinu_malkenu",
    "yaalehVeyavo": "yaale_veyavo",
    "alHanissim": "al_hanisim",
}


def hebrew_letters(text):
    return re.sub(r"[^א-ת]", "", html.unescape(re.sub(r"<[^>]+>", "", text)))


def digest(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def fingerprint(text):
    letters = hebrew_letters(text)
    if len(letters) < 24:
        return None
    return digest(letters)


def condition(expression):
    """Preserve OR clauses and AND terms. Reject other syntax and unknown facts."""
    alternatives = []
    for clause in expression.split("||"):
        terms = []
        for term in clause.split("&&"):
            term = term.strip()
            if term == "true":
                continue
            flag = FLAGS.get(term.lstrip("!"))
            if flag is None:
                return None
            terms.append(("!" + flag if not flag.startswith("!") else flag[1:]) if term.startswith("!") else flag)
        if not terms:
            return ""
        alternatives.append("&".join(sorted(set(terms))))
    return "|".join(sorted(set(alternatives)))


def with_minyan(expression):
    if expression is None:
        return None
    return "|".join("&".join(sorted(set(filter(None, clause.split("&"))) | {"minyan"}))
                    for clause in expression.split("|"))


def annotations(directory):
    rules = defaultdict(set)
    for path in sorted(directory.glob("*.json")):
        for leaf in json.loads(path.read_text(encoding="utf-8")).get("leaves", ()):
            leaf_condition = leaf.get("when", "true")
            for segment in leaf.get("he", {}).get("segs", ()):
                parts = segment.get("parts", (segment,))
                key = fingerprint("".join(part["text"] for part in parts))
                if key is None:
                    continue
                split = []
                for part in parts:
                    part_condition = part.get("when", "true")
                    # An OR inherited at one level would need parentheses to combine it with the other.
                    if leaf_condition == "true":
                        rule = condition(part_condition)
                    elif part_condition == "true":
                        rule = condition(leaf_condition)
                    elif "||" not in leaf_condition and "||" not in part_condition:
                        rule = condition(leaf_condition + " && " + part_condition)
                    else:
                        rule = None
                    length = len(hebrew_letters(part["text"]))
                    if length:
                        split.append((length, with_minyan(rule) if part.get("minyan") else rule))
                rules[key].add(tuple(split))
    return rules


TAG = re.compile(r"<(/?)([a-z][\w-]*)\b[^>]*>", re.IGNORECASE)
VOID = {"br", "wbr", "hr", "img", "input", "meta", "link"}


def fragments(source):
    """Yield only top-level complete markup units and the text between them."""
    stack = []
    start = 0
    result = []
    for tag in TAG.finditer(source):
        closing, name = tag[1], tag[2].lower()
        if not stack and start != tag.start():
            result.append((start, tag.start()))
            start = tag.start()
        if closing:
            if not stack or stack.pop() != name:
                return []
        elif name not in VOID and not tag[0].endswith("/>"):
            stack.append(name)
        if not stack:
            result.append((start, tag.end()))
            start = tag.end()
    if stack:
        return []
    if start != len(source):
        result.append((start, len(source)))
    return result


def utf16_offset(source, offset):
    return len(source[:offset].encode("utf-16-le")) // 2


def inline_rules(source, parts):
    conditions = [rule for length, rule in parts for _ in range(length)]
    cursor = 0
    for start, end in fragments(source):
        length = len(hebrew_letters(source[start:end]))
        choices = set(conditions[cursor:cursor + length])
        cursor += length
        if len(choices) == 1 and (rule := choices.pop()):
            yield f"{utf16_offset(source, start)}:{utf16_offset(source, end)}:{rule}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("amud", type=Path, help="Path to the Amud source checkout")
    parser.add_argument("database", type=Path, help="Path to seforim.db")
    args = parser.parse_args()
    output = Path(__file__).resolve().parents[1] / "src/jvmMain/resources/open-siddur/amud-annotations.tsv"
    rows = []
    inline = []
    with sqlite3.connect(args.database.resolve().as_uri() + "?mode=ro&immutable=1", uri=True) as database:
        for folder, (nusach, title) in BOOKS.items():
            corpus = database.execute(
                "SELECT l.content FROM line l JOIN book b ON b.id = l.bookId WHERE b.title = ?", (title,),
            )
            by_key = defaultdict(list)
            for (text,) in corpus:
                key = fingerprint(text)
                if key is not None:
                    by_key[key].append(text)
            for key, choices in sorted(annotations(args.amud / "corpus" / "siddur" / folder).items()):
                originals = by_key[key]
                if len(originals) != 1:
                    continue
                all_rules = {rule for parts in choices for _, rule in parts}
                if len(all_rules) == 1:
                    if rule := all_rules.pop():
                        rows.append(f"{nusach}\t{key}\t{rule}")
                elif len(choices) == 1:
                    source = originals[0]
                    spans = list(inline_rules(source, next(iter(choices))))
                    if spans:
                        inline.append(f"{nusach}\t{digest(source)}\t" + ";".join(spans))
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8", newline="\n")
    output.with_name("amud-inline.tsv").write_text("\n".join(inline) + "\n", encoding="utf-8", newline="\n")
    print(f"{len(rows)} whole-paragraph annotations and {len(inline)} paragraphs with tagged fragments")


if __name__ == "__main__":
    main()
