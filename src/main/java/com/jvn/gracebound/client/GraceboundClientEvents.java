package com.jvn.gracebound.client;

import com.jvn.gracebound.client.compat.antiqueatlas.AntiqueAtlasCompatBridge;
import com.jvn.gracebound.Gracebound;
import com.jvn.gracebound.client.compat.xaero.XaeroCompatBridge;
import com.jvn.gracebound.config.GraceboundConfig;
import com.jvn.gracebound.config.GraceboundConfig.TrailStyle;
import com.jvn.gracebound.guidance.GuidanceMode;
import com.jvn.gracebound.guidance.GuidanceRenderState;
import com.jvn.gracebound.guidance.GuidanceTargetResolver;
import com.jvn.gracebound.guidance.RuntimeGuidanceState;
import com.jvn.gracebound.guidance.TargetResolution;
import com.jvn.gracebound.network.GraceboundConnectionState;
import com.jvn.gracebound.network.GraceboundNetwork;
import com.jvn.gracebound.network.GraceboundServerRuntimeSettings;
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
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

public final class GraceboundClientEvents {
    private static final String CATEGORY = "key.categories.gracebound";
    private static final int FALLBACK_MODE_LOG_DELAY_TICKS = 100;
    private static boolean inWorldLastTick;
    private static int ticksInSession;
    private static boolean modeLoggedForSession;
    private static TrailStyle lastSyncedTrailStyle = TrailStyle.CLASSIC;

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

        @SubscribeEvent
        public static void registerShaders(RegisterShadersEvent event) {
            GraceboundGuidanceVisuals.registerShaders(event);
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
                    lastSyncedTrailStyle = GraceboundConfig.trailStyle;
                }
                if (GraceboundConfig.showMessages && minecraft.player != null) {
                    minecraft.player.displayClientMessage(visible
                            ? net.minecraft.network.chat.Component.translatable("message.gracebound.visibility.enabled")
                            : net.minecraft.network.chat.Component.translatable("message.gracebound.visibility.disabled"), true);
                }
            }

            if (minecraft.player != null) {
                if (!inWorldLastTick && minecraft.level != null) {
                    ticksInSession = 0;
                    modeLoggedForSession = false;
                    RuntimeGuidanceState.resetToDefaultMode(GraceboundSettingsView.defaultGuidanceMode());
                    if (GraceboundConnectionState.serverHasGracebound()) {
                        GraceboundNetwork.syncLocalVisibilityToServer();
                        lastSyncedTrailStyle = GraceboundConfig.trailStyle;
                    }
                }
                inWorldLastTick = true;
                if (!modeLoggedForSession) {
                    if (GraceboundConnectionState.serverHasGracebound()) {
                        Gracebound.LOGGER.info("Gracebound server support detected; enhanced mode enabled.");
                        modeLoggedForSession = true;
                    } else if (ticksInSession >= FALLBACK_MODE_LOG_DELAY_TICKS) {
                        Gracebound.LOGGER.info("Gracebound running in client-only fallback mode.");
                        modeLoggedForSession = true;
                    }
                }
                ticksInSession++;
                if (minecraft.level != null) {
                    GuidanceMode defaultMode = GraceboundSettingsView.defaultGuidanceMode();
                    if (RuntimeGuidanceState.mode() != defaultMode) {
                        RuntimeGuidanceState.resetToDefaultMode(defaultMode);
                    }
                    if (GraceboundConnectionState.serverHasGracebound() && GraceboundConfig.trailStyle != lastSyncedTrailStyle) {
                        GraceboundNetwork.syncLocalVisibilityToServer();
                        lastSyncedTrailStyle = GraceboundConfig.trailStyle;
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
                GraceboundConnectionState.clear();
                GraceboundServerRuntimeSettings.clear();
                ticksInSession = 0;
                modeLoggedForSession = false;
                lastSyncedTrailStyle = TrailStyle.CLASSIC;
            }
        }

        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            GraceboundGuidanceVisuals.renderWorld(event);
        }
    }
}
