package name.mlgmaster;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.MinecraftClient;

public class MLGHighFrequencyTimer {
    private static ScheduledExecutorService scheduler;
    private static volatile boolean isRunning = false;

    public static void startHighFrequencyUpdates() {
        if (scheduler != null) {
            stopHighFrequencyUpdates();
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MLG-HighFreq-Timer");
            t.setDaemon(true);
            return t;
        });

        isRunning = true;

        scheduler.scheduleAtFixedRate(() -> {
            if (!isRunning)
                return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.execute(() -> {
                    if (client.player != null && client.world != null) {
                        MLGHandler.onHighFrequencyTick();
                    }
                });
            }
        }, 0, 2, TimeUnit.MILLISECONDS);
    }

    public static void stopHighFrequencyUpdates() {
        isRunning = false;
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
    }
}

