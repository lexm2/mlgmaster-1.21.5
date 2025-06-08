package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public abstract class MLGType {

    /**
     * Checks if this MLG type is applicable for the current situation
     * This method should contain all fall detection logic specific to this MLG type
     */
    public abstract boolean isApplicable(MinecraftClient client, ClientPlayerEntity player, Vec3d velocity);

    /**
     * Checks if this MLG type can be executed (has required items, etc.)
     */
    public abstract boolean canExecute(MinecraftClient client, ClientPlayerEntity player, MLGPredictionResult prediction);

    /**
     * Check if this MLG type can execute right now (without prediction)
     */
    public abstract boolean canExecute(MinecraftClient client, ClientPlayerEntity player);

    /**
     * Execute the MLG placement with prediction
     */
    public abstract boolean execute(MinecraftClient client, ClientPlayerEntity player, MLGPredictionResult prediction);

    /**
     * Execute the MLG placement without prediction
     */
    public abstract boolean execute(MinecraftClient client, ClientPlayerEntity player);

    /**
     * Whether this MLG type requires high frequency timer when applicable
     */
    public abstract boolean requiresHighFrequencyTimer();

    /**
     * Called when the MLG placement was successful (with prediction)
     */
    public abstract void onSuccessfulPlacement(MinecraftClient client, ClientPlayerEntity player,
            MLGPredictionResult prediction, long currentTime);

    /**
     * Called when placement is successful (without prediction)
     */
    public abstract void onSuccessfulPlacement(MinecraftClient client, ClientPlayerEntity player,
            long timestamp);

    /**
     * Called when the player lands or stops falling
     */
    public abstract void handlePostLanding(MinecraftClient client, ClientPlayerEntity player);

    /**
     * Resets the MLG type state
     */
    public abstract void reset();

    /**
     * Gets the priority of this MLG type (higher = more preferred)
     */
    public abstract int getPriority();

    /**
     * Gets the name of this MLG type
     */
    public abstract String getName();
}
