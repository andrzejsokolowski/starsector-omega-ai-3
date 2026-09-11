package omegaai3;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import omegaai3.runtime.*;
import omegaai3.runtime.Observer;
import omegaai3.tactics.*;
import omegaai3.diagnostics.DecisionOverlay;
import omegaai3.diagnostics.ControlStatus;
import org.apache.log4j.Logger;
import java.util.*;

public final class OmegaCombatPlugin extends BaseEveryFrameCombatPlugin {
    private static final Logger LOG = Logger.getLogger(OmegaCombatPlugin.class);
    private CombatEngineAPI engine;
    private PilotSession pilots;
    private Observer observer;
    private TacticalPlanner planner;
    private DecisionOverlay overlay;
    private final Map<String, String> prior = new HashMap<>();
    private final Map<ShipAPI, String> waiting = new IdentityHashMap<>();
    private double accumulated, logAt, maxPlanMillis;
    private int selected, readable, active;
    private boolean failed, ended, supported;

    @Override public void init(CombatEngineAPI engine) {
        this.engine = engine;
        pilots = new PilotSession(engine); observer = new Observer(); planner = new TacticalPlanner(); overlay = new DecisionOverlay();
        accumulated = logAt = maxPlanMillis = 0; selected = readable = active = 0;
        prior.clear(); waiting.clear(); failed = ended = false;
        supported = Rc8Helm.supported(Global.getSettings().getVersionString());
        if (!supported) LOG.warn("Omega direct helm disabled: unsupported game version " + Global.getSettings().getVersionString());
    }

    @Override public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || ended) return;
        Options options = OmegaSettings.current();
        try {
            boolean allowed = supported && !failed && options.enabled() && (!engine.isSimulation() || options.simulator())
                    && Global.getCurrentState() != GameState.TITLE && !engine.isCombatOver();
            pilots.reconcile(options, allowed);
            if (!allowed) {
                overlay.dispose();
                if (engine.isCombatOver()) { pilots.restoreAll(); ended = true; }
            } else if (!engine.isPaused() && Float.isFinite(amount) && amount > 0) {
                accumulated += amount;
                if (accumulated >= .25) {
                    accumulated = 0;
                    long start = System.nanoTime();
                    evaluate(options);
                    maxPlanMillis = Math.max(maxPlanMillis, (System.nanoTime() - start) / 1_000_000d);
                }
            }
            active = 0;
            for (ShipAPI ship : engine.getShips()) if (pilots.active(ship) != null) active++;
            if (options.status() && engine.getPlayerShip() != null) {
                String mode = failed ? "Stopped" : !supported ? "Unsupported game version" : options.enabled() ? "Enabled" : "Disabled";
                String detail = String.join(" | ", statusLines(options).stream().skip(1).toList());
                engine.maintainStatusForPlayerShip("omega_ai3_status", "graphics/omega_ai_icon.png", "Omega AI: " + mode, detail, failed);
            }
            double now = engine.getTotalElapsedTime(false);
            if (options.logging() && now >= logAt) {
                LOG.info("Omega helm t=" + Math.round(now) + " mode=" + options.effectiveMode() + " readable=" + readable
                        + " selected=" + selected + " actively-steered=" + active + " max-plan-ms=" + Math.round(maxPlanMillis * 100) / 100d);
                logAt = now + 10;
            }
        } catch (RuntimeException | LinkageError e) {
            if (!failed) LOG.error("Omega tactical control stopped; restoring native pilots", e);
            failed = true;
            try { pilots.restoreAll(); } catch (RuntimeException | LinkageError ignored) { }
        }
    }

    private void evaluate(Options options) {
        selected = readable = 0; waiting.clear();
        double now = engine.getTotalElapsedTime(false);
        Map<String, String> changed = new HashMap<>();
        for (int side = 0; side < 2; side++) {
            if (!options.includes(side)) continue;
            Observer.Read read = observer.capture(engine, side, now, s -> pilots.blockReason(s, options).isEmpty());
            readable += read.frame().friends().size();
            Map<String, TacticalIntent> decisions = planner.plan(read.frame());
            for (var snapshot : read.frame().friends()) {
                ShipAPI ship = read.ships().get(snapshot.id());
                String reason = decisions.containsKey(snapshot.id()) ? "Waiting for a ship update"
                        : snapshot.dp() <= 0 ? "Deployment data unavailable" : snapshot.usefulRange() <= 0 ? "No available main weapons"
                        : read.frame().enemies().isEmpty() ? "Searching for visible enemies" : "No movement decision";
                waiting.put(ship, reason);
                if (options.logging()) {
                    String exclusion = pilots.blockReason(ship, options);
                    String key = side + ":" + snapshot.id() + ":control";
                    String value = (exclusion.isEmpty() ? reason : exclusion) + " pilot=" + PilotAccess.description(ship.getShipAI());
                    changed.put(key, value);
                    if (!value.equals(prior.get(key))) LOG.info("Omega control [" + key + "] " + value);
                }
            }
            selected += decisions.size();
            Map<String, ShipAPI> targets = new HashMap<>();
            for (ShipAPI ship : engine.getShips()) if (ship.getOwner() != side && engine.isAwareOf(side, ship)) targets.put(Observer.key(ship), ship);
            for (var entry : decisions.entrySet()) {
                ShipAPI ship = read.ships().get(entry.getKey());
                TacticalIntent intent = entry.getValue();
                if (options.mayIssueOrders()) pilots.publish(ship, intent, targets.get(intent.targetId()), options);
                String key = side + ":" + entry.getKey();
                String value = intent.action() + " target=" + intent.targetId() + " reason=" + intent.reason();
                changed.put(key, value);
                if (options.logging() && !value.equals(prior.get(key))) LOG.info("Omega intent [" + key + "] " + value);
            }
        }
        prior.clear(); prior.putAll(changed);
    }

    @Override public void renderInUICoords(ViewportAPI viewport) {
        if (engine == null || overlay == null || Global.getCurrentState() == GameState.TITLE) return;
        Options options = OmegaSettings.current();
        List<DecisionOverlay.Entry> entries = new ArrayList<>();
        for (ShipAPI ship : engine.getShips()) {
            TacticalIntent current = pilots.active(ship);
            if (current != null) entries.add(new DecisionOverlay.Entry(ship, current.action().label));
        }
        overlay.render(engine, viewport, entries, options.mayIssueOrders() && options.labels() && !failed && !ended,
                entry -> pilots.active(entry.ship()) != null, options.status() && !ended ? statusLines(options) : List.of());
    }

    List<String> statusLines(Options options) {
        if (failed) return List.of("Omega AI: stopped after an error", "Native pilots restored");
        if (!supported) return List.of("Omega AI: unsupported game version", "Requires Starsector 0.98a-RC8");
        if (!options.conflict().isEmpty()) return List.of("Omega AI: blocked", options.conflict());
        if (!options.enabled()) return List.of("Omega AI: disabled");
        if (engine.isSimulation() && !options.simulator()) return List.of("Omega AI: simulator excluded in settings");
        List<String> lines = new ArrayList<>(); lines.add("Omega AI: enabled");
        int viewer = engine.getPlayerShip() == null ? 0 : engine.getPlayerShip().getOwner();
        for (int side = 0; side < 2; side++) {
            int activeShips = 0; Map<String, Integer> reasons = new HashMap<>();
            for (ShipAPI ship : engine.getShips()) {
                if (ship.getOwner() != side || !ship.isAlive() || ship.isExpired() || ship.isFighter() || ship.isHulk()) continue;
                if (side != viewer && !engine.isAwareOf(viewer, ship)) continue;
                if (pilots.active(ship) != null) { activeShips++; continue; }
                String reason = pilots.blockReason(ship, options);
                var pilot = PilotAccess.unwrap(ship.getShipAI());
                if (reason.isEmpty() && pilot instanceof OmegaShipAI omega) reason = omega.idleReason();
                if (reason.isEmpty()) reason = waiting.getOrDefault(ship, "Waiting for combat update");
                reasons.merge(reason, 1, Integer::sum);
            }
            lines.add(ControlStatus.fleet(side == 0 ? "Player" : "Enemy", options.includes(side), activeShips, reasons));
        }
        return List.copyOf(lines);
    }
}
