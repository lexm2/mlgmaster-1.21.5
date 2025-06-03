package name.mlgmaster.statemachine.handlers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import name.mlgmaster.MLGMaster;
import name.mlgmaster.WaterMLGHandler;
import name.mlgmaster.statemachine.*;

public class FallDetectedStateHandler implements FallStateHandler {

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

        // Analyze the fall and create prediction
        WaterMLGHandler.MLGPredictionResult prediction = 
            WaterMLGHandler.analyzeFallAndPlacement(client, player, velocity);

        if (prediction == null) {
            return new StateTransition(FallState.IDLE, "Could not analyze fall");
        }

        // Move to tracking with the prediction
        return new StateTransition(FallState.TRACKING_FALL, 
                                 "Fall analysis complete", prediction);
    }

    @Override
    public void onEnter(StateChangeEvent event) {
        MLGMaster.LOGGER.info("=== FALL DETECTED ===");
        if (event.getPlayerPosition() != null) {
            MLGMaster.LOGGER.info("Fall start position: {}", event.getPlayerPosition());
            MLGMaster.LOGGER.info("Fall start velocity: {}", event.getPlayerVelocity());
        }
        
        // Store fall start position in the state machine
        if (event instanceof EnhancedStateChangeEvent enhancedEvent) {
            WaterMLGStateMachine stateMachine = enhancedEvent.getStateMachine();
            if (event.getPlayerPosition() != null) {
                stateMachine.setFallStartPosition(event.getPlayerPosition());
            }
        }
    }

    @Override
    public void onExit(StateChangeEvent event) {
        MLGMaster.LOGGER.debug("Fall detection complete, transitioning to: {}", event.getToState());
    }

    @Override
    public FallState getHandledState() {
        return FallState.FALL_DETECTED;
    }
}
