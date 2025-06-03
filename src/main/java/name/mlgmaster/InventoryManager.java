package name.mlgmaster;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class InventoryManager {
    public interface InventoryAccess {
        ItemStack getStackInSlot(int slot);

        void setStackInSlot(int slot, ItemStack stack);

        boolean switchToSlot(int slot);

        int getSelectedSlot();

        void setSelectedSlot(int slot);

        int getInventorySize();

        boolean moveItemToHotbar(int inventorySlot, int hotbarSlot);
    }

    /**
     * Get inventory access for a player (provided by mixin)
     */
    private static InventoryAccess getInventoryAccess(ClientPlayerEntity player) {
        // Cast to PlayerEntity since that's where our mixin is applied
        return (InventoryAccess) (PlayerEntity) player;
    }

    /**
     * Check if we can access the player's inventory system
     */
    public static boolean canAccessInventory(ClientPlayerEntity player) {
        if (player == null) {
            return false;
        }

        try {
            InventoryAccess inventory = getInventoryAccess(player);

            // Test basic inventory operations
            int inventorySize = inventory.getInventorySize();
            int selectedSlot = inventory.getSelectedSlot();

            // Basic sanity checks
            if (inventorySize <= 0 || inventorySize > 100) { // Reasonable inventory size bounds
                MLGMaster.LOGGER.warn("Inventory size out of reasonable bounds: {}", inventorySize);
                return false;
            }

            if (selectedSlot < 0 || selectedSlot >= 9) { // Hotbar slots should be 0-8
                MLGMaster.LOGGER.warn("Selected slot out of hotbar bounds: {}", selectedSlot);
                return false;
            }

            // Try to access a known slot (current selected slot)
            ItemStack currentStack = inventory.getStackInSlot(selectedSlot);
            // If we can get here without exception, inventory access is working

            return true;

        } catch (Exception e) {
            MLGMaster.LOGGER.error("Failed to access inventory: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if inventory access is working and log detailed status
     */
    public static boolean canAccessInventoryWithLogging(ClientPlayerEntity player) {
        if (player == null) {
            MLGMaster.LOGGER.warn("Cannot access inventory: player is null");
            return false;
        }

        try {
            InventoryAccess inventory = getInventoryAccess(player);

            MLGMaster.LOGGER.debug("Testing inventory access...");

            // Test inventory size
            int inventorySize = inventory.getInventorySize();
            MLGMaster.LOGGER.debug("  Inventory size: {}", inventorySize);

            if (inventorySize <= 0 || inventorySize > 100) {
                MLGMaster.LOGGER.warn("  Inventory size out of reasonable bounds: {}",
                        inventorySize);
                return false;
            }

            // Test selected slot
            int selectedSlot = inventory.getSelectedSlot();
            MLGMaster.LOGGER.debug("  Selected slot: {}", selectedSlot);

            if (selectedSlot < 0 || selectedSlot >= 9) {
                MLGMaster.LOGGER.warn("  Selected slot out of hotbar bounds: {}", selectedSlot);
                return false;
            }

            // Test slot access
            ItemStack currentStack = inventory.getStackInSlot(selectedSlot);
            MLGMaster.LOGGER.debug("  Current stack: {}", currentStack.getItem());

            // Test hotbar access
            for (int i = 0; i < 9; i++) {
                ItemStack hotbarStack = inventory.getStackInSlot(i);
                // Just accessing it is enough to test
            }
            MLGMaster.LOGGER.debug("  Hotbar access: OK");

            // Test main inventory access
            for (int i = 9; i < Math.min(inventorySize, 36); i++) {
                ItemStack invStack = inventory.getStackInSlot(i);
                // Just accessing it is enough to test
            }
            MLGMaster.LOGGER.debug("  Main inventory access: OK");

            MLGMaster.LOGGER.debug("Inventory access test: PASSED");
            return true;

        } catch (Exception e) {
            MLGMaster.LOGGER.error("Inventory access test: FAILED - {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get inventory access status with detailed information
     */
    public static InventoryAccessStatus getInventoryAccessStatus(ClientPlayerEntity player) {
        if (player == null) {
            return new InventoryAccessStatus(false, "Player is null", 0, -1);
        }

        try {
            InventoryAccess inventory = getInventoryAccess(player);

            int inventorySize = inventory.getInventorySize();
            int selectedSlot = inventory.getSelectedSlot();

            // Check for issues
            if (inventorySize <= 0 || inventorySize > 100) {
                return new InventoryAccessStatus(false,
                        "Inventory size out of bounds: " + inventorySize, inventorySize,
                        selectedSlot);
            }

            if (selectedSlot < 0 || selectedSlot >= 9) {
                return new InventoryAccessStatus(false,
                        "Selected slot out of bounds: " + selectedSlot, inventorySize,
                        selectedSlot);
            }

            // Test slot access
            ItemStack testStack = inventory.getStackInSlot(selectedSlot);

            return new InventoryAccessStatus(true, "Inventory access OK", inventorySize,
                    selectedSlot);

        } catch (Exception e) {
            return new InventoryAccessStatus(false, "Exception: " + e.getMessage(), 0, -1);
        }
    }

    /**
     * Ensure water bucket is in hand - searches entire inventory
     */
    public static boolean ensureWaterBucketInHand(ClientPlayerEntity player) {
        InventoryAccess inventory = getInventoryAccess(player);

        // Check if already in main hand
        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.getItem() == Items.WATER_BUCKET) {
            MLGMaster.LOGGER.info("Water bucket already in main hand");
            return true;
        }

        MLGMaster.LOGGER.info("Searching for water bucket in entire inventory...");

        // First, search hotbar (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.getItem() == Items.WATER_BUCKET) {
                MLGMaster.LOGGER.info("Found water bucket in hotbar slot {}", i);
                return switchToSlot(player, i);
            }
        }

        // Then search main inventory (slots 9-35)
        for (int i = 9; i < inventory.getInventorySize(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.getItem() == Items.WATER_BUCKET) {
                MLGMaster.LOGGER.info("Found water bucket in inventory slot {}", i);
                return moveWaterBucketToHand(player, i);
            }
        }

        MLGMaster.LOGGER.warn("No water bucket found in entire inventory");
        return false;
    }

    /**
     * Switch to a hotbar slot using mixin
     */
    private static boolean switchToSlot(ClientPlayerEntity player, int slot) {
        if (slot < 0 || slot >= 9) {
            MLGMaster.LOGGER.error("Invalid hotbar slot: {}", slot);
            return false;
        }

        try {
            InventoryAccess inventory = getInventoryAccess(player);
            inventory.setSelectedSlot(slot);
            MLGMaster.LOGGER.info("Switched to water bucket in hotbar slot {}", slot);
            return true;
        } catch (Exception e) {
            MLGMaster.LOGGER.error("Failed to switch to slot {}: {}", slot, e.getMessage());
            return false;
        }
    }

    /**
     * Move water bucket from inventory to current hand using mixin
     */
    private static boolean moveWaterBucketToHand(ClientPlayerEntity player, int inventorySlot) {
        try {
            InventoryAccess inventory = getInventoryAccess(player);
            int currentSlot = inventory.getSelectedSlot();

            MLGMaster.LOGGER.info("Moving water bucket from inventory slot {} to hotbar slot {}",
                    inventorySlot, currentSlot);

            // Move item from inventory to current hotbar slot
            boolean success = inventory.moveItemToHotbar(inventorySlot, currentSlot);

            if (success) {
                MLGMaster.LOGGER.info("Successfully moved water bucket to hand");
                return true;
            } else {
                MLGMaster.LOGGER.error("Failed to move water bucket from slot {}", inventorySlot);
                return false;
            }
        } catch (Exception e) {
            MLGMaster.LOGGER.error("Exception while moving water bucket: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if player has water bucket anywhere in inventory
     */
    public static boolean hasWaterBucket(ClientPlayerEntity player) {
        if (!canAccessInventory(player)) {
            return false;
        }

        InventoryAccess inventory = getInventoryAccess(player);

        // Check entire inventory (0-35)
        for (int i = 0; i < inventory.getInventorySize(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.getItem() == Items.WATER_BUCKET) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the slot number of the first water bucket found in inventory Returns -1 if not found
     */
    public static int findWaterBucketSlot(ClientPlayerEntity player) {
        if (!canAccessInventory(player)) {
            return -1;
        }

        InventoryAccess inventory = getInventoryAccess(player);

        // Check entire inventory (0-35)
        for (int i = 0; i < inventory.getInventorySize(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.getItem() == Items.WATER_BUCKET) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Count total water buckets in inventory
     */
    public static int countWaterBuckets(ClientPlayerEntity player) {
        if (!canAccessInventory(player)) {
            return 0;
        }

        InventoryAccess inventory = getInventoryAccess(player);
        int count = 0;

        // Check entire inventory (0-35)
        for (int i = 0; i < inventory.getInventorySize(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.getItem() == Items.WATER_BUCKET) {
                count += stack.getCount();
            }
        }

        return count;
    }

    /**
     * Get detailed inventory info for debugging
     */
    public static void logInventoryContents(ClientPlayerEntity player) {
        if (!canAccessInventory(player)) {
            MLGMaster.LOGGER.warn("Cannot log inventory contents - inventory access failed");
            return;
        }

        InventoryAccess inventory = getInventoryAccess(player);

        MLGMaster.LOGGER.info("=== INVENTORY CONTENTS ===");
        MLGMaster.LOGGER.info("Selected slot: {}", inventory.getSelectedSlot());

        MLGMaster.LOGGER.info("HOTBAR (0-8):");
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                String selected = (i == inventory.getSelectedSlot()) ? " [SELECTED]" : "";
                MLGMaster.LOGGER.info("  Slot {}: {} x{}{}", i, stack.getItem(), stack.getCount(),
                        selected);
            }
        }

        MLGMaster.LOGGER.info("MAIN INVENTORY (9-35):");
        for (int i = 9; i < inventory.getInventorySize(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                MLGMaster.LOGGER.info("  Slot {}: {} x{}", i, stack.getItem(), stack.getCount());
            }
        }

        int waterBuckets = countWaterBuckets(player);
        MLGMaster.LOGGER.info("Total water buckets: {}", waterBuckets);
        MLGMaster.LOGGER.info("=== END INVENTORY ===");
    }

    /**
     * Find the best empty or least valuable hotbar slot for moving water bucket
     */
    public static int findBestHotbarSlotForWater(ClientPlayerEntity player) {
        if (!canAccessInventory(player)) {
            return -1;
        }

        InventoryAccess inventory = getInventoryAccess(player);

        // First, look for empty slots in hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                MLGMaster.LOGGER.info("Found empty hotbar slot {} for water bucket", i);
                return i;
            }
        }

        // If no empty slots, prefer slots without important items
        // Avoid slots with weapons, tools, food, blocks
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            String itemName = stack.getItem().toString().toLowerCase();

            // Skip important items
            if (itemName.contains("sword") || itemName.contains("axe")
                    || itemName.contains("pickaxe") || itemName.contains("shovel")
                    || itemName.contains("bow") || itemName.contains("food")
                    || itemName.contains("block") || itemName.contains("pearl")) {
                continue;
            }

            MLGMaster.LOGGER.info("Found suitable hotbar slot {} (contains: {}) for water bucket",
                    i, itemName);
            return i;
        }

        // If all slots have important items, use current selected slot as last resort
        MLGMaster.LOGGER.info("All hotbar slots contain important items, using current slot {}",
                inventory.getSelectedSlot());
        return inventory.getSelectedSlot();
    }

    /**
     * Intelligently move water bucket to best available hotbar slot
     */
    public static boolean smartMoveWaterBucketToHotbar(ClientPlayerEntity player) {
        if (!canAccessInventory(player)) {
            MLGMaster.LOGGER.warn("Cannot move water bucket - inventory access failed");
            return false;
        }

        int waterBucketSlot = findWaterBucketSlot(player);
        if (waterBucketSlot == -1) {
            MLGMaster.LOGGER.warn("No water bucket found in inventory");
            return false;
        }

        if (waterBucketSlot < 9) {
            // Already in hotbar, just select it
            return switchToSlot(player, waterBucketSlot);
        }

        // Find best hotbar slot and move water bucket there
        int bestSlot = findBestHotbarSlotForWater(player);
        if (bestSlot == -1) {
            MLGMaster.LOGGER.error("Could not find suitable hotbar slot");
            return false;
        }

        InventoryAccess inventory = getInventoryAccess(player);

        if (inventory.moveItemToHotbar(waterBucketSlot, bestSlot)) {
            // Select the slot with water bucket
            inventory.setSelectedSlot(bestSlot);
            MLGMaster.LOGGER.info("Smart moved water bucket to hotbar slot {}", bestSlot);
            return true;
        } else {
            MLGMaster.LOGGER.error("Failed to smart move water bucket");
            return false;
        }
    }

    /**
     * Perform comprehensive inventory validation
     */
    public static InventoryValidation validateInventoryForMLG(ClientPlayerEntity player) {
        InventoryValidation.Builder builder = new InventoryValidation.Builder();

        // Basic access check
        boolean canAccess = canAccessInventory(player);
        builder.canAccessInventory(canAccess);

        if (!canAccess) {
            return builder.build();
        }

        // Water bucket checks
        boolean hasWaterBucket = hasWaterBucket(player);
        int waterBucketCount = countWaterBuckets(player);
        int waterBucketSlot = findWaterBucketSlot(player);
        boolean waterBucketInHotbar = waterBucketSlot >= 0 && waterBucketSlot < 9;
        boolean waterBucketInHand = player.getMainHandStack().getItem() == Items.WATER_BUCKET
                || player.getOffHandStack().getItem() == Items.WATER_BUCKET;

        builder.hasWaterBucket(hasWaterBucket).waterBucketCount(waterBucketCount)
                .waterBucketSlot(waterBucketSlot).waterBucketInHotbar(waterBucketInHotbar)
                .waterBucketInHand(waterBucketInHand);

        // Hotbar analysis
        InventoryAccess inventory = getInventoryAccess(player);
        int emptyHotbarSlots = 0;
        int selectedSlot = inventory.getSelectedSlot();

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                emptyHotbarSlots++;
            }
        }

        builder.emptyHotbarSlots(emptyHotbarSlots).selectedSlot(selectedSlot)
                .canMoveToHotbar(hasWaterBucket && (waterBucketInHotbar || emptyHotbarSlots > 0));

        return builder.build();
    }

    /**
     * Quick check if MLG is possible from inventory perspective
     */
    public static boolean isMLGPossible(ClientPlayerEntity player) {
        return canAccessInventory(player) && hasWaterBucket(player);
    }

    /**
     * Get inventory summary for debugging
     */
    public static String getInventorySummary(ClientPlayerEntity player) {
        if (!canAccessInventory(player)) {
            return "Inventory inaccessible";
        }

        int waterBuckets = countWaterBuckets(player);
        int waterBucketSlot = findWaterBucketSlot(player);
        boolean inHotbar = waterBucketSlot >= 0 && waterBucketSlot < 9;
        boolean inHand = player.getMainHandStack().getItem() == Items.WATER_BUCKET
                || player.getOffHandStack().getItem() == Items.WATER_BUCKET;

        return String.format("Water buckets: %d, Slot: %d, In hotbar: %s, In hand: %s",
                waterBuckets, waterBucketSlot, inHotbar, inHand);
    }

    // Helper classes

    /**
     * Result class for inventory access status
     */
    public static class InventoryAccessStatus {
        private final boolean canAccess;
        private final String statusMessage;
        private final int inventorySize;
        private final int selectedSlot;

        public InventoryAccessStatus(boolean canAccess, String statusMessage, int inventorySize,
                int selectedSlot) {
            this.canAccess = canAccess;
            this.statusMessage = statusMessage;
            this.inventorySize = inventorySize;
            this.selectedSlot = selectedSlot;
        }

        public boolean canAccess() {
            return canAccess;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

        public int getInventorySize() {
            return inventorySize;
        }

        public int getSelectedSlot() {
            return selectedSlot;
        }

        @Override
        public String toString() {
            return String.format(
                    "InventoryAccess[canAccess=%s, message='%s', size=%d, selected=%d]", canAccess,
                    statusMessage, inventorySize, selectedSlot);
        }
    }

    public static class InventoryValidation {
        private final boolean canAccessInventory;
        private final boolean hasWaterBucket;
        private final int waterBucketCount;
        private final int waterBucketSlot;
        private final boolean waterBucketInHotbar;
        private final boolean waterBucketInHand;
        private final int emptyHotbarSlots;
        private final int selectedSlot;
        private final boolean canMoveToHotbar;

        private InventoryValidation(Builder builder) {
            this.canAccessInventory = builder.canAccessInventory;
            this.hasWaterBucket = builder.hasWaterBucket;
            this.waterBucketCount = builder.waterBucketCount;
            this.waterBucketSlot = builder.waterBucketSlot;
            this.waterBucketInHotbar = builder.waterBucketInHotbar;
            this.waterBucketInHand = builder.waterBucketInHand;
            this.emptyHotbarSlots = builder.emptyHotbarSlots;
            this.selectedSlot = builder.selectedSlot;
            this.canMoveToHotbar = builder.canMoveToHotbar;
        }

        public boolean isMLGReady() {
            return canAccessInventory && hasWaterBucket && (waterBucketInHand || canMoveToHotbar);
        }

        public String getReadinessStatus() {
            if (!canAccessInventory)
                return "Cannot access inventory";
            if (!hasWaterBucket)
                return "No water bucket available";
            if (waterBucketInHand)
                return "Ready - water bucket in hand";
            if (canMoveToHotbar)
                return "Ready - can move water bucket to hotbar";
            return "Not ready - cannot access water bucket";
        }

        public void logValidation() {
            MLGMaster.LOGGER.info("=== INVENTORY VALIDATION ===");
            MLGMaster.LOGGER.info("Can access inventory: {}", canAccessInventory);
            MLGMaster.LOGGER.info("Has water bucket: {} (count: {})", hasWaterBucket,
                    waterBucketCount);
            MLGMaster.LOGGER.info("Water bucket slot: {} (in hotbar: {})", waterBucketSlot,
                    waterBucketInHotbar);
            MLGMaster.LOGGER.info("Water bucket in hand: {}", waterBucketInHand);
            MLGMaster.LOGGER.info("Empty hotbar slots: {}", emptyHotbarSlots);
            MLGMaster.LOGGER.info("Selected slot: {}", selectedSlot);
            MLGMaster.LOGGER.info("Can move to hotbar: {}", canMoveToHotbar);
            MLGMaster.LOGGER.info("MLG ready: {}", isMLGReady());
            MLGMaster.LOGGER.info("Status: {}", getReadinessStatus());
        }

        // Getters
        public boolean canAccessInventory() {
            return canAccessInventory;
        }

        public boolean hasWaterBucket() {
            return hasWaterBucket;
        }

        public int getWaterBucketCount() {
            return waterBucketCount;
        }

        public int getWaterBucketSlot() {
            return waterBucketSlot;
        }

        public boolean isWaterBucketInHotbar() {
            return waterBucketInHotbar;
        }

        public boolean isWaterBucketInHand() {
            return waterBucketInHand;
        }

        public int getEmptyHotbarSlots() {
            return emptyHotbarSlots;
        }

        public int getSelectedSlot() {
            return selectedSlot;
        }

        public boolean canMoveToHotbar() {
            return canMoveToHotbar;
        }

        public static class Builder {
            private boolean canAccessInventory = false;
            private boolean hasWaterBucket = false;
            private int waterBucketCount = 0;
            private int waterBucketSlot = -1;
            private boolean waterBucketInHotbar = false;
            private boolean waterBucketInHand = false;
            private int emptyHotbarSlots = 0;
            private int selectedSlot = 0;
            private boolean canMoveToHotbar = false;

            public Builder canAccessInventory(boolean canAccess) {
                this.canAccessInventory = canAccess;
                return this;
            }

            public Builder hasWaterBucket(boolean hasWaterBucket) {
                this.hasWaterBucket = hasWaterBucket;
                return this;
            }

            public Builder waterBucketCount(int count) {
                this.waterBucketCount = count;
                return this;
            }

            public Builder waterBucketSlot(int slot) {
                this.waterBucketSlot = slot;
                return this;
            }

            public Builder waterBucketInHotbar(boolean inHotbar) {
                this.waterBucketInHotbar = inHotbar;
                return this;
            }

            public Builder waterBucketInHand(boolean inHand) {
                this.waterBucketInHand = inHand;
                return this;
            }

            public Builder emptyHotbarSlots(int emptySlots) {
                this.emptyHotbarSlots = emptySlots;
                return this;
            }

            public Builder selectedSlot(int selected) {
                this.selectedSlot = selected;
                return this;
            }

            public Builder canMoveToHotbar(boolean canMove) {
                this.canMoveToHotbar = canMove;
                return this;
            }

            public InventoryValidation build() {
                return new InventoryValidation(this);
            }
        }
    }
}

