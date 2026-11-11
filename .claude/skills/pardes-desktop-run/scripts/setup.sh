#!/usr/bin/env bash
# What the cloud container lacks to run Pardes desktop headless: a virtual display, input and window tools, Mesa's EGL
# (the Nucleus Tao window renders through EGL and fails with "Failed to create EGL context" without it; llvmpipe
# renders in software, as there is no GPU), and the tools the library scripts use. It also gives this container's
# Gradle a mirror of Maven Central, which answers 429 through the container's shared address. Idempotent.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
# The package lists may be stale ("Unable to locate package libegl1")
apt-get update -qq >/dev/null
apt-get install -y -qq \
  xvfb xdotool x11-utils xclip imagemagick zstd sqlite3 \
  libegl1 libegl-mesa0 libgles2 libgl1-mesa-dri mesa-utils fonts-noto-core >/dev/null
mkdir -p "$HOME/.gradle/init.d"
cp "$(dirname "$0")/central-mirror.init.gradle.kts" "$HOME/.gradle/init.d/"
echo "installed"
