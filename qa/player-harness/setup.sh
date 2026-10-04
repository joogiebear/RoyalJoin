#!/usr/bin/env bash
set -euo pipefail

HARNESS_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
TOOLS_DIR=${ROYALJOIN_HARNESS_TOOLS:-"$HARNESS_DIR/.tools"}
mkdir -p "$TOOLS_DIR"

download() {
  local url=$1 target=$2 sha256=$3
  if [[ ! -f "$target" ]] || ! printf '%s  %s\n' "$sha256" "$target" | sha256sum -c - >/dev/null 2>&1; then
    curl --fail --location --proto '=https' --tlsv1.2 "$url" --output "$target"
  fi
  printf '%s  %s\n' "$sha256" "$target" | sha256sum -c -
}

download \
  'https://github.com/MCCTeam/Minecraft-Console-Client/releases/download/20260929-519/MinecraftClient-20260929-519-linux-x64' \
  "$TOOLS_DIR/MinecraftClient-20260929-519-linux-x64" \
  'a60a5a63813c9e5b4f0f86e275a5e7162d7ff091f66e2281e013af444477b9cb'
chmod 700 "$TOOLS_DIR/MinecraftClient-20260929-519-linux-x64"

download \
  'https://fill-data.papermc.io/v1/objects/b1d8f6bfa1b6101fa8e947b53041cb3bdf5540e7b83b6547ca19ba7edefeb083/paper-26.2-129.jar' \
  "$TOOLS_DIR/paper-26.2-129.jar" \
  'b1d8f6bfa1b6101fa8e947b53041cb3bdf5540e7b83b6547ca19ba7edefeb083'

MAVEN_ARCHIVE="$TOOLS_DIR/apache-maven-3.9.9-bin.tar.gz"
MAVEN_SHA512='a555254d6b53d267965a3404ecb14e53c3827c09c3b94b5678835887ab404556bfaf78dcfe03ba76fa2508649dca8531c74bca4d5846513522404d48e8c4ac8b'
if [[ ! -f "$MAVEN_ARCHIVE" ]] || ! printf '%s  %s\n' "$MAVEN_SHA512" "$MAVEN_ARCHIVE" | sha512sum -c - >/dev/null 2>&1; then
  curl --fail --location --proto '=https' --tlsv1.2 \
    'https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz' \
    --output "$MAVEN_ARCHIVE"
fi
printf '%s  %s\n' "$MAVEN_SHA512" "$MAVEN_ARCHIVE" | sha512sum -c -
if [[ ! -x "$TOOLS_DIR/apache-maven-3.9.9/bin/mvn" ]]; then
  tar -xzf "$TOOLS_DIR/apache-maven-3.9.9-bin.tar.gz" -C "$TOOLS_DIR"
fi

if [[ ! -x "$TOOLS_DIR/jdk-25/bin/java" ]]; then
  JDK_ARCHIVE="$TOOLS_DIR/OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz"
  curl --fail --location --proto '=https' --tlsv1.2 \
    'https://api.adoptium.net/v3/binary/version/jdk-25.0.4.1%2B1/linux/x64/jdk/hotspot/normal/eclipse' \
    --output "$JDK_ARCHIVE"
  mkdir -p "$TOOLS_DIR/jdk-25"
  tar -xzf "$JDK_ARCHIVE" -C "$TOOLS_DIR/jdk-25" --strip-components=1
fi

echo "Tools installed in $TOOLS_DIR"
echo "Run: qa/player-harness/run-smoke.sh"
