# ROY-45 harness evidence

## Scope

Harness-only remediation for the full-metadata, atomic reload, and PlaceholderAPI fallback evidence gaps identified in ROY-43. No production source was changed and nothing was published.

## Revisions and artifacts

- Baseline source revision: `c0497dda9e174cc0176993e375096b8d6f38bd45`
- Paper: `26.2-129`
- Paper SHA-256: `b1d8f6bfa1b6101fa8e947b53041cb3bdf5540e7b83b6547ca19ba7edefeb083`
- Production `RoyalJoin.jar` SHA-256: `e573f10d25c6d045a81dd350c427ed824e2a43f9e7bee86a897deffb09a38784`
- QA helper SHA-256: `0342fe7d84af539b94957ff0b6aab05b444250fb631b056090ed88782d8e21cb`

The final commit revision is recorded in the ROY-45 task comment/work product because this file is part of that commit.

## Exact verification

The runner was invoked from a recreated run-owned runtime/cache:

```text
ROYALJOIN_HARNESS_RUNTIME=$PAPERCLIP_RUN_SCRATCH_DIR/roy45-runtime qa/player-harness/run-smoke.sh
```

Result:

```text
PASS: Paper 26.2 build 129 accepted two real MCC protocol logins.
PASS: 42 named RoyalJoin acceptance assertions passed; real protocol clicks were rejected without moving the locked item.
```

The server log contained 51 total `CASE PASS` records because join, command-fixture, and sender checks intentionally execute more than once. It contained zero `CASE FAIL` records and two real `RoyalJoinQA joined the game` records.

## Added evidence

- Join and reconnect directly assert configured material, quantity, display name, lore, the exact PDC key set, and `royaljoin:item-id` value.
- Inventory conservation/idempotence comparisons use Paper's complete binary `ItemStack` serialization. Logged state includes display name, lore, quantity, flags, enchantments, custom model data, every PDC key, and a SHA-256 of the complete serialized stack.
- Live parse-invalid and semantic-invalid `worlds/world.yml` reloads both return failure. Each directly compares the previous inventory snapshot and reflected active main item, world item, and cooldown values (`17`, `2`, `50`, `1000`) before/after rejection.
- Missing and throwing PlaceholderAPI fixtures assert literal fallback independently for display name and lore. A real MCC use-item packet proves the literal `%qa_value%` also reaches the configured command.
- Existing evidence labels remain explicit: `protocol=real-client`, `protocol=real-container-click`, `protocol=real-use-item`, `protocol=server-fixture`, `protocol=lifecycle`, and `protocol=synthetic-bukkit`.

Representative passing cases:

```text
join-configured-full-metadata
repeated-refresh-idempotent-full-state
reload-valid-main-world-cooldown
reload-invalid-world-parse-retains-main-world-cooldown
reload-invalid-world-semantic-retains-main-world-cooldown
papi-missing-display-name-fallback
papi-missing-lore-fallback
papi-missing-command-fallback
papi-throwing-display-name-fallback
papi-throwing-lore-fallback
papi-throwing-command-fallback
```

## Negative control

The required-case gate was queried for a deliberately absent case:

```text
grep -q 'CASE PASS name=negative-control-must-not-exist ' server.log
exit=1
```

The nonzero result confirms a missing named case is rejected.
