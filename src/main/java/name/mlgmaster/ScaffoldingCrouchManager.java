package name.mlgmaster;

import net.minecraft.client.network.ClientPlayerEntity;

public class ScaffoldingCrouchManager {

    private static boolean forceSneak = false;
    private static boolean wasAlreadySneaking = false;

    /**
     * Called by mixin to check if we should force sneak
     */
    public static boolean shouldForceSneak() {
        return forceSneak;
    }

    /**
     * Activate scaffolding crouch
     */
    public static void activateScaffoldingCrouch(ClientPlayerEntity player) {
        if (!forceSneak) {
            // Store if player was already sneaking
            wasAlreadySneaking = player.isSneaking();

            // Activate forced sneak
            forceSneak = true;

            MLGMaster.LOGGER.info("Activated scaffolding crouch via mixin");
        }
    }

    /**
     * Release scaffolding crouch
     */
    public static void releaseScaffoldingCrouch() {
        if (forceSneak) {
            // Only release if player wasn't already sneaking
            if (!wasAlreadySneaking) {
                forceSneak = false;
                MLGMaster.LOGGER.info("Released scaffolding crouch");
            } else {
                MLGMaster.LOGGER.info("Keeping crouch (player was already sneaking)");
            }

            wasAlreadySneaking = false;
        }
    }

    /**
     * Force release crouch (emergency cleanup)
     */
    public static void forceReleaseCrouch() {
        if (forceSneak) {
            forceSneak = false;
            wasAlreadySneaking = false;
            MLGMaster.LOGGER.info("Force released scaffolding crouch");
        }
    }

    /**
     * Check if currently forcing sneak
     */
    public static boolean isCrouchingForScaffolding() {
        return forceSneak;
    }
}
