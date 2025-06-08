package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Blocks;

public class BlockPlacer {

    public static boolean executeBlockPlacement(MinecraftClient client, ClientPlayerEntity player,
            MLGPredictionResult prediction) {

        if (!prediction.shouldPlace()) {
            MLGMaster.LOGGER.warn("Placement cancelled: {}", prediction.getReason());
            return false;
        }

        Item targetItem = prediction.getTargetItem();
        if (!InventoryManager.ensureItemInHand(player, targetItem)) {
            MLGMaster.LOGGER.warn("Required item {} not available",
                    targetItem.getName().getString());
            return false;
        }

        BlockPos targetLandingBlock = prediction.getHighestLandingBlock();
        Vec3d placementTarget = prediction.getPlacementTarget();
        BlockPos placementPos = targetLandingBlock.up();

        var blockAtPlacement = client.world.getBlockState(placementPos);
        if (blockAtPlacement.getBlock() != Blocks.AIR) {
            MLGMaster.LOGGER.info("Placement location {} is not air: {}, adjusting placement",
                    placementPos, blockAtPlacement.getBlock());

            targetLandingBlock = placementPos;
            placementPos = targetLandingBlock.up();
            placementTarget = Vec3d.ofCenter(placementPos);

            var newBlockAtPlacement = client.world.getBlockState(placementPos);
            if (newBlockAtPlacement.getBlock() != Blocks.AIR) {
                MLGMaster.LOGGER.warn("Adjusted placement location {} is also blocked: {}",
                        placementPos, newBlockAtPlacement.getBlock());
                return tryAlternativePlacements(client, player, prediction);
            }
        }

        return executePlacementWithMixin(client, player, targetLandingBlock, placementTarget,
                targetItem);
    }

    private static boolean tryAlternativePlacements(MinecraftClient client,
            ClientPlayerEntity player, MLGPredictionResult prediction) {
        MLGMaster.LOGGER.info("Trying alternative placements...");

        HitboxLandingResult landingResult = prediction.getLandingResult();
        if (landingResult == null) {
            MLGMaster.LOGGER.warn("No landing result available for alternatives");
            return false;
        }

        java.util.List<BlockPos> sortedHitBlocks =
                new java.util.ArrayList<>(landingResult.getAllHitBlocks());
        sortedHitBlocks.sort((a, b) -> Integer.compare(b.getY(), a.getY()));

        for (BlockPos altBlock : sortedHitBlocks) {
            if (altBlock.equals(prediction.getHighestLandingBlock())) {
                continue;
            }

            Vec3d currentPos = player.getPos();
            if (prediction.getTargetItem() == Items.WATER_BUCKET && SafeLandingBlockChecker
                    .shouldSkipWaterPlacement(client, player, altBlock, currentPos)) {
                continue;
            }

            BlockPos altPlacement = altBlock.up();
            if (client.world.getBlockState(altPlacement).getBlock() == Blocks.AIR) {
                MLGMaster.LOGGER.info("Found alternative placement at {} (on block {})",
                        altPlacement, altBlock);
                Vec3d altTarget = Vec3d.ofCenter(altPlacement);
                return executePlacementWithMixin(client, player, altBlock, altTarget,
                        prediction.getTargetItem());
            }
        }

        MLGMaster.LOGGER.warn("No valid alternative placement locations found");
        return false;
    }

    private static boolean executePlacementWithMixin(MinecraftClient client,
            ClientPlayerEntity player, BlockPos targetBlock, Vec3d lookTarget, Item targetItem) {

        PlayerRotationManager.storeOriginalRotation(player);

        try {
            // Look at the target location
            PlayerRotationManager.lookAtTarget(player, lookTarget);

            if (player.getMainHandStack().getItem() == targetItem) {
                boolean itemResult = MLGBlockPlacer.placeItem(client, player, Hand.MAIN_HAND);
                if (itemResult) {
                    return true;
                }
            }

            if (player.getOffHandStack().getItem() == targetItem) {
                boolean offhandResult = MLGBlockPlacer.placeItem(client, player, Hand.OFF_HAND);

                if (offhandResult) {
                    return true;
                }
            }

            MLGMaster.LOGGER.error("PLACEMENT FAILED!");
            MLGMaster.LOGGER.error("Failure analysis:");
            MLGMaster.LOGGER.error("  Target item: {}", targetItem.getName().getString());
            MLGMaster.LOGGER.error("  Target block: {} (state: {})", targetBlock,
                    client.world.getBlockState(targetBlock));
            MLGMaster.LOGGER.error("  Player distance to target: {} blocks",
                    player.getPos().distanceTo(lookTarget));
            MLGMaster.LOGGER.error("  Player main hand: {}", player.getMainHandStack());
            MLGMaster.LOGGER.error("  Player off hand: {}", player.getOffHandStack());
            MLGMaster.LOGGER.error("  Final rotation: Yaw={}, Pitch={}", player.getYaw(),
                    player.getPitch());

            return false;

        } catch (Exception e) {
            MLGMaster.LOGGER.error("ERROR during placement execution: {}", e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            PlayerRotationManager.restoreOriginalRotation(player);
        }
    }
}
