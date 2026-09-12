package omegaai3;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.combat.ai.AI;
import com.fs.starfarer.combat.ai.BasicShipAI;
import com.fs.starfarer.combat.entities.Ship;
import lunalib.lunaSettings.LunaSettings;
import omegaai3.runtime.*;
import omegaai3.model.Vec;
import omegaai3.tactics.TacticalIntent;
import data.scripts.plugins.AISystems.*;
import data.scripts.plugins.RTSAssist;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Loads installed AI Tweaks through its own bytecode transformer. No copied third-party classes. */
class AiTweaksInteropTest {
    @BeforeAll static void settings() throws Exception { Rc8HelmTest.nativeSettings(); }
    @Test void realExtendedPilotRunsAndIsPreservedDuringOmegaMovementControl() throws Exception {
        Path launcher;
        try (var mods = Files.list(Path.of(System.getProperty("omega.test.mods")))) {
            launcher = mods.filter(p -> p.getFileName().toString().startsWith("AI Tweaks"))
                    .map(p -> p.resolve("jars/aitweaks-launcher.jar")).filter(Files::isRegularFile).findFirst().orElseThrow();
        }
        var settings = mock(SettingsAPI.class, RETURNS_DEEP_STUBS);
        when(settings.getScriptClassLoader()).thenReturn(getClass().getClassLoader());
        var engine = mock(CombatEngineAPI.class, RETURNS_DEEP_STUBS);
        when(engine.getPlayerShip()).thenReturn(null); when(engine.isAwareOf(anyInt(), any())).thenReturn(true);
        List<Ship.Oo> commands = new ArrayList<>(); Ship ship = Rc8HelmTest.ship(commands);
        when(ship.getSystem()).thenReturn(null); when(ship.getPhaseCloak()).thenReturn(null); when(ship.getShield()).thenReturn(null);
        when(ship.getFluxTracker().getMaxFlux()).thenReturn(10000f);
        when(ship.getHullSize()).thenReturn(ShipAPI.HullSize.DESTROYER);
        when(ship.getCustomData()).thenReturn(new HashMap<>());
        var current = new AtomicReference<ShipAIPlugin>();
        when(ship.getShipAI()).thenAnswer(call -> current.get()); when(ship.getAI()).thenAnswer(call -> current.get());
        doAnswer(call -> { ShipAIPlugin ai = call.getArgument(0); current.set(ai instanceof AI ? ai : new Ship.ShipAIWrapper(ai)); return null; }).when(ship).setShipAI(any());
        try (var global = mockStatic(Global.class, CALLS_REAL_METHODS);
             var nativeEngine = mockStatic(com.fs.starfarer.combat.CombatEngine.class);
             var luna = mockStatic(LunaSettings.class, call -> {
                 Class<?> type = call.getMethod().getReturnType();
                 if (type == Boolean.class || type == boolean.class) return false;
                 if (type == Integer.class || type == int.class) return 0;
                 if (type == Double.class || type == double.class) return 1d;
                 if (type == Float.class || type == float.class) return 1f;
                 if (type == String.class) return "Steady";
                 return null;
             });
             var launcherLoader = new URLClassLoader(new URL[]{launcher.toUri().toURL()}, getClass().getClassLoader()) {
                 @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
                     if (name.startsWith("com.genir.aitweaks.launcher.")) {
                         Class<?> found = findLoadedClass(name); return found == null ? findClass(name) : found;
                     }
                     return super.loadClass(name);
                 }
             }) {
            global.when(Global::getSettings).thenReturn(settings); global.when(Global::getCombatEngine).thenReturn(engine);
            global.when(Global::getCurrentState).thenReturn(GameState.COMBAT);
            nativeEngine.when(com.fs.starfarer.combat.CombatEngine::getInstance).thenReturn(mock(com.fs.starfarer.combat.CombatEngine.class, RETURNS_DEEP_STUBS));
            try (var core = (URLClassLoader) launcherLoader.loadClass("com.genir.aitweaks.launcher.loading.CoreLoader").getConstructor().newInstance()) {
                Class<?> type = core.loadClass("com.genir.aitweaks.core.shipai.ExtendedShipAI");
                ShipAIPlugin extended = (ShipAIPlugin) type.getConstructor(ShipAPI.class, ShipAIConfig.class).newInstance(ship, new ShipAIConfig());
                assertInstanceOf(BasicShipAI.class, extended);
                assertTrue(PilotAccess.supportedDelegate(extended));
                var omega = new OmegaShipAI(ship, extended, engine, () -> true);
                ship.setShipAI(omega);
                Ship target = Rc8HelmTest.ship(new ArrayList<>()); when(target.isAlive()).thenReturn(true);
                when(target.getLocation()).thenReturn(new org.lwjgl.util.vector.Vector2f(2000, 0));
                omega.publish(new TacticalIntent(TacticalIntent.Action.ADVANCE, "target", new Vec(80, 0), 0, 10, "test", 10), target);
                omega.advance(.016f);
                assertNotNull(omega.activeIntent()); assertSame(omega, ship.getShipAI());
                assertSame(extended.getConfig(), omega.getConfig()); assertSame(extended.getAIFlags(), omega.getAIFlags());
                assertTrue(commands.stream().anyMatch(c -> c.\u00d200000.name().equals("ACCELERATE")));
                omega.clear(); ship.setShipAI(extended); extended.advance(.016f);
                assertSame(extended, PilotAccess.unwrap(ship.getShipAI()));
                // Use both installed mods at once, including RTS's real post-advance event ordering.
                Map<String, Object> state = new HashMap<>(); state.put(RTSAssist.stNames.engine, engine);
                RTS_AIInjector rts = new RTS_AIInjector(state); rts.hookAI(ship);
                for (int n = 0; n < 5; n++) rts.update();
                ShipAIPlugin outerRts = ship.getShipAI(); assertTrue(PilotAccess.isRts(outerRts));
                var combined = new OmegaShipAI(ship, outerRts, engine, () -> true, (BasicShipAI) extended);
                ship.setShipAI(combined); commands.clear();
                combined.publish(new TacticalIntent(TacticalIntent.Action.RETREAT, "target", new Vec(-80, 0), 0, 10, "test", 2), target);
                combined.advance(.016f);
                assertSame(target, ((BasicShipAI) extended).getTargetOverride());
                assertNotNull(combined.activeIntent());
                String hook = rts.registerInjection(ship, RTS_AIInjector.eventType.POSTADVANCE, new RTS_AIInjectedEvent() {
                    @Override public void run() { ship.getBlockedCommands().add(Ship.oo.valueOf("ACCELERATE_BACKWARDS")); }
                    @Override public boolean removeOnCompletion() { return false; }
                });
                commands.clear(); combined.advance(.016f);
                assertNull(combined.activeIntent()); assertNull(((BasicShipAI) extended).getTargetOverride());
                rts.removeInjection(ship, hook, RTS_AIInjector.eventType.POSTADVANCE); ship.getBlockedCommands().clear();
                combined.advance(.016f); assertNotNull(combined.activeIntent());
                combined.clear(); ship.setShipAI(outerRts); outerRts.advance(.016f);
                assertSame(extended.getAIFlags(), ship.getShipAI().getAIFlags());
            }
        }
    }
}
