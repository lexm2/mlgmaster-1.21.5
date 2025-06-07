package name.mlgmaster.MLGTypes;

import name.mlgmaster.BlockPlacer;
import name.mlgmaster.InventoryManager;
import name.mlgmaster.MLGBlockPlacer;
import name.mlgmaster.MLGPredictionResult;
import name.mlgmaster.MLGType;
import name.mlgmaster.PlayerRotationManager;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class WaterMLG extends MLGType {
    private boolean waterPlaced = false;
    private BlockPos placedWaterPos = null;
    private long waterPlacementTime = 0;
    private static final long WATER_PICKUP_DELAY = 100;
    private boolean pickupAttempted = false;

    @Override
    public boolean isApplicable(MinecraftClient client, ClientPlayerEntity player, MLGPredictionResult prediction) {
        return prediction.getTargetItem() == Items.WATER_BUCKET;
    }

    @Override
    public boolean canExecute(MinecraftClient client, ClientPlayerEntity player, MLGPredictionResult prediction) {
        return InventoryManager.hasItem(player, Items.WATER_BUCKET);
    }

    @Override
    public boolean execute(MinecraftClient client, ClientPlayerEntity player, MLGPredictionResult prediction) {
        return BlockPlacer.executeBlockPlacement(client, player, prediction);
    }

    @Override
    public void onSuccessfulPlacement(MinecraftClient client, ClientPlayerEntity player, 
                                    MLGPredictionResult prediction, long currentTime) {
        waterPlaced = true;
        placedWaterPos = prediction.getHighestLandingBlock().up();
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
        if (placedWaterPos == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        
        if (currentTime - waterPlacementTime < WATER_PICKUP_DELAY) {
            return;
        }

        if (client.world.getBlockState(placedWaterPos).getBlock() != Blocks.WATER) {
            reset();
            return;
        }

        if (!InventoryManager.ensureItemInHand(player, Items.BUCKET)) {
            reset();
            return;
        }

        pickupWater(client, player, placedWaterPos);
        reset();
    }

    private boolean pickupWater(MinecraftClient client, ClientPlayerEntity player, BlockPos waterPos) {
        try {
            PlayerRotationManager.storeOriginalRotation(player);
            
            Vec3d waterCenter = Vec3d.ofCenter(waterPos);
            PlayerRotationManager.lookAtTarget(player, waterCenter);
            
            Thread.sleep(50);
            
            Vec3d hitPos = new Vec3d(waterPos.getX() + 0.5, waterPos.getY() + 0.5, waterPos.getZ() + 0.5);
            BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, waterPos, false);
            
            boolean success = MLGBlockPlacer.interactBlock(client, player, Hand.MAIN_HAND, waterPos, hitPos, Direction.UP);
            
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