package com.mystipixel.royaljoin;

import com.mystipixel.royaljoin.command.RoyalJoinCommand;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/**
 * Pins configured items to hotbar slots and runs a command when they're clicked. Knows nothing about
 * other plugins: the command is just a command, whether it opens a menu, another plugin's GUI or a warp.
 */
public final class RoyalJoinPlugin extends JavaPlugin {

    // identifies the plugin, not the server, so it is fixed rather than configurable
    private static final int BSTATS_PLUGIN_ID = 33888;

    record ActiveConfig(Map<String, HotbarItem> defaults,
                                Map<String, Map<String, HotbarItem>> perWorld,
                                Map<String, Boolean> inheritsDefault,
                                long betweenUsesMillis, int spamThreshold,
                                long spamWindowMillis, long lockoutSeconds,
                                String cooldownMessage, boolean debug) {}

    public record ReloadResult(boolean success, String error) {}

    private ActiveConfig active = new ActiveConfig(Map.of(), Map.of(), Map.of(),
            400, 6, 3000, 5, "", false);
    private ItemService itemService;
    private final CooldownTracker cooldowns = new CooldownTracker();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.itemService = new ItemService(this);
        ReloadResult initial = reloadItems();
        if (!initial.success()) {
            getLogger().severe("RoyalJoin configuration was not activated: " + initial.error());
        }

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

        setupMetrics();
        getLogger().info("RoyalJoin enabled: " + itemCount() + " item(s) configured.");
    }

    // opting out is done globally in plugins/bStats/config.yml
    private void setupMetrics() {
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("item_count", () -> String.valueOf(itemCount())));
        // per-world overrides are the feature most likely to be unused, so track whether anyone uses them
        metrics.addCustomChart(new SimplePie("per_world_items",
                () -> String.valueOf(!active.perWorld().isEmpty())));
        metrics.addCustomChart(new SimplePie("placeholderapi",
                () -> String.valueOf(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))));
    }

    @Override
    public void onDisable() {
        // take our items back, or they stay behind as ordinary items to be duplicated, sold or dropped
        if (itemService != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                itemService.clear(player);
            }
        }
    }

    /**
     * Re-read config.yml and worlds/*.yml. The whole candidate is validated before it replaces the live
     * configuration, so a rejected reload keeps the last-good one.
     */
    public ReloadResult reloadItems() {
        try {
            ActiveConfig candidate = loadCandidate(getDataFolder());
            active = candidate;
            cooldowns.configure(candidate.betweenUsesMillis(), candidate.spamThreshold(),
                    candidate.spamWindowMillis(), candidate.lockoutSeconds());
            ensureWorldFolder();
            warnSlotClashes(new ArrayList<>(candidate.defaults().values()), candidate.defaults().keySet(),
                    "config.yml");
            for (String world : candidate.perWorld().keySet()) {
                warnSlotClashes(itemsForKey(candidate, world), candidate.perWorld().get(world).keySet(),
                        "worlds/" + world + ".yml");
            }
            return new ReloadResult(true, null);
        } catch (ConfigException e) {
            getLogger().warning("Reload rejected; keeping the last-good configuration: " + e.getMessage());
            return new ReloadResult(false, e.getMessage());
        }
    }

    static ActiveConfig loadCandidate(File dataFolder) throws ConfigException {
        FileConfiguration main = loadYaml(new File(dataFolder, "config.yml"), "config.yml");
        Map<String, HotbarItem> defaults = new LinkedHashMap<>();
        Map<String, Map<String, HotbarItem>> perWorld = new LinkedHashMap<>();
        Map<String, Boolean> inheritsDefault = new LinkedHashMap<>();
        readItems(itemsSection(main, "config.yml"), defaults, "config.yml");

        long betweenUses = nonNegativeLong(main, "cooldown.between-uses-ms", 400, "config.yml");
        int spamThreshold = nonNegativeInt(main, "cooldown.spam-threshold", 6, "config.yml");
        long spamWindow = positiveLong(main, "cooldown.spam-window-ms", 3000, "config.yml");
        long lockout = nonNegativeLong(main, "cooldown.lockout-seconds", 5, "config.yml");
        if (lockout > Long.MAX_VALUE / 1000L) {
            throw new ConfigException("config.yml: cooldown.lockout-seconds is too large");
        }

        File folder = new File(dataFolder, "worlds");
        if (folder.isDirectory()) {
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
            if (files == null) {
                throw new ConfigException("worlds/: could not list configuration files");
            }
            java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
            for (File file : files) {
                String fileName = file.getName();
                if (fileName.startsWith("_")) continue;
                String world = fileName.substring(0, fileName.length() - 4).toLowerCase(Locale.ROOT);
                if (perWorld.containsKey(world)) {
                    throw new ConfigException("worlds/" + fileName + ": duplicates world '" + world + "'");
                }
                FileConfiguration cfg = loadYaml(file, "worlds/" + fileName);
                Map<String, HotbarItem> loaded = new LinkedHashMap<>();
                readItems(itemsSection(cfg, "worlds/" + fileName), loaded, "worlds/" + fileName);
                perWorld.put(world, immutableOrdered(loaded));
                inheritsDefault.put(world, booleanValue(cfg, "inherit-default", false,
                        "worlds/" + fileName));
            }
        }
        return new ActiveConfig(immutableOrdered(defaults), immutableOrdered(perWorld),
                immutableOrdered(inheritsDefault),
                betweenUses, spamThreshold, spamWindow, lockout,
                stringValue(main, "cooldown.message", "", "config.yml"),
                booleanValue(main, "settings.debug", false, "config.yml"));
    }

    private void ensureWorldFolder() {
        File folder = new File(getDataFolder(), "worlds");
        if (!folder.isDirectory() && folder.mkdirs()) {
            saveResource("worlds/_example.yml", false);
        }
    }

    private static ConfigurationSection itemsSection(ConfigurationSection cfg, String source)
            throws ConfigException {
        if (!cfg.contains("items")) return null;
        ConfigurationSection section = cfg.getConfigurationSection("items");
        if (section == null) throw new ConfigException(source + ": items must be a section");
        return section;
    }

    private static <K, V> Map<K, V> immutableOrdered(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static FileConfiguration loadYaml(File file, String source) throws ConfigException {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(file);
            return cfg;
        } catch (IOException | InvalidConfigurationException e) {
            throw new ConfigException(source + ": " + e.getMessage(), e);
        }
    }

    private static void readItems(ConfigurationSection section, Map<String, HotbarItem> into, String source)
            throws ConfigException {
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                throw new ConfigException(source + ": item '" + id + "' must be a section");
            }
            try {
                into.put(id, HotbarItem.load(id, entry));
            } catch (ConfigException e) {
                throw new ConfigException(source + ": " + e.getMessage(), e);
            }
        }
    }

    /** The items a world should show: its own file if it has one, otherwise the defaults. */
    public List<HotbarItem> itemsFor(World world) {
        if (world == null) {
            return new ArrayList<>(active.defaults().values());
        }
        return itemsForKey(active, world.getName().toLowerCase(Locale.ROOT));
    }

    private List<HotbarItem> itemsForKey(ActiveConfig config, String key) {
        Map<String, HotbarItem> specific = config.perWorld().get(key);
        if (specific == null) {
            return new ArrayList<>(config.defaults().values());
        }
        if (!config.inheritsDefault().getOrDefault(key, false)) {
            return new ArrayList<>(specific.values());
        }
        // inherit-default: the world's own entries win where ids collide.
        Map<String, HotbarItem> merged = new LinkedHashMap<>(config.defaults());
        merged.putAll(specific);
        return new ArrayList<>(merged.values());
    }

    // only warns: items limited by permission or world may never meet; a real clash is rejected at apply time
    private void warnSlotClashes(List<HotbarItem> items, Set<String> ownIds, String source) {
        Map<Integer, String> bySlot = new HashMap<>();
        for (HotbarItem item : items) {
            String earlier = bySlot.putIfAbsent(item.slot(), item.id());
            // Only report clashes this file is part of; config.yml's own are reported once, for config.yml.
            if (earlier != null && (ownIds.contains(earlier) || ownIds.contains(item.id()))) {
                getLogger().warning("Items '" + earlier + "' and '" + item.id() + "' (" + source + ") are both"
                        + " in slot " + (item.slot() + 1) + ". Give one a different slot unless their permissions or"
                        + " worlds keep them apart.");
            }
        }
    }

    /**
     * Look up an item by id for the world a player is in, so per-world overrides win. Agrees with
     * {@link #itemsFor}: a world whose file doesn't inherit the defaults doesn't see them here either.
     */
    public HotbarItem item(World world, String id) {
        if (world != null) {
            String key = world.getName().toLowerCase(Locale.ROOT);
            Map<String, HotbarItem> specific = active.perWorld().get(key);
            if (specific != null) {
                HotbarItem own = specific.get(id);
                if (own != null || !active.inheritsDefault().getOrDefault(key, false)) {
                    return own;
                }
            }
        }
        return active.defaults().get(id);
    }

    public int itemCount() {
        return active.defaults().size() + active.perWorld().values().stream().mapToInt(Map::size).sum();
    }

    public boolean debug() {
        return active.debug();
    }

    public String cooldownMessage() { return active.cooldownMessage(); }

    private static long nonNegativeLong(ConfigurationSection cfg, String path, long fallback, String source)
            throws ConfigException {
        long value = longValue(cfg, path, fallback, source);
        if (value < 0) throw new ConfigException(source + ": " + path + " must be non-negative");
        return value;
    }

    private static long positiveLong(ConfigurationSection cfg, String path, long fallback, String source)
            throws ConfigException {
        long value = longValue(cfg, path, fallback, source);
        if (value < 1) throw new ConfigException(source + ": " + path + " must be at least 1");
        return value;
    }

    private static int nonNegativeInt(ConfigurationSection cfg, String path, int fallback, String source)
            throws ConfigException {
        long value = longValue(cfg, path, fallback, source);
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new ConfigException(source + ": " + path + " must be between 0 and " + Integer.MAX_VALUE);
        }
        return (int) value;
    }

    private static long longValue(ConfigurationSection cfg, String path, long fallback, String source)
            throws ConfigException {
        if (!cfg.contains(path)) return fallback;
        Object raw = cfg.get(path);
        if (!(raw instanceof Number number)) {
            throw new ConfigException(source + ": " + path + " must be an integer");
        }
        double decimal = number.doubleValue();
        long value = number.longValue();
        if (!Double.isFinite(decimal) || decimal != value) {
            throw new ConfigException(source + ": " + path + " must be an integer");
        }
        return value;
    }

    private static boolean booleanValue(ConfigurationSection cfg, String path, boolean fallback, String source)
            throws ConfigException {
        if (!cfg.contains(path)) return fallback;
        Object raw = cfg.get(path);
        if (!(raw instanceof Boolean value)) {
            throw new ConfigException(source + ": " + path + " must be true or false");
        }
        return value;
    }

    private static String stringValue(ConfigurationSection cfg, String path, String fallback, String source)
            throws ConfigException {
        if (!cfg.contains(path)) return fallback;
        Object raw = cfg.get(path);
        if (!(raw instanceof String value)) {
            throw new ConfigException(source + ": " + path + " must be text");
        }
        return value;
    }

    public CooldownTracker cooldowns() {
        return cooldowns;
    }

    public ItemService itemService() {
        return itemService;
    }
}
