package name.mlgmaster;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MLGMaster implements ModInitializer {
	public static final String MOD_ID = "mlgmaster";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Register tick event with cached client reference
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client != null && client.player != null && client.world != null) {
				WaterMLGHandler.onHighFrequencyTick();
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ScaffoldingCrouchManager.forceReleaseCrouch();
			// Also stop any active fall tracking when disconnecting
			WaterMLGHandler.forceStopTracking();
		});

		LOGGER.info("WaterMLG mod initialized successfully!");
	}

}
