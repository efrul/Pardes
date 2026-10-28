"""The semantic pack the app installs: the query model and the library's vector index as one .tar.zst, in the format
SeforimLibrary packages the library in (generator/packaging's PackageArtifacts), so that the app's BundleManifest and
BundleParts read it: `<name>.tar.zst`, or when it is larger than PART_BYTES, `<name>.tar.zst.partNN` from 01, all of one
size but a strictly smaller last one, and `<name>.tar.zst.manifest.json` (formatVersion 1: the bundle's size and sha256,
each file's). The manifest also says what the pack holds: its number, the model's id and dimension, the library
release the index was built on, and its lines.

    python package_pack.py --model build/model-student --index pardes.vec.lucene --library v3-20261012193827 \
        --pack 2 --out build/pack
    python package_pack.py --verify build/pack --model build/model-student --index pardes.vec.lucene

The archive holds pardes-model/ (pardes-model.json, the ONNX model, tokenizer.json, and NOTICE.md from here) and
pardes.vec.lucene/ (without its write.lock), as they go next to seforim.db. The bundle is written as a stream (tar,
zstd, then the parts), so it never needs room for itself twice; --verify unpacks it as a stream too, checking every part
against the manifest and every file against its source.
"""

import argparse
import hashlib
import json
import sys
import tarfile
from pathlib import Path

import zstandard

HERE = Path(__file__).resolve().parent
NAME = "pardes-semantic.tar.zst"
MANIFEST = NAME + ".manifest.json"
# SeforimLibrary's split: GitHub caps a release asset at 2 GiB
PART_BYTES = 2_040_109_465
LEVEL = 9  # the int8 vectors: half their size from level 3 on, and level 19 is 20 times slower for 1% more
CHUNK = 8 << 20


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        while block := f.read(CHUNK):
            h.update(block)
    return h.hexdigest()


def members(model: Path, index: Path) -> list[tuple[str, Path]]:
    """The archive's files (their name in it, the local file): the model's, checked against its pardes-model.json, and
    the index's."""
    spec = json.loads((model / "pardes-model.json").read_text("utf-8"))
    for key, name in (("onnxSha256", spec["onnx"]), ("tokenizerSha256", spec["tokenizer"])):
        if sha256(model / name) != spec[key]:
            sys.exit(f"{model / name} is not the file pardes-model.json describes")
    files = [("pardes-model/pardes-model.json", model / "pardes-model.json"),
             (f"pardes-model/{spec['onnx']}", model / spec["onnx"]),
             (f"pardes-model/{spec['tokenizer']}", model / spec["tokenizer"]),
             ("pardes-model/NOTICE.md", HERE / "NOTICE.md")]
    files += [(f"pardes.vec.lucene/{p.name}", p) for p in sorted(index.iterdir()) if p.is_file() and p.name != "write.lock"]
    return files


class Parts:
    """A write-only stream cut into `<name>.partNN` files of at most PART_BYTES, hashed as they are written."""

    def __init__(self, out: Path):
        self.out = out
        self.size = PART_BYTES
        self.parts: list[list] = []  # [path, size, hash]
        self.file = None

    def _next(self):
        if self.file:
            self.file.close()
        path = self.out / f"{NAME}.part{len(self.parts) + 1:02d}"
        self.parts.append([path, 0, hashlib.sha256()])
        self.file = path.open("wb")

    def write(self, data) -> int:
        view = memoryview(data)
        while view:
            if not self.parts or self.parts[-1][1] == self.size:
                self._next()
            part = self.parts[-1]
            n = min(len(view), self.size - part[1])
            self.file.write(view[:n])
            part[1] += n
            part[2].update(view[:n])
            view = view[n:]
        return len(data)

    def close(self) -> list[dict]:
        self.file.close()
        if len(self.parts) > 1 and self.parts[-1][1] == self.size:
            # Clients without the manifest find a missing last part by its being the only shorter one: as SeforimLibrary
            # does when the bundle is a multiple of the part size, cut it again into parts one byte smaller
            self._resplit(self.size - 1)
        if len(self.parts) == 1:
            # It fits in one file: published whole, as SeforimLibrary does
            whole = self.out / NAME
            self.parts[0][0].rename(whole)
            self.parts[0][0] = whole
        return [{"file": p.name, "size": size, "sha256": h.hexdigest()} for p, size, h in self.parts]

    def _resplit(self, size: int) -> None:
        old = [p for p, _, _ in self.parts]
        for p in old:
            p.rename(p.with_name(p.name + ".old"))
        self.parts, self.file, self.size = [], None, size
        for p in old:
            with p.with_name(p.name + ".old").open("rb") as f:
                while block := f.read(CHUNK):
                    self.write(block)
        self.file.close()
        for p in old:
            p.with_name(p.name + ".old").unlink()


def package(args) -> None:
    files = members(args.model, args.index)
    spec = json.loads((args.model / "pardes-model.json").read_text("utf-8"))
    segments = b"".join(p.read_bytes() for p in args.index.glob("segments_*"))
    for value in (spec["id"], args.library.removeprefix("v").split("-")[-1]):
        if value.encode() not in segments:
            sys.exit(f"The index's commit does not name {value}: an index of another model or library release?")
    args.out.mkdir(parents=True, exist_ok=True)
    for old in list(args.out.glob(NAME + "*")):
        old.unlink()
    parts = Parts(args.out)
    whole = hashlib.sha256()

    class Hashing:
        def write(self, data):
            whole.update(data)
            return parts.write(data)

    zstd = zstandard.ZstdCompressor(level=LEVEL, threads=-1, write_checksum=True)
    with zstd.stream_writer(Hashing(), closefd=False) as compressed:
        with tarfile.open(fileobj=compressed, mode="w|", format=tarfile.USTAR_FORMAT) as tar:
            for name, path in files:
                info = tar.gettarinfo(str(path), arcname=name)
                info.uid = info.gid = 0
                info.uname = info.gname = ""
                with path.open("rb") as f:
                    tar.addfile(info, f)
                print(f"{info.size:>12} {name}")
    listed = parts.close()
    size = sum(p["size"] for p in listed)
    manifest = {"formatVersion": 1, "file": NAME, "size": size, "sha256": whole.hexdigest(), "parts": listed,
                "pack": args.pack, "model": spec["id"], "dimension": spec["dimension"], "library": args.library,
                "lines": args.lines}
    (args.out / MANIFEST).write_text(json.dumps(manifest, indent=2) + "\n", "utf-8")
    print(f"{size} bytes in {len(listed)} file(s) -> {args.out}")


def verify(args) -> None:
    manifest = json.loads((args.verify / MANIFEST).read_text("utf-8"))
    whole = hashlib.sha256()

    class Concatenated:
        def __init__(self):
            self.queue = [(args.verify / p["file"], p["size"], p["sha256"]) for p in manifest["parts"]]
            self.file = None
            self.hash = None
            self.expected = None

        def read(self, n=-1):
            while True:
                if self.file is None:
                    if not self.queue:
                        return b""
                    path, size, digest = self.queue.pop(0)
                    if path.stat().st_size != size:
                        sys.exit(f"{path.name}: {path.stat().st_size} bytes, the manifest says {size}")
                    self.file, self.hash, self.expected = path.open("rb"), hashlib.sha256(), (path.name, digest)
                block = self.file.read(n if n and n > 0 else CHUNK)
                if block:
                    self.hash.update(block)
                    whole.update(block)
                    return block
                self.file.close()
                if self.hash.hexdigest() != self.expected[1]:
                    sys.exit(f"{self.expected[0]}: its sha256 is not the manifest's")
                self.file = None

    sources = dict(members(args.model, args.index))
    seen = set()
    raw = Concatenated()
    stream = zstandard.ZstdDecompressor().stream_reader(raw)
    with tarfile.open(fileobj=stream, mode="r|") as tar:
        for info in tar:
            if info.name not in sources:
                sys.exit(f"{info.name} is not a file of the pack")
            h = hashlib.sha256()
            f = tar.extractfile(info)
            while block := f.read(CHUNK):
                h.update(block)
            if h.hexdigest() != sha256(sources[info.name]):
                sys.exit(f"{info.name} differs from {sources[info.name]}")
            seen.add(info.name)
    if seen != set(sources):
        sys.exit(f"Missing from the pack: {sorted(set(sources) - seen)}")
    # The tar ends before the zstd frame does (its checksum) and the last part: read both to their end
    while stream.read(CHUNK):
        pass
    while raw.read(CHUNK):
        pass
    if whole.hexdigest() != manifest["sha256"]:
        sys.exit("The bundle's sha256 is not the manifest's")
    print(f"{len(seen)} files, as their sources; {len(manifest['parts'])} part(s) and the bundle as the manifest says")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", type=Path, required=True, help="export_onnx.py's folder (pardes-model.json)")
    ap.add_argument("--index", type=Path, required=True, help="VectorIndexBuilder's pardes.vec.lucene")
    ap.add_argument("--library", help="the library release the index was built on (v3-20261012193827)")
    ap.add_argument("--pack", type=int, help="the pack's number, raised for every pack published")
    ap.add_argument("--lines", type=int, default=0, help="the index's vectors")
    ap.add_argument("--out", type=Path)
    ap.add_argument("--verify", type=Path, help="check a packed folder against the model and the index")
    args = ap.parse_args()
    if args.verify:
        verify(args)
    elif args.out and args.library and args.pack:
        package(args)
    else:
        ap.error("--out, --library and --pack, or --verify")


if __name__ == "__main__":
    main()
