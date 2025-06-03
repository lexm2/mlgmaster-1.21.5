package name.mlgmaster.statemachine;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import name.mlgmaster.WaterMLGHandler.MLGPredictionResult;

public class EnhancedStateChangeEvent extends StateChangeEvent {
    private final WaterMLGStateMachine stateMachine;

    public EnhancedStateChangeEvent(FallState fromState, FallState toState, String reason,
            MinecraftClient client, ClientPlayerEntity player, Vec3d playerPosition,
            Vec3d playerVelocity, MLGPredictionResult prediction, WaterMLGStateMachine stateMachine) {
        super(fromState, toState, reason, client, player, playerPosition, playerVelocity, prediction);
        this.stateMachine = stateMachine;
    }

    public WaterMLGStateMachine getStateMachine() {
        return stateMachine;
    }

    public Vec3d getFallStartPosition() {
        return stateMachine.getFallStartPosition();
    }

    public long getStateStartTime() {
        return stateMachine.getStateStartTime();
    }

    public boolean isWaterPlacementAttempted() {
        return stateMachine.isWaterPlacementAttempted();
    }

    public long getFallDuration() {
        Vec3d fallStart = getFallStartPosition();
        if (fallStart == null) return 0;
        return getTimestamp() - getStateStartTime();
    }

    public double getFallDistance() {
        Vec3d fallStart = getFallStartPosition();
        Vec3d currentPos = getPlayerPosition();
        if (fallStart == null || currentPos == null) return 0.0;
        return fallStart.y - currentPos.y;
    }
}