package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class MLGAnalyzer {
    /**
     * DISTANCE-BASED PREDICTION FUNCTION
     */
    public static MLGPredictionResult analyzeFallAndPlacement(MinecraftClient client, ClientPlayerEntity player, Vec3d velocity) {
        Vec3d playerPos = player.getPos();
        
        MLGMaster.LOGGER.info("🚀 DISTANCE-BASED MLG ANALYSIS: Starting fall prediction");
        MLGMaster.LOGGER.info("📍 Player position: {} (X={:.3f}, Y={:.3f}, Z={:.3f})", 
            playerPos, playerPos.x, playerPos.y, playerPos.z);
        MLGMaster.LOGGER.info("🏃 Player velocity: {} (dX={:.3f}, dY={:.3f}, dZ={:.3f})", 
            velocity, velocity.x, velocity.y, velocity.z);
        MLGMaster.LOGGER.info("⬇️ Fall speed: {:.3f} blocks/tick ({:.3f} blocks/second)", 
            Math.abs(velocity.y), Math.abs(velocity.y) * 20);
        
        // Basic fall validation
        if (velocity.y >= -0.1) {
            MLGMaster.LOGGER.info("❌ Not falling (velocity.y = {:.3f})", velocity.y);
            return new MLGPredictionResult(false, false, null, null, null, -1, 
                "Not falling", new SafeLandingBlockChecker.SafetyResult(false, "Not falling"),
                0, false);
        }
        
        if (player.fallDistance < 3.0) {
            MLGMaster.LOGGER.info("❌ Insufficient fall distance ({:.3f} < 3.0)", player.fallDistance);
            return new MLGPredictionResult(false, false, null, null, null, -1, 
                "Insufficient fall distance", new SafeLandingBlockChecker.SafetyResult(false, "Insufficient fall distance"),
                0, false);
        }
        
        if (player.isOnGround()) {
            MLGMaster.LOGGER.info("❌ Player is on ground");
            return new MLGPredictionResult(false, false, null, null, null, -1, 
                "On ground", new SafeLandingBlockChecker.SafetyResult(false, "On ground"),
                0, false);
        }
        
        MLGMaster.LOGGER.info("✅ Fall validation passed - proceeding with distance-based prediction");
        
        // Calculate distance-based placement parameters
        MLGDistanceCalculator.DistanceResult distanceResult = MLGDistanceCalculator.calculatePlacementDistance(client, player, velocity);
        
        // Predict landing with full hitbox consideration
        LandingPredictor.HitboxLandingResult landingResult = LandingPredictor.predictHitboxLanding(client, player, playerPos, velocity);
        
        if (landingResult == null) {
            MLGMaster.LOGGER.info("❌ No landing predicted by hitbox analysis");
            return new MLGPredictionResult(false, false, null, null, null, -1, 
                "No landing predicted", new SafeLandingBlockChecker.SafetyResult(false, "No landing predicted"),
                distanceResult.getPlacementDistance(), distanceResult.isUrgent());
        }
        
        // Find the HIGHEST block from all hit blocks
        BlockPos highestBlock = findHighestHitBlock(landingResult.getAllHitBlocks());
        
        if (highestBlock == null) {
            MLGMaster.LOGGER.warn("⚠️ No highest block found despite successful landing prediction");
            return new MLGPredictionResult(false, false, landingResult, null, null, -1, 
                "No highest block found", new SafeLandingBlockChecker.SafetyResult(false, "No highest block found"),
                distanceResult.getPlacementDistance(), distanceResult.isUrgent());
        }
        
        // Use the highest block for safety checking
        SafeLandingBlockChecker.SafetyResult safetyResult = 
            SafeLandingBlockChecker.checkLandingSafety(client, player, highestBlock, playerPos);
        
        if (safetyResult.isSafe()) {
            MLGMaster.LOGGER.info("🛡️ Landing is SAFE ({}), no water needed", safetyResult.getReason());
            return new MLGPredictionResult(false, true, landingResult, highestBlock, null, -1, 
                "Safe landing: " + safetyResult.getReason(), safetyResult,
                distanceResult.getPlacementDistance(), distanceResult.isUrgent());
        }
        
        // Calculate water placement target
        BlockPos waterPlacementPos = highestBlock.up();
        Vec3d waterPlacementTarget = Vec3d.ofCenter(waterPlacementPos);
        
        // Calculate distance to placement target
        double distanceToTarget = playerPos.distanceTo(waterPlacementTarget);
        
        // Determine if we should place water based on DISTANCE
        boolean shouldPlace = MLGPlacementDecision.shouldPlaceWaterNow(distanceToTarget, distanceResult);
        
        String reason = MLGPlacementDecision.buildPlacementReason(shouldPlace, distanceToTarget, distanceResult);
        
        MLGMaster.LOGGER.info("🎯 DISTANCE-BASED PLACEMENT DECISION:");
        MLGMaster.LOGGER.info("  Distance to target: {:.3f} blocks", distanceToTarget);
        MLGMaster.LOGGER.info("  Placement threshold: {:.3f} blocks", distanceResult.getPlacementDistance());
        MLGMaster.LOGGER.info("  Estimated impact time: {} ticks", distanceResult.getEstimatedImpactTime());
        MLGMaster.LOGGER.info("  Is urgent: {}", distanceResult.isUrgent());
        MLGMaster.LOGGER.info("  Decision: {}", shouldPlace ? "PLACE NOW!" : "WAIT");
        MLGMaster.LOGGER.info("  Reason: {}", reason);
        
        return new MLGPredictionResult(shouldPlace, true, landingResult, highestBlock, 
            waterPlacementTarget, distanceToTarget, reason, safetyResult,
            distanceResult.getPlacementDistance(), distanceResult.isUrgent());
    }
    
    /**
     * Find the block with the highest Y coordinate from all hit blocks
     */
    public static BlockPos findHighestHitBlock(java.util.List<BlockPos> hitBlocks) {
        if (hitBlocks.isEmpty()) {
            MLGMaster.LOGGER.warn("⚠️ HIGHEST BLOCK SEARCH: No hit blocks provided");
            return null;
        }
        
        BlockPos highest = hitBlocks.get(0);
        int highestY = highest.getY();
        
        for (BlockPos block : hitBlocks) {
            if (block.getY() > highestY) {
                highestY = block.getY();
                highest = block;
            }
        }
        
        MLGMaster.LOGGER.info("🏆 HIGHEST BLOCK: {} (Y={}) from {} candidates", 
            highest, highestY, hitBlocks.size());
        
        return highest;
    }
}
