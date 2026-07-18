package com.mystipixel.royaljoin;

import com.mystipixel.royaljoin.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * One configured hotbar item: what it looks like, where it sits, and what clicking it does.
 *
 * <p>Everything here comes from config — nothing about the item, its slot or its command is fixed in
 * code, so a server can pin whatever it likes wherever it likes.
 */
public final class HotbarItem {

    /** Which click opens it. */
    public enum ClickType {
        RIGHT, LEFT, EITHER;

        static ClickType parse(String raw, Logger logger, String id) {
            if (raw == null || raw.isBlank()) {
                return RIGHT;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                logger.warning("Item '" + id + "' has click: " + raw + ", which isn't right/left/either."
                        + " Using right.");
                return RIGHT;
            }
        }
    }

    private final String id;
    private final int slot;              // 0-8, resolved from the 1-9 written in config
    private final Material material;
    private final String name;
    private final List<String> lore;
    private final String command;
    private final boolean asConsole;
    private final String permission;     // empty = everyone
    private final List<String> worlds;   // empty = every world
    private final boolean whitelist;     // how the world list is read
    private final boolean locked;        // can't be moved, dropped or stored
    private final boolean keepOnDeath;
    private final ClickType click;
    private final boolean glow;

    private HotbarItem(String id, int slot, Material material, String name, List<String> lore, String command,
                       boolean asConsole, String permission, List<String> worlds, boolean whitelist,
                       boolean locked, boolean keepOnDeath, ClickType click, boolean glow) {
        this.id = id;
        this.slot = slot;
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.command = command;
        this.asConsole = asConsole;
        this.permission = permission;
        this.worlds = worlds;
        this.whitelist = whitelist;
        this.locked = locked;
        this.keepOnDeath = keepOnDeath;
        this.click = click;
        this.glow = glow;
    }

    /**
     * Read one item from its config section. Returns null and explains why if the section can't produce a
     * usable item, so one bad entry doesn't stop the rest loading.
     */
    public static HotbarItem load(String id, ConfigurationSection sec, Logger logger) {
        String rawMaterial = sec.getString("material", "NETHER_STAR");
        Material material = Material.matchMaterial(rawMaterial);
        if (material == null || !material.isItem()) {
            logger.warning("Item '" + id + "' has material: " + rawMaterial + ", which isn't a valid item."
                    + " Skipping it.");
            return null;
        }

        // Config counts hotbar slots 1-9 left to right; the inventory indexes them 0-8.
        int configured = sec.getInt("slot", 9);
        if (configured < 1 || configured > 9) {
            logger.warning("Item '" + id + "' has slot: " + configured + ", outside the hotbar (1-9)."
                    + " Using slot 9.");
            configured = 9;
        }

        String command = sec.getString("command", "");
        if (command.isBlank()) {
            logger.warning("Item '" + id + "' has no command, so clicking it would do nothing. Skipping it.");
            return null;
        }

        return new HotbarItem(
                id,
                configured - 1,
                material,
                sec.getString("name", "&f" + id),
                sec.getStringList("lore"),
                command.startsWith("/") ? command.substring(1) : command,
                sec.getBoolean("as-console", false),
                sec.getString("permission", ""),
                sec.getStringList("worlds"),
                "whitelist".equalsIgnoreCase(sec.getString("world-mode", "blacklist")),
                sec.getBoolean("locked", true),
                sec.getBoolean("keep-on-death", true),
                ClickType.parse(sec.getString("click", "right"), logger, id),
                sec.getBoolean("glow", false));
    }

    /** Build the item, tagged so it can be recognised later regardless of how it was renamed. */
    public ItemStack build(org.bukkit.NamespacedKey key) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.item(name));
            if (!lore.isEmpty()) {
                List<net.kyori.adventure.text.Component> lines = new ArrayList<>(lore.size());
                for (String line : lore) {
                    lines.add(Text.item(line));
                }
                meta.lore(lines);
            }
            if (glow) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            // The tag is what identifies our item. Matching on material or name would break the moment a
            // server configures two items sharing a material, or renames one.
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Whether this item should exist for the player right now, in the world they're standing in. */
    public boolean appliesTo(Player player) {
        if (!permission.isBlank() && !player.hasPermission(permission)) {
            return false;
        }
        if (worlds.isEmpty()) {
            return true;
        }
        boolean listed = worlds.stream().anyMatch(w -> w.equalsIgnoreCase(player.getWorld().getName()));
        return whitelist == listed;
    }

    public String id() { return id; }
    public int slot() { return slot; }
    public String command() { return command; }
    public boolean asConsole() { return asConsole; }
    public boolean locked() { return locked; }
    public boolean keepOnDeath() { return keepOnDeath; }
    public ClickType click() { return click; }
}
