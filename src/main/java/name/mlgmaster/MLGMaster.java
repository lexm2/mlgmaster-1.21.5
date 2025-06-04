package name.mlgmaster;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MLGMaster implements ModInitializer {
	public static final String MOD_ID = "mlgmaster";
	private static final Logger RAW_LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final FormattedLogger LOGGER = new FormattedLogger(RAW_LOGGER);

	@Override
	public void onInitialize() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client != null && client.player != null && client.world != null) {
				WaterMLGHandler.onHighFrequencyTick();
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ScaffoldingCrouchManager.forceReleaseCrouch();
		});

		LOGGER.info("WaterMLG mod initialized successfully!");
	}

	public static class FormattedLogger {
		private final Logger logger;

		public FormattedLogger(Logger logger) {
			this.logger = logger;
		}

		public void info(String message, Object... args) {
			logger.info(formatMessage(message, args));
		}

		public void warn(String message, Object... args) {
			logger.warn(formatMessage(message, args));
		}

		public void error(String message, Object... args) {
			logger.error(formatMessage(message, args));
		}

		public void debug(String message, Object... args) {
			logger.debug(formatMessage(message, args));
		}

		private String formatMessage(String message, Object... args) {
			if (args.length == 0) {
				return message;
			}

			Object[] formattedArgs = new Object[args.length];
			for (int i = 0; i < args.length; i++) {
				formattedArgs[i] = formatArgument(args[i]);
			}

			return String.format(message.replace("{}", "%s"), formattedArgs);
		}

		private Object formatArgument(Object arg) {
			if (arg instanceof Double) {
				return String.format("%.3f", (Double) arg);
			} else if (arg instanceof Float) {
				return String.format("%.3f", (Float) arg);
			} else if (arg != null && arg.toString().matches("-?\\d+\\.\\d+")) {
				try {
					double value = Double.parseDouble(arg.toString());
					return String.format("%.3f", value);
				} catch (NumberFormatException e) {
					return arg;
				}
			}
			return arg;
		}
	}
}
