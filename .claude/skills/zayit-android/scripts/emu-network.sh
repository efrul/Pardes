#!/usr/bin/env bash
# Gives the emulator's apps the container's network: the agent proxy as Android's global HTTP proxy (the host's
# 127.0.0.1 is 10.0.2.2 from the guest), and the proxy's interception CA as a user-installed CA, which Pardes trusts
# (network_security_config.xml), as a filtering proxy's CA would be on a phone. "off" undoes both.
#   emu-network.sh [on|off]
# After a restart Wi-Fi may stay down ("Active default network: none"): `adb shell cmd wifi connect-network
# AndroidWifi open`, then wait for wlan0's address. An app reads the pack list again only when its card reappears.
set -uo pipefail
export PATH=$PATH:/opt/android-sdk/platform-tools
CA=/root/.ccr/agent-proxy-ca.crt
HASH=$(openssl x509 -subject_hash_old -noout -in "$CA")
PORT=$(echo "${HTTPS_PROXY:-http://127.0.0.1:46355}" | sed -E 's#.*:([0-9]+)/?$#\1#')
adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
if [ "${1:-on}" = off ]; then
  adb shell settings delete global http_proxy
  adb shell rm -f /data/misc/user/0/cacerts-added/$HASH.0
  echo "network setup removed"
  exit 0
fi
adb push "$CA" /data/local/tmp/ca.crt >/dev/null
adb shell "mkdir -p /data/misc/user/0/cacerts-added && cp /data/local/tmp/ca.crt /data/misc/user/0/cacerts-added/$HASH.0 && chown system:system /data/misc/user/0/cacerts-added /data/misc/user/0/cacerts-added/$HASH.0 && chmod 644 /data/misc/user/0/cacerts-added/$HASH.0 && restorecon -R /data/misc/user/0/cacerts-added && rm /data/local/tmp/ca.crt"
adb shell settings put global http_proxy 10.0.2.2:$PORT
echo "proxy: $(adb shell settings get global http_proxy), CA: $(adb shell ls -la /data/misc/user/0/cacerts-added/)"
