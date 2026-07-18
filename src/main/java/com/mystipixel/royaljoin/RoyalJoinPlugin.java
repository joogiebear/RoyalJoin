package com.mystipixel.royaljoin;

import com.mystipixel.royaljoin.command.RoyalJoinCommand;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pins configured items to hotbar slots and runs a command when they're clicked.
 *
 * <p>Deliberately knows nothing about any other plugin: the command it runs is just a command, so it
 * works the same whether that opens a menu from this suite, another plugin's GUI, or a warp.
 */
public final class RoyalJoinPlugin extends JavaPlugin {

    /** Items from config.yml — the catch-all, used by any world without a file of its own. */
    private final Map<String, HotbarItem> defaults = new LinkedHashMap<>();
    /** world name (lowercase) → that world's items, from worlds/<world>.yml. */
    private final Map<String, Map<String, HotbarItem>> perWorld = new LinkedHashMap<>();
    /** world name (lowercase) → whether its file adds to the config.yml items rather than replacing them. */
    private final Map<String, Boolean> inheritsDefault = new LinkedHashMap<>();
    private ItemService itemService;
    private final CooldownTracker cooldowns = new CooldownTracker();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.itemService = new ItemService(this);
        reloadItems();

        getServer().getPluginManager().registerEvents(new JoinListener(this, itemService), this);

        RoyalJoinCommand command = new RoyalJoinCommand(this);
        if (getCommand("royaljoin") != null) {
            getCommand("royaljoin").setExecutor(command);
            getCommand("royaljoin").setTabCompleter(command);
        }

        // Covers /reload and a mid-session install, where nobody will fire a join event.
        for (Player player : Bukkit.getOnlinePlayers()) {
            itemService.apply(player);
        }

        getLogger().info("RoyalJoin enabled — " + itemCount() + " item(s) configured.");
    }

    @Override
    public void onDisable() {
        // Take our items back rather than leaving them behind as ordinary items that would then be
        // duplicated by the next startup, or sold, or dropped.
        if (itemService != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                itemService.clear(player);
            }
        }
    }

    /**
     * Re-read every item definition.
     *
     * <p>config.yml holds the items that apply everywhere — the catch-all. A world only needs a file in
     * worlds/ when it wants something different, so a server with one setup never touches that folder.
     *
     * <p>Invalid entries are skipped with a reason rather than being fatal.
     */
    public void reloadItems() {
        reloadConfig();
        cooldowns.configure(
                getConfig().getLong("cooldown.between-uses-ms", 400),
                getConfig().getInt("cooldown.spam-threshold", 6),
                getConfig().getLong("cooldown.spam-window-ms", 3000),
                getConfig().getLong("cooldown.lockout-seconds", 5));

        defaults.clear();
        perWorld.clear();
        inheritsDefault.clear();

        readItems(getConfig().getConfigurationSection("items"), defaults, "config.yml");
        if (defaults.isEmpty()) {
            getLogger().warning("No usable items in config.yml — only worlds with their own file will"
                    + " get anything.");
        }
        loadWorldFiles();
    }

    /** Load worlds/<world>.yml. Files starting with _ are examples and skipped. */
    private void loadWorldFiles() {
        File folder = new File(getDataFolder(), "worlds");
        if (!folder.isDirectory()) {
            if (!folder.mkdirs()) {
                return;
            }
            saveResource("worlds/_example.yml", false);
            return;
        }
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String fileName = file.getName();
            if (fileName.startsWith("_")) {
                continue;
            }
            String world = fileName.substring(0, fileName.length() - 4).toLowerCase(Locale.ROOT);
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            Map<String, HotbarItem> loaded = new LinkedHashMap<>();
            readItems(cfg.getConfigurationSection("items"), loaded, "worlds/" + fileName);
            perWorld.put(world, loaded);
            inheritsDefault.put(world, cfg.getBoolean("inherit-default", false));
        }
        if (!perWorld.isEmpty()) {
            getLogger().info("Per-world items: " + String.join(", ", perWorld.keySet()) + ".");
        }
    }

    private void readItems(ConfigurationSection section, Map<String, HotbarItem> into, String source) {
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            HotbarItem item = HotbarItem.load(id, entry, getLogger());
            if (item == null) {
                getLogger().warning("  (skipped item '" + id + "' from " + source + ")");
                continue;
            }
            into.put(id, item);
        }
    }

    /** The items a world should show: its own file if it has one, otherwise the defaults. */
    public List<HotbarItem> itemsFor(World world) {
        if (world == null) {
            return new ArrayList<>(defaults.values());
        }
        String key = world.getName().toLowerCase(Locale.ROOT);
        Map<String, HotbarItem> specific = perWorld.get(key);
        if (specific == null) {
            return new ArrayList<>(defaults.values());
        }
        if (!inheritsDefault.getOrDefault(key, false)) {
            return new ArrayList<>(specific.values());
        }
        // inherit-default: the world's own entries win where ids collide.
        Map<String, HotbarItem> merged = new LinkedHashMap<>(defaults);
        merged.putAll(specific);
        return new ArrayList<>(merged.values());
    }

    /** Look up an item by id for the world a player is in, so per-world overrides win. */
    public HotbarItem item(World world, String id) {
        if (world != null) {
            Map<String, HotbarItem> specific = perWorld.get(world.getName().toLowerCase(Locale.ROOT));
            if (specific != null && specific.containsKey(id)) {
                return specific.get(id);
            }
        }
        return defaults.get(id);
    }

    /** Every item defined anywhere, for the reload summary. */
    public int itemCount() {
        return defaults.size() + perWorld.values().stream().mapToInt(Map::size).sum();
    }

    /** Extra console output while working out why a click isn't doing what you expect. */
    public boolean debug() {
        return getConfig().getBoolean("settings.debug", false);
    }

    public CooldownTracker cooldowns() {
        return cooldowns;
    }

    public ItemService itemService() {
        return itemService;
    }
}
