package omegaai3.runtime;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.combat.ai.AI;
import com.fs.starfarer.combat.ai.BasicShipAI;
import com.fs.starfarer.combat.entities.Ship;
import omegaai3.model.Vec;
import omegaai3.tactics.TacticalIntent;
import org.apache.log4j.Logger;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Runs the existing pilot and its wrappers, then supplies autonomous movement when they permit it. */
public final class OmegaShipAI implements ShipAIPlugin, AI {
    private static final Logger LOG = Logger.getLogger(OmegaShipAI.class);
    private final Ship ship;
    private final ShipAIPlugin original;
    private final CombatEngineAPI engine;
    private final Rc8Helm helm;
    private final BooleanSupplier permitted;
    private final BasicShipAI capturedNative;
    private double rtsTargetCommandUntil = -1;
    private TacticalIntent pending, active;
    private ShipAPI target, ownedOverride;
    private boolean broken;
    private String idle = "Waiting for a movement decision";

    public OmegaShipAI(Ship ship, ShipAIConfig config, CombatEngineAPI engine, BooleanSupplier permitted) {
        this(ship, new BasicShipAI(ship, config.clone()), engine, permitted);
    }
    public OmegaShipAI(Ship ship, ShipAIPlugin original, CombatEngineAPI engine, BooleanSupplier permitted) {
        this(ship, original, engine, permitted, null);
    }
    public OmegaShipAI(Ship ship, ShipAIPlugin original, CombatEngineAPI engine, BooleanSupplier permitted, BasicShipAI capturedNative) {
        this.ship = ship; this.original = original; this.engine = engine; this.permitted = permitted;
        this.capturedNative = capturedNative; helm = new Rc8Helm(ship);
    }
    public void publish(TacticalIntent intent, ShipAPI target) { pending = intent; this.target = target; }
    public boolean failed() { return broken; }
    public void keepAlive() {
        // RTS uses calls to its delegate to track whether its wrapper is still in use. It remains
        // in use inside this adapter, including while manual piloting skips ship-AI advances.
        if (PilotAccess.isRts(original)) original.getConfig();
    }
    public String idleReason() {
        if (broken) return "Omega movement stopped after an error";
        if (engine.getTotalElapsedTime(false) <= rtsTargetCommandUntil) return "Following RTSAssist commands";
        if (helm.blocked()) return PilotAccess.isRts(original) ? "Following RTSAssist commands" : "Movement commands blocked";
        BasicShipAI basic = nativePilot();
        if (basic != null && basic.getTargetOverride() != null && basic.getTargetOverride() != ownedOverride) return "Another controller selected the target";
        if (pending != null && pending.expires() < engine.getTotalElapsedTime(false)) return "Movement decision expired";
        return idle;
    }
    private BasicShipAI nativePilot() {
        var pilot = PilotAccess.unwrap(original);
        if (pilot instanceof BasicShipAI basic) return basic;
        // The native pilot was observed before RTS wrapped it. Verify both forwarded identities
        // before using its public targeting/collision API; never inspect RTS's private pilot list.
        return capturedNative != null && PilotAccess.isRts(original)
                && original.getConfig() == capturedNative.getConfig() && original.getAIFlags() == capturedNative.getAIFlags() ? capturedNative : null;
    }
    private void releaseTarget() {
        BasicShipAI basic = nativePilot();
        if (ownedOverride != null && basic != null && basic.getTargetOverride() == ownedOverride) basic.setTargetOverride((ShipAPI) null);
        ownedOverride = null;
    }
    public void clear() { pending = active = null; target = null; releaseTarget(); }
    private boolean canSteer() {
        BasicShipAI basic = nativePilot();
        return !broken && pending != null && pending.expires() >= engine.getTotalElapsedTime(false)
                && engine.getTotalElapsedTime(false) > rtsTargetCommandUntil
                && (engine.getPlayerShip() != ship || engine.isUIAutopilotOn())
                && PilotAccess.installed(ship, this) && permitted.getAsBoolean() && target != null && target.isAlive()
                && !target.isExpired() && !target.isHulk() && engine.isAwareOf(ship.getOwner(), target)
                && (basic == null || basic.getTargetOverride() == null || basic.getTargetOverride() == ownedOverride);
    }
    public TacticalIntent activeIntent() { return active != null && active == pending && canSteer() && !helm.blocked() ? active : null; }

    @Override public void advance(float amount) {
        active = null; idle = "Waiting for a movement decision";
        if (engine.getPlayerShip() == ship && !engine.isUIAutopilotOn()) { clear(); keepAlive(); return; }
        Set<Ship.Oo> before = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean control = false;
        try {
            before.addAll(ship.getCommands());
            control = canSteer() && !helm.blocked() && before.stream().noneMatch(Rc8Helm::movement);
            if (control && nativePilot() != null) {
                nativePilot().setTargetOverride(target); ownedOverride = target;
            } else releaseTarget();
        } catch (RuntimeException | LinkageError e) { disable(e); }
        runOriginal(amount);
        try {
            if (!control || !canSteer()) return;
            if (helm.blocked()) { idle = PilotAccess.isRts(original) ? "Following RTSAssist commands" : "Movement commands blocked"; releaseTarget(); return; }
            BasicShipAI basic = nativePilot();
            if (basic != null ? basic.isAvoidingCollision() : immediateCollisionRisk()) { idle = "Native collision avoidance"; return; }
            if (ship.getCommands().stream().anyMatch(Rc8Helm::system)) { idle = "Ship system handling"; return; }
            helm.steer(pending, amount, before);
            active = pending; idle = "";
        } catch (RuntimeException | LinkageError e) { disable(e); }
    }
    private void runOriginal(float amount) {
        ShipAIPlugin outer = ship.getShipAI();
        ship.setShipAI(original);
        try { original.advance(amount); }
        finally {
            // Native and AI Tweaks pilots check their installed identity. RTS also swaps the
            // installed pilot during its update. Accept its fresh native wrapper around the same
            // delegate, but preserve a genuinely different replacement made by another mod.
            if (PilotAccess.unwrap(ship.getShipAI()) == PilotAccess.unwrap(original)) ship.setShipAI(outer);
            else { clear(); idle = "Another mod replaced the pilot"; }
        }
    }
    private boolean immediateCollisionRisk() {
        Vec position = new Vec(ship.getLocation().x, ship.getLocation().y);
        Vec velocity = new Vec(ship.getVelocity().x, ship.getVelocity().y);
        for (ShipAPI other : engine.getShips()) {
            if (other == ship || other.isExpired() || other.isFighter() || other.isPhased()) continue;
            if (other.getOwner() != ship.getOwner() && !engine.isAwareOf(ship.getOwner(), other)) continue;
            Vec separation = position.sub(new Vec(other.getLocation().x, other.getLocation().y));
            Vec relative = velocity.sub(new Vec(other.getVelocity().x, other.getVelocity().y));
            double time = Math.max(0, Math.min(1.5, -separation.dot(relative) / Math.max(1, relative.dot(relative))));
            if (separation.add(relative.scale(time)).length() < ship.getCollisionRadius() + other.getCollisionRadius() + 40) return true;
        }
        return false;
    }
    private void disable(Throwable error) {
        clear(); broken = true;
        LOG.error("Omega movement stopped for " + ship.getName() + "; original pilot continues", error);
    }
    @Override public void setDoNotFireDelay(float amount) { original.setDoNotFireDelay(amount); }
    @Override public void forceCircumstanceEvaluation() { original.forceCircumstanceEvaluation(); }
    @Override public boolean needsRefit() { return original.needsRefit(); }
    @Override public ShipwideAIFlags getAIFlags() { return original.getAIFlags(); }
    @Override public void cancelCurrentManeuver() { original.cancelCurrentManeuver(); }
    @Override public ShipAIConfig getConfig() { return original.getConfig(); }
    @Override public void setTargetOverride(ShipAPI target) {
        releaseTarget();
        if (PilotAccess.isRts(original)) rtsTargetCommandUntil = engine.getTotalElapsedTime(false) + .05;
        original.setTargetOverride(target);
    }
    @Override public void render() { if (original instanceof AI ai) ai.render(); }
}
