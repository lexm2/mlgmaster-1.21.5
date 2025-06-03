package name.mlgmaster.mixin;

import name.mlgmaster.InventoryManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PlayerEntity.class)
public class InventoryAccessMixin implements InventoryManager.InventoryAccess {
    
    @Shadow
    private PlayerInventory inventory;
    
    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory.getStack(slot);
    }
    
    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        inventory.setStack(slot, stack);
    }
    
    @Override
    public boolean switchToSlot(int slot) {
        if (slot < 0 || slot >= PlayerInventory.MAIN_SIZE) {
            return false;
        }
        
        // If slot is in hotbar (0-8), just select it
        if (slot < 9) {
            PlayerInventoryAccessor accessor = (PlayerInventoryAccessor) inventory;
            accessor.setSelectedSlot(slot);
            return true;
        }
        
        // If slot is in main inventory (9-35), swap with current selected slot
        PlayerInventoryAccessor accessor = (PlayerInventoryAccessor) inventory;
        int selectedSlot = accessor.getSelectedSlot();
        ItemStack stackInInventory = inventory.getStack(slot);
        ItemStack stackInHotbar = inventory.getStack(selectedSlot);
        
        // Swap the items
        inventory.setStack(slot, stackInHotbar);
        inventory.setStack(selectedSlot, stackInInventory);
        
        return true;
    }
    
    @Override
    public int getSelectedSlot() {
        PlayerInventoryAccessor accessor = (PlayerInventoryAccessor) inventory;
        return accessor.getSelectedSlot();
    }
    
    @Override
    public void setSelectedSlot(int slot) {
        if (slot >= 0 && slot < 9) {
            PlayerInventoryAccessor accessor = (PlayerInventoryAccessor) inventory;
            accessor.setSelectedSlot(slot);
        }
    }
    
    @Override
    public int getInventorySize() {
        return PlayerInventory.MAIN_SIZE; // 36 slots total
    }
    
    @Override
    public boolean moveItemToHotbar(int inventorySlot, int hotbarSlot) {
        if (inventorySlot < 9 || inventorySlot >= PlayerInventory.MAIN_SIZE) {
            return false; // Invalid inventory slot
        }
        if (hotbarSlot < 0 || hotbarSlot >= 9) {
            return false; // Invalid hotbar slot
        }
        
        ItemStack inventoryStack = inventory.getStack(inventorySlot);
        ItemStack hotbarStack = inventory.getStack(hotbarSlot);
        
        // Swap the items
        inventory.setStack(inventorySlot, hotbarStack);
        inventory.setStack(hotbarSlot, inventoryStack);
        
        return true;
    }
}
