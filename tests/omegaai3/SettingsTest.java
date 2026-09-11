package omegaai3;

import com.fs.starfarer.api.*;
import lunalib.lunaSettings.LunaSettings;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @Test void oneSwitchDefaultsOnAndSavedObserveModeCannotDisableIt() throws Exception {
        var current = OmegaSettings.class.getDeclaredField("current"); current.setAccessible(true);
        Object before = current.get(null);
        var settings = mock(SettingsAPI.class, RETURNS_DEEP_STUBS);
        when(settings.getJSONObject("omega_ai3")).thenReturn(new JSONObject("{\"enabled\":true}"));
        try (var global = mockStatic(Global.class, CALLS_REAL_METHODS); var luna = mockStatic(LunaSettings.class)) {
            global.when(Global::getSettings).thenReturn(settings);
            luna.when(() -> LunaSettings.getBoolean(anyString(), anyString())).thenReturn(null);
            luna.when(() -> LunaSettings.hasSettingsListenerOfClass(OmegaSettings.class)).thenReturn(true);
            luna.when(() -> LunaSettings.getString("omega_ai3", "omega3_mode")).thenReturn("Observe");
            OmegaSettings.init();
            assertTrue(OmegaSettings.current().enabled()); assertTrue(OmegaSettings.current().mayIssueOrders());
            luna.verify(() -> LunaSettings.getString("omega_ai3", "omega3_mode"), never());
            luna.when(() -> LunaSettings.getBoolean("omega_ai3", "omega3_enabled")).thenReturn(false);
            new OmegaSettings().settingsChanged("omega_ai3");
            assertFalse(OmegaSettings.current().enabled()); assertFalse(OmegaSettings.current().mayIssueOrders());
        } finally { current.set(null, before); }
    }
}
