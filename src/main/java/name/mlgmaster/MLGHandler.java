package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import name.mlgmaster.MLGTypes.BlockMLG;
import name.mlgmaster.MLGTypes.WaterMLG;

public class MLGHandler {
    private static boolean highFreqTimerRunning = false;
    private static long lastPredictionTime = 0;
    private static final long PREDICTION_INTERVAL = 50;

    private static final List<MLGType> mlgTypes = new ArrayList<>();

    // Landing prediction rendering
    private static BlockPos predictedLandingBlock = null;
    private static BlockPos previousPredictedLandingBlock = null; // Track previous prediction
    private static Vec3d predictedLandingPosition = null;
    private static Vec3d previousPredictedLandingPosition = null;

    public static class MLGApplicabilityResult {
        private final MLGType type;
        private final String name;
        private final boolean isApplicable;
        private final int priority; // Higher number = higher priority

        public MLGApplicabilityResult(MLGType type, String name, boolean isApplicable,
                int priority) {
            this.type = type;
            this.name = name;
            this.isApplicable = isApplicable;
            this.priority = priority;
        }

        @Override
        public String toString() {
            return String.format("MLGApplicabilityResult{name='%s', applicable=%s, priority=%d}",
                    name, isApplicable, priority);
        }

        public MLGType getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public boolean isApplicable() {
            return isApplicable;
        }

        public int getPriority() {
            return priority;
        }
    }

    static {
        registerMLGTypes();
    }

    private static void registerMLGTypes() {
        mlgTypes.add(new WaterMLG());
        mlgTypes.add(new BlockMLG());
    }

    public static void onHighFrequencyTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        Vec3d velocity = player.getVelocity();

        if (velocity.y >= -0.1 || player.isOnGround()) {
            handleCleanup(client, player);
            return;
        }

        updateLandingPrediction(client, player, velocity);

        MLGPredictionResult prediction =
                FallPredictionSystem.analyzeFallAndPlacement(client, player, velocity);

        List<MLGApplicabilityResult> applicabilityResults =
                evaluateMLGTypes(client, player, velocity, prediction);

        handleHighFrequencyTimer(client, player, velocity, applicabilityResults);

        

        MLGApplicabilityResult chosenMLG = selectBestMLGType(applicabilityResults);

        if (chosenMLG != null) {
            executeChosenMLG(client, player, chosenMLG, prediction);
        }
    }

    private static void updateLandingPrediction(MinecraftClient client, ClientPlayerEntity player,
            Vec3d velocity) {
        try {
            HitboxLandingResult landingResult = FallPredictionSystem.predictLanding(client, player);

            if (landingResult != null) {
                // Update current predictions
                predictedLandingBlock = landingResult.getPrimaryLandingBlock();
                predictedLandingPosition = landingResult.getLandingPosition();

                // Check if landing block prediction changed
                if (hasLandingBlockChanged()) {
                    logLandingBlockChange();
                }
            } else {
                if (predictedLandingBlock != null) {
                    MLGMaster.LOGGER.warn(
                            "LANDING PREDICTION LOST: Previous block was {}, now no landing predicted",
                            predictedLandingBlock);
                }

                previousPredictedLandingBlock = predictedLandingBlock;
                previousPredictedLandingPosition = predictedLandingPosition;
            }
        } catch (Exception e) {
            MLGMaster.LOGGER.warn("Error updating landing prediction: {}", e.getMessage());
            previousPredictedLandingBlock = predictedLandingBlock;
            previousPredictedLandingPosition = predictedLandingPosition;
            predictedLandingBlock = null;
            predictedLandingPosition = null;
        }
    }

    private static boolean hasLandingBlockChanged() {
        if (previousPredictedLandingBlock == null || predictedLandingBlock == null) {
            return false;
        }

        return !previousPredictedLandingBlock.equals(predictedLandingBlock);
    }

    private static void logLandingBlockChange() {
        MinecraftClient client = MinecraftClient.getInstance();

        String previousBlockType = "null";
        String currentBlockType = "null";

        if (previousPredictedLandingBlock != null && client.world != null) {
            previousBlockType = client.world.getBlockState(previousPredictedLandingBlock).getBlock()
                    .getName().getString();
        }

        if (predictedLandingBlock != null && client.world != null) {
            currentBlockType = client.world.getBlockState(predictedLandingBlock).getBlock()
                    .getName().getString();
        }

        MLGMaster.LOGGER.info("LANDING PREDICTION CHANGED: {} -> {}", previousBlockType,
                currentBlockType);
        MLGMaster.LOGGER.info("PREDICTED LANDING POSITIONS: {} -> {}", predictedLandingPosition,
                previousPredictedLandingPosition);
    }



    private static List<MLGApplicabilityResult> evaluateMLGTypes(MinecraftClient client,
            ClientPlayerEntity player, Vec3d velocity, MLGPredictionResult prediction) {
        List<MLGApplicabilityResult> results = new ArrayList<>();

        for (MLGType mlgType : mlgTypes) {
            boolean applicable = mlgType.isApplicable(client, player, velocity, prediction);
            int priority = mlgType.getPriority();

            results.add(
                    new MLGApplicabilityResult(mlgType, mlgType.getName(), applicable, priority));
        }

        return results;
    }

    private static void handleHighFrequencyTimer(MinecraftClient client, ClientPlayerEntity player,
            Vec3d velocity, List<MLGApplicabilityResult> results) {

        boolean shouldRunHighFreq = results.stream().anyMatch(
                result -> result.isApplicable() && result.getType().requiresHighFrequencyTimer());

        if (shouldRunHighFreq && !highFreqTimerRunning) {
            MLGHighFrequencyTimer.startHighFrequencyUpdates();
            highFreqTimerRunning = true;
            MLGMaster.LOGGER.info("HIGH FREQUENCY TIMER STARTED: Fall speed {} b/t detected",
                    velocity.y);
        }

        if (!shouldRunHighFreq && highFreqTimerRunning) {
            MLGHighFrequencyTimer.stopHighFrequencyUpdates();
            highFreqTimerRunning = false;
            MLGMaster.LOGGER.info("HIGH FREQUENCY TIMER STOPPED: Fall speed {} b/t, on ground: {}",
                    velocity.y, player.isOnGround());
        }
    }

    private static MLGApplicabilityResult selectBestMLGType(List<MLGApplicabilityResult> results) {
        return results.stream().filter(MLGApplicabilityResult::isApplicable)
                .max((r1, r2) -> Integer.compare(r1.getPriority(), r2.getPriority())).orElse(null);
    }

    private static void executeChosenMLG(MinecraftClient client, ClientPlayerEntity player,
            MLGApplicabilityResult chosenMLG, MLGPredictionResult prediction) {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastPredictionTime < getDynamicInterval(player.getVelocity().y)) {
            return;
        }

        lastPredictionTime = currentTime;

        MLGType mlgType = chosenMLG.getType();

        if (mlgType.canExecute(client, player, prediction)) {
            if (mlgType.execute(client, player, prediction)) {
                mlgType.onSuccessfulPlacement(client, player, currentTime);
                lastPredictionTime = currentTime + 40;

                MLGMaster.LOGGER.info("MLG SUCCESS: {} executed successfully", chosenMLG.getName());
            }
        } else {
            MLGMaster.LOGGER.warn("MLG BLOCKED: {} cannot execute at this time",
                    chosenMLG.getName());
        }
    }

    private static long getDynamicInterval(double fallSpeed) {
        double absFallSpeed = Math.abs(fallSpeed);
        return absFallSpeed > 1.0 ? PREDICTION_INTERVAL / 2 : PREDICTION_INTERVAL;
    }

    public static void handleCleanup(MinecraftClient client, ClientPlayerEntity player) {
        ScaffoldingCrouchManager.releaseScaffoldingCrouch();

        // Clear landing prediction when landing/cleanup
        previousPredictedLandingBlock = predictedLandingBlock;
        predictedLandingBlock = null;
        predictedLandingPosition = null;

        for (MLGType mlgType : mlgTypes) {
            mlgType.handlePostLanding(client, player);
        }
    }

    public static List<MLGType> getRegisteredTypes() {
        return new ArrayList<>(mlgTypes);
    }

    public static boolean isHighFrequencyTimerRunning() {
        return highFreqTimerRunning;
    }

    public static void forceStopHighFrequencyTimer() {
        if (highFreqTimerRunning) {
            MLGHighFrequencyTimer.stopHighFrequencyUpdates();
            highFreqTimerRunning = false;
            MLGMaster.LOGGER.info("HIGH FREQUENCY TIMER FORCE STOPPED");
        }
    }

    // Public getters for rendering system
    public static BlockPos getPredictedLandingBlock() {
        return predictedLandingBlock;
    }

    public static Vec3d getPredictedLandingPosition() {
        return predictedLandingPosition;
    }

    public static BlockPos getPreviousPredictedLandingBlock() {
        return previousPredictedLandingBlock;
    }
}
