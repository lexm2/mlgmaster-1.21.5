package name.mlgmaster.statemachine;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public interface FallStateHandler {
    /**
     * Process the current state and determine if a state transition should occur
     * @return new state if transition should occur, null if staying in current state
     */
    StateTransition processState(MinecraftClient client, ClientPlayerEntity player, 
                               Vec3d position, Vec3d velocity, WaterMLGStateMachine stateMachine);
    
    /**
     * Called when entering this state
     */
    void onEnter(StateChangeEvent event);
    
    /**
     * Called when exiting this state
     */
    void onExit(StateChangeEvent event);
    
    /**
     * Get the state this handler manages
     */
    FallState getHandledState();
}