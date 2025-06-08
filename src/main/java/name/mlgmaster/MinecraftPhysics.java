package name.mlgmaster;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.util.shape.VoxelShape;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive Minecraft physics engine for accurate fall prediction Handles both theoretical
 * calculations and real-world simulation
 */
public class MinecraftPhysics {

    // Core physics constants (authoritative values)
    private static final double GRAVITY_ACCELERATION = -0.08; // blocks per tick per tick
    private static final double AIR_DRAG = 0.98; // velocity multiplier each tick
    private static final double HORIZONTAL_DRAG = 0.91; // horizontal drag when on ground/moving
    private static final double TERMINAL_VELOCITY = 3.92; // theoretical maximum (blocks/tick)
    private static final double MIN_VELOCITY_THRESHOLD = 0.001; // negligible movement threshold
    private static final int MAX_SIMULATION_TICKS = 1000; // safety limit for simulation

    /**
     * Calculate theoretical velocity at a given tick (pure physics) v(t) = (0.98^floor(t) - 1) ×
     * 3.92
     */
    public static double calculateVelocityAtTick(double tickTime) {
        int flooredTick = (int) Math.floor(tickTime);
        return (Math.pow(AIR_DRAG, flooredTick) - 1) * TERMINAL_VELOCITY;
    }

    /**
     * Calculate theoretical distance fallen by a given tick (pure physics) d(t) = 196 - 3.92 × t -
     * 194.04 × 0.98^(t-0.5)
     */
    public static double calculateDistanceFallenAtTick(double tickTime) {
        return 196 - (TERMINAL_VELOCITY * tickTime) - (194.04 * Math.pow(AIR_DRAG, tickTime - 0.5));
    }

    /**
     * Estimate time to fall a given distance using iterative approximation
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
                increment *= 0.5;
            }
        }

        return tickTime;
    }

    /**
     * Simulate realistic player movement with collision detection This accounts for horizontal
     * movement, air resistance, and block collisions
     */
    public static MovementSimulationResult simulatePlayerMovement(MinecraftClient client,
            ClientPlayerEntity player, Vec3d startPosition, Vec3d initialVelocity) {
        Vec3d currentPos = startPosition;
        Vec3d currentVelocity = initialVelocity;
        Box playerHitbox = player.getBoundingBox().offset(startPosition.subtract(player.getPos()));

        List<BlockPos> collidingBlocks = new ArrayList<>();
        List<MovementTick> movementHistory = new ArrayList<>();
        Vec3d startHorizontal = new Vec3d(startPosition.x, 0, startPosition.z);

        for (int tick = 0; tick < MAX_SIMULATION_TICKS; tick++) {
            // Apply Minecraft physics each tick
            currentVelocity = applyPhysicsTick(currentVelocity);

            // Calculate next position
            Vec3d nextPos = currentPos.add(currentVelocity);
            Box nextHitbox = playerHitbox.offset(nextPos.subtract(startPosition));

            // Record this tick's movement
            movementHistory.add(new MovementTick(tick, currentPos, currentVelocity, nextPos));

            // Check for collisions
            List<BlockPos> tickCollisions =
                    checkCollisions(client, playerHitbox, nextHitbox, currentVelocity);

            if (!tickCollisions.isEmpty()) {
                // Collision detected - calculate final results
                collidingBlocks.addAll(tickCollisions);
                Vec3d horizontalDisplacement =
                        calculateHorizontalDisplacement(nextPos, startHorizontal);

                return new MovementSimulationResult(true, nextPos, collidingBlocks, nextHitbox,
                        tick, horizontalDisplacement, movementHistory, currentVelocity);
            }

            // Update position and hitbox for next iteration
            currentPos = nextPos;
            playerHitbox = nextHitbox;

            // Check if movement is negligible
            if (isMovementNegligible(currentVelocity)) {
                break;
            }

            // Safety check for extreme falls
            if (currentPos.y < startPosition.y - 200) {
                break;
            }
        }

        // No collision found
        Vec3d horizontalDisplacement = calculateHorizontalDisplacement(currentPos, startHorizontal);
        return new MovementSimulationResult(false, currentPos, collidingBlocks, playerHitbox,
                MAX_SIMULATION_TICKS, horizontalDisplacement, movementHistory, currentVelocity);
    }

    /**
     * Apply one tick of Minecraft physics to velocity
     */
    private static Vec3d applyPhysicsTick(Vec3d velocity) {
        // Apply gravity to Y velocity
        double newY = velocity.y + GRAVITY_ACCELERATION;

        // Apply drag
        return new Vec3d(velocity.x * HORIZONTAL_DRAG, newY * AIR_DRAG,
                velocity.z * HORIZONTAL_DRAG);
    }

    /**
     * Check for block collisions during movement
     */
    private static List<BlockPos> checkCollisions(MinecraftClient client, Box currentHitbox,
            Box nextHitbox, Vec3d velocity) {
        List<BlockPos> collidingBlocks = new ArrayList<>();
        Box movementBox = currentHitbox.union(nextHitbox);

        int minX = (int) Math.floor(movementBox.minX);
        int minY = (int) Math.floor(movementBox.minY);
        int minZ = (int) Math.floor(movementBox.minZ);
        int maxX = (int) Math.ceil(movementBox.maxX);
        int maxY = (int) Math.ceil(movementBox.maxY);
        int maxZ = (int) Math.ceil(movementBox.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);

                    if (isBlockSolid(client, blockPos)) {
                        VoxelShape blockShape = getBlockCollisionShape(client, blockPos);
                        if (!blockShape.isEmpty()) {
                            Box blockBox = blockShape.getBoundingBox().offset(blockPos);

                            if (doesMovementIntersectBlock(currentHitbox, nextHitbox, blockBox,
                                    velocity)) {
                                collidingBlocks.add(blockPos);
                            }
                        }
                    }
                }
            }
        }

        return collidingBlocks;
    }

    /**
     * Check if movement path intersects with a block
     */
    private static boolean doesMovementIntersectBlock(Box currentHitbox, Box nextHitbox,
            Box blockBox, Vec3d velocity) {
        if (currentHitbox.intersects(blockBox) || nextHitbox.intersects(blockBox)) {
            return true;
        }

        // Sample intermediate positions for fast movement
        double velocityMagnitude = velocity.length();
        if (velocityMagnitude > 1.0) {
            int samples = Math.min(10, (int) Math.ceil(velocityMagnitude * 2));
            for (int i = 1; i < samples; i++) {
                double t = (double) i / samples;
                Vec3d intermediateOffset = velocity.multiply(t);
                Box intermediateHitbox = currentHitbox.offset(intermediateOffset);

                if (intermediateHitbox.intersects(blockBox)) {
                    return true;
                }
            }
        }

        return false;
    }

    // Helper methods
    private static boolean isBlockSolid(MinecraftClient client, BlockPos blockPos) {
        BlockState blockState = client.world.getBlockState(blockPos);
        return !blockState.isAir()
                && !blockState.getCollisionShape(client.world, blockPos).isEmpty();
    }

    private static VoxelShape getBlockCollisionShape(MinecraftClient client, BlockPos blockPos) {
        BlockState blockState = client.world.getBlockState(blockPos);
        return blockState.getCollisionShape(client.world, blockPos);
    }

    private static boolean isMovementNegligible(Vec3d velocity) {
        return Math.abs(velocity.x) < MIN_VELOCITY_THRESHOLD
                && Math.abs(velocity.y) < MIN_VELOCITY_THRESHOLD
                && Math.abs(velocity.z) < MIN_VELOCITY_THRESHOLD;
    }

    private static Vec3d calculateHorizontalDisplacement(Vec3d currentPos, Vec3d startHorizontal) {
        Vec3d currentHorizontal = new Vec3d(currentPos.x, 0, currentPos.z);
        return currentHorizontal.subtract(startHorizontal);
    }

    /**
     * Analyze current falling state with detailed physics information
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

        // Calculate speed in various units
        double speedBlocksPerSecond = Math.abs(currentVelocityY) * 20;
        double speedMetersPerSecond = speedBlocksPerSecond;

        // Calculate terminal velocity approach percentage
        double terminalVelocityPercent = (Math.abs(currentVelocityY) / TERMINAL_VELOCITY) * 100;

        return new FallingSpeedInfo(currentVelocityY, Math.abs(currentVelocityY),
                speedBlocksPerSecond, speedMetersPerSecond, fallDistance, estimatedTick,
                theoreticalVelocity, terminalVelocityPercent,
                isNearTerminalVelocity(currentVelocityY));
    }

    /**
     * Check if velocity is near terminal velocity (within 90%)
     */
    public static boolean isNearTerminalVelocity(double velocityY) {
        return Math.abs(velocityY) >= (TERMINAL_VELOCITY * 0.9);
    }

    // Data classes
    public static class MovementSimulationResult {
        private final boolean hasCollision;
        private final Vec3d finalPosition;
        private final List<BlockPos> collidingBlocks;
        private final Box finalHitbox;
        private final int simulationTicks;
        private final Vec3d horizontalDisplacement;
        private final List<MovementTick> movementHistory;
        private final Vec3d finalVelocity;

        public MovementSimulationResult(boolean hasCollision, Vec3d finalPosition,
                List<BlockPos> collidingBlocks, Box finalHitbox, int simulationTicks,
                Vec3d horizontalDisplacement, List<MovementTick> movementHistory,
                Vec3d finalVelocity) {
            this.hasCollision = hasCollision;
            this.finalPosition = finalPosition;
            this.collidingBlocks = collidingBlocks;
            this.finalHitbox = finalHitbox;
            this.simulationTicks = simulationTicks;
            this.horizontalDisplacement = horizontalDisplacement;
            this.movementHistory = movementHistory;
            this.finalVelocity = finalVelocity;
        }

        // Getters
        public boolean hasCollision() {
            return hasCollision;
        }

        public Vec3d getFinalPosition() {
            return finalPosition;
        }

        public List<BlockPos> getCollidingBlocks() {
            return collidingBlocks;
        }

        public Box getFinalHitbox() {
            return finalHitbox;
        }

        public int getSimulationTicks() {
            return simulationTicks;
        }

        public Vec3d getHorizontalDisplacement() {
            return horizontalDisplacement;
        }

        public List<MovementTick> getMovementHistory() {
            return movementHistory;
        }

        public Vec3d getFinalVelocity() {
            return finalVelocity;
        }
    }

    public static class MovementTick {
        private final int tickNumber;
        private final Vec3d startPosition;
        private final Vec3d velocity;
        private final Vec3d endPosition;

        public MovementTick(int tickNumber, Vec3d startPosition, Vec3d velocity,
                Vec3d endPosition) {
            this.tickNumber = tickNumber;
            this.startPosition = startPosition;
            this.velocity = velocity;
            this.endPosition = endPosition;
        }

        // Getters
        public int getTickNumber() {
            return tickNumber;
        }

        public Vec3d getStartPosition() {
            return startPosition;
        }

        public Vec3d getVelocity() {
            return velocity;
        }

        public Vec3d getEndPosition() {
            return endPosition;
        }
    }

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
                double totalFallDistance, double estimatedFallTick, double theoreticalVelocity,
                double terminalVelocityPercent, boolean nearTerminalVelocity) {
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
        public double getRawVelocityY() {
            return rawVelocityY;
        }

        public double getFallSpeedBlocksPerTick() {
            return fallSpeedBlocksPerTick;
        }

        public double getFallSpeedBlocksPerSecond() {
            return fallSpeedBlocksPerSecond;
        }

        public double getFallSpeedMetersPerSecond() {
            return fallSpeedMetersPerSecond;
        }

        public double getTotalFallDistance() {
            return totalFallDistance;
        }

        public double getEstimatedFallTick() {
            return estimatedFallTick;
        }

        public double getTheoreticalVelocity() {
            return theoreticalVelocity;
        }

        public double getTerminalVelocityPercent() {
            return terminalVelocityPercent;
        }

        public boolean isNearTerminalVelocity() {
            return nearTerminalVelocity;
        }

        @Override
        public String toString() {
            return String.format("FallingSpeed[%.2f b/t, %.2f b/s, %.1f%% terminal, tick %.1f]",
                    fallSpeedBlocksPerTick, fallSpeedBlocksPerSecond, terminalVelocityPercent,
                    estimatedFallTick);
        }
    }
}

