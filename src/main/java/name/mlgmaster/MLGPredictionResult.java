package name.mlgmaster;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class MLGPredictionResult {
    private final boolean shouldPlace;
    private final boolean willLand;
    private final LandingPredictor.HitboxLandingResult landingResult;
    private final BlockPos highestLandingBlock;
    private final Vec3d waterPlacementTarget;
    private final double distanceToTarget;
    private final String reason;
    private final SafeLandingBlockChecker.SafetyResult safetyResult;
    private final double placementDistance;
    private final boolean isUrgentPlacement;
    
    public MLGPredictionResult(boolean shouldPlace, boolean willLand, 
                              LandingPredictor.HitboxLandingResult landingResult,
                              BlockPos highestLandingBlock, Vec3d waterPlacementTarget,
                              double distanceToTarget, String reason,
                              SafeLandingBlockChecker.SafetyResult safetyResult,
                              double placementDistance, boolean isUrgentPlacement) {
        this.shouldPlace = shouldPlace;
        this.willLand = willLand;
        this.landingResult = landingResult;
        this.highestLandingBlock = highestLandingBlock;
        this.waterPlacementTarget = waterPlacementTarget;
        this.distanceToTarget = distanceToTarget;
        this.reason = reason;
        this.safetyResult = safetyResult;
        this.placementDistance = placementDistance;
        this.isUrgentPlacement = isUrgentPlacement;
    }
    
    public boolean shouldPlace() { return shouldPlace; }
    public boolean willLand() { return willLand; }
    public LandingPredictor.HitboxLandingResult getLandingResult() { return landingResult; }
    public BlockPos getHighestLandingBlock() { return highestLandingBlock; }
    public Vec3d getWaterPlacementTarget() { return waterPlacementTarget; }
    public double getDistanceToTarget() { return distanceToTarget; }
    public String getReason() { return reason; }
    public SafeLandingBlockChecker.SafetyResult getSafetyResult() { return safetyResult; }
    public double getPlacementDistance() { return placementDistance; }
    public boolean isUrgentPlacement() { return isUrgentPlacement; }
    public boolean isWithinPlacementDistance() { return distanceToTarget <= placementDistance && distanceToTarget > 0; }
}