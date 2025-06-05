package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Blocks;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;

public class MLGHandler {
    private static boolean isActive = false;
    private static long lastPredictionTime = 0;
    private static final long PREDICTION_INTERVAL = 50;
    private static final double FALL_TRIGGER_DISTANCE = 4.5;
    
    // Water pickup tracking
    private static boolean waterPlaced = false;
    private static BlockPos placedWaterPos = null;
    private static long waterPlacementTime = 0;
    private static final long WATER_PICKUP_DELAY = 100; // ms
    private static boolean pickupAttempted = false;

    public static void onHighFrequencyTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        ClientPlayerEntity player = client.player;
        Vec3d velocity = player.getVelocity();

        if (velocity.y >= -0.1 || player.isOnGround()) {
            ScaffoldingCrouchManager.releaseScaffoldingCrouch();

            if (waterPlaced && !pickupAttempted) {
                handleWaterPickup(client, player);
            }

            if (isActive) {
                isActive = false;
                MLGMaster.LOGGER.info("Player stopped falling, deactivating MLG");
            }
            return;
        }

        // Only activate water MLG when falling enough distance
        if (player.fallDistance < FALL_TRIGGER_DISTANCE) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Reduce prediction interval for high-speed falls
        double fallSpeed = Math.abs(velocity.y);
        long dynamicInterval = fallSpeed > 1.0 ? PREDICTION_INTERVAL / 2 : PREDICTION_INTERVAL;

        if (currentTime - lastPredictionTime < dynamicInterval) {
            return;
        }
        lastPredictionTime = currentTime;

        // Use the DISTANCE-BASED prediction function
        MLGPredictionResult prediction =
                MLGAnalyzer.analyzeFallAndPlacement(client, player, velocity);

        if (prediction.shouldPlace()) {
            BlockPos targetBlock = prediction.getHighestLandingBlock();
            Vec3d targetPos = prediction.getPlacementTarget();

            MLGMaster.LOGGER.info("EXECUTING DISTANCE-BASED PLACEMENT:");
            MLGMaster.LOGGER.info("Target: {} at {}", targetBlock, targetPos);

            if (BlockPlacer.executeBlockPlacement(client, player, prediction)) {
                isActive = true;
                
                if (prediction.getTargetItem() == Items.WATER_BUCKET) {
                    waterPlaced = true;
                    placedWaterPos = targetBlock.up();
                    waterPlacementTime = currentTime;
                    pickupAttempted = false;
                    MLGMaster.LOGGER.info("Water placed at {} - will attempt pickup after landing", placedWaterPos);
                }
                
                MLGMaster.LOGGER.info("Distance-based water placement successful!");

                // 500 ms wait
                lastPredictionTime = currentTime + 500;
            } else {
                MLGMaster.LOGGER.warn("Distance-based water placement failed");
            }
        } else if (prediction.willLand()) {
            MLGMaster.LOGGER.info("Distance analysis: {} | Distance: {} | Speed: {}",
                    prediction.getReason(), prediction.getDistanceToTarget(), Math.abs(velocity.y));
        }
    }

    private static void handleWaterPickup(MinecraftClient client, ClientPlayerEntity player) {
        if (placedWaterPos == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        
        if (currentTime - waterPlacementTime < WATER_PICKUP_DELAY) {
            return;
        }

        MLGMaster.LOGGER.info("Attempting to pick up water at {}", placedWaterPos);

        // Check if there's still water at the placed position
        if (client.world.getBlockState(placedWaterPos).getBlock() != Blocks.WATER) {
            MLGMaster.LOGGER.info("No water found at {} - pickup not needed", placedWaterPos);
            resetWaterTracking();
            return;
        }

        // Ensure we have an empty bucket
        if (!InventoryManager.ensureItemInHand(player, Items.BUCKET)) {
            MLGMaster.LOGGER.warn("No empty bucket available for water pickup");
            resetWaterTracking();
            return;
        }

        // Attempt to pick up the water
        if (pickupWater(client, player, placedWaterPos)) {
            MLGMaster.LOGGER.info("Successfully picked up water at {}", placedWaterPos);
        } else {
            MLGMaster.LOGGER.warn("Failed to pick up water at {}", placedWaterPos);
        }

        resetWaterTracking();
    }

    private static boolean pickupWater(MinecraftClient client, ClientPlayerEntity player, BlockPos waterPos) {
        try {
            // Store original rotation
            PlayerRotationManager.storeOriginalRotation(player);
            
            // Look at the water block
            Vec3d waterCenter = Vec3d.ofCenter(waterPos);
            PlayerRotationManager.lookAtTarget(player, waterCenter);
            
            Thread.sleep(50); // Small delay for rotation
            
            // Create hit result for the water block
            Vec3d hitPos = new Vec3d(waterPos.getX() + 0.5, waterPos.getY() + 0.5, waterPos.getZ() + 0.5);
            BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, waterPos, false);
            
            // Try to interact with the water block using empty bucket
            boolean success = MLGBlockPlacer.interactBlock(client, player, Hand.MAIN_HAND, waterPos, hitPos, Direction.UP);
            
            if (!success) {
                // Try item interaction as fallback
                success = MLGBlockPlacer.placeItem(client, player, Hand.MAIN_HAND);
            }
            
            return success;
            
        } catch (Exception e) {
            MLGMaster.LOGGER.error("Error during water pickup: {}", e.getMessage());
            return false;
        } finally {
            PlayerRotationManager.restoreOriginalRotation(player);
        }
    }

    private static void resetWaterTracking() {
        waterPlaced = false;
        placedWaterPos = null;
        waterPlacementTime = 0;
        pickupAttempted = true;
    }

    // Getter methods for external access
    public static boolean isActive() {
        return isActive;
    }

    public static void setActive(boolean active) {
        isActive = active;
        if (!active) {
            resetWaterTracking(); // Reset tracking when deactivating
        }
    }
    
    // Additional getter methods for water pickup status
    public static boolean isWaterPlaced() {
        return waterPlaced;
    }
    
    public static BlockPos getPlacedWaterPos() {
        return placedWaterPos;
    }
}
