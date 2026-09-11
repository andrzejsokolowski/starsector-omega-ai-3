package omegaai3;

import omegaai3.diagnostics.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ControlStatusTest {
    @Test void zeroControlExplainsConflictsInsteadOfClaimingAnAction() {
        String message = ControlStatus.fleet("Player", true, 0, Map.of("RTSAssist controls ship AI", 4, "Manual player control", 1));
        assertTrue(message.contains("RTSAssist")); assertTrue(message.contains("Manual player control"));
        assertFalse(message.contains("under Omega control"));
        assertEquals("Player: 2 under Omega control", ControlStatus.fleet("Player", true, 2, Map.of()));
    }
    @Test void hudRendersConflictTextEvenWithoutAnyShipLabelsOrFlagship() throws Exception {
        var engine = mock(com.fs.starfarer.api.combat.CombatEngineAPI.class);
        var viewport = mock(com.fs.starfarer.api.combat.ViewportAPI.class);
        var settings = mock(com.fs.starfarer.api.SettingsAPI.class);
        when(engine.isUIShowingHUD()).thenReturn(true); when(settings.getScreenHeight()).thenReturn(1080f);
        var font = mock(org.lazywizard.lazylib.ui.LazyFont.class);
        var line = mock(org.lazywizard.lazylib.ui.LazyFont.DrawableString.class);
        when(line.getText()).thenReturn(""); when(font.createText(anyString(), any(), anyFloat())).thenReturn(line);
        try (var global = mockStatic(com.fs.starfarer.api.Global.class); var fonts = mockStatic(org.lazywizard.lazylib.ui.LazyFont.class)) {
            global.when(com.fs.starfarer.api.Global::getSettings).thenReturn(settings);
            fonts.when(() -> org.lazywizard.lazylib.ui.LazyFont.loadFont("graphics/fonts/victor10.fnt")).thenReturn(font);
            new DecisionOverlay().render(engine, viewport, List.of(), false, e -> false, List.of("RTSAssist controls ship AI"));
            verify(line).setText("RTSAssist controls ship AI"); verify(line).draw(24, 990);
        }
    }
}
