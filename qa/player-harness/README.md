# Minecraft 26.2 actual-player harness

This QA-only harness starts **Paper 26.2 build 129** on `127.0.0.1:25571`, logs in a real
protocol client twice, and runs named RoyalJoin-owned inventory assertions. It never adds test code to
`target/RoyalJoin.jar`. Runtime state is deleted and recreated for every run, and traps stop Paper
when the script exits.

## Pinned tools

- Paper `26.2-129` (`b1d8f6…eb083`), implementing protocol 776.
- Minecraft Console Client `20260929-519`, build 519 from commit `4fbe90c` (`a60a5a…7b9cb`).
- Eclipse Temurin JDK `25.0.4.1+1` and Apache Maven `3.9.9`.
- QA helper `4.0.0`, compiled against `paper-api 26.2.build.129-stable`.

All binaries are free. The setup script pins immutable artifact URLs and verifies published SHA-256
values for Paper, MCC, and Maven. JDK installation uses Adoptium's immutable version endpoint.

## Run

On Linux x86-64 with `curl`, `tar`, and `sha256sum`:

```bash
qa/player-harness/setup.sh
qa/player-harness/run-smoke.sh
```

To keep downloaded tools outside the checkout or runtime in a specific scratch directory:

```bash
ROYALJOIN_HARNESS_TOOLS=/path/to/cache \
ROYALJOIN_HARNESS_RUNTIME=/path/to/scratch \
qa/player-harness/run-smoke.sh
```

The server is deliberately offline, binds only to loopback, disables RCON/query, and uses the fixed
identity `RoyalJoinQA`. Do not point this configuration at a public server. `DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1`
allows MCC to run on minimal CI images without ICU.

The runner exits nonzero for any missing or failed named case. Full-state snapshots use Paper's
binary ItemStack serialization, covering quantity, display name, lore, flags, enchantments, custom
model data, and every PDC entry. It covers configured item state on join and reconnect, repeated
refresh, displacement/partial merge, exact full-inventory rollback,
multiple/changed slots, deterministic conflicts, configured permission, whitelist/blacklist
transitions, valid reloads plus parse-invalid and semantic-invalid live world-file reloads with direct
last-good main/world/cooldown retention assertions, empty reloads, death drops and respawn inventories with `keepInventory`
off and on, and command dispatch/rate limiting from real protocol use-item packets. It also verifies
the lockout message in client chat and two real protocol container clicks on the locked item.

The QA-only `papi-fault` plugin deliberately supplies PlaceholderAPI's binary entry point. The helper
disables it to exercise the missing-plugin path, then enables a throwing implementation to prove that
RoyalJoin's apply path retains literal placeholders instead of failing inventory refresh. Neither QA
plugin is copied into or shaded into `target/RoyalJoin.jar`. Both missing-plugin and throwing-PAPI
cases assert literal fallback for the configured display name, lore, and command; command fallback
is exercised by a real protocol use-item.

## Manual coverage

Keep Paper running by copying the setup portion of `run-smoke.sh`, then type commands into MCC. MCC
internal `/inventory` reads the client inventory; server commands begin with `/harness`:

- `/harness assert-join`, `/harness refresh`, `/harness inventory`
- `/harness permission`, `/harness world`
- `/harness reload-valid`, `/harness reload-invalid`, `/harness empty`
- `/harness protect`, `/harness papi`, `/harness death false`, `/harness death true`
- `/harness commands player|console|cooldown|burst`
- `/harness dump`, `/harness result`

From the Paper console, the same controls accept a player name after the action, for example
`harness assert-join RoyalJoinQA`.

## Coverage boundary

Login, reconnect, synchronization, container clicks, and use-item actions are real protocol actions.
Death and respawn are real server lifecycle events initiated by the helper. Config/permission
mutation, snapshots, counters, PDC inspection, reload, teleport, and PlaceholderAPI fault injection
are server-side fixtures. Drag, number-key, creative, drop, offhand, item-frame, allay, and armor-stand
protection cases are explicitly logged as `protocol=synthetic-bukkit`; each calls the normal Paper
event bus and asserts both cancellation and exact player-inventory conservation. They are not
represented as actual-client gesture proof.
