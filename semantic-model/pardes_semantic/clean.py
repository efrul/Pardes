"""The text cleaning applied before the tokenizer, on queries and on the corpus alike.

`OpenModelCleaner.kt` in the app is the same function in Kotlin; `parity/clean_cases.jsonl` (written by
make_parity_fixtures.py) holds the expected output of both. Any change here is a new CLEANER_ID: vectors indexed
with one cleaner are not comparable with queries cleaned by another, so the manifest of the model and of the vector
index both name it and the app refuses a mismatch.

The tokenizer (tokenizer.json) already does NFKC, lowercasing and the removal of niqqud and teamim (StripAccents), so
the cleaner only does what it cannot: HTML, entities, the Hebrew geresh/gershayim (which the tokenizer's acronym rule
only recognises as ASCII ' and "), and whitespace. Final letters are kept: the model was trained on ordinary text.
"""

import re

CLEANER_ID = "pardes-clean-1"

_TAG = re.compile(r"<[^>]*>")
_ENTITY = re.compile(r"&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|amp|lt|gt|quot|apos|nbsp);")
# Spelled out rather than \s: Python's and Java's \s do not agree on the Unicode spaces
_SPACES = re.compile("[\t\n\u000b\f\r \u0085   -     　﻿]+")
_NAMED = {"amp": "&", "lt": "<", "gt": ">", "quot": '"', "apos": "'", "nbsp": " "}


def _entity(m: "re.Match[str]") -> str:
    name = m.group(1)
    if name[0] != "#":
        return _NAMED[name]
    code = int(name[2:], 16) if name[1] in "xX" else int(name[1:])
    # An invalid or surrogate code point stays as written, as Kotlin cannot build it either
    if code > 0x10FFFF or 0xD800 <= code <= 0xDFFF or code == 0:
        return m.group(0)
    return chr(code)


def clean(text: str) -> str:
    text = _TAG.sub(" ", text)
    text = _ENTITY.sub(_entity, text)
    text = text.replace("״", '"').replace("׳", "'")
    # strip(" "), not strip(): Python's strip() also drops characters (\x1c-\x1f) that the spaces above leave
    return _SPACES.sub(" ", text).strip(" ")
