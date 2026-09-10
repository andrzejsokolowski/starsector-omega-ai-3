package omegaai3;

import com.fs.starfarer.api.BaseModPlugin;

public final class OmegaModPlugin extends BaseModPlugin {
    @Override public void onApplicationLoad() { OmegaSettings.init(); }
}
