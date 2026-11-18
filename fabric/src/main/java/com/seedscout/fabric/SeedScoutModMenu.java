package com.seedscout.fabric;

import com.seedscout.gui.SeedScoutConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class SeedScoutModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SeedScoutConfigScreen::new;
    }
}
