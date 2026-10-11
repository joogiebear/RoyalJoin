package com.mystipixel.royaljoin;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// A `locked: false` item may be rearranged inside the player's own inventory, but any path that puts a
// copy somewhere else lets the next apply hand out another one.
class UnlockedItemDupeTest {

    private static final int CHEST_SIZE = 27;
    private static final boolean CHEST = false;
    private static final boolean OWN_VIEW = true;

    private RoyalJoinPlugin plugin;
    private ItemService items;
    private JoinListener listener;
    private Player player;
    private PlayerInventory playerInventory;
    private HotbarItem unlocked;
    private ItemStack ours;
    private ItemStack plain;

    @BeforeEach
    void setUp() {
        plugin = mock(RoyalJoinPlugin.class);
        items = mock(ItemService.class);
        listener = new JoinListener(plugin, items);
        player = mock(Player.class);
        playerInventory = mock(PlayerInventory.class);
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getInventory()).thenReturn(playerInventory);

        unlocked = mock(HotbarItem.class);
        when(unlocked.locked()).thenReturn(false);
        when(unlocked.click()).thenReturn(HotbarItem.ClickType.RIGHT);
        when(plugin.item(world, "menu")).thenReturn(unlocked);

        ours = mock(ItemStack.class, "ours");
        plain = mock(ItemStack.class, "plain");
        when(items.idOf(ours)).thenReturn("menu");
        when(items.isOurs(ours)).thenReturn(true);
    }

    @Test
    void placingTheCursorCopyIntoAChestIsCancelled() {
        InventoryClickEvent click = click(CHEST, 3, ClickType.LEFT,
                InventoryAction.PLACE_ALL, null, ours);
        listener.onClick(click);
        verify(click).setCancelled(true);
    }

    @Test
    void shiftClickingIntoAnOpenChestIsCancelled() {
        InventoryClickEvent click = click(CHEST, CHEST_SIZE + 30, ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY, ours, null);
        listener.onClick(click);
        verify(click).setCancelled(true);
    }

    @Test
    void numberKeyIntoAChestSlotIsCancelled() {
        InventoryClickEvent click = click(CHEST, 3, ClickType.NUMBER_KEY,
                InventoryAction.HOTBAR_SWAP, null, null);
        when(click.getHotbarButton()).thenReturn(8);
        when(playerInventory.getItem(8)).thenReturn(ours);
        listener.onClick(click);
        verify(click).setCancelled(true);
    }

    @Test
    void offHandKeyIntoAChestSlotIsCancelled() {
        InventoryClickEvent click = click(CHEST, 3, ClickType.SWAP_OFFHAND,
                InventoryAction.HOTBAR_SWAP, null, null);
        when(playerInventory.getItemInOffHand()).thenReturn(ours);
        listener.onClick(click);
        verify(click).setCancelled(true);
    }

    @Test
    void placingIntoTheTwoByTwoCraftingGridIsCancelled() {
        InventoryClickEvent click = click(OWN_VIEW, 1, ClickType.LEFT,
                InventoryAction.PLACE_ALL, null, ours);
        listener.onClick(click);
        verify(click).setCancelled(true);
    }

    @Test
    void puttingTheCopyIntoABundleIsCancelled() {
        InventoryClickEvent click = click(OWN_VIEW, 20, ClickType.RIGHT,
                InventoryAction.PLACE_ALL_INTO_BUNDLE, plain, ours);
        listener.onClick(click);
        verify(click).setCancelled(true);
    }

    @Test
    void droppingFromTheCursorOutsideTheWindowIsCancelled() {
        InventoryClickEvent click = click(OWN_VIEW, InventoryView.OUTSIDE,
                ClickType.LEFT, InventoryAction.DROP_ALL_CURSOR, null, ours);
        listener.onClick(click);
        verify(click).setCancelled(true);
    }

    @Test
    void movingBetweenOwnSlotsIsStillAllowed() {
        InventoryClickEvent click = click(OWN_VIEW, 20, ClickType.LEFT,
                InventoryAction.PLACE_ALL, null, ours);
        listener.onClick(click);
        verify(click, never()).setCancelled(true);
    }

    @Test
    void shiftClickingInsideOwnInventoryIsStillAllowed() {
        InventoryClickEvent click = click(OWN_VIEW, 40, ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY, ours, null);
        listener.onClick(click);
        verify(click, never()).setCancelled(true);
    }

    @Test
    void takingAnOldCopyOutOfAChestIsAllowed() {
        InventoryClickEvent click = click(CHEST, 3, ClickType.LEFT,
                InventoryAction.PICKUP_ALL, ours, null);
        listener.onClick(click);
        verify(click, never()).setCancelled(true);
    }

    @Test
    void draggingIntoAChestIsCancelled() {
        InventoryDragEvent drag = drag(Set.of(4, CHEST_SIZE + 2));
        listener.onDrag(drag);
        verify(drag).setCancelled(true);
    }

    @Test
    void draggingAcrossOwnSlotsIsStillAllowed() {
        InventoryDragEvent drag = drag(Set.of(CHEST_SIZE + 2, CHEST_SIZE + 3));
        listener.onDrag(drag);
        verify(drag, never()).setCancelled(true);
    }

    @Test
    void droppingIsCancelled() {
        Item entity = mock(Item.class);
        when(entity.getItemStack()).thenReturn(ours);
        PlayerDropItemEvent drop = mock(PlayerDropItemEvent.class);
        when(drop.getPlayer()).thenReturn(player);
        when(drop.getItemDrop()).thenReturn(entity);
        listener.onDrop(drop);
        verify(drop).setCancelled(true);
    }

    @Test
    void puttingInAnItemFrameIsCancelled() {
        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        when(event.getRightClicked()).thenReturn(mock(ItemFrame.class));
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(playerInventory.getItem(EquipmentSlot.HAND)).thenReturn(ours);
        listener.onInteractEntity(event);
        verify(event).setCancelled(true);
    }

    @Test
    void givingToAnArmorStandIsCancelled() {
        PlayerArmorStandManipulateEvent event = mock(PlayerArmorStandManipulateEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getRightClicked()).thenReturn(mock(ArmorStand.class));
        when(event.getPlayerItem()).thenReturn(ours);
        listener.onArmorStand(event);
        verify(event).setCancelled(true);
    }

    @Test
    void craftingWithACopyGivesNoResult() {
        CraftingInventory grid = mock(CraftingInventory.class);
        when(grid.getMatrix()).thenReturn(new ItemStack[]{plain, ours, null, plain});
        PrepareItemCraftEvent event = mock(PrepareItemCraftEvent.class);
        when(event.getInventory()).thenReturn(grid);
        listener.onPrepareCraft(event);
        verify(grid).setResult(null);
    }

    @Test
    void craftingWithoutACopyIsLeftAlone() {
        CraftingInventory grid = mock(CraftingInventory.class);
        when(grid.getMatrix()).thenReturn(new ItemStack[]{plain, null, plain});
        PrepareItemCraftEvent event = mock(PrepareItemCraftEvent.class);
        when(event.getInventory()).thenReturn(grid);
        listener.onPrepareCraft(event);
        verify(grid, never()).setResult(any());
    }

    @Test
    void theOtherClickCannotPlaceOrUseTheCopy() {
        when(unlocked.click()).thenReturn(HotbarItem.ClickType.LEFT);
        PlayerInteractEvent right = interact(EquipmentSlot.HAND, Action.RIGHT_CLICK_BLOCK);
        listener.onInteract(right);
        verify(right).setUseItemInHand(Event.Result.DENY);
        verify(right, never()).setCancelled(true);
    }

    @Test
    void theCopyCannotBeUsedFromTheOffHand() {
        PlayerInteractEvent event = interact(EquipmentSlot.OFF_HAND, Action.RIGHT_CLICK_BLOCK);
        listener.onInteract(event);
        verify(event).setUseItemInHand(Event.Result.DENY);
    }

    @Test
    void swappingToTheOffHandIsStillAllowed() {
        PlayerSwapHandItemsEvent event = mock(PlayerSwapHandItemsEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getMainHandItem()).thenReturn(null);
        when(event.getOffHandItem()).thenReturn(ours);
        listener.onSwapHands(event);
        verify(event, never()).setCancelled(true);
    }

    // the player's own view has the 2x2 crafting grid (four slots plus the result) on top
    private InventoryClickEvent click(boolean ownView, int rawSlot, ClickType type,
                                      InventoryAction action, ItemStack current, ItemStack cursor) {
        Inventory top = ownView ? mock(CraftingInventory.class) : mock(Inventory.class);
        when(top.getSize()).thenReturn(ownView ? 5 : CHEST_SIZE);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getClick()).thenReturn(type);
        when(event.getAction()).thenReturn(action);
        when(event.getCurrentItem()).thenReturn(current);
        when(event.getCursor()).thenReturn(cursor);
        return event;
    }

    private InventoryDragEvent drag(Set<Integer> rawSlots) {
        Inventory top = mock(Inventory.class);
        when(top.getSize()).thenReturn(CHEST_SIZE);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getOldCursor()).thenReturn(ours);
        when(event.getRawSlots()).thenReturn(rawSlots);
        return event;
    }

    private PlayerInteractEvent interact(EquipmentSlot hand, Action action) {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(hand);
        when(event.getItem()).thenReturn(ours);
        when(event.getAction()).thenReturn(action);
        when(event.getBlockFace()).thenReturn(BlockFace.UP);
        when(event.getClickedBlock()).thenReturn(mock(Block.class));
        return event;
    }
}
