package com.jvn.gracebound.client;

import com.jvn.gracebound.client.compat.antiqueatlas.AntiqueAtlasCompatBridge;
import com.jvn.gracebound.Gracebound;
import com.jvn.gracebound.client.compat.xaero.XaeroCompatBridge;
import com.jvn.gracebound.config.GraceboundConfig;
import com.jvn.gracebound.guidance.GuidanceMode;
import com.jvn.gracebound.guidance.GuidanceRenderState;
import com.jvn.gracebound.guidance.GuidanceTargetResolver;
import com.jvn.gracebound.guidance.RuntimeGuidanceState;
import com.jvn.gracebound.guidance.TargetResolution;
import com.jvn.gracebound.network.GraceboundConnectionState;
import com.jvn.gracebound.network.GraceboundNetwork;
import com.jvn.gracebound.settings.GraceboundSettingsView;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Optional;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

public final class GraceboundClientEvents {
    private static final String CATEGORY = "key.categories.gracebound";
    private static boolean inWorldLastTick;

    private static final KeyMapping CYCLE_MODE = new KeyMapping(
            "key.gracebound.cycle_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
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
            Minecraft minecraft = Minecraft.getInstance();
            while (CYCLE_MODE.consumeClick()) {
                boolean visible = GuidanceRenderState.toggleLocalVisible();
                if (GraceboundConnectionState.serverHasGracebound()) {
                    GraceboundNetwork.syncLocalVisibilityToServer();
                }
                if (GraceboundConfig.showMessages && minecraft.player != null) {
                    minecraft.player.displayClientMessage(visible
                            ? net.minecraft.network.chat.Component.translatable("message.gracebound.visibility.enabled")
                            : net.minecraft.network.chat.Component.translatable("message.gracebound.visibility.disabled"), true);
                }
            }

            if (minecraft.player != null) {
                if (!inWorldLastTick && minecraft.level != null) {
                    RuntimeGuidanceState.resetToDefaultMode(GraceboundSettingsView.defaultGuidanceMode());
                    if (GraceboundConnectionState.serverHasGracebound()) {
                        GraceboundNetwork.syncLocalVisibilityToServer();
                    }
                }
                inWorldLastTick = true;
                if (minecraft.level != null) {
                    GuidanceMode defaultMode = GraceboundSettingsView.defaultGuidanceMode();
                    if (RuntimeGuidanceState.mode() != defaultMode) {
                        RuntimeGuidanceState.resetToDefaultMode(defaultMode);
                    }
                }

                RuntimeGuidanceState.clientTick(minecraft.player, minecraft.player.getLastDeathLocation());
                if (GraceboundConnectionState.serverHasGracebound() && RuntimeGuidanceState.consumeWipeDeathLocationRequest()) {
                    GraceboundNetwork.requestWipeDeathLocationOnArrival();
                }
                TargetResolution resolution = GuidanceRenderState.isLocalVisible()
                        ? GuidanceTargetResolver.resolve(minecraft.player)
                        : TargetResolution.none();
                GraceboundClientMessages.updateCrossDimension(resolution);
                GraceboundGuidanceVisuals.tick(minecraft.player, resolution.target());
                AntiqueAtlasCompatBridge.clientTick(minecraft.player, resolution.target());
                XaeroCompatBridge.clientTick(minecraft.player, resolution.target());

                ClientLevel level = minecraft.level;
                if (level != null && GraceboundSettingsView.showOthersGuidanceEnabled()) {
                    for (Player player : level.players()) {
                        if (player == minecraft.player) {
                            continue;
                        }
                        if (!GuidanceRenderState.isRemoteVisible(player.getUUID())) {
                            GraceboundGuidanceVisuals.tick(player, Optional.empty());
                            continue;
                        }

                        TargetResolution otherResolution = GuidanceTargetResolver.resolveHeldOnly(player);
                        GraceboundGuidanceVisuals.tick(player, otherResolution.target());
                    }
                } else {
                    GraceboundGuidanceVisuals.clearOtherPlayers(minecraft.player);
                }
            } else {
                inWorldLastTick = false;
                GraceboundClientMessages.clearState();
                GraceboundGuidanceVisuals.clearAll();
                AntiqueAtlasCompatBridge.clear();
                XaeroCompatBridge.clear();
                GuidanceRenderState.resetClientState();
                RuntimeGuidanceState.clearClientSession();
            }
        }

        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            GraceboundGuidanceVisuals.renderWorld(event);
        }
    }
}
