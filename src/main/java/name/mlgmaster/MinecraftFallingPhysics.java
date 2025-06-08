package name.mlgmaster;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Accurate Minecraft falling physics calculations
 * Based on Minecraft's gravity mechanics: -0.08 blocks/tick deceleration, then *0.98 drag
 */
public class MinecraftFallingPhysics {
    
    // Minecraft physics constants
    private static final double GRAVITY_ACCELERATION = -0.08; // blocks per tick per tick
    private static final double AIR_DRAG = 0.98; // velocity multiplier each tick
    private static final double TERMINAL_VELOCITY = 3.92; // theoretical maximum (blocks/tick)
    
    /**
     * Calculate velocity at a given tick using Minecraft's physics formula
     * v(t) = (0.98^floor(t) - 1) × 3.92
     * @param tickTime The time in ticks (will be floored as Minecraft only updates once per tick)
     * @return Velocity in blocks/tick (negative for downward)
     */
    public static double calculateVelocityAtTick(double tickTime) {
        int flooredTick = (int) Math.floor(tickTime);
        return (Math.pow(AIR_DRAG, flooredTick) - 1) * TERMINAL_VELOCITY;
    }
    
    /**
     * Calculate distance fallen by a given tick using the integrated formula
     * d(t) = 196 - 3.92 × t - 194.04 × 0.98^(t-0.5)
     * @param tickTime The time in ticks
     * @return Distance fallen in blocks (positive value)
     */
    public static double calculateDistanceFallenAtTick(double tickTime) {
        return 196 - (TERMINAL_VELOCITY * tickTime) - (194.04 * Math.pow(AIR_DRAG, tickTime - 0.5));
    }
    
    /**
     * Estimate time to fall a given distance using iterative approximation
     * @param distanceToFall Distance in blocks (positive)
     * @param maxIterations Maximum iterations to prevent infinite loops
     * @return Estimated time in ticks
     */
    public static double estimateTimeToFallDistance(double distanceToFall, int maxIterations) {
        double tickTime = 0;
        double increment = 1.0;
        
        for (int i = 0; i < maxIterations; i++) {
            double currentDistance = calculateDistanceFallenAtTick(tickTime);
            
            if (Math.abs(currentDistance - distanceToFall) < 0.1) {
                return tickTime;
            }
            
            if (currentDistance < distanceToFall) {
                tickTime += increment;
            } else {
                tickTime -= increment;
                increment *= 0.5; // Refine search
            }
        }
        
        return tickTime;
    }
    
    /**
     * Get current falling speed based on player's current velocity and fall distance
     * This provides more context than just the raw velocity
     */
    public static FallingSpeedInfo analyzeFallingSpeed(ClientPlayerEntity player, Vec3d velocity) {
        double currentVelocityY = velocity.y;
        double fallDistance = player.fallDistance;
        
        // Estimate what tick we're at based on fall distance
        double estimatedTick = 0;
        if (fallDistance > 0) {
            estimatedTick = estimateTimeToFallDistance(fallDistance, 100);
        }
        
        // Calculate theoretical velocity at this point
        double theoreticalVelocity = calculateVelocityAtTick(estimatedTick);
        
        // Calculate speed in blocks/second
        double speedBlocksPerSecond = Math.abs(currentVelocityY) * 20;
        double speedMetersPerSecond = speedBlocksPerSecond; // 1 block = 1 meter in Minecraft
        
        // Estimate terminal velocity approach
        double terminalVelocityPercent = (Math.abs(currentVelocityY) / TERMINAL_VELOCITY) * 100;
        
        return new FallingSpeedInfo(
            currentVelocityY,
            Math.abs(currentVelocityY),
            speedBlocksPerSecond,
            speedMetersPerSecond,
            fallDistance,
            estimatedTick,
            theoreticalVelocity,
            terminalVelocityPercent,
            isNearTerminalVelocity(currentVelocityY)
        );
    }
    
    /**
     * Predict landing time more accurately using physics
     * @param currentHeight Current Y position
     * @param groundHeight Ground Y position
     * @param currentVelocity Current downward velocity
     * @return Estimated ticks until landing
     */
    public static double predictLandingTime(double currentHeight, double groundHeight, double currentVelocity) {
        double distanceToFall = currentHeight - groundHeight;
        
        if (distanceToFall <= 0 || currentVelocity >= 0) {
            return 0; // Already landed or not falling
        }
        
        // Use iterative approach to find when we'll reach the ground
        double tickTime = 0;
        double currentPos = currentHeight;
        double velocity = currentVelocity;
        
        for (int tick = 0; tick < 1000 && currentPos > groundHeight; tick++) {
            // Apply Minecraft physics each tick
            velocity += GRAVITY_ACCELERATION;
            velocity *= AIR_DRAG;
            currentPos += velocity;
            tickTime++;
        }
        
        return tickTime;
    }
    
    /**
     * Check if velocity is near terminal velocity (within 90%)
     */
    public static boolean isNearTerminalVelocity(double velocityY) {
        return Math.abs(velocityY) >= (TERMINAL_VELOCITY * 0.9);
    }
    
    /**
     * Data class containing detailed falling speed information
     */
    public static class FallingSpeedInfo {
        private final double rawVelocityY;
        private final double fallSpeedBlocksPerTick;
        private final double fallSpeedBlocksPerSecond;
        private final double fallSpeedMetersPerSecond;
        private final double totalFallDistance;
        private final double estimatedFallTick;
        private final double theoreticalVelocity;
        private final double terminalVelocityPercent;
        private final boolean nearTerminalVelocity;
        
        public FallingSpeedInfo(double rawVelocityY, double fallSpeedBlocksPerTick, 
                               double fallSpeedBlocksPerSecond, double fallSpeedMetersPerSecond,
                               double totalFallDistance, double estimatedFallTick,
                               double theoreticalVelocity, double terminalVelocityPercent,
                               boolean nearTerminalVelocity) {
            this.rawVelocityY = rawVelocityY;
            this.fallSpeedBlocksPerTick = fallSpeedBlocksPerTick;
            this.fallSpeedBlocksPerSecond = fallSpeedBlocksPerSecond;
            this.fallSpeedMetersPerSecond = fallSpeedMetersPerSecond;
            this.totalFallDistance = totalFallDistance;
            this.estimatedFallTick = estimatedFallTick;
            this.theoreticalVelocity = theoreticalVelocity;
            this.terminalVelocityPercent = terminalVelocityPercent;
            this.nearTerminalVelocity = nearTerminalVelocity;
        }
        
        // Getters
        public double getRawVelocityY() { return rawVelocityY; }
        public double getFallSpeedBlocksPerTick() { return fallSpeedBlocksPerTick; }
        public double getFallSpeedBlocksPerSecond() { return fallSpeedBlocksPerSecond; }
        public double getFallSpeedMetersPerSecond() { return fallSpeedMetersPerSecond; }
        public double getTotalFallDistance() { return totalFallDistance; }
        public double getEstimatedFallTick() { return estimatedFallTick; }
        public double getTheoreticalVelocity() { return theoreticalVelocity; }
        public double getTerminalVelocityPercent() { return terminalVelocityPercent; }
        public boolean isNearTerminalVelocity() { return nearTerminalVelocity; }
        
        @Override
        public String toString() {
            return String.format("FallingSpeed[%.2f b/t, %.2f b/s, %.1f%% terminal, tick %.1f]",
                fallSpeedBlocksPerTick, fallSpeedBlocksPerSecond, terminalVelocityPercent, estimatedFallTick);
        }
    }
}