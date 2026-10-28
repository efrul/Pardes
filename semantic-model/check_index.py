"""Reads IndexCheck.kt's output (eval-100/harness): the app's query vectors against Python's (the same int8 ONNX model,
onnxruntime and tokenizers), how much of an exact search's first ten the vector index finds through the app's dense
search, the line a query was taken from among the first ten (group rand40, its line id as `expected`), and the time of
the app's search.

    python check_index.py check.jsonl pardes-model/

Two measures of the first ten: by line (the share of the exact search's lines the app also gives), and by score (the
share of the app's lines that score at least as high as the exact search's tenth). They part when many lines tie at the
same score, such as the same text in many books: any ten of them are as good, the line ids then differ.
"""

import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pardes_semantic.clean import clean  # noqa: E402

# Lucene's scores are float32 ((1 + cosine) / 2): a tie by score allows for their rounding
TIE = 1e-5


def main() -> None:
    rows = [json.loads(line) for line in open(sys.argv[1], encoding="utf-8")]
    model = Path(sys.argv[2])
    spec = json.loads((model / "pardes-model.json").read_text(encoding="utf-8"))
    assert all(r["model"] == spec["id"] for r in rows), "the output is of another model"
    tok = Tokenizer.from_file(str(model / spec["tokenizer"]))
    tok.enable_truncation(spec["queryMaxTokens"])
    sess = ort.InferenceSession(str(model / spec["onnx"]), providers=["CPUExecutionProvider"])
    names = {i.name for i in sess.get_inputs()}

    def embed(text):
        e = tok.encode(spec["queryPrefix"] + clean(text))
        feed = {"input_ids": np.array([e.ids], dtype=np.int64), "attention_mask": np.array([e.attention_mask], dtype=np.int64)}
        v = sess.run([spec["output"]], {k: x for k, x in feed.items() if k in names})[0][0]
        return v / np.linalg.norm(v)

    cos = [float(np.dot(embed(r["query"]), np.array(r["vector"]) / np.linalg.norm(r["vector"]))) for r in rows]
    print(f"{spec['id']}: {len(rows)} queries; the app's query vectors against Python's: cosine min {min(cos):.4f}, "
          f"mean {statistics.mean(cos):.4f}")

    def by_line(app, exact):
        return len({h[0] for h in app} & {h[0] for h in exact}) / max(1, len(exact))

    def by_score(app, exact):
        return sum(h[1] >= exact[-1][1] - TIE for h in app) / max(1, len(exact)) if exact else 1.0

    groups = defaultdict(list)
    for r in rows:
        groups[r["group"]].append(r)
    for group, rs in groups.items():
        print(f"{group} ({len(rs)}):")
        for side in ("All", "Base"):
            app, exact = f"app{side}", f"exact{side}"
            ms = sorted(r[f"us{side}"] / 1000 for r in rs)
            print(f"  {side.lower():4s}: the exact first ten by line {statistics.mean(by_line(r[app], r[exact]) for r in rs):.3f}, "
                  f"by score {statistics.mean(by_score(r[app], r[exact]) for r in rs):.3f}; "
                  f"{statistics.mean(ms):.0f} ms mean, {ms[len(ms) // 2]:.0f} median, {ms[int(len(ms) * 0.9)]:.0f} p90")
        if group == "rand40":
            print(f"  its line in the first ten: app {sum(int(r['expected']) in {h[0] for h in r['appAll']} for r in rs)}, "
                  f"exact {sum(int(r['expected']) in {h[0] for h in r['exactAll']} for r in rs)}")


if __name__ == "__main__":
    main()
