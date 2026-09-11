package omegaai3;

public record Options(boolean enabled, boolean coordinate, boolean playerFleet, boolean enemyFleet,
                      boolean simulator, boolean status, boolean logging, String conflict, boolean labels) {
    public Options(boolean enabled, boolean coordinate, boolean playerFleet, boolean enemyFleet,
                   boolean simulator, boolean status, boolean logging, String conflict) {
        this(enabled, coordinate, playerFleet, enemyFleet, simulator, status, logging, conflict, true);
    }
    public static Options defaults() { return new Options(true, false, true, true, true, true, false, ""); }
    public boolean includes(int side) { return side == 0 ? playerFleet : side == 1 && enemyFleet; }
    public boolean mayIssueOrders() { return enabled && coordinate && conflict.isEmpty(); }
    public String effectiveMode() { return !enabled ? "Disabled" : !coordinate ? "Observe" : conflict.isEmpty() ? "Coordinate" : "Blocked"; }
}
