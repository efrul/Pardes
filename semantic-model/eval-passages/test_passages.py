"""passages.py against the cases of SeforimApp's PassagesTest.kt (the cut itself, not bestPassages):

    python test_passages.py      (or pytest)
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from passages import passages, split  # noqa: E402


def test_sentences_verbatim():
    text = "(א) אמר רבי יוחנן: כל המתפלל צריך שיתפלל בכוונה. ואם לא כיון, יחזור ויתפלל."
    assert passages(text) == ["(א) אמר רבי יוחנן:", "כל המתפלל צריך שיתפלל בכוונה.", "ואם לא כיון, יחזור ויתפלל."]
    verse = "בְּרֵאשִׁית בָּרָא אֱלֹהִים אֵת הַשָּׁמַיִם וְאֵת הָאָרֶץ׃"
    assert passages(verse) == [verse]


def test_short_pieces_join():
    assert passages("שאלה: מה? תשובה: כך וכך היא ההלכה.") == ["שאלה: מה? תשובה: כך וכך היא ההלכה."]
    assert passages("ואלו דברים שאין להם שיעור הפאה והבכורים. וגמילות חסדים ותלמוד תורה: כנגד כולם.") == [
        "ואלו דברים שאין להם שיעור הפאה והבכורים.", "וגמילות חסדים ותלמוד תורה: כנגד כולם."]


def test_long_sentence_commas_then_words():
    clause = " ".join(f"מילה{i}" for i in range(1, 21))
    assert len(passages(f"{clause}, {clause}, {clause}.")) == 3
    for words, sizes in [(75, [25, 25, 25]), (34, [17, 17]), (64, [21, 21, 22]), (33, [33]), (63, [31, 32]),
                         (30, [30])]:
        bare = " ".join(f"מילה{i}" for i in range(1, words + 1))
        cut = passages(bare)
        assert [len(p.split(" ")) for p in cut] == sizes, words
        assert " ".join(cut) == bare


def test_entity_semicolon():
    assert passages("אמר להם משה רבנו&nbsp;עליו השלום דברים אלו. ויענו כל העם יחד ויאמרו נעשה &amp; נשמע.") == [
        "אמר להם משה רבנו&nbsp;עליו השלום דברים אלו.", "ויענו כל העם יחד ויאמרו נעשה &amp; נשמע."]
    assert len(passages("אמר להם משה רבנו דברים אלו; ויענו כל העם יחד ויאמרו נעשה")) == 2


def test_cover_in_order_trimmed():
    text = "  ויאמר: לא כן.   ויען ויאמר; כן הוא באמת, ואין לשנות דבר!  "
    ranges = split(text)
    assert all(a[1] <= b[0] for a, b in zip(ranges, ranges[1:]))
    assert all(text[a:b] == text[a:b].strip() for a, b in ranges)


if __name__ == "__main__":
    for name, test in list(globals().items()):
        if name.startswith("test_"):
            test()
            print(f"ok {name}")
