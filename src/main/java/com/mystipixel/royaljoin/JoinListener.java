package com.mystipixel.royaljoin;

import com.mystipixel.royaljoin.util.Papi;
import com.mystipixel.royaljoin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

/**
 * Keeps configured items where they belong, and turns a click into a command. World change also fires
 * when another plugin moves a player between worlds (a skyblock profile swap, a portal), so re-applying
 * there keeps the items without knowing about those plugins.
 */
public final class JoinListener implements Listener {

    private final RoyalJoinPlugin plugin;
    private final ItemService items;

    public JoinListener(RoyalJoinPlugin plugin, ItemService items) {
        this.plugin = plugin;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // next tick: plugins that restore or clear inventories on join would otherwise overwrite us
        reapplyNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        reapplyNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        reapplyNextTick(event.getPlayer());
    }

    private void reapplyNextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                items.apply(player);
            }
        });
    }

    // respawn hands out fresh copies, so strip ours from the drops; LOWEST so it runs before grave plugins
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Iterator<ItemStack> it = event.getDrops().iterator();
        while (it.hasNext()) {
            if (items.isOurs(it.next())) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.cooldowns().forget(event.getPlayer());   // keep the tracker bounded
    }

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
            return;
        }
        if (leavesPlayerInventory(event, player)) {
            event.setCancelled(true);
        }
    }

    // Applies to unlocked items too: apply only sees the player's own inventory, so a copy left anywhere
    // else (a chest, the crafting grid, a bundle) is a free extra one once the next apply hands out another.
    private boolean leavesPlayerInventory(InventoryClickEvent event, Player player) {
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        if (isIntoBundle(event.getAction()) && (items.isOurs(cursor) || items.isOurs(current))) {
            return true;
        }
        InventoryView view = event.getView();
        int raw = event.getRawSlot();
        if (raw == InventoryView.OUTSIDE) {
            return items.isOurs(cursor);
        }
        boolean inTop = raw >= 0 && raw < view.getTopInventory().getSize();
        if (inTop) {
            if (items.isOurs(cursor)) {
                return true;
            }
            return switch (event.getClick()) {
                case NUMBER_KEY -> items.isOurs(player.getInventory().getItem(event.getHotbarButton()));
                case SWAP_OFFHAND -> items.isOurs(player.getInventory().getItemInOffHand());
                default -> false;
            };
        }
        return event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && items.isOurs(current)
                && !isOwnInventoryView(view.getTopInventory());
    }

    // the player's own inventory screen has the 2x2 grid on top, and shift-click never moves items into it
    private static boolean isOwnInventoryView(Inventory top) {
        return top instanceof CraftingInventory && top.getSize() == 5;
    }

    private static boolean isIntoBundle(InventoryAction action) {
        return action == InventoryAction.PICKUP_ALL_INTO_BUNDLE
                || action == InventoryAction.PICKUP_SOME_INTO_BUNDLE
                || action == InventoryAction.PLACE_ALL_INTO_BUNDLE
                || action == InventoryAction.PLACE_SOME_INTO_BUNDLE;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack dragged = event.getOldCursor();
        if (locked(player, dragged)) {
            event.setCancelled(true);
            return;
        }
        if (items.isOurs(dragged)) {
            int topSize = event.getView().getTopInventory().getSize();
            if (event.getRawSlots().stream().anyMatch(raw -> raw < topSize)) {
                event.setCancelled(true);
            }
        }
    }

    // a copy used up in a recipe comes back at the next apply, so it would be a free ingredient
    @EventHandler(priority = EventPriority.LOW)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory grid = event.getInventory();
        for (ItemStack ingredient : grid.getMatrix()) {
            if (items.isOurs(ingredient)) {
                grid.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (items.isOurs(event.getItemDrop().getItemStack())) {
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

    // entity clicks don't fire PlayerInteractEvent; only frames and allays can take the item, and
    // villagers, mounts and NPCs must stay usable with the selector in hand
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame) && !(event.getRightClicked() instanceof Allay)) {
            return;
        }
        Player player = event.getPlayer();
        if (items.isOurs(player.getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
        }
    }

    // armor stands have their own event for taking an item from a player's hand
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (items.isOurs(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    // resolved against the player's world, since a world file can redefine the same id
    private boolean locked(Player player, ItemStack stack) {
        String id = items.idOf(stack);
        if (id == null) {
            return false;
        }
        HotbarItem item = plugin.item(player.getWorld(), id);
        // tagged but no longer configured: keep it locked so it can't be stashed or sold
        return item == null || item.locked();
    }

    // not ignoreCancelled: protection plugins (WorldGuard in a hub) cancel interacts, and our item
    // places or breaks nothing, so acting on a cancelled event is safe
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        // The event fires once per hand; without this the command would run twice per click.
        if (event.getHand() != EquipmentSlot.HAND) {
            // an unlocked item can sit in the off-hand; don't let it be placed, eaten or thrown from there
            if (items.isOurs(event.getItem())) {
                event.setUseItemInHand(Event.Result.DENY);
            }
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
        Player player = event.getPlayer();
        HotbarItem item = plugin.item(player.getWorld(), id);
        if (item == null) {
            // ours, but no longer applies here (removed, or a world that doesn't inherit it): swap it out
            event.setCancelled(true);
            reapplyNextTick(player);
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
            } else {
                // a placed, eaten or thrown copy is replaced at the next apply
                event.setUseItemInHand(Event.Result.DENY);
            }
            return;
        }
        event.setCancelled(true);

        // re-check at click time: a player who just lost the permission still holds the item until the
        // next apply, and it must not keep working (especially an as-console one)
        if (!item.appliesTo(player)) {
            if (plugin.debug()) {
                plugin.getLogger().info("[debug] " + player.getName() + " no longer qualifies for '" + id
                        + "'; taking it back.");
            }
            reapplyNextTick(player);
            return;
        }

        CooldownTracker.Result gate = plugin.cooldowns().check(player);
        if (gate != CooldownTracker.Result.ALLOW) {
            // message only when the lockout trips, or an auto-clicker becomes chat spam
            if (gate == CooldownTracker.Result.LOCKED_OUT_NOW) {
                String message = plugin.cooldownMessage();
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

        String command = Papi.apply(player,
                item.command().replace("%player%", player.getName()));
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
