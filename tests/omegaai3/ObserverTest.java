package omegaai3;

import com.fs.starfarer.api.combat.*;
import omegaai3.runtime.Observer;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ObserverTest {
    @Test void hiddenEnemiesAreNeverReadForPositionOrWeapons() {
        FakeCombat game = new FakeCombat(); game.add("friend"); var enemy = game.add("hidden"); enemy.owner = 1;
        game.hidden.add(enemy.ship);
        enemy.calls.put("getLocation", a -> { throw new AssertionError("Hidden position read"); });
        enemy.calls.put("getAllWeapons", a -> { throw new AssertionError("Hidden loadout read"); });
        var read = new Observer().capture(game.engine, 0, 0, s -> true);
        assertEquals(1, read.frame().friends().size()); assertTrue(read.frame().enemies().isEmpty()); assertEquals(0, read.skipped());
    }
    @Test void actualDpAndGameVentTimeAreReadRatherThanGuessed() {
        FakeCombat game = new FakeCombat(); var unit = game.add("friend");
        var read = new Observer().capture(game.engine, 0, 0, s -> true);
        var ship = read.frame().friends().get(0);
        assertEquals(40, ship.dp()); assertEquals(42, ship.flux().ventTime());
        unit.location.x = 1000;
        assertEquals(0, ship.position().x(), "Snapshot must not retain live mutable vectors");
    }
    @Test void anEmptyLauncherContributesNoNewWeaponThreat() {
        FakeCombat game = new FakeCombat(); var unit = game.add("friend");
        var ds = FakeCombat.mock(WeaponAPI.DerivedWeaponStatsAPI.class, Map.of("getSustainedDps", a -> 500f, "getDamagePerShot", a -> 100f));
        unit.weapons.add(FakeCombat.mock(WeaponAPI.class, Map.ofEntries(
                Map.entry("getType", a -> WeaponAPI.WeaponType.MISSILE), Map.entry("getDerivedStats", a -> ds),
                Map.entry("getLocation", a -> new Vector2f()), Map.entry("getRange", a -> 1200f),
                Map.entry("usesAmmo", a -> true), Map.entry("getAmmo", a -> 0), Map.entry("getDamageType", a -> DamageType.HIGH_EXPLOSIVE)
        )));
        var read = new Observer().capture(game.engine, 0, 0, s -> true);
        assertFalse(read.frame().friends().get(0).guns().get(0).available());
    }
    @Test void stationModulesRemainVisibleThreatEntities() {
        FakeCombat game = new FakeCombat(); var module = game.add("module"); module.owner = 1;
        module.calls.put("isStationModule", a -> true);
        var read = new Observer().capture(game.engine, 0, 0, s -> true);
        assertEquals(1, read.frame().enemies().size()); assertTrue(read.frame().enemies().get(0).specialist());
    }
    @Test void projectileSamplingCanBeSkippedWhenDiagnosticsAreDisabled() {
        var engine = FakeCombat.mock(CombatEngineAPI.class, Map.of(
                "getShips", a -> java.util.List.of(),
                "getProjectiles", a -> { throw new AssertionError("Unneeded projectile enumeration"); },
                "getMissiles", a -> { throw new AssertionError("Unneeded missile enumeration"); }
        ));
        assertTrue(new Observer().capture(engine, 0, 0, s -> false, false).frame().shots().isEmpty());
    }
}
