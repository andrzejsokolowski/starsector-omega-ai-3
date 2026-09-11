package omegaai3;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import omegaai3.model.CombatMath;
import omegaai3.plan.FleetPlanner;
import omegaai3.runtime.Observer;
import omegaai3.runtime.OrderBook;
import omegaai3.diagnostics.DecisionOverlay;
import omegaai3.diagnostics.DecisionView;
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
    private final Map<String, String> priorDecisions = new HashMap<>();
    private DecisionOverlay overlay;
    private List<DecisionOverlay.Entry> labels = List.of();
    private final int[] observed = new int[2], eligible = new int[2];
    private String effectiveMode = "Observe", conflict = "";
    private long nextPausedRefresh;
    private double clock, accumulated, logAt, shotsAt, maxPlanMillis;
    private int proposals, issued, rejected, skipped, closingShots;
    private boolean failed, ended;

    @Override public void init(CombatEngineAPI engine) {
        this.engine = engine;
        orders = new OrderBook(engine);
        planner = new FleetPlanner();
        priorReasons.clear();
        priorDecisions.clear();
        overlay = new DecisionOverlay(); labels = List.of();
        Arrays.fill(observed, 0); Arrays.fill(eligible, 0);
        nextPausedRefresh = 0;
        clock = accumulated = logAt = shotsAt = maxPlanMillis = 0;
        proposals = issued = rejected = skipped = closingShots = 0;
        failed = ended = false;
    }

    @Override public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || ended) return;
        Options options = OmegaSettings.current();
        effectiveMode = options.effectiveMode(); conflict = options.conflict();
        try {
            // A mission can initialize while the menu still owns the current game state.
            // Test the live state, not a title-screen flag retained from init().
            if (Global.getCurrentState() == GameState.TITLE) {
                orders.releaseAll(clock);
                labels = List.of(); overlay.dispose();
                return;
            }
            boolean allowed = options.enabled() && (!engine.isSimulation() || options.simulator());
            if (!allowed || failed || engine.isCombatOver()) {
                orders.releaseAll(clock);
                labels = List.of(); overlay.dispose();
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
            if (engine.isPaused() && System.nanoTime() >= nextPausedRefresh) {
                refreshPausedLabels(options);
                nextPausedRefresh = System.nanoTime() + 250_000_000L;
            }
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
                String mode = options.effectiveMode();
                String text = options.conflict().isEmpty() ? proposals + " proposals, " + orders.active() + " active orders"
                        : options.conflict() + "; no Omega orders";
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
        Map<String, String> decisions = new HashMap<>();
        List<DecisionOverlay.Entry> nextLabels = new ArrayList<>();
        Arrays.fill(observed, 0); Arrays.fill(eligible, 0);
        for (int side = 0; side < 2; side++) {
            if (!options.includes(side)) continue;
            Map<String, String> exclusions = new HashMap<>();
            // Projectile diagnostics do not influence these orders. Sample only at the log interval.
            Observer.Read read = observer.capture(engine, side, clock, ship -> {
                String reason = orders.blockReason(ship, options, clock);
                exclusions.put(Observer.key(ship), reason);
                return reason.isEmpty();
            }, sampleShots);
            skipped += read.skipped();
            List<FleetPlanner.Proposal> plans = planner.plan(read.frame());
            proposals += plans.size();
            for (var ship : read.frame().friends()) {
                if (!ship.fighter()) observed[side]++;
                if (ship.controllable() && !ship.fighter()) eligible[side]++;
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
            Map<String, FleetPlanner.Proposal> byShip = new HashMap<>();
            for (var plan : plans) byShip.put(plan.shipId(), plan);
            Map<String, String> names = new HashMap<>();
            for (var enemy : read.frame().enemies()) names.put(enemy.id(), enemy.name());
            for (var snapshot : read.frame().friends()) {
                if (snapshot.fighter()) continue;
                ShipAPI ship = read.ships().get(snapshot.id());
                String exclusion = exclusions.getOrDefault(snapshot.id(), "Control state not observed");
                if (exclusion.isEmpty() && !snapshot.controllable()) exclusion = "Deployment points unavailable";
                String controller;
                try { controller = ship.getShipAI() == null ? "none" : ship.getShipAI().getClass().getName(); }
                catch (RuntimeException e) { controller = "unreadable"; }
                DecisionView view = DecisionView.describe(options, exclusion, orders.activePurpose(ship), byShip.get(snapshot.id()),
                        controller, orders.assignmentName(ship), names.getOrDefault(snapshot.targetId(), "unreported"));
                nextLabels.add(new DecisionOverlay.Entry(ship, view));
                String key = side + ":" + snapshot.id();
                decisions.put(key, view.eventKey());
                if (options.logging() && !view.eventKey().equals(priorDecisions.get(key))) {
                    LOG.info("Omega ship [" + key + "] name=" + DecisionView.clean(snapshot.name()) + " state=" + view.state()
                            + " reason=" + view.reason() + " eligibility=" + view.eligibility() + " controller=" + view.controller()
                            + " assignment=" + view.assignment() + " target=" + view.target() + " proposal=" + view.proposal());
                }
            }
        }
        priorReasons.clear(); priorReasons.putAll(reasons);
        priorDecisions.clear(); priorDecisions.putAll(decisions);
        labels = List.copyOf(nextLabels);
    }

    @Override public void renderInUICoords(ViewportAPI viewport) {
        if (engine == null || overlay == null || Global.getCurrentState() == GameState.TITLE) return;
        Options options = OmegaSettings.current();
        overlay.render(engine, viewport, labels, options.mayIssueOrders() && options.labels() && !failed && !ended,
                entry -> entry.view().reason().equals(orders.activePurpose(entry.ship())) && orders.canManage(entry.ship(), options, clock));
    }

    private void refreshPausedLabels(Options options) {
        List<DecisionOverlay.Entry> refreshed = new ArrayList<>();
        for (var entry : labels) {
            ShipAPI ship = entry.ship();
            if (!ship.isAlive() || ship.isExpired()) continue;
            String reason = orders.blockReason(ship, options, clock);
            if (reason.isEmpty() && entry.view().eligibility().equals("Deployment points unavailable")) reason = entry.view().eligibility();
            String pilot;
            try { pilot = ship.getShipAI() == null ? "none" : ship.getShipAI().getClass().getName(); }
            catch (RuntimeException e) { pilot = "unreadable"; }
            refreshed.add(new DecisionOverlay.Entry(ship,
                    entry.view().refreshControl(options, reason, orders.activePurpose(ship), pilot, orders.assignmentName(ship))));
        }
        labels = List.copyOf(refreshed);
    }

    private String summary(String mode) {
        return "Omega AI [" + mode + "] t=" + Math.round(clock) + " proposals=" + proposals + " active=" + orders.active()
                + " accepted=" + issued + " rejected=" + rejected + " unreadable=" + skipped + " projectile-reach-bounds=" + closingShots
                + " observed=" + observed[0] + "/" + observed[1] + " eligible=" + eligible[0] + "/" + eligible[1]
                + " effective=" + effectiveMode + " conflict=" + (conflict.isEmpty() ? "none" : conflict)
                + " max-plan-ms=" + Math.round(maxPlanMillis * 100) / 100d;
    }
}
