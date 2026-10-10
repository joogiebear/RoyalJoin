package com.mystipixel.royaljoin.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Optional PlaceholderAPI bridge; strings pass through untouched without it. The PAPI class is only
 * referenced behind the enabled check, so the JVM never links it when it is absent.
 */
public final class Papi {

    private Papi() {
    }

    public static String apply(Player player, String text) {
        if (text == null || text.indexOf('%') < 0
                || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable broken) {
            return text;                         // a broken expansion must never break the item
        }
    }
}
