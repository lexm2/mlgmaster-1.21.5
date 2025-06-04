package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;

/**
 * Manages automatic crouching/sneaking behavior for scaffolding operations. This class actively
 * controls the player's sneak state during scaffolding.
 */
public class ScaffoldingCrouchManager {

    private static boolean isScaffoldingCrouchActive = false;
    private static boolean wasAlreadySneaking = false;
    private static MinecraftClient client;
    private static KeyBinding sneakKey;

    /**
     * Activates scaffolding crouch mode for the given player. Actually makes the player crouch by
     * manipulating the sneak key state.
     * 
     * @param player the client player entity
     * @param minecraftClient the minecraft client instance
     */
    public static void activateScaffoldingCrouch(ClientPlayerEntity player,
            MinecraftClient minecraftClient) {
        if (player == null || minecraftClient == null) {
            return;
        }

        if (!isScaffoldingCrouchActive) {
            client = minecraftClient;
            sneakKey = client.options.sneakKey;

            // Store if player was already sneaking
            wasAlreadySneaking = player.isSneaking();

            // Actually make the player crouch
            if (!wasAlreadySneaking) {
                KeyBinding.setKeyPressed(sneakKey.getDefaultKey(), true);
                KeyBinding.onKeyPressed(sneakKey.getDefaultKey());
            }

            isScaffoldingCrouchActive = true;
            MLGMaster.LOGGER.debug("Activated scaffolding crouch (was already sneaking: {})",
                    wasAlreadySneaking);
        }
    }

    /**
     * Alternative activation method using direct player state manipulation
     */
    public static void activateScaffoldingCrouchDirect(ClientPlayerEntity player,
            MinecraftClient minecraftClient) {
        if (player == null || minecraftClient == null) {
            return;
        }

        if (!isScaffoldingCrouchActive) {
            client = minecraftClient;

            // Store if player was already sneaking
            wasAlreadySneaking = player.isSneaking();

            // Directly set the player's sneaking state
            if (!wasAlreadySneaking) {
                player.setSneaking(true);
            }

            isScaffoldingCrouchActive = true;
            MLGMaster.LOGGER.debug("Activated scaffolding crouch direct (was already sneaking: {})",
                    wasAlreadySneaking);
        }
    }

    /**
     * Releases scaffolding crouch mode. Actually stops the player from crouching if they weren't
     * already sneaking.
     */
    public static void releaseScaffoldingCrouch() {
        if (isScaffoldingCrouchActive && client != null) {
            if (!wasAlreadySneaking) {
                // Release the sneak key
                if (sneakKey != null) {
                    KeyBinding.setKeyPressed(sneakKey.getDefaultKey(), false);
                }

                // Or directly set player state
                ClientPlayerEntity player = client.player;
                if (player != null) {
                    player.setSneaking(false);
                }

                MLGMaster.LOGGER.debug("Released scaffolding crouch");
            } else {
                MLGMaster.LOGGER.debug("Keeping crouch - player was already sneaking");
            }

            wasAlreadySneaking = false;
            isScaffoldingCrouchActive = false;
        }
    }

    /**
     * Forces an immediate release of crouch mode. Used for emergency cleanup when normal release
     * logic shouldn't apply.
     */
    public static void forceReleaseCrouch() {
        if (isScaffoldingCrouchActive && client != null) {
            // Force release sneak key
            if (sneakKey != null) {
                KeyBinding.setKeyPressed(sneakKey.getDefaultKey(), false);
            }

            // Force release player state
            ClientPlayerEntity player = client.player;
            if (player != null) {
                player.setSneaking(false);
            }

            wasAlreadySneaking = false;
            isScaffoldingCrouchActive = false;
            MLGMaster.LOGGER.debug("Force released scaffolding crouch");
        }
    }

    /**
     * Maintains the crouch state during tick updates. Call this every tick to ensure the crouch
     * remains active.
     */
    public static void tick() {
        if (isScaffoldingCrouchActive && client != null && client.player != null) {
            if (!wasAlreadySneaking && !client.player.isSneaking()) {
                // Re-apply crouch if it was lost
                client.player.setSneaking(true);
            }
        }
    }

    /**
     * Checks if scaffolding crouch is currently active.
     * 
     * @return true if currently forcing sneak for scaffolding, false otherwise
     */
    public static boolean isCrouchingForScaffolding() {
        return isScaffoldingCrouchActive;
    }

    /**
     * Resets the manager to its initial state. Useful for cleanup when switching worlds or
     * disconnecting.
     */
    public static void reset() {
        if (isScaffoldingCrouchActive) {
            forceReleaseCrouch();
        }

        isScaffoldingCrouchActive = false;
        wasAlreadySneaking = false;
        client = null;
        sneakKey = null;
        MLGMaster.LOGGER.debug("Reset scaffolding crouch manager");
    }
}
