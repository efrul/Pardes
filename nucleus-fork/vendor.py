#!/usr/bin/env python3
"""Copies efrul/Nucleus's decorated-window-tao from Maven Local into nucleus-fork/maven.

The fork's scripts/publish-tearoff-local.sh publishes the module under FORK; this keeps its
jar, POM and Gradle module here with every other Nucleus dependency set back to BASE, the
release the fork branched from, so the app takes only this module from the fork and the
others from Maven Central.

    python3 nucleus-fork/vendor.py   (after scripts/publish-tearoff-local.sh in the fork)
"""

import json
import os
import shutil

BASE = "3.0.0-dev-202610272317"
FORK = "3.0.0-dev-202610272317-1-tearoff"
ARTIFACT = "nucleus.decorated-window-tao"
GROUP_PATH = "dev/nucleusframework"

here = os.path.dirname(os.path.abspath(__file__))
source = os.path.expanduser(f"~/.m2/repository/{GROUP_PATH}/{ARTIFACT}/{FORK}")
target = os.path.join(here, "maven", GROUP_PATH, ARTIFACT, FORK)
shutil.rmtree(os.path.join(here, "maven"), ignore_errors=True)
os.makedirs(target)

name = f"{ARTIFACT}-{FORK}"
shutil.copyfile(os.path.join(source, f"{name}.jar"), os.path.join(target, f"{name}.jar"))
for ext in ("pom", "module"):
    with open(os.path.join(source, f"{name}.{ext}"), encoding="utf-8") as f:
        text = f.read()
    # Its Nucleus dependencies go back to BASE; the module itself keeps the fork's version.
    text = text.replace(FORK, BASE).replace(f"{ARTIFACT}-{BASE}", name)
    if ext == "pom":
        text = text.replace(
            f"<artifactId>{ARTIFACT}</artifactId>\n  <version>{BASE}</version>",
            f"<artifactId>{ARTIFACT}</artifactId>\n  <version>{FORK}</version>",
        )
    else:
        module = json.loads(text)
        module["component"]["version"] = FORK
        # The jar alone is kept here: no sources or javadoc variant pointing at a file that is not.
        module["variants"] = [
            v for v in module["variants"] if all(f["name"] == f"{name}.jar" for f in v.get("files", []))
        ]
        text = json.dumps(module, indent=2) + "\n"
    with open(os.path.join(target, f"{name}.{ext}"), "w", encoding="utf-8") as f:
        f.write(text)
print("vendored", target)
