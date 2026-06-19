#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SONARCLOUD_WEBAPP_DIR="${SONARCLOUD_WEBAPP_DIR:-"$(cd "$ROOT_DIR/.." && pwd)/sonarcloud-webapp"}"
ESBUILD="$SONARCLOUD_WEBAPP_DIR/node_modules/.bin/esbuild"
APP_ENTRY="$ROOT_DIR/src/main/frontend/issue-history-app.tsx"
APP_OUTPUT="$ROOT_DIR/src/main/resources/mcp-apps/issue-history-app.js"

if [[ ! -x "$ESBUILD" ]]; then
  echo "Missing esbuild at $ESBUILD" >&2
  echo "Run the sonarcloud-webapp install/build first, or set SONARCLOUD_WEBAPP_DIR." >&2
  exit 1
fi

NODE_PATH="$SONARCLOUD_WEBAPP_DIR/node_modules" "$ESBUILD" \
  "$APP_ENTRY" \
  --bundle \
  --format=iife \
  --global-name=IssueHistoryMcpApp \
  --jsx=automatic \
  --alias:react="$SONARCLOUD_WEBAPP_DIR/node_modules/react" \
  --alias:react-dom="$SONARCLOUD_WEBAPP_DIR/node_modules/react-dom" \
  --minify \
  --outfile="$APP_OUTPUT"

for output in "$APP_OUTPUT" "${APP_OUTPUT%.js}.css"; do
  if [[ -f "$output" ]]; then
    perl -pi -e 's/[ \t]+$//' "$output"
  fi
done
