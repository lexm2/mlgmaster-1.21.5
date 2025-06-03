package name.mlgmaster.statemachine;

public enum FallState {
    IDLE("Not tracking any fall"),
    FALL_DETECTED("Fall detected, analyzing conditions"),
    TRACKING_FALL("Actively tracking fall with prediction"),
    READY_TO_PLACE("Within placement range, ready to place water"),
    WATER_PLACED("Water placed, monitoring fall completion"),
    FALL_ENDED("Fall completed, ready to reset");

    private final String description;

    FallState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}