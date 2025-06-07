package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class PlacementTimingCalculator {
    private static final int PLACEMENT_BUFFER_TICKS = 3; // Place 3 ticks before impact
    private static final int MAX_PLACEMENT_DISTANCE_BLOCKS = 5;

    public static MLGPredictionResult analyzeFallAndPlacement(MinecraftClient client,
            ClientPlayerEntity player, Vec3d velocity) {
        Vec3d playerPos = player.getPos();

        // Basic fall validation
        if (velocity.y >= -0.1) {
            return createFailResult("Not falling", 0);
        }

        if (player.fallDistance < 3.0) {
            return createFailResult("Insufficient fall distance", 0);
        }

        if (player.isOnGround()) {
            return createFailResult("On ground", 0);
        }

        // Predict landing with precise timing
        LandingPredictor.HitboxLandingResult landingResult =
                LandingPredictor.predictHitboxLanding(client, player, playerPos, velocity);

        if (landingResult == null) {
            return createFailResult("No landing predicted", 0);
        }

        BlockPos highestBlock = findHighestHitBlock(landingResult.getAllHitBlocks());
        if (highestBlock == null) {
            return createFailResult("No highest block found", 0);
        }

        // Check landing safety
        SafeLandingBlockChecker.SafetyResult safetyResult =
                SafeLandingBlockChecker.checkLandingSafety(client, player, highestBlock, playerPos);

        if (safetyResult.isSafe()) {
            return new MLGPredictionResult(false, true, landingResult, highestBlock, null, -1,
                    "Safe landing: " + safetyResult.getReason(), safetyResult, 0,
                    Items.WATER_BUCKET);
        }

        // Calculate EXACT placement timing using MinecraftFallingPhysics
        PlacementTiming timing = calculateOptimalPlacementTiming(player, velocity, highestBlock);
        
        BlockPos waterPlacementPos = highestBlock.up();
        Vec3d waterPlacementTarget = Vec3d.ofCenter(waterPlacementPos);
        double distanceToTarget = playerPos.distanceTo(waterPlacementTarget);
        
        // Calculate distance to ground for logging
        double distanceToGround = playerPos.y - (highestBlock.getY() + 1.0); // +1.0 for player height
        
        // Check if target is reachable
        if (distanceToTarget > MAX_PLACEMENT_DISTANCE_BLOCKS) {
            MLGMaster.LOGGER.info("TIMING: Target too far - Distance: {} blocks, Max: {} blocks", 
                distanceToTarget, MAX_PLACEMENT_DISTANCE_BLOCKS);
            return createFailResult("Target too far: " + String.format("%.1f", distanceToTarget) + " blocks", distanceToTarget);
        }

        // Log timing analysis
        MLGMaster.LOGGER.info("TIMING ANALYSIS: Ticks to impact: {}, Optimal placement tick: {}, Distance to ground: {} blocks", 
            timing.ticksToImpact, timing.optimalPlacementTick, distanceToGround);

        // Decide based on EXACT timing, not distance
        boolean shouldPlace = timing.shouldPlaceNow();
        String reason = timing.getPlacementReason();

        // Log placement decision
        if (shouldPlace) {
            MLGMaster.LOGGER.info("PLACEMENT DECISION: PLACING NOW - Reason: {}, Distance to ground: {} blocks, Fall speed: {} b/t", 
                reason, distanceToGround, Math.abs(velocity.y));
        } else {
            MLGMaster.LOGGER.info("PLACEMENT DECISION: WAITING - Reason: {}, Distance to ground: {} blocks", 
                reason, distanceToGround);
        }

        return new MLGPredictionResult(shouldPlace, true, landingResult, highestBlock,
                waterPlacementTarget, distanceToTarget, reason, safetyResult, distanceToTarget,
                Items.WATER_BUCKET);
    }

    private static PlacementTiming calculateOptimalPlacementTiming(ClientPlayerEntity player, 
                                                                 Vec3d velocity, 
                                                                 BlockPos targetBlock) {
        
        double currentHeight = player.getPos().y;
        double groundHeight = targetBlock.getY() + 1.0; // Account for player height
        double currentVelocity = velocity.y;
        
        MLGMaster.LOGGER.info("PHYSICS CALCULATION: Current height: {}, Ground height: {}, Current velocity: {}", 
            currentHeight, groundHeight, currentVelocity);
        
        // Use MinecraftFallingPhysics for accurate timing
        double ticksToImpact = MinecraftFallingPhysics.predictLandingTime(
            currentHeight, groundHeight, currentVelocity
        );
        
        // Calculate when we should place (before impact)
        double optimalPlacementTick = ticksToImpact - PLACEMENT_BUFFER_TICKS;
        
        // Ensure we don't place too early (minimum 1 tick from now)
        optimalPlacementTick = Math.max(1.0, optimalPlacementTick);
        
        return new PlacementTiming(ticksToImpact, optimalPlacementTick);
    }

    private static MLGPredictionResult createFailResult(String reason, double distance) {
        return new MLGPredictionResult(false, false, null, null, null, -1, reason,
                new SafeLandingBlockChecker.SafetyResult(false, reason), distance,
                Items.WATER_BUCKET);
    }

    public static BlockPos findHighestHitBlock(java.util.List<BlockPos> hitBlocks) {
        if (hitBlocks.isEmpty()) {
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

        return highest;
    }

    // Enhanced PlacementTiming class with logging
    private static class PlacementTiming {
        private final double ticksToImpact;
        private final double optimalPlacementTick;
        
        public PlacementTiming(double ticksToImpact, double optimalPlacementTick) {
            this.ticksToImpact = ticksToImpact;
            this.optimalPlacementTick = optimalPlacementTick;
        }
        
        public boolean shouldPlaceNow() {
            boolean shouldPlace = ticksToImpact <= optimalPlacementTick;
            
            if (shouldPlace) {
                MLGMaster.LOGGER.info("PLACEMENT TRIGGER: Should place NOW - Ticks to impact: {}, Threshold: {}", 
                    ticksToImpact, optimalPlacementTick);
            } else {
                MLGMaster.LOGGER.debug("PLACEMENT CHECK: Not yet - Ticks to impact: {}, Need: {} (wait {} more ticks)", 
                    ticksToImpact, optimalPlacementTick, optimalPlacementTick - ticksToImpact);
            }
            
            return shouldPlace;
        }
        
        public String getPlacementReason() {
            if (ticksToImpact <= PLACEMENT_BUFFER_TICKS) {
                return String.format("Within buffer ticks - %.1f ticks to impact", ticksToImpact);
            } else if (ticksToImpact <= optimalPlacementTick) {
                return String.format("Optimal timing - placing %.1f ticks before impact", 
                    ticksToImpact - optimalPlacementTick + PLACEMENT_BUFFER_TICKS);
            } else {
                return String.format("Wait - %.1f ticks until optimal placement", 
                    optimalPlacementTick - ticksToImpact);
            }
        }
    }

    // Keep the FallAnalysisResult class but use MinecraftFallingPhysics
    public static FallAnalysisResult analyzeCurrentFall(ClientPlayerEntity player, Vec3d velocity) {
        Vec3d playerPos = player.getPos();
        double fallSpeed = Math.abs(velocity.y);
        boolean isFalling = velocity.y < -0.1 && !player.isOnGround();
        boolean isDangerous = player.fallDistance >= 3.0;
        
        // Use MinecraftFallingPhysics for accurate analysis
        MinecraftFallingPhysics.FallingSpeedInfo speedInfo = 
            MinecraftFallingPhysics.analyzeFallingSpeed(player, velocity);
        
        boolean isHighSpeed = speedInfo.isNearTerminalVelocity();
        double remainingFallDistance = Math.max(0, playerPos.y);
        
        // Estimate time to ground using physics
        double estimatedTimeToGround = 0;
        if (isFalling && remainingFallDistance > 0) {
            estimatedTimeToGround = MinecraftFallingPhysics.estimateTimeToFallDistance(
                remainingFallDistance, 100
            );
        }

        return new FallAnalysisResult(isFalling, isDangerous, isHighSpeed, fallSpeed,
                remainingFallDistance, estimatedTimeToGround, playerPos.y);
    }

    public static class FallAnalysisResult {
        private final boolean isFalling;
        private final boolean isDangerous;
        private final boolean isHighSpeed;
        private final double fallSpeed;
        private final double remainingFallDistance;
        private final double estimatedTimeToGround;
        private final double currentHeight;

        public FallAnalysisResult(boolean isFalling, boolean isDangerous, boolean isHighSpeed,
                double fallSpeed, double remainingFallDistance, double estimatedTimeToGround,
                double currentHeight) {
            this.isFalling = isFalling;
            this.isDangerous = isDangerous;
            this.isHighSpeed = isHighSpeed;
            this.fallSpeed = fallSpeed;
            this.remainingFallDistance = remainingFallDistance;
            this.estimatedTimeToGround = estimatedTimeToGround;
            this.currentHeight = currentHeight;
        }

        // Getters
        public boolean isFalling() { return isFalling; }
        public boolean isDangerous() { return isDangerous; }
        public boolean isHighSpeed() { return isHighSpeed; }
        public double getFallSpeed() { return fallSpeed; }
        public double getRemainingFallDistance() { return remainingFallDistance; }
        public double getEstimatedTimeToGround() { return estimatedTimeToGround; }
        public double getCurrentHeight() { return currentHeight; }

        @Override
        public String toString() {
            return String.format(
                    "FallAnalysis[falling=%s, dangerous=%s, highSpeed=%s, height=%.1f, timeToGround=%.1f]",
                    isFalling, isDangerous, isHighSpeed, currentHeight, estimatedTimeToGround);
        }
    }
}
