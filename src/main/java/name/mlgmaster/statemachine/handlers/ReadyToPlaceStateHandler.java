package name.mlgmaster.statemachine.handlers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import name.mlgmaster.MLGMaster;
import name.mlgmaster.WaterMLGHandler;
import name.mlgmaster.WaterPlacer;
import name.mlgmaster.statemachine.*;

public class ReadyToPlaceStateHandler implements FallStateHandler {

    @Override
    public StateTransition processState(MinecraftClient client, ClientPlayerEntity player, 
                                      Vec3d position, Vec3d velocity, WaterMLGStateMachine stateMachine) {
        // Check if fall has ended before we could place
        if (velocity.y >= -0.05 || player.isOnGround()) {
            return new StateTransition(FallState.FALL_ENDED, "Fall ended before water placement");
        }

        WaterMLGHandler.MLGPredictionResult prediction = stateMachine.getActivePrediction();
        if (prediction == null) {
            return new StateTransition(FallState.IDLE, "No prediction data available");
        }

        // If we already attempted placement, transition to water placed state
        if (stateMachine.isWaterPlacementAttempted()) {
            return new StateTransition(FallState.WATER_PLACED, "Water placement already attempted");
        }

        // Attempt water placement
        MLGMaster.LOGGER.info("ATTEMPTING WATER PLACEMENT");
        logDetailedPlacementState(client, player, position, prediction);

        boolean placementSuccess = WaterPlacer.executeWaterPlacement(client, player, prediction);
        
        // Mark as attempted regardless of success
        stateMachine.setWaterPlacementAttempted(true);
        stateMachine.setLastPlacementAttempt(System.currentTimeMillis());

        if (placementSuccess) {
            MLGMaster.LOGGER.info("WATER MLG SUCCESSFUL!");
            return new StateTransition(FallState.WATER_PLACED, "Water placed successfully");
        } else {
            MLGMaster.LOGGER.warn("WATER MLG FAILED - placement unsuccessful");
            logPlacementFailureAnalysis(client, player, position, prediction);
            return new StateTransition(FallState.WATER_PLACED, "Water placement failed");
        }
    }

    private void logDetailedPlacementState(MinecraftClient client, ClientPlayerEntity player,
                                         Vec3d playerPosition, WaterMLGHandler.MLGPredictionResult prediction) {
        MLGMaster.LOGGER.info("=== PRE-PLACEMENT STATE ===");
        MLGMaster.LOGGER.info("Player position: {} (X={}, Y={}, Z={})", playerPosition,
                String.format("%.3f", playerPosition.x), String.format("%.3f", playerPosition.y),
                String.format("%.3f", playerPosition.z));
        MLGMaster.LOGGER.info("Player velocity: {}", player.getVelocity());
        MLGMaster.LOGGER.info("Target landing block: {}", prediction.getHighestLandingBlock());
        MLGMaster.LOGGER.info("Water placement target: {}", prediction.getWaterPlacementTarget());
    }

    private void logPlacementFailureAnalysis(MinecraftClient client, ClientPlayerEntity player,
                                           Vec3d playerPosition, WaterMLGHandler.MLGPredictionResult prediction) {
        MLGMaster.LOGGER.error("=== WATER PLACEMENT FAILURE ANALYSIS ===");
        MLGMaster.LOGGER.error("Player position: {}", playerPosition);
        MLGMaster.LOGGER.error("Player velocity: {}", player.getVelocity());
        MLGMaster.LOGGER.error("Target block: {}", prediction.getHighestLandingBlock());
        MLGMaster.LOGGER.error("Water target: {}", prediction.getWaterPlacementTarget());
        // Add more detailed analysis as needed
    }

    @Override
    public void onEnter(StateChangeEvent event) {
        MLGMaster.LOGGER.info("=== READY TO PLACE WATER ===");
        MLGMaster.LOGGER.info("Within placement range, preparing to place water");
    }

    @Override
    public void onExit(StateChangeEvent event) {
        MLGMaster.LOGGER.debug("Water placement phase complete, transitioning to: {}", event.getToState());
    }

    @Override
    public FallState getHandledState() {
        return FallState.READY_TO_PLACE;
    }
}