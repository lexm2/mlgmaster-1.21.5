package name.mlgmaster;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class MLGAnalyzer {
    private static final double BASE_PLACEMENT_DISTANCE = 5.0;
    private static final double MAX_PLACEMENT_DISTANCE = 15.0;
    private static final double VELOCITY_MULTIPLIER = 8.0;
    private static final double TERMINAL_VELOCITY = 3.92;

    public static MLGPredictionResult analyzeFallAndPlacement(MinecraftClient client,
            ClientPlayerEntity player, Vec3d velocity) {
        Vec3d playerPos = player.getPos();

        // Basic fall validation
        if (velocity.y >= -0.1) {
            return createFailResult("Not falling", 0, false);
        }

        if (player.fallDistance < 3.0) {
            return createFailResult("Insufficient fall distance", 0, false);
        }

        if (player.isOnGround()) {
            return createFailResult("On ground", 0, false);
        }

        // Calculate placement parameters
        double fallSpeed = Math.abs(velocity.y);
        double placementDistance = calculatePlacementDistance(fallSpeed);
        boolean isUrgent = isUrgentPlacement(player, velocity);
        int estimatedImpactTime = estimateImpactTime(client, player, velocity);

        // Predict landing
        LandingPredictor.HitboxLandingResult landingResult =
                LandingPredictor.predictHitboxLanding(client, player, playerPos, velocity);

        if (landingResult == null) {
            return createFailResult("No landing predicted", placementDistance, isUrgent);
        }

        BlockPos highestBlock = findHighestHitBlock(landingResult.getAllHitBlocks());
        if (highestBlock == null) {
            return createFailResult("No highest block found", placementDistance, isUrgent);
        }

        // Check landing safety
        SafeLandingBlockChecker.SafetyResult safetyResult =
                SafeLandingBlockChecker.checkLandingSafety(client, player, highestBlock, playerPos);

        if (safetyResult.isSafe()) {
            return new MLGPredictionResult(false, true, landingResult, highestBlock, null, -1,
                    "Safe landing: " + safetyResult.getReason(), safetyResult, placementDistance,
                    isUrgent);
        }

        // Calculate water placement
        BlockPos waterPlacementPos = highestBlock.up();
        Vec3d waterPlacementTarget = Vec3d.ofCenter(waterPlacementPos);
        double distanceToTarget = playerPos.distanceTo(waterPlacementTarget);

        // Decide if we should place water
        boolean shouldPlace = shouldPlaceWaterNow(distanceToTarget, placementDistance,
                estimatedImpactTime, isUrgent);
        String reason = buildPlacementReason(shouldPlace, distanceToTarget, placementDistance,
                estimatedImpactTime);

        return new MLGPredictionResult(shouldPlace, true, landingResult, highestBlock,
                waterPlacementTarget, distanceToTarget, reason, safetyResult, placementDistance,
                isUrgent);
    }

    private static double calculatePlacementDistance(double fallSpeed) {
        double velocityFactor = Math.min(fallSpeed * VELOCITY_MULTIPLIER,
                MAX_PLACEMENT_DISTANCE - BASE_PLACEMENT_DISTANCE);
        double dynamicDistance = BASE_PLACEMENT_DISTANCE + velocityFactor;
        return Math.clamp(dynamicDistance, BASE_PLACEMENT_DISTANCE, MAX_PLACEMENT_DISTANCE);
    }

    private static boolean isUrgentPlacement(ClientPlayerEntity player, Vec3d velocity) {
        double fallSpeed = Math.abs(velocity.y);
        double terminalPercent = (fallSpeed / TERMINAL_VELOCITY) * 100;
        return terminalPercent > 80 || player.fallDistance > 20;
    }

    private static int estimateImpactTime(MinecraftClient client, ClientPlayerEntity player,
            Vec3d velocity) {
        Vec3d testPos = player.getPos();
        Vec3d testVel = velocity;

        for (int tick = 0; tick < 100; tick++) {
            testVel = testVel.add(0, -0.08, 0);
            testVel = testVel.multiply(0.98);
            testPos = testPos.add(testVel);

            BlockPos blockBelow = BlockPos.ofFloored(testPos);
            if (isBlockSolid(client, blockBelow)) {
                return tick;
            }

            if (testPos.y < player.getPos().y - 50) {
                break;
            }
        }

        return 50;
    }

    /**
     * Check if a block is solid (not air)
     */
    public static boolean isBlockSolid(MinecraftClient client, BlockPos pos) {
        if (client.world == null) {
            MLGMaster.LOGGER.warn("BLOCK CHECK: World is null for position {}", pos);
            return false;
        }

        boolean isSolid = !client.world.getBlockState(pos).getBlock().equals(Blocks.AIR);
        String blockType = client.world.getBlockState(pos).getBlock().toString();

        // Only log interesting blocks (solid ones or occasionally air)
        if (isSolid) {
            MLGMaster.LOGGER.info("BLOCK: {} = {} ({})", pos, "SOLID", blockType);
        }

        return isSolid;
    }

    private static boolean shouldPlaceWaterNow(double distanceToTarget, double placementDistance,
            int estimatedImpactTime, boolean isUrgent) {
        if (estimatedImpactTime <= 10) {
            return true;
        }

        if (isUrgent && estimatedImpactTime <= 20) {
            return true;
        }

        return distanceToTarget <= placementDistance;
    }

    private static String buildPlacementReason(boolean shouldPlace, double distanceToTarget,
            double placementDistance, int estimatedImpactTime) {
        if (shouldPlace) {
            if (estimatedImpactTime <= 10) {
                return "Emergency placement - " + estimatedImpactTime + " ticks left";
            } else if (distanceToTarget <= placementDistance) {
                return "Within placement distance (" + distanceToTarget + "/"
                        + placementDistance + ")";
            } else {
                return "Urgent placement needed";
            }
        } else {
            return "Wait - " + estimatedImpactTime + " ticks remaining";
        }
    }

    private static MLGPredictionResult createFailResult(String reason, double placementDistance,
            boolean isUrgent) {
        return new MLGPredictionResult(false, false, null, null, null, -1, reason,
                new SafeLandingBlockChecker.SafetyResult(false, reason), placementDistance,
                isUrgent);
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

    public static FallAnalysisResult analyzeCurrentFall(ClientPlayerEntity player, Vec3d velocity) {
        Vec3d playerPos = player.getPos();
        double fallSpeed = Math.abs(velocity.y);
        boolean isFalling = velocity.y < -0.1 && !player.isOnGround();
        boolean isDangerous = player.fallDistance >= 3.0;
        boolean isHighSpeed = (fallSpeed / TERMINAL_VELOCITY) > 0.9;

        double remainingFallDistance = Math.max(0, playerPos.y);
        double estimatedTimeToGround = 0;

        if (isFalling && remainingFallDistance > 0 && fallSpeed > 0) {
            estimatedTimeToGround = remainingFallDistance / fallSpeed;
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

        public boolean isFalling() {
            return isFalling;
        }

        public boolean isDangerous() {
            return isDangerous;
        }

        public boolean isHighSpeed() {
            return isHighSpeed;
        }

        public double getFallSpeed() {
            return fallSpeed;
        }

        public double getRemainingFallDistance() {
            return remainingFallDistance;
        }

        public double getEstimatedTimeToGround() {
            return estimatedTimeToGround;
        }

        public double getCurrentHeight() {
            return currentHeight;
        }

        @Override
        public String toString() {
            return String.format(
                    "FallAnalysis[falling=%s, dangerous=%s, highSpeed=%s, height=%.1f, timeToGround=%.1f]",
                    isFalling, isDangerous, isHighSpeed, currentHeight, estimatedTimeToGround);
        }
    }
}
