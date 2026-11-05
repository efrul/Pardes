"""The feedback packages users sent, downloaded from Pardes's feedback service (feedback-service/, a Cloudflare Worker)
into an inbox folder, for collect.py --packages. README.md.

    PARDES_FEEDBACK_ADMIN_TOKEN=... python fetch.py --url https://pardes-feedback.<subdomain>.workers.dev --inbox build/feedback/inbox

The token is the FEEDBACK_ADMIN_TOKEN secret the service was deployed with; it is read from the environment only, never
from the command line. Each run downloads the packages received since the last one (the inbox's index.json keeps up
to which), each as pardes-feedback-<sender>-s<id>.json, the package as it came. A sender who had what they sent deleted
(Settings › ניהול נתונים › שליחת משוב) has their packages deleted from the inbox too, those received before the
deletion. The inbox is the users' data: keep it outside the repository.
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

TOKEN_ENV = "PARDES_FEEDBACK_ADMIN_TOKEN"
PAGE = 500


class Service:
    def __init__(self, url: str, token: str):
        self.url = url.rstrip("/")
        self.token = token

    def get(self, path: str) -> bytes:
        request = urllib.request.Request(f"{self.url}{path}", headers={
            "Authorization": f"Bearer {self.token}", "User-Agent": "pardes-feedback-fetch"})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return response.read()
        except urllib.error.HTTPError as e:
            reason = e.read().decode("utf-8", "replace")
            sys.exit(f"{path}: {e.code} {reason}")


def read_index(inbox: Path) -> dict:
    path = inbox / "index.json"
    return json.loads(path.read_text("utf-8")) if path.exists() else {"after": 0, "packages": {}}


def write(path: Path, data: bytes) -> None:
    """[data] in [path], whole or not at all."""
    part = path.with_name(path.name + ".part")
    part.write_bytes(data)
    part.replace(path)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", required=True, help="the service's address (the PARDES_FEEDBACK_URL secret)")
    ap.add_argument("--inbox", type=Path, required=True, help="where the packages go (outside the repository)")
    args = ap.parse_args()
    token = os.environ.get(TOKEN_ENV)
    if not token:
        sys.exit(f"Set {TOKEN_ENV} to the service's FEEDBACK_ADMIN_TOKEN")
    service = Service(args.url, token)
    args.inbox.mkdir(parents=True, exist_ok=True)
    index = read_index(args.inbox)

    new, senders, deletions = 0, set(), []
    while True:
        page = json.loads(service.get(f"/v1/packages?after={index['after']}&limit={PAGE}"))
        for meta in page["packages"]:
            name = f"pardes-feedback-{meta['sender']}-s{meta['id']}.json"
            write(args.inbox / name, service.get(f"/v1/packages/{meta['id']}"))
            index["packages"][str(meta["id"])] = {"sender": meta["sender"], "receivedAt": meta["receivedAt"], "file": name}
            index["after"] = meta["id"]
            new += 1
            senders.add(meta["sender"])
        # Kept as it goes: a run stopped halfway goes on from there
        write(args.inbox / "index.json", json.dumps(index, indent=1).encode("utf-8"))
        deletions = page["deletions"]
        if not page["more"]:
            break

    # What senders had deleted: their copies here too, those received before they asked
    gone = 0
    for deletion in deletions:
        for pid, meta in list(index["packages"].items()):
            if meta["sender"] == deletion["sender"] and meta["receivedAt"] <= deletion["deletedAt"]:
                (args.inbox / meta["file"]).unlink(missing_ok=True)
                del index["packages"][pid]
                gone += 1
    write(args.inbox / "index.json", json.dumps(index, indent=1).encode("utf-8"))
    print(f"{new} new packages from {len(senders)} senders -> {args.inbox}"
          + (f"; {gone} deleted at their senders' request" if gone else "")
          + f"; {len(index['packages'])} in the inbox")


if __name__ == "__main__":
    main()
