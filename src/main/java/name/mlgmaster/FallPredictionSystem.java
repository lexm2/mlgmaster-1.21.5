package name.mlgmaster;

import name.mlgmaster.SafeLandingBlockChecker.SafetyResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Comprehensive fall prediction and MLG timing system with improved validation
 */
public class FallPredictionSystem {

    // MLG Configuration
    private static final int PLACEMENT_BUFFER_TICKS = 1;
    private static final int MAX_PLACEMENT_DISTANCE_BLOCKS = 5;
    private static final double MIN_DANGEROUS_FALL_DISTANCE = 3.0;
    private static final double MIN_FALL_VELOCITY = 0.1;

    /**
     * Complete MLG analysis with improved validation
     */
    public static MLGPredictionResult analyzeFallAndPlacement(MinecraftClient client,
            ClientPlayerEntity player, Vec3d velocity) {
        Vec3d playerPos = player.getPos();

        // Initial fall state validation
        FallStateValidation validation = validateFallState(player, velocity);
        if (!validation.isValid()) {
            return createFailResult(validation.getReason(), 0);
        }

        // Simulate player movement using improved physics
        MinecraftPhysics.MovementSimulationResult simulation = MinecraftPhysics.simulatePlayerMovement(client, player,
                playerPos, velocity);

        if (!simulation.hasCollision()) {
            return createFailResult("No landing predicted", 0);
        }

        // Validate collision results
        CollisionValidation collisionValidation = validateCollisionResults(simulation, playerPos);
        if (!collisionValidation.isValid()) {
            MLGMaster.LOGGER.warn("COLLISION VALIDATION FAILED: {}", collisionValidation.getReason());
            return createFailResult(collisionValidation.getReason(), 0);
        }

        // Create proper HitboxLandingResult with validation
        HitboxLandingResult landingResult = HitboxLandingResult.fromPhysicsSimulation(simulation, client, player,
                playerPos);

        // Find optimal landing block with additional validation
        BlockPos landingBlock = landingResult.getPrimaryLandingBlock();
        if (landingBlock == null) {
            return createFailResult("No suitable landing block", 0);
        }

        // Validate landing block position
        if (!validateLandingBlock(landingBlock, playerPos, simulation.getFinalPosition())) {
            MLGMaster.LOGGER.warn("INVALID LANDING BLOCK: Block {} is invalid for player at {} falling to {}",
                    landingBlock, playerPos, simulation.getFinalPosition());
            return createFailResult("Invalid landing block position", 0);
        }

        MLGHandler.handleCleanup(client, player);

        // Get safety result from landing result
        SafeLandingBlockChecker.SafetyResult safetyResult = landingResult.getSafetyResult();

        if (safetyResult.isSafe()) {
            return new MLGPredictionResult(false, true, landingResult, landingBlock, null, -1,
                    "Safe landing: " + safetyResult.getReason(), safetyResult, 0,
                    Items.WATER_BUCKET);
        }

        // Calculate precise placement timing
        PlacementAnalysis timing = calculatePlacementTiming(player, velocity, simulation, landingBlock);

        // Determine water placement position
        Vec3d waterPlacementTarget = landingResult.getLookTarget();
        double distanceToTarget = waterPlacementTarget != null ? playerPos.distanceTo(waterPlacementTarget)
                : Double.MAX_VALUE;

        // Validate placement distance
        if (distanceToTarget > MAX_PLACEMENT_DISTANCE_BLOCKS) {
            MLGMaster.LOGGER.info(
                    "TIMING: Target too far - Distance: {} blocks, Max: {} blocks",
                    distanceToTarget, MAX_PLACEMENT_DISTANCE_BLOCKS);
            return createFailResult(
                    "Target too far: " + String.format("%.1f", distanceToTarget) + " blocks",
                    distanceToTarget);
        }

        // Log comprehensive timing analysis
        logTimingAnalysis(timing, playerPos, landingBlock, velocity, simulation);

        // Make final placement decision
        boolean shouldPlace = timing.shouldPlaceNow();
        String reason = timing.getPlacementReason();

        return new MLGPredictionResult(shouldPlace, true, landingResult, landingBlock,
                waterPlacementTarget, distanceToTarget, reason, safetyResult,
                MAX_PLACEMENT_DISTANCE_BLOCKS, Items.WATER_BUCKET);
    }

    /**
     * Validate collision results to ensure they make physical sense
     */
    private static CollisionValidation validateCollisionResults(
            MinecraftPhysics.MovementSimulationResult simulation, Vec3d playerPos) {

        if (simulation.getCollidingBlocks().isEmpty()) {
            return new CollisionValidation(false, "No colliding blocks found");
        }

        BlockPos collisionBlock = simulation.getCollidingBlocks().get(0);
        Vec3d finalPos = simulation.getFinalPosition();

        // Check if collision block is significantly above the player's final position
        // Allow some tolerance for player height (1.8 blocks)
        if (collisionBlock.getY() > finalPos.y + 2.0) { // Increased tolerance
            return new CollisionValidation(false,
                    String.format("Collision block Y=%d is too far above player final Y=%.2f",
                            collisionBlock.getY(), finalPos.y));
        }

        // Check if collision block is too far below starting position
        double fallDistance = playerPos.y - (collisionBlock.getY() + 1.0);
        if (fallDistance < -1.0) { // Allow small tolerance for edge cases
            return new CollisionValidation(false,
                    String.format("Collision block Y=%d is above starting position Y=%.2f",
                            collisionBlock.getY(), playerPos.y));
        }

        // Check horizontal distance (player shouldn't teleport horizontally)
        double horizontalDistance = Math.sqrt(
                Math.pow(finalPos.x - playerPos.x, 2) +
                        Math.pow(finalPos.z - playerPos.z, 2));

        if (horizontalDistance > 10.0) { // Reasonable limit for horizontal drift
            return new CollisionValidation(false,
                    String.format("Excessive horizontal movement: %.2f blocks", horizontalDistance));
        }

        return new CollisionValidation(true, "Valid collision");
    }

    /**
     * Validate that the landing block makes sense given player position and
     * trajectory
     */
    private static boolean validateLandingBlock(BlockPos landingBlock, Vec3d playerPos, Vec3d finalPos) {
        // Block should be at or below player's current position
        if (landingBlock.getY() > playerPos.y) {
            MLGMaster.LOGGER.warn("Landing block {} is above player at Y={}", landingBlock, playerPos.y);
            return false;
        }

        // Block should be reasonably close to the final position
        double distanceToFinal = Math.sqrt(
                Math.pow(landingBlock.getX() + 0.5 - finalPos.x, 2) +
                        Math.pow(landingBlock.getZ() + 0.5 - finalPos.z, 2));

        if (distanceToFinal > 2.0) { // Allow some tolerance
            MLGMaster.LOGGER.warn("Landing block {} is too far from final position {}: {} blocks",
                    landingBlock, finalPos, distanceToFinal);
            return false;
        }

        // Final position should be close to the top of the landing block
        double expectedY = landingBlock.getY() + 1.0; // Top of block
        if (Math.abs(finalPos.y - expectedY) > 2.0) {
            MLGMaster.LOGGER.warn("Final Y position {} is too far from landing block top {}",
                    finalPos.y, expectedY);
            return false;
        }

        return true;
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
        MinecraftPhysics.FallingSpeedInfo speedInfo = MinecraftPhysics.analyzeFallingSpeed(player, velocity);

        boolean isHighSpeed = speedInfo.isNearTerminalVelocity();
        double remainingFallDistance = Math.max(0, playerPos.y);

        // Estimate time to ground using physics
        double estimatedTimeToGround = 0;
        if (isFalling && remainingFallDistance > 0) {
            estimatedTimeToGround = MinecraftPhysics.estimateTimeToFallDistance(remainingFallDistance, 100);
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
        MinecraftPhysics.MovementSimulationResult simulation = MinecraftPhysics.simulatePlayerMovement(client, player,
                playerPos, velocity);

        if (!simulation.hasCollision()) {
            return null; // No landing predicted
        }

        // Validate before creating result
        CollisionValidation validation = validateCollisionResults(simulation, playerPos);
        if (!validation.isValid()) {
            MLGMaster.LOGGER.warn("PREDICTION VALIDATION FAILED: {}", validation.getReason());
            return null;
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

        // More aggressive timing - place when we're very close to impact
        int optimalPlacementTick = Math.max(0, ticksToImpact - PLACEMENT_BUFFER_TICKS);

        MLGMaster.LOGGER.debug(
                "PHYSICS CALCULATION: Current height: {}, Ground height: {}, "
                        + "Ticks to impact: {}, Optimal placement tick: {}",
                currentHeight, groundHeight, ticksToImpact, optimalPlacementTick);

        return new PlacementAnalysis(ticksToImpact, optimalPlacementTick,
                currentHeight - groundHeight);
    }

    private static void logTimingAnalysis(PlacementAnalysis timing, Vec3d playerPos,
            BlockPos landingBlock, Vec3d velocity, MinecraftPhysics.MovementSimulationResult simulation) {
        double distanceToGround = playerPos.y - (landingBlock.getY() + 1.0);

        MLGMaster.LOGGER.info(
                "TIMING ANALYSIS: Ticks to impact: {}, Optimal placement tick: {}, "
                        + "Distance to ground: {} blocks, Final position: {}",
                timing.getTicksToImpact(), timing.getOptimalPlacementTick(),
                distanceToGround, simulation.getFinalPosition());

        if (timing.shouldPlaceNow()) {
            MLGMaster.LOGGER.info(
                    "PLACEMENT DECISION: PLACING NOW - Reason: {}, Fall speed: {} b/t",
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

    private static class CollisionValidation {
        private final boolean valid;
        private final String reason;

        public CollisionValidation(boolean valid, String reason) {
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
            if (ticksToImpact <= 0) {
                return "Critical timing - impact imminent";
            } else if (ticksToImpact <= optimalPlacementTick) {
                return String.format("Optimal timing - %d ticks to impact", ticksToImpact);
            } else {
                return String.format("Wait - %d ticks until optimal placement",
                        ticksToImpact - optimalPlacementTick);
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
