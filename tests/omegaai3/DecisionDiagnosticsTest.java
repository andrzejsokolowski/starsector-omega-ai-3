package omegaai3;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import omegaai3.diagnostics.DecisionView;
import omegaai3.model.Vec;
import omegaai3.plan.FleetPlanner;
import omegaai3.runtime.ControlGate;
import omegaai3.runtime.OrderBook;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DecisionDiagnosticsTest {
    final Options coordinate = new Options(true, true, true, true, true, true, true, "");
    final FleetPlanner.Proposal proposal = new FleetPlanner.Proposal("ship", "anchor", new Vec(2000, 0),
            FleetPlanner.Reason.REJOIN_GROUP, "Rejoin Anchor");

    @Test void blockedCoordinateSelectionIsNeverDisplayedAsActiveCoordination() {
        var options = new Options(true, true, true, true, true, true, true, "AI Tweaks fleet cohesion is enabled");
        var view = DecisionView.describe(options, "Custom or wrapped ship AI", "", proposal, "mod.Wrapper", "none", "enemy");
        assertEquals("Blocked", options.effectiveMode());
        assertEquals("BLOCKED", view.state()); assertTrue(view.reason().contains("AI Tweaks"));
        assertTrue(view.eligibility().contains("Custom or wrapped")); assertTrue(view.proposal().contains("REJOIN_GROUP"));
        assertEquals("", view.label());
        assertFalse(options.mayIssueOrders());
    }
    @Test void observeProposalIsNotPresentedAsAnIssuedOrder() {
        var view = DecisionView.describe(Options.defaults(), "", "", proposal, "BasicShipAI", "none", "enemy");
        assertEquals("OBSERVE", view.state()); assertTrue(view.proposal().contains("REJOIN_GROUP"));
        assertEquals("", view.label());
    }
    @Test void actualOwnedOrderKeepsItsOriginalReason() {
        var view = DecisionView.describe(coordinate, "", "WAIT_FOR_SUPPORT: Wait beside Heavy", proposal, "BasicShipAI", "RALLY_TASK_FORCE", "enemy");
        assertEquals("OWNED RALLY", view.state()); assertTrue(view.reason().contains("Wait beside Heavy"));
        assertEquals("Waiting for support", view.label());
        assertEquals("Regrouping", DecisionView.describe(coordinate, "", "REJOIN_GROUP: Rejoin Anchor", null,
                "BasicShipAI", "RALLY_TASK_FORCE", "enemy").label());
        assertEquals("Disengaging", DecisionView.describe(coordinate, "", "BREAK_PURSUIT: Stop futile chase", null,
                "BasicShipAI", "RALLY_TASK_FORCE", "enemy").label());
    }
    @Test void existingOrdersAndManualControlHaveExplicitReasons() {
        FakeCombat game = new FakeCombat(); var ship = game.add("ship"); var orders = new OrderBook(game.engine, s -> true);
        game.player.set(ship.member, new FakeCombat.Assignment(CombatAssignmentType.CAPTURE, null));
        assertEquals("Existing CAPTURE order", orders.blockReason(ship.ship, coordinate, 0));
        assertEquals("CAPTURE", orders.assignmentName(ship.ship));
        game.playerShip = ship.ship; game.autopilot = false;
        assertEquals("Manual player control", orders.blockReason(ship.ship, coordinate, 0));
        assertEquals("Manual player control", ControlGate.blockReason(game.engine, ship.ship));
    }
    @Test void missingPilotAndUnavailableOrderStateAreDistinguished() {
        FakeCombat game = new FakeCombat(); var ship = game.add("ship");
        assertEquals("No ship AI", ControlGate.blockReason(game.engine, ship.ship));
        var orders = new OrderBook(game.engine, s -> true); game.player.failRead = true;
        assertEquals("Order or controller state unreadable", orders.blockReason(ship.ship, coordinate, 0));
        assertFalse(orders.canManage(ship.ship, coordinate, 0));
    }
    @Test void disabledFleetAndUnmatchedRuleDoNotPretendToControlTheShip() {
        FakeCombat game = new FakeCombat(); var ship = game.add("ship");
        var orders = new OrderBook(game.engine, s -> true);
        var enemyOnly = new Options(true, true, false, true, true, true, true, "");
        assertEquals("Fleet excluded in settings", orders.blockReason(ship.ship, enemyOnly, 0));
        var view = DecisionView.describe(coordinate, "", "", null, "BasicShipAI", "none", "unreported");
        assertEquals("NO CHANGE", view.state()); assertEquals("No regrouping proposal", view.reason());
        assertEquals("", view.label());
    }
    @Test void diagnosticsAreReadOnly() {
        FakeCombat game = new FakeCombat(); var ship = game.add("ship"); var orders = new OrderBook(game.engine, s -> true);
        var exclusion = orders.blockReason(ship.ship, coordinate, 0);
        DecisionView.describe(coordinate, exclusion, orders.activePurpose(ship.ship), proposal, "BasicShipAI", orders.assignmentName(ship.ship), "enemy").label();
        assertEquals(0, game.player.created); assertEquals(0, game.player.assigned); assertEquals(0, game.player.removed);
        assertTrue(game.deleted.isEmpty());
    }
    @Test void pausedControlRefreshDoesNotKeepAnOldOrderDisplayedAsActive() {
        var active = DecisionView.describe(coordinate, "", "REJOIN_GROUP: Rejoin Anchor", proposal, "BasicShipAI", "RALLY_TASK_FORCE", "enemy");
        var manual = active.refreshControl(coordinate, "Manual player control", "", "none", "none");
        assertEquals("EXCLUDED", manual.state()); assertTrue(manual.reason().contains("Manual"));
        assertEquals("Regrouping", active.label()); assertEquals("", manual.label());
        assertEquals(active.proposal(), manual.proposal());
    }
}
