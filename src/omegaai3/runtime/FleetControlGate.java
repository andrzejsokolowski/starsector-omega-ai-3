package omegaai3.runtime;

import com.fs.starfarer.api.combat.*;
import omegaai3.Options;

/** Read-only command precedence for direct piloting. */
public final class FleetControlGate {
    private FleetControlGate() {}
    public static String blockReason(CombatEngineAPI engine, ShipAPI ship, Options options) {
        try {
            if (!options.enabled()) return "Omega disabled";
            if (!options.includes(ship.getOwner())) return "Fleet excluded";
            String pilot = ControlGate.blockReason(engine, ship);
            if (!pilot.isEmpty()) return pilot;
            CombatTaskManagerAPI tasks = engine.getFleetManager(ship.getOwner()).getTaskManager(false);
            if (tasks.isInFullRetreat() || tasks.isFullAssault()) return "Fleet assault or retreat directive";
            for (var order : tasks.getAllAssignments()) if (order.getType() == CombatAssignmentType.AVOID || order.getType() == CombatAssignmentType.IGNORE)
                return "Fleet avoid or ignore directive";
            if (engine.getFleetManager(1 - ship.getOwner()).getTaskManager(false).isInFullRetreat()) return "Native routed-enemy pursuit";
            var order = tasks.getAssignmentFor(ship);
            return order == null || order.getType() == CombatAssignmentType.SEARCH_AND_DESTROY ? "" : "Existing " + order.getType() + " order";
        } catch (RuntimeException e) { return "Unreadable control state"; }
    }
}
