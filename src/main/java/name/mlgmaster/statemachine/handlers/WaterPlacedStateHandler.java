package name.mlgmaster.statemachine.handlers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import name.mlgmaster.MLGMaster;
import name.mlgmaster.statemachine.*;

public class WaterPlacedStateHandler implements FallStateHandler {

    @Override
    public StateTransition processState(MinecraftClient client, ClientPlayerEntity player, 
                                      Vec3d position, Vec3d velocity, WaterMLGStateMachine stateMachine) {
        // Check if fall has ended
        if (shouldEndFall(velocity, player, stateMachine)) {
            return new StateTransition(FallState.FALL_ENDED, "Fall completed after water placement");
        }

        return null; // Continue monitoring fall
    }

    private boolean shouldEndFall(Vec3d velocity, ClientPlayerEntity player, 
                                WaterMLGStateMachine stateMachine) {
        // Fall ended if player stopped falling significantly
        if (velocity.y >= -0.05) {
            return true;
        }

        // Fall ended if player is on ground or in water
        if (player.isOnGround() || player.isTouchingWater()) {
            return true;
        }

        // Safety timeout
        long fallDuration = System.currentTimeMillis() - stateMachine.getStateStartTime();
        if (fallDuration > stateMachine.getMaxFallDurationMs()) {
            return true;
        }

        return false;
    }

    @Override
    public void onEnter(StateChangeEvent event) {
        MLGMaster.LOGGER.info("=== WATER PLACED ===");
        MLGMaster.LOGGER.info("Monitoring fall completion after water placement");
    }

    @Override
    public void onExit(StateChangeEvent event) {
        MLGMaster.LOGGER.debug("Water placement monitoring complete, transitioning to: {}", event.getToState());
    }

    @Override
    public FallState getHandledState() {
        return FallState.WATER_PLACED;
    }
}