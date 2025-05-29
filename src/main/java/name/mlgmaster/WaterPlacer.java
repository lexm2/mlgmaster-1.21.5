package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Blocks;

public class WaterPlacer {
    
    /**ks
     * Execute water placement using pre-computed prediction results
     * This ensures consistency with the handler's analysis
     */
    public static boolean executeWaterPlacement(MinecraftClient client, ClientPlayerEntity player, 
                                              WaterMLGHandler.MLGPredictionResult prediction) {
        MLGMaster.LOGGER.info("🎯 EXECUTING WATER PLACEMENT based on shared prediction...");
        
        if (!prediction.shouldPlace()) {
            MLGMaster.LOGGER.warn("❌ Prediction indicates placement should not occur: {}", prediction.getReason());
            return false;
        }
        
        // Ensure we have a water bucket
        if (!InventoryManager.ensureWaterBucketInHand(player)) {
            MLGMaster.LOGGER.warn("❌ No water bucket available");
            return false;
        }
        
        // Get placement details from the shared prediction
        BlockPos targetLandingBlock = prediction.getHighestLandingBlock();
        Vec3d waterPlacementTarget = prediction.getWaterPlacementTarget();
        BlockPos waterPlacementPos = targetLandingBlock.up();
        
        MLGMaster.LOGGER.info("🎯 PLACEMENT TARGET DETAILS:");
        MLGMaster.LOGGER.info("  Landing block: {} (X={}, Y={}, Z={})", 
            targetLandingBlock, targetLandingBlock.getX(), targetLandingBlock.getY(), targetLandingBlock.getZ());
        MLGMaster.LOGGER.info("  Water position: {} (X={}, Y={}, Z={})", 
            waterPlacementPos, waterPlacementPos.getX(), waterPlacementPos.getY(), waterPlacementPos.getZ());
        MLGMaster.LOGGER.info("  Target center: {} (X={:.3f}, Y={:.3f}, Z={:.3f})", 
            waterPlacementTarget, waterPlacementTarget.x, waterPlacementTarget.y, waterPlacementTarget.z);
        
        // Validate placement location is clear
        var blockAtPlacement = client.world.getBlockState(waterPlacementPos);
        if (blockAtPlacement.getBlock() != Blocks.AIR) {
            MLGMaster.LOGGER.warn("❌ Placement location {} is not air: {}", 
                waterPlacementPos, blockAtPlacement.getBlock());
            
            // Try alternative blocks from the landing result
            return tryAlternativePlacements(client, player, prediction);
        }
        
        MLGMaster.LOGGER.info("✅ Placement location {} is clear (air)", waterPlacementPos);
        
        // Create hit result for placement on the target block
        BlockHitResult waterPlacementHit = createHitResult(waterPlacementPos, targetLandingBlock);
        
        MLGMaster.LOGGER.info("🎯 HIT RESULT DETAILS:");
        MLGMaster.LOGGER.info("  Hit position: {} (X={:.3f}, Y={:.3f}, Z={:.3f})", 
            waterPlacementHit.getPos(), waterPlacementHit.getPos().x, waterPlacementHit.getPos().y, waterPlacementHit.getPos().z);
        MLGMaster.LOGGER.info("  Hit block: {} (X={}, Y={}, Z={})", 
            waterPlacementHit.getBlockPos(), waterPlacementHit.getBlockPos().getX(), 
            waterPlacementHit.getBlockPos().getY(), waterPlacementHit.getBlockPos().getZ());
        MLGMaster.LOGGER.info("  Hit side: {}", waterPlacementHit.getSide());
        MLGMaster.LOGGER.info("  Is inside: {}", waterPlacementHit.isInsideBlock());
        
        // Execute placement with precise aim
        return executePlacementWithAim(client, player, waterPlacementPos, waterPlacementHit, waterPlacementTarget);
    }
    
    /**
     * Try alternative placement locations if the primary location is blocked
     */
    private static boolean tryAlternativePlacements(MinecraftClient client, ClientPlayerEntity player, 
                                                   WaterMLGHandler.MLGPredictionResult prediction) {
        MLGMaster.LOGGER.info("🔄 TRYING ALTERNATIVE PLACEMENTS...");
        
        LandingPredictor.HitboxLandingResult landingResult = prediction.getLandingResult();
        if (landingResult == null) {
            MLGMaster.LOGGER.warn("❌ No landing result available for alternatives");
            return false;
        }
        
        // Try other hit blocks in height order (highest first)
        java.util.List<BlockPos> sortedHitBlocks = new java.util.ArrayList<>(landingResult.getAllHitBlocks());
        sortedHitBlocks.sort((a, b) -> Integer.compare(b.getY(), a.getY())); // Sort by Y descending
        
        MLGMaster.LOGGER.info("🔍 ALTERNATIVE BLOCKS (sorted by height):");
        for (int i = 0; i < sortedHitBlocks.size(); i++) {
            BlockPos block = sortedHitBlocks.get(i);
            MLGMaster.LOGGER.info("  {}. {} (X={}, Y={}, Z={})", 
                i + 1, block, block.getX(), block.getY(), block.getZ());
        }
        
        for (BlockPos altBlock : sortedHitBlocks) {
            // Skip if this is the same as our failed primary block
            if (altBlock.equals(prediction.getHighestLandingBlock())) {
                MLGMaster.LOGGER.info("⏭️ Skipping primary block {} (already failed)", altBlock);
                continue;
            }
            
            // Check if alternative block is safe (if not safe, we should place water)
            Vec3d currentPos = player.getPos();
            if (SafeLandingBlockChecker.shouldSkipWaterPlacement(client, player, altBlock, currentPos)) {
                MLGMaster.LOGGER.info("⏭️ Skipping alternative block {} - landing is safe", altBlock);
                continue;
            }
            
            BlockPos altPlacement = altBlock.up();
            var blockAtAltPlacement = client.world.getBlockState(altPlacement);
            
            MLGMaster.LOGGER.info("🔍 Testing alternative: {} → water at {} (currently: {})", 
                altBlock, altPlacement, blockAtAltPlacement.getBlock());
            
            if (blockAtAltPlacement.getBlock() == Blocks.AIR) {
                MLGMaster.LOGGER.info("✅ Found alternative placement at {} (on block {})", altPlacement, altBlock);
                
                Vec3d altTarget = Vec3d.ofCenter(altPlacement);
                BlockHitResult altHit = createHitResult(altPlacement, altBlock);
                
                return executePlacementWithAim(client, player, altPlacement, altHit, altTarget);
            } else {
                MLGMaster.LOGGER.info("❌ Alternative placement {} blocked by {}", 
                    altPlacement, blockAtAltPlacement.getBlock());
            }
        }
        
        MLGMaster.LOGGER.warn("❌ No valid alternative placement locations found");
        return false;
    }
    
    /**
     * Create a hit result for block interaction
     */
    private static BlockHitResult createHitResult(BlockPos waterPos, BlockPos targetBlock) {
        // Calculate hit position on the top face of the target block
        Vec3d hitPos = new Vec3d(
            targetBlock.getX() + 0.5,
            targetBlock.getY() + 1.0, // Top of the block
            targetBlock.getZ() + 0.5
        );
        
        MLGMaster.LOGGER.info("🎯 CREATING HIT RESULT:");
        MLGMaster.LOGGER.info("  Water position: {} (X={}, Y={}, Z={})", 
            waterPos, waterPos.getX(), waterPos.getY(), waterPos.getZ());
        MLGMaster.LOGGER.info("  Target block: {} (X={}, Y={}, Z={})", 
            targetBlock, targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
        MLGMaster.LOGGER.info("  Hit position: {} (X={:.3f}, Y={:.3f}, Z={:.3f})", 
            hitPos, hitPos.x, hitPos.y, hitPos.z);
        MLGMaster.LOGGER.info("  Hit side: UP (placing on top of block)");
        
        // Always target the UP face since we're placing on top
        return new BlockHitResult(hitPos, Direction.UP, targetBlock, false);
    }
    
    /**
     * Execute the actual placement with multiple strategies and precise aiming
     * REMOVED the "look down" strategy that was placing water in wrong location
     */
    private static boolean executePlacementWithAim(MinecraftClient client, ClientPlayerEntity player, 
                                                  BlockPos placementPos, BlockHitResult hitResult, Vec3d lookTarget) {
        MLGMaster.LOGGER.info("🎯 EXECUTING PLACEMENT WITH PRECISE AIM:");
        MLGMaster.LOGGER.info("  Placement pos: {} (X={}, Y={}, Z={})", 
            placementPos, placementPos.getX(), placementPos.getY(), placementPos.getZ());
        MLGMaster.LOGGER.info("  Look target: {} (X={:.3f}, Y={:.3f}, Z={:.3f})", 
            lookTarget, lookTarget.x, lookTarget.y, lookTarget.z);
        
        // Validate current state
        MLGMaster.LOGGER.info("📋 PRE-PLACEMENT VALIDATION:");
        MLGMaster.LOGGER.info("  Player position: {} (X={:.3f}, Y={:.3f}, Z={:.3f})", 
            player.getPos(), player.getPos().x, player.getPos().y, player.getPos().z);
        MLGMaster.LOGGER.info("  Main hand item: {}", player.getMainHandStack().getItem());
        MLGMaster.LOGGER.info("  Off hand item: {}", player.getOffHandStack().getItem());
        MLGMaster.LOGGER.info("  Target block state: {}", client.world.getBlockState(hitResult.getBlockPos()));
        MLGMaster.LOGGER.info("  Placement location state: {}", client.world.getBlockState(placementPos));
        
        // Store rotation before modifying
        PlayerRotationManager.storeOriginalRotation(player);
        
        try {
            // Look at the specific target location
            MLGMaster.LOGGER.info("🔄 ADJUSTING AIM to target: {}", lookTarget);
            PlayerRotationManager.lookAtTarget(player, lookTarget);
            
            // Small delay for rotation to take effect
            Thread.sleep(1);
            
            MLGMaster.LOGGER.info("🎯 Current player rotation: Yaw={:.2f}, Pitch={:.2f}", 
                player.getYaw(), player.getPitch());
            
            // Strategy 1: Standard block interaction (right-click on block face)
            MLGMaster.LOGGER.info("🔥 STRATEGY 1: Standard block interaction");
            MLGMaster.LOGGER.info("  Interacting with block: {} at {}", 
                client.world.getBlockState(hitResult.getBlockPos()).getBlock(), hitResult.getBlockPos());
            MLGMaster.LOGGER.info("  Hit result side: {}", hitResult.getSide());
            MLGMaster.LOGGER.info("  Hit position: {}", hitResult.getPos());
            MLGMaster.LOGGER.info("  Using hand: MAIN_HAND");
            
            var result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
            MLGMaster.LOGGER.info("  ✅ Strategy 1 result: {} (accepted: {})", result, result.isAccepted());
            
            if (result.isAccepted()) {
                MLGMaster.LOGGER.info("🎉 SUCCESS! Water placed at {} with interactBlock strategy", placementPos);
                return true;
            } else {
                MLGMaster.LOGGER.warn("❌ Strategy 1 FAILED: Block interaction was not accepted");
                MLGMaster.LOGGER.warn("  Possible reasons:");
                MLGMaster.LOGGER.warn("    - Target block is not solid/interactable");
                MLGMaster.LOGGER.warn("    - Placement location is already occupied");
                MLGMaster.LOGGER.warn("    - Player is too far from target");
                MLGMaster.LOGGER.warn("    - Water bucket is not in correct hand");
                MLGMaster.LOGGER.warn("    - Hit result is invalid");
            }
            
            // Strategy 2: Try using item while still aiming at target (NOT looking down!)
            MLGMaster.LOGGER.info("🔥 STRATEGY 2: Item interaction while aiming at target");
            MLGMaster.LOGGER.info("  Still aiming at: {}", lookTarget);
            MLGMaster.LOGGER.info("  Current rotation: Yaw={:.2f}, Pitch={:.2f}", player.getYaw(), player.getPitch());
            
            var itemResult = client.interactionManager.interactItem(player, Hand.MAIN_HAND);
            MLGMaster.LOGGER.info("  ✅ Strategy 2 result: {} (accepted: {})", itemResult, itemResult.isAccepted());
            
            if (itemResult.isAccepted()) {
                MLGMaster.LOGGER.info("🎉 SUCCESS! Water placed with interactItem while aiming at target");
                return true;
            } else {
                MLGMaster.LOGGER.warn("❌ Strategy 2 FAILED: Item interaction was not accepted");
            }
            
            // Strategy 3: Try different hand if water bucket is there
            if (player.getOffHandStack().getItem().toString().contains("water_bucket")) {
                MLGMaster.LOGGER.info("🔥 STRATEGY 3: Trying offhand");
                MLGMaster.LOGGER.info("  Off hand has water bucket, trying interaction");
                
                var offhandResult = client.interactionManager.interactItem(player, Hand.OFF_HAND);
                MLGMaster.LOGGER.info("  ✅ Strategy 3 result: {} (accepted: {})", offhandResult, offhandResult.isAccepted());
                
                if (offhandResult.isAccepted()) {
                    MLGMaster.LOGGER.info("🎉 SUCCESS! Water placed with offhand");
                    return true;
                } else {
                    MLGMaster.LOGGER.warn("❌ Strategy 3 FAILED: Offhand interaction was not accepted");
                }
            } else {
                MLGMaster.LOGGER.info("⏭️ STRATEGY 3 SKIPPED: No water bucket in offhand");
            }
            
            // Strategy 4: Try re-creating hit result and block interaction
            MLGMaster.LOGGER.info("🔥 STRATEGY 4: Re-creating hit result");
            
            // Re-aim at target to ensure we're looking at the right place
            PlayerRotationManager.lookAtTarget(player, lookTarget);
            Thread.sleep(1);
            
            // Create a new hit result in case the original was bad
            BlockHitResult newHitResult = createHitResult(placementPos, hitResult.getBlockPos());
            
            MLGMaster.LOGGER.info("  New hit result: block={}, pos={}, side={}", 
                newHitResult.getBlockPos(), newHitResult.getPos(), newHitResult.getSide());
            
            var directResult = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, newHitResult);
            MLGMaster.LOGGER.info("  ✅ Strategy 4 result: {} (accepted: {})", directResult, directResult.isAccepted());
            
            if (directResult.isAccepted()) {
                MLGMaster.LOGGER.info("🎉 SUCCESS! Water placed with re-created hit result");
                return true;
            } else {
                MLGMaster.LOGGER.warn("❌ Strategy 4 FAILED: Re-created hit result was not accepted");
            }
            
            // All strategies failed - provide comprehensive failure analysis
            MLGMaster.LOGGER.error("💥 ALL PLACEMENT STRATEGIES FAILED!");
            MLGMaster.LOGGER.error("🔍 FAILURE ANALYSIS:");
            MLGMaster.LOGGER.error("  Target block: {} (state: {})", 
                hitResult.getBlockPos(), client.world.getBlockState(hitResult.getBlockPos()));
            MLGMaster.LOGGER.error("  Placement location: {} (state: {})", 
                placementPos, client.world.getBlockState(placementPos));
            MLGMaster.LOGGER.error("  Player distance to target: {:.3f} blocks", 
                player.getPos().distanceTo(lookTarget));
            MLGMaster.LOGGER.error("  Player main hand: {}", player.getMainHandStack());
            MLGMaster.LOGGER.error("  Player off hand: {}", player.getOffHandStack());
            MLGMaster.LOGGER.error("  Final rotation: Yaw={:.2f}, Pitch={:.2f}", player.getYaw(), player.getPitch());
            
        } catch (Exception e) {
            MLGMaster.LOGGER.error("💥 ERROR during placement execution: {}", e.getMessage());
            e.printStackTrace();
        } finally {
            // Always restore rotation
            PlayerRotationManager.restoreOriginalRotation(player);
            MLGMaster.LOGGER.info("🔄 Restored original player rotation");
        }
        
        return false;
    }
    
    // Legacy methods for backward compatibility
    
    /**
     * @deprecated Use executeWaterPlacement with MLGPredictionResult instead
     */
    @Deprecated
    public static boolean attemptPlacement(MinecraftClient client, ClientPlayerEntity player, Vec3d velocity) {
        MLGMaster.LOGGER.warn("⚠️ Using deprecated attemptPlacement method - consider updating to use shared prediction");
        
        // Fall back to doing our own prediction
        WaterMLGHandler.MLGPredictionResult prediction = WaterMLGHandler.analyzeFallAndPlacement(client, player, velocity);
        
        if (!prediction.shouldPlace()) {
            MLGMaster.LOGGER.info("❌ Legacy placement check failed: {}", prediction.getReason());
            return false;
        }
        
        return executeWaterPlacement(client, player, prediction);
    }
    
    /**
     * @deprecated Use WaterMLGHandler.analyzeFallAndPlacement instead
     */
    @Deprecated
    public static boolean checkAndAttemptPlacement(MinecraftClient client, ClientPlayerEntity player, Vec3d velocity) {
        return attemptPlacement(client, player, velocity);
    }
}
