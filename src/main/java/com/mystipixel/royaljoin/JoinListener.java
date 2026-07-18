package com.mystipixel.royaljoin;

import com.mystipixel.royaljoin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

/**
 * Keeps configured items where they belong, and turns a click into a command.
 *
 * <p>Items are re-applied on join, respawn and world change. World change matters more than it looks:
 * it is also what fires when another plugin moves a player between worlds — a per-profile skyblock
 * swap, a farming server portal — so the item survives those without this plugin knowing about them.
 */
public final class JoinListener implements Listener {

    private final RoyalJoinPlugin plugin;
    private final ItemService items;

    public JoinListener(RoyalJoinPlugin plugin, ItemService items) {
        this.plugin = plugin;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        // A tick later: plugins that restore or clear inventories on join run at their own priorities,
        // and applying before them would just be overwritten.
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                items.apply(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                items.apply(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                items.apply(player);
            }
        });
    }

    /**
     * Drop our items from the death drops when they're set to survive death.
     *
     * <p>Removing rather than re-adding on respawn: if the item stayed in the drops it would also be
     * lying on the ground, so a player could end up with two.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent event) {
        Iterator<ItemStack> it = event.getDrops().iterator();
        while (it.hasNext()) {
            String id = items.idOf(it.next());
            if (id == null) {
                continue;
            }
            HotbarItem item = plugin.item(event.getEntity().getWorld(), id);
            if (item == null || item.keepOnDeath()) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        plugin.cooldowns().forget(event.getPlayer());   // keep the tracker bounded
    }

    // ── protection ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (locked(player, event.getCurrentItem()) || locked(player, event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        // Number-key swaps move the hotbar slot's contents without the item being "current".
        if (event.getClick() == ClickType.NUMBER_KEY
                && locked(player, player.getInventory().getItem(event.getHotbarButton()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && locked(player, event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (locked(event.getPlayer(), event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (locked(event.getPlayer(), event.getMainHandItem())
                || locked(event.getPlayer(), event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * Whether a stack is one of ours and pinned in place. Resolved against the player's world, since a
     * world file can define the same id with different behaviour.
     */
    private boolean locked(Player player, ItemStack stack) {
        String id = items.idOf(stack);
        if (id == null) {
            return false;
        }
        HotbarItem item = plugin.item(player.getWorld(), id);
        // An item we tagged but can no longer resolve is still ours; keep it locked so it can't be
        // stashed or sold after a config change removed it.
        return item == null || item.locked();
    }

    // ── the actual point of the plugin ───────────────────────────────────────────

    /**
     * Deliberately NOT ignoreCancelled: protection plugins routinely cancel interact events (WorldGuard
     * denying build or use in a hub, for example), and the click would then never reach us. Our item
     * isn't placing or breaking anything, so acting on a cancelled event is safe here.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        // The event fires once per hand; without this the command would run twice per click.
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        String id = items.idOf(event.getItem());
        if (id == null) {
            return;
        }
        if (plugin.debug()) {
            plugin.getLogger().info("[debug] " + event.getPlayer().getName() + " " + event.getAction()
                    + " with '" + id + "' (cancelled=" + event.isCancelled() + ")");
        }
        HotbarItem item = plugin.item(event.getPlayer().getWorld(), id);
        if (item == null) {
            return;
        }
        boolean right = event.getAction().isRightClick();
        boolean left = event.getAction().isLeftClick();
        boolean matches = switch (item.click()) {
            case RIGHT -> right;
            case LEFT -> left;
            case EITHER -> right || left;
        };
        if (!matches) {
            // Still cancel, so a locked item can't place a block or break one.
            if (item.locked()) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();

        CooldownTracker.Result gate = plugin.cooldowns().check(player);
        if (gate != CooldownTracker.Result.ALLOW) {
            // Only speak when the lockout trips. Messaging every blocked click would turn an
            // auto-clicker into chat spam, which is worse than the thing being prevented.
            if (gate == CooldownTracker.Result.LOCKED_OUT_NOW) {
                String message = plugin.getConfig().getString("cooldown.message", "");
                if (message != null && !message.isBlank()) {
                    player.sendMessage(Text.chat(message.replace("%seconds%",
                            String.valueOf(plugin.cooldowns().secondsRemaining(player)))));
                }
            }
            if (plugin.debug()) {
                plugin.getLogger().info("[debug] " + player.getName() + " blocked by rate limit: " + gate);
            }
            return;
        }

        String command = item.command().replace("%player%", player.getName());
        boolean handled;
        if (item.asConsole()) {
            handled = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else {
            handled = player.performCommand(command);
        }
        if (plugin.debug()) {
            plugin.getLogger().info("[debug] ran '/" + command + "' for " + player.getName()
                    + " -> " + (handled ? "handled" : "NOT handled (unknown command or no permission)"));
        }
    }
}
