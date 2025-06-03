package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import name.mlgmaster.statemachine.*;

public class WaterMLGHandler {

    // Distance-based placement trigger
    private static final double PLACEMENT_REACH_DISTANCE = 4.0;
    private static final double MIN_FALL_DISTANCE = 3.0;

    // State machine instance
    private static final WaterMLGStateMachine stateMachine = new WaterMLGStateMachine();
    private static final MLGStatisticsLogger statisticsLogger = new MLGStatisticsLogger();

    // Initialize the state machine
    static {
        stateMachine.addStateChangeListener(statisticsLogger);
    }

    public static class MLGPredictionResult {
        private final boolean shouldPlace;
        private final String reason;
        private final BlockPos highestLandingBlock;
        private final Vec3d waterPlacementTarget;
        private final LandingPredictor.HitboxLandingResult landingResult;
        private final double distanceToTarget;
        private final Vec3d predictedLandingPosition;

        public MLGPredictionResult(boolean shouldPlace, String reason, BlockPos highestLandingBlock,
                Vec3d waterPlacementTarget, LandingPredictor.HitboxLandingResult landingResult,
                double distanceToTarget, Vec3d predictedLandingPosition) {
            this.shouldPlace = shouldPlace;
            this.reason = reason;
            this.highestLandingBlock = highestLandingBlock;
            this.waterPlacementTarget = waterPlacementTarget;
            this.landingResult = landingResult;
            this.distanceToTarget = distanceToTarget;
            this.predictedLandingPosition = predictedLandingPosition;
        }

        // Getters
        public boolean shouldPlace() {
            return shouldPlace;
        }

        public String getReason() {
            return reason;
        }

        public BlockPos getHighestLandingBlock() {
            return highestLandingBlock;
        }

        public Vec3d getWaterPlacementTarget() {
            return waterPlacementTarget;
        }

        public LandingPredictor.HitboxLandingResult getLandingResult() {
            return landingResult;
        }

        public double getDistanceToTarget() {
            return distanceToTarget;
        }

        public Vec3d getPredictedLandingPosition() {
            return predictedLandingPosition;
        }
    }

    /**
     * Main tick method - now delegates to state machine
     */
    public static void onHighFrequencyTick() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.player == null || client.world == null) {
            // State machine will handle cleanup if needed
            stateMachine.tick(client, null);
            return;
        }

        // Delegate to state machine
        stateMachine.tick(client, client.player);
    }

    /**
     * Alternative tick method with pre-fetched client and player
     */
    public static void onHighFrequencyTick(MinecraftClient client, ClientPlayerEntity player) {
        stateMachine.tick(client, player);
    }

    /**
     * Analyze fall and determine if/when water should be placed This method is now primarily used
     * by the state machine handlers
     */
    public static MLGPredictionResult analyzeFallAndPlacement(MinecraftClient client,
            ClientPlayerEntity player, Vec3d velocity) {
        MLGMaster.LOGGER.debug("=== WATER MLG ANALYSIS START ===");
        MLGMaster.LOGGER.debug("Player position: {}", player.getPos());
        MLGMaster.LOGGER.debug("Player velocity: {}", velocity);

        Vec3d currentPos = player.getPos();

        // Check if player is falling
        if (velocity.y >= 0) {
            MLGMaster.LOGGER.debug("Player is not falling (Y velocity: {}), no MLG needed",
                    velocity.y);
            return new MLGPredictionResult(false, "Player is not falling", null, null, null, 0.0,
                    null);
        }

        // Predict landing using hitbox-aware collision detection
        LandingPredictor.HitboxLandingResult landingResult =
                LandingPredictor.predictHitboxLanding(client, player, currentPos, velocity);

        if (landingResult == null) {
            MLGMaster.LOGGER.warn("Could not predict landing location");
            return new MLGPredictionResult(false, "Could not predict landing location", null, null,
                    null, 0.0, null);
        }

        Vec3d predictedLandingPosition = landingResult.getLandingPosition();
        BlockPos highestLandingBlock = landingResult.getPrimaryLandingBlock();

        double distanceToLanding = currentPos.distanceTo(predictedLandingPosition);
        double fallDistance = currentPos.y - predictedLandingPosition.y;

        MLGMaster.LOGGER.debug("Predicted landing at {} with fall distance {}",
                predictedLandingPosition, fallDistance);

        // Check if fall distance is significant enough
        if (fallDistance < MIN_FALL_DISTANCE) {
            return new MLGPredictionResult(false, "Fall distance too small for MLG",
                    highestLandingBlock, null, landingResult, distanceToLanding,
                    predictedLandingPosition);
        }

        // Check if landing is already safe
        if (landingResult.isSafeLanding()) {
            return new MLGPredictionResult(false, "Landing is already safe", highestLandingBlock,
                    null, landingResult, distanceToLanding, predictedLandingPosition);
        }

        // Calculate water placement target
        Vec3d waterPlacementTarget = landingResult.getLookTarget();

        // Verify we have water bucket
        if (!InventoryManager.hasWaterBucket(player)) {
            return new MLGPredictionResult(false, "No water bucket available", highestLandingBlock,
                    waterPlacementTarget, landingResult, distanceToLanding,
                    predictedLandingPosition);
        }

        MLGMaster.LOGGER.debug("Water placement approved for unsafe landing");
        return new MLGPredictionResult(true, "Unsafe landing detected, MLG required",
                highestLandingBlock, waterPlacementTarget, landingResult, distanceToLanding,
                predictedLandingPosition);
    }

    // Legacy API methods for backward compatibility and external access

    /**
     * Check if water placement should be triggered (legacy method)
     */
    public static boolean shouldTriggerWaterPlacement(MinecraftClient client,
            ClientPlayerEntity player) {
        return stateMachine.getCurrentState() == FallState.READY_TO_PLACE;
    }

    /**
     * Get current state information
     */
    public static String getStateInfo() {
        return stateMachine.getStateInfo();
    }

    /**
     * Get current fall state
     */
    public static FallState getCurrentState() {
        return stateMachine.getCurrentState();
    }

    /**
     * Check if currently tracking a fall
     */
    public static boolean isCurrentlyTrackingFall() {
        FallState state = stateMachine.getCurrentState();
        return state != FallState.IDLE && state != FallState.FALL_ENDED;
    }

    /**
     * Get distance to landing for current tracked fall
     */
    public static double getCurrentDistanceToLanding() {
        MLGPredictionResult prediction = stateMachine.getActivePrediction();
        if (prediction == null) {
            return Double.MAX_VALUE;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return Double.MAX_VALUE;
        }

        return client.player.getPos().distanceTo(prediction.getPredictedLandingPosition());
    }

    /**
     * Check if player is currently in a state that could require MLG
     */
    public static boolean isMLGCandidate(ClientPlayerEntity player) {
        Vec3d velocity = player.getVelocity();
        return velocity.y < -0.1; // Falling faster than just walking off a block
    }

    /**
     * Execute water placement if conditions are met (legacy method)
     */
    public static boolean handleWaterMLG(MinecraftClient client, ClientPlayerEntity player) {
        FallState currentState = stateMachine.getCurrentState();

        // Only attempt placement if we're in the ready state
        if (currentState == FallState.READY_TO_PLACE) {
            MLGMaster.LOGGER.info("Legacy handleWaterMLG called during READY_TO_PLACE state");
            return true; // State machine will handle the actual placement
        }

        return false; // Not in appropriate state
    }

    /**
     * Get the distance to predicted landing (legacy method)
     */
    public static double getDistanceToLanding(MinecraftClient client, ClientPlayerEntity player) {
        return getCurrentDistanceToLanding();
    }

    /**
     * Force stop fall tracking (for testing/debugging)
     */
    public static void forceStopTracking() {
        stateMachine.forceReset();
    }

    /**
     * Reset cooldown (useful for testing or manual triggers)
     */
    public static void resetCooldown() {
        stateMachine.setLastPlacementAttempt(0);
        MLGMaster.LOGGER.info("Placement cooldown reset");
    }

    /**
     * Add a state change listener
     */
    public static void addStateChangeListener(StateChangeListener listener) {
        stateMachine.addStateChangeListener(listener);
    }

    /**
     * Remove a state change listener
     */
    public static void removeStateChangeListener(StateChangeListener listener) {
        stateMachine.removeStateChangeListener(listener);
    }

    /**
     * Get the state machine instance (for advanced usage)
     */
    public static WaterMLGStateMachine getStateMachine() {
        return stateMachine;
    }

    /**
     * Get current active prediction (if any)
     */
    public static MLGPredictionResult getActivePrediction() {
        return stateMachine.getActivePrediction();
    }

    /**
     * Get detailed tracking state info (for debug/UI)
     */
    public static String getTrackingStateInfo() {
        FallState state = stateMachine.getCurrentState();

        if (state == FallState.IDLE) {
            return "Not tracking any fall";
        }

        StringBuilder info = new StringBuilder();
        info.append("State: ").append(state.getDescription());

        MLGPredictionResult prediction = stateMachine.getActivePrediction();
        if (prediction != null) {
            info.append(", needs_water=").append(prediction.shouldPlace());
            info.append(", attempted=").append(stateMachine.isWaterPlacementAttempted());
            info.append(", distance=").append(String.format("%.2f", getCurrentDistanceToLanding()));
            info.append(", threshold=").append(PLACEMENT_REACH_DISTANCE);
        }

        return info.toString();
    }
}


