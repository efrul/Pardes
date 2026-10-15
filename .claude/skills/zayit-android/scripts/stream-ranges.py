#!/usr/bin/env python3
"""Streams URLs to stdout one after another, resuming each from where it stopped when the connection drops.

  stream-ranges.py URL [URL ...] | zstd -d -c | tar -x ...

The cloud container's proxy resets long transfers (a bare "Connection reset by peer" after a few GB), and
curl --retry restarts a file from byte 0, which a pipe into tar cannot take. This asks for the file in chunks
with HTTP Range requests and retries a failed chunk from the byte it reached, so the bytes written to stdout
are exactly the file's, in order. Uses HTTPS_PROXY and the system CA bundle like the rest of the session.
"""
import os
import sys
import time
import urllib.request

CHUNK = 256 * 1024 * 1024
TRIES = 12


def size_and_final_url(url):
    req = urllib.request.Request(url, method="HEAD")
    with urllib.request.urlopen(req, timeout=60) as r:
        return int(r.headers["Content-Length"]), r.geturl()


def stream(url, out):
    total, final = size_and_final_url(url)
    pos, tries = 0, 0
    while pos < total:
        end = min(pos + CHUNK, total) - 1
        req = urllib.request.Request(final, headers={"Range": f"bytes={pos}-{end}"})
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                if r.status != 206:
                    raise IOError(f"no range support (HTTP {r.status})")
                while True:
                    buf = r.read(1 << 20)
                    if not buf:
                        break
                    out.write(buf)
                    pos += len(buf)
            tries = 0
        except Exception as e:  # reset, timeout, expired signed URL: retry from pos
            tries += 1
            if tries > TRIES:
                raise
            print(f"[stream-ranges] {os.path.basename(url)} at {pos}/{total}: {e}; retry {tries}", file=sys.stderr)
            time.sleep(min(60, 2 ** tries))
            if tries % 3 == 0:  # a redirect target may be a short-lived signed URL
                total, final = size_and_final_url(url)
    print(f"[stream-ranges] {os.path.basename(url)}: {total} bytes", file=sys.stderr)


def main():
    out = sys.stdout.buffer
    for url in sys.argv[1:]:
        stream(url, out)
    out.flush()


if __name__ == "__main__":
    main()
