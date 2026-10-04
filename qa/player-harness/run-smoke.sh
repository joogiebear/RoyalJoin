#!/usr/bin/env bash
set -euo pipefail

HARNESS_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPO_DIR=$(cd -- "$HARNESS_DIR/../.." && pwd)
RUNTIME_DIR=${ROYALJOIN_HARNESS_RUNTIME:-"${PAPERCLIP_RUN_SCRATCH_DIR:-$HARNESS_DIR/.runtime}"}
TOOLS_DIR=${ROYALJOIN_HARNESS_TOOLS:-"$HARNESS_DIR/.tools"}
JAVA_HOME=${JAVA_HOME:-"$TOOLS_DIR/jdk-25"}
MAVEN_HOME=${MAVEN_HOME:-"$TOOLS_DIR/apache-maven-3.9.9"}
MCC_BIN=${MCC_BIN:-"$TOOLS_DIR/MinecraftClient-20260929-519-linux-x64"}
PAPER_JAR=${PAPER_JAR:-"$TOOLS_DIR/paper-26.2-129.jar"}
SERVER_DIR="$RUNTIME_DIR/server"
FIFO="$RUNTIME_DIR/server.stdin"
SERVER_PID=""
MCC_PID=""

cleanup() {
  set +e
  if [[ -n "$MCC_PID" ]] && kill -0 "$MCC_PID" 2>/dev/null; then kill "$MCC_PID"; fi
  if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    printf 'stop\n' >"$FIFO"
    for _ in {1..30}; do kill -0 "$SERVER_PID" 2>/dev/null || break; sleep 1; done
    kill "$SERVER_PID" 2>/dev/null || true
  fi
  rm -f "$FIFO"
}
trap cleanup EXIT INT TERM

for required in "$JAVA_HOME/bin/java" "$MAVEN_HOME/bin/mvn" "$MCC_BIN" "$PAPER_JAR"; do
  [[ -e "$required" ]] || { echo "Missing $required; run qa/player-harness/setup.sh" >&2; exit 2; }
done

rm -rf "$SERVER_DIR"
rm -f "$FIFO"
mkdir -p "$SERVER_DIR/plugins"
mkfifo "$FIFO"

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
mvn -q -Dmaven.repo.local="$RUNTIME_DIR/m2" -f "$REPO_DIR/pom.xml" package
mvn -q -Dmaven.repo.local="$RUNTIME_DIR/m2" -f "$HARNESS_DIR/helper/pom.xml" package
mvn -q -Dmaven.repo.local="$RUNTIME_DIR/m2" -f "$HARNESS_DIR/papi-fault/pom.xml" package
cp "$REPO_DIR/target/RoyalJoin.jar" "$SERVER_DIR/plugins/RoyalJoin.jar"
cp "$HARNESS_DIR/helper/target/RoyalJoinHarnessHelper.jar" "$SERVER_DIR/plugins/RoyalJoinHarnessHelper.jar"
cp "$HARNESS_DIR/papi-fault/target/PlaceholderAPI-QAFault.jar" "$SERVER_DIR/plugins/PlaceholderAPI-QAFault.jar"
cp "$PAPER_JAR" "$SERVER_DIR/paper.jar"

# Deterministic starting fixture. Every later mutation is performed by the QA-only helper.
mkdir -p "$SERVER_DIR/plugins/RoyalJoin"
cat >"$SERVER_DIR/plugins/RoyalJoin/config.yml" <<'YAML'
items:
  menu:
    slot: 9
    material: NETHER_STAR
    name: '&6QA Menu'
    lore: ['&7%player%']
    command: 'harness dump'
    locked: true
    glow: true
    custom-model-data: 4242
cooldown:
  between-uses-ms: 0
  spam-threshold: 0
  spam-window-ms: 1000
  lockout-seconds: 0
YAML

printf '%s\n' 'eula=true' >"$SERVER_DIR/eula.txt"
cat >"$SERVER_DIR/server.properties" <<'PROPERTIES'
server-ip=127.0.0.1
server-port=25571
online-mode=false
enforce-secure-profile=false
motd=RoyalJoin isolated QA harness
spawn-protection=0
view-distance=4
simulation-distance=4
enable-rcon=false
enable-query=false
PROPERTIES

(cd "$SERVER_DIR" && "$JAVA_HOME/bin/java" -Xms512M -Xmx1G -jar paper.jar --nogui <"$FIFO" >server.log 2>&1) &
SERVER_PID=$!
exec 3>"$FIFO"
for _ in {1..120}; do
  grep -q 'Done (' "$SERVER_DIR/server.log" 2>/dev/null && break
  kill -0 "$SERVER_PID" 2>/dev/null || { tail -100 "$SERVER_DIR/server.log"; exit 1; }
  sleep 1
done
grep -q 'Done (' "$SERVER_DIR/server.log" || { echo 'Paper startup timed out' >&2; exit 1; }

MCC_LOG="$RUNTIME_DIR/mcc.log"
MCC_CONFIG="$RUNTIME_DIR/MinecraftClient.ini"
cp "$HARNESS_DIR/MinecraftClient.ini" "$MCC_CONFIG"
{
  sleep 5
  printf '/harness assert-join\n'
  sleep 2
  printf '/inventory player list\n'
  sleep 2
  # Two real container-click packets attempt to move RoyalJoin's locked hotbar item (protocol slot 44).
  printf '/inventory player click 44\n'
  sleep 1
  printf '/inventory player click 44\n'
  sleep 2
  printf '/harness real-protect\n'
  printf '/harness dump\n'
  sleep 2
  printf '/harness assert-join\n'
  sleep 2
  printf '/harness refresh\n'
  sleep 2
  printf '/harness inventory\n'
  sleep 3
  printf '/harness permission\n'
  sleep 3
  printf '/harness world\n'
  sleep 5
  printf '/harness reload-valid\n'
  sleep 2
  printf '/harness reload-invalid\n'
  sleep 2
  printf '/harness empty\n'
  sleep 2
  printf '/harness protect\n'
  sleep 2
  printf '/harness papi missing\n'
  sleep 1
  printf '/useitem\n'
  sleep 1
  printf '/harness papi check missing\n'
  sleep 1
  printf '/harness papi throwing\n'
  sleep 1
  printf '/useitem\n'
  sleep 1
  printf '/harness papi check throwing\n'
  sleep 1
  printf '/harness papi reset\n'
  sleep 2
  printf '/harness death false\n'
  sleep 4
  printf '/harness death true\n'
  sleep 4
  printf '/harness commands player\n'
  sleep 1
  printf '/changeslot 9\n'
  printf '/useitem\n'
  sleep 2
  printf '/harness command-check 1 command-player-count\n'
  sleep 2
  printf '/harness commands console\n'
  sleep 1
  printf '/useitem\n'
  sleep 2
  printf '/harness command-check 1 command-console-count\n'
  sleep 2
  printf '/harness commands cooldown\n'
  sleep 1
  printf '/useitem\n/useitem\n'
  sleep 1
  printf '/harness command-check 1 cooldown-between-uses\n'
  sleep 2
  printf '/useitem\n'
  sleep 1
  printf '/harness command-check 2 cooldown-reset-after-expiry\n'
  sleep 2
  printf '/harness commands burst\n'
  sleep 1
  printf '/useitem\n/useitem\n/useitem\n'
  sleep 2
  printf '/harness command-check 1 cooldown-burst-lockout-message\n'
  sleep 2
  printf '/harness reset\n'
  sleep 2
  printf '/harness result\n'
  sleep 2
  printf '/quit\n'
} | (cd "$RUNTIME_DIR" && DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 "$MCC_BIN" "$MCC_CONFIG") >"$MCC_LOG" 2>&1 &
MCC_PID=$!
wait "$MCC_PID"
MCC_PID=""

# Reconnect is a second protocol login, not a synthetic server-side Player.
{
  sleep 5
  printf '/harness assert-join\n'
  sleep 2
  printf '/quit\n'
} | (cd "$RUNTIME_DIR" && DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 "$MCC_BIN" "$MCC_CONFIG") >>"$MCC_LOG" 2>&1

grep -q 'RoyalJoinQA joined the game' "$SERVER_DIR/server.log"
[[ $(grep -c 'RoyalJoinQA joined the game' "$SERVER_DIR/server.log") -ge 2 ]]
required_cases=(join-configured-full-metadata repeated-refresh-idempotent-full-state reserved-target-partial-merge
  full-inventory-old-tag-rich-metadata-fixture full-inventory-exact-rollback-old-tags multiple-items-changed-slots deterministic-conflict-rejection
  permission-absent permission-grant permission-removal world-whitelist-included
  world-whitelist-excluded-transition world-blacklist-included world-blacklist-excluded-transition
  reload-valid-main-world-cooldown reload-invalid-world-parse-retains-main-world-cooldown
  reload-invalid-world-semantic-retains-main-world-cooldown intentional-empty-items
  protected-container-click-real-protocol protected-number-key-synthetic protected-drag-synthetic
  protected-creative-synthetic protected-container-synthetic protected-drop-synthetic protected-offhand-synthetic
  protected-item-frame-synthetic protected-allay-synthetic protected-armor-stand-synthetic
  death-keepinventory-false-drops-respawn death-keepinventory-true-drops-respawn
  command-player-sender-substitution-normalization command-player-count
  command-console-sender-substitution-normalization command-console-count
  cooldown-between-uses cooldown-reset-after-expiry cooldown-burst-lockout-message
  papi-missing-display-name-fallback papi-missing-lore-fallback papi-missing-command-fallback
  papi-throwing-display-name-fallback papi-throwing-lore-fallback papi-throwing-command-fallback)
for case_name in "${required_cases[@]}"; do
  grep -q "CASE PASS name=$case_name " "$SERVER_DIR/server.log" || { echo "FAIL: missing case $case_name" >&2; exit 1; }
done
! grep -q 'CASE FAIL' "$SERVER_DIR/server.log"
grep -q 'HARNESS RESULT failures=0' "$MCC_LOG"
grep -q 'QA lockout 1' "$MCC_LOG" || { echo 'FAIL: missing real-client cooldown lockout message' >&2; exit 1; }
[[ $(grep -c 'Left clicking slot 44 in window #0' "$MCC_LOG") -eq 2 ]]

printf 'PASS: Paper 26.2 build 129 accepted two real MCC protocol logins.\n'
printf 'PASS: %d named RoyalJoin acceptance assertions passed; real protocol clicks were rejected without moving the locked item.\n' "${#required_cases[@]}"
printf 'Logs: %s and %s\n' "$SERVER_DIR/server.log" "$MCC_LOG"
