package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.List;

/**
 * Landing result class that works with the new physics system
 * Replaces the old LandingPlacmentPredictor.HitboxLandingResult
 */
public class HitboxLandingResult {
    private final BlockPos primaryLandingBlock;
    private final Vec3d landingPosition;
    private final List<BlockPos> allHitBlocks;
    private final Vec3d lookTarget;
    private final Box finalHitbox;
    private final SafeLandingBlockChecker.SafetyResult safetyResult;
    private final int simulationSteps;
    private final Vec3d horizontalDisplacement;
    private final MinecraftPhysics.MovementSimulationResult physicsSimulation;
    
    public HitboxLandingResult(MinecraftPhysics.MovementSimulationResult physicsSimulation,
                               MinecraftClient client, ClientPlayerEntity player, Vec3d currentPos) {
        this.physicsSimulation = physicsSimulation;
        this.landingPosition = physicsSimulation.getFinalPosition();
        this.allHitBlocks = physicsSimulation.getCollidingBlocks();
        this.finalHitbox = physicsSimulation.getFinalHitbox();
        this.simulationSteps = physicsSimulation.getSimulationTicks();
        this.horizontalDisplacement = physicsSimulation.getHorizontalDisplacement();
        
        // Find the highest landing block
        this.primaryLandingBlock = findHighestBlock(allHitBlocks);
        
        // Set look target to water placement position
        this.lookTarget = primaryLandingBlock != null ? Vec3d.ofCenter(primaryLandingBlock.up()) : null;
        
        // Check landing safety
        this.safetyResult = primaryLandingBlock != null ? 
            SafeLandingBlockChecker.checkLandingSafety(client, player, primaryLandingBlock, currentPos) :
            new SafeLandingBlockChecker.SafetyResult(false, "No landing block found");
    }
    
    // Static factory method for creating results from physics simulation
    public static HitboxLandingResult fromPhysicsSimulation(MinecraftPhysics.MovementSimulationResult simulation,
                                                             MinecraftClient client, 
                                                             ClientPlayerEntity player, 
                                                             Vec3d currentPos) {
        return new HitboxLandingResult(simulation, client, player, currentPos);
    }
    
    // Core getters - maintain compatibility with existing code
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
    
    // Enhanced getters - provide access to new physics data
    public MinecraftPhysics.MovementSimulationResult getPhysicsSimulation() {
        return physicsSimulation;
    }
    
    public Vec3d getFinalVelocity() {
        return physicsSimulation.getFinalVelocity();
    }
    
    public List<MinecraftPhysics.MovementTick> getMovementHistory() {
        return physicsSimulation.getMovementHistory();
    }
    
    public boolean hasCollision() {
        return physicsSimulation.hasCollision();
    }
    
    // Analysis methods
    public double getHorizontalDistanceTraveled() {
        return horizontalDisplacement.length();
    }
    
    public double getVerticalDistanceFallen(Vec3d startPos) {
        return startPos.y - landingPosition.y;
    }
    
    public double getTimeToLandingSeconds() {
        return simulationSteps / 20.0; // Convert ticks to seconds
    }
    
    // Helper methods
    private BlockPos findHighestBlock(List<BlockPos> blocks) {
        if (blocks.isEmpty()) {
            return null;
        }
        
        BlockPos highest = blocks.get(0);
        int highestY = highest.getY();
        
        for (BlockPos block : blocks) {
            if (block.getY() > highestY) {
                highestY = block.getY();
                highest = block;
            }
        }
        
        return highest;
    }
    
    @Override
    public String toString() {
        return String.format("HitboxLandingResult[landingBlock=%s, position=%s, steps=%d, safe=%s, displacement=%s]",
            primaryLandingBlock, landingPosition, simulationSteps, isSafeLanding(), horizontalDisplacement);
    }
}