package omegaai3;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import omegaai3.runtime.*;
import omegaai3.runtime.Observer;
import omegaai3.tactics.*;
import omegaai3.diagnostics.DecisionOverlay;
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
    private double accumulated, logAt, maxPlanMillis;
    private int selected, readable, active;
    private boolean failed, ended, supported;

    @Override public void init(CombatEngineAPI engine) {
        this.engine = engine;
        pilots = new PilotSession(engine); observer = new Observer(); planner = new TacticalPlanner(); overlay = new DecisionOverlay();
        accumulated = logAt = maxPlanMillis = 0; selected = readable = active = 0;
        prior.clear(); failed = ended = false;
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
                String mode = failed ? "Stopped" : !supported ? "Unsupported game version" : options.effectiveMode();
                String detail = !options.conflict().isEmpty() ? options.conflict() : options.coordinate()
                        ? active + " ships under Omega movement control" : "Native pilots active";
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
        selected = readable = 0;
        double now = engine.getTotalElapsedTime(false);
        Map<String, String> changed = new HashMap<>();
        for (int side = 0; side < 2; side++) {
            if (!options.includes(side)) continue;
            Observer.Read read = observer.capture(engine, side, now, s -> pilots.blockReason(s, options).isEmpty());
            readable += read.frame().friends().size();
            Map<String, TacticalIntent> decisions = planner.plan(read.frame());
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
                entry -> pilots.active(entry.ship()) != null);
    }
}
