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

## Building

```bash
mvn clean package     # target/RoyalJoin.jar
```

Requires JDK 25 to build (paper-api 26.2 ships Java 25 bytecode); runs on Java 21+.
