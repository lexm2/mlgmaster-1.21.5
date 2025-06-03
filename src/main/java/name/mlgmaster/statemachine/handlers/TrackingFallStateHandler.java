package name.mlgmaster.statemachine.handlers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import name.mlgmaster.MLGMaster;
import name.mlgmaster.WaterMLGHandler;
import name.mlgmaster.statemachine.*;

public class TrackingFallStateHandler implements FallStateHandler {

    private static final double PLACEMENT_HEIGHT_THRESHOLD = 4d; // In 2d space

    private static final double PLACEMENT_DISTANCE_FALLBACK = 4d; // In 3d space

    @Override
    public StateTransition processState(MinecraftClient client, ClientPlayerEntity player,
            Vec3d position, Vec3d velocity, WaterMLGStateMachine stateMachine) {

        // If player stopped falling, return to idle
        if (velocity.y >= -0.05) {
            return new StateTransition(FallState.IDLE, "Player stopped falling");
        }

        // If player is on ground, return to idle
        if (player.isOnGround()) {
            return new StateTransition(FallState.IDLE, "Player landed");
        }

        // Get the active prediction
        WaterMLGHandler.MLGPredictionResult prediction = stateMachine.getActivePrediction();
        if (prediction == null || !prediction.shouldPlace()) {
            return new StateTransition(FallState.IDLE, "No valid prediction for water placement");
        }

        // Calculate height above predicted landing
        Vec3d predictedLanding = prediction.getPredictedLandingPosition();
        double heightAboveLanding = position.y - predictedLanding.y;

        // Calculate 3D distance as fallback
        double distanceToLanding = position.distanceTo(predictedLanding);

        // Calculate direct height above ground (more accurate)
        double heightAboveGround = calculateHeightAboveGround(client, position);

        // Log current fall progress
        MLGMaster.LOGGER.info(
                "Fall progress: height above landing = {}, height above ground = {}, 3D distance = {}",
                String.format("%.3f", heightAboveLanding), String.format("%.3f", heightAboveGround),
                String.format("%.3f", distanceToLanding));

        // Primary trigger: Height above ground
        if (heightAboveGround <= PLACEMENT_HEIGHT_THRESHOLD && heightAboveGround > 0) {
            MLGMaster.LOGGER.info("TRIGGERING PLACEMENT: {} blocks above ground (threshold: {})",
                    String.format("%.3f", heightAboveGround), PLACEMENT_HEIGHT_THRESHOLD);
            return new StateTransition(FallState.READY_TO_PLACE,
                    String.format("Height above ground: %.3f", heightAboveGround));
        }

        // Fallback trigger: 3D distance (safety net)
        if (distanceToLanding <= PLACEMENT_DISTANCE_FALLBACK) {
            MLGMaster.LOGGER.info(
                    "TRIGGERING PLACEMENT (FALLBACK): {} blocks from landing (threshold: {})",
                    String.format("%.3f", distanceToLanding), PLACEMENT_DISTANCE_FALLBACK);
            return new StateTransition(FallState.READY_TO_PLACE,
                    String.format("Distance fallback: %.3f", distanceToLanding));
        }

        // Continue tracking
        return new StateTransition(FallState.TRACKING_FALL, "Continuing fall tracking");
    }

    /**
     * Calculate height above solid ground directly below player
     */
    private double calculateHeightAboveGround(MinecraftClient client, Vec3d playerPosition) {
        int playerX = (int) Math.floor(playerPosition.x);
        int playerZ = (int) Math.floor(playerPosition.z);
        int startY = (int) Math.floor(playerPosition.y);

        // Search down for solid ground
        for (int y = startY; y >= Math.max(startY - 100, client.world.getBottomY()); y--) {
            BlockPos checkPos = new BlockPos(playerX, y, playerZ);

            if (!client.world.getBlockState(checkPos).isAir()
                    && client.world.getBlockState(checkPos).isSolid()) {

                double groundY = y + 1.0; // Top of the solid block
                double heightAbove = playerPosition.y - groundY;

                MLGMaster.LOGGER.debug("Found ground at Y={}, player at Y={}, height above = {}",
                        groundY, String.format("%.3f", playerPosition.y),
                        String.format("%.3f", heightAbove));

                return heightAbove;
            }
        }

        // If no ground found within reasonable distance, return large value
        MLGMaster.LOGGER.warn("No ground found within 100 blocks below player");
        return 100.0;
    }

    @Override
    public void onEnter(StateChangeEvent event) {
        MLGMaster.LOGGER.info("=== TRACKING FALL ===");
        if (event.getPrediction() != null) {
            WaterMLGHandler.MLGPredictionResult prediction = event.getPrediction();
            MLGMaster.LOGGER.info("Predicted landing: {}",
                    prediction.getPredictedLandingPosition());
            MLGMaster.LOGGER.info("Landing block: {}", prediction.getHighestLandingBlock());
            MLGMaster.LOGGER.info("Should place water: {}", prediction.shouldPlace());
            MLGMaster.LOGGER.info("Reason: {}", prediction.getReason());
        }
    }

    @Override
    public void onExit(StateChangeEvent event) {
        MLGMaster.LOGGER.debug("Fall tracking complete, transitioning to: {}", event.getToState());
    }

    @Override
    public FallState getHandledState() {
        return FallState.TRACKING_FALL;
    }
}
