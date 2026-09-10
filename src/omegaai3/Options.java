package omegaai3;

public record Options(boolean enabled, boolean coordinate, boolean playerFleet, boolean enemyFleet,
                      boolean simulator, boolean status, boolean logging, String conflict) {
    public static Options defaults() { return new Options(true, false, true, true, true, true, false, ""); }
    public boolean includes(int side) { return side == 0 ? playerFleet : side == 1 && enemyFleet; }
    public boolean mayIssueOrders() { return enabled && coordinate && conflict.isEmpty(); }
}
