package omegaai3;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.combat.ai.AI;
import com.fs.starfarer.combat.ai.BasicShipAI;
import com.fs.starfarer.combat.entities.Ship;
import data.scripts.plugins.AISystems.*;
import data.scripts.plugins.RTSAssist;
import omegaai3.runtime.*;
import omegaai3.model.Vec;
import omegaai3.tactics.TacticalIntent;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class InteropTest {
    @BeforeAll static void initializeNativeSettings() throws Exception { Rc8HelmTest.nativeSettings(); }
    static final class Harness {
        final List<Ship.Oo> commands = new ArrayList<>();
        final Ship ship = Rc8HelmTest.ship(commands);
        final ShipAPI target = mock(ShipAPI.class);
        final CombatEngineAPI engine = mock(CombatEngineAPI.class, RETURNS_DEEP_STUBS);
        final AtomicReference<ShipAIPlugin> current = new AtomicReference<>();
        final BasicShipAI base = mock(BasicShipAI.class);
        final RTS_AIInjector rts;
        final ShipAIPlugin rtsOuter;
        final OmegaShipAI omega;
        Harness() {
            when(ship.getId()).thenReturn("ship"); when(ship.isAlive()).thenReturn(true);
            when(ship.getSystem()).thenReturn(null); when(ship.getPhaseCloak()).thenReturn(null);
            when(ship.getCustomData()).thenReturn(new HashMap<>());
            when(target.isAlive()).thenReturn(true); when(target.getLocation()).thenReturn(new org.lwjgl.util.vector.Vector2f(5000, 0));
            when(engine.isAwareOf(anyInt(), any())).thenReturn(true); when(engine.getShips()).thenReturn(List.of(ship));
            when(engine.getPlayerShip()).thenReturn(null);
            when(base.getConfig()).thenReturn(new ShipAIConfig()); when(base.getAIFlags()).thenReturn(new ShipwideAIFlags());
            when(ship.getShipAI()).thenAnswer(call -> current.get()); when(ship.getAI()).thenAnswer(call -> current.get());
            doAnswer(call -> { ShipAIPlugin ai = call.getArgument(0); current.set(ai instanceof AI ? ai : new Ship.ShipAIWrapper(ai)); return null; }).when(ship).setShipAI(any());
            doAnswer(call -> { commands.add(Rc8HelmTest.command(((ShipCommand) call.getArgument(0)).name())); return null; })
                    .when(ship).giveCommand(any(ShipCommand.class), any(), anyInt());
            doAnswer(call -> { ship.getBlockedCommands().add(Ship.oo.valueOf(((ShipCommand) call.getArgument(0)).name())); return null; })
                    .when(ship).blockCommandForOneFrame(any(ShipCommand.class));
            doAnswer(call -> {
                assertSame(base, PilotAccess.unwrap(ship.getShipAI()), "Delegate must run under its own installed identity");
                commands.add(Rc8HelmTest.command("FIRE")); commands.add(Rc8HelmTest.command("ACCELERATE"));
                return null;
            }).when(base).advance(anyFloat());
            ship.setShipAI(base);
            Map<String, Object> state = new HashMap<>(); state.put(RTSAssist.stNames.engine, engine);
            rts = new RTS_AIInjector(state); rts.hookAI(ship);
            for (int i = 0; i < 5; i++) rts.update();
            rtsOuter = ship.getShipAI(); assertTrue(PilotAccess.isRts(rtsOuter));
            omega = new OmegaShipAI(ship, rtsOuter, engine, () -> true);
            ship.setShipAI(omega);
        }
        void frame() {
            commands.clear(); ship.getBlockedCommands().clear();
            rts.update(); omega.keepAlive();
            omega.publish(new TacticalIntent(TacticalIntent.Action.RETREAT, "enemy", new Vec(-80, 0), 0, 100, "test", 2), target);
            omega.advance(1f / 60);
        }
        Set<String> names() {
            Set<String> names = new HashSet<>(); for (Ship.Oo c : commands) names.add(c.\u00d200000.name()); return names;
        }
    }
    @Test void realRtsWrapperRemainsInstalledAsDelegateWithoutRecursionOrRepeatedRehooking() {
        Harness h = new Harness();
        for (int frame = 0; frame < 180; frame++) h.frame();
        assertSame(h.omega, h.ship.getShipAI()); assertNotNull(h.omega.activeIntent());
        assertTrue(h.names().contains("FIRE")); assertTrue(h.names().contains("ACCELERATE_BACKWARDS"));
        assertFalse(h.names().contains("ACCELERATE")); verify(h.base, times(180)).advance(anyFloat());
        h.omega.clear(); h.ship.setShipAI(h.rtsOuter); h.commands.clear(); h.ship.getShipAI().advance(.016f);
        assertTrue(h.names().contains("ACCELERATE")); assertFalse(h.names().contains("ACCELERATE_BACKWARDS"));
        verify(h.base, times(181)).advance(anyFloat());
    }
    @Test void realRtsPostAdvanceCommandsTakePriorityAndAutonomyReturnsWhenRemoved() {
        Harness h = new Harness();
        String hook = h.rts.registerInjection(h.ship, RTS_AIInjector.eventType.POSTADVANCE, new RTS_AIInjectedEvent() {
            @Override public void run() {
                h.ship.giveCommand(ShipCommand.ACCELERATE, null, 0);
                h.ship.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS);
                h.ship.blockCommandForOneFrame(ShipCommand.STRAFE_LEFT);
                h.ship.blockCommandForOneFrame(ShipCommand.STRAFE_RIGHT);
                h.ship.blockCommandForOneFrame(ShipCommand.TURN_LEFT);
                h.ship.blockCommandForOneFrame(ShipCommand.TURN_RIGHT);
            }
            @Override public boolean removeOnCompletion() { return false; }
        });
        for (int frame = 0; frame < 60; frame++) {
            h.frame(); assertNull(h.omega.activeIntent()); assertTrue(h.names().contains("ACCELERATE"));
            assertFalse(h.names().contains("ACCELERATE_BACKWARDS")); assertTrue(h.names().contains("FIRE"));
        }
        h.rts.removeInjection(h.ship, hook, RTS_AIInjector.eventType.POSTADVANCE);
        h.frame(); assertNotNull(h.omega.activeIntent()); assertTrue(h.names().contains("ACCELERATE_BACKWARDS"));
    }
    @Test void longManualTakeoverDoesNotCreateARtsWrapperCycleOnReturnToAutopilot() {
        Harness h = new Harness(); h.frame(); when(h.engine.getPlayerShip()).thenReturn(h.ship);
        when(h.engine.isUIAutopilotOn()).thenReturn(false);
        for (int frame = 0; frame < 90; frame++) {
            h.commands.clear(); h.rts.update(); h.omega.keepAlive(); h.omega.advance(.016f);
            assertNull(h.omega.activeIntent()); assertTrue(h.commands.isEmpty());
        }
        when(h.engine.isUIAutopilotOn()).thenReturn(true);
        h.frame(); assertNotNull(h.omega.activeIntent()); verify(h.base, times(2)).advance(anyFloat());
    }
    @Test void rtsTargetCommandImmediatelyHidesTheOmegaActionAndYields() {
        Harness h = new Harness(); h.frame();
        h.omega.setTargetOverride(h.target); assertNull(h.omega.activeIntent());
        h.frame(); assertTrue(h.names().contains("ACCELERATE")); assertFalse(h.names().contains("ACCELERATE_BACKWARDS"));
        when(h.engine.getTotalElapsedTime(false)).thenReturn(.1f);
        h.frame(); assertNotNull(h.omega.activeIntent());
    }
    @Test void sessionKeepsTheNativePilotObservedBeforeRtsWrappedIt() {
        Harness h = new Harness();
        when(h.engine.getFleetManager(0).getTaskManager(false).getAssignmentFor(h.ship)).thenReturn(null);
        Options on = new Options(true, true, true, true, true, true, false, "");
        try (var options = mockStatic(OmegaSettings.class)) {
            options.when(OmegaSettings::current).thenReturn(on);
            PilotSession session = new PilotSession(h.engine);
            h.ship.setShipAI(h.base); session.reconcile(on, true);
            h.ship.setShipAI(h.rtsOuter); session.reconcile(on, true);
            session.publish(h.ship, new TacticalIntent(TacticalIntent.Action.ADVANCE, "enemy", new Vec(80, 0), 0, 10, "test", 10), h.target, on);
            var installed = assertInstanceOf(OmegaShipAI.class, h.ship.getShipAI());
            installed.advance(.016f);
            assertNotNull(session.active(h.ship)); verify(h.base).setTargetOverride(h.target);
            session.restoreAll(); assertSame(h.rtsOuter, h.ship.getShipAI());
        }
    }
    @Test void rtsAttachingAfterOmegaDoesNotAdvanceTheOriginalPilotTwice() {
        Harness h = new Harness(); h.ship.setShipAI(h.base);
        when(h.engine.getFleetManager(0).getTaskManager(false).getAssignmentFor(h.ship)).thenReturn(null);
        Options on = new Options(true, true, true, true, true, true, false, "");
        var intent = new TacticalIntent(TacticalIntent.Action.RETREAT, "enemy", new Vec(-80, 0), 0, 10, "test", 2);
        try (var options = mockStatic(OmegaSettings.class)) {
            options.when(OmegaSettings::current).thenReturn(on);
            PilotSession session = new PilotSession(h.engine); session.reconcile(on, true);
            session.publish(h.ship, intent, h.target, on);
            Map<String, Object> state = new HashMap<>(); state.put(RTSAssist.stNames.engine, h.engine);
            RTS_AIInjector lateRts = new RTS_AIInjector(state); lateRts.hookAI(h.ship);
            for (int i = 0; i < 5; i++) lateRts.update();
            assertTrue(PilotAccess.isRts(h.ship.getShipAI()));
            for (int i = 0; i < 60; i++) {
                h.commands.clear(); h.ship.getBlockedCommands().clear();
                lateRts.update(); session.reconcile(on, true); session.publish(h.ship, intent, h.target, on);
                h.ship.getShipAI().advance(.016f);
                assertNotNull(session.active(h.ship));
            }
            verify(h.base, times(60)).advance(anyFloat());
            session.restoreAll(); h.ship.getShipAI().advance(.016f);
            verify(h.base, times(61)).advance(anyFloat()); assertNull(session.active(h.ship));
        }
    }
}
