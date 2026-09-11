package omegaai3.tactics;

import omegaai3.model.Vec;

/** A velocity/facing goal consumed inside OmegaShipAI, not a fleet waypoint or a display-only posture. */
public record TacticalIntent(Action action, String targetId, Vec velocity, double facing, double expires,
                             String reason, double dangerSeconds) {
    public enum Action {
        ADVANCE("Advancing"), ENGAGE("Engaging"), PURSUE("Pursuing"), RETREAT("Retreating"),
        WAIT("Waiting for support"), DISENGAGE("Disengaging");
        public final String label;
        Action(String label) { this.label = label; }
    }
}
