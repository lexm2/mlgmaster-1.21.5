package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;

public class WaterMLGHandler {

    // Distance-based placement trigger
    private static final double PLACEMENT_REACH_DISTANCE = 4.0; // Place water when within 4 blocks
                                                                // of target (non-inclusive)
    private static final double MIN_FALL_DISTANCE = 3.0; // Minimum fall distance to consider MLG
    private static final double FALL_START_VELOCITY_THRESHOLD = -0.2; // Y velocity to consider
                                                                      // "start of significant fall"

    // State tracking for current fall
    private static boolean isTrackingFall = false;
    private static MLGPredictionResult activeFallPrediction = null;
    private static long fallStartTime = 0;
    private static Vec3d fallStartPosition = null;
    private static boolean waterPlacementAttempted = false;

    // Prevent spam placement attempts
    private static long lastPlacementAttempt = 0;
    private static final long PLACEMENT_COOLDOWN_MS = 500;

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
     * Called every client tick - accepts client and player to avoid repeated lookups
     */
    public static void onHighFrequencyTick(MinecraftClient client, ClientPlayerEntity player) {
        // Safety checks
        if (client == null || player == null || client.world == null) {
            return;
        }

        Vec3d currentVelocity = player.getVelocity();
        Vec3d currentPosition = player.getPos();

        // Check if we should start tracking a new fall
        if (!isTrackingFall && shouldStartTrackingFall(currentVelocity, currentPosition)) {
            startFallTracking(client, player, currentVelocity, currentPosition);
        }

        // If we're tracking a fall, check if we should place water or stop tracking
        if (isTrackingFall) {
            handleActiveFall(client, player, currentVelocity, currentPosition);
        }
    }

    /**
     * Main tick method called from onInitialize - now more efficient
     */
    public static void onHighFrequencyTick() {
        MinecraftClient client = MinecraftClient.getInstance();

        // Cache client and player references
        if (client == null || client.player == null || client.world == null) {
            // If we were tracking a fall but client is invalid, stop tracking
            if (isTrackingFall) {
                stopFallTracking("Client or player became null");
            }
            return;
        }

        // Pass cached references to avoid repeated lookups
        onHighFrequencyTick(client, client.player);
    }

    /**
     * Determine if we should start tracking a new fall
     */
    private static boolean shouldStartTrackingFall(Vec3d velocity, Vec3d position) {
        // Must be falling with significant velocity
        if (velocity.y >= FALL_START_VELOCITY_THRESHOLD) {
            return false;
        }

        // Don't start new tracking too soon after last attempt
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPlacementAttempt < PLACEMENT_COOLDOWN_MS) {
            return false;
        }

        MLGMaster.LOGGER.info("Should start tracking fall: velocity.y = {}, threshold = {}",
                velocity.y, FALL_START_VELOCITY_THRESHOLD);
        return true;
    }

    /**
     * Start tracking a new fall - calculate landing ONCE and store it
     */
    private static void startFallTracking(MinecraftClient client, ClientPlayerEntity player,
            Vec3d velocity, Vec3d position) {
        MLGMaster.LOGGER.info("=== STARTING FALL TRACKING ===");
        MLGMaster.LOGGER.info("Fall start position: {}", position);
        MLGMaster.LOGGER.info("Fall start velocity: {}", velocity);

        // Calculate landing prediction ONCE
        activeFallPrediction = analyzeFallAndPlacement(client, player, velocity);

        if (activeFallPrediction == null) {
            MLGMaster.LOGGER.warn("Could not analyze fall, not tracking");
            return;
        }

        // Store fall state
        isTrackingFall = true;
        fallStartTime = System.currentTimeMillis();
        fallStartPosition = position;
        waterPlacementAttempted = false;

        MLGMaster.LOGGER.info("Fall tracking started:");
        MLGMaster.LOGGER.info("  Predicted landing: {}",
                activeFallPrediction.getPredictedLandingPosition());
        MLGMaster.LOGGER.info("  Landing block: {}", activeFallPrediction.getHighestLandingBlock());
        MLGMaster.LOGGER.info("  Should place water: {}", activeFallPrediction.shouldPlace());
        MLGMaster.LOGGER.info("  Reason: {}", activeFallPrediction.getReason());

        if (!activeFallPrediction.shouldPlace()) {
            MLGMaster.LOGGER.info("Fall doesn't require water placement: {}",
                    activeFallPrediction.getReason());
            // Continue tracking in case conditions change, but mark as no action needed
        }
    }

    /**
     * Handle an active fall that we're tracking
     */
    private static void handleActiveFall(MinecraftClient client, ClientPlayerEntity player,
            Vec3d velocity, Vec3d position) {
        // Check if fall has ended
        if (shouldStopTrackingFall(velocity, position)) {
            stopFallTracking("Fall ended");
            return;
        }

        // If we don't need to place water, just continue tracking
        if (!activeFallPrediction.shouldPlace()) {
            return;
        }

        // If we already attempted placement, don't try again
        if (waterPlacementAttempted) {
            return;
        }

        // Calculate current distance to the ORIGINAL prediction
        double currentDistanceToLanding =
                position.distanceTo(activeFallPrediction.getPredictedLandingPosition());

        MLGMaster.LOGGER.info("Fall progress: distance to landing = {} blocks (threshold = {})",
                String.format("%.3f", currentDistanceToLanding), PLACEMENT_REACH_DISTANCE);

        // Check if we're within placement range
        if (currentDistanceToLanding < PLACEMENT_REACH_DISTANCE) {
            MLGMaster.LOGGER.info("WITHIN PLACEMENT RANGE! Attempting water placement");

            // Log detailed pre-placement state
            logDetailedPlacementState(client, player, position, activeFallPrediction);

            // Attempt water placement using the stored prediction
            boolean placementSuccess =
                    WaterPlacer.executeWaterPlacement(client, player, activeFallPrediction);

            waterPlacementAttempted = true;
            lastPlacementAttempt = System.currentTimeMillis();

            if (placementSuccess) {
                MLGMaster.LOGGER.info("WATER MLG SUCCESSFUL!");
                stopFallTracking("Water placed successfully");
            } else {
                MLGMaster.LOGGER.warn("WATER MLG FAILED - placement unsuccessful");
                // Log comprehensive failure analysis
                logPlacementFailureAnalysis(client, player, position, activeFallPrediction);
                // Keep tracking in case we get another chance
            }
        }
    }


    /**
     * Log detailed state before attempting placement
     */
    private static void logDetailedPlacementState(MinecraftClient client, ClientPlayerEntity player,
            Vec3d playerPosition, MLGPredictionResult prediction) {
        MLGMaster.LOGGER.info("=== PRE-PLACEMENT STATE ===");
        MLGMaster.LOGGER.info("Player position: {} (X={}, Y={}, Z={})", playerPosition,
                String.format("%.3f", playerPosition.x), String.format("%.3f", playerPosition.y),
                String.format("%.3f", playerPosition.z));
        MLGMaster.LOGGER.info("Player velocity: {}", player.getVelocity());
        MLGMaster.LOGGER.info("Target landing block: {} (X={}, Y={}, Z={})",
                prediction.getHighestLandingBlock(), prediction.getHighestLandingBlock().getX(),
                prediction.getHighestLandingBlock().getY(),
                prediction.getHighestLandingBlock().getZ());
        MLGMaster.LOGGER.info("Water placement target: {} (X={}, Y={}, Z={})",
                prediction.getWaterPlacementTarget(),
                String.format("%.3f", prediction.getWaterPlacementTarget().x),
                String.format("%.3f", prediction.getWaterPlacementTarget().y),
                String.format("%.3f", prediction.getWaterPlacementTarget().z));
    }

    /**
     * Log comprehensive failure analysis when water placement fails
     */
    private static void logPlacementFailureAnalysis(MinecraftClient client,
            ClientPlayerEntity player, Vec3d playerPosition, MLGPredictionResult prediction) {
        MLGMaster.LOGGER.error("=== WATER PLACEMENT FAILURE ANALYSIS ===");

        // Player state at failure
        MLGMaster.LOGGER.error("PLAYER STATE AT FAILURE:");
        MLGMaster.LOGGER.error("  Position: {} (X={}, Y={}, Z={})", playerPosition,
                String.format("%.3f", playerPosition.x), String.format("%.3f", playerPosition.y),
                String.format("%.3f", playerPosition.z));
        MLGMaster.LOGGER.error("  Velocity: {} (X={}, Y={}, Z={})", player.getVelocity(),
                String.format("%.3f", player.getVelocity().x),
                String.format("%.3f", player.getVelocity().y),
                String.format("%.3f", player.getVelocity().z));
        MLGMaster.LOGGER.error("  On ground: {}", player.isOnGround());
        MLGMaster.LOGGER.error("  In water: {}", player.isTouchingWater());
        MLGMaster.LOGGER.error("  Health: {}/{}", String.format("%.1f", player.getHealth()),
                String.format("%.1f", player.getMaxHealth()));

        // Distance calculations
        Vec3d predictedLanding = prediction.getPredictedLandingPosition();
        double distanceToLanding = playerPosition.distanceTo(predictedLanding);
        double heightAboveLanding = playerPosition.y - predictedLanding.y;

        MLGMaster.LOGGER.error("DISTANCE ANALYSIS:");
        MLGMaster.LOGGER.error("  Distance to predicted landing: {} blocks",
                String.format("%.3f", distanceToLanding));
        MLGMaster.LOGGER.error("  Height above landing: {} blocks",
                String.format("%.3f", heightAboveLanding));
        MLGMaster.LOGGER.error("  Placement reach threshold: {} blocks", PLACEMENT_REACH_DISTANCE);

        // Target block analysis
        BlockPos targetLandingBlock = prediction.getHighestLandingBlock();
        Vec3d waterPlacementTarget = prediction.getWaterPlacementTarget();
        BlockPos waterPlacementPos = targetLandingBlock.up();

        MLGMaster.LOGGER.error("TARGET BLOCK ANALYSIS:");
        MLGMaster.LOGGER.error("  Target landing block: {} (X={}, Y={}, Z={})", targetLandingBlock,
                targetLandingBlock.getX(), targetLandingBlock.getY(), targetLandingBlock.getZ());
        MLGMaster.LOGGER.error("  Block state: {}", client.world.getBlockState(targetLandingBlock));
        MLGMaster.LOGGER.error("  Block material: {}",
                client.world.getBlockState(targetLandingBlock).getBlock());
        MLGMaster.LOGGER.error("  Is solid: {}",
                client.world.getBlockState(targetLandingBlock).isSolid());

        MLGMaster.LOGGER.error("WATER PLACEMENT ANALYSIS:");
        MLGMaster.LOGGER.error("  Water placement position: {} (X={}, Y={}, Z={})",
                waterPlacementPos, waterPlacementPos.getX(), waterPlacementPos.getY(),
                waterPlacementPos.getZ());
        MLGMaster.LOGGER.error("  Water placement target: {} (X={}, Y={}, Z={})",
                waterPlacementTarget, String.format("%.3f", waterPlacementTarget.x),
                String.format("%.3f", waterPlacementTarget.y),
                String.format("%.3f", waterPlacementTarget.z));
        MLGMaster.LOGGER.error("  Block at placement location: {}",
                client.world.getBlockState(waterPlacementPos));
        MLGMaster.LOGGER.error("  Distance to placement target: {} blocks",
                String.format("%.3f", playerPosition.distanceTo(waterPlacementTarget)));

        // Ground distance analysis
        double distanceToGround = calculateDistanceToGround(client, playerPosition);
        MLGMaster.LOGGER.error("GROUND ANALYSIS:");
        MLGMaster.LOGGER.error("  Direct distance to ground: {} blocks",
                String.format("%.3f", distanceToGround));
        MLGMaster.LOGGER.error("  Estimated impact time: {} ticks", String.format("%.2f",
                calculateEstimatedImpactTime(player.getVelocity().y, distanceToGround)));

        // Inventory analysis
        MLGMaster.LOGGER.error("INVENTORY ANALYSIS:");
        MLGMaster.LOGGER.error("  Main hand: {}", player.getMainHandStack().getItem());
        MLGMaster.LOGGER.error("  Off hand: {}", player.getOffHandStack().getItem());
        MLGMaster.LOGGER.error("  Has water bucket: {}", InventoryManager.hasWaterBucket(player));
        MLGMaster.LOGGER.error("  Water bucket in main hand: {}",
                player.getMainHandStack().getItem().toString().contains("water_bucket"));
        MLGMaster.LOGGER.error("  Water bucket in off hand: {}",
                player.getOffHandStack().getItem().toString().contains("water_bucket"));

        // Surrounding blocks analysis
        MLGMaster.LOGGER.error("SURROUNDING BLOCKS ANALYSIS:");
        logSurroundingBlocks(client, waterPlacementPos, "water placement");
        logSurroundingBlocks(client, targetLandingBlock, "target landing");

        // Alternative blocks analysis
        if (prediction.getLandingResult() != null
                && prediction.getLandingResult().getAllHitBlocks() != null) {
            MLGMaster.LOGGER.error("ALTERNATIVE LANDING BLOCKS:");
            java.util.List<BlockPos> allHitBlocks = prediction.getLandingResult().getAllHitBlocks();
            for (int i = 0; i < Math.min(allHitBlocks.size(), 5); i++) { // Log first 5 alternatives
                BlockPos altBlock = allHitBlocks.get(i);
                BlockPos altPlacement = altBlock.up();
                MLGMaster.LOGGER.error("  Alternative {}: {} -> water at {} (state: {})", i + 1,
                        altBlock, altPlacement, client.world.getBlockState(altPlacement));
            }
        }

        MLGMaster.LOGGER.error("=== END FAILURE ANALYSIS ===");
    }


    /**
     * Calculate distance to ground directly below player
     */
    private static double calculateDistanceToGround(MinecraftClient client, Vec3d playerPosition) {
        for (int i = 1; i <= 100; i++) { // Check up to 100 blocks down
            BlockPos checkPos =
                    BlockPos.ofFloored(playerPosition.x, playerPosition.y - i, playerPosition.z);
            if (!client.world.getBlockState(checkPos).isAir()) {
                return i - 1; // Return distance to the block surface
            }
        }
        return 100.0; // If no ground found within 100 blocks
    }

    /**
     * Calculate estimated time until impact based on current velocity and distance
     */
    private static double calculateEstimatedImpactTime(double yVelocity, double distanceToGround) {
        if (yVelocity >= 0) {
            return Double.MAX_VALUE; // Not falling
        }

        // Simple estimation: time = distance / speed (ignoring acceleration for simplicity)
        return distanceToGround / Math.abs(yVelocity);
    }

    /**
     * Log surrounding blocks around a position
     */
    private static void logSurroundingBlocks(MinecraftClient client, BlockPos centerPos,
            String description) {
        MLGMaster.LOGGER.error("  Blocks around {} ({}): ", description, centerPos);

        // Check blocks in a small area around the position
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos checkPos = centerPos.add(dx, dy, dz);
                    var blockState = client.world.getBlockState(checkPos);

                    if (dx == 0 && dy == 0 && dz == 0) {
                        MLGMaster.LOGGER.error("    CENTER {}: {}", checkPos,
                                blockState.getBlock());
                    } else if (!blockState.isAir()) {
                        String direction = String.format("(%+d,%+d,%+d)", dx, dy, dz);
                        MLGMaster.LOGGER.error("    {} {}: {}", direction, checkPos,
                                blockState.getBlock());
                    }
                }
            }
        }
    }

    /**
     * Determine if we should stop tracking the current fall
     */
    private static boolean shouldStopTrackingFall(Vec3d velocity, Vec3d position) {
        // Stop if player is no longer falling significantly
        if (velocity.y >= -0.05) { // Almost stopped or moving up
            return true;
        }

        // Stop if fall has been going on too long (safety)
        long fallDuration = System.currentTimeMillis() - fallStartTime;
        if (fallDuration > 30000) { // 30 seconds max
            return true;
        }

        // Stop if player is on ground (landed)
        if (MinecraftClient.getInstance().player != null
                && MinecraftClient.getInstance().player.isOnGround()) {
            return true;
        }

        return false;
    }

    /**
     * Stop tracking the current fall
     */
    private static void stopFallTracking(String reason) {
        MLGMaster.LOGGER.info("=== STOPPING FALL TRACKING ===");
        MLGMaster.LOGGER.info("Reason: {}", reason);

        if (isTrackingFall && fallStartPosition != null) {
            Vec3d currentPos = MinecraftClient.getInstance().player.getPos();
            double totalFallDistance = fallStartPosition.y - currentPos.y;
            long fallDuration = System.currentTimeMillis() - fallStartTime;

            MLGMaster.LOGGER.info("Fall statistics:");
            MLGMaster.LOGGER.info("  Total fall distance: {} blocks",
                    String.format("%.3f", totalFallDistance));
            MLGMaster.LOGGER.info("  Fall duration: {} ms", fallDuration);
            MLGMaster.LOGGER.info("  Water placement attempted: {}", waterPlacementAttempted);
        }

        // Reset state
        isTrackingFall = false;
        activeFallPrediction = null;
        fallStartTime = 0;
        fallStartPosition = null;
        waterPlacementAttempted = false;
    }


    /**
     * Analyze fall and determine if/when water should be placed
     */
    public static MLGPredictionResult analyzeFallAndPlacement(MinecraftClient client,
            ClientPlayerEntity player, Vec3d velocity) {
        MLGMaster.LOGGER.info("=== WATER MLG ANALYSIS START ===");
        MLGMaster.LOGGER.info("Player position: {}", player.getPos());
        MLGMaster.LOGGER.info("Player velocity: {}", velocity);
        MLGMaster.LOGGER.info("Placement reach distance: {} blocks", PLACEMENT_REACH_DISTANCE);

        Vec3d currentPos = player.getPos();

        // Check if player is falling (negative Y velocity)
        if (velocity.y >= 0) {
            MLGMaster.LOGGER.info("Player is not falling (Y velocity: {}), no MLG needed",
                    velocity.y);
            return new MLGPredictionResult(false, "Player is not falling", null, null, null, 0.0,
                    null);
        }

        MLGMaster.LOGGER.info("Player is falling with Y velocity: {}", velocity.y);

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

        MLGMaster.LOGGER.info("Predicted landing:");
        MLGMaster.LOGGER.info("  Position: {}", predictedLandingPosition);
        MLGMaster.LOGGER.info("  Highest block: {} (Y={})", highestLandingBlock,
                highestLandingBlock.getY());

        // Calculate distance to predicted landing position
        double distanceToLanding = currentPos.distanceTo(predictedLandingPosition);
        double fallDistance = currentPos.y - predictedLandingPosition.y;

        MLGMaster.LOGGER.info("Distance calculations:");
        MLGMaster.LOGGER.info("  Current to landing: {} blocks", distanceToLanding);
        MLGMaster.LOGGER.info("  Fall distance: {} blocks", fallDistance);

        // Check if fall distance is significant enough to warrant MLG
        if (fallDistance < MIN_FALL_DISTANCE) {
            MLGMaster.LOGGER.info("Fall distance {} is less than minimum {} blocks, no MLG needed",
                    fallDistance, MIN_FALL_DISTANCE);
            return new MLGPredictionResult(false, "Fall distance too small for MLG",
                    highestLandingBlock, null, landingResult, distanceToLanding,
                    predictedLandingPosition);
        }

        // Check if landing is already safe
        if (landingResult.isSafeLanding()) {
            MLGMaster.LOGGER.info("Landing is already safe: {}",
                    landingResult.getSafetyResult().getReason());
            return new MLGPredictionResult(false, "Landing is already safe", highestLandingBlock,
                    null, landingResult, distanceToLanding, predictedLandingPosition);
        }

        MLGMaster.LOGGER.info("Landing is unsafe: {}", landingResult.getSafetyResult().getReason());

        // Calculate water placement target
        Vec3d waterPlacementTarget = landingResult.getLookTarget();

        MLGMaster.LOGGER.info("Water placement analysis:");
        MLGMaster.LOGGER.info("  Target block: {}", highestLandingBlock);
        MLGMaster.LOGGER.info("  Water placement target: {}", waterPlacementTarget);

        // Verify we have inventory access for water bucket
        if (!InventoryManager.hasWaterBucket(player)) {
            MLGMaster.LOGGER.warn("No water bucket available in inventory");
            return new MLGPredictionResult(false, "No water bucket available", highestLandingBlock,
                    waterPlacementTarget, landingResult, distanceToLanding,
                    predictedLandingPosition);
        }

        MLGMaster.LOGGER.info("=== WATER PLACEMENT APPROVED ===");
        MLGMaster.LOGGER.info("Reason: Unsafe landing detected, fall distance sufficient");
        MLGMaster.LOGGER.info("Distance to target: {} blocks", distanceToLanding);

        return new MLGPredictionResult(true, "Unsafe landing detected, MLG required",
                highestLandingBlock, waterPlacementTarget, landingResult, distanceToLanding,
                predictedLandingPosition);
    }

    /**
     * Check if water placement should be triggered based on current conditions This is the main
     * entry point for the MLG system
     */
    public static boolean shouldTriggerWaterPlacement(MinecraftClient client,
            ClientPlayerEntity player) {
        // Get current velocity
        Vec3d velocity = player.getVelocity();

        // Quick pre-checks
        if (velocity.y >= 0) {
            return false; // Not falling
        }

        // Perform full analysis
        MLGPredictionResult result = analyzeFallAndPlacement(client, player, velocity);

        if (result.shouldPlace()) {
            MLGMaster.LOGGER.info("TRIGGERING WATER PLACEMENT");
            MLGMaster.LOGGER.info("  Distance to landing: {} blocks", result.getDistanceToTarget());
            MLGMaster.LOGGER.info("  Reason: {}", result.getReason());
            return true;
        } else {
            // Only log if we're falling significantly (to avoid spam)
            if (velocity.y < -0.2) {
                MLGMaster.LOGGER.info("Water placement not triggered: {}", result.getReason());
                MLGMaster.LOGGER.info("  Distance to landing: {} blocks (threshold: {})",
                        result.getDistanceToTarget(), PLACEMENT_REACH_DISTANCE);
            }
            return false;
        }
    }

    /**
     * Execute water placement if conditions are met Returns true if placement was attempted
     * (regardless of success)
     */
    public static boolean handleWaterMLG(MinecraftClient client, ClientPlayerEntity player) {
        Vec3d velocity = player.getVelocity();

        MLGPredictionResult prediction = analyzeFallAndPlacement(client, player, velocity);

        if (!prediction.shouldPlace()) {
            return false; // Conditions not met
        }

        MLGMaster.LOGGER.info("EXECUTING WATER MLG");
        MLGMaster.LOGGER.info("  Distance to target: {} blocks", prediction.getDistanceToTarget());
        MLGMaster.LOGGER.info("  Landing block: {}", prediction.getHighestLandingBlock());

        // Attempt water placement
        boolean placementSuccess = WaterPlacer.executeWaterPlacement(client, player, prediction);

        if (placementSuccess) {
            MLGMaster.LOGGER.info("WATER MLG SUCCESSFUL!");
        } else {
            MLGMaster.LOGGER.warn("WATER MLG FAILED - placement unsuccessful");
        }

        return true; // Attempted placement
    }

    /**
     * Get the current distance to predicted landing (for debug/UI purposes)
     */
    public static double getDistanceToLanding(MinecraftClient client, ClientPlayerEntity player) {
        Vec3d velocity = player.getVelocity();

        if (velocity.y >= 0) {
            return Double.MAX_VALUE; // Not falling
        }

        MLGPredictionResult result = analyzeFallAndPlacement(client, player, velocity);
        return result.getDistanceToTarget();
    }

    /**
     * Check if player is currently in a state that could require MLG
     */
    public static boolean isMLGCandidate(ClientPlayerEntity player) {
        Vec3d velocity = player.getVelocity();

        // Must be falling with significant velocity
        return velocity.y < -0.1; // Falling faster than just walking off a block
    }

    /**
     * Get current fall tracking status (for debug/UI)
     */
    public static boolean isCurrentlyTrackingFall() {
        return isTrackingFall;
    }

    /**
     * Get distance to landing for current tracked fall (for debug/UI)
     */
    public static double getCurrentDistanceToLanding() {
        if (!isTrackingFall || activeFallPrediction == null) {
            return Double.MAX_VALUE;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return Double.MAX_VALUE;
        }

        return client.player.getPos()
                .distanceTo(activeFallPrediction.getPredictedLandingPosition());
    }

    /**
     * Force stop fall tracking (for testing/debugging)
     */
    public static void forceStopTracking() {
        stopFallTracking("Forced stop");
    }

    /**
     * Reset cooldown (useful for testing or manual triggers)
     */
    public static void resetCooldown() {
        lastPlacementAttempt = 0;
        MLGMaster.LOGGER.info("Placement cooldown reset");
    }

    /**
     * Get current tracking state info (for debug/UI)
     */
    public static String getTrackingStateInfo() {
        if (!isTrackingFall) {
            return "Not tracking any fall";
        }

        StringBuilder info = new StringBuilder();
        info.append("Tracking fall: ");

        if (activeFallPrediction != null) {
            info.append("needs_water=").append(activeFallPrediction.shouldPlace());
            info.append(", attempted=").append(waterPlacementAttempted);
            info.append(", distance=").append(String.format("%.2f", getCurrentDistanceToLanding()));
            info.append(", threshold=").append(PLACEMENT_REACH_DISTANCE);
        } else {
            info.append("no prediction data");
        }

        return info.toString();
    }
}

