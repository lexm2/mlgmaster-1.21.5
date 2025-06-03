package name.mlgmaster.statemachine.handlers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import name.mlgmaster.MLGMaster;
import name.mlgmaster.statemachine.*;

public class IdleStateHandler implements FallStateHandler {
    private static final double FALL_START_VELOCITY_THRESHOLD = -0.2;

    @Override
    public StateTransition processState(MinecraftClient client, ClientPlayerEntity player, 
                                      Vec3d position, Vec3d velocity, WaterMLGStateMachine stateMachine) {
        // Check if we should start tracking a new fall
        if (shouldStartTrackingFall(velocity, stateMachine)) {
            return new StateTransition(FallState.FALL_DETECTED, 
                                     "Fall detected with velocity: " + velocity.y);
        }
        
        return null; // Stay in IDLE
    }

    private boolean shouldStartTrackingFall(Vec3d velocity, WaterMLGStateMachine stateMachine) {
        // Must be falling with significant velocity
        if (velocity.y >= FALL_START_VELOCITY_THRESHOLD) {
            return false;
        }

        // Don't start new tracking too soon after last attempt
        long currentTime = System.currentTimeMillis();
        if (currentTime - stateMachine.getLastPlacementAttempt() < stateMachine.getPlacementCooldownMs()) {
            return false;
        }

        return true;
    }

    @Override
    public void onEnter(StateChangeEvent event) {
        MLGMaster.LOGGER.debug("Entered IDLE state: {}", event.getReason());
    }

    @Override
    public void onExit(StateChangeEvent event) {
        MLGMaster.LOGGER.debug("Exiting IDLE state to {}: {}", event.getToState(), event.getReason());
    }

    @Override
    public FallState getHandledState() {
        return FallState.IDLE;
    }
}