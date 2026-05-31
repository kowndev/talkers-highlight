package net.kown.talkershighligth;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.kown.talkershighligth.config.TracerConfigScreen;

/**
 * Provides a "Config" button in Mod Menu.
 *
 * <p>This class is only loaded when Mod Menu is present (Fabric's entrypoint
 * system won't invoke the {@code modmenu} entrypoint group otherwise).
 */
public final class ModMenuApiImpl implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return TracerConfigScreen::createScreen;
    }
}