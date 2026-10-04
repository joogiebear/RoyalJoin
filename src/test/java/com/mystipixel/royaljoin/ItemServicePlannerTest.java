package com.mystipixel.royaljoin;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemServicePlannerTest {

    @Test
    void fullStorageCannotRelocateAndLeavesCandidateConserved() {
        ItemStack[] contents = filled("cobble", 36, 64);
        ItemStack displaced = stack("diamond", 3);

        assertFalse(ItemService.relocate(contents, 36, Set.of(8), displaced));
        assertEquals(3, displaced.getAmount());
        assertEquals(36 * 64, count(contents, "cobble"));
        assertEquals(0, count(contents, "diamond"));
    }

    @Test
    void relocationUsesFreeNonReservedStorageSlot() {
        ItemStack[] contents = filled("cobble", 36, 64);
        contents[20] = null;

        assertTrue(ItemService.relocate(contents, 36, Set.of(8), stack("diamond", 3)));
        assertEquals(3, count(contents, "diamond"));
    }

    @Test
    void relocationCanCompleteAcrossPartialMergesWithoutDuplication() {
        ItemStack[] contents = filled("cobble", 36, 64);
        contents[10] = stack("diamond", 63);
        contents[11] = stack("diamond", 62);

        assertTrue(ItemService.relocate(contents, 36, Set.of(8), stack("diamond", 3)));
        assertEquals(128, count(contents, "diamond"));
        assertEquals(64, contents[10].getAmount());
        assertEquals(64, contents[11].getAmount());
    }

    @Test
    void reservedSlotsAndEquipmentSlotsAreNeverRelocationTargets() {
        ItemStack[] contents = filled("cobble", 41, 64);
        contents[8] = null;
        contents[36] = null;

        assertFalse(ItemService.relocate(contents, 36, Set.of(8), stack("diamond", 1)));
        assertNull(contents[8]);
        assertNull(contents[36]);
    }

    @Test
    void completePlanRemovesOldTagsMovesChangedSlotAndIsRepeatable() {
        ItemStack oldTag = stack("owned-old", 1);
        ItemStack ordinary = stack("ordinary", 7);
        ItemStack replacement = stack("owned-new", 1);
        ItemStack[] original = new ItemStack[36];
        original[2] = oldTag;
        original[8] = ordinary;

        ItemStack[] first = ItemService.plan(original, 36, Set.of(8),
                stack -> stack != null && name(stack).startsWith("owned-"), Map.of(8, replacement));
        assertNotNull(first);
        assertEquals(0, count(first, "owned-old"));
        assertEquals(1, count(first, "owned-new"));
        assertEquals(7, count(first, "ordinary"));
        assertEquals("owned-new", name(first[8]));

        ItemStack[] second = ItemService.plan(first, 36, Set.of(8),
                stack -> stack != null && name(stack).startsWith("owned-"), Map.of(8, replacement));
        assertNotNull(second);
        assertEquals(1, count(second, "owned-new"));
        assertEquals(7, count(second, "ordinary"));
    }

    @Test
    void failedCompletePlanDoesNotMutateOldTagsOrOrdinaryItems() {
        ItemStack[] original = filled("full", 36, 64);
        original[8] = stack("ordinary", 2);
        original[7] = stack("ordinary-two", 3);
        original[10] = stack("owned-old", 1);

        assertNull(ItemService.plan(original, 36, Set.of(7, 8),
                stack -> stack != null && name(stack).startsWith("owned-"),
                Map.of(7, stack("owned-second", 1), 8, stack("owned-new", 1))));
        assertEquals(1, count(original, "owned-old"));
        assertEquals(2, count(original, "ordinary"));
        assertEquals(3, count(original, "ordinary-two"));
    }

    private static ItemStack[] filled(String kind, int size, int amount) {
        ItemStack[] contents = new ItemStack[size];
        for (int i = 0; i < size; i++) contents[i] = stack(kind, amount);
        return contents;
    }

    private static ItemStack stack(String kind, int initialAmount) {
        class Amount { int value = initialAmount; }
        Amount amount = new Amount();
        ItemStack item = mock(ItemStack.class, kind);
        Material material = mock(Material.class, kind + "-material");
        when(material.isAir()).thenReturn(false);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenAnswer(ignored -> amount.value);
        doAnswer(call -> { amount.value = call.getArgument(0); return null; }).when(item).setAmount(anyInt());
        when(item.getMaxStackSize()).thenReturn(64);
        when(item.isSimilar(any())).thenAnswer(call -> kind.equals(mockingDetails(call.getArgument(0)).getMockCreationSettings().getMockName().toString()));
        when(item.clone()).thenAnswer(ignored -> stack(kind, amount.value));
        return item;
    }

    private static int count(ItemStack[] contents, String kind) {
        int total = 0;
        for (ItemStack stack : contents) {
            if (stack != null && kind.equals(name(stack))) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static String name(ItemStack stack) {
        return mockingDetails(stack).getMockCreationSettings().getMockName().toString();
    }
}
