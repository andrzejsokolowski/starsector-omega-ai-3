package omegaai3;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;
import org.json.JSONObject;
import org.apache.log4j.Logger;

public final class OmegaSettings implements LunaSettingsListener {
    public static final String MOD_ID = "omega_ai3";
    private static final Logger LOG = Logger.getLogger(OmegaSettings.class);
    private static volatile Options current = Options.defaults();
    public static Options current() { return current; }
    public static void init() {
        reload();
        if (!LunaSettings.hasSettingsListenerOfClass(OmegaSettings.class)) LunaSettings.addSettingsListener(new OmegaSettings());
    }
    @Override public void settingsChanged(String id) { if (MOD_ID.equals(id)) reload(); }
    private static void reload() {
        try {
            JSONObject defaults = Global.getSettings().getJSONObject("omega_ai3");
            // Ignore the removed mode setting, including saved Observe selections from earlier builds.
            boolean enabled = value("omega3_enabled", defaults.optBoolean("enabled", true));
            String conflict = "";
            var mods = Global.getSettings().getModManager();
            if (mods.isModEnabled("omega_ai")) conflict = "Earlier Omega AI is enabled";
            current = new Options(enabled, enabled,
                    value("omega3_player", defaults.optBoolean("playerFleet", true)), value("omega3_enemy", defaults.optBoolean("enemyFleet", true)),
                    value("omega3_simulator", defaults.optBoolean("simulator", true)), value("omega3_status", defaults.optBoolean("showStatus", true)),
                    value("omega3_log", defaults.optBoolean("logDecisions", false)), conflict,
                    value("omega3_labels", defaults.optBoolean("decisionLabels", true)));
        } catch (Exception e) {
            current = new Options(false, false, true, true, true, true, false, "Settings unavailable");
            LOG.warn("Omega AI settings could not be read; control disabled.", e);
        }
    }
    private static boolean value(String key, boolean fallback) {
        Boolean value = LunaSettings.getBoolean(MOD_ID, key);
        return value == null ? fallback : value;
    }
}
