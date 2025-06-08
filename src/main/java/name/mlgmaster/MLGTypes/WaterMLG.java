package name.mlgmaster.MLGTypes;

import name.mlgmaster.BlockPlacer;
import name.mlgmaster.InventoryManager;
import name.mlgmaster.MLGBlockPlacer;
import name.mlgmaster.MLGMaster;
import name.mlgmaster.MLGPredictionResult;
import name.mlgmaster.MLGType;
import name.mlgmaster.MinecraftPhysics;
import name.mlgmaster.PlayerRotationManager;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class WaterMLG extends MLGType {
    private boolean waterPlaced = false;
    private BlockPos placedWaterPos = null;
    private long waterPlacementTime = 0;
    private static final long WATER_PICKUP_DELAY = 100;
    private boolean pickupAttempted = false;

    private Vec3d lastVelocity = new Vec3d(0, 0, 0);

    private static final double FALL_TRIGGER_DISTANCE = 4.5;

    @Override
    public boolean isApplicable(MinecraftClient client, ClientPlayerEntity player, Vec3d velocity, MLGPredictionResult prediction) {
        if (!InventoryManager.hasItem(player, Items.WATER_BUCKET)) {
            return false;
        }

        if (velocity.y >= -0.1 || player.isOnGround()) {
            return false;
        }

        if (player.fallDistance < FALL_TRIGGER_DISTANCE) {
            return false;
        }

        lastVelocity = velocity;

        return true;
    }

    @Override
    public boolean requiresHighFrequencyTimer() {
        return MinecraftPhysics.isNearTerminalVelocity(lastVelocity.y);
    }

    @Override
    public boolean canExecute(MinecraftClient client, ClientPlayerEntity player,
            MLGPredictionResult prediction) {
        return InventoryManager.hasItem(player, Items.WATER_BUCKET);
    }

    @Override
    public boolean execute(MinecraftClient client, ClientPlayerEntity player,
            MLGPredictionResult prediction) {
        if (prediction.shouldPlace()) {
            if (BlockPlacer.executeBlockPlacement(client, player, prediction)) {
            placedWaterPos = prediction.getHighestLandingBlock().up();
                return true;
            }
            return false;
        }

        return false;
    }

    @Override
    public void onSuccessfulPlacement(MinecraftClient client, ClientPlayerEntity player, long currentTime) {
        waterPlaced = true;
        waterPlacementTime = currentTime;
        pickupAttempted = false;
    }

    @Override
    public void handlePostLanding(MinecraftClient client, ClientPlayerEntity player) {
        if (waterPlaced && !pickupAttempted) {
            handleWaterPickup(client, player);
        }
    }

    private void handleWaterPickup(MinecraftClient client, ClientPlayerEntity player) {
        long currentTime = System.currentTimeMillis();

        if (currentTime - waterPlacementTime < WATER_PICKUP_DELAY) {
            return;
        }

        if (client.world.getBlockState(placedWaterPos).getBlock() != Blocks.WATER) {
            MLGMaster.LOGGER.info(
                    client.world.getBlockState(placedWaterPos).getBlock().getTranslationKey());
            reset();
            return;
        }

        if (pickupWater(client, player, placedWaterPos)) {
            reset();
        }
    }

    private boolean pickupWater(MinecraftClient client, ClientPlayerEntity player,
            BlockPos waterPos) {
        try {
            PlayerRotationManager.storeOriginalRotation(player);

            Vec3d waterCenter = Vec3d.ofCenter(waterPos);
            PlayerRotationManager.lookAtTarget(player, waterCenter);

            Thread.sleep(50);

            Vec3d hitPos =
                    new Vec3d(waterPos.getX() + 0.5, waterPos.getY() + 0.5, waterPos.getZ() + 0.5);

            boolean success = MLGBlockPlacer.interactBlock(client, player, Hand.MAIN_HAND, waterPos,
                    hitPos, Direction.UP);

            if (!success) {
                success = MLGBlockPlacer.placeItem(client, player, Hand.MAIN_HAND);
            }

            return success;

        } catch (Exception e) {
            return false;
        } finally {
            PlayerRotationManager.restoreOriginalRotation(player);
        }
    }

    @Override
    public void reset() {
        waterPlaced = false;
        placedWaterPos = null;
        waterPlacementTime = 0;
        pickupAttempted = true;
    }

    @Override
    public int getPriority() {
        return 10; // High priority for water MLG
    }

    @Override
    public String getName() {
        return "Water MLG";
    }

    public boolean isWaterPlaced() {
        return waterPlaced;
    }

    public BlockPos getPlacedWaterPos() {
        return placedWaterPos;
    }
}
