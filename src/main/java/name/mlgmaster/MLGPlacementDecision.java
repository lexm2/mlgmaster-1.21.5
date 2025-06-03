package name.mlgmaster;

public class MLGPlacementDecision {
    private static final double HIGH_SPEED_THRESHOLD = 1.5;
    private static final double EMERGENCY_DISTANCE_MULTIPLIER = 1.2;
    
    /**
     * Determine if we should place water now based on distance factors
     */
    public static boolean shouldPlaceWaterNow(double distanceToTarget, MLGDistanceCalculator.DistanceResult distanceResult) {
        double placementDistance = distanceResult.getPlacementDistance();
        boolean isUrgent = distanceResult.isUrgent();
        double fallSpeed = distanceResult.getFallSpeed();
        
        // Primary check: within dynamic distance threshold
        boolean withinDistance = distanceToTarget <= placementDistance;
        
        // Secondary check: urgent placement (running out of time)
        boolean urgentTiming = isUrgent && distanceToTarget <= placementDistance * EMERGENCY_DISTANCE_MULTIPLIER;
        
        // Tertiary check: very fast fall speed (emergency placement)
        boolean emergencySpeed = fallSpeed > HIGH_SPEED_THRESHOLD && distanceToTarget <= placementDistance * EMERGENCY_DISTANCE_MULTIPLIER;
        
        return withinDistance || urgentTiming || emergencySpeed;
    }
    
    /**
     * Build a descriptive reason for the placement decision
     */
    public static String buildPlacementReason(boolean shouldPlace, double distanceToTarget, 
                                            MLGDistanceCalculator.DistanceResult distanceResult) {
        double placementDistance = distanceResult.getPlacementDistance();
        boolean isUrgent = distanceResult.isUrgent();
        double fallSpeed = distanceResult.getFallSpeed();
        int estimatedTime = distanceResult.getEstimatedImpactTime();
        
        if (!shouldPlace) {
            return String.format("Waiting: distance %.2f > threshold %.2f, %d ticks remaining", 
                distanceToTarget, placementDistance, estimatedTime);
        }
        
        if (isUrgent) {
            return String.format("URGENT: Only %d ticks left (distance %.2f)", 
                estimatedTime, distanceToTarget);
        }
        
        if (fallSpeed > HIGH_SPEED_THRESHOLD) {
            return String.format("HIGH SPEED: Fall speed %.3f (distance %.2f)", 
                fallSpeed, distanceToTarget);
        }
        
        return String.format("Ready: distance %.2f <= threshold %.2f", 
            distanceToTarget, placementDistance);
    }
}