package name.mlgmaster.statemachine;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import name.mlgmaster.MLGMaster;
import name.mlgmaster.WaterMLGHandler.MLGPredictionResult;
import name.mlgmaster.statemachine.handlers.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WaterMLGStateMachine {
    private FallState currentState = FallState.IDLE;
    private final Map<FallState, FallStateHandler> stateHandlers = new HashMap<>();
    private final List<StateChangeListener> listeners = new ArrayList<>();
    
    // State data
    private MLGPredictionResult activePrediction;
    private long stateStartTime;
    private Vec3d fallStartPosition;
    private boolean waterPlacementAttempted;
    private long lastPlacementAttempt;
    
    // Constants
    private static final long PLACEMENT_COOLDOWN_MS = 500;
    private static final long MAX_FALL_DURATION_MS = 30000; // 30 seconds safety timeout

    public WaterMLGStateMachine() {
        initializeStateHandlers();
    }

    private void initializeStateHandlers() {
        stateHandlers.put(FallState.IDLE, new IdleStateHandler());
        stateHandlers.put(FallState.FALL_DETECTED, new FallDetectedStateHandler());
        stateHandlers.put(FallState.TRACKING_FALL, new TrackingFallStateHandler());
        stateHandlers.put(FallState.READY_TO_PLACE, new ReadyToPlaceStateHandler());
        stateHandlers.put(FallState.WATER_PLACED, new WaterPlacedStateHandler());
        stateHandlers.put(FallState.FALL_ENDED, new FallEndedStateHandler());
    }

    public void tick(MinecraftClient client, ClientPlayerEntity player) {
        if (client == null || player == null || client.world == null) {
            if (currentState != FallState.IDLE) {
                transitionToState(FallState.IDLE, "Client or player became null", 
                                client, player, null, null, null);
            }
            return;
        }

        Vec3d position = player.getPos();
        Vec3d velocity = player.getVelocity();

        // Get handler for current state
        FallStateHandler handler = stateHandlers.get(currentState);
        if (handler == null) {
            MLGMaster.LOGGER.error("No handler found for state: {}", currentState);
            return;
        }

        // Process current state
        StateTransition transition = handler.processState(client, player, position, velocity, this);
        
        // Handle state transition if needed
        if (transition != null) {
            transitionToState(transition.getNewState(), transition.getReason(), 
                            client, player, position, velocity, transition.getPrediction());
        }
    }

    private void transitionToState(FallState newState, String reason, MinecraftClient client, 
                                 ClientPlayerEntity player, Vec3d position, Vec3d velocity, 
                                 MLGPredictionResult prediction) {
        FallState oldState = currentState;
        
        MLGMaster.LOGGER.info("State transition: {} -> {} ({})", oldState, newState, reason);

        // Handle special state data updates BEFORE creating the event
        if (newState == FallState.FALL_DETECTED && position != null) {
            fallStartPosition = position;
        } else if (newState == FallState.IDLE) {
            resetStateData();
        }

        // Create enhanced state change event with access to state machine
        EnhancedStateChangeEvent event = new EnhancedStateChangeEvent(oldState, newState, reason, 
                                                                     client, player, position, 
                                                                     velocity, prediction, this);

        // Exit current state
        FallStateHandler oldHandler = stateHandlers.get(oldState);
        if (oldHandler != null) {
            oldHandler.onExit(event);
        }

        // Update state
        currentState = newState;
        stateStartTime = System.currentTimeMillis();
        
        // Store prediction if provided
        if (prediction != null) {
            activePrediction = prediction;
        }

        // Enter new state
        FallStateHandler newHandler = stateHandlers.get(newState);
        if (newHandler != null) {
            newHandler.onEnter(event);
        }

        // Notify listeners
        notifyStateChange(event);
    }

    private void notifyStateChange(StateChangeEvent event) {
        for (StateChangeListener listener : listeners) {
            try {
                listener.onStateChanged(event);
                
                // Call specific state methods
                switch (event.getToState()) {
                    case FALL_DETECTED -> listener.onFallDetected(event);
                    case TRACKING_FALL -> listener.onFallTracking(event);
                    case READY_TO_PLACE -> listener.onReadyToPlace(event);
                    case WATER_PLACED -> listener.onWaterPlaced(event);
                    case FALL_ENDED -> listener.onFallEnded(event);
                    case IDLE -> listener.onResetToIdle(event);
                }
            } catch (Exception e) {
                MLGMaster.LOGGER.error("Error in state change listener", e);
            }
        }
    }

    // Public API methods
    public void addStateChangeListener(StateChangeListener listener) {
        listeners.add(listener);
    }

    public void removeStateChangeListener(StateChangeListener listener) {
        listeners.remove(listener);
    }

    public FallState getCurrentState() {
        return currentState;
    }

    public MLGPredictionResult getActivePrediction() {
        return activePrediction;
    }

    public long getStateStartTime() {
        return stateStartTime;
    }

    public Vec3d getFallStartPosition() {
        return fallStartPosition;
    }

    public boolean isWaterPlacementAttempted() {
        return waterPlacementAttempted;
    }

    public long getLastPlacementAttempt() {
        return lastPlacementAttempt;
    }

    public long getPlacementCooldownMs() {
        return PLACEMENT_COOLDOWN_MS;
    }

    public long getMaxFallDurationMs() {
        return MAX_FALL_DURATION_MS;
    }

    // State data setters (for handlers)
    public void setActivePrediction(MLGPredictionResult prediction) {
        this.activePrediction = prediction;
    }

    public void setFallStartPosition(Vec3d position) {
        this.fallStartPosition = position;
    }

    public void setWaterPlacementAttempted(boolean attempted) {
        this.waterPlacementAttempted = attempted;
    }

    public void setLastPlacementAttempt(long timestamp) {
        this.lastPlacementAttempt = timestamp;
    }

    public void forceReset() {
        transitionToState(FallState.IDLE, "Manual reset", null, null, null, null, null);
        resetStateData();
    }

    private void resetStateData() {
        activePrediction = null;
        fallStartPosition = null;
        waterPlacementAttempted = false;
        lastPlacementAttempt = 0;
    }

    public String getStateInfo() {
        StringBuilder info = new StringBuilder();
        info.append("State: ").append(currentState.getDescription());
        
        if (currentState != FallState.IDLE) {
            long duration = System.currentTimeMillis() - stateStartTime;
            info.append(" (").append(duration).append("ms)");
            
            if (activePrediction != null) {
                info.append(", needs_water=").append(activePrediction.shouldPlace());
                info.append(", attempted=").append(waterPlacementAttempted);
            }
        }
        
        return info.toString();
    }
}
