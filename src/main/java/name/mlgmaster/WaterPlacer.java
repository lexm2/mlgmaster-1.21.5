package name.mlgmaster;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class WaterPlacer {

        /**
         * Execute water placement using pre-computed prediction results This ensures consistency
         * with the handler's analysis
         */
        public static boolean executeWaterPlacement(MinecraftClient client,
                        ClientPlayerEntity player, WaterMLGHandler.MLGPredictionResult prediction) {
                MLGMaster.LOGGER.info("Executing water placement based on prediction...");

                if (!prediction.shouldPlace()) {
                        MLGMaster.LOGGER.warn("Placement cancelled: {}", prediction.getReason());
                        return false;
                }

                // Ensure we have a water bucket
                if (!InventoryManager.ensureWaterBucketInHand(player)) {
                        MLGMaster.LOGGER.warn("No water bucket available");
                        return false;
                }

                // Get placement details from the shared prediction
                BlockPos targetLandingBlock = prediction.getHighestLandingBlock();
                BlockPos initialWaterPlacementPos = targetLandingBlock.up();

                MLGMaster.LOGGER.info("Initial placement target details:");
                MLGMaster.LOGGER.info("  Landing block: {} at ({}, {}, {})", targetLandingBlock,
                                targetLandingBlock.getX(), targetLandingBlock.getY(),
                                targetLandingBlock.getZ());
                MLGMaster.LOGGER.info("  Initial water position: {} at ({}, {}, {})",
                                initialWaterPlacementPos, initialWaterPlacementPos.getX(),
                                initialWaterPlacementPos.getY(), initialWaterPlacementPos.getZ());

                // Find the first clear air block above the landing block (recursive)
                WaterPlacementLocation placementLocation = findClearWaterPlacement(client,
                                targetLandingBlock, initialWaterPlacementPos, 0);

                if (placementLocation == null) {
                        MLGMaster.LOGGER.warn(
                                        "Could not find a clear location for water placement after checking multiple blocks above landing block");
                        // Try alternative blocks from the landing result as final fallback
                        return tryAlternativePlacements(client, player, prediction);
                }

                MLGMaster.LOGGER.info("Final placement location found:");
                MLGMaster.LOGGER.info("  Water position: {} at ({}, {}, {})",
                                placementLocation.waterPos, placementLocation.waterPos.getX(),
                                placementLocation.waterPos.getY(),
                                placementLocation.waterPos.getZ());
                MLGMaster.LOGGER.info("  Placement block: {} at ({}, {}, {})",
                                placementLocation.placementBlock,
                                placementLocation.placementBlock.getX(),
                                placementLocation.placementBlock.getY(),
                                placementLocation.placementBlock.getZ());
                MLGMaster.LOGGER.info("  Blocks checked above original: {}",
                                placementLocation.blocksAboveOriginal);

                // Create hit result for placement on the final target block
                BlockHitResult waterPlacementHit = createHitResult(placementLocation.waterPos,
                                placementLocation.placementBlock);

                MLGMaster.LOGGER.info("Hit result details:");
                MLGMaster.LOGGER.info("  Hit position: ({}, {}, {})", waterPlacementHit.getPos().x,
                                waterPlacementHit.getPos().y, waterPlacementHit.getPos().z);
                MLGMaster.LOGGER.info("  Hit block: {} at ({}, {}, {})",
                                waterPlacementHit.getBlockPos(),
                                waterPlacementHit.getBlockPos().getX(),
                                waterPlacementHit.getBlockPos().getY(),
                                waterPlacementHit.getBlockPos().getZ());
                MLGMaster.LOGGER.info("  Hit side: {}", waterPlacementHit.getSide());
                MLGMaster.LOGGER.info("  Is inside: {}", waterPlacementHit.isInsideBlock());

                // Calculate target center for aiming
                Vec3d waterPlacementTarget = Vec3d.ofCenter(placementLocation.waterPos);

                // Execute placement with precise aim
                return executePlacementWithAim(client, player, placementLocation.waterPos,
                                waterPlacementHit, waterPlacementTarget);
        }

        /**
         * Recursively find a clear air block for water placement by moving up one block at a time
         */
        private static WaterPlacementLocation findClearWaterPlacement(MinecraftClient client,
                        BlockPos originalLandingBlock, BlockPos currentWaterPos,
                        int blocksChecked) {
                final int MAX_BLOCKS_TO_CHECK = 10; // Prevent infinite recursion

                MLGMaster.LOGGER.info("Checking water placement at {} (blocks above original: {})",
                                currentWaterPos, blocksChecked);

                // Safety check to prevent infinite recursion
                if (blocksChecked >= MAX_BLOCKS_TO_CHECK) {
                        MLGMaster.LOGGER.warn(
                                        "Reached maximum blocks to check ({}), stopping recursion",
                                        MAX_BLOCKS_TO_CHECK);
                        return null;
                }

                // Check if current position is air
                var blockAtCurrentPos = client.world.getBlockState(currentWaterPos);
                if (blockAtCurrentPos.getBlock() == Blocks.AIR) {
                        MLGMaster.LOGGER.info(
                                        "Found clear air block at {} after checking {} blocks above original",
                                        currentWaterPos, blocksChecked);

                        // Determine which block to place water on top of
                        BlockPos placementBlock = currentWaterPos.down(); // The block below the air
                                                                          // block

                        return new WaterPlacementLocation(currentWaterPos, placementBlock,
                                        blocksChecked);
                } else {
                        MLGMaster.LOGGER.info(
                                        "Position {} is not air (contains: {}), moving up one block",
                                        currentWaterPos, blockAtCurrentPos.getBlock());

                        // Recursively check the block above
                        return findClearWaterPlacement(client, originalLandingBlock,
                                        currentWaterPos.up(), blocksChecked + 1);
                }
        }

        /**
         * Helper class to store water placement location information
         */
        private static class WaterPlacementLocation {
                final BlockPos waterPos; // Where to place the water
                final BlockPos placementBlock; // Which block to place water on top of
                final int blocksAboveOriginal; // How many blocks above the original target

                WaterPlacementLocation(BlockPos waterPos, BlockPos placementBlock,
                                int blocksAboveOriginal) {
                        this.waterPos = waterPos;
                        this.placementBlock = placementBlock;
                        this.blocksAboveOriginal = blocksAboveOriginal;
                }
        }

        /**
         * Try alternative placement locations if the primary location is blocked
         */
        private static boolean tryAlternativePlacements(MinecraftClient client,
                        ClientPlayerEntity player, WaterMLGHandler.MLGPredictionResult prediction) {
                MLGMaster.LOGGER.info("Trying alternative placements...");

                LandingPredictor.HitboxLandingResult landingResult = prediction.getLandingResult();
                if (landingResult == null) {
                        MLGMaster.LOGGER.warn("No landing result available for alternatives");
                        return false;
                }

                // Try other hit blocks in height order (highest first)
                java.util.List<BlockPos> sortedHitBlocks =
                                new java.util.ArrayList<>(landingResult.getAllHitBlocks());
                sortedHitBlocks.sort((a, b) -> Integer.compare(b.getY(), a.getY())); // Sort by Y
                                                                                     // descending

                MLGMaster.LOGGER.info("Alternative blocks (sorted by height):");
                for (int i = 0; i < sortedHitBlocks.size(); i++) {
                        BlockPos block = sortedHitBlocks.get(i);
                        MLGMaster.LOGGER.info("  {}. {} at ({}, {}, {})", i + 1, block,
                                        block.getX(), block.getY(), block.getZ());
                }

                for (BlockPos altBlock : sortedHitBlocks) {
                        // Skip if this is the same as our failed primary block
                        if (altBlock.equals(prediction.getHighestLandingBlock())) {
                                MLGMaster.LOGGER.info("Skipping primary block {} (already failed)",
                                                altBlock);
                                continue;
                        }

                        // Check if alternative block is safe (if not safe, we should place water)
                        Vec3d currentPos = player.getPos();
                        if (SafeLandingBlockChecker.shouldSkipWaterPlacement(client, player,
                                        altBlock, currentPos)) {
                                MLGMaster.LOGGER.info(
                                                "Skipping alternative block {} - landing is safe",
                                                altBlock);
                                continue;
                        }

                        // Try to find a clear placement location above this alternative block
                        // (recursive)
                        WaterPlacementLocation altPlacementLocation =
                                        findClearWaterPlacement(client, altBlock, altBlock.up(), 0);

                        if (altPlacementLocation != null) {
                                MLGMaster.LOGGER.info(
                                                "Found alternative placement at {} (on block {}, {} blocks above)",
                                                altPlacementLocation.waterPos,
                                                altPlacementLocation.placementBlock,
                                                altPlacementLocation.blocksAboveOriginal);

                                Vec3d altTarget = Vec3d.ofCenter(altPlacementLocation.waterPos);
                                BlockHitResult altHit = createHitResult(
                                                altPlacementLocation.waterPos,
                                                altPlacementLocation.placementBlock);

                                return executePlacementWithAim(client, player,
                                                altPlacementLocation.waterPos, altHit, altTarget);
                        } else {
                                MLGMaster.LOGGER.info(
                                                "Alternative block {} area is too cluttered (no clear space found)",
                                                altBlock);
                        }
                }

                MLGMaster.LOGGER.warn("No valid alternative placement locations found");
                return false;
        }

        /**
         * Create a hit result for block interaction
         */
        private static BlockHitResult createHitResult(BlockPos waterPos, BlockPos targetBlock) {
                // Calculate hit position on the top face of the target block
                Vec3d hitPos = new Vec3d(targetBlock.getX() + 0.5, targetBlock.getY() + 1.0, // Top
                                                                                             // of
                                                                                             // the
                                                                                             // block
                                targetBlock.getZ() + 0.5);

                MLGMaster.LOGGER.info("Creating hit result:");
                MLGMaster.LOGGER.info("  Water position: {} at ({}, {}, {})", waterPos,
                                waterPos.getX(), waterPos.getY(), waterPos.getZ());
                MLGMaster.LOGGER.info("  Target block: {} at ({}, {}, {})", targetBlock,
                                targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
                MLGMaster.LOGGER.info("  Hit position: ({}, {}, {})", hitPos.x, hitPos.y, hitPos.z);
                MLGMaster.LOGGER.info("  Hit side: UP (placing on top of block)");

                // Always target the UP face since we're placing on top
                return new BlockHitResult(hitPos, Direction.UP, targetBlock, false);
        }

        /**
         * Execute the actual placement with multiple strategies and precise aiming Prioritizes item
         * interaction over block interaction for better reliability
         */
        private static boolean executePlacementWithAim(MinecraftClient client,
                        ClientPlayerEntity player, BlockPos placementPos, BlockHitResult hitResult,
                        Vec3d lookTarget) {
                MLGMaster.LOGGER.info("Executing placement with precise aim:");
                MLGMaster.LOGGER.info("  Placement pos: {} at ({}, {}, {})", placementPos,
                                placementPos.getX(), placementPos.getY(), placementPos.getZ());
                MLGMaster.LOGGER.info("  Look target: ({}, {}, {})", lookTarget.x, lookTarget.y,
                                lookTarget.z);

                // Validate current state
                MLGMaster.LOGGER.info("Pre-placement validation:");
                MLGMaster.LOGGER.info("  Player position: ({}, {}, {})", player.getPos().x,
                                player.getPos().y, player.getPos().z);
                MLGMaster.LOGGER.info("  Main hand item: {}", player.getMainHandStack().getItem());
                MLGMaster.LOGGER.info("  Off hand item: {}", player.getOffHandStack().getItem());
                MLGMaster.LOGGER.info("  Target block state: {}",
                                client.world.getBlockState(hitResult.getBlockPos()));
                MLGMaster.LOGGER.info("  Placement location state: {}",
                                client.world.getBlockState(placementPos));

                // Store rotation before modifying
                PlayerRotationManager.storeOriginalRotation(player);

                try {
                        // Look at the specific target location
                        MLGMaster.LOGGER.info("Adjusting aim to target: {}", lookTarget);
                        PlayerRotationManager.lookAtTarget(player, lookTarget);

                        // Small delay for rotation to take effect
                        Thread.sleep(1);

                        MLGMaster.LOGGER.info("Current player rotation: Yaw={}, Pitch={}",
                                        player.getYaw(), player.getPitch());

                        // Strategy 1 (PRIORITIZED): Item interaction while aiming at target
                        MLGMaster.LOGGER.info(
                                        "Strategy 1: Item interaction while aiming at target (PRIORITIZED)");
                        MLGMaster.LOGGER.info("  Aiming at: {}", lookTarget);
                        MLGMaster.LOGGER.info("  Current rotation: Yaw={}, Pitch={}",
                                        player.getYaw(), player.getPitch());

                        var itemResult = client.interactionManager.interactItem(player,
                                        Hand.MAIN_HAND);
                        MLGMaster.LOGGER.info("  Strategy 1 result: {} (accepted: {})", itemResult,
                                        itemResult.isAccepted());

                        if (itemResult.isAccepted()) {
                                MLGMaster.LOGGER.info(
                                                "SUCCESS! Water placed with interactItem while aiming at target");
                                return true;
                        } else {
                                MLGMaster.LOGGER.warn(
                                                "Strategy 1 FAILED: Item interaction was not accepted");
                        }

                        // Strategy 2: Try offhand if water bucket is there
                        if (player.getOffHandStack().getItem().toString()
                                        .contains("water_bucket")) {
                                MLGMaster.LOGGER.info("Strategy 2: Trying offhand");
                                MLGMaster.LOGGER.info(
                                                "  Off hand has water bucket, trying interaction");

                                var offhandResult = client.interactionManager.interactItem(player,
                                                Hand.OFF_HAND);
                                MLGMaster.LOGGER.info("  Strategy 2 result: {} (accepted: {})",
                                                offhandResult, offhandResult.isAccepted());

                                if (offhandResult.isAccepted()) {
                                        MLGMaster.LOGGER.info("SUCCESS! Water placed with offhand");
                                        return true;
                                } else {
                                        MLGMaster.LOGGER.warn(
                                                        "Strategy 2 FAILED: Offhand interaction was not accepted");
                                }
                        } else {
                                MLGMaster.LOGGER.info(
                                                "Strategy 2 SKIPPED: No water bucket in offhand");
                        }

                        // Strategy 3: Standard block interaction (as fallback only)
                        MLGMaster.LOGGER.info("Strategy 3: Standard block interaction (FALLBACK)");
                        MLGMaster.LOGGER.info("  Interacting with block: {} at {}", client.world
                                        .getBlockState(hitResult.getBlockPos()).getBlock(),
                                        hitResult.getBlockPos());
                        MLGMaster.LOGGER.info("  Hit result side: {}", hitResult.getSide());
                        MLGMaster.LOGGER.info("  Hit position: {}", hitResult.getPos());
                        MLGMaster.LOGGER.info("  Using hand: MAIN_HAND");

                        var result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                                        hitResult);
                        MLGMaster.LOGGER.info("  Strategy 3 result: {} (accepted: {})", result,
                                        result.isAccepted());

                        if (result.isAccepted()) {
                                MLGMaster.LOGGER.info(
                                                "SUCCESS! Water placed at {} with interactBlock strategy",
                                                placementPos);
                                return true;
                        } else {
                                MLGMaster.LOGGER.warn(
                                                "Strategy 3 FAILED: Block interaction was not accepted");
                        }

                        // Strategy 4: Try re-creating hit result and block interaction
                        MLGMaster.LOGGER.info("Strategy 4: Re-creating hit result");

                        // Re-aim at target to ensure we're looking at the right place
                        PlayerRotationManager.lookAtTarget(player, lookTarget);
                        Thread.sleep(1);

                        // Create a new hit result in case the original was bad
                        BlockHitResult newHitResult =
                                        createHitResult(placementPos, hitResult.getBlockPos());

                        MLGMaster.LOGGER.info("  New hit result: block={}, pos={}, side={}",
                                        newHitResult.getBlockPos(), newHitResult.getPos(),
                                        newHitResult.getSide());

                        var directResult = client.interactionManager.interactBlock(player,
                                        Hand.MAIN_HAND, newHitResult);
                        MLGMaster.LOGGER.info("  Strategy 4 result: {} (accepted: {})",
                                        directResult, directResult.isAccepted());

                        if (directResult.isAccepted()) {
                                MLGMaster.LOGGER.info(
                                                "SUCCESS! Water placed with re-created hit result");
                                return true;
                        } else {
                                MLGMaster.LOGGER.warn(
                                                "Strategy 4 FAILED: Re-created hit result was not accepted");
                        }

                        // All strategies failed - provide comprehensive failure analysis
                        MLGMaster.LOGGER.error("ALL PLACEMENT STRATEGIES FAILED!");
                        MLGMaster.LOGGER.error("Failure analysis:");
                        MLGMaster.LOGGER.error("  Target block: {} (state: {})",
                                        hitResult.getBlockPos(),
                                        client.world.getBlockState(hitResult.getBlockPos()));
                        MLGMaster.LOGGER.error("  Placement location: {} (state: {})", placementPos,
                                        client.world.getBlockState(placementPos));
                        MLGMaster.LOGGER.error("  Player distance to target: {} blocks",
                                        player.getPos().distanceTo(lookTarget));
                        MLGMaster.LOGGER.error("  Player main hand: {}", player.getMainHandStack());
                        MLGMaster.LOGGER.error("  Player off hand: {}", player.getOffHandStack());
                        MLGMaster.LOGGER.error("  Final rotation: Yaw={}, Pitch={}",
                                        player.getYaw(), player.getPitch());

                } catch (Exception e) {
                        MLGMaster.LOGGER.error("ERROR during placement execution: {}",
                                        e.getMessage());
                        e.printStackTrace();
                } finally {
                        // Always restore rotation
                        PlayerRotationManager.restoreOriginalRotation(player);
                        MLGMaster.LOGGER.info("Restored original player rotation");
                }

                return false;
        }

        /**
         * @deprecated Use executeWaterPlacement with MLGPredictionResult instead
         */
        @Deprecated
        public static boolean attemptPlacement(MinecraftClient client, ClientPlayerEntity player,
                        Vec3d velocity) {
                MLGMaster.LOGGER.warn(
                                "Using deprecated attemptPlacement method - consider updating to use shared prediction");

                // Fall back to doing our own prediction
                WaterMLGHandler.MLGPredictionResult prediction =
                                WaterMLGHandler.analyzeFallAndPlacement(client, player, velocity);

                if (!prediction.shouldPlace()) {
                        MLGMaster.LOGGER.info("Legacy placement check failed: {}",
                                        prediction.getReason());
                        return false;
                }

                return executeWaterPlacement(client, player, prediction);
        }

        /**
         * @deprecated Use WaterMLGHandler.analyzeFallAndPlacement instead
         */
        @Deprecated
        public static boolean checkAndAttemptPlacement(MinecraftClient client,
                        ClientPlayerEntity player, Vec3d velocity) {
                return attemptPlacement(client, player, velocity);
        }
}

