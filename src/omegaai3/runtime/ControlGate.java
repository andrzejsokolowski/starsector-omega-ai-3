package omegaai3.runtime;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.Map;

public final class ControlGate {
    private static final String[] RTS_KEYS = {"RTS_BLOCKAI_ForYourOwnSakeDontUseThis",
            "RTS_OVERRIDETHRUST_ForYourOwnSakeDontUseThis", "RTS_OVERRIDEFACING_ForYourOwnSakeDontUseThis"};
    private ControlGate() {}
    public static boolean knownPilot(String name) { return "com.fs.starfarer.combat.ai.BasicShipAI".equals(name) || OmegaShipAI.class.getName().equals(name); }
    public static boolean externallyControlled(Map<String, Object> data) {
        for (String key : RTS_KEYS) {
            Object value = data.get(key);
            if (value != null && !Boolean.FALSE.equals(value)) return true;
        }
        return false;
    }
    public static boolean eligible(CombatEngineAPI engine, ShipAPI ship) {
        return blockReason(engine, ship).isEmpty();
    }
    public static String blockReason(CombatEngineAPI engine, ShipAPI ship) {
        if (!ship.isAlive() || ship.isExpired() || ship.isHulk()) return "Ship inactive";
        if (ship.isRetreating()) return "Retreating";
        if (ship.isAlly()) return "Allied fleet retains control";
        if (ship.isFighter() || ship.isDrone() || ship.isShuttlePod()) return "Fighter or drone pilot";
        if (ship.isStation() || ship.isStationModule() || ship.isPiece()) return "Station or module";
        if (ship.controlsLocked()) return "Ship controls locked";
        if (ship.isPhased() || ship.getPhaseCloak() != null) return "Phase ship retains control";
        if (ship.hasLaunchBays()) return "Carrier retains control";
        if (ship.isNonCombat(false)) return "Noncombat ship";
        if (engine.getPlayerShip() == ship && !engine.isUIAutopilotOn()) return "Manual player control";
        if (ship.getFluxTracker().isOverloadedOrVenting()) return "Overloaded or venting";
        if (ship.getSystem() != null && ship.getSystem().isOn()) return "Active ship system";
        var ai = ship.getShipAI();
        String pilotProblem = PilotAccess.problem(ai);
        if (!pilotProblem.isEmpty()) return pilotProblem;
        if (externallyControlled(ship.getCustomData())) return "RTSAssist control";
        return "";
    }
}
