package com.jvn.gracebound.client;

import com.jvn.gracebound.Gracebound;
import com.jvn.gracebound.config.GraceboundConfig;
import com.jvn.gracebound.guidance.GuidanceMode;
import com.jvn.gracebound.guidance.RuntimeGuidanceState;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class GraceboundClientEvents {
    private static final String CATEGORY = "key.categories.gracebound";

    private static final KeyMapping CYCLE_MODE = new KeyMapping(
            "key.gracebound.cycle_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    private GraceboundClientEvents() {
    }

    @SuppressWarnings("removal")
    @EventBusSubscriber(modid = Gracebound.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBusEvents {
        private ModBusEvents() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(CYCLE_MODE);
        }
    }

    @EventBusSubscriber(modid = Gracebound.MOD_ID, value = Dist.CLIENT)
    public static final class GameBusEvents {
        private GameBusEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            while (CYCLE_MODE.consumeClick()) {
                GuidanceMode mode = RuntimeGuidanceState.cycleMode();
                Minecraft minecraft = Minecraft.getInstance();
                if (GraceboundConfig.showMessages && minecraft.player != null) {
                    minecraft.player.displayClientMessage(mode.displayMessage(), true);
                }
            }
        }
    }
}
