"""Turns the float32 vectors of embed_corpus.py into the int8 the index stores: a quarter of the size to move.

    python quantize_vectors.py --vectors build/vectors --out build/vectors-int8

The index keeps int8 vectors only (VectorIndexBuilder quantizes each float vector with OnnxEmbeddingProvider's
quantizeInt8); this is the same quantization done ahead, bit for bit (pardes_semantic.model.quantize_int8), so the
5.7M vectors of the library are 2.2 GB to download instead of 8.8. Writes ids.i64, meta.txt and embed-info.json as they
are, and vecs.i8 (int8, one row per id) in place of vecs.f32: VectorIndexBuilder reads either.
"""

import argparse
import json
import shutil
import sys
from pathlib import Path

import numpy as np

from pardes_semantic.model import quantize_int8

# float32 rows read at a time: 100k x 384 x 4 = 154 MB
ROWS = 100_000


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--vectors", type=Path, required=True, help="embed_corpus.py's output")
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    count, dim = (int(x) for x in (args.vectors / "meta.txt").read_text().split())
    src = args.vectors / "vecs.f32"
    if src.stat().st_size != count * dim * 4:
        sys.exit(f"{src}: {src.stat().st_size} bytes, meta.txt says {count} x {dim} float32")
    args.out.mkdir(parents=True, exist_ok=True)
    for name in ("ids.i64", "meta.txt"):
        shutil.copyfile(args.vectors / name, args.out / name)
    info = json.loads((args.vectors / "embed-info.json").read_text("utf-8"))
    info["vectors"] = "int8"
    (args.out / "embed-info.json").write_text(json.dumps(info, ensure_ascii=False, indent=2) + "\n", "utf-8")
    vecs = np.memmap(src, dtype="<f4", mode="r", shape=(count, dim))
    with (args.out / "vecs.i8").open("wb") as f:
        for i in range(0, count, ROWS):
            f.write(quantize_int8(np.asarray(vecs[i:i + ROWS])).tobytes())
    print(f"{count} vectors of {dim} -> {args.out / 'vecs.i8'} ({count * dim / 1e9:.2f} GB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
