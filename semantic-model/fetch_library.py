"""Downloads seforim.db from a SeforimLibrary release, streaming: the 5.5 GB bundle is never stored.

    python fetch_library.py --out build/library                      # the latest release
    python fetch_library.py --out build/library --release v3-20261012193827

The bundle (seforim_bundle.tar.zst.partNN) is one zstd-compressed tar; only seforim.db and release_info.txt are
written (8 GB). The Lucene indexes in it are read past, not kept: the semantic index is built from the database alone.
"""

import argparse
import tarfile
import urllib.error
import urllib.request
from pathlib import Path

import zstandard

REPO = "https://github.com/kdroidFilter/SeforimLibrary/releases"
KEEP = {"seforim.db", "release_info.txt"}


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def latest_tag() -> str:
    # latest/download/<asset> redirects to download/<tag>/<asset>: the tag, without the (rate-limited) API
    opener = urllib.request.build_opener(_NoRedirect)
    try:
        opener.open(f"{REPO}/latest/download/seforim_bundle.tar.zst.part01")
    except urllib.error.HTTPError as e:
        if e.code in (301, 302, 303, 307, 308):
            return e.headers["Location"].split("/download/", 1)[1].split("/", 1)[0]
        raise
    raise RuntimeError("No redirect from the latest release")


class Parts:
    """The parts of the bundle, read one after the other as one stream."""

    def __init__(self, tag: str):
        self.base = f"{REPO}/download/{tag}/seforim_bundle.tar.zst.part"
        self.number = 0
        self.current = None

    def read(self, n: int) -> bytes:
        while True:
            if self.current is None:
                self.number += 1
                try:
                    self.current = urllib.request.urlopen(f"{self.base}{self.number:02d}")
                except urllib.error.HTTPError as e:
                    if e.code == 404 and self.number > 1:
                        return b""
                    raise
                print(f"part {self.number:02d}", flush=True)
            chunk = self.current.read(n)
            if chunk:
                return chunk
            self.current = None


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--release", help="a release tag (default: the latest)")
    args = ap.parse_args()
    tag = args.release or latest_tag()
    print(f"SeforimLibrary release {tag}", flush=True)
    args.out.mkdir(parents=True, exist_ok=True)
    stream = zstandard.ZstdDecompressor().stream_reader(Parts(tag), read_size=1 << 20)
    with tarfile.open(fileobj=stream, mode="r|") as tar:
        for member in tar:
            if member.name.lstrip("./") in KEEP:
                try:
                    tar.extract(member, args.out, filter="data")
                except TypeError:  # Python before 3.11.4 (Kaggle's may be): no extraction filters
                    tar.extract(member, args.out)
                print(f"{member.name} ({member.size / 1e9:.2f} GB)", flush=True)


if __name__ == "__main__":
    main()
