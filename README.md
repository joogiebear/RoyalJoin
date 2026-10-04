# RoyalJoin

Pins items to players' hotbar slots. Clicking one runs a command — usually to open a menu, so players
don't have to remember commands.

Out of the box: a nether star in the far-right hotbar slot that runs `/menu`. Everything about that —
the slot, the item, its name and lore, the command, which worlds it appears in — is config.

`/menu` is not supplied by RoyalJoin. Install a menu plugin that registers that command, or change
`items.menu.command` to a command your server already provides.

Part of the Royal plugin suite, but deliberately independent of it: the command it runs is just a
command, so it works the same whether that opens a menu from this suite, another plugin's GUI, or a
warp.

---

## Installation

1. Stop the server and place the RoyalJoin jar in the server's `plugins/` directory.
2. Start the server once. RoyalJoin creates `plugins/RoyalJoin/config.yml` and
   `plugins/RoyalJoin/worlds/_example.yml`.
3. Edit `config.yml` and, if needed, add per-world files under `plugins/RoyalJoin/worlds/`.
4. Run `/royaljoin reload` after configuration-only changes. Restart the server after replacing the
   jar or changing installed dependencies.

[PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) is optional. When it is
installed and enabled, PlaceholderAPI placeholders in item names, lore and commands are resolved.
Without it, RoyalJoin still runs, but those placeholder strings remain unchanged. RoyalJoin's own
`%player%` command placeholder does not require PlaceholderAPI.

### If the menu item does not appear or `/menu` does nothing

1. Check the startup log for RoyalJoin configuration warnings and confirm the plugin is enabled.
2. Confirm the player is in an allowed world, has the item's configured `permission`, and has room
   for an item already occupying the configured slot to be moved.
3. Run the configured command directly as the player. For the default `menu` command, confirm another
   enabled plugin actually registers `/menu`.
4. After correcting configuration, run `/royaljoin reload`; after installing or replacing a plugin
   jar, restart the server.

---

## Configuration

### `config.yml`

```yaml
items:
  menu:                       # the key is this item's id
    slot: 9                   # hotbar position, 1-9 left to right
    material: NETHER_STAR
    name: "&6&lMenu"
    lore:
      - "&7Right-click to open the menu."
    command: "menu"           # no leading slash; %player% becomes their name
    as-console: false         # true runs it from console, for commands players can't use
    click: right              # right, left, or either
    permission: ""            # empty gives it to everyone; re-checked on every click
    worlds: []                # empty means every world
    world-mode: blacklist     # blacklist = all except those listed; whitelist = only those
    locked: true              # can't be moved, dropped, stored, swapped to the off-hand or framed
    glow: false
```

Add more entries under `items:` for more items.

### Per-world items — `worlds/<world>.yml`

`config.yml` is the catch-all: its items apply in every world, which is all most servers need. A world
only needs a file when it wants something different.

Name the file after the world — `farm.yml` for a world called `farm` — and it takes over there:

```yaml
inherit-default: false        # false replaces the config.yml items for this world
                              # true  gives the config.yml items PLUS these

items:
  farm-menu:
    slot: 9
    material: WHEAT
    name: "&aFarming Menu"
    command: "farmmenu"
```

Anything without a file of its own falls back to `config.yml`, so a hub, an end world and dynamically
named worlds all work with no configuration. Files starting with `_` are examples and ignored.

Two ways to scope an item, worth choosing deliberately:

- **`worlds:` on an item in `config.yml`** — one shared item, hidden in (or limited to) named worlds
- **`worlds/<world>.yml`** — a whole different set for that world

Worlds are matched by exact name. There's no pattern matching, so a world whose name isn't known ahead
of time — one generated per player, for example — always uses the `config.yml` items.

### Rate limiting

```yaml
cooldown:
  between-uses-ms: 400        # minimum gap between activations
  spam-threshold: 6           # this many uses...
  spam-window-ms: 3000        # ...inside this window trips a lockout
  lockout-seconds: 5          # clicks ignored for this long
  message: "&cEasy — that's on cooldown for %seconds%s."
```

Two stages, because one isn't enough: a plain delay doesn't stop an auto-clicker, it just paces it at
exactly the delay. The burst guard catches that pattern and stops it for a few seconds.

The message is sent **once**, when the lockout starts — messaging every blocked click would turn an
auto-clicker into chat spam, which is worse than what's being prevented. Set it to `""` for silence.
Ordinary use never accumulates toward a lockout: the window slides, counting only the uses inside
the last `spam-window-ms`.

---

## Commands

```text
/royaljoin reload     Reload config and per-world files, and refresh everyone online
```

Alias: `/rj`.

## Permissions

```text
royaljoin.admin   default: op   /royaljoin reload
```

---

## Behaviour worth knowing

**Inventory refreshes are planned before the live inventory is changed.** The plan removes old
RoyalJoin-tagged items, places the newly configured items, and relocates ordinary items displaced
from configured slots. If every displaced ordinary item cannot fit, the refresh is rejected and the
inventory remains unchanged, including the old tagged items. This rollback behavior has been tested
with a full inventory and a metadata-rich old tagged item; still test representative inventories on
a staging server and keep backups.

**Items are re-applied on join, respawn and world change.** When planning a re-apply, RoyalJoin removes
its old items from the proposed result, wherever they ended up. The live inventory is replaced only
after the whole plan succeeds. So duplicates can't accumulate, and changing a slot in config doesn't
leave the old copy behind. World change matters more than it looks: it's also what fires when another
plugin moves a player between worlds, so items survive things this plugin knows nothing about.

**Permission and world are checked at the click, too.** A player who loses an item's permission
mid-session — an expired rank, say — can't keep using it; the click takes it back instead.

**Items never drop on death.** They're taken out of the death drops before grave or death-chest
plugins see them, and respawning hands out a fresh copy. (The old `keep-on-death` option is no longer
read — leaving it in your config does nothing.)

**Two items in one slot get a warning at load.** The later one in the config pushes the earlier to a
free slot, which is almost never what was meant.

**Treat `as-console` commands as privileged input.** Prefer fixed commands and RoyalJoin's `%player%`
placeholder, which is replaced with the clicking player's account name. Do not put PlaceholderAPI
values that players can control (for example nicknames, display names or chat input) into a console
command. Test the final command with an unprivileged account, and use `as-console: false` unless the
target command genuinely requires console permissions.

**Items are identified by a tag, not by material or name.** Renaming one, or configuring two items
that share a material, doesn't confuse it.

**An item the plugin can no longer resolve stays locked.** If you delete an item from config, existing
copies can't be stashed in a chest or sold — `/royaljoin reload` removes them properly.

**On disable, items are taken back**, so they don't persist as ordinary items to be duplicated at next
startup.

### Interaction with per-profile inventories

If another plugin swaps a player's whole inventory — a per-profile skyblock system, for example — it
may capture this plugin's item into its saved inventory and hand it back on a different profile,
producing duplicates or losing it. Where that plugin can exclude a slot from its snapshot, exclude the
one used here.

For RoyalSkyblock, that is:

```yaml
profile:
  externally-managed-hotbar-slots: [9]
```

Those slots are left out of the profile snapshot on save and untouched on load, so the item stays put
across a profile switch.

---

## Metrics

Reports anonymous usage to [bStats](https://bstats.org/plugin/bukkit/RoyalJoin/33888). Turn it off for
the whole server in `plugins/bStats/config.yml`.

---

## Building

```bash
mvn clean package     # target/RoyalJoin.jar
```

Building requires JDK 25 or newer because the configured `paper-api 26.2.build.123-stable` dependency
uses Java 25 class files. RoyalJoin's own classes are emitted as Java 21 bytecode via
`maven.compiler.release`, and the plugin declares Bukkit/Paper API version `26.2`.

The Java 21 bytecode target is not a verified Java 21 runtime claim: the server and its Paper API must
also support that runtime.

The final reliability check passed **43 required live-harness assertions** on Paper 26.2 build 129
with Eclipse Temurin Java 25.0.4.1+1, in addition to the prior 21 automated tests. Login and reconnect,
container clicks, and use-item checks used real client protocol actions; death and respawn were real
server lifecycle events. Fixture and configuration changes, permissions, snapshots, teleport,
reflection, and PlaceholderAPI fault injection were server-side harness actions. Drag, number-key,
creative, drop, offhand, item-frame, allay, and armor-stand protection checks used synthetic Bukkit
events rather than client gestures. See the [actual-player harness documentation](qa/player-harness/README.md)
for the test setup and coverage.

These results are evidence for that exact Paper and Java environment, not a broad compatibility
guarantee. Other Minecraft versions, Paper builds, server implementations, Java versions, unrelated
integrations, and load or performance behavior were not verified. Test upgrades on a staging server
with backups.
