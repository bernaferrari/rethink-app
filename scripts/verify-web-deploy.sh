#!/usr/bin/env bash
# Mirrors GitHub Actions and Vercel: build the Kotlin/Wasm app, then verify the
# complete static bundle before it is deployed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST="$ROOT/shared/build/dist/wasmJs/productionExecutable"
SKIP_BUILD=false
SERVE_MODE=false

for arg in "$@"; do
  case "$arg" in
    --ci|--web-only)
      # Accepted so local invocations match QuietGuard's CI command. This
      # project has no separate Android setup step in the web verification.
      ;;
    --skip-build) SKIP_BUILD=true ;;
    --serve) SERVE_MODE=true ;;
    -h|--help)
      echo "Usage: $0 [--ci] [--web-only] [--skip-build] [--serve]"
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 1
      ;;
  esac
done

cd "$ROOT"

if [[ "$SKIP_BUILD" == false ]]; then
  echo "==> Building Kotlin/Wasm web distribution"
  ./gradlew \
    :shared:wasmJsBrowserDistribution \
    --no-daemon \
    --parallel \
    --build-cache \
    --configuration-cache \
    --console=plain
fi

echo "==> Verifying production artifacts"
required_files=(
  index.html
  shared.js
  rethink-cipher-current.js
  favicon.svg
  opengraph-image.png
  opengraph-image.svg
)
for file in "${required_files[@]}"; do
  if [[ ! -f "$DIST/$file" ]]; then
    echo "Missing required file: $DIST/$file" >&2
    exit 1
  fi
done

wasm_count="$(find "$DIST" -maxdepth 1 -name '*.wasm' | wc -l | tr -d ' ')"
if [[ "$wasm_count" -lt 1 ]]; then
  echo "Expected at least one .wasm file in $DIST" >&2
  exit 1
fi

# Kotlin/Wasm writes content-hashed assets. Verifying that each filename emitted
# in JavaScript exists catches stale or incomplete output before Vercel serves it.
echo "==> Verifying JavaScript -> Wasm asset references"
wasm_references="$(grep -ahoE '[0-9a-f]{20}\.wasm' "$DIST/shared.js" | sort -u || true)"
if [[ -z "$wasm_references" ]]; then
  echo "No content-hashed Wasm references found in emitted JavaScript" >&2
  exit 1
fi

while IFS= read -r wasm_asset; do
  if [[ ! -f "$DIST/$wasm_asset" ]]; then
    echo "JavaScript references missing Wasm asset: $wasm_asset" >&2
    exit 1
  fi
  echo "    ok: $wasm_asset"
done <<< "$wasm_references"

if ! grep -q 'base href="/"' "$DIST/index.html"; then
  echo 'index.html must use base href="/" for Vercel root deployment' >&2
  exit 1
fi

# The browser demo uses cross-origin isolation; prevent a deployment that omits
# the headers required by the Wasm runtime.
if ! grep -q 'Cross-Origin-Embedder-Policy' "$ROOT/vercel.json" || \
   ! grep -q 'Cross-Origin-Opener-Policy' "$ROOT/vercel.json"; then
  echo "vercel.json must declare COOP/COEP headers" >&2
  exit 1
fi

echo "==> Web deploy bundle is ready"
echo "    dist:      $DIST"
echo "    shared.js: $(du -h "$DIST/shared.js" | awk '{print $1}')"
echo "    wasm:      $wasm_count file(s)"

if [[ "$SERVE_MODE" == true ]]; then
  if command -v vercel >/dev/null 2>&1; then
    echo "==> Starting local Vercel preview (Ctrl+C to stop)"
    cd "$ROOT"
    vercel dev --listen 4173
  else
    echo "vercel CLI not found; install with: npm i -g vercel" >&2
    exit 1
  fi
fi
