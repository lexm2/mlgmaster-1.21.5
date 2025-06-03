package name.mlgmaster.statemachine;

import name.mlgmaster.MLGMaster;

public class MLGStatisticsLogger implements StateChangeListener {
    
    @Override
    public void onStateChanged(StateChangeEvent event) {
        // Log all state changes for debugging
        MLGMaster.LOGGER.debug("State: {} -> {} ({})", 
                              event.getFromState(), event.getToState(), event.getReason());
    }

    @Override
    public void onFallDetected(StateChangeEvent event) {
        MLGMaster.LOGGER.info("Fall detected at position: {}", event.getPlayerPosition());
    }

    @Override
    public void onWaterPlaced(StateChangeEvent event) {
        MLGMaster.LOGGER.info("Water MLG attempted");
    }

    @Override
    public void onFallEnded(StateChangeEvent event) {
        logDetailedFallStatistics(event);
    }

    private void logDetailedFallStatistics(StateChangeEvent event) {
        MLGMaster.LOGGER.info("=== FALL STATISTICS ===");
        
        if (event.getPlayerPosition() != null) {
            MLGMaster.LOGGER.info("Final position: {}", event.getPlayerPosition());
            MLGMaster.LOGGER.info("Final velocity: {}", event.getPlayerVelocity());
        }
        
        if (event.getPlayer() != null) {
            MLGMaster.LOGGER.info("Player health: {}/{}", 
                                 event.getPlayer().getHealth(), 
                                 event.getPlayer().getMaxHealth());
            MLGMaster.LOGGER.info("Player on ground: {}", event.getPlayer().isOnGround());
            MLGMaster.LOGGER.info("Player in water: {}", event.getPlayer().isTouchingWater());
        }
    }
}