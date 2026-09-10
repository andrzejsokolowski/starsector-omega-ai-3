package omegaai3.runtime;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.Map;

public final class ControlGate {
    private static final String[] RTS_KEYS = {"RTS_BLOCKAI_ForYourOwnSakeDontUseThis",
            "RTS_OVERRIDETHRUST_ForYourOwnSakeDontUseThis", "RTS_OVERRIDEFACING_ForYourOwnSakeDontUseThis"};
    private ControlGate() {}
    public static boolean knownPilot(String name) { return "com.fs.starfarer.combat.ai.BasicShipAI".equals(name); }
    public static boolean externallyControlled(Map<String, Object> data) {
        for (String key : RTS_KEYS) {
            Object value = data.get(key);
            if (value != null && !Boolean.FALSE.equals(value)) return true;
        }
        return false;
    }
    public static boolean eligible(CombatEngineAPI engine, ShipAPI ship) {
        if (!ship.isAlive() || ship.isExpired() || ship.isHulk() || ship.isRetreating() || ship.isAlly()
                || ship.isFighter() || ship.isDrone() || ship.isStation() || ship.isStationModule() || ship.isPiece()
                || ship.isShuttlePod() || ship.controlsLocked() || ship.isPhased()
                || ship.getPhaseCloak() != null || ship.hasLaunchBays() || ship.isNonCombat(false)) return false;
        if (engine.getPlayerShip() == ship && !engine.isUIAutopilotOn()) return false;
        if (ship.getFluxTracker().isOverloadedOrVenting()) return false;
        if (ship.getSystem() != null && ship.getSystem().isOn()) return false;
        var ai = ship.getShipAI();
        if (ai == null || !knownPilot(ai.getClass().getName())) return false;
        return !externallyControlled(ship.getCustomData());
    }
}
