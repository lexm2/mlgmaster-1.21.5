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
    
    public static class HitboxLandingResult {
        private final BlockPos primaryLandingBlock;
        private final Vec3d landingPosition;
        private final List<BlockPos> allHitBlocks;
        private final Vec3d lookTarget;
        private final Box finalHitbox;
        private final SafeLandingBlockChecker.SafetyResult safetyResult;
        
        public HitboxLandingResult(BlockPos primaryLandingBlock, Vec3d landingPosition, 
                                  List<BlockPos> allHitBlocks, Vec3d lookTarget, Box finalHitbox, 
                                  SafeLandingBlockChecker.SafetyResult safetyResult) {
            this.primaryLandingBlock = primaryLandingBlock;
            this.landingPosition = landingPosition;
            this.allHitBlocks = allHitBlocks;
            this.lookTarget = lookTarget;
            this.finalHitbox = finalHitbox;
            this.safetyResult = safetyResult;
        }
        
        public BlockPos getPrimaryLandingBlock() { return primaryLandingBlock; }
        public Vec3d getLandingPosition() { return landingPosition; }
        public List<BlockPos> getAllHitBlocks() { return allHitBlocks; }
        public Vec3d getLookTarget() { return lookTarget; }
        public Box getOriginalHitbox() { return finalHitbox; }
        public SafeLandingBlockChecker.SafetyResult getSafetyResult() { return safetyResult; }
        public boolean isSafeLanding() { return safetyResult.isSafe(); }
    }
    
    public static HitboxLandingResult predictHitboxLanding(MinecraftClient client, ClientPlayerEntity player, Vec3d currentPos, Vec3d velocity) {
        MLGMaster.LOGGER.info("Starting hitbox-aware landing prediction...");
        MLGMaster.LOGGER.info("Current pos: {} | Velocity: {}", currentPos, velocity);
        
        // Get player hitbox and dimensions
        Box playerHitbox = player.getBoundingBox();
        double hitboxWidth = playerHitbox.getLengthX();
        double hitboxDepth = playerHitbox.getLengthZ();
        double hitboxHeight = playerHitbox.getLengthY();
        
        MLGMaster.LOGGER.info("Player hitbox dimensions: width={} | depth={} | height={}", 
            hitboxWidth, hitboxDepth, hitboxHeight);
        MLGMaster.LOGGER.info("Hitbox bounds: min=({}, {}, {}) max=({}, {}, {})", 
            playerHitbox.minX, playerHitbox.minY, playerHitbox.minZ,
            playerHitbox.maxX, playerHitbox.maxY, playerHitbox.maxZ);
        
        // Perform hitbox-based collision simulation
        CollisionResult collision = simulateHitboxCollision(client, player, currentPos, velocity, playerHitbox);
        
        if (!collision.hasCollision()) {
            MLGMaster.LOGGER.warn("No landing collision found for hitbox");
            return null;
        }
        
        MLGMaster.LOGGER.info("Collision found at position: {}", collision.collisionPosition);
        MLGMaster.LOGGER.info("Colliding blocks: {}", collision.collidingBlocks.size());
        
        // Choose the best landing block (highest Y)
        BlockPos bestLandingBlock = chooseBestLandingBlock(collision.collidingBlocks);
        
        // Check safety of the primary landing block
        SafeLandingBlockChecker.SafetyResult safetyResult = 
            SafeLandingBlockChecker.checkLandingSafety(client, player, bestLandingBlock, currentPos);
        
        MLGMaster.LOGGER.info("Landing safety check: {} - {}", 
            safetyResult.isSafe() ? "SAFE" : "UNSAFE", safetyResult.getReason());
        
        // Sort all hit blocks by height (highest first)
        List<BlockPos> sortedHitBlocks = new ArrayList<>(collision.collidingBlocks);
        sortedHitBlocks.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        
        MLGMaster.LOGGER.info("All hit blocks (sorted by height):");
        for (int i = 0; i < sortedHitBlocks.size(); i++) {
            BlockPos block = sortedHitBlocks.get(i);
            MLGMaster.LOGGER.info("  {}. {} (Y={})", i + 1, block, block.getY());
        }
        
        // Calculate optimal look target (center of the water placement location on the highest block)
        Vec3d waterPlacementCenter = Vec3d.ofCenter(bestLandingBlock.up());
        
        MLGMaster.LOGGER.info("Final selection:");
        MLGMaster.LOGGER.info("  Primary landing: {} (Y={})", bestLandingBlock, bestLandingBlock.getY());
        MLGMaster.LOGGER.info("  Water target: {}", waterPlacementCenter);
        MLGMaster.LOGGER.info("  Safety: {}", safetyResult.isSafe() ? "SAFE" : "UNSAFE");
        
        return new HitboxLandingResult(bestLandingBlock, collision.collisionPosition, 
            sortedHitBlocks, waterPlacementCenter, collision.finalHitbox, safetyResult);
    }
    
    private static CollisionResult simulateHitboxCollision(MinecraftClient client, ClientPlayerEntity player, 
                                                          Vec3d startPos, Vec3d velocity, Box originalHitbox) {
        MLGMaster.LOGGER.info("Starting hitbox collision simulation");
        MLGMaster.LOGGER.info("Start position: {}", startPos);
        MLGMaster.LOGGER.info("Initial velocity: {}", velocity);
        
        // Physics constants
        final double GRAVITY = 0.08; // Minecraft gravity
        final double DRAG = 0.98; // Air resistance
        final double MIN_VELOCITY = 0.001; // Stop simulation when movement is negligible
        final int MAX_STEPS = 1000; // Prevent infinite loops
        
        Vec3d currentPos = startPos;
        Vec3d currentVelocity = velocity;
        Box currentHitbox = originalHitbox.offset(startPos.subtract(originalHitbox.getCenter()));
        
        List<BlockPos> collidingBlocks = new ArrayList<>();
        
        for (int step = 0; step < MAX_STEPS; step++) {
            // Apply gravity
            currentVelocity = currentVelocity.add(0, -GRAVITY, 0);
            
            // Apply drag
            currentVelocity = currentVelocity.multiply(DRAG, DRAG, DRAG);
            
            // Calculate next position
            Vec3d nextPos = currentPos.add(currentVelocity);
            Box nextHitbox = currentHitbox.offset(currentVelocity);
            
            MLGMaster.LOGGER.info("Step {}: pos={} vel={} hitbox_center={}", 
                step, nextPos, currentVelocity, nextHitbox.getCenter());
            
            // Check for collisions with blocks
            List<BlockPos> stepCollisions = checkHitboxCollisions(client, currentHitbox, nextHitbox);
            
            if (!stepCollisions.isEmpty()) {
                MLGMaster.LOGGER.info("Collision detected at step {} with {} blocks", step, stepCollisions.size());
                
                // Add all colliding blocks
                for (BlockPos blockPos : stepCollisions) {
                    if (!collidingBlocks.contains(blockPos)) {
                        collidingBlocks.add(blockPos);
                        MLGMaster.LOGGER.info("  Colliding with block: {} (Y={})", blockPos, blockPos.getY());
                    }
                }
                
                // Return collision result
                return new CollisionResult(true, nextPos, collidingBlocks, nextHitbox);
            }
            
            // Update position and hitbox for next iteration
            currentPos = nextPos;
            currentHitbox = nextHitbox;
            
            // Stop if velocity becomes negligible
            if (Math.abs(currentVelocity.x) < MIN_VELOCITY && 
                Math.abs(currentVelocity.y) < MIN_VELOCITY && 
                Math.abs(currentVelocity.z) < MIN_VELOCITY) {
                MLGMaster.LOGGER.info("Simulation stopped: velocity became negligible at step {}", step);
                break;
            }
        }
        
        MLGMaster.LOGGER.warn("No collision found after {} steps", MAX_STEPS);
        return new CollisionResult(false, currentPos, collidingBlocks, currentHitbox);
    }
    
    private static List<BlockPos> checkHitboxCollisions(MinecraftClient client, Box currentHitbox, Box nextHitbox) {
        List<BlockPos> collidingBlocks = new ArrayList<>();
        
        // Create a combined box that encompasses both current and next positions
        Box movementBox = currentHitbox.union(nextHitbox);
        
        // Get all block positions that could intersect with our movement
        int minX = (int) Math.floor(movementBox.minX);
        int minY = (int) Math.floor(movementBox.minY);
        int minZ = (int) Math.floor(movementBox.minZ);
        int maxX = (int) Math.ceil(movementBox.maxX);
        int maxY = (int) Math.ceil(movementBox.maxY);
        int maxZ = (int) Math.ceil(movementBox.maxZ);
        
        MLGMaster.LOGGER.info("Checking collisions in area: ({},{},{}) to ({},{},{})", 
            minX, minY, minZ, maxX, maxY, maxZ);
        
        // Check each block position for collision
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    BlockState blockState = client.world.getBlockState(blockPos);
                    
                    // Skip air blocks
                    if (blockState.isAir()) {
                        continue;
                    }
                    
                    // Get the collision shape of the block
                    VoxelShape blockShape = blockState.getCollisionShape(client.world, blockPos);
                    if (blockShape.isEmpty()) {
                        continue;
                    }
                    
                    // Convert voxel shape to box for intersection testing
                    Box blockBox = blockShape.getBoundingBox().offset(blockPos);
                    
                    // Check if the next hitbox intersects with this block
                    if (nextHitbox.intersects(blockBox)) {
                        collidingBlocks.add(blockPos);
                        MLGMaster.LOGGER.info("  Collision detected with block {} (type: {})", 
                            blockPos, blockState.getBlock());
                    }
                }
            }
        }
        
        return collidingBlocks;
    }
    
    private static BlockPos chooseBestLandingBlock(List<BlockPos> collidingBlocks) {
        MLGMaster.LOGGER.info("Choosing best landing from {} candidates:", collidingBlocks.size());
        
        if (collidingBlocks.isEmpty()) {
            return null;
        }
        
        // Find the block with the highest Y value
        BlockPos best = collidingBlocks.get(0);
        int highestY = best.getY();
        
        MLGMaster.LOGGER.info("Initial candidate: {} (Y={})", best, highestY);
        
        for (BlockPos block : collidingBlocks) {
            int currentY = block.getY();
            MLGMaster.LOGGER.info("Comparing: {} (Y={}) vs current highest Y={}", 
                block, currentY, highestY);
            
            if (currentY > highestY) {
                highestY = currentY;
                best = block;
                MLGMaster.LOGGER.info("  New highest: {} (Y={})", block, currentY);
            }
        }
        
        MLGMaster.LOGGER.info("Final selection: Block {} (Y={})", best, highestY);
        return best;
    }
    
    private static class CollisionResult {
        final boolean hasCollision;
        final Vec3d collisionPosition;
        final List<BlockPos> collidingBlocks;
        final Box finalHitbox;
        
        CollisionResult(boolean hasCollision, Vec3d collisionPosition, List<BlockPos> collidingBlocks, Box finalHitbox) {
            this.hasCollision = hasCollision;
            this.collisionPosition = collisionPosition;
            this.collidingBlocks = collidingBlocks;
            this.finalHitbox = finalHitbox;
        }
        
        boolean hasCollision() { return hasCollision; }
    }
}
