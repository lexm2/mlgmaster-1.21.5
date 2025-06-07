package name.mlgmaster;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PlayerRotationManager {
    private static float originalYaw = 0;
    private static float originalPitch = 0;
    private static boolean rotationStored = false;

    public static void storeOriginalRotation(ClientPlayerEntity player) {
        if (!rotationStored) {
            originalYaw = player.getYaw();
            originalPitch = player.getPitch();
            rotationStored = true;
        }
    }

    public static void lookAtTarget(ClientPlayerEntity player, Vec3d target) {
        Vec3d playerEyes = player.getEyePos();

        // Calculate direction to target
        double deltaX = target.x - playerEyes.x;
        double deltaY = target.y - playerEyes.y;
        double deltaZ = target.z - playerEyes.z;

        // Calculate yaw (horizontal rotation)
        double yaw = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0;

        // Calculate pitch (vertical rotation)
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double pitch = -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));

        // Clamp pitch to valid range (-90 to 90)
        pitch = MathHelper.clamp(pitch, -90.0, 90.0);

        // Set rotation
        player.setYaw((float) yaw);
        player.setPitch((float) pitch);
    }

    public static void setLookDown(ClientPlayerEntity player) {
        player.setYaw(originalYaw); // Keep original yaw
        player.setPitch(90.0f); // Look straight down
        MLGMaster.LOGGER.info("Player rotation set to look down: Yaw={} | Pitch={}",
                player.getYaw(), player.getPitch());
    }

    public static void restoreOriginalRotation(ClientPlayerEntity player) {
        if (rotationStored) {
            player.setYaw(originalYaw);
            player.setPitch(originalPitch);
            rotationStored = false;
        }
    }

    public static boolean hasStoredRotation() {
        return rotationStored;
    }
}
