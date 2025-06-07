package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import name.mlgmaster.MLGTypes.BlockMLG;
import name.mlgmaster.MLGTypes.WaterMLG;

public class MLGHandler {
    private static boolean isActive = false;
    private static boolean highFreqTimerRunning = false;
    private static long lastPredictionTime = 0;
    private static final long PREDICTION_INTERVAL = 50;
    private static final double FALL_TRIGGER_DISTANCE = 4.5;
    private static final double HIGH_SPEED_THRESHOLD = -3; // 3 blocks per tick
    
    private static final List<MLGType> mlgTypes = new ArrayList<>();
    
    static {
        registerMLGTypes();
    }
    
    private static void registerMLGTypes() {
        mlgTypes.add(new WaterMLG());
        mlgTypes.add(new BlockMLG());
    }

    public static void onHighFrequencyTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        ClientPlayerEntity player = client.player;
        Vec3d velocity = player.getVelocity();

        // Start high frequency timer when falling fast
        if (velocity.y <= HIGH_SPEED_THRESHOLD && !highFreqTimerRunning) {
            MLGHighFrequencyTimer.startHighFrequencyUpdates();
            highFreqTimerRunning = true;
            MLGMaster.LOGGER.info("HIGH FREQUENCY TIMER STARTED: Fall speed {} b/t detected", velocity.y);
        }
        
        // Stop high frequency timer when no longer falling fast
        if ((velocity.y < HIGH_SPEED_THRESHOLD || player.isOnGround()) && highFreqTimerRunning) {
            MLGHighFrequencyTimer.stopHighFrequencyUpdates();
            highFreqTimerRunning = false;
            MLGMaster.LOGGER.info("HIGH FREQUENCY TIMER STOPPED: Fall speed {} b/t, on ground: {}", velocity.y, player.isOnGround());
        }

        // Only consider falling when moving downward
        if (velocity.y >= -0.1 || player.isOnGround()) {
            handleLandingCleanup(client, player);
            return;
        }

        if (player.fallDistance < FALL_TRIGGER_DISTANCE) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        double fallSpeed = Math.abs(velocity.y);
        long dynamicInterval = fallSpeed > 1.0 ? PREDICTION_INTERVAL / 2 : PREDICTION_INTERVAL;

        if (currentTime - lastPredictionTime < dynamicInterval) {
            return;
        }

        lastPredictionTime = currentTime;

        MLGPredictionResult prediction = PlacementTimingCalculator.analyzeFallAndPlacement(client, player, velocity);

        if (prediction.shouldPlace()) {
            executeMLGPlacement(client, player, prediction, currentTime);
        }
    }
    
    private static void handleLandingCleanup(MinecraftClient client, ClientPlayerEntity player) {
        ScaffoldingCrouchManager.releaseScaffoldingCrouch();
        
        for (MLGType mlgType : mlgTypes) {
            mlgType.handlePostLanding(client, player);
        }
        
        if (isActive) {
            isActive = false;
        }
    }
    
    private static void executeMLGPlacement(MinecraftClient client, ClientPlayerEntity player, 
                                         MLGPredictionResult prediction, long currentTime) {
        MLGType suitableType = findSuitableMLGType(client, player, prediction);
        
        if (suitableType != null && suitableType.canExecute(client, player, prediction)) {
        
            // Log placement attempt with distance to ground
            Vec3d playerPos = player.getPos();
            BlockPos targetBlock = prediction.getHighestLandingBlock();
            double distanceToGround = playerPos.y - (targetBlock.getY() + 1.0);
        
            MLGMaster.LOGGER.info("PLACEMENT ATTEMPT: Type: {}, Distance to ground: {} blocks, Target: {}", 
                suitableType.getName(), distanceToGround, targetBlock);
        
            if (suitableType.execute(client, player, prediction)) {
                isActive = true;
                suitableType.onSuccessfulPlacement(client, player, prediction, currentTime);
                lastPredictionTime = currentTime + 40;
            
                MLGMaster.LOGGER.info("PLACEMENT SUCCESS: {} placed successfully at distance {} blocks from ground", 
                    suitableType.getName(), distanceToGround);
            } else {
                MLGMaster.LOGGER.warn("PLACEMENT FAILED: {} failed to place at distance {:.1f} blocks from ground", 
                    suitableType.getName(), distanceToGround);
            }
        } else {
            MLGMaster.LOGGER.warn("PLACEMENT BLOCKED: No suitable MLG type available or cannot execute");
        }
    }
    
    private static MLGType findSuitableMLGType(MinecraftClient client, ClientPlayerEntity player, 
                                             MLGPredictionResult prediction) {
        for (MLGType mlgType : mlgTypes) {
            if (mlgType.isApplicable(client, player, prediction)) {
                return mlgType;
            }
        }
        return null;
    }

    public static boolean isActive() {
        return isActive;
    }

    public static void setActive(boolean active) {
        isActive = active;
        if (!active) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                for (MLGType mlgType : mlgTypes) {
                    mlgType.reset();
                }
            }
        }
    }
    
    public static List<MLGType> getRegisteredTypes() {
        return new ArrayList<>(mlgTypes);
    }
    
    // Add getter for timer status
    public static boolean isHighFrequencyTimerRunning() {
        return highFreqTimerRunning;
    }
    
    // Add method to force stop timer (useful for cleanup)
    public static void forceStopHighFrequencyTimer() {
        if (highFreqTimerRunning) {
            MLGHighFrequencyTimer.stopHighFrequencyUpdates();
            highFreqTimerRunning = false;
            MLGMaster.LOGGER.info("HIGH FREQUENCY TIMER FORCE STOPPED");
        }
    }
}
