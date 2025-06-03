package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class MLGDistanceCalculator {
    private static final double BASE_PLACEMENT_DISTANCE = 5.0;
    private static final double MAX_PLACEMENT_DISTANCE = 15.0;
    private static final double VELOCITY_MULTIPLIER = 8.0;
    private static final double URGENT_DISTANCE_MULTIPLIER = 1.5;
    
    public static class DistanceResult {
        private final double placementDistance;
        private final boolean isUrgent;
        private final double fallSpeed;
        private final int estimatedImpactTime;
        
        public DistanceResult(double placementDistance, boolean isUrgent, double fallSpeed, int estimatedImpactTime) {
            this.placementDistance = placementDistance;
            this.isUrgent = isUrgent;
            this.fallSpeed = fallSpeed;
            this.estimatedImpactTime = estimatedImpactTime;
        }
        
        public double getPlacementDistance() { return placementDistance; }
        public boolean isUrgent() { return isUrgent; }
        public double getFallSpeed() { return fallSpeed; }
        public int getEstimatedImpactTime() { return estimatedImpactTime; }
    }
    
    /**
     * Calculate dynamic placement distance based on fall characteristics
     */
    public static DistanceResult calculatePlacementDistance(MinecraftClient client, ClientPlayerEntity player, Vec3d velocity) {
        double fallSpeed = Math.abs(velocity.y);
        
        // Quick physics simulation to estimate impact time
        int estimatedImpactTime = estimateImpactTime(client, player, velocity);
        
        // Calculate dynamic placement distance based on velocity
        double velocityFactor = Math.min(fallSpeed * VELOCITY_MULTIPLIER, MAX_PLACEMENT_DISTANCE - BASE_PLACEMENT_DISTANCE);
        double dynamicPlacementDistance = BASE_PLACEMENT_DISTANCE + velocityFactor;
        
        // Clamp to reasonable bounds
        dynamicPlacementDistance = Math.max(BASE_PLACEMENT_DISTANCE, 
                                          Math.min(MAX_PLACEMENT_DISTANCE, dynamicPlacementDistance));
        
        // Determine if this is urgent (very little time left)
        boolean isUrgent = estimatedImpactTime <= 20; // Less than 1 second
        
        // If urgent, increase placement distance
        if (isUrgent) {
            dynamicPlacementDistance = Math.min(MAX_PLACEMENT_DISTANCE, dynamicPlacementDistance * URGENT_DISTANCE_MULTIPLIER);
            MLGMaster.LOGGER.warn("🚨 URGENT PLACEMENT detected - increasing distance to {:.3f}", dynamicPlacementDistance);
        }
        
        MLGMaster.LOGGER.info("📏 DISTANCE CALCULATION:");
        MLGMaster.LOGGER.info("  Fall speed: {:.3f} blocks/tick", fallSpeed);
        MLGMaster.LOGGER.info("  Estimated impact time: {} ticks", estimatedImpactTime);
        MLGMaster.LOGGER.info("  Dynamic placement distance: {:.3f} blocks", dynamicPlacementDistance);
        MLGMaster.LOGGER.info("  Is urgent placement: {}", isUrgent);
        
        return new DistanceResult(dynamicPlacementDistance, isUrgent, fallSpeed, estimatedImpactTime);
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
}