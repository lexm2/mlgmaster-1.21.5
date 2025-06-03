package name.mlgmaster.statemachine;

import name.mlgmaster.WaterMLGHandler.MLGPredictionResult;

public class StateTransition {
    private final FallState newState;
    private final String reason;
    private final MLGPredictionResult prediction;

    public StateTransition(FallState newState, String reason) {
        this(newState, reason, null);
    }

    public StateTransition(FallState newState, String reason, MLGPredictionResult prediction) {
        this.newState = newState;
        this.reason = reason;
        this.prediction = prediction;
    }

    public FallState getNewState() { return newState; }
    public String getReason() { return reason; }
    public MLGPredictionResult getPrediction() { return prediction; }
}