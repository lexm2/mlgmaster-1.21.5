package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public abstract class MLGType {
    
    /**
     * Checks if this MLG type is applicable for the current situation
     */
    public abstract boolean isApplicable(MinecraftClient client, ClientPlayerEntity player, MLGPredictionResult prediction);
    
    /**
     * Checks if this MLG type can be executed (has required items, etc.)
     */
    public abstract boolean canExecute(MinecraftClient client, ClientPlayerEntity player, MLGPredictionResult prediction);
    
    /**
     * Executes the MLG placement
     */
    public abstract boolean execute(MinecraftClient client, ClientPlayerEntity player, MLGPredictionResult prediction);
    
    /**
     * Called when the MLG placement was successful
     */
    public abstract void onSuccessfulPlacement(MinecraftClient client, ClientPlayerEntity player, 
                                             MLGPredictionResult prediction, long currentTime);
    
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