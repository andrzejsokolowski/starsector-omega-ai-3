package omegaai3.runtime;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.combat.ai.BasicShipAI;
import com.fs.starfarer.combat.entities.Ship;
import omegaai3.tactics.TacticalIntent;
import org.apache.log4j.Logger;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Stock combat modules plus an owned tactical helm inside the same ship-AI advance. */
public final class OmegaShipAI extends BasicShipAI {
    private static final Logger LOG = Logger.getLogger(OmegaShipAI.class);
    private final Ship ship;
    private final CombatEngineAPI engine;
    private final Rc8Helm helm;
    private final BooleanSupplier permitted;
    private TacticalIntent pending, active;
    private ShipAPI target, ownedOverride;
    private boolean broken;
    private String idle = "Waiting for a movement decision";

    public OmegaShipAI(Ship ship, ShipAIConfig config, CombatEngineAPI engine, BooleanSupplier permitted) {
        super(ship, config.clone());
        this.ship = ship; this.engine = engine; this.permitted = permitted; helm = new Rc8Helm(ship);
    }
    public void publish(TacticalIntent intent, ShipAPI target) { pending = intent; this.target = target; }
    public boolean failed() { return broken; }
    public String idleReason() {
        if (broken) return "Omega movement stopped after an error";
        if (pending != null && pending.expires() < engine.getTotalElapsedTime(false)) return "Movement decision expired";
        return idle;
    }
    public void clear() {
        pending = active = null; target = null;
        if (ownedOverride != null && getTargetOverride() == ownedOverride) super.setTargetOverride((ShipAPI) null);
        ownedOverride = null;
    }
    private boolean canSteer() {
        return !broken && pending != null && pending.expires() >= engine.getTotalElapsedTime(false)
                && (engine.getPlayerShip() != ship || engine.isUIAutopilotOn())
                && PilotAccess.installed(ship, this) && permitted.getAsBoolean() && target != null && target.isAlive()
                && !target.isExpired() && !target.isHulk() && engine.isAwareOf(ship.getOwner(), target)
                && (getTargetOverride() == null || getTargetOverride() == ownedOverride);
    }
    public TacticalIntent activeIntent() { return active != null && active == pending && canSteer() ? active : null; }

    @Override public void advance(float amount) {
        active = null; idle = "Waiting for a movement decision";
        if (engine.getPlayerShip() == ship && !engine.isUIAutopilotOn()) { clear(); return; }
        boolean enabled;
        try { enabled = canSteer(); } catch (RuntimeException | LinkageError e) { disable(e); enabled = false; }
        if (!enabled) { clear(); super.advance(amount); return; }
        Set<Ship.Oo> before = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean yield = false;
        try {
            before.addAll(ship.getCommands());
            yield = before.stream().anyMatch(Rc8Helm::movement) || helm.blocked();
            if (yield) idle = "Another controller issued or blocked movement";
            if (!yield) { super.setTargetOverride(target); ownedOverride = target; }
        } catch (RuntimeException | LinkageError e) { disable(e); yield = true; }
        if (yield) { clear(); super.advance(amount); return; }
        super.advance(amount); // Weapon, shield, vent, system, and emergency collision modules run normally.
        try {
            if (!canSteer()) return;
            if (isAvoidingCollision()) { idle = "Native collision avoidance"; return; }
            if (helm.blocked()) { idle = "Movement commands blocked"; return; }
            if (ship.getCommands().stream().anyMatch(Rc8Helm::system)) { idle = "Ship system handling"; return; }
            helm.steer(pending, amount, before);
            active = pending; idle = ""; // Only a consumed helm decision gets a label.
        } catch (RuntimeException | LinkageError e) { disable(e); }
    }
    private void disable(Throwable error) {
        clear(); broken = true;
        LOG.error("Omega helm stopped for " + ship.getName() + "; native pilot continues", error);
    }
}
