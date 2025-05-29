package name.mlgmaster;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.client.network.ClientPlayerEntity;

public class SafeLandingBlockChecker {

    public static class SafetyResult {
        private final boolean isSafe;
        private final String reason;
        private final boolean needsCrouching;

        public SafetyResult(boolean isSafe, String reason, boolean needsCrouching) {
            this.isSafe = isSafe;
            this.reason = reason;
            this.needsCrouching = needsCrouching;
        }

        public SafetyResult(boolean isSafe, String reason) {
            this(isSafe, reason, false);
        }

        public boolean isSafe() {
            return isSafe;
        }

        public String getReason() {
            return reason;
        }

        public boolean needsCrouching() {
            return needsCrouching;
        }

        @Override
        public String toString() {
            return String.format("SafetyResult{safe=%s, reason='%s', needsCrouching=%s}",
                    isSafe, reason, needsCrouching);
        }
    }

    /**
     * Check if a landing block is safe given the current conditions
     */
    public static SafetyResult checkLandingSafety(MinecraftClient client, ClientPlayerEntity player,
            BlockPos landingBlock, Vec3d currentPlayerPos) {
        if (client.world == null) {
            return new SafetyResult(false, "World not available");
        }

        BlockState landingState = client.world.getBlockState(landingBlock);
        Block landingBlockType = landingState.getBlock();

        MLGMaster.LOGGER.info("Checking landing safety for block: {} at position: {}",
                landingBlockType.getName().getString(), landingBlock);

        // Water is always safe
        if (landingBlockType == Blocks.WATER) {
            return new SafetyResult(true, "Water always prevents fall damage");
        }

        // Twisted vines prevent fall damage
        if (landingBlockType == Blocks.TWISTING_VINES || landingBlockType == Blocks.TWISTING_VINES_PLANT) {
            return new SafetyResult(true, "Twisted vines prevent fall damage");
        }

        // Slime blocks are safe if no carpet on top
        if (landingBlockType == Blocks.SLIME_BLOCK) {
            return new SafetyResult(true, "Slime blocks prevent fall damage");
        }

        // Powder snow is safe if no carpet on top
        if (landingBlockType == Blocks.POWDER_SNOW) {
            return new SafetyResult(true, "Powder snow prevent fall damage");
        }

        // Check carpet with slime/powder snow below
        if (landingState.isIn(BlockTags.WOOL_CARPETS)) {
            return checkCarpetWithSafeBlockBelow(client, landingBlock);
        }

        // Check scaffolding with crouch requirement
        if (landingBlockType == Blocks.SCAFFOLDING) {
            return checkScaffoldingWithCrouch(client, player, currentPlayerPos, landingBlock);
        }

        // Everything else is not safe - need water clutch
        return new SafetyResult(false,
                String.format("Block '%s' is not safe for landing - need water clutch",
                        landingBlockType.getName().getString()));
    }

    /**
     * Check if carpet has slime block or powder snow below it
     */
    private static SafetyResult checkCarpetWithSafeBlockBelow(MinecraftClient client, BlockPos carpetPos) {
        BlockPos belowPos = carpetPos.down();
        BlockState belowState = client.world.getBlockState(belowPos);
        Block belowBlock = belowState.getBlock();

        MLGMaster.LOGGER.info("Checking carpet at {} | Block below: {}",
                carpetPos, belowBlock.getName().getString());

        if (belowBlock == Blocks.SLIME_BLOCK) {
            return new SafetyResult(true, "Carpet with slime block below - safe landing");
        }

        if (belowBlock == Blocks.POWDER_SNOW) {
            return new SafetyResult(true, "Carpet with powder snow below - safe landing");
        }

        return new SafetyResult(false,
                String.format("Carpet with unsafe block below (%s) - need water clutch",
                        belowBlock.getName().getString()));
    }

    /**
     * Check scaffolding safety and handle crouch requirement
     */
    private static SafetyResult checkScaffoldingWithCrouch(MinecraftClient client, ClientPlayerEntity player,
            Vec3d currentPos, BlockPos scaffoldingPos) {
        double fallDistance = currentPos.y - scaffoldingPos.getY();

        MLGMaster.LOGGER.info("Checking scaffolding safety: fall distance = {:.1f} blocks", fallDistance);

        if (fallDistance >= 150.0) {
            return new SafetyResult(false,
                    String.format(
                            "Scaffolding unsafe for %.1f block fall (exceeds 150 block limit) - need water clutch",
                            fallDistance));
        }

        // This line activates the mixin crouch
        ScaffoldingCrouchManager.activateScaffoldingCrouch(player);

        MLGMaster.LOGGER.info("Scaffolding requires crouching - activated crouch for {:.1f} block fall",
                fallDistance);
        return new SafetyResult(true,
                String.format("Scaffolding safe with forced crouch for %.1f block fall", fallDistance),
                true);
    }

    /**
     * Simple check if we should skip water placement
     */
    public static boolean shouldSkipWaterPlacement(MinecraftClient client, ClientPlayerEntity player,
            BlockPos landingBlock, Vec3d currentPlayerPos) {
        SafetyResult result = checkLandingSafety(client, player, landingBlock, currentPlayerPos);

        MLGMaster.LOGGER.info("Landing safety evaluation: {}", result);

        if (result.isSafe()) {
            MLGMaster.LOGGER.info("✅ Skipping water placement: {}", result.getReason());
            return true;
        } else {
            MLGMaster.LOGGER.info("⚠️ Water placement needed: {}", result.getReason());
            return false;
        }
    }

    /**
     * Quick check for obviously safe blocks
     */
    public static boolean isObviouslySafe(Block block) {
        return block == Blocks.WATER ||
                block == Blocks.TWISTING_VINES ||
                block == Blocks.TWISTING_VINES_PLANT ||
                block == Blocks.SLIME_BLOCK ||
                block == Blocks.POWDER_SNOW;
    }
}
