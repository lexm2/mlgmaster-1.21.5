package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.List;

/**
 * Comprehensive fall prediction and MLG timing system Uses MinecraftPhysics for accurate
 * calculations
 */
public class FallPredictionSystem {

    // MLG Configuration
    private static final int PLACEMENT_BUFFER_TICKS = 3; // Place 3 ticks before impact
    private static final int MAX_PLACEMENT_DISTANCE_BLOCKS = 5;
    private static final double MIN_DANGEROUS_FALL_DISTANCE = 3.0;
    private static final double MIN_FALL_VELOCITY = 0.1;

    /**
     * Complete MLG analysis - determines if and when to place water bucket
     */
    public static MLGPredictionResult analyzeFallAndPlacement(MinecraftClient client,
            ClientPlayerEntity player, Vec3d velocity) {
        Vec3d playerPos = player.getPos();

        // Initial fall state validation
        FallStateValidation validation = validateFallState(player, velocity);
        if (!validation.isValid()) {
            return createFailResult(validation.getReason(), 0);
        }

        // Simulate player movement using accurate physics
        MinecraftPhysics.MovementSimulationResult simulation =
                MinecraftPhysics.simulatePlayerMovement(client, player, playerPos, velocity);

        if (!simulation.hasCollision()) {
            return createFailResult("No landing predicted", 0);
        }

        // Create proper HitboxLandingResult
        HitboxLandingResult landingResult =
                HitboxLandingResult.fromPhysicsSimulation(simulation, client, player, playerPos);

        // Find optimal landing block
        BlockPos landingBlock = landingResult.getPrimaryLandingBlock();
        if (landingBlock == null) {
            return createFailResult("No suitable landing block", 0);
        }

        // Get safety result from landing result
        SafeLandingBlockChecker.SafetyResult safetyResult = landingResult.getSafetyResult();

        if (safetyResult.isSafe()) {
            return new MLGPredictionResult(false, true, landingResult, landingBlock, null, -1,
                    "Safe landing: " + safetyResult.getReason(), safetyResult, 0,
                    Items.WATER_BUCKET);
        }

        // Calculate precise placement timing
        PlacementAnalysis timing =
                calculatePlacementTiming(player, velocity, simulation, landingBlock);

        // Determine water placement position
        Vec3d waterPlacementTarget = landingResult.getLookTarget();
        double distanceToTarget =
                waterPlacementTarget != null ? playerPos.distanceTo(waterPlacementTarget)
                        : Double.MAX_VALUE;

        // Validate placement distance
        if (distanceToTarget > MAX_PLACEMENT_DISTANCE_BLOCKS) {
            MLGMaster.LOGGER.info(
                    "TIMING: Target too far - Distance: {:.1f} blocks, Max: {} blocks",
                    distanceToTarget, MAX_PLACEMENT_DISTANCE_BLOCKS);
            return createFailResult(
                    "Target too far: " + String.format("%.1f", distanceToTarget) + " blocks",
                    distanceToTarget);
        }

        // Log comprehensive timing analysis
        logTimingAnalysis(timing, playerPos, landingBlock, velocity);

        // Make final placement decision
        boolean shouldPlace = timing.shouldPlaceNow();
        String reason = timing.getPlacementReason();

        return new MLGPredictionResult(shouldPlace, true, landingResult, landingBlock,
                waterPlacementTarget, distanceToTarget, reason, safetyResult,
                MAX_PLACEMENT_DISTANCE_BLOCKS, Items.WATER_BUCKET);
    }

    /**
     * Analyze current fall state for UI display
     */
    public static FallAnalysisResult analyzeCurrentFall(ClientPlayerEntity player, Vec3d velocity) {
        Vec3d playerPos = player.getPos();
        double fallSpeed = Math.abs(velocity.y);
        boolean isFalling = velocity.y < -MIN_FALL_VELOCITY && !player.isOnGround();
        boolean isDangerous = player.fallDistance >= MIN_DANGEROUS_FALL_DISTANCE;

        // Use physics engine for detailed analysis
        MinecraftPhysics.FallingSpeedInfo speedInfo =
                MinecraftPhysics.analyzeFallingSpeed(player, velocity);

        boolean isHighSpeed = speedInfo.isNearTerminalVelocity();
        double remainingFallDistance = Math.max(0, playerPos.y);

        // Estimate time to ground using physics
        double estimatedTimeToGround = 0;
        if (isFalling && remainingFallDistance > 0) {
            estimatedTimeToGround =
                    MinecraftPhysics.estimateTimeToFallDistance(remainingFallDistance, 100);
        }

        return new FallAnalysisResult(isFalling, isDangerous, isHighSpeed, fallSpeed,
                remainingFallDistance, estimatedTimeToGround, playerPos.y, speedInfo);
    }

    /**
     * Predict exact landing position and timing using physics simulation
     */
    public static HitboxLandingResult predictLanding(MinecraftClient client,
            ClientPlayerEntity player) {
        Vec3d playerPos = player.getPos();
        Vec3d velocity = player.getVelocity();

        // Run full physics simulation
        MinecraftPhysics.MovementSimulationResult simulation =
                MinecraftPhysics.simulatePlayerMovement(client, player, playerPos, velocity);

        if (!simulation.hasCollision()) {
            return null; // No landing predicted
        }

        return HitboxLandingResult.fromPhysicsSimulation(simulation, client, player, playerPos);
    }

    // Helper Methods

    private static FallStateValidation validateFallState(ClientPlayerEntity player,
            Vec3d velocity) {
        if (velocity.y >= -MIN_FALL_VELOCITY) {
            return new FallStateValidation(false, "Not falling");
        }

        if (player.fallDistance < MIN_DANGEROUS_FALL_DISTANCE) {
            return new FallStateValidation(false, "Insufficient fall distance");
        }

        if (player.isOnGround()) {
            return new FallStateValidation(false, "On ground");
        }

        return new FallStateValidation(true, "Valid fall state");
    }

    private static PlacementAnalysis calculatePlacementTiming(ClientPlayerEntity player,
            Vec3d velocity, MinecraftPhysics.MovementSimulationResult simulation,
            BlockPos targetBlock) {

        double currentHeight = player.getPos().y;
        double groundHeight = targetBlock.getY() + 1.0; // Account for player height
        int ticksToImpact = simulation.getSimulationTicks();

        // Calculate optimal placement timing
        int optimalPlacementTick = Math.max(1, ticksToImpact - PLACEMENT_BUFFER_TICKS);

        MLGMaster.LOGGER.debug(
                "PHYSICS CALCULATION: Current height: {:.2f}, Ground height: {:.2f}, "
                        + "Ticks to impact: {}, Optimal placement tick: {}",
                currentHeight, groundHeight, ticksToImpact, optimalPlacementTick);

        return new PlacementAnalysis(ticksToImpact, optimalPlacementTick,
                currentHeight - groundHeight);
    }

    private static void logTimingAnalysis(PlacementAnalysis timing, Vec3d playerPos,
            BlockPos landingBlock, Vec3d velocity) {
        double distanceToGround = playerPos.y - (landingBlock.getY() + 1.0);

        MLGMaster.LOGGER.info(
                "TIMING ANALYSIS: Ticks to impact: {}, Optimal placement tick: {}, "
                        + "Distance to ground: {:.2f} blocks",
                timing.getTicksToImpact(), timing.getOptimalPlacementTick(), distanceToGround);

        if (timing.shouldPlaceNow()) {
            MLGMaster.LOGGER.info(
                    "PLACEMENT DECISION: PLACING NOW - Reason: {}, Fall speed: {:.3f} b/t",
                    timing.getPlacementReason(), Math.abs(velocity.y));
        } else {
            MLGMaster.LOGGER.debug("PLACEMENT DECISION: WAITING - Reason: {}",
                    timing.getPlacementReason());
        }
    }

    private static MLGPredictionResult createFailResult(String reason, double distance) {
        return new MLGPredictionResult(false, false, null, null, null, -1, reason,
                new SafeLandingBlockChecker.SafetyResult(false, reason), distance,
                Items.WATER_BUCKET);
    }

    // Data Classes

    private static class FallStateValidation {
        private final boolean valid;
        private final String reason;

        public FallStateValidation(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }

        public boolean isValid() {
            return valid;
        }

        public String getReason() {
            return reason;
        }
    }

    private static class PlacementAnalysis {
        private final int ticksToImpact;
        private final int optimalPlacementTick;
        private final double distanceToGround;

        public PlacementAnalysis(int ticksToImpact, int optimalPlacementTick,
                double distanceToGround) {
            this.ticksToImpact = ticksToImpact;
            this.optimalPlacementTick = optimalPlacementTick;
            this.distanceToGround = distanceToGround;
        }

        public boolean shouldPlaceNow() {
            boolean shouldPlace = ticksToImpact <= optimalPlacementTick;

            if (shouldPlace) {
                MLGMaster.LOGGER.info(
                        "PLACEMENT TRIGGER: Should place NOW - Ticks to impact: {}, Threshold: {}",
                        ticksToImpact, optimalPlacementTick);
            }

            return shouldPlace;
        }

        public String getPlacementReason() {
            if (ticksToImpact <= PLACEMENT_BUFFER_TICKS) {
                return String.format("Critical timing - %d ticks to impact", ticksToImpact);
            } else if (ticksToImpact <= optimalPlacementTick) {
                return String.format("Optimal timing - placing %d ticks before impact",
                        ticksToImpact - optimalPlacementTick + PLACEMENT_BUFFER_TICKS);
            } else {
                return String.format("Wait - %d ticks until optimal placement",
                        optimalPlacementTick - ticksToImpact);
            }
        }

        public int getTicksToImpact() {
            return ticksToImpact;
        }

        public int getOptimalPlacementTick() {
            return optimalPlacementTick;
        }

        public double getDistanceToGround() {
            return distanceToGround;
        }
    }

    public static class FallAnalysisResult {
        private final boolean isFalling;
        private final boolean isDangerous;
        private final boolean isHighSpeed;
        private final double fallSpeed;
        private final double remainingFallDistance;
        private final double estimatedTimeToGround;
        private final double currentHeight;
        private final MinecraftPhysics.FallingSpeedInfo physicsInfo;

        public FallAnalysisResult(boolean isFalling, boolean isDangerous, boolean isHighSpeed,
                double fallSpeed, double remainingFallDistance, double estimatedTimeToGround,
                double currentHeight, MinecraftPhysics.FallingSpeedInfo physicsInfo) {
            this.isFalling = isFalling;
            this.isDangerous = isDangerous;
            this.isHighSpeed = isHighSpeed;
            this.fallSpeed = fallSpeed;
            this.remainingFallDistance = remainingFallDistance;
            this.estimatedTimeToGround = estimatedTimeToGround;
            this.currentHeight = currentHeight;
            this.physicsInfo = physicsInfo;
        }

        // Getters
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

        public MinecraftPhysics.FallingSpeedInfo getPhysicsInfo() {
            return physicsInfo;
        }

        @Override
        public String toString() {
            return String.format(
                    "FallAnalysis[falling=%s, dangerous=%s, highSpeed=%s, height=%.1f, timeToGround=%.1f, physics=%s]",
                    isFalling, isDangerous, isHighSpeed, currentHeight, estimatedTimeToGround,
                    physicsInfo);
        }
    }

    public static class LandingPrediction {
        private final boolean willLand;
        private final BlockPos landingBlock;
        private final Vec3d landingPosition;
        private final int ticksToLanding;
        private final Vec3d horizontalDisplacement;

        public LandingPrediction(boolean willLand, BlockPos landingBlock, Vec3d landingPosition,
                int ticksToLanding, Vec3d horizontalDisplacement) {
            this.willLand = willLand;
            this.landingBlock = landingBlock;
            this.landingPosition = landingPosition;
            this.ticksToLanding = ticksToLanding;
            this.horizontalDisplacement = horizontalDisplacement;
        }

        // Getters
        public boolean willLand() {
            return willLand;
        }

        public BlockPos getLandingBlock() {
            return landingBlock;
        }

        public Vec3d getLandingPosition() {
            return landingPosition;
        }

        public int getTicksToLanding() {
            return ticksToLanding;
        }

        public Vec3d getHorizontalDisplacement() {
            return horizontalDisplacement;
        }

        @Override
        public String toString() {
            if (!willLand) {
                return "LandingPrediction[no landing predicted]";
            }
            return String.format(
                    "LandingPrediction[block=%s, position=%s, ticks=%d, displacement=%s]",
                    landingBlock, landingPosition, ticksToLanding, horizontalDisplacement);
        }
    }
}

