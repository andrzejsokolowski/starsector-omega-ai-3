package omegaai3;

import omegaai3.model.*;
import omegaai3.tactics.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static omegaai3.Fixtures.*;
import static omegaai3.model.BattleFrame.*;
import static org.junit.jupiter.api.Assertions.*;

class TacticalPlannerTest {
    static Ship move(Ship s, Vec p, Vec velocity, Flux flux) {
        Vec offset = p.sub(s.position());
        List<Gun> guns = s.guns().stream().map(g -> new Gun(g.position().add(offset), g.range(), g.facing(), g.aim(), g.arc(), g.turnRate(),
                g.sustainedDps(), g.damagePerShot(), g.cooldown(), g.fluxPerSecond(), g.damage(), g.beam(), g.missile(), g.pd(), g.available())).toList();
        return new Ship(s.id(), s.name(), s.kind(), p, velocity, s.speed(), s.acceleration(), s.deceleration(), s.radius(), s.dp(), s.hull(), s.maxHull(),
                flux, guns, s.incapacitated(), s.specialist(), s.fighter(), s.controllable(), s.targetId(), s.defense());
    }
    static Ship stationary(Ship s) { return move(s, s.position(), Vec.ZERO, s.flux()); }
    @Test void closesAndSettlesInsideBatteryRangeInARepeatedMovementSimulation() {
        TacticalPlanner planner = new TacticalPlanner();
        Ship me = stationary(ship("me", Kind.DESTROYER, -2000, 100, 10, 800, "enemy"));
        Ship enemy = stationary(ship("enemy", Kind.FRIGATE, 0, 80, 5, 400, null));
        for (int step = 0; step < 200; step++) {
            var intent = planner.plan(frame(step * .25, List.of(me), List.of(enemy))).get("me");
            assertNotNull(intent);
            Vec change = intent.velocity().sub(me.velocity());
            if (change.length() > me.acceleration() * .25) change = change.unit().scale(me.acceleration() * .25);
            Vec velocity = me.velocity().add(change);
            me = move(me, me.position().add(velocity.scale(.25)), velocity, me.flux());
        }
        double distance = me.position().distance(enemy.position());
        assertTrue(distance < me.usefulRange() + enemy.radius(), "Must actually reach firing range: " + distance);
        assertTrue(distance > me.radius() + enemy.radius() + 100, "Must not ram target");
        assertTrue(me.velocity().length() < 5, "Must settle instead of oscillating");
    }
    @Test void backsOutBeforeFluxSaturationAndKeepsRetreatUntilReserveRecovers() {
        TacticalPlanner planner = new TacticalPlanner();
        Ship me = stationary(atFlux(ship("me", Kind.FRIGATE, -400, 160, 5, 400, "enemy"), .8));
        Ship enemy = stationary(ship("enemy", Kind.CAPITAL, 0, 40, 60, 1200, null));
        var retreat = planner.plan(frame(0, List.of(me), List.of(enemy))).get("me");
        assertEquals(TacticalIntent.Action.RETREAT, retreat.action());
        assertTrue(retreat.velocity().x() < 0);
        me = move(me, new Vec(-800, 0), retreat.velocity(), flux(6000, 5000));
        assertEquals(TacticalIntent.Action.RETREAT, planner.plan(frame(3, List.of(me), List.of(enemy))).get("me").action());
        me = move(me, new Vec(-2000, 0), Vec.ZERO, flux(1000, 500));
        assertNotEquals(TacticalIntent.Action.RETREAT, planner.plan(frame(8, List.of(me), List.of(enemy))).get("me").action());
    }
    @Test void stopsChasingAnOpeningFasterTarget() {
        Ship me = stationary(ship("me", Kind.CRUISER, 0, 70, 20, 700, "enemy"));
        Ship enemy = ship("enemy", Kind.FRIGATE, 1800, 180, 5, 500, null);
        var intent = new TacticalPlanner().plan(frame(0, List.of(me), List.of(enemy))).get("me");
        assertEquals(TacticalIntent.Action.DISENGAGE, intent.action());
        assertTrue(intent.velocity().length() < 1);
    }
    @Test void waitsForHeavySupportInsteadOfStartingAnUnsupportedFight() {
        Ship me = stationary(ship("me", Kind.FRIGATE, -1400, 160, 5, 400, "enemy"));
        Ship anchor = stationary(ship("anchor", Kind.CAPITAL, -3000, 40, 60, 1000, "enemy"));
        Ship enemy = stationary(ship("enemy", Kind.CAPITAL, 0, 40, 60, 1200, null));
        var intent = new TacticalPlanner().plan(frame(0, List.of(me, anchor), List.of(enemy))).get("me");
        assertEquals(TacticalIntent.Action.WAIT, intent.action());
        assertTrue(intent.velocity().x() < 0);
    }
    @Test void pursuesReachableFluxedTargetAndSharesThatTarget() {
        Ship a = stationary(ship("a", Kind.DESTROYER, -1000, 160, 10, 600, null));
        Ship b = stationary(ship("b", Kind.DESTROYER, -1100, 160, 10, 600, null));
        Ship wounded = atFlux(ship("wounded", Kind.DESTROYER, 0, 60, 10, 600, null), .85);
        Ship fresh = stationary(ship("fresh", Kind.DESTROYER, 100, 60, 10, 600, null));
        var plans = new TacticalPlanner().plan(frame(0, List.of(a, b), List.of(fresh, wounded)));
        assertEquals("wounded", plans.get("a").targetId()); assertEquals("wounded", plans.get("b").targetId());
        assertEquals(TacticalIntent.Action.PURSUE, plans.get("a").action());
        assertTrue(plans.get("a").velocity().x() > wounded.velocity().x());
    }
    @Test void broadsideHullFacesItsBatteryTowardTheTarget() {
        Ship base = stationary(ship("broadside", Kind.CRUISER, -700, 80, 20, 800, null));
        Gun broadside = new Gun(base.position(), 800, 90, 90, 60, 30, 500, 100, 0, 100, Damage.ENERGY, false, false, false, true);
        Ship me = new Ship(base.id(), base.name(), base.kind(), base.position(), base.velocity(), base.speed(), base.acceleration(), base.deceleration(), base.radius(),
                base.dp(), base.hull(), base.maxHull(), base.flux(), List.of(broadside), false, false, false, true, null, base.defense());
        Ship enemy = stationary(ship("enemy", Kind.DESTROYER, 0, 60, 10, 600, null));
        assertEquals(-90, TacticalPlanner.firingFacing(me, enemy), .001);
    }
    @Test void plannedPathDoesNotRunStraightThroughAFriendlyHulk() {
        Ship me = stationary(ship("me", Kind.DESTROYER, -1800, 100, 10, 800, null));
        Ship enemy = stationary(ship("enemy", Kind.FRIGATE, 0, 80, 5, 400, null));
        BattleFrame f = new BattleFrame(0, 0, 10000, 10000, List.of(me), List.of(enemy), List.of(),
                List.of(new Obstacle("hulk", new Vec(-1600, 0), Vec.ZERO, 100)));
        Vec v = new TacticalPlanner().plan(f).get("me").velocity();
        assertTrue(v.x() < 50 || Math.abs(v.y()) > 40, "Must brake or steer around obstacle");
    }
    @Test void hiddenOrUncontrolledShipsReceiveNoIntent() {
        Ship me = stationary(ship("me", Kind.DESTROYER, 0, 100, 10, 800, null));
        assertTrue(new TacticalPlanner().plan(frame(0, List.of(me), List.of())).isEmpty());
        assertTrue(new TacticalPlanner().plan(frame(0, List.of(control(me, false, false)), List.of(me))).isEmpty());
    }
}
