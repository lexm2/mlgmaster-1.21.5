package name.mlgmaster.statemachine;

import net.minecraft.util.math.Vec3d;

public class FallStatistics {
    private final Vec3d startPosition;
    private final Vec3d endPosition;
    private final long startTime;
    private final long endTime;
    private final boolean waterPlaced;
    private final boolean successful;

    public FallStatistics(Vec3d startPosition, Vec3d endPosition, long startTime, long endTime, 
                         boolean waterPlaced, boolean successful) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.startTime = startTime;
        this.endTime = endTime;
        this.waterPlaced = waterPlaced;
        this.successful = successful;
    }

    public double getFallDistance() {
        if (startPosition == null || endPosition == null) return 0.0;
        return startPosition.y - endPosition.y;
    }

    public long getDuration() {
        return endTime - startTime;
    }

    public double getHorizontalDistance() {
        if (startPosition == null || endPosition == null) return 0.0;
        return Math.sqrt(Math.pow(endPosition.x - startPosition.x, 2) + 
                        Math.pow(endPosition.z - startPosition.z, 2));
    }

    // Getters
    public Vec3d getStartPosition() { return startPosition; }
    public Vec3d getEndPosition() { return endPosition; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public boolean wasWaterPlaced() { return waterPlaced; }
    public boolean wasSuccessful() { return successful; }
}