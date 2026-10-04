package com.mystipixel.royaljoin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationValidationTest {

    @TempDir Path data;

    @Test
    void intentionalEmptyItemSetLoads() throws Exception {
        write("config.yml", "items: {}\n");
        assertEquals(0, RoyalJoinPlugin.loadCandidate(data.toFile()).defaults().size());
    }

    @Test
    void mainSyntaxErrorNamesMainFile() throws IOException {
        write("config.yml", "items: [\n");
        assertErrorContains("config.yml");
    }

    @Test
    void worldSyntaxErrorNamesWorldFile() throws IOException {
        validMain();
        write("worlds/hub.yml", "items: [\n");
        assertErrorContains("worlds/hub.yml");
    }

    @Test
    void invalidCooldownSettingRejectsWholeCandidate() throws IOException {
        write("config.yml", "cooldown:\n  spam-window-ms: 0\nitems: {}\n");
        assertErrorContains("cooldown.spam-window-ms");
    }

    @Test
    void worldModeIsTrimmedAndLimitedToExplicitModes() throws Exception {
        assertEquals("whitelist", HotbarItem.normalizeWorldMode("  WHITELIST  ", "menu"));
        assertThrows(ConfigException.class, () -> HotbarItem.normalizeWorldMode("allowlist", "menu"));
    }

    @Test
    void commandIsTrimmedAndOneOptionalSlashIsRemoved() throws Exception {
        assertEquals("menu open", HotbarItem.normalizeCommand("   /menu open   ", "menu"));
    }

    @Test
    void blankAndSlashOnlyCommandsAreRejectedWithItemCause() throws IOException {
        ConfigException error = assertThrows(ConfigException.class,
                () -> HotbarItem.normalizeCommand(" / ", "menu"));
        assertTrue(error.getMessage().contains("item 'menu': command"));
        assertThrows(ConfigException.class, () -> HotbarItem.normalizeCommand(" /// ", "menu"));
    }

    @Test
    void aFailedSecondParseCannotAlterLastGoodSnapshot() throws Exception {
        write("config.yml", "items: {}\nsettings:\n  debug: true\n");
        RoyalJoinPlugin.ActiveConfig good = RoyalJoinPlugin.loadCandidate(data.toFile());
        write("config.yml", "cooldown:\n  between-uses-ms: nope\nitems: {}\n");
        assertThrows(ConfigException.class, () -> RoyalJoinPlugin.loadCandidate(data.toFile()));
        assertTrue(good.debug());
    }

    private String item(String mode, String command) {
        return "items:\n  menu:\n    material: NETHER_STAR\n    slot: 9\n    command: \""
                + command + "\"\n    world-mode: \"" + mode + "\"\n";
    }

    private void validMain() throws IOException { write("config.yml", "items: {}\n"); }

    private void assertErrorContains(String expected) {
        ConfigException error = assertThrows(ConfigException.class,
                () -> RoyalJoinPlugin.loadCandidate(data.toFile()));
        assertTrue(error.getMessage().contains(expected), error.getMessage());
    }

    private void write(String relative, String body) throws IOException {
        Path file = data.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, body);
    }
}
