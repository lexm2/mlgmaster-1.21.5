package name.mlgmaster.MLGTypes;

import name.mlgmaster.BlockPlacer;
import name.mlgmaster.InventoryManager;
import name.mlgmaster.MLGPredictionResult;
import name.mlgmaster.MLGType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;

public class BlockMLG extends MLGType {

    @Override
    public boolean isApplicable(MinecraftClient client, ClientPlayerEntity player, MLGPredictionResult prediction) {
        return prediction.getTargetItem() != Items.WATER_BUCKET && 
               prediction.getTargetItem() instanceof BlockItem;
    }

    @Override
    public boolean canExecute(MinecraftClient client, ClientPlayerEntity player, MLGPredictionResult prediction) {
        return InventoryManager.hasItem(player, prediction.getTargetItem());
    }

    @Override
    public boolean execute(MinecraftClient client, ClientPlayerEntity player, MLGPredictionResult prediction) {
        return BlockPlacer.executeBlockPlacement(client, player, prediction);
    }

    @Override
    public void onSuccessfulPlacement(MinecraftClient client, ClientPlayerEntity player, 
                                    MLGPredictionResult prediction, long currentTime) {
    }

    @Override
    public void handlePostLanding(MinecraftClient client, ClientPlayerEntity player) {
        // No post-landing cleanup needed for blocks
    }

    @Override
    public void reset() {
        // No state to reset for basic block MLG
    }

    @Override
    public int getPriority() {
        return 5; // Lower priority than water MLG
    }

    @Override
    public String getName() {
        return "Block MLG";
    }
}