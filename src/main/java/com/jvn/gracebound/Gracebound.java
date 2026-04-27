package com.jvn.gracebound;

import com.mojang.logging.LogUtils;
import com.jvn.gracebound.config.GraceboundConfig;
import com.jvn.gracebound.network.GraceboundNetwork;
import com.jvn.gracebound.world.GraceboundGameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(Gracebound.MOD_ID)
public class Gracebound {
    public static final String MOD_ID = "gracebound";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Gracebound(IEventBus modEventBus, ModContainer modContainer) {
        GraceboundGameRules.bootstrap();
        GraceboundConfig.register(modEventBus);
        GraceboundNetwork.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.CLIENT, GraceboundConfig.SPEC);
        if (FMLEnvironment.dist.isClient()) {
            registerClientConfigScreen(modContainer);
        }
        LOGGER.info("Gracebound is ready to guide.");
    }

    private static void registerClientConfigScreen(ModContainer modContainer) {
        try {
            Class<?> hookClass = Class.forName("com.jvn.gracebound.client.GraceboundClientConfigScreen");
            hookClass.getMethod("register", ModContainer.class).invoke(null, modContainer);
        } catch (ReflectiveOperationException exception) {
            LOGGER.error("Failed to register Gracebound config screen hook.", exception);
        }
    }
}
