package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class FallPredictor {
    
    public static class FallPrediction {
        private final boolean willLand;
        private final int ticksToImpact;
        private final Vec3d impactPosition;
        private final BlockPos landingBlock;
        private final Vec3d optimalWaterPosition;
        private final int ticksToPlaceWater;
        private final boolean shouldPlaceNow;
        private final SafeLandingBlockChecker.SafetyResult safetyResult;
        
        public FallPrediction(boolean willLand, int ticksToImpact, Vec3d impactPosition, 
                             BlockPos landingBlock, Vec3d optimalWaterPosition, 
                             int ticksToPlaceWater, boolean shouldPlaceNow,
                             SafeLandingBlockChecker.SafetyResult safetyResult) {
            this.willLand = willLand;
            this.ticksToImpact = ticksToImpact;
            this.impactPosition = impactPosition;
            this.landingBlock = landingBlock;
            this.optimalWaterPosition = optimalWaterPosition;
            this.ticksToPlaceWater = ticksToPlaceWater;
            this.shouldPlaceNow = shouldPlaceNow;
            this.safetyResult = safetyResult;
        }
        
        public boolean willLand() { return willLand; }
        public int getTicksToImpact() { return ticksToImpact; }
        public Vec3d getImpactPosition() { return impactPosition; }
        public BlockPos getLandingBlock() { return landingBlock; }
        public Vec3d getOptimalWaterPosition() { return optimalWaterPosition; }
        public int getTicksToPlaceWater() { return ticksToPlaceWater; }
        public boolean shouldPlaceNow() { return shouldPlaceNow && !safetyResult.isSafe(); }
        public SafeLandingBlockChecker.SafetyResult getSafetyResult() { return safetyResult; }
        public boolean isSafeLanding() { return safetyResult != null && safetyResult.isSafe(); }
    }
    
    public static FallPrediction predictHitboxFall(MinecraftClient client, ClientPlayerEntity player) {
        Vec3d currentPos = player.getPos();
        Vec3d velocity = player.getVelocity();
        
        MLGMaster.LOGGER.info("Starting hitbox fall prediction from {} with velocity {}", currentPos, velocity);
        
        if (velocity.y >= 0) {
            return new FallPrediction(false, -1, null, null, null, -1, false, 
                new SafeLandingBlockChecker.SafetyResult(false, "Not falling"));
        }
        
        // Use shared physics simulation
        PhysicsSimulator.SimulationResult simulation = PhysicsSimulator.simulateHitboxFall(
            client, player, currentPos, velocity);
        
        if (!simulation.foundImpact()) {
            return new FallPrediction(false, -1, null, null, null, -1, false,
                new SafeLandingBlockChecker.SafetyResult(false, "No ground found"));
        }
        
        PhysicsSimulator.SimulationStep impactStep = simulation.getImpactStep();
        BlockPos landingBlock = PhysicsSimulator.findBestLandingBlock(client, impactStep.getPosition());
        
        // Check safety of the landing block
        SafeLandingBlockChecker.SafetyResult safetyResult = 
            SafeLandingBlockChecker.checkLandingSafety(client, player, landingBlock, currentPos);
        
        MLGMaster.LOGGER.info("Predicted hitbox impact at tick {} | Position: {} | Landing: {} | Safety: {}", 
            impactStep.getTick(), impactStep.getPosition(), landingBlock, 
            safetyResult.isSafe() ? "SAFE" : "UNSAFE");
        
        WaterPlacementResult placement = calculateOptimalWaterPlacement(
            client, currentPos, impactStep.getPosition(), landingBlock, 
            impactStep.getTick(), safetyResult);
        
        return new FallPrediction(
            true, impactStep.getTick(), impactStep.getPosition(), landingBlock, 
            placement.waterPosition, placement.placementTick, placement.shouldPlaceNow, safetyResult
        );
    }
    
    private static class WaterPlacementResult {
        final Vec3d waterPosition;
        final int placementTick;
        final boolean shouldPlaceNow;
        
        WaterPlacementResult(Vec3d waterPosition, int placementTick, boolean shouldPlaceNow) {
            this.waterPosition = waterPosition;
            this.placementTick = placementTick;
            this.shouldPlaceNow = shouldPlaceNow;
        }
    }
    
    private static WaterPlacementResult calculateOptimalWaterPlacement(
            MinecraftClient client, Vec3d currentPos, Vec3d impactPos, BlockPos landingBlock, 
            int impactTick, SafeLandingBlockChecker.SafetyResult safetyResult) {
        
        // If landing is safe, don't place water
        if (safetyResult.isSafe()) {
            MLGMaster.LOGGER.info("Landing is safe ({}), skipping water placement", safetyResult.getReason());
            return new WaterPlacementResult(null, -1, false);
        }
        
        // Calculate where to place water (on top of landing block)
        BlockPos waterBlock = landingBlock.up();
        Vec3d waterCenter = Vec3d.ofCenter(waterBlock);
        
        // Calculate when to place water based on water flow mechanics
        // Water needs time to flow and create a safe landing area
        // Place water 10-15 ticks before impact to allow flow time
        int safetyBuffer = Math.min(15, Math.max(5, impactTick / 3));
        int placementTick = impactTick - safetyBuffer;
        
        // Should we place now? (with some prediction buffer)
        boolean shouldPlaceNow = placementTick <= 2; // Place if we're within 2 ticks of optimal time
        
        MLGMaster.LOGGER.info("Water placement: block={} | center={} | place_tick={} | impact_tick={} | buffer={} | place_now={}", 
            waterBlock, waterCenter, placementTick, impactTick, safetyBuffer, shouldPlaceNow);
        
        return new WaterPlacementResult(waterCenter, placementTick, shouldPlaceNow);
    }
}
