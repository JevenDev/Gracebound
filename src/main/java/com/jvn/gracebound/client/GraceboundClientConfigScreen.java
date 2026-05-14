package com.jvn.gracebound.client;

import com.jvn.toucanlib.neoforge.config.ToucanConfigScreens;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public final class GraceboundClientConfigScreen {
    private GraceboundClientConfigScreen() {
    }

    public static void register(ModContainer modContainer) {
        ToucanConfigScreens.register(modContainer, (IConfigScreenFactory) (container, parent) -> new ConfigurationScreen(container, parent));
    }
}
