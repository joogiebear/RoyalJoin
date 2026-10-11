package com.mystipixel.royaljoin.command;

import com.mystipixel.royaljoin.RoyalJoinPlugin;
import com.mystipixel.royaljoin.message.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** {@code /royaljoin reload}: re-read the config and re-apply items to everyone online. */
public final class RoyalJoinCommand implements CommandExecutor, TabCompleter {

    private final RoyalJoinPlugin plugin;
    private final MessageManager messages;

    public RoyalJoinCommand(RoyalJoinPlugin plugin, MessageManager messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            messages.send(sender, "usage", "label", label);
            return true;
        }
        if (!sender.hasPermission("royaljoin.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        messages.reload();
        RoyalJoinPlugin.ReloadResult result = plugin.reloadItems();
        if (!result.success()) {
            messages.send(sender, "reload-failed", "error", result.error());
            return true;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.itemService().apply(player);
        }
        messages.send(sender, "reloaded", "items", String.valueOf(plugin.itemCount()),
                "players", String.valueOf(Bukkit.getOnlinePlayers().size()));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("royaljoin.admin") && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("reload");
        }
        return List.of();
    }
}
