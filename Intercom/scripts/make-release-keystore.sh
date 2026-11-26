#!/usr/bin/env bash
# Creates a permanent release key and prints the values to paste into
# GitHub → Settings → Secrets and variables → Actions. Never commit the .jks.
set -euo pipefail
OUT="${1:-$HOME/intercom-release.jks}"
read -rsp "Choose a keystore password: " PW; echo
keytool -genkeypair -keystore "$OUT" -alias intercom -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PW" -keypass "$PW" -dname "CN=Intercom"
echo
echo "Add these repository secrets:"
echo "  INTERCOM_KEYSTORE_BASE64 = (contents of $OUT.b64)"
echo "  INTERCOM_KEYSTORE_PASSWORD = <your password>"
echo "  INTERCOM_KEY_ALIAS = intercom"
echo "  INTERCOM_KEY_PASSWORD = <your password>"
base64 -w0 "$OUT" > "$OUT.b64"
echo "Wrote $OUT.b64 — keep $OUT safe and OUTSIDE the repository."
