package name.mlgmaster.statemachine;

public interface StateChangeListener {
    /**
     * Called for every state change
     */
    void onStateChanged(StateChangeEvent event);

    /**
     * Called when a fall is first detected
     */
    default void onFallDetected(StateChangeEvent event) {}

    /**
     * Called when starting to track a fall
     */
    default void onFallTracking(StateChangeEvent event) {}

    /**
     * Called when ready to place water
     */
    default void onReadyToPlace(StateChangeEvent event) {}

    /**
     * Called when water placement is attempted
     */
    default void onWaterPlaced(StateChangeEvent event) {}

    /**
     * Called when a fall ends
     */
    default void onFallEnded(StateChangeEvent event) {}

    /**
     * Called when returning to idle state
     */
    default void onResetToIdle(StateChangeEvent event) {}
}