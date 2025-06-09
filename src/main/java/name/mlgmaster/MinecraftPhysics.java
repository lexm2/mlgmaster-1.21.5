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
 * Comprehensive Minecraft physics engine for accurate fall prediction
 */
public class MinecraftPhysics {

    // Core physics constants (authoritative values)
    private static final double GRAVITY_ACCELERATION = -0.08; // blocks per tick per tick
    private static final double AIR_DRAG = 0.98; // velocity multiplier each tick
    private static final double HORIZONTAL_DRAG = 0.91; // horizontal drag when on ground/moving
    private static final double TERMINAL_VELOCITY = 3.92; // theoretical maximum (blocks/tick)
    private static final int MAX_SIMULATION_TICKS = 1000; // safety limit for simulation

    /**
     * Calculate theoretical velocity at a given tick (pure physics)
     */
    public static double calculateVelocityAtTick(double tickTime) {
        int flooredTick = (int) Math.floor(tickTime);
        return (Math.pow(AIR_DRAG, flooredTick) - 1) * TERMINAL_VELOCITY;
    }

    /**
     * Calculate theoretical distance fallen by a given tick (pure physics)
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
     * Simulate realistic player movement with improved collision detection
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

            // Check for collisions using improved detection
            BlockPos collisionBlock = findFirstCollision(client, currentPos, nextPos, playerHitbox);

            if (collisionBlock != null) {
                // Collision detected - calculate final results
                collidingBlocks.add(collisionBlock);
                Vec3d horizontalDisplacement = calculateHorizontalDisplacement(nextPos, startHorizontal);

                return new MovementSimulationResult(true, nextPos, collidingBlocks, nextHitbox,
                        tick, horizontalDisplacement, movementHistory, currentVelocity);
            }

            // Update position and hitbox for next iteration
            currentPos = nextPos;
            playerHitbox = nextHitbox;

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
     * Improved collision detection that finds the first (topmost) block hit
     */
    private static BlockPos findFirstCollision(MinecraftClient client, Vec3d currentPos,
            Vec3d nextPos, Box playerHitbox) {

        // Calculate movement bounds with more precise collision detection
        double minX = Math.min(currentPos.x, nextPos.x) - 0.3; // Player half-width
        double maxX = Math.max(currentPos.x, nextPos.x) + 0.3;
        double minZ = Math.min(currentPos.z, nextPos.z) - 0.3;
        double maxZ = Math.max(currentPos.z, nextPos.z) + 0.3;
        double minY = Math.min(currentPos.y, nextPos.y) - 1.8; // Player height
        double maxY = Math.max(currentPos.y, nextPos.y);

        // Convert to block coordinates
        int blockMinX = (int) Math.floor(minX);
        int blockMaxX = (int) Math.ceil(maxX);
        int blockMinZ = (int) Math.floor(minZ);
        int blockMaxZ = (int) Math.ceil(maxZ);
        int blockMinY = (int) Math.floor(minY);
        int blockMaxY = (int) Math.ceil(maxY);

        BlockPos firstCollision = null;

        // For falling players, we need to find the highest block that actually stops
        // their fall
        // Check from bottom up to find the first solid surface they'll land on
        for (int y = blockMinY; y <= blockMaxY; y++) {
            for (int x = blockMinX; x <= blockMaxX; x++) {
                for (int z = blockMinZ; z <= blockMaxZ; z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);

                    if (isBlockSolid(client, blockPos)) {
                        if (wouldPlayerLandOnBlock(currentPos, nextPos, blockPos)) {
                            // This is a valid landing - check if it's the first one we'll hit
                            if (firstCollision == null || blockPos.getY() > firstCollision.getY()) {
                                firstCollision = blockPos;

                                MLGMaster.LOGGER.debug(
                                        "COLLISION CANDIDATE: Block {} at Y={}, player falling from Y={} to Y={}",
                                        blockPos, blockPos.getY(), currentPos.y, nextPos.y);
                            }
                        }
                    }
                }
            }
        }

        return firstCollision;
    }

    /**
     * More accurate collision check - specifically for landing scenarios
     */
    private static boolean wouldPlayerLandOnBlock(Vec3d currentPos, Vec3d nextPos, BlockPos blockPos) {
        // Player dimensions
        double playerWidth = 0.6;
        double playerHeight = 1.8;

        // Block bounds
        double blockTop = blockPos.getY() + 1.0;
        double blockBottom = blockPos.getY();

        // Only consider this a landing collision if:
        // 1. Player is falling (nextPos.y < currentPos.y)
        // 2. Player will pass through or land on the top surface of the block
        // 3. Player's horizontal position overlaps with the block

        if (nextPos.y >= currentPos.y) {
            return false; // Not falling
        }

        // Check if player's feet will be at or below block top but above block bottom
        double playerFeetY = nextPos.y - playerHeight;
        if (playerFeetY > blockTop || nextPos.y < blockBottom) {
            return false; // Player passes above or below this block
        }

        // Check horizontal overlap
        double playerMinX = nextPos.x - playerWidth / 2;
        double playerMaxX = nextPos.x + playerWidth / 2;
        double playerMinZ = nextPos.z - playerWidth / 2;
        double playerMaxZ = nextPos.z + playerWidth / 2;

        double blockMinX = blockPos.getX();
        double blockMaxX = blockPos.getX() + 1.0;
        double blockMinZ = blockPos.getZ();
        double blockMaxZ = blockPos.getZ() + 1.0;

        boolean xOverlap = playerMaxX > blockMinX && playerMinX < blockMaxX;
        boolean zOverlap = playerMaxZ > blockMinZ && playerMinZ < blockMaxZ;

        // Additional check: ensure player is actually moving toward this block's top
        // surface
        boolean movingTowardBlock = currentPos.y > blockTop && nextPos.y <= blockTop + playerHeight;

        return xOverlap && zOverlap && movingTowardBlock;
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

    // Helper methods
    private static boolean isBlockSolid(MinecraftClient client, BlockPos blockPos) {
        try {
            BlockState blockState = client.world.getBlockState(blockPos);
            return !blockState.isAir()
                    && !blockState.getCollisionShape(client.world, blockPos).isEmpty();
        } catch (Exception e) {
            return false; // Assume non-solid if we can't check
        }
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

    // Data classes remain the same...
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
