package name.mlgmaster;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class FallDetector {
    private static final double FALL_TRIGGER_DISTANCE = 5.0; // Minimum fall distance to trigger
    
    public static class FallState {
        private final boolean actuallyFalling;
        private final boolean fastFalling;
        private final boolean dangerous;
        private final boolean shouldTrigger;
        private final boolean allowsReplacement;
        private final String triggerReason;
        
        public FallState(boolean actuallyFalling, boolean fastFalling, boolean dangerous, 
                        boolean shouldTrigger, boolean allowsReplacement, String triggerReason) {
            this.actuallyFalling = actuallyFalling;
            this.fastFalling = fastFalling;
            this.dangerous = dangerous;
            this.shouldTrigger = shouldTrigger;
            this.allowsReplacement = allowsReplacement;
            this.triggerReason = triggerReason;
        }
        
        public boolean isActuallyFalling() { return actuallyFalling; }
        public boolean isFastFalling() { return fastFalling; }
        public boolean isDangerous() { return dangerous; }
        public boolean shouldTrigger() { return shouldTrigger; }
        public boolean allowsReplacement() { return allowsReplacement; }
        public String getTriggerReason() { return triggerReason; }
    }
    
    public static FallState analyzeFall(ClientPlayerEntity player, Vec3d velocity) {
        double verticalSpeed = velocity.y;
        
        // Falling detection (account for idle gravity)
        boolean actuallyFalling = verticalSpeed < -0.15 && !player.isOnGround();
        boolean fastFalling = verticalSpeed < -0.5;
        boolean dangerous = player.fallDistance > FALL_TRIGGER_DISTANCE;
        
        // Early exit conditions - must be falling at least 5 blocks
        if (!actuallyFalling && player.fallDistance < FALL_TRIGGER_DISTANCE) {
            return new FallState(actuallyFalling, fastFalling, dangerous, false, false, "Not falling enough distance");
        }
        
        // Trigger conditions - requires 5 block minimum fall distance
        boolean shouldTrigger = false;
        String reason = "";
        
        if (dangerous && fastFalling && player.fallDistance >= FALL_TRIGGER_DISTANCE) {
            shouldTrigger = true;
            reason = "Dangerous fast fall with sufficient distance";
        } else if (player.fallDistance > FALL_TRIGGER_DISTANCE && actuallyFalling) {
            shouldTrigger = true;
            reason = "Sufficient fall distance reached";
        } else if (Math.abs(verticalSpeed) > 1.0 && player.fallDistance >= FALL_TRIGGER_DISTANCE) {
            shouldTrigger = true;
            reason = "High speed fall with sufficient distance";
        }
        
        // Replacement allowed for extreme cases
        boolean allowsReplacement = Math.abs(verticalSpeed) > 1.5 || player.fallDistance > 10;
        
        return new FallState(actuallyFalling, fastFalling, dangerous, shouldTrigger, allowsReplacement, reason);
    }
}
