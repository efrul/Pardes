"""A line cut into passages exactly as the app cuts it: SeforimApp's features/search/semantic/Passages.kt, which picks
the passage a search by meaning highlights. Here it gives the passages of a long line their own vectors, to measure
whether a long line found by its best passage ranks better (README.md).

Cut after . : ; ? ! and sof pasuq, then after the commas of a sentence of more than MAX_WORDS words, then by words
when still long; a piece of fewer than MIN_WORDS words is joined to the next one (or to the last one, at the end).
The same rules, word count and entity test as the Kotlin; test_passages.py holds PassagesTest.kt's cases.
"""

import unicodedata

MIN_WORDS = 4
MAX_WORDS = 30
MAX_ENTITY = 10
SENTENCE_ENDS = frozenset(".:;?!׃")
CLAUSE_ENDS = frozenset(",،")


def _letter_or_digit(c: str) -> bool:
    """Kotlin's Char.isLetterOrDigit(): a letter (L*) or a decimal digit (Nd); niqqud (Mn) is neither."""
    category = unicodedata.category(c)
    return category[0] == "L" or category == "Nd"


def _entity_name(c: str) -> bool:
    return "a" <= c <= "z" or "A" <= c <= "Z" or "0" <= c <= "9" or c == "#"


def _closes_entity(text: str, i: int) -> bool:
    """Whether the ';' at [i] ends an HTML entity (&nbsp; &amp; &#1488;): no sentence ends there."""
    if text[i] != ";":
        return False
    j = i - 1
    while j >= 0 and i - j <= MAX_ENTITY and _entity_name(text[j]):
        j -= 1
    return 0 <= j < i - 1 and text[j] == "&"


def _cut_after(text: str, first: int, last: int, ends: frozenset) -> list[tuple[int, int]]:
    """[first, last] (inclusive, as Kotlin's IntRange) cut after each of [ends]."""
    out, start = [], first
    for i in range(first, last + 1):
        if text[i] in ends and not _closes_entity(text, i):
            out.append((start, i))
            start = i + 1
    if start <= last:
        out.append((start, last))
    return out


def _words(text: str, first: int, last: int) -> int:
    count, in_word = 0, False
    for i in range(first, last + 1):
        word = _letter_or_digit(text[i])
        if word and not in_word:
            count += 1
        in_word = word or (in_word and not text[i].isspace())
    return count


def _by_words(text: str, first: int, last: int) -> list[tuple[int, int]]:
    """Even pieces of MAX_WORDS words at most, fewer than MIN_WORDS left over staying with the rest."""
    starts = [i for i in range(first, last + 1)
              if not text[i].isspace() and (i == first or text[i - 1].isspace())]
    pieces = (len(starts) - MIN_WORDS + MAX_WORDS) // MAX_WORDS
    if pieces <= 1:
        return [(first, last)]
    n = len(starts)
    return [(first if p == 0 else starts[p * n // pieces],
             last if p == pieces - 1 else starts[(p + 1) * n // pieces] - 1) for p in range(pieces)]


def _join_short(text: str, pieces: list[tuple[int, int]]) -> list[tuple[int, int]]:
    out, pending = [], None
    for piece in pieces:
        current = (pending[0], piece[1]) if pending else piece
        if _words(text, *current) < MIN_WORDS:
            pending = current
        else:
            out.append(current)
            pending = None
    if pending is not None:
        if out:
            out[-1] = (out[-1][0], pending[1])
        else:
            out.append(pending)
    return out


def split(text: str) -> list[tuple[int, int]]:
    """The passages of [text], as slices (start, end) of it in order, without the spaces around them."""
    pieces = []
    for sentence in _cut_after(text, 0, len(text) - 1, SENTENCE_ENDS):
        if _words(text, *sentence) <= MAX_WORDS:
            pieces.append(sentence)
            continue
        for clause in _cut_after(text, *sentence, CLAUSE_ENDS):
            pieces.extend([clause] if _words(text, *clause) <= MAX_WORDS else _by_words(text, *clause))
    out = []
    for first, last in _join_short(text, pieces):
        while first <= last and text[first].isspace():
            first += 1
        while last >= first and text[last].isspace():
            last -= 1
        if first <= last:
            out.append((first, last + 1))
    return out


def passages(text: str) -> list[str]:
    return [text[a:b] for a, b in split(text)]
