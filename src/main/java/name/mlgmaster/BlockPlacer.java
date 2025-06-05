package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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

        MLGMaster.LOGGER.info("Placing {} block:", targetItem.getName().getString());
        MLGMaster.LOGGER.info("  Landing block: {} at ({}, {}, {})", targetLandingBlock,
                targetLandingBlock.getX(), targetLandingBlock.getY(), targetLandingBlock.getZ());
        MLGMaster.LOGGER.info("  Placement position: {} at ({}, {}, {})", placementPos,
                placementPos.getX(), placementPos.getY(), placementPos.getZ());

        // Check if placement location is clear
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

        LandingPredictor.HitboxLandingResult landingResult = prediction.getLandingResult();
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
            // For non-water blocks, we might not need to check landing safety
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
        MLGMaster.LOGGER.info("Executing placement with mixin accessor:");
        MLGMaster.LOGGER.info("  Target item: {}", targetItem.getName().getString());
        MLGMaster.LOGGER.info("  Target block: {}", targetBlock);
        MLGMaster.LOGGER.info("  Look target: ({}, {}, {})", lookTarget.x, lookTarget.y,
                lookTarget.z);

        PlayerRotationManager.storeOriginalRotation(player);

        try {
            // Look at the target location
            PlayerRotationManager.lookAtTarget(player, lookTarget);
            Thread.sleep(1);

            MLGMaster.LOGGER.info("Player rotation adjusted: Yaw={}, Pitch={}", player.getYaw(),
                    player.getPitch());
            MLGMaster.LOGGER.info("Player main hand item: {}", player.getMainHandStack().getItem());
            MLGMaster.LOGGER.info("Player off hand item: {}", player.getOffHandStack().getItem());

            // Strategy 1: Direct item placement (works for most items)
            MLGMaster.LOGGER.info("Strategy 1: Direct item placement");
            boolean itemResult = MLGBlockPlacer.placeItem(client, player, Hand.MAIN_HAND);
            MLGMaster.LOGGER.info("  Main hand result: {}", itemResult);

            if (itemResult) {
                MLGMaster.LOGGER.info("SUCCESS! {} placed with main hand item interaction",
                        targetItem.getName().getString());
                return true;
            }

            // Strategy 2: Try offhand if available
            if (player.getOffHandStack().getItem() == targetItem) {
                MLGMaster.LOGGER.info("Strategy 2: Offhand item placement");
                boolean offhandResult = MLGBlockPlacer.placeItem(client, player, Hand.OFF_HAND);
                MLGMaster.LOGGER.info("  Offhand result: {}", offhandResult);

                if (offhandResult) {
                    MLGMaster.LOGGER.info("SUCCESS! {} placed with offhand item interaction",
                            targetItem.getName().getString());
                    return true;
                }
            } else {
                MLGMaster.LOGGER.info("Strategy 2 skipped: No {} in offhand",
                        targetItem.getName().getString());
            }

            // Strategy 3: Block interaction with specific targeting
            MLGMaster.LOGGER.info("Strategy 3: Block interaction placement");
            Vec3d hitPos = new Vec3d(targetBlock.getX() + 0.5, targetBlock.getY() + 1.0,
                    targetBlock.getZ() + 0.5);
            boolean blockResult = MLGBlockPlacer.interactBlock(client, player, Hand.MAIN_HAND,
                    targetBlock, hitPos, Direction.UP);
            MLGMaster.LOGGER.info("  Block interaction result: {}", blockResult);

            if (blockResult) {
                MLGMaster.LOGGER.info("SUCCESS! {} placed with block interaction",
                        targetItem.getName().getString());
                return true;
            }

            // Strategy 4: Internal block interaction
            MLGMaster.LOGGER.info("Strategy 4: Internal block interaction placement");
            boolean internalResult = MLGBlockPlacer.interactBlockInternal(client, player,
                    Hand.MAIN_HAND, targetBlock, hitPos, Direction.UP);
            MLGMaster.LOGGER.info("  Internal block interaction result: {}", internalResult);

            if (internalResult) {
                MLGMaster.LOGGER.info("SUCCESS! {} placed with internal block interaction",
                        targetItem.getName().getString());
                return true;
            }

            // Strategy 5: Special handling for water buckets (legacy compatibility)
            if (targetItem == Items.WATER_BUCKET) {
                MLGMaster.LOGGER.info("Strategy 5: Water-specific placement");
                boolean waterResult = MLGBlockPlacer.placeWater(client, player, Hand.MAIN_HAND,
                        targetBlock, hitPos);
                MLGMaster.LOGGER.info("  Water-specific placement result: {}", waterResult);

                if (waterResult) {
                    MLGMaster.LOGGER.info("SUCCESS! Water placed with water-specific approach");
                    return true;
                }
            }

            MLGMaster.LOGGER.error("ALL PLACEMENT STRATEGIES FAILED!");
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
            MLGMaster.LOGGER.info("Restored original player rotation");
        }
    }
}
