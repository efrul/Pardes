"""The library's vectors against their lines (BENCHMARK.md, section 4): N random rows of vecs.i8 against their line's
text embedded again by the int8 ONNX model (cosine), and against the next row's line (what a shifted id would give).

    python check_vectors.py build/vectors-int8 build/model-student seforim.db [N]
"""

import json
import random
import sqlite3
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pardes_semantic.clean import clean  # noqa: E402


def main() -> None:
    vec_dir, model_dir, db = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]
    n = int(sys.argv[4]) if len(sys.argv) > 4 else 300
    spec = json.loads((model_dir / "pardes-model.json").read_text(encoding="utf-8"))
    count, dim = (int(x) for x in (vec_dir / "meta.txt").read_text().split())
    ids = np.fromfile(vec_dir / "ids.i64", dtype="<i8")
    vecs = np.memmap(vec_dir / "vecs.i8", dtype=np.int8, mode="r", shape=(count, dim))
    assert len(ids) == count
    con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    tok = Tokenizer.from_file(str(model_dir / spec["tokenizer"]))
    tok.enable_truncation(spec["documentMaxTokens"])
    sess = ort.InferenceSession(str(model_dir / spec["onnx"]), providers=["CPUExecutionProvider"])
    names = {i.name for i in sess.get_inputs()}

    def embed(text):
        e = tok.encode(spec["documentPrefix"] + text)
        feed = {"input_ids": np.array([e.ids], dtype=np.int64), "attention_mask": np.array([e.attention_mask], dtype=np.int64)}
        out = sess.run([spec["output"]], {k: v for k, v in feed.items() if k in names})[0][0]
        return out / np.linalg.norm(out)

    rng = random.Random(5812)
    same, shifted = [], []
    for r in rng.sample(range(count - 1), n):
        v = embed(clean(con.execute("SELECT content FROM line WHERE id = ?", (int(ids[r]),)).fetchone()[0]))
        a, b = vecs[r].astype(np.float32), vecs[r + 1].astype(np.float32)
        same.append(float(a @ v / np.linalg.norm(a)))
        shifted.append(float(b @ v / np.linalg.norm(b)))
    print(f"{n} rows: cosine to their line min {min(same):.3f} mean {np.mean(same):.3f}; "
          f"to the next row's line mean {np.mean(shifted):.3f}")


if __name__ == "__main__":
    main()
