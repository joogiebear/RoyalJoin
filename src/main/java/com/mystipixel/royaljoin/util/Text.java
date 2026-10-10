package com.mystipixel.royaljoin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Legacy '&' colour strings to Adventure components. */
public final class Text {

    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    /** Item names and lore, with Minecraft's default italic turned off. */
    public static Component item(String input) {
        return AMP.deserialize(input == null ? "" : input).decoration(TextDecoration.ITALIC, false);
    }

    /** Chat messages, where authored italics should survive. */
    public static Component chat(String input) {
        return AMP.deserialize(input == null ? "" : input);
    }
}
