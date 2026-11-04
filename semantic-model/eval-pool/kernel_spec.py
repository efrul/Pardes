"""The pool.json a pool kernel ran on, taken out of its script: kaggle_run.py packs the spec into the script's bundle
(data/pool.json), so the last measurement's searches and candidates can be had again without the harness.

    kaggle kernels pull <user>/pardes-semantic-pool -p build/pool-kernel
    python kernel_spec.py build/pool-kernel/run.py build/pool.json
"""

import argparse
import base64
import io
import re
import tarfile
from pathlib import Path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("script", type=Path, help="the pool kernel's run.py")
    ap.add_argument("out", type=Path)
    args = ap.parse_args()
    found = re.search(r"base64\.b64decode\('([A-Za-z0-9+/=]+)'\)", args.script.read_text("utf-8"))
    if not found:
        raise SystemExit(f"{args.script}: no bundle (not a script of kaggle_run.py?)")
    with tarfile.open(fileobj=io.BytesIO(base64.b64decode(found.group(1)))) as t:
        member = t.extractfile("data/pool.json")
        if member is None:
            raise SystemExit(f"{args.script}: no data/pool.json in its bundle (not a pool kernel?)")
        args.out.write_bytes(member.read())
    print(f"-> {args.out}")


if __name__ == "__main__":
    main()
