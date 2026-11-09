"""Streams a semantic pack of Pardes's releases (the model and the vector index) into the library's folder, where the
search finds them (pardes-model/, pardes.vec.lucene/).

    python fetch_pack.py semantic-2-v3-20261012193827 LIBRARY_DIR
"""

import sys
import tarfile
import urllib.error
import urllib.request
from pathlib import Path

import zstandard


class Parts:
    def __init__(self, tag):
        self.base = f"https://github.com/efrul/Pardes/releases/download/{tag}/pardes-semantic.tar.zst.part"
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
    with tarfile.open(fileobj=zstandard.ZstdDecompressor().stream_reader(Parts(tag), read_size=1 << 20), mode="r|") as tar:
        for m in tar:
            tar.extract(m, out, filter="data")
            print(f"{m.name} ({m.size / 1e6:.1f} MB)", flush=True)


if __name__ == "__main__":
    main()
