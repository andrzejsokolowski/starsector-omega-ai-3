package omegaai3;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import omegaai3.model.CombatMath;
import omegaai3.plan.FleetPlanner;
import omegaai3.runtime.Observer;
import omegaai3.runtime.OrderBook;
import org.apache.log4j.Logger;
import java.util.*;

public final class OmegaCombatPlugin extends BaseEveryFrameCombatPlugin {
    private static final Logger LOG = Logger.getLogger(OmegaCombatPlugin.class);
    private static final String STATUS = "omega_ai3_status";
    private CombatEngineAPI engine;
    private OrderBook orders;
    private final Observer observer = new Observer();
    private FleetPlanner planner;
    private final Map<String, String> priorReasons = new HashMap<>();
    private double clock, accumulated, logAt, shotsAt, maxPlanMillis;
    private int proposals, issued, rejected, skipped, closingShots;
    private boolean failed, ended;

    @Override public void init(CombatEngineAPI engine) {
        this.engine = engine;
        orders = new OrderBook(engine);
        planner = new FleetPlanner();
        priorReasons.clear();
        clock = accumulated = logAt = shotsAt = maxPlanMillis = 0;
        proposals = issued = rejected = skipped = closingShots = 0;
        failed = ended = false;
    }

    @Override public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || ended) return;
        Options options = OmegaSettings.current();
        try {
            // A mission can initialize while the menu still owns the current game state.
            // Test the live state, not a title-screen flag retained from init().
            if (Global.getCurrentState() == GameState.TITLE) {
                orders.releaseAll(clock);
                return;
            }
            boolean allowed = options.enabled() && (!engine.isSimulation() || options.simulator());
            if (!allowed || failed || engine.isCombatOver()) {
                orders.releaseAll(clock);
                if (failed && options.status() && engine.getPlayerShip() != null) {
                    engine.maintainStatusForPlayerShip(STATUS, "graphics/omega_ai_icon.png", "Omega AI stopped",
                            "Combat error; see starsector.log", true);
                }
                if (engine.isCombatOver()) {
                    ended = true;
                    if (options.logging()) LOG.info(summary("battle ended"));
                }
                return;
            }
            orders.reconcile(options, clock);
            if (!engine.isPaused() && Float.isFinite(amount) && amount > 0) {
                clock += amount;
                accumulated += amount;
                if (accumulated >= .25) {
                    accumulated = 0;
                    long started = System.nanoTime();
                    evaluate(options);
                    maxPlanMillis = Math.max(maxPlanMillis, (System.nanoTime() - started) / 1_000_000d);
                }
            }
            if (options.status() && engine.getPlayerShip() != null) {
                String mode = options.coordinate() ? "Coordinate" : "Observe";
                String text = options.conflict().isEmpty() ? proposals + " proposals, " + orders.active() + " active orders"
                        : options.conflict() + "; observing only";
                engine.maintainStatusForPlayerShip(STATUS, "graphics/omega_ai_icon.png", "Omega AI: " + mode, text, false);
            }
            if (options.logging() && clock >= logAt) {
                LOG.info(summary(options.coordinate() ? "coordinate" : "observe"));
                logAt = clock + 10;
            }
        } catch (RuntimeException e) {
            if (!failed) LOG.error("Omega AI stopped for this battle after an error; releasing owned orders.", e);
            failed = true;
            // Keep retrying cleanup next frame if the game rejected a cleanup call.
            try { orders.releaseAll(clock); } catch (RuntimeException ignored) { }
        }
    }

    private void evaluate(Options options) {
        proposals = 0; skipped = 0;
        boolean sampleShots = options.logging() && clock >= shotsAt;
        if (sampleShots) { closingShots = 0; shotsAt = clock + 10; }
        Map<String, String> reasons = new HashMap<>();
        for (int side = 0; side < 2; side++) {
            if (!options.includes(side)) continue;
            // Projectile diagnostics do not influence these orders. Sample only at the log interval.
            Observer.Read read = observer.capture(engine, side, clock, ship -> orders.canManage(ship, options, clock), sampleShots);
            skipped += read.skipped();
            List<FleetPlanner.Proposal> plans = planner.plan(read.frame());
            proposals += plans.size();
            for (var ship : read.frame().friends()) {
                if (!ship.controllable()) continue;
                for (var shot : read.frame().shots()) if (Double.isFinite(CombatMath.impactTime(shot, ship, 3))) closingShots++;
            }
            for (FleetPlanner.Proposal plan : plans) {
                String key = side + ":" + plan.shipId();
                String reason = plan.reason() + " / " + plan.anchorId();
                reasons.put(key, reason);
                if (options.logging() && !reason.equals(priorReasons.get(key))) {
                    LOG.info("Omega AI proposal [" + key + "] " + plan.reason() + ": " + plan.detail());
                }
                ShipAPI ship = read.ships().get(plan.shipId());
                if (ship == null) continue;
                OrderBook.Result result = orders.apply(ship, plan, options, clock);
                if (result == OrderBook.Result.CREATED) issued++;
                if (result == OrderBook.Result.REJECTED) rejected++;
            }
        }
        priorReasons.clear(); priorReasons.putAll(reasons);
    }

    private String summary(String mode) {
        return "Omega AI [" + mode + "] t=" + Math.round(clock) + " proposals=" + proposals + " active=" + orders.active()
                + " accepted=" + issued + " rejected=" + rejected + " unreadable=" + skipped + " projectile-reach-bounds=" + closingShots
                + " max-plan-ms=" + Math.round(maxPlanMillis * 100) / 100d;
    }
}
