package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class WaterMLGHandler {
    private static boolean isActive = false;
    private static long lastPredictionTime = 0;
    private static final long PREDICTION_INTERVAL = 50;
    private static final double FALL_TRIGGER_DISTANCE = 5.0;

    public static void onHighFrequencyTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        ClientPlayerEntity player = client.player;
        Vec3d velocity = player.getVelocity();

        // Release scaffolding crouch if not falling anymore
        if (velocity.y >= -0.1 || player.isOnGround()) {
            ScaffoldingCrouchManager.releaseScaffoldingCrouch();

            if (isActive) {
                isActive = false;
                MLGMaster.LOGGER.info("Player stopped falling, deactivating MLG");
            }
            return;
        }

        // Only activate water MLG when falling enough distance
        if (player.fallDistance < FALL_TRIGGER_DISTANCE) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Reduce prediction interval for high-speed falls
        double fallSpeed = Math.abs(velocity.y);
        long dynamicInterval = fallSpeed > 1.0 ? PREDICTION_INTERVAL / 2 : PREDICTION_INTERVAL;

        if (currentTime - lastPredictionTime < dynamicInterval) {
            return;
        }
        lastPredictionTime = currentTime;

        // Use the DISTANCE-BASED prediction function
        MLGPredictionResult prediction =
                MLGAnalyzer.analyzeFallAndPlacement(client, player, velocity);

        if (prediction.isUrgentPlacement()) {
            MLGMaster.LOGGER.warn("🚨 URGENT MLG SITUATION: {} blocks to target!",
                    prediction.getDistanceToTarget());
        }

        MLGMaster.LOGGER.info("🎯 MLG TICK ANALYSIS: {} - {}",
                prediction.shouldPlace() ? "PLACE NOW!" : "WAITING", prediction.getReason());

        if (prediction.shouldPlace()) {
            BlockPos targetBlock = prediction.getHighestLandingBlock();
            Vec3d targetPos = prediction.getWaterPlacementTarget();

            MLGMaster.LOGGER.info("💧 EXECUTING DISTANCE-BASED PLACEMENT:");
            MLGMaster.LOGGER.info("  Fall speed: {:.3f} blocks/tick", Math.abs(velocity.y));
            MLGMaster.LOGGER.info("  Placement threshold: {:.3f} blocks",
                    prediction.getPlacementDistance());
            MLGMaster.LOGGER.info("  Distance to target: {:.3f} blocks",
                    prediction.getDistanceToTarget());
            MLGMaster.LOGGER.info("  Target: {} at {}", targetBlock, targetPos);

            if (WaterPlacer.executeWaterPlacement(client, player, prediction)) {
                isActive = true;
                MLGMaster.LOGGER.info("✅ Distance-based water placement successful!");

                // Shorter pause for urgent situations
                long pauseDuration = prediction.isUrgentPlacement() ? 500 : 1000;
                lastPredictionTime = currentTime + pauseDuration;
            } else {
                MLGMaster.LOGGER.warn("❌ Distance-based water placement failed");
            }
        } else if (prediction.willLand()) {
            MLGMaster.LOGGER.info("📊 Distance analysis: {} | Distance: {:.3f} | Speed: {:.3f}",
                    prediction.getReason(), prediction.getDistanceToTarget(), Math.abs(velocity.y));
        }
    }

    // Getter methods for external access
    public static boolean isActive() {
        return isActive;
    }

    public static void setActive(boolean active) {
        isActive = active;
    }
}
