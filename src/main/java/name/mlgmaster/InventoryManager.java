package name.mlgmaster;

import name.mlgmaster.mixin.PlayerInventoryAccessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class InventoryManager {

    public static boolean ensureItemInHand(ClientPlayerEntity player, Item targetItem) {
        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.getItem() == targetItem) {
            return true;
        }

        // Search hotbar for target item
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == targetItem) {
                return switchToSlot(player, i);
            }
        }

        MLGMaster.LOGGER.warn("No {} found in hotbar", targetItem.getName().getString());
        return false;
    }

    private static boolean switchToSlot(ClientPlayerEntity player, int slot) {
        try {
            PlayerInventoryAccessor inventoryAccessor =
                    (PlayerInventoryAccessor) player.getInventory();
            inventoryAccessor.setSelectedSlot(slot);
            return true;
        } catch (Exception e) {
            MLGMaster.LOGGER.error("Failed to switch to slot {}: {}", slot, e.getMessage());
            return false;
        }
    }

    public static boolean hasItem(ClientPlayerEntity player, Item targetItem) {
        // Check main hand first
        if (player.getMainHandStack().getItem() == targetItem) {
            return true;
        }

        // Check hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == targetItem) {
                return true;
            }
        }

        return false;
    }
}
