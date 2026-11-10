"""daf_release.py's versions: a tractate's goes up only in a release where its layout changed.

    python3 .github/scripts/test_daf_release.py      (or pytest)
"""

import gzip
import io
import json
import sys
import tempfile
from contextlib import redirect_stderr
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from daf_release import MANIFEST, build, packed  # noqa: E402


def layout(tractate: str, he: str, word: str) -> dict:
    line = {"c": "G", "b": [1.0, 2.0, 3.0, 4.0], "t": word, "s": 1}
    return {"format": 2, "tractate": tractate, "tractateHe": he,
            "amudim": [{"amud": "2a", "page": [600.0, 850.0], "block": [0.0, 0.0, 600.0, 850.0], "lines": [line]}],
            "library": {"release": "v1", "books": {"G": [1]}}}


def release(root: Path, number: int, layouts: list[dict], previous: Path | None = None) -> tuple[dict, Path]:
    """The release [number] of [layouts], built as the workflow builds it; its manifest and its files' folder."""
    source = root / f"layouts-{number}"
    source.mkdir()
    for one in layouts:
        (source / f"{one['tractate'].replace(' ', '')}.json").write_bytes(packed(one))
    out = root / f"out-{number}"
    with redirect_stderr(io.StringIO()):
        manifest = build(source, out, root / f"meta-{number}", number, previous=previous)
    return manifest, out


def by_name(manifest: dict) -> dict:
    return {e["tractate"]: e for e in manifest["tractates"]}


def test_a_tractates_version_goes_up_only_when_its_layout_changes():
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        berakhot, batra = layout("Berakhot", "ברכות", "מאימתי"), layout("Bava Batra", "בבא בתרא", "השותפין")
        first, out1 = release(root, 1, [berakhot, batra])
        assert {name: e["version"] for name, e in by_name(first).items()} == {"Berakhot": 1, "Bava Batra": 1}
        assert first["format"] == 1 and first["daf"] == 1

        # The version is at the head of the tractate's file, where the app reads it, and the file is the manifest's
        raw = gzip.decompress((out1 / "BavaBatra.json.gz").read_bytes())
        assert raw.startswith('{"format":2,"tractate":"Bava Batra","tractateHe":"בבא בתרא","version":1,'.encode())
        assert json.loads(raw)["amudim"] == batra["amudim"]

        # The next release changes one tractate: its version alone goes up, the other's file is the same bytes
        second, out2 = release(root, 2, [berakhot, layout("Bava Batra", "בבא בתרא", "שרצו")], previous=out1 / MANIFEST)
        one, two = by_name(first), by_name(second)
        assert (two["Berakhot"]["version"], two["Bava Batra"]["version"]) == (1, 2)
        assert two["Berakhot"]["jsonSha256"] == one["Berakhot"]["jsonSha256"]
        assert two["Berakhot"]["sha256"] == one["Berakhot"]["sha256"]
        assert (out2 / "Berakhot.json.gz").read_bytes() == (out1 / "Berakhot.json.gz").read_bytes()
        assert two["Bava Batra"]["jsonSha256"] != one["Bava Batra"]["jsonSha256"]
        notes = (root / "meta-2" / "notes.md").read_text("utf-8")
        assert "השתנו 1 מסכתות: בבא בתרא (גרסה 2)" in notes

        # A layout that comes with a version of its own (a released file brought back) is the same layout
        stamped = json.loads(gzip.decompress((out2 / "BavaBatra.json.gz").read_bytes()))
        assert stamped["version"] == 2
        third, _ = release(root, 3, [berakhot, stamped, layout("Keritot", "כריתות", "שלשים")], previous=out2 / MANIFEST)
        three = by_name(third)
        assert (three["Berakhot"]["version"], three["Bava Batra"]["version"], three["Keritot"]["version"]) == (1, 2, 1)
        assert three["Bava Batra"]["jsonSha256"] == two["Bava Batra"]["jsonSha256"]
        assert "השתנו 1 מסכתות: כריתות (גרסה 1)" in (root / "meta-3" / "notes.md").read_text("utf-8")


def test_a_release_after_one_without_versions_gives_every_tractate_its_first():
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        berakhot = layout("Berakhot", "ברכות", "מאימתי")
        # The releases before the versions: a manifest with no version and no contentSha256
        old = root / "old.json"
        old.write_text(json.dumps({"format": 1, "daf": 4, "tractates": [
            {"tractate": "Berakhot", "tractateHe": "ברכות", "file": "Berakhot.json.gz", "size": 1, "sha256": "a", "jsonSha256": "b"}]}))
        manifest, _ = release(root, 5, [berakhot], previous=old)
        assert by_name(manifest)["Berakhot"]["version"] == 1
        assert "לכל מסכת גרסה משלה" in (root / "meta-5" / "notes.md").read_text("utf-8")
        # And with no release before it at all
        assert by_name(release(root, 6, [berakhot], previous=root / "none.json")[0])["Berakhot"]["version"] == 1


if __name__ == "__main__":
    for name, test in list(globals().items()):
        if name.startswith("test_"):
            test()
            print(f"ok {name}")
