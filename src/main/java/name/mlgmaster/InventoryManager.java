package name.mlgmaster;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class InventoryManager {
    
    public static boolean ensureWaterBucketInHand(ClientPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.getItem() == Items.WATER_BUCKET) {
            return true;
        }
        
        MLGMaster.LOGGER.info("Searching for water bucket in hotbar...");
        
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == Items.WATER_BUCKET) {
                return switchToSlot(player, i);
            }
        }
        
        MLGMaster.LOGGER.warn("No water bucket found in hotbar");
        return false;
    }
    
    private static boolean switchToSlot(ClientPlayerEntity player, int slot) {
        try {
            java.lang.reflect.Field field = player.getInventory().getClass().getDeclaredField("selectedSlot");
            field.setAccessible(true);
            field.setInt(player.getInventory(), slot);
            MLGMaster.LOGGER.info("Switched to water bucket in slot {}", slot);
            return true;
        } catch (Exception e) {
            MLGMaster.LOGGER.error("Failed to switch to slot {}: {}", slot, e.getMessage());
            return false;
        }
    }
    
    public static boolean hasWaterBucket(ClientPlayerEntity player) {
        // Check main hand first
        if (player.getMainHandStack().getItem() == Items.WATER_BUCKET) {
            return true;
        }
        
        // Check hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == Items.WATER_BUCKET) {
                return true;
            }
        }
        
        return false;
    }
}