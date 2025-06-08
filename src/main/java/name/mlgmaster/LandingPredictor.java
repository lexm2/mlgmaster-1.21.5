package name.mlgmaster;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

public class LandingPredictor {
    // TODO: use physics class
    // Physics constants matching Minecraft
    private static final double GRAVITY = 0.08;
    private static final double AIR_DRAG = 0.98;
    private static final double HORIZONTAL_DRAG = 0.91;
    private static final double MIN_VELOCITY = 0.001;
    private static final int MAX_SIMULATION_STEPS = 1000;

    public static class HitboxLandingResult {
        private final BlockPos primaryLandingBlock;
        private final Vec3d landingPosition;
        private final List<BlockPos> allHitBlocks;
        private final Vec3d lookTarget;
        private final Box finalHitbox;
        private final SafeLandingBlockChecker.SafetyResult safetyResult;
        private final int simulationSteps;
        private final Vec3d horizontalDisplacement;

        public HitboxLandingResult(BlockPos primaryLandingBlock, Vec3d landingPosition,
                List<BlockPos> allHitBlocks, Vec3d lookTarget, Box finalHitbox,
                SafeLandingBlockChecker.SafetyResult safetyResult, int simulationSteps,
                Vec3d horizontalDisplacement) {
            this.primaryLandingBlock = primaryLandingBlock;
            this.landingPosition = landingPosition;
            this.allHitBlocks = allHitBlocks;
            this.lookTarget = lookTarget;
            this.finalHitbox = finalHitbox;
            this.safetyResult = safetyResult;
            this.simulationSteps = simulationSteps;
            this.horizontalDisplacement = horizontalDisplacement;
        }

        public BlockPos getPrimaryLandingBlock() {
            return primaryLandingBlock;
        }

        public Vec3d getLandingPosition() {
            return landingPosition;
        }

        public List<BlockPos> getAllHitBlocks() {
            return allHitBlocks;
        }

        public Vec3d getLookTarget() {
            return lookTarget;
        }

        public Box getOriginalHitbox() {
            return finalHitbox;
        }

        public SafeLandingBlockChecker.SafetyResult getSafetyResult() {
            return safetyResult;
        }

        public boolean isSafeLanding() {
            return safetyResult.isSafe();
        }

        public int getSimulationSteps() {
            return simulationSteps;
        }

        public Vec3d getHorizontalDisplacement() {
            return horizontalDisplacement;
        }
    }

    public static HitboxLandingResult predictHitboxLanding(MinecraftClient client,
            ClientPlayerEntity player, Vec3d currentPos, Vec3d velocity) {

        Box playerHitbox = player.getBoundingBox();
        CollisionResult collision =
                simulateMovementWithCollision(client, player, currentPos, velocity, playerHitbox);

        if (!collision.hasCollision()) {
            return null;
        }

        BlockPos bestLandingBlock = chooseBestLandingBlock(collision.collidingBlocks);
        SafeLandingBlockChecker.SafetyResult safetyResult = SafeLandingBlockChecker
                .checkLandingSafety(client, player, bestLandingBlock, currentPos);

        List<BlockPos> sortedHitBlocks = new ArrayList<>(collision.collidingBlocks);
        sortedHitBlocks.sort((a, b) -> Integer.compare(b.getY(), a.getY()));

        Vec3d waterPlacementCenter = Vec3d.ofCenter(bestLandingBlock.up());

        return new HitboxLandingResult(bestLandingBlock, collision.collisionPosition,
                sortedHitBlocks, waterPlacementCenter, collision.finalHitbox, safetyResult,
                collision.simulationSteps, collision.horizontalDisplacement);
    }

    private static CollisionResult simulateMovementWithCollision(MinecraftClient client,
            ClientPlayerEntity player, Vec3d startPos, Vec3d velocity, Box originalHitbox) {
        Vec3d currentPos = startPos;
        Vec3d currentVelocity = velocity;
        Vec3d startHorizontalPos = new Vec3d(startPos.x, 0, startPos.z);

        // Center the hitbox on the starting position
        Box currentHitbox = originalHitbox.offset(startPos.subtract(originalHitbox.getCenter()));

        List<BlockPos> collidingBlocks = new ArrayList<>();

        for (int step = 0; step < MAX_SIMULATION_STEPS; step++) {
            // Apply gravity to vertical velocity
            currentVelocity =
                    new Vec3d(currentVelocity.x, currentVelocity.y - GRAVITY, currentVelocity.z);

            // Apply different drag for horizontal and vertical movement
            currentVelocity = new Vec3d(currentVelocity.x * HORIZONTAL_DRAG,
                    currentVelocity.y * AIR_DRAG, currentVelocity.z * HORIZONTAL_DRAG);

            // Calculate next position with full 3D movement
            Vec3d nextPos = currentPos.add(currentVelocity);
            Box nextHitbox = currentHitbox.offset(currentVelocity);

            // Check for block collisions along the movement path
            List<BlockPos> stepCollisions =
                    checkMovementCollisions(client, currentHitbox, nextHitbox, currentVelocity);

            if (!stepCollisions.isEmpty()) {
                // Calculate horizontal displacement from start to collision point
                Vec3d currentHorizontalPos = new Vec3d(nextPos.x, 0, nextPos.z);
                Vec3d horizontalDisplacement = currentHorizontalPos.subtract(startHorizontalPos);

                // Add all colliding blocks
                for (BlockPos blockPos : stepCollisions) {
                    if (!collidingBlocks.contains(blockPos)) {
                        collidingBlocks.add(blockPos);
                    }
                }

                return new CollisionResult(true, nextPos, collidingBlocks, nextHitbox, step,
                        horizontalDisplacement);
            }

            // Update position and hitbox
            currentPos = nextPos;
            currentHitbox = nextHitbox;

            // Check if movement has become negligible
            if (isMovementNegligible(currentVelocity)) {
                MLGMaster.LOGGER.info("Movement became negligible at step {}", step);
                break;
            }

            // Safety check - if falling too far below start, stop simulation
            if (currentPos.y < startPos.y - 100) {
                MLGMaster.LOGGER.warn("Fell too far, stopping simulation at step {}", step);
                break;
            }
        }

        // Calculate final horizontal displacement even if no collision
        Vec3d finalHorizontalPos = new Vec3d(currentPos.x, 0, currentPos.z);
        Vec3d horizontalDisplacement = finalHorizontalPos.subtract(startHorizontalPos);

        return new CollisionResult(false, currentPos, collidingBlocks, currentHitbox,
                MAX_SIMULATION_STEPS, horizontalDisplacement);
    }

    private static List<BlockPos> checkMovementCollisions(MinecraftClient client, Box currentHitbox,
            Box nextHitbox, Vec3d velocity) {
        List<BlockPos> collidingBlocks = new ArrayList<>();

        Box movementBox = currentHitbox.union(nextHitbox);

        int minX = (int) Math.floor(movementBox.minX);
        int minY = (int) Math.floor(movementBox.minY);
        int minZ = (int) Math.floor(movementBox.minZ);
        int maxX = (int) Math.ceil(movementBox.maxX);
        int maxY = (int) Math.ceil(movementBox.maxY);
        int maxZ = (int) Math.ceil(movementBox.maxZ);

        // Check each potential block position
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);

                    if (isBlockSolid(client, blockPos)) {
                        // Get precise collision shape
                        VoxelShape blockShape = getBlockCollisionShape(client, blockPos);
                        if (!blockShape.isEmpty()) {
                            Box blockBox = blockShape.getBoundingBox().offset(blockPos);

                            // Check if movement path intersects this block
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

    private static boolean doesMovementIntersectBlock(Box currentHitbox, Box nextHitbox,
            Box blockBox, Vec3d velocity) {
        if (currentHitbox.intersects(blockBox) || nextHitbox.intersects(blockBox)) {
            return true;
        }

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
        return Math.abs(velocity.x) < MIN_VELOCITY && Math.abs(velocity.y) < MIN_VELOCITY
                && Math.abs(velocity.z) < MIN_VELOCITY;
    }

    private static BlockPos chooseBestLandingBlock(List<BlockPos> collidingBlocks) {
        if (collidingBlocks.isEmpty()) {
            return null;
        }

        BlockPos best = collidingBlocks.get(0);
        int highestY = best.getY();

        for (BlockPos block : collidingBlocks) {
            if (block.getY() > highestY) {
                highestY = block.getY();
                best = block;
            }
        }

        return best;
    }

    private static class CollisionResult {
        final boolean hasCollision;
        final Vec3d collisionPosition;
        final List<BlockPos> collidingBlocks;
        final Box finalHitbox;
        final int simulationSteps;
        final Vec3d horizontalDisplacement;

        CollisionResult(boolean hasCollision, Vec3d collisionPosition,
                List<BlockPos> collidingBlocks, Box finalHitbox, int simulationSteps,
                Vec3d horizontalDisplacement) {
            this.hasCollision = hasCollision;
            this.collisionPosition = collisionPosition;
            this.collidingBlocks = collidingBlocks;
            this.finalHitbox = finalHitbox;
            this.simulationSteps = simulationSteps;
            this.horizontalDisplacement = horizontalDisplacement;
        }

        boolean hasCollision() {
            return hasCollision;
        }
    }
}
