package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import name.mlgmaster.mixin.ClientPlayerInteractionManagerAccessor;

public class MLGBlockPlacer {
    public static boolean placeItem(MinecraftClient client, ClientPlayerEntity player, Hand hand) {
        ClientPlayerInteractionManagerAccessor accessor =
                (ClientPlayerInteractionManagerAccessor) client.interactionManager;

        try {
            ActionResult result = accessor.invokeInteractItem(player, hand);
            boolean success = result.isAccepted();
            MLGMaster.LOGGER.info("Used placeItem");
            return success;
        } catch (Exception e) {
            MLGMaster.LOGGER.warn("Item placement failed for hand {}: {}", hand, e.getMessage());
            return false;
        }
    }

    public static boolean interactBlock(MinecraftClient client, ClientPlayerEntity player,
            Hand hand, BlockPos targetPos, Vec3d hitPos, Direction side) {
        ClientPlayerInteractionManagerAccessor accessor =
                (ClientPlayerInteractionManagerAccessor) client.interactionManager;

        try {
            BlockHitResult hitResult = new BlockHitResult(hitPos, side, targetPos, false);
            ActionResult result = accessor.invokeInteractBlock(player, hand, hitResult);
            boolean success = result.isAccepted();
            return success;
        } catch (Exception e) {
            MLGMaster.LOGGER.warn("Block interaction failed at {}: {}", targetPos, e.getMessage());
            return false;
        }
    }
}
