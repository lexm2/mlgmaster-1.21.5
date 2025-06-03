package name.mlgmaster.statemachine;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import name.mlgmaster.WaterMLGHandler.MLGPredictionResult;

public class StateChangeEvent {
    private final FallState fromState;
    private final FallState toState;
    private final String reason;
    private final MinecraftClient client;
    private final ClientPlayerEntity player;
    private final Vec3d playerPosition;
    private final Vec3d playerVelocity;
    private final MLGPredictionResult prediction;
    private final long timestamp;

    public StateChangeEvent(FallState fromState, FallState toState, String reason,
                           MinecraftClient client, ClientPlayerEntity player, 
                           Vec3d playerPosition, Vec3d playerVelocity, 
                           MLGPredictionResult prediction) {
        this.fromState = fromState;
        this.toState = toState;
        this.reason = reason;
        this.client = client;
        this.player = player;
        this.playerPosition = playerPosition;
        this.playerVelocity = playerVelocity;
        this.prediction = prediction;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public FallState getFromState() { return fromState; }
    public FallState getToState() { return toState; }
    public String getReason() { return reason; }
    public MinecraftClient getClient() { return client; }
    public ClientPlayerEntity getPlayer() { return player; }
    public Vec3d getPlayerPosition() { return playerPosition; }
    public Vec3d getPlayerVelocity() { return playerVelocity; }
    public MLGPredictionResult getPrediction() { return prediction; }
    public long getTimestamp() { return timestamp; }
}
