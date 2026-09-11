package omegaai3;

import com.fs.starfarer.combat.entities.Ship;
import omegaai3.runtime.Rc8Helm;
import omegaai3.tactics.TacticalIntent;
import omegaai3.model.Vec;
import org.lwjgl.util.vector.Vector2f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Rc8HelmTest {
    @org.junit.jupiter.api.BeforeAll static void nativeSettings() throws Exception {
        // Test harness only: initialize the native settings table without launching a graphics context.
        var field = com.fs.starfarer.settings.StarfarerSettings.class.getDeclaredField("\u00f5\u00d80000");
        field.setAccessible(true);
        String raw = java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("omega.test.core"), "data/config/settings.json"));
        StringBuilder json = new StringBuilder(); boolean quoted = false, comment = false, escaped = false;
        for (char c : raw.toCharArray()) {
            if (c == '\n') comment = false;
            if (comment) continue;
            if (!quoted && c == '#') { comment = true; continue; }
            json.append(c);
            if (c == '"' && !escaped) quoted = !quoted;
            escaped = c == '\\' && !escaped;
        }
        field.set(null, new org.json.JSONObject(json.toString()));
    }
    private Ship ship(List<Ship.Oo> commands) {
        Ship ship = mock(Ship.class, RETURNS_DEEP_STUBS);
        when(ship.getCommands()).thenReturn(commands);
        doReturn(EnumSet.noneOf(Ship.oo.class)).when(ship).getBlockedCommands();
        when(ship.getLocation()).thenReturn(new Vector2f());
        when(ship.getVelocity()).thenReturn(new Vector2f());
        when(ship.getFacing()).thenReturn(0f);
        when(ship.getMaxSpeed()).thenReturn(100f);
        when(ship.getAcceleration()).thenReturn(100f);
        when(ship.getDeceleration()).thenReturn(100f);
        when(ship.getMaxTurnRate()).thenReturn(60f);
        when(ship.getTurnAcceleration()).thenReturn(120f);
        when(ship.getEngineController().getEffectiveMaxSpeed()).thenReturn(100f);
        when(ship.getEngineController().getEffectiveAcceleration()).thenReturn(100f);
        when(ship.getEngineController().getEffectiveDeceleration()).thenReturn(100f);
        when(ship.getEngineController().getEffectiveStrafeAcceleration()).thenReturn(100f);
        doAnswer(call -> { commands.add(call.getArgument(0)); return null; }).when(ship).giveCommand(any(Ship.Oo.class));
        return ship;
    }
    private static Ship.Oo command(String name) { return new Ship.Oo(Ship.oo.valueOf(name), null); }
    private static TacticalIntent intent(Vec velocity, double facing) {
        return new TacticalIntent(TacticalIntent.Action.ADVANCE, "enemy", velocity, facing, 10, "test", 20);
    }
    private static Set<String> names(List<Ship.Oo> commands) {
        Set<String> names = new HashSet<>();
        for (Ship.Oo c : commands) names.add(c.\u00d200000.name());
        return names;
    }
    @Test void actualNativeEngineEmitsForwardAndBackwardThrust() {
        List<Ship.Oo> commands = new ArrayList<>(); Ship ship = ship(commands); Rc8Helm helm = new Rc8Helm(ship);
        helm.steer(intent(new Vec(80, 0), 0), .016f, Set.of());
        assertTrue(names(commands).contains("ACCELERATE"), names(commands).toString());
        commands.clear();
        helm.steer(intent(new Vec(-80, 0), 0), .016f, Set.of());
        assertTrue(names(commands).contains("ACCELERATE_BACKWARDS"), names(commands).toString());
    }
    @Test void movementReplacementPreservesWeaponsShieldsAndExistingCommands() {
        List<Ship.Oo> commands = new ArrayList<>(); Ship ship = ship(commands);
        Ship.Oo fire = command("FIRE"), shield = command("TOGGLE_SHIELD"), nativeMove = command("ACCELERATE"), oldTurn = command("TURN_LEFT");
        commands.addAll(List.of(fire, shield, nativeMove, oldTurn));
        new Rc8Helm(ship).steer(intent(new Vec(-80, 0), 0), .016f, Set.of(oldTurn));
        assertTrue(commands.contains(fire)); assertTrue(commands.contains(shield)); assertTrue(commands.contains(oldTurn));
        assertFalse(commands.contains(nativeMove)); assertTrue(names(commands).contains("ACCELERATE_BACKWARDS"));
    }
    @Test void requestedWorldSpeedBelowCurrentSpeedProducesBraking() {
        List<Ship.Oo> commands = new ArrayList<>(); Ship ship = ship(commands);
        when(ship.getVelocity()).thenReturn(new Vector2f(70, 0));
        new Rc8Helm(ship).steer(intent(new Vec(20, 0), 0), .016f, Set.of());
        assertFalse(names(commands).contains("ACCELERATE"), names(commands).toString());
        assertTrue(names(commands).contains("DECELERATE") || names(commands).contains("ACCELERATE_BACKWARDS"), names(commands).toString());
    }
    @Test void nativeFailureRestoresTheMovementCommandsItReplaced() {
        List<Ship.Oo> commands = new ArrayList<>(); Ship ship = ship(commands);
        Ship.Oo original = command("ACCELERATE"), fire = command("FIRE"); commands.addAll(List.of(original, fire));
        when(ship.getEngineController().getEffectiveMaxSpeed()).thenThrow(new IllegalStateException("engine unavailable"));
        assertThrows(IllegalStateException.class, () -> new Rc8Helm(ship).steer(intent(new Vec(-80, 0), 0), .016f, Set.of()));
        assertTrue(commands.contains(original)); assertTrue(commands.contains(fire)); assertEquals(2, commands.size());
    }
    @Test void repeatedNativeThrustUpdatesSettleAtRequestedSpeedAndThenStop() {
        List<Ship.Oo> commands = new ArrayList<>(); Ship ship = ship(commands);
        Vector2f velocity = new Vector2f(70, 0); when(ship.getVelocity()).thenReturn(velocity);
        Rc8Helm helm = new Rc8Helm(ship);
        for (double goal : new double[] {20, 0, -30}) {
            for (int frame = 0; frame < 120; frame++) {
                commands.clear(); helm.steer(intent(new Vec(goal, 0), 0), 1f / 60, Set.of());
                Set<String> issued = names(commands);
                if (issued.contains("ACCELERATE")) velocity.x += 100f / 60;
                if (issued.contains("ACCELERATE_BACKWARDS")) velocity.x -= 100f / 60;
                if (issued.contains("DECELERATE")) velocity.x -= Math.copySign(Math.min(Math.abs(velocity.x), 100f / 60), velocity.x);
            }
            assertEquals(goal, velocity.x, 2, "Native commands must converge on desired velocity");
        }
    }
    @Test void onlyTheVerifiedGameVersionEnablesTheNativeBridge() {
        assertTrue(Rc8Helm.supported("0.98a-RC8")); assertTrue(Rc8Helm.supported("Starsector 0.98a-RC8"));
        assertFalse(Rc8Helm.supported("0.98a-RC9")); assertFalse(Rc8Helm.supported(null));
    }
    @Test void explicitFacingCanStrafeWithoutPointingTheHullAlongTheVelocity() {
        List<Ship.Oo> commands = new ArrayList<>(); Ship ship = ship(commands);
        new Rc8Helm(ship).steer(intent(new Vec(0, 80), 0), .016f, Set.of());
        assertTrue(names(commands).stream().anyMatch(n -> n.startsWith("STRAFE_")), names(commands).toString());
        assertFalse(names(commands).contains("TURN_LEFT")); assertFalse(names(commands).contains("TURN_RIGHT"));
    }
    @Test void nativePilotCanBeConstructedAndManualTakeoverEmitsNoCommands() {
        List<Ship.Oo> commands = new ArrayList<>(); Ship ship = ship(commands);
        when(ship.getSystem()).thenReturn(null);
        when(ship.getShield()).thenReturn(null);
        var engine = mock(com.fs.starfarer.api.combat.CombatEngineAPI.class);
        try (var singleton = mockStatic(com.fs.starfarer.combat.CombatEngine.class)) {
        singleton.when(com.fs.starfarer.combat.CombatEngine::getInstance).thenReturn(mock(com.fs.starfarer.combat.CombatEngine.class, RETURNS_DEEP_STUBS));
        var pilot = new omegaai3.runtime.OmegaShipAI(ship, new com.fs.starfarer.api.combat.ShipAIConfig(), engine, () -> true);
        when(ship.getShipAI()).thenReturn(pilot);
        when(engine.getPlayerShip()).thenReturn(ship);
        when(engine.isUIAutopilotOn()).thenReturn(false);
        pilot.publish(intent(new Vec(100, 0), 0), ship);
        pilot.advance(.016f);
        assertTrue(commands.isEmpty()); assertNull(pilot.activeIntent());
        }
    }
    @Test void installedPilotConsumesAnIntentAndExpiresItsLabel() {
        List<Ship.Oo> commands = new ArrayList<>(); Ship ship = ship(commands), target = ship(new ArrayList<>());
        when(ship.getSystem()).thenReturn(null); when(ship.getShield()).thenReturn(null);
        when(ship.getHullSize()).thenReturn(com.fs.starfarer.api.combat.ShipAPI.HullSize.DESTROYER);
        when(target.isAlive()).thenReturn(true);
        var engine = mock(com.fs.starfarer.api.combat.CombatEngineAPI.class);
        when(engine.isAwareOf(anyInt(), any())).thenReturn(true);
        try (var singleton = mockStatic(com.fs.starfarer.combat.CombatEngine.class)) {
            singleton.when(com.fs.starfarer.combat.CombatEngine::getInstance).thenReturn(mock(com.fs.starfarer.combat.CombatEngine.class, RETURNS_DEEP_STUBS));
            var pilot = new omegaai3.runtime.OmegaShipAI(ship, new com.fs.starfarer.api.combat.ShipAIConfig(), engine, () -> true);
            when(ship.getShipAI()).thenReturn(pilot); when(ship.getAI()).thenReturn(pilot);
            pilot.publish(intent(new Vec(80, 0), 0), target);
            assertNull(pilot.activeIntent(), "Publishing alone must not display a label");
            pilot.advance(.016f);
            assertTrue(names(commands).contains("ACCELERATE"));
            assertNotNull(pilot.activeIntent());
            when(engine.getPlayerShip()).thenReturn(ship);
            assertNull(pilot.activeIntent(), "Paused manual takeover must immediately hide the label");
            when(engine.getPlayerShip()).thenReturn(null);
            when(engine.getTotalElapsedTime(false)).thenReturn(11f);
            assertNull(pilot.activeIntent(), "Expired intent must immediately lose its label");
        }
    }
    @Test void sessionInstallsRealPilotAndRestoresOriginalWithoutTouchingOrders() {
        List<Ship.Oo> commands = new ArrayList<>(); Ship ship = ship(commands), target = ship(new ArrayList<>());
        when(ship.isAlive()).thenReturn(true); when(ship.getSystem()).thenReturn(null);
        when(ship.getShield()).thenReturn(null); when(ship.getPhaseCloak()).thenReturn(null);
        var engine = mock(com.fs.starfarer.api.combat.CombatEngineAPI.class, RETURNS_DEEP_STUBS);
        var tasks = engine.getFleetManager(0).getTaskManager(false);
        when(tasks.getAssignmentFor(ship)).thenReturn(null);
        try (var singleton = mockStatic(com.fs.starfarer.combat.CombatEngine.class)) {
            singleton.when(com.fs.starfarer.combat.CombatEngine::getInstance).thenReturn(mock(com.fs.starfarer.combat.CombatEngine.class, RETURNS_DEEP_STUBS));
            var original = new com.fs.starfarer.combat.ai.BasicShipAI(ship);
            var current = new java.util.concurrent.atomic.AtomicReference<com.fs.starfarer.api.combat.ShipAIPlugin>(original);
            when(ship.getShipAI()).thenAnswer(call -> current.get());
            doAnswer(call -> { current.set(call.getArgument(0)); return null; }).when(ship).setShipAI(any());
            Options coordinate = new Options(true, true, true, true, true, true, false, "");
            var session = new omegaai3.runtime.PilotSession(engine);
            session.reconcile(coordinate, true);
            assertEquals("", session.blockReason(ship, coordinate));
            var assignment = mock(com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo.class);
            when(assignment.getType()).thenReturn(com.fs.starfarer.api.combat.CombatAssignmentType.CAPTURE);
            when(tasks.getAssignmentFor(ship)).thenReturn(assignment);
            session.publish(ship, intent(new Vec(80, 0), 0), target, coordinate);
            assertSame(original, current.get(), "Specific commands take precedence");
            when(assignment.getType()).thenReturn(com.fs.starfarer.api.combat.CombatAssignmentType.SEARCH_AND_DESTROY);
            session.publish(ship, intent(new Vec(80, 0), 0), target, coordinate);
            assertInstanceOf(omegaai3.runtime.OmegaShipAI.class, current.get());
            session.reconcile(Options.defaults(), true);
            assertSame(original, current.get());
            verify(tasks, never()).createAssignment(any(), any(), anyBoolean());
            verify(tasks, never()).removeAssignment(any());
            session.reconcile(coordinate, true);
            session.publish(ship, intent(new Vec(80, 0), 0), target, coordinate);
            var foreign = mock(com.fs.starfarer.api.combat.ShipAIPlugin.class);
            current.set(foreign);
            session.restoreAll();
            assertSame(foreign, current.get(), "Never overwrite another mod's replacement pilot");
        }
    }
}
