package com.jvn.gracebound;

import com.mojang.logging.LogUtils;
import com.jvn.gracebound.config.GraceboundConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(Gracebound.MOD_ID)
public class Gracebound {
    public static final String MOD_ID = "gracebound";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Gracebound(IEventBus modEventBus, ModContainer modContainer) {
        GraceboundConfig.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.CLIENT, GraceboundConfig.SPEC);
        LOGGER.info("Gracebound is ready to guide.");
    }
}
