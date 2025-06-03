package name.mlgmaster.debug;

import name.mlgmaster.MLGMaster;
import name.mlgmaster.WaterMLGHandler;
import name.mlgmaster.statemachine.*;
import net.minecraft.client.MinecraftClient;

public class MLGDebugMonitor implements StateChangeListener {
    private static final boolean DEBUG_ENABLED = true; // Toggle for debug output
    
    private long totalFalls = 0;
    private long successfulMLGs = 0;
    private long failedMLGs = 0;
    private long fallsNotRequiringMLG = 0;

    @Override
    public void onStateChanged(StateChangeEvent event) {
        if (!DEBUG_ENABLED) return;
        
        MLGMaster.LOGGER.debug("[DEBUG] State: {} -> {} | Reason: {} | Time: {}ms", 
                              event.getFromState(), 
                              event.getToState(), 
                              event.getReason(),
                              System.currentTimeMillis() - event.getTimestamp());
    }

    @Override
    public void onFallDetected(StateChangeEvent event) {
        totalFalls++;
        MLGMaster.LOGGER.info("[STATS] Fall #{} detected", totalFalls);
    }

    @Override
    public void onWaterPlaced(StateChangeEvent event) {
        MLGMaster.LOGGER.info("[STATS] Water placement attempted");
    }

    @Override
    public void onFallEnded(StateChangeEvent event) {
        if (event instanceof EnhancedStateChangeEvent enhancedEvent) {
            boolean wasWaterAttempted = enhancedEvent.isWaterPlacementAttempted();
            boolean playerSafe = event.getPlayer() != null && 
                               (event.getPlayer().isOnGround() || event.getPlayer().isTouchingWater());
            
            if (wasWaterAttempted) {
                if (playerSafe) {
                    successfulMLGs++;
                    MLGMaster.LOGGER.info("[STATS] Successful MLG! Total: {}/{}", 
                                         successfulMLGs, successfulMLGs + failedMLGs);
                } else {
                    failedMLGs++;
                    MLGMaster.LOGGER.warn("[STATS] Failed MLG! Total failures: {}", failedMLGs);
                }
            } else {
                fallsNotRequiringMLG++;
                MLGMaster.LOGGER.info("[STATS] Fall ended without MLG requirement");
            }
            
            logSessionStats();
        }
    }

    @Override
    public void onResetToIdle(StateChangeEvent event) {
        if (DEBUG_ENABLED) {
            MLGMaster.LOGGER.debug("[DEBUG] Reset to idle state: {}", event.getReason());
        }
    }

    private void logSessionStats() {
        if (totalFalls % 10 == 0 && totalFalls > 0) { // Log every 10 falls
            MLGMaster.LOGGER.info("=== SESSION STATISTICS ===");
            MLGMaster.LOGGER.info("Total falls detected: {}", totalFalls);
            MLGMaster.LOGGER.info("Successful MLGs: {}", successfulMLGs);
            MLGMaster.LOGGER.info("Failed MLGs: {}", failedMLGs);
            MLGMaster.LOGGER.info("Falls not requiring MLG: {}", fallsNotRequiringMLG);
            
            if (successfulMLGs + failedMLGs > 0) {
                double successRate = (double) successfulMLGs / (successfulMLGs + failedMLGs) * 100;
                MLGMaster.LOGGER.info("MLG success rate: {:.1f}%", successRate);
            }
        }
    }

    public void printCurrentState() {
        WaterMLGStateMachine stateMachine = WaterMLGHandler.getStateMachine();
        MinecraftClient client = MinecraftClient.getInstance();
        
        MLGMaster.LOGGER.info("=== CURRENT STATE DEBUG ===");
        MLGMaster.LOGGER.info("State: {}", stateMachine.getCurrentState());
        MLGMaster.LOGGER.info("State info: {}", WaterMLGHandler.getTrackingStateInfo());
        
        if (client.player != null) {
            MLGMaster.LOGGER.info("Player position: {}", client.player.getPos());
            MLGMaster.LOGGER.info("Player velocity: {}", client.player.getVelocity());
            MLGMaster.LOGGER.info("Player on ground: {}", client.player.isOnGround());
            MLGMaster.LOGGER.info("Player in water: {}", client.player.isTouchingWater());
        }
        
        if (stateMachine.getActivePrediction() != null) {
            var prediction = stateMachine.getActivePrediction();
            MLGMaster.LOGGER.info("Active prediction:");
            MLGMaster.LOGGER.info("  Should place: {}", prediction.shouldPlace());
            MLGMaster.LOGGER.info("  Reason: {}", prediction.getReason());
            MLGMaster.LOGGER.info("  Distance to landing: {}", 
                                 WaterMLGHandler.getCurrentDistanceToLanding());
        }
    }

    // Getters for external access
    public long getTotalFalls() { return totalFalls; }
    public long getSuccessfulMLGs() { return successfulMLGs; }
    public long getFailedMLGs() { return failedMLGs; }
    public long getFallsNotRequiringMLG() { return fallsNotRequiringMLG; }
    
    public double getSuccessRate() {
        if (successfulMLGs + failedMLGs == 0) return 0.0;
        return (double) successfulMLGs / (successfulMLGs + failedMLGs) * 100;
    }
}