package name.mlgmaster;

import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class MLGPredictionResult {
    private final boolean shouldPlace;
    private final boolean willLand;
    private final HitboxLandingResult landingResult;
    private final BlockPos highestLandingBlock;
    private final Vec3d placementTarget;
    private final double distanceToTarget;
    private final String reason;
    private final SafeLandingBlockChecker.SafetyResult safetyResult;
    private final double placementDistance;
    private final Item targetItem;
    
    public MLGPredictionResult(boolean shouldPlace, boolean willLand, 
                              HitboxLandingResult landingResult,
                              BlockPos highestLandingBlock, Vec3d placementTarget,
                              double distanceToTarget, String reason,
                              SafeLandingBlockChecker.SafetyResult safetyResult,
                              double placementDistance, Item targetItem) {
        this.shouldPlace = shouldPlace;
        this.willLand = willLand;
        this.landingResult = landingResult;
        this.highestLandingBlock = highestLandingBlock;
        this.placementTarget = placementTarget;
        this.distanceToTarget = distanceToTarget;
        this.reason = reason;
        this.safetyResult = safetyResult;
        this.placementDistance = placementDistance;
        this.targetItem = targetItem;
    }
    
    public boolean shouldPlace() { return shouldPlace; }
    public boolean willLand() { return willLand; }
    public HitboxLandingResult getLandingResult() { return landingResult; }
    public BlockPos getHighestLandingBlock() { return highestLandingBlock; }
    public Vec3d getPlacementTarget() { return placementTarget; }
    public double getDistanceToTarget() { return distanceToTarget; }
    public String getReason() { return reason; }
    public SafeLandingBlockChecker.SafetyResult getSafetyResult() { return safetyResult; }
    public double getPlacementDistance() { return placementDistance; }
    public boolean isWithinPlacementDistance() { return distanceToTarget <= placementDistance && distanceToTarget > 0; }
    public Item getTargetItem() { return targetItem; }
    
    @Override
    public String toString() {
        return String.format("MLGResult[shouldPlace=%s, willLand=%s, target=%s, distance=%.1f, reason='%s']",
            shouldPlace, willLand, highestLandingBlock, distanceToTarget, reason);
    }
}
