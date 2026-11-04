#!/usr/bin/env bash
# What the cloud container lacks to run Pardes desktop headless: a virtual display, input and window tools, Mesa's EGL
# (the Nucleus Tao window renders through EGL and fails with "Failed to create EGL context" without it; llvmpipe
# renders in software, as there is no GPU), and the tools the library scripts use. Idempotent.
set -euo pipefail
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
  xvfb xdotool x11-utils imagemagick zstd sqlite3 \
  libegl1 libegl-mesa0 libgles2 libgl1-mesa-dri mesa-utils fonts-noto-core >/dev/null
echo "installed"
