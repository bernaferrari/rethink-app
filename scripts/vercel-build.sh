#!/usr/bin/env bash
# Build the common Compose Multiplatform browser distribution for Vercel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Kotlin/Wasm uses Yarn 1 internally. Pinning it in this repository keeps an unrelated
# package-manager declaration higher in a developer's filesystem from affecting the build.
export COREPACK_ENABLE_PROJECT_SPEC=0

java_major=0
if command -v java >/dev/null 2>&1; then
  java_major="$(java -version 2>&1 | head -n 1 | sed -E 's/.*version "([0-9]+).*/\1/')"
  [[ "$java_major" =~ ^[0-9]+$ ]] || java_major=0
fi

if (( java_major < 17 )); then
  jdk_cache_dir="${VERCEL_CACHE_DIR:-${HOME}/.cache}/temurin-21"
  if [[ ! -x "$jdk_cache_dir/bin/java" ]]; then
    mkdir -p "$jdk_cache_dir"
    curl --fail --location --silent --show-error \
      "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" \
      | tar -xz --strip-components=1 -C "$jdk_cache_dir"
  fi
  export JAVA_HOME="$jdk_cache_dir"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

java -version
cd "$ROOT"
exec ./gradlew :shared:wasmJsBrowserDistribution --no-daemon --console=plain
