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

    public static boolean placeWater(MinecraftClient client, ClientPlayerEntity player, Hand hand,
            BlockPos targetPos, Vec3d hitPos) {
        ClientPlayerInteractionManagerAccessor accessor =
                (ClientPlayerInteractionManagerAccessor) client.interactionManager;

        // Strategy 1: Try item interaction first (more reliable for water buckets)
        try {
            ActionResult itemResult = accessor.invokeInteractItem(player, hand);
            if (itemResult.isAccepted()) {
                MLGMaster.LOGGER.info("Water placed successfully with item interaction");
                return true;
            }
        } catch (Exception e) {
            MLGMaster.LOGGER.warn("Item interaction failed: {}", e.getMessage());
        }

        // Strategy 2: Try block interaction
        try {
            BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, targetPos, false);
            ActionResult blockResult = accessor.invokeInteractBlock(player, hand, hitResult);
            if (blockResult.isAccepted()) {
                MLGMaster.LOGGER.info("Water placed successfully with block interaction");
                return true;
            }
        } catch (Exception e) {
            MLGMaster.LOGGER.warn("Block interaction failed: {}", e.getMessage());
        }

        // Strategy 3: Try internal block interaction as fallback
        try {
            BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, targetPos, false);
            ActionResult internalResult =
                    accessor.invokeInteractBlockInternal(player, hand, hitResult);
            if (internalResult.isAccepted()) {
                MLGMaster.LOGGER.info("Water placed successfully with internal block interaction");
                return true;
            }
        } catch (Exception e) {
            MLGMaster.LOGGER.warn("Internal block interaction failed: {}", e.getMessage());
        }

        return false;
    }

    public static boolean placeItem(MinecraftClient client, ClientPlayerEntity player, Hand hand) {
        ClientPlayerInteractionManagerAccessor accessor =
                (ClientPlayerInteractionManagerAccessor) client.interactionManager;

        try {
            ActionResult result = accessor.invokeInteractItem(player, hand);
            boolean success = result.isAccepted();
            if (success) {
                MLGMaster.LOGGER.info("Item placed successfully with hand: {}", hand);
            }
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
            if (success) {
                MLGMaster.LOGGER.info("Block interaction successful at {} with side {}", targetPos,
                        side);
            }
            return success;
        } catch (Exception e) {
            MLGMaster.LOGGER.warn("Block interaction failed at {}: {}", targetPos, e.getMessage());
            return false;
        }
    }

    public static boolean interactBlockInternal(MinecraftClient client, ClientPlayerEntity player,
            Hand hand, BlockPos targetPos, Vec3d hitPos, Direction side) {
        ClientPlayerInteractionManagerAccessor accessor =
                (ClientPlayerInteractionManagerAccessor) client.interactionManager;

        try {
            BlockHitResult hitResult = new BlockHitResult(hitPos, side, targetPos, false);
            ActionResult result = accessor.invokeInteractBlockInternal(player, hand, hitResult);
            boolean success = result.isAccepted();
            if (success) {
                MLGMaster.LOGGER.info("Internal block interaction successful at {} with side {}",
                        targetPos, side);
            }
            return success;
        } catch (Exception e) {
            MLGMaster.LOGGER.warn("Internal block interaction failed at {}: {}", targetPos,
                    e.getMessage());
            return false;
        }
    }
}
