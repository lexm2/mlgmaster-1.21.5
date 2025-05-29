package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class WaterMLGHandler {
    private static boolean isActive = false;
    private static long lastPredictionTime = 0;
    private static final long PREDICTION_INTERVAL = 50;
    private static final double FALL_TRIGGER_DISTANCE = 5.0;
    
    // Dynamic thresholds based on velocity
    private static final double BASE_PLACEMENT_DISTANCE = 5.0;
    private static final double MAX_PLACEMENT_DISTANCE = 15.0;
    private static final double VELOCITY_MULTIPLIER = 8.0; // How much velocity affects distance
    private static final double WATER_FLOW_TIME_TICKS = 10.0; // Time for water to flow and create safe area
    private static final double REACTION_TIME_BUFFER_TICKS = 5.0; // Buffer for processing/placement time
    
    /**
     * Enhanced prediction result with velocity-aware timing
     */
    public static class MLGPredictionResult {
        private final boolean shouldPlace;
        private final boolean willLand;
        private final LandingPredictor.HitboxLandingResult landingResult;
        private final BlockPos highestLandingBlock;
        private final Vec3d waterPlacementTarget;
        private final double distanceToTarget;
        private final String reason;
        private final SafeLandingBlockChecker.SafetyResult safetyResult;
        private final int ticksToImpact;
        private final double dynamicPlacementDistance;
        private final boolean isUrgentPlacement;
        
        public MLGPredictionResult(boolean shouldPlace, boolean willLand, 
                                  LandingPredictor.HitboxLandingResult landingResult,
                                  BlockPos highestLandingBlock, Vec3d waterPlacementTarget,
                                  double distanceToTarget, String reason,
                                  SafeLandingBlockChecker.SafetyResult safetyResult,
                                  int ticksToImpact, double dynamicPlacementDistance,
                                  boolean isUrgentPlacement) {
            this.shouldPlace = shouldPlace;
            this.willLand = willLand;
            this.landingResult = landingResult;
            this.highestLandingBlock = highestLandingBlock;
            this.waterPlacementTarget = waterPlacementTarget;
            this.distanceToTarget = distanceToTarget;
            this.reason = reason;
            this.safetyResult = safetyResult;
            this.ticksToImpact = ticksToImpact;
            this.dynamicPlacementDistance = dynamicPlacementDistance;
            this.isUrgentPlacement = isUrgentPlacement;
        }
        
        public boolean shouldPlace() { return shouldPlace; }
        public boolean willLand() { return willLand; }
        public LandingPredictor.HitboxLandingResult getLandingResult() { return landingResult; }
        public BlockPos getHighestLandingBlock() { return highestLandingBlock; }
        public Vec3d getWaterPlacementTarget() { return waterPlacementTarget; }
        public double getDistanceToTarget() { return distanceToTarget; }
        public String getReason() { return reason; }
        public SafeLandingBlockChecker.SafetyResult getSafetyResult() { return safetyResult; }
        public int getTicksToImpact() { return ticksToImpact; }
        public double getDynamicPlacementDistance() { return dynamicPlacementDistance; }
        public boolean isUrgentPlacement() { return isUrgentPlacement; }
        public boolean isWithinPlacementDistance() { return distanceToTarget <= dynamicPlacementDistance && distanceToTarget > 0; }
    }
    
    /**
     * ENHANCED PREDICTION FUNCTION with velocity-aware timing
     */
    public static MLGPredictionResult analyzeFallAndPlacement(MinecraftClient client, ClientPlayerEntity player, Vec3d velocity) {
        Vec3d playerPos = player.getPos();
        
        MLGMaster.LOGGER.info("🚀 VELOCITY-AWARE MLG ANALYSIS: Starting enhanced fall prediction");
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
                -1, 0, false);
        }
        
        if (player.fallDistance < 3.0) {
            MLGMaster.LOGGER.info("❌ Insufficient fall distance ({:.3f} < 3.0)", player.fallDistance);
            return new MLGPredictionResult(false, false, null, null, null, -1, 
                "Insufficient fall distance", new SafeLandingBlockChecker.SafetyResult(false, "Insufficient fall distance"),
                -1, 0, false);
        }
        
        if (player.isOnGround()) {
            MLGMaster.LOGGER.info("❌ Player is on ground");
            return new MLGPredictionResult(false, false, null, null, null, -1, 
                "On ground", new SafeLandingBlockChecker.SafetyResult(false, "On ground"),
                -1, 0, false);
        }
        
        MLGMaster.LOGGER.info("✅ Fall validation passed - proceeding with enhanced prediction");
        
        // Calculate velocity-aware timing
        double fallSpeed = Math.abs(velocity.y);
        VelocityTimingResult timing = calculateVelocityAwareTiming(client, player, velocity);
        
        MLGMaster.LOGGER.info("⏱️ VELOCITY TIMING ANALYSIS:");
        MLGMaster.LOGGER.info("  Fall speed: {:.3f} blocks/tick", fallSpeed);
        MLGMaster.LOGGER.info("  Estimated ticks to impact: {}", timing.ticksToImpact);
        MLGMaster.LOGGER.info("  Dynamic placement distance: {:.3f} blocks", timing.dynamicPlacementDistance);
        MLGMaster.LOGGER.info("  Required lead time: {:.1f} ticks", timing.requiredLeadTime);
        MLGMaster.LOGGER.info("  Is urgent placement: {}", timing.isUrgent);
        
        // Predict landing with full hitbox consideration
        LandingPredictor.HitboxLandingResult landingResult = LandingPredictor.predictHitboxLanding(client, player, playerPos, velocity);
        
        if (landingResult == null) {
            MLGMaster.LOGGER.info("❌ No landing predicted by hitbox analysis");
            return new MLGPredictionResult(false, false, null, null, null, -1, 
                "No landing predicted", new SafeLandingBlockChecker.SafetyResult(false, "No landing predicted"),
                timing.ticksToImpact, timing.dynamicPlacementDistance, timing.isUrgent);
        }
        
        // Find the HIGHEST block from all hit blocks
        BlockPos highestBlock = findHighestHitBlock(landingResult.getAllHitBlocks());
        
        if (highestBlock == null) {
            MLGMaster.LOGGER.warn("⚠️ No highest block found despite successful landing prediction");
            return new MLGPredictionResult(false, false, landingResult, null, null, -1, 
                "No highest block found", new SafeLandingBlockChecker.SafetyResult(false, "No highest block found"),
                timing.ticksToImpact, timing.dynamicPlacementDistance, timing.isUrgent);
        }
        
        // Use the highest block for safety checking
        SafeLandingBlockChecker.SafetyResult safetyResult = 
            SafeLandingBlockChecker.checkLandingSafety(client, player, highestBlock, playerPos);
        
        if (safetyResult.isSafe()) {
            MLGMaster.LOGGER.info("🛡️ Landing is SAFE ({}), no water needed", safetyResult.getReason());
            return new MLGPredictionResult(false, true, landingResult, highestBlock, null, -1, 
                "Safe landing: " + safetyResult.getReason(), safetyResult,
                timing.ticksToImpact, timing.dynamicPlacementDistance, timing.isUrgent);
        }
        
        // Calculate water placement target
        BlockPos waterPlacementPos = highestBlock.up();
        Vec3d waterPlacementTarget = Vec3d.ofCenter(waterPlacementPos);
        
        // Calculate distance to placement target
        double distanceToTarget = playerPos.distanceTo(waterPlacementTarget);
        
        // Determine if we should place water based on DYNAMIC distance and timing
        boolean shouldPlace = shouldPlaceWaterNow(distanceToTarget, timing, fallSpeed);
        
        String reason = buildPlacementReason(shouldPlace, distanceToTarget, timing, fallSpeed);
        
        MLGMaster.LOGGER.info("🎯 ENHANCED PLACEMENT DECISION:");
        MLGMaster.LOGGER.info("  Distance to target: {:.3f} blocks", distanceToTarget);
        MLGMaster.LOGGER.info("  Dynamic threshold: {:.3f} blocks", timing.dynamicPlacementDistance);
        MLGMaster.LOGGER.info("  Ticks to impact: {}", timing.ticksToImpact);
        MLGMaster.LOGGER.info("  Is urgent: {}", timing.isUrgent);
        MLGMaster.LOGGER.info("  Decision: {}", shouldPlace ? "PLACE NOW!" : "WAIT");
        MLGMaster.LOGGER.info("  Reason: {}", reason);
        
        return new MLGPredictionResult(shouldPlace, true, landingResult, highestBlock, 
            waterPlacementTarget, distanceToTarget, reason, safetyResult,
            timing.ticksToImpact, timing.dynamicPlacementDistance, timing.isUrgent);
    }
    
    /**
     * Calculate velocity-aware timing and thresholds
     */
    private static class VelocityTimingResult {
        final int ticksToImpact;
        final double dynamicPlacementDistance;
        final double requiredLeadTime;
        final boolean isUrgent;
        
        VelocityTimingResult(int ticksToImpact, double dynamicPlacementDistance, 
                           double requiredLeadTime, boolean isUrgent) {
            this.ticksToImpact = ticksToImpact;
            this.dynamicPlacementDistance = dynamicPlacementDistance;
            this.requiredLeadTime = requiredLeadTime;
            this.isUrgent = isUrgent;
        }
    }
    
    private static VelocityTimingResult calculateVelocityAwareTiming(MinecraftClient client, ClientPlayerEntity player, Vec3d velocity) {
        double fallSpeed = Math.abs(velocity.y);
        
        // Run a quick physics simulation to estimate impact time
        int ticksToImpact = estimateImpactTime(client, player, velocity);
        
        // Calculate required lead time (water flow + reaction buffer)
        double requiredLeadTime = WATER_FLOW_TIME_TICKS + REACTION_TIME_BUFFER_TICKS;
        
        // Calculate dynamic placement distance based on velocity
        // Faster fall = place water from farther away
        double velocityFactor = Math.min(fallSpeed * VELOCITY_MULTIPLIER, MAX_PLACEMENT_DISTANCE - BASE_PLACEMENT_DISTANCE);
        double dynamicPlacementDistance = BASE_PLACEMENT_DISTANCE + velocityFactor;
        
        // Clamp to reasonable bounds
        dynamicPlacementDistance = Math.max(BASE_PLACEMENT_DISTANCE, 
                                          Math.min(MAX_PLACEMENT_DISTANCE, dynamicPlacementDistance));
        
        // Determine if this is urgent (very little time left)
        boolean isUrgent = ticksToImpact <= requiredLeadTime + 5; // 5 tick safety buffer
        
        // If urgent, increase placement distance even more
        if (isUrgent) {
            dynamicPlacementDistance = Math.min(MAX_PLACEMENT_DISTANCE, dynamicPlacementDistance * 1.5);
            MLGMaster.LOGGER.warn("🚨 URGENT PLACEMENT detected - increasing distance to {:.3f}", dynamicPlacementDistance);
        }
        
        return new VelocityTimingResult(ticksToImpact, dynamicPlacementDistance, requiredLeadTime, isUrgent);
    }
    
    /**
     * Quick physics simulation to estimate impact time
     */
    private static int estimateImpactTime(MinecraftClient client, ClientPlayerEntity player, Vec3d velocity) {
        Vec3d testPos = player.getPos();
        Vec3d testVel = velocity;
        
        for (int tick = 0; tick < 100; tick++) { // Max 5 seconds of simulation
            testVel = testVel.add(0, -0.08, 0); // Gravity
            testVel = testVel.multiply(0.98); // Air resistance
            testPos = testPos.add(testVel);
            
            // Simple ground check
            BlockPos blockBelow = BlockPos.ofFloored(testPos);
            if (PhysicsSimulator.isBlockSolid(client, blockBelow)) {
                MLGMaster.LOGGER.info("🎯 Impact estimated at tick {} (position: {})", tick, testPos);
                return tick;
            }
            
            // Safety check
            if (testPos.y < player.getPos().y - 50) {
                MLGMaster.LOGGER.warn("⚠️ Impact estimation stopped - fell too far");
                break;
            }
        }
        
        MLGMaster.LOGGER.warn("⚠️ Could not estimate impact time - using default");
        return 50; // Default fallback
    }
    
    /**
     * Determine if we should place water now based on velocity-aware factors
     */
    private static boolean shouldPlaceWaterNow(double distanceToTarget, VelocityTimingResult timing, double fallSpeed) {
        // Primary check: within dynamic distance threshold
        boolean withinDistance = distanceToTarget <= timing.dynamicPlacementDistance;
        
        // Secondary check: urgent placement (running out of time)
        boolean urgentTiming = timing.isUrgent && distanceToTarget <= MAX_PLACEMENT_DISTANCE;
        
        // Tertiary check: very fast fall speed (emergency placement)
        boolean emergencySpeed = fallSpeed > 1.5 && distanceToTarget <= timing.dynamicPlacementDistance * 1.2;
        
        return withinDistance || urgentTiming || emergencySpeed;
    }
    
    /**
     * Build a descriptive reason for the placement decision
     */
    private static String buildPlacementReason(boolean shouldPlace, double distanceToTarget, 
                                             VelocityTimingResult timing, double fallSpeed) {
        if (!shouldPlace) {
            return String.format("Waiting: distance %.2f > threshold %.2f, %d ticks remaining", 
                distanceToTarget, timing.dynamicPlacementDistance, timing.ticksToImpact);
        }
        
        if (timing.isUrgent) {
            return String.format("URGENT: Only %d ticks left (distance %.2f)", 
                timing.ticksToImpact, distanceToTarget);
        }
        
        if (fallSpeed > 1.5) {
            return String.format("HIGH SPEED: Fall speed %.3f (distance %.2f)", 
                fallSpeed, distanceToTarget);
        }
        
        return String.format("Ready: distance %.2f <= threshold %.2f", 
            distanceToTarget, timing.dynamicPlacementDistance);
    }
    
    /**
     * Find the block with the highest Y coordinate from all hit blocks
     */
    private static BlockPos findHighestHitBlock(java.util.List<BlockPos> hitBlocks) {
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
    
    public static void onHighFrequencyTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return;
        }
        
        ClientPlayerEntity player = client.player;
        Vec3d velocity = player.getVelocity();
        
        // Release scaffolding crouch if not falling anymore
        if (velocity.y >= -0.1 || player.isOnGround()) {
            ScaffoldingCrouchManager.releaseScaffoldingCrouch();
            
            if (isActive) {
                isActive = false;
                MLGMaster.LOGGER.info("Player stopped falling, deactivating MLG");
            }
            return;
        }
        
        // Only activate water MLG when falling enough distance
        if (player.fallDistance < FALL_TRIGGER_DISTANCE) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // Reduce prediction interval for high-speed falls
        double fallSpeed = Math.abs(velocity.y);
        long dynamicInterval = fallSpeed > 1.0 ? PREDICTION_INTERVAL / 2 : PREDICTION_INTERVAL;
        
        if (currentTime - lastPredictionTime < dynamicInterval) {
            return;
        }
        lastPredictionTime = currentTime;
        
        // Use the ENHANCED prediction function
        MLGPredictionResult prediction = analyzeFallAndPlacement(client, player, velocity);
        
        if (prediction.isUrgentPlacement()) {
            MLGMaster.LOGGER.warn("🚨 URGENT MLG SITUATION: {} ticks to impact!", prediction.getTicksToImpact());
        }
        
        MLGMaster.LOGGER.info("🎯 MLG TICK ANALYSIS: {} - {}", 
            prediction.shouldPlace() ? "PLACE NOW!" : "WAITING", prediction.getReason());
        
        if (prediction.shouldPlace()) {
            BlockPos targetBlock = prediction.getHighestLandingBlock();
            Vec3d targetPos = prediction.getWaterPlacementTarget();
            
            MLGMaster.LOGGER.info("💧 EXECUTING VELOCITY-AWARE PLACEMENT:");
            MLGMaster.LOGGER.info("  Fall speed: {:.3f} blocks/tick", Math.abs(velocity.y));
            MLGMaster.LOGGER.info("  Dynamic threshold: {:.3f} blocks", prediction.getDynamicPlacementDistance());
            MLGMaster.LOGGER.info("  Time to impact: {} ticks", prediction.getTicksToImpact());
            MLGMaster.LOGGER.info("  Target: {} at {}", targetBlock, targetPos);
            
            if (WaterPlacer.executeWaterPlacement(client, player, prediction)) {
                isActive = true;
                MLGMaster.LOGGER.info("✅ Velocity-aware water placement successful!");
                
                // Shorter pause for high-speed situations
                long pauseDuration = prediction.isUrgentPlacement() ? 500 : 1000;
                lastPredictionTime = currentTime + pauseDuration;
            } else {
                MLGMaster.LOGGER.warn("❌ Velocity-aware water placement failed");
            }
        } else if (prediction.willLand()) {
            MLGMaster.LOGGER.info("📊 Velocity analysis: {} | ETA: {} ticks | Speed: {:.3f}", 
                prediction.getReason(), prediction.getTicksToImpact(), Math.abs(velocity.y));
        }
    }
}
