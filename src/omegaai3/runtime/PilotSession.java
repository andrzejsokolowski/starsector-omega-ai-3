package omegaai3.runtime;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.combat.ai.BasicShipAI;
import com.fs.starfarer.combat.entities.Ship;
import omegaai3.*;
import omegaai3.tactics.TacticalIntent;
import java.util.*;

/** Owns pilots, never assignments. Restores only a pilot that is still ours. */
public final class PilotSession {
    private record Owned(ShipAPI ship, ShipAIPlugin original, OmegaShipAI pilot) {}
    private final CombatEngineAPI engine;
    private final Map<ShipAPI, Owned> pilots = new IdentityHashMap<>();
    private final Map<ShipAPI, BasicShipAI> nativePilots = new IdentityHashMap<>();
    private final Set<ShipAPI> failed = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean enabled;
    public PilotSession(CombatEngineAPI engine) { this.engine = engine; }
    public String blockReason(ShipAPI ship, Options options) {
        if (!(ship instanceof Ship)) return "Unsupported ship implementation";
        if (failed.contains(ship)) return "Omega pilot could not start";
        Owned item = pilots.get(ship);
        if (item != null && item.pilot.failed()) return "Omega movement stopped after an error";
        return FleetControlGate.blockReason(engine, ship, options);
    }
    private boolean allowed(ShipAPI ship) {
        Options options = OmegaSettings.current();
        return enabled && options.mayIssueOrders() && (!engine.isSimulation() || options.simulator())
                && !engine.isCombatOver() && blockReason(ship, options).isEmpty();
    }
    public void reconcile(Options options, boolean enabled) {
        this.enabled = enabled && options.mayIssueOrders();
        nativePilots.keySet().removeIf(s -> !s.isAlive() || s.isExpired());
        // Read each frame, before the slower tactical pass: RTS hooks newly deployed ships after
        // several updates. Remember the original public pilot while it is still directly visible.
        for (ShipAPI ship : engine.getShips()) {
            if (!options.includes(ship.getOwner()) || !ship.isAlive() || ship.isExpired() || ship.isFighter()) continue;
            var pilot = PilotAccess.unwrap(ship.getShipAI());
            if (pilot instanceof BasicShipAI basic && PilotAccess.supportedDelegate(pilot)) nativePilots.put(ship, basic);
        }
        for (Owned item : new ArrayList<>(pilots.values())) {
            if (!this.enabled || !options.includes(item.ship.getOwner()) || !item.ship.isAlive() || item.ship.isExpired()) restore(item);
            else {
                item.pilot.keepAlive();
                if (!PilotAccess.installed(item.ship, item.pilot)) { item.pilot.clear(); pilots.remove(item.ship); }
            }
        }
    }
    public void publish(ShipAPI ship, TacticalIntent intent, ShipAPI target, Options options) {
        if (!enabled || !options.mayIssueOrders() || failed.contains(ship) || !blockReason(ship, options).isEmpty()) return;
        Owned item = pilots.get(ship);
        if (item == null) {
            ShipAIPlugin original = ship.getShipAI();
            ShipAIPlugin delegate = PilotAccess.unwrap(original);
            if (!PilotAccess.supportedDelegate(original)) return;
            if (delegate instanceof BasicShipAI basic && basic.getTargetOverride() != null) return;
            try {
                OmegaShipAI pilot = new OmegaShipAI((Ship) ship, original, engine, () -> allowed(ship), nativePilots.get(ship));
                item = new Owned(ship, original, pilot);
                pilots.put(ship, item);
                ship.setShipAI(pilot);
            } catch (RuntimeException | LinkageError e) {
                failed.add(ship);
                if (item != null) restore(item);
                org.apache.log4j.Logger.getLogger(PilotSession.class).error("Could not install Omega pilot for " + ship.getName(), e);
                return;
            }
        }
        item.pilot.publish(intent, target);
    }
    public TacticalIntent active(ShipAPI ship) {
        try {
            Owned item = pilots.get(ship);
            return item != null && PilotAccess.installed(ship, item.pilot) ? item.pilot.activeIntent() : null;
        } catch (RuntimeException | LinkageError e) { return null; }
    }
    public void restoreAll() { enabled = false; for (Owned item : new ArrayList<>(pilots.values())) restore(item); }
    private void restore(Owned item) {
        item.pilot.clear();
        if (PilotAccess.installed(item.ship, item.pilot)) { item.ship.setShipAI(item.original); item.original.forceCircumstanceEvaluation(); }
        pilots.remove(item.ship);
    }
}
