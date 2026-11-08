"""Streams a SeforimLibrary release's bundle and keeps what the search needs: seforim.db, its Lucene index
(seforim.db.lucene), lexical.db and release_info.txt (about 12.5 GB). The bundle itself is never stored.

    python fetch_library.py v3-20261012193827 LIBRARY_DIR
"""

import sys
import tarfile
import urllib.error
import urllib.request
from pathlib import Path

import zstandard

REPO = "https://github.com/kdroidFilter/SeforimLibrary/releases"
KEEP_FILES = {"seforim.db", "release_info.txt", "lexical.db"}
KEEP_DIR = "seforim.db.lucene"


class Parts:
    """The bundle's parts (seforim_bundle.tar.zst.partNN), read one after the other as one stream."""

    def __init__(self, tag):
        self.base = f"{REPO}/download/{tag}/seforim_bundle.tar.zst.part"
        self.number, self.current = 0, None

    def read(self, n):
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


def main():
    tag, out = sys.argv[1], Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    stream = zstandard.ZstdDecompressor().stream_reader(Parts(tag), read_size=1 << 20)
    with tarfile.open(fileobj=stream, mode="r|") as tar:
        for m in tar:
            name = m.name.lstrip("./")
            if name in KEEP_FILES or name == KEEP_DIR or name.startswith(KEEP_DIR + "/"):
                tar.extract(m, out, filter="data")
                print(f"{name} ({m.size / 1e6:.1f} MB)", flush=True)


if __name__ == "__main__":
    main()
