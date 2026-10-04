package com.mystipixel.royaljoin;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Puts configured items where they belong, and recognises them again afterwards. */
public final class ItemService {

    private final RoyalJoinPlugin plugin;
    private final NamespacedKey key;

    public ItemService(RoyalJoinPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "item-id");
    }

    public NamespacedKey key() {
        return key;
    }

    /** The configured id stamped on a stack, or null if it isn't one of ours. */
    public String idOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public boolean isOurs(ItemStack stack) {
        return idOf(stack) != null;
    }

    /**
     * Bring a player's hotbar in line with the config: give what they should have, and take back anything
     * of ours they shouldn't (a world they've left, a permission they've lost).
     *
     * <p>Safe to call repeatedly — it replaces rather than accumulates, which is what makes join,
     * respawn and world-change all able to call it without risking duplicates.
     */
    public void apply(Player player) {
        PlayerInventory inv = player.getInventory();
        // Config order, so when two items share a slot the outcome is the same every time.
        Map<String, HotbarItem> wanted = new LinkedHashMap<>();
        for (HotbarItem item : plugin.itemsFor(player.getWorld())) {
            if (item.appliesTo(player)) {
                wanted.put(item.id(), item);
            }
        }

        Set<Integer> reserved = new HashSet<>();
        Map<Integer, ItemStack> replacements = new LinkedHashMap<>();
        for (HotbarItem item : wanted.values()) {
            if (!reserved.add(item.slot())) {
                plugin.getLogger().warning("Cannot refresh inventory for " + player.getName()
                        + ": multiple effective RoyalJoin items target slot " + (item.slot() + 1) + ".");
                return;
            }
            replacements.put(item.slot(), item.build(key, player));
        }

        int storageSize = inv.getStorageContents().length;
        ItemStack[] candidate = plan(inv.getContents(), storageSize, reserved, this::isOurs, replacements);
        if (candidate == null) {
            plugin.getLogger().fine("Inventory full for " + player.getName()
                    + "; retaining their complete inventory rather than partially refreshing it.");
            return;
        }
        inv.setContents(candidate);
    }

    static ItemStack[] plan(ItemStack[] original, int storageSize, Set<Integer> reserved,
                            Predicate<ItemStack> owned, Map<Integer, ItemStack> replacements) {
        ItemStack[] candidate = cloneContents(original);
        for (int i = 0; i < candidate.length; i++) {
            if (owned.test(candidate[i])) candidate[i] = null;
        }
        List<ItemStack> displaced = new ArrayList<>();
        for (int slot : reserved) {
            ItemStack existing = candidate[slot];
            if (existing != null && !existing.getType().isAir()) {
                displaced.add(existing);
            }
            candidate[slot] = null;
        }
        for (ItemStack stack : displaced) {
            if (!relocate(candidate, storageSize, reserved, stack)) {
                return null;
            }
        }
        replacements.forEach((slot, stack) -> candidate[slot] = stack.clone());
        return candidate;
    }

    private static ItemStack[] cloneContents(ItemStack[] original) {
        ItemStack[] copy = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            copy[i] = original[i] == null ? null : original[i].clone();
        }
        return copy;
    }

    static boolean relocate(ItemStack[] contents, int storageSize, Set<Integer> reserved, ItemStack original) {
        ItemStack remaining = original.clone();
        for (int i = 0; i < storageSize && remaining.getAmount() > 0; i++) {
            ItemStack present = contents[i];
            if (reserved.contains(i) || present == null || !present.isSimilar(remaining)) {
                continue;
            }
            int room = present.getMaxStackSize() - present.getAmount();
            if (room > 0) {
                int moved = Math.min(room, remaining.getAmount());
                present.setAmount(present.getAmount() + moved);
                remaining.setAmount(remaining.getAmount() - moved);
            }
        }
        for (int i = 0; i < storageSize && remaining.getAmount() > 0; i++) {
            if (!reserved.contains(i) && (contents[i] == null || contents[i].getType().isAir())) {
                contents[i] = remaining.clone();
                remaining.setAmount(0);
            }
        }
        return remaining.getAmount() == 0;
    }

    /** Remove every item this plugin owns from a player, e.g. on disable so nothing is left behind. */
    public void clear(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isOurs(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
    }

}
