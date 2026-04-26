package com.jvn.gracebound;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Gracebound.MOD_ID)
public class Gracebound {
    public static final String MOD_ID = "gracebound";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Gracebound(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Gracebound is ready to guide.");
    }
}
