# RoyalJoin

Pins items to players' hotbar slots. Clicking one runs a command — usually to open a menu, so players
don't have to remember commands.

Out of the box: a nether star in the far-right hotbar slot that runs `/menu`. Everything about that —
the slot, the item, its name and lore, the command, which worlds it appears in — is config.

Part of the Royal plugin suite, but deliberately independent of it: the command it runs is just a
command, so it works the same whether that opens a menu from this suite, another plugin's GUI, or a
warp.

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
    permission: ""            # empty gives it to everyone
    worlds: []                # empty means every world
    world-mode: blacklist     # blacklist = all except those listed; whitelist = only those
    locked: true              # can't be moved, dropped, stored or swapped to the off-hand
    keep-on-death: true
    glow: false
```

Add more entries under `items:` for more items.

### Per-world items — `worlds/<world>.yml`

Name a file after a world and it defines that world's items:

```yaml
inherit-default: false        # false replaces the config.yml items, true adds to them

items:
  farm-menu:
    slot: 9
    material: WHEAT
    name: "&aFarming Menu"
    command: "farmmenu"
```

Worlds with no file use `config.yml`, so if one setup suits every world you never need this folder.
Files starting with `_` are examples and ignored.

Two ways to scope an item, worth choosing deliberately:

- **`worlds:` on an item** — one shared item, hidden in some worlds
- **`worlds/<world>.yml`** — a whole different kit for that world

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
Ordinary use never accumulates toward a lockout; the window rolls forward once it elapses.

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

**It never destroys a player's item.** If something is already in the configured slot, that item is
moved to a free slot first. If the inventory is full, the configured item is skipped rather than
costing the player something they were carrying — it gets another chance on their next respawn or
world change.

**Items are re-applied on join, respawn and world change**, and re-applying clears the plugin's items
first, wherever they ended up. So duplicates can't accumulate, and changing a slot in config doesn't
leave the old copy behind. World change matters more than it looks: it's also what fires when another
plugin moves a player between worlds, so items survive things this plugin knows nothing about.

**Items are identified by a tag, not by material or name.** Renaming one, or configuring two items
that share a material, doesn't confuse it.

**An item the plugin can no longer resolve stays locked.** If you delete an item from config, existing
copies can't be stashed in a chest or sold — `/royaljoin reload` removes them properly.

**On disable, items are taken back**, so they don't persist as ordinary items to be duplicated at next
startup.

### Interaction with per-profile inventories

If another plugin swaps a player's whole inventory — a per-profile skyblock system, for example — it
may capture this plugin's item into its saved inventory and hand it back later, producing duplicates
or losing it. Where that plugin can exclude a slot from its snapshot, exclude the one used here.

---

## Building

```bash
mvn clean package     # target/RoyalJoin.jar
```

Requires JDK 25 to build (paper-api 26.2 ships Java 25 bytecode); runs on Java 21+.
