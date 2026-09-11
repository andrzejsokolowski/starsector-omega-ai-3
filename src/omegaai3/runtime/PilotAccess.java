package omegaai3.runtime;

import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.combat.ai.BasicShipAI;
import com.fs.starfarer.combat.entities.Ship;

/** RC8 exposes this one transparent engine wrapper. Other mods' wrappers are opaque. */
public final class PilotAccess {
    private PilotAccess() {}
    public static ShipAIPlugin unwrap(ShipAIPlugin pilot) {
        return pilot != null && pilot.getClass() == Ship.ShipAIWrapper.class ? ((Ship.ShipAIWrapper) pilot).getAI() : pilot;
    }
    public static boolean installed(ShipAPI ship, ShipAIPlugin pilot) { return unwrap(ship.getShipAI()) == pilot; }
    public static String problem(ShipAIPlugin outer) {
        ShipAIPlugin pilot = unwrap(outer);
        if (pilot == null) return "No ship AI";
        if (pilot.getClass() == BasicShipAI.class)
            return ((BasicShipAI) pilot).getTargetOverride() == null ? "" : "Another controller selected the target";
        if (pilot.getClass() == OmegaShipAI.class) return "";
        String name = pilot.getClass().getName();
        if (name.startsWith("com.genir.aitweaks.")) return "AI Tweaks controls ship AI";
        if (name.startsWith("data.scripts.plugins.AISystems.RTS_AIInjector")) return "RTSAssist controls ship AI";
        return "Another mod controls ship AI";
    }
    public static String description(ShipAIPlugin outer) {
        ShipAIPlugin pilot = unwrap(outer);
        return pilot == null ? "none" : pilot.getClass().getName();
    }
}
