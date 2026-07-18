package com.mystipixel.royaljoin.command;

import com.mystipixel.royaljoin.RoyalJoinPlugin;
import com.mystipixel.royaljoin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** {@code /royaljoin reload} — re-read the config and re-apply items to everyone online. */
public final class RoyalJoinCommand implements CommandExecutor, TabCompleter {

    private final RoyalJoinPlugin plugin;

    public RoyalJoinCommand(RoyalJoinPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(Text.chat("&6RoyalJoin &8» &7Usage: &f/" + label + " reload"));
            return true;
        }
        if (!sender.hasPermission("royaljoin.admin")) {
            sender.sendMessage(Text.chat("&cYou don't have permission to do that."));
            return true;
        }
        plugin.reloadItems();
        // Re-apply immediately, so a slot or material change is visible without relogging.
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.itemService().apply(player);
        }
        sender.sendMessage(Text.chat("&6RoyalJoin &8» &aReloaded &f" + plugin.itemCount()
                + "&a item(s) and refreshed &f" + Bukkit.getOnlinePlayers().size() + "&a player(s)."));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("royaljoin.admin") && "reload".startsWith(args[0].toLowerCase())) {
            return List.of("reload");
        }
        return List.of();
    }
}
