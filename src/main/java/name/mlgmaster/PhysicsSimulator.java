package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class PhysicsSimulator {
    
    // Minecraft physics constants
    private static final double GRAVITY = -0.08;
    private static final double AIR_RESISTANCE = 0.98;
    private static final int MAX_SIMULATION_TICKS = 200;
    private static final double MAX_FALL_DISTANCE = 100.0;
    
    public static class SimulationResult {
        private final List<SimulationStep> steps;
        private final SimulationStep impactStep;
        private final boolean foundImpact;
        
        public SimulationResult(List<SimulationStep> steps, SimulationStep impactStep, boolean foundImpact) {
            this.steps = steps;
            this.impactStep = impactStep;
            this.foundImpact = foundImpact;
        }
        
        public List<SimulationStep> getSteps() { return steps; }
        public SimulationStep getImpactStep() { return impactStep; }
        public boolean foundImpact() { return foundImpact; }
        public int getTicksToImpact() { return foundImpact ? impactStep.tick : -1; }
    }
    
    public static class SimulationStep {
        private final int tick;
        private final Vec3d position;
        private final Vec3d velocity;
        private final BlockPos blockBelow;
        private final boolean hasCollision;
        private final BlockHitResult collision;
        
        public SimulationStep(int tick, Vec3d position, Vec3d velocity, BlockPos blockBelow, 
                             boolean hasCollision, BlockHitResult collision) {
            this.tick = tick;
            this.position = position;
            this.velocity = velocity;
            this.blockBelow = blockBelow;
            this.hasCollision = hasCollision;
            this.collision = collision;
        }
        
        public int getTick() { return tick; }
        public Vec3d getPosition() { return position; }
        public Vec3d getVelocity() { return velocity; }
        public BlockPos getBlockBelow() { return blockBelow; }
        public boolean hasCollision() { return hasCollision; }
        public BlockHitResult getCollision() { return collision; }
    }
    
    /**
     * Simulate physics for a single point with collision detection
     */
    public static SimulationResult simulatePointFall(MinecraftClient client, ClientPlayerEntity player, 
                                                    Vec3d startPos, Vec3d startVelocity,
                                                    Predicate<SimulationStep> collisionChecker) {
        List<SimulationStep> steps = new ArrayList<>();
        Vec3d currentPos = startPos;
        Vec3d currentVel = startVelocity;
        
        MLGMaster.LOGGER.info("🔍 PHYSICS SIM: Starting from {} with velocity {}", startPos, startVelocity);
        
        for (int tick = 0; tick < MAX_SIMULATION_TICKS; tick++) {
            // Apply Minecraft physics
            currentVel = currentVel.add(0, GRAVITY, 0); // Gravity
            currentVel = currentVel.multiply(AIR_RESISTANCE); // Air resistance
            
            Vec3d nextPos = currentPos.add(currentVel);
            
            // Log the position being tested
            if (tick % 10 == 0 || tick < 5) { // Log every 10 ticks for first few and periodically
                MLGMaster.LOGGER.info("📍 PHYSICS SIM Tick {}: Testing position {} → {}", tick, currentPos, nextPos);
            }
            
            // Check for raycast collision
            BlockHitResult hitResult = client.world.raycast(new RaycastContext(
                currentPos, 
                nextPos,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                (Entity) player
            ));
            
            boolean hasRaycastCollision = hitResult.getType() == HitResult.Type.BLOCK;
            
            if (hasRaycastCollision) {
                MLGMaster.LOGGER.info("🎯 RAYCAST HIT at tick {}: Block {} at position {}", 
                    tick, hitResult.getBlockPos(), hitResult.getPos());
            }
            
            // Create simulation step
            BlockPos blockBelow = BlockPos.ofFloored(nextPos);
            SimulationStep step = new SimulationStep(tick, nextPos, currentVel, blockBelow, 
                                                   hasRaycastCollision, hitResult);
            steps.add(step);
            
            // Log block being checked
            if (tick % 10 == 0 || hasRaycastCollision) {
                MLGMaster.LOGGER.info("🧱 BLOCK CHECK Tick {}: Testing block at {} - {}", 
                    tick, blockBelow, isBlockSolid(client, blockBelow) ? "SOLID" : "AIR");
            }
            
            // Check for collision using provided checker
            if (collisionChecker.test(step)) {
                MLGMaster.LOGGER.info("💥 COLLISION DETECTED at tick {} - Position: {} - Block: {}", 
                    tick, nextPos, blockBelow);
                return new SimulationResult(steps, step, true);
            }
            
            currentPos = nextPos;
            
            // Safety check - don't simulate too far down
            if (currentPos.y < startPos.y - MAX_FALL_DISTANCE) {
                MLGMaster.LOGGER.info("⬇️ PHYSICS SIM: Stopped - fell too far (Y: {} → {})", 
                    startPos.y, currentPos.y);
                break;
            }
        }
        
        MLGMaster.LOGGER.info("🔚 PHYSICS SIM: Completed without collision after {} ticks", MAX_SIMULATION_TICKS);
        return new SimulationResult(steps, null, false);
    }
    
    /**
     * Simulate hitbox collision for the entire player
     */
    public static SimulationResult simulateHitboxFall(MinecraftClient client, ClientPlayerEntity player,
                                                     Vec3d startPos, Vec3d startVelocity) {
        double hitboxWidth = 0.6;   // Standard player width
        double hitboxHeight = 1.8;  // Standard player height
        
        MLGMaster.LOGGER.info("🎭 HITBOX SIM: Starting hitbox simulation ({}x{}) from {}", 
            hitboxWidth, hitboxHeight, startPos);
        
        return simulatePointFall(client, player, startPos, startVelocity, step -> {
            boolean collision = checkHitboxCollision(client, step.getPosition(), hitboxWidth, hitboxHeight);
            if (collision) {
                MLGMaster.LOGGER.info("🎯 HITBOX COLLISION at position {}", step.getPosition());
            }
            return collision;
        });
    }
    
    /**
     * Check if a hitbox at the given position collides with solid blocks
     */
    public static boolean checkHitboxCollision(MinecraftClient client, Vec3d pos, double width, double height) {
        double halfWidth = width / 2.0;
        
        // Check the four corners of the hitbox bottom + center
        Vec3d[] checkPoints = {
            new Vec3d(pos.x - halfWidth, pos.y - height, pos.z - halfWidth), // Back-left
            new Vec3d(pos.x + halfWidth, pos.y - height, pos.z - halfWidth), // Back-right
            new Vec3d(pos.x - halfWidth, pos.y - height, pos.z + halfWidth), // Front-left
            new Vec3d(pos.x + halfWidth, pos.y - height, pos.z + halfWidth), // Front-right
            new Vec3d(pos.x, pos.y - height, pos.z) // Center point
        };
        
        String[] pointNames = {"Back-Left", "Back-Right", "Front-Left", "Front-Right", "Center"};
        
        MLGMaster.LOGGER.info("🔲 HITBOX CHECK: Testing {} collision points at Y={:.2f}", 
            checkPoints.length, pos.y - height);
        
        for (int i = 0; i < checkPoints.length; i++) {
            Vec3d point = checkPoints[i];
            BlockPos blockPos = BlockPos.ofFloored(point);
            boolean isSolid = isBlockSolid(client, blockPos);
            
            MLGMaster.LOGGER.info("  📌 Point {}: {} at {} → Block {} = {}", 
                i + 1, pointNames[i], point, blockPos, isSolid ? "SOLID" : "AIR");
            
            if (isSolid) {
                MLGMaster.LOGGER.info("🎯 HITBOX COLLISION FOUND: {} point hit solid block at {}", 
                    pointNames[i], blockPos);
                return true;
            }
        }
        
        MLGMaster.LOGGER.info("✅ HITBOX CHECK: No collisions found");
        return false;
    }
    
    /**
     * Check if a block is solid (not air)
     */
    public static boolean isBlockSolid(MinecraftClient client, BlockPos pos) {
        if (client.world == null) {
            MLGMaster.LOGGER.warn("⚠️ BLOCK CHECK: World is null for position {}", pos);
            return false;
        }
        
        boolean isSolid = !client.world.getBlockState(pos).getBlock().equals(Blocks.AIR);
        String blockType = client.world.getBlockState(pos).getBlock().toString();
        
        // Only log interesting blocks (solid ones or occasionally air)
        if (isSolid) {
            MLGMaster.LOGGER.info("🧱 BLOCK: {} = {} ({})", pos,  "SOLID", blockType);
        }
        
        return isSolid;
    }
    
    /**
     * Find the highest solid block in an area around a position
     */
    public static BlockPos findBestLandingBlock(MinecraftClient client, Vec3d pos) {
        BlockPos centerBlock = BlockPos.ofFloored(pos);
        BlockPos bestBlock = null;
        int highestY = Integer.MIN_VALUE;
        
        MLGMaster.LOGGER.info("🔍 LANDING SEARCH: Looking for highest solid block around {}", centerBlock);
        
        List<BlockPos> checkedBlocks = new ArrayList<>();
        List<BlockPos> solidBlocks = new ArrayList<>();
        
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = centerBlock.getY(); y >= centerBlock.getY() - 5; y--) {
                    BlockPos checkPos = new BlockPos(centerBlock.getX() + x, y, centerBlock.getZ() + z);
                    checkedBlocks.add(checkPos);
                    
                    if (isBlockSolid(client, checkPos)) {
                        solidBlocks.add(checkPos);
                        MLGMaster.LOGGER.info("  ✅ SOLID BLOCK FOUND: {} (Y={})", checkPos, y);
                        
                        if (y > highestY) {
                            highestY = y;
                            bestBlock = checkPos;
                            MLGMaster.LOGGER.info("    🏆 NEW HIGHEST: {} (Y={})", checkPos, y);
                        }
                    }
                }
            }
        }
        
        MLGMaster.LOGGER.info("📊 LANDING SEARCH RESULTS:");
        MLGMaster.LOGGER.info("  📍 Checked {} blocks total", checkedBlocks.size());
        MLGMaster.LOGGER.info("  🧱 Found {} solid blocks: {}", solidBlocks.size(), solidBlocks);
        MLGMaster.LOGGER.info("  🎯 Selected highest: {} (Y={})", bestBlock, bestBlock != null ? bestBlock.getY() : "null");
        
        return bestBlock != null ? bestBlock : centerBlock;
    }
}
