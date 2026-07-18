package com.mystipixel.royaljoin;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

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
        Inventory inv = player.getInventory();
        Map<String, HotbarItem> wanted = new HashMap<>();
        for (HotbarItem item : plugin.itemsFor(player.getWorld())) {
            if (item.appliesTo(player)) {
                wanted.put(item.id(), item);
            }
        }

        // Clear out our items first, wherever they ended up, so a slot change in config doesn't leave the
        // old copy behind and a player who lost access doesn't keep it.
        for (int i = 0; i < inv.getSize(); i++) {
            String id = idOf(inv.getItem(i));
            if (id != null) {
                inv.setItem(i, null);
            }
        }

        for (HotbarItem item : wanted.values()) {
            place(player, inv, item);
        }
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

    /**
     * Put the item in its slot, moving anything already there somewhere safe.
     *
     * <p>A player's own item is never destroyed to make room: if the inventory is full, the configured
     * item is skipped this time rather than costing them something they were carrying. It gets another
     * chance on their next respawn or world change.
     */
    private void place(Player player, Inventory inv, HotbarItem item) {
        int slot = item.slot();
        ItemStack existing = inv.getItem(slot);
        if (existing != null && !existing.getType().isAir()) {
            Map<Integer, ItemStack> leftover = inv.addItem(existing.clone());
            if (!leftover.isEmpty()) {
                plugin.getLogger().fine("Inventory full for " + player.getName() + "; leaving slot "
                        + (slot + 1) + " alone rather than dropping their item.");
                return;
            }
        }
        inv.setItem(slot, item.build(key));
    }

}
