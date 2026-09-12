package omegaai3;

import omegaai3.model.CombatMath;
import omegaai3.model.Vec;
import omegaai3.runtime.ControlGate;
import org.junit.jupiter.api.Test;
import java.util.List;
import static omegaai3.model.BattleFrame.*;
import static omegaai3.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class CombatMathTest {
    @Test void raisedShieldCannotDissipateHardFluxWithSpareDissipation() {
        Flux f = new Flux(0, 0, 1000, 500, 0, 0, true, 1, 0, true);
        var result = CombatMath.fluxAfter(f, 0, 0, 200, 2);
        assertEquals(400, result.hard(), 1e-5); assertEquals(400, result.total(), 1e-5);
    }
    @Test void beamSoftFluxCanDissipateWhileShieldIsRaised() {
        Flux f = new Flux(0, 0, 1000, 500, 0, 0, true, 1, 0, true);
        assertEquals(0, CombatMath.fluxAfter(f, 0, 300, 0, 2).total(), 1e-5);
    }
    @Test void weaponsAndUpkeepShareFluxCapacity() {
        Flux f = new Flux(100, 0, 1000, 100, 0, 50, true, 1, 0, true);
        assertEquals(400, CombatMath.fluxAfter(f, 200, 0, 0, 2).total(), 1e-5);
    }
    @Test void hardFluxSetsFloorAndLoweredShieldRemovesIt() {
        Flux up = new Flux(500, 500, 1000, 100, 0, 0, true, 1, 0, true);
        assertEquals(500, CombatMath.fluxAfter(up, 0, 0, 0, 5).total(), 1e-5);
        Flux down = new Flux(500, 500, 1000, 100, 0, 0, false, 1, 0, true);
        assertEquals(0, CombatMath.fluxAfter(down, 0, 0, 0, 5).hard(), 1e-5);
    }
    @Test void hardDissipationUsesPartOfTotalBudgetNotAnotherWholeBudget() {
        Flux f = new Flux(500, 400, 1000, 100, .5, 0, true, 1, 0, true);
        var r = CombatMath.fluxAfter(f, 0, 0, 0, 1);
        assertEquals(400, r.total(), 1e-5); assertEquals(350, r.hard(), 1e-5);
    }
    @Test void shieldsCanOverloadButWeaponFluxAloneIsNotAnOverload() {
        Flux f = new Flux(900, 900, 1000, 100, 0, 0, true, 1, 0, true);
        assertTrue(CombatMath.fluxAfter(f, 0, 0, 200, 1).overloadRisk());
        assertFalse(CombatMath.fluxAfter(f, 300, 0, 0, 1).overloadRisk());
    }
    @Test void damageTypesNeverApplyArmorBonusToHull() {
        for (Damage d : Damage.values()) assertEquals(1, d.hull());
        assertEquals(4, Damage.KINETIC.shield / Damage.HIGH_EXPLOSIVE.shield);
    }
    @Test void interceptionDistinguishesPursuitFromHeadOnMeeting() {
        assertEquals(9, CombatMath.interceptTime(new Vec(1000, 0), Vec.ZERO, 100, 100), 1e-8);
        assertEquals(Double.POSITIVE_INFINITY, CombatMath.interceptTime(new Vec(1000, 0), new Vec(120, 0), 80, 100));
        assertEquals(4.5, CombatMath.interceptTime(new Vec(1000, 0), new Vec(-120, 0), 80, 100), 1e-8);
        assertEquals(Double.POSITIVE_INFINITY, CombatMath.interceptTime(new Vec(1000, 0), new Vec(80, 0), 80, 100));
    }
    @Test void perpendicularInterceptionUsesVectorGeometry() {
        assertEquals(12.5, CombatMath.interceptTime(new Vec(1000, 0), new Vec(0, 60), 100, 0), 1e-8);
        assertEquals(0, CombatMath.interceptTime(new Vec(10, 0), new Vec(100, 0), 0, 100));
    }
    @Test void projectileThreatSurvivesWithoutAnyLauncherReference() {
        Ship s = ship("victim", Kind.FRIGATE, 0, 0, 5, 400, null);
        Shot shot = new Shot(new Vec(1000, 0), new Vec(-500, 0), 10, 4000, Damage.HIGH_EXPLOSIVE, false, false, 500, 10);
        assertEquals(1.88, CombatMath.impactTime(shot, s, 3), 1e-8);
        assertEquals(Double.POSITIVE_INFINITY, CombatMath.impactTime(shot, s, 1));
    }
    @Test void missileLifetimeLimitsTheReachabilityBound() {
        Ship s = ship("victim", Kind.FRIGATE, 0, 0, 5, 400, null);
        Shot shot = new Shot(new Vec(1000, 0), new Vec(500, 0), 10, 4000, Damage.HIGH_EXPLOSIVE, false, true, 500, .5);
        assertEquals(Double.POSITIVE_INFINITY, CombatMath.impactTime(shot, s, 3));
    }
    @Test void turretSlewDoesNotExtendItsPhysicalMountArc() {
        Gun g = new Gun(Vec.ZERO, 1000, 90, 90, 30, 360, 500, 500, 0, 100, Damage.ENERGY, false, false, false, true);
        assertFalse(g.canReach(new Vec(500, 0), 30, 4));
        assertTrue(g.canReach(new Vec(0, 500), 30, 1));
    }
    @Test void shortSecondaryDoesNotSetTheFleetSupportRange() {
        Ship s = ship("ship", Kind.CRUISER, 0, 80, 20, 1000, null);
        Ship varied = new Ship(s.id(), s.name(), s.kind(), s.position(), s.velocity(), s.speed(), s.acceleration(), s.deceleration(), s.radius(),
                s.dp(), s.hull(), s.maxHull(), s.flux(), List.of(gun(Vec.ZERO, 1000, 900), gun(Vec.ZERO, 300, 100)), false, false, false, true, null);
        assertEquals(1000, varied.usefulRange());
    }
    @Test void brakingAndUnknownControllersAreExplicit() {
        assertEquals(900, CombatMath.stoppingDistance(300, 50));
        assertEquals(Double.POSITIVE_INFINITY, CombatMath.stoppingDistance(300, 0));
        assertTrue(ControlGate.knownPilot("com.fs.starfarer.combat.ai.BasicShipAI"));
        assertFalse(ControlGate.knownPilot("com.fs.starfarer.combat.entities.Ship$ShipAIWrapper"));
        assertFalse(ControlGate.knownPilot("some.mod.CustomAI"));
    }
    @Test void allThreeRtsAssistControlMarkersAreRespected() {
        for (String key : List.of("RTS_BLOCKAI_ForYourOwnSakeDontUseThis", "RTS_OVERRIDETHRUST_ForYourOwnSakeDontUseThis", "RTS_OVERRIDEFACING_ForYourOwnSakeDontUseThis")) {
            assertTrue(ControlGate.externallyControlled(java.util.Map.of(key, true)));
            assertTrue(ControlGate.externallyControlled(java.util.Map.of(key, new Object())));
            assertTrue(ControlGate.externallyControlled(java.util.Map.of(key, false)));
        }
        assertFalse(ControlGate.externallyControlled(java.util.Map.of("unrelated", true)));
    }
    @Test void zeroDurationDoesNotInventAnOverloadAtFullFlux() {
        Flux f = new Flux(1000, 0, 1000, 100, 0, 0, true, 1, 0, true);
        assertFalse(CombatMath.fluxAfter(f, 0, 0, 0, 0).overloadRisk());
    }
}
