package name.mlgmaster.statemachine.handlers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import name.mlgmaster.MLGMaster;
import name.mlgmaster.statemachine.*;

public class FallEndedStateHandler implements FallStateHandler {

    @Override
    public StateTransition processState(MinecraftClient client, ClientPlayerEntity player, 
                                      Vec3d position, Vec3d velocity, WaterMLGStateMachine stateMachine) {
        // Immediately transition back to idle after logging fall statistics
        return new StateTransition(FallState.IDLE, "Fall analysis complete, returning to idle");
    }

    @Override
    public void onEnter(StateChangeEvent event) {
        MLGMaster.LOGGER.info("=== FALL ENDED ===");
        MLGMaster.LOGGER.info("Reason: {}", event.getReason());
        
        // Log fall statistics if we have the data
        logFallStatistics(event);
    }

    private void logFallStatistics(StateChangeEvent event) {
        if (event instanceof EnhancedStateChangeEvent enhancedEvent) {
            Vec3d fallStartPosition = enhancedEvent.getFallStartPosition();
            WaterMLGStateMachine stateMachine = enhancedEvent.getStateMachine();
            
            if (event.getPlayerPosition() != null && fallStartPosition != null) {
                double totalFallDistance = fallStartPosition.y - event.getPlayerPosition().y;
                long fallDuration = System.currentTimeMillis() - enhancedEvent.getStateStartTime();
                
                MLGMaster.LOGGER.info("Fall statistics:");
                MLGMaster.LOGGER.info("  Start position: {}", fallStartPosition);
                MLGMaster.LOGGER.info("  End position: {}", event.getPlayerPosition());
                MLGMaster.LOGGER.info("  Total fall distance: {} blocks", String.format("%.3f", totalFallDistance));
                MLGMaster.LOGGER.info("  Fall duration: {} ms", fallDuration);
                MLGMaster.LOGGER.info("  Water placement attempted: {}", enhancedEvent.isWaterPlacementAttempted());
                
                if (event.getPlayer() != null) {
                    MLGMaster.LOGGER.info("  Player health: {}/{}", 
                                         String.format("%.1f", event.getPlayer().getHealth()),
                                         String.format("%.1f", event.getPlayer().getMaxHealth()));
                    MLGMaster.LOGGER.info("  Player on ground: {}", event.getPlayer().isOnGround());
                    MLGMaster.LOGGER.info("  Player in water: {}", event.getPlayer().isTouchingWater());
                }
            } else {
                MLGMaster.LOGGER.warn("Insufficient data for fall statistics");
            }
        } else {
            MLGMaster.LOGGER.debug("Standard state change event, limited statistics available");
        }
    }

    @Override
    public void onExit(StateChangeEvent event) {
        MLGMaster.LOGGER.debug("Fall analysis complete, returning to idle state");
    }

    @Override
    public FallState getHandledState() {
        return FallState.FALL_ENDED;
    }
}
