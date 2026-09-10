package omegaai3;

import omegaai3.plan.FleetPlanner;
import org.junit.jupiter.api.Test;
import java.util.List;
import static omegaai3.model.BattleFrame.*;
import static omegaai3.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class FleetPlannerTest {
    private final Ship anchor = ship("anchor", Kind.CAPITAL, 0, 25, 40, 1200, null);
    @Test void futileChaseRequiresObservedLackOfProgress() {
        var planner = new FleetPlanner();
        Ship hunter = ship("hunter", Kind.CRUISER, 3000, 80, 20, 700, "runner");
        Ship runner = ship("runner", Kind.FRIGATE, 5000, 120, 5, 400, null);
        assertTrue(planner.plan(frame(0, List.of(anchor, hunter), List.of(runner))).isEmpty());
        assertTrue(planner.plan(frame(5, List.of(anchor, hunter), List.of(runner))).isEmpty());
        var result = planner.plan(frame(6, List.of(anchor, hunter), List.of(runner)));
        assertEquals(1, result.size()); assertEquals(FleetPlanner.Reason.BREAK_PURSUIT, result.get(0).reason());
    }
    @Test void usefulInterceptionAndActiveFireAreNotLeashed() {
        var planner = new FleetPlanner();
        Ship hunter = ship("hunter", Kind.CRUISER, 3000, 200, 20, 700, "runner");
        Ship runner = ship("runner", Kind.FRIGATE, 3500, 120, 5, 400, null);
        planner.plan(frame(0, List.of(anchor, hunter), List.of(runner)));
        assertTrue(planner.plan(frame(10, List.of(anchor, hunter), List.of(runner))).isEmpty());
    }
    @Test void unsupportedFrigateWaitsForHeavierSupport() {
        var planner = new FleetPlanner();
        Ship scout = ship("scout", Kind.FRIGATE, 3000, 150, 5, 400, "capital");
        Ship enemy = ship("capital", Kind.CAPITAL, 4000, 0, 40, 1200, null);
        var result = planner.plan(frame(0, List.of(anchor, scout), List.of(enemy)));
        assertEquals(1, result.size()); assertEquals(FleetPlanner.Reason.WAIT_FOR_SUPPORT, result.get(0).reason());
    }
    @Test void twoEarlyScoutsDoNotMistakeEachOtherForHeavySupport() {
        var planner = new FleetPlanner();
        Ship a = ship("a", Kind.FRIGATE, 3000, 150, 5, 400, "capital");
        Ship b = ship("b", Kind.FRIGATE, 3000, 150, 5, 400, "capital");
        Ship enemy = ship("capital", Kind.CAPITAL, 4000, 0, 40, 1200, null);
        var plans = planner.plan(frame(0, List.of(anchor, a, b), List.of(enemy)));
        assertEquals(2, plans.size());
        assertTrue(plans.get(0).destination().distance(plans.get(1).destination()) >= 260);
    }
    @Test void safeSmallShipDuelDoesNotWaitForCapital() {
        var planner = new FleetPlanner();
        Ship scout = ship("scout", Kind.FRIGATE, 3000, 150, 5, 400, "peer");
        Ship enemy = ship("peer", Kind.FRIGATE, 4000, 0, 5, 400, null);
        assertTrue(planner.plan(frame(0, List.of(anchor, scout), List.of(enemy))).isEmpty());
    }
    @Test void overfluxedFriendIsNotAUsefulAnchor() {
        var planner = new FleetPlanner();
        Ship scout = ship("scout", Kind.FRIGATE, 3000, 150, 5, 400, "capital");
        Ship enemy = ship("capital", Kind.CAPITAL, 4000, 0, 40, 1200, null);
        assertTrue(planner.plan(frame(0, List.of(atFlux(anchor, .95), scout), List.of(enemy))).isEmpty());
    }
    @Test void foreignOrdersAndSpecialistPilotsAreExcluded() {
        var planner = new FleetPlanner();
        Ship scout = ship("scout", Kind.FRIGATE, 3000, 150, 5, 400, "capital");
        Ship enemy = ship("capital", Kind.CAPITAL, 4000, 0, 40, 1200, null);
        assertTrue(planner.plan(frame(0, List.of(anchor, control(scout, false, false)), List.of(enemy))).isEmpty());
        assertTrue(planner.plan(frame(0, List.of(anchor, control(scout, true, true)), List.of(enemy))).isEmpty());
    }
    @Test void noVisibleEnemiesMeansNoRegroupOrders() {
        assertTrue(new FleetPlanner().plan(frame(0, List.of(anchor, ship("scout", Kind.FRIGATE, 3000, 150, 5, 400, null)), List.of())).isEmpty());
    }
    @Test void idleStragglerCanRejoinABattleGroup() {
        Ship straggler = ship("late", Kind.CRUISER, -4000, 80, 20, 700, null);
        Ship enemy = ship("enemy", Kind.CRUISER, 2000, 0, 20, 700, null);
        var result = new FleetPlanner().plan(frame(0, List.of(anchor, straggler), List.of(enemy)));
        assertEquals(1, result.size()); assertEquals(FleetPlanner.Reason.REJOIN_GROUP, result.get(0).reason());
    }
    @Test void regroupDoesNotCrossAnEnemyLineToReachTheAnchor() {
        Ship straggler = ship("late", Kind.CRUISER, -4000, 80, 20, 700, null);
        Ship obstruction = ship("barrier", Kind.CAPITAL, -2000, 0, 40, 1200, null);
        assertTrue(new FleetPlanner().plan(frame(0, List.of(anchor, straggler), List.of(obstruction))).isEmpty());
    }
    @Test void targetChangeResetsTheChaseTimer() {
        var planner = new FleetPlanner();
        Ship hunter = ship("hunter", Kind.CRUISER, 3000, 80, 20, 700, "runner");
        Ship runner = ship("runner", Kind.FRIGATE, 5000, 160, 5, 400, null);
        planner.plan(frame(0, List.of(anchor, hunter), List.of(runner)));
        hunter = ship("hunter", Kind.CRUISER, 3000, 80, 20, 700, "new-runner");
        runner = ship("new-runner", Kind.FRIGATE, 5000, 160, 5, 400, null);
        assertTrue(planner.plan(frame(7, List.of(anchor, hunter), List.of(runner))).isEmpty());
    }
}
