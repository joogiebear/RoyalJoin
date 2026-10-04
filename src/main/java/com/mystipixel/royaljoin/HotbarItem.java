package com.mystipixel.royaljoin;

import com.mystipixel.royaljoin.util.Papi;
import com.mystipixel.royaljoin.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

        static ClickType parse(String raw, String id) throws ConfigException {
            if (raw == null || raw.isBlank()) {
                return RIGHT;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ConfigException("item '" + id + "': click '" + raw
                        + "' must be right, left, or either");
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
    private final ClickType click;
    private final boolean glow;
    private final int customModelData;   // -1 = none

    private HotbarItem(String id, int slot, Material material, String name, List<String> lore, String command,
                       boolean asConsole, String permission, List<String> worlds, boolean whitelist,
                       boolean locked, ClickType click, boolean glow,
                       int customModelData) {
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
        this.click = click;
        this.glow = glow;
        this.customModelData = customModelData;
    }

    /**
     * Read and validate one item from its config section.
     */
    public static HotbarItem load(String id, ConfigurationSection sec) throws ConfigException {
        String rawMaterial = stringValue(sec, "material", "NETHER_STAR", id);
        Material material = Material.matchMaterial(rawMaterial);
        if (material == null || !material.isItem()) {
            throw new ConfigException("item '" + id + "': material '" + rawMaterial + "' is not a valid item");
        }

        // Config counts hotbar slots 1-9 left to right; the inventory indexes them 0-8.
        int configured = intValue(sec, "slot", 9, id);
        if (configured < 1 || configured > 9) {
            throw new ConfigException("item '" + id + "': slot " + configured + " is outside 1-9");
        }

        String command = normalizeCommand(stringValue(sec, "command", "", id), id);
        String rawWorldMode = normalizeWorldMode(stringValue(sec, "world-mode", "blacklist", id), id);

        return new HotbarItem(
                id,
                configured - 1,
                material,
                stringValue(sec, "name", "&f" + id, id),
                stringList(sec, "lore", id),
                command,
                booleanValue(sec, "as-console", false, id),
                stringValue(sec, "permission", "", id),
                stringList(sec, "worlds", id),
                rawWorldMode.equals("whitelist"),
                booleanValue(sec, "locked", true, id),
                ClickType.parse(stringValue(sec, "click", "right", id), id),
                booleanValue(sec, "glow", false, id),
                intValue(sec, "custom-model-data", -1, id));
    }

    /**
     * Build the item for this player, tagged so it can be recognised later regardless of renames.
     * Name and lore go through PlaceholderAPI (when installed), so they are as fresh as the last
     * apply — join, respawn, world change or reload.
     */
    public ItemStack build(NamespacedKey key, Player player) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.item(Papi.apply(player, name)));
            if (!lore.isEmpty()) {
                List<Component> lines = new ArrayList<>(lore.size());
                for (String line : lore) {
                    lines.add(Text.item(Papi.apply(player, line)));
                }
                meta.lore(lines);
            }
            if (glow) {
                meta.setEnchantmentGlintOverride(true);
            }
            if (customModelData >= 0) {
                meta.setCustomModelData(customModelData);
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
    public ClickType click() { return click; }
    boolean whitelist() { return whitelist; }

    static String normalizeCommand(String raw, String id) throws ConfigException {
        String command = raw == null ? "" : raw.trim();
        if (command.startsWith("/")) command = command.substring(1).trim();
        if (command.isBlank() || command.chars().allMatch(character -> character == '/')) {
            throw new ConfigException("item '" + id + "': command is blank after normalization");
        }
        return command;
    }

    static String normalizeWorldMode(String raw, String id) throws ConfigException {
        String mode = raw == null ? "blacklist" : raw.trim().toLowerCase(Locale.ROOT);
        if (!mode.equals("whitelist") && !mode.equals("blacklist")) {
            throw new ConfigException("item '" + id + "': world-mode '" + mode
                    + "' must be whitelist or blacklist");
        }
        return mode;
    }

    private static String stringValue(ConfigurationSection sec, String path, String fallback, String id)
            throws ConfigException {
        if (!sec.contains(path)) return fallback;
        Object raw = sec.get(path);
        if (!(raw instanceof String value)) {
            throw new ConfigException("item '" + id + "': " + path + " must be text");
        }
        return value;
    }

    private static int intValue(ConfigurationSection sec, String path, int fallback, String id)
            throws ConfigException {
        if (!sec.contains(path)) return fallback;
        Object raw = sec.get(path);
        if (!(raw instanceof Number number) || number.doubleValue() != number.intValue()) {
            throw new ConfigException("item '" + id + "': " + path + " must be an integer");
        }
        return number.intValue();
    }

    private static boolean booleanValue(ConfigurationSection sec, String path, boolean fallback, String id)
            throws ConfigException {
        if (!sec.contains(path)) return fallback;
        Object raw = sec.get(path);
        if (!(raw instanceof Boolean value)) {
            throw new ConfigException("item '" + id + "': " + path + " must be true or false");
        }
        return value;
    }

    private static List<String> stringList(ConfigurationSection sec, String path, String id)
            throws ConfigException {
        if (!sec.contains(path)) return List.of();
        Object raw = sec.get(path);
        if (!(raw instanceof List<?> values) || values.stream().anyMatch(value -> !(value instanceof String))) {
            throw new ConfigException("item '" + id + "': " + path + " must be a list of text values");
        }
        return values.stream().map(String.class::cast).toList();
    }
}
