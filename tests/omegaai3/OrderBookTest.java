package omegaai3;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import omegaai3.model.Vec;
import omegaai3.plan.FleetPlanner;
import omegaai3.runtime.OrderBook;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {
    final FakeCombat game = new FakeCombat();
    final FakeCombat.Unit unit = game.add("unit");
    final OrderBook book = new OrderBook(game.engine, ship -> true);
    final Options coordinate = new Options(true, true, true, true, true, false, false, "");
    final FleetPlanner.Proposal proposal = new FleetPlanner.Proposal("unit", "anchor", new Vec(2000, 0), FleetPlanner.Reason.REJOIN_GROUP, "test");
    void issue() { assertEquals(OrderBook.Result.CREATED, book.apply(unit.ship, proposal, coordinate, 0)); }

    @Test void observeMakesNoGameplayWrites() {
        assertEquals(OrderBook.Result.BLOCKED, book.apply(unit.ship, proposal, Options.defaults(), 0));
        assertEquals(0, game.player.created); assertEquals(0, game.player.assigned); assertTrue(game.deleted.isEmpty());
    }
    @Test void existingPlayerAndCommanderOrdersArePreserved() {
        for (CombatAssignmentType type : CombatAssignmentType.values()) {
            var task = new FakeCombat.Assignment(type, new FakeCombat.Waypoint(new org.lwjgl.util.vector.Vector2f()));
            game.player.set(unit.member, task);
            assertEquals(OrderBook.Result.BLOCKED, book.apply(unit.ship, proposal, coordinate, 0), type.name());
            assertSame(task, game.player.current.get(unit.ship));
        }
        assertEquals(0, game.player.created); assertEquals(0, game.player.removed);
    }
    @Test void globalAvoidAndIgnoreStopUnassignedShipsToo() {
        for (var type : new CombatAssignmentType[]{CombatAssignmentType.AVOID, CombatAssignmentType.IGNORE}) {
            game.player.all.clear(); game.player.all.add(new FakeCombat.Assignment(type, null));
            assertFalse(book.canManage(unit.ship, coordinate, 0));
        }
    }
    @Test void fullAssaultAndRetreatAreNotSecondGuessed() {
        game.player.fullAssault = true; assertFalse(book.canManage(unit.ship, coordinate, 0));
        game.player.fullAssault = false; game.player.fullRetreat = true; assertFalse(book.canManage(unit.ship, coordinate, 0));
        game.player.fullRetreat = false; game.enemy.fullRetreat = true; assertFalse(book.canManage(unit.ship, coordinate, 0));
    }
    @Test void acceptedOrdersAreReusedThenReleasedOnDisable() {
        issue(); Object original = game.player.current.get(unit.ship);
        assertEquals(OrderBook.Result.REFRESHED, book.apply(unit.ship, proposal, coordinate, .25));
        assertSame(original, game.player.current.get(unit.ship)); assertEquals(1, game.player.created);
        book.reconcile(Options.defaults(), .3);
        assertEquals(0, book.active()); assertNull(game.player.current.get(unit.ship)); assertEquals(1, game.player.removed); assertEquals(1, game.deleted.size());
    }
    @Test void manualControlIsProtectedAtApplicationAndBetweenPlanningTicks() {
        game.playerShip = unit.ship; game.autopilot = false;
        assertEquals(OrderBook.Result.BLOCKED, book.apply(unit.ship, proposal, coordinate, 0));
        game.autopilot = true; issue(); game.autopilot = false;
        book.reconcile(coordinate, .01);
        assertEquals(0, book.active()); assertNull(game.player.current.get(unit.ship));
    }
    @Test void newPlayerOrderIsNotRemovedWhenOldLeaseIsCleanedUp() {
        issue(); var playerOrder = new FakeCombat.Assignment(CombatAssignmentType.DEFEND, new FakeCombat.Waypoint(new org.lwjgl.util.vector.Vector2f()));
        game.player.set(unit.member, playerOrder);
        book.reconcile(coordinate, .1);
        assertSame(playerOrder, game.player.current.get(unit.ship)); assertTrue(game.player.all.contains(playerOrder)); assertEquals(0, book.active());
    }
    @Test void movedPlayerWaypointBecomesPlayerOwned() {
        issue(); var task = game.player.current.get(unit.ship); task.getTarget().getLocation().x += 100;
        book.reconcile(coordinate, .1);
        assertSame(task, game.player.current.get(unit.ship)); assertEquals(0, book.active()); assertEquals(0, game.player.removed); assertTrue(game.deleted.isEmpty());
    }
    @Test void sharedPlayerWaypointBecomesPlayerOwned() {
        issue(); var task = game.player.current.get(unit.ship); FakeCombat.Unit second = game.add("second");
        game.player.set(second.member, task); book.releaseAll(.1);
        assertSame(task, game.player.current.get(unit.ship)); assertSame(task, game.player.current.get(second.ship)); assertEquals(0, game.player.removed);
    }
    @Test void rejectedOrdersDoNotLeakTheirAssignmentOrWaypoint() {
        game.player.reject = true;
        assertEquals(OrderBook.Result.REJECTED, book.apply(unit.ship, proposal, coordinate, 0));
        assertEquals(0, book.active()); assertTrue(game.player.all.isEmpty()); assertEquals(1, game.deleted.size());
    }
    @Test void unreadableOrdersFailClosed() {
        game.player.failRead = true;
        assertEquals(OrderBook.Result.BLOCKED, book.apply(unit.ship, proposal, coordinate, 0));
        assertEquals(0, game.player.created);
    }
    @Test void stalledAndExpiredOrdersHaveACooldown() {
        issue();
        for (int i = 1; i <= 6; i++) { book.apply(unit.ship, proposal, coordinate, i); book.reconcile(coordinate, i); }
        assertEquals(0, book.active()); assertFalse(book.canManage(unit.ship, coordinate, 7)); assertTrue(book.canManage(unit.ship, coordinate, 15));
    }
    @Test void expiredLeaseIsReleasedWithoutANewProposal() {
        issue(); book.reconcile(coordinate, 1.5);
        assertEquals(0, book.active()); assertNull(game.player.current.get(unit.ship));
    }
    @Test void otherCoordinatorAndPerSideSwitchesBlockWrites() {
        var conflict = new Options(true, true, true, true, true, true, false, "AI Tweaks");
        assertEquals(OrderBook.Result.BLOCKED, book.apply(unit.ship, proposal, conflict, 0));
        var enemyOnly = new Options(true, true, false, true, true, true, false, "");
        assertEquals(OrderBook.Result.BLOCKED, book.apply(unit.ship, proposal, enemyOnly, 0));
        unit.ally = true; assertEquals(OrderBook.Result.BLOCKED, book.apply(unit.ship, proposal, coordinate, 0));
    }
    @Test void failureAfterAssignmentRetainsEnoughOwnershipToCleanUp() {
        game.player.throwAfterGive = true;
        assertThrows(IllegalStateException.class, () -> book.apply(unit.ship, proposal, coordinate, 0));
        assertEquals(1, book.active());
        book.releaseAll(.1);
        assertEquals(0, book.active()); assertTrue(game.player.all.isEmpty()); assertEquals(1, game.deleted.size());
    }
    @Test void failedCleanupCanBeRetriedWithoutLosingOwnership() {
        issue(); game.player.failRemove = true;
        assertThrows(IllegalStateException.class, () -> book.releaseAll(.1));
        assertEquals(1, book.active());
        game.player.failRemove = false; book.releaseAll(.2);
        assertEquals(0, book.active()); assertTrue(game.player.all.isEmpty()); assertEquals(1, game.deleted.size());
    }
}
