package com.jvn.gracebound.network;

import com.jvn.gracebound.Gracebound;
import com.jvn.gracebound.config.GraceboundConfig;
import com.jvn.gracebound.config.GraceboundConfig.TrailStyle;
import com.jvn.gracebound.guidance.GuidanceRenderState;
import com.jvn.gracebound.guidance.RuntimeGuidanceState;
import com.jvn.gracebound.world.GraceboundGameRules;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class GraceboundNetwork {
    private static final String PROTOCOL_VERSION = "2";
    private static final double ARRIVAL_RADIUS_BLOCKS = 2.5D;
    private static final double ARRIVAL_RADIUS_SQUARED = ARRIVAL_RADIUS_BLOCKS * ARRIVAL_RADIUS_BLOCKS;
    private static final Map<UUID, Boolean> serverHiddenPlayers = new HashMap<>();
    private static final Map<UUID, TrailStyle> serverTrailStyles = new HashMap<>();

    private GraceboundNetwork() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(GraceboundNetwork::onRegisterPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(GraceboundNetwork::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(GraceboundNetwork::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(GraceboundNetwork::onStartTracking);
    }

    public static void syncLocalVisibilityToServer() {
        if (!GraceboundConnectionState.serverHasGracebound()) {
            return;
        }
        PacketDistributor.sendToServer(new SetGuidanceVisibilityC2SPayload(GuidanceRenderState.isLocalVisible(), encodeTrailStyle(GraceboundConfig.trailStyle)));
    }

    public static void requestWipeDeathLocationOnArrival() {
        if (!GraceboundConnectionState.serverHasGracebound()) {
            return;
        }
        PacketDistributor.sendToServer(new WipeDeathLocationOnArrivalC2SPayload());
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(SetGuidanceVisibilityC2SPayload.TYPE, SetGuidanceVisibilityC2SPayload.STREAM_CODEC, GraceboundNetwork::handleSetVisibilityC2S);
        registrar.playToServer(WipeDeathLocationOnArrivalC2SPayload.TYPE, WipeDeathLocationOnArrivalC2SPayload.STREAM_CODEC, GraceboundNetwork::handleWipeDeathLocationOnArrivalC2S);
        registrar.playToClient(ServerHelloS2CPayload.TYPE, ServerHelloS2CPayload.STREAM_CODEC, GraceboundNetwork::handleServerHelloS2C);
        registrar.playToClient(SetGuidanceVisibilityS2CPayload.TYPE, SetGuidanceVisibilityS2CPayload.STREAM_CODEC, GraceboundNetwork::handleSetVisibilityS2C);
    }

    private static void handleSetVisibilityC2S(SetGuidanceVisibilityC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        setServerVisible(serverPlayer.getUUID(), payload.visible());
        setServerTrailStyle(serverPlayer.getUUID(), decodeTrailStyle(payload.trailStyle()));
        PacketDistributor.sendToAllPlayers(new SetGuidanceVisibilityS2CPayload(serverPlayer.getUUID(), payload.visible(), payload.trailStyle()));
    }

    private static void handleSetVisibilityS2C(SetGuidanceVisibilityS2CPayload payload, IPayloadContext context) {
        GuidanceRenderState.setRemoteVisible(payload.playerId(), payload.visible());
        GuidanceRenderState.setRemoteTrailStyle(payload.playerId(), decodeTrailStyle(payload.trailStyle()));
        if (context.player().getUUID().equals(payload.playerId())) {
            GuidanceRenderState.setLocalVisible(payload.visible());
        }
    }

    private static void handleServerHelloS2C(ServerHelloS2CPayload payload, IPayloadContext context) {
        GraceboundConnectionState.setServerHasGracebound(true);
        GraceboundServerRuntimeSettings.updateFromServerSync(
                payload.showOthersGuidance(),
                payload.defaultGuidanceMode(),
                payload.showDeathGuidanceWithoutRecoveryCompass(),
                payload.wipeDeathLocationOnArrival()
        );
        RuntimeGuidanceState.resetToDefaultMode(GraceboundServerRuntimeSettings.defaultGuidanceMode());
        syncLocalVisibilityToServer();
    }

    private static void handleWipeDeathLocationOnArrivalC2S(WipeDeathLocationOnArrivalC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!GraceboundGameRules.wipeDeathLocationOnArrivalEnabled(serverPlayer.level())) {
            return;
        }

        Optional<GlobalPos> deathLocation = serverPlayer.getLastDeathLocation();
        if (deathLocation.isEmpty()) {
            return;
        }

        GlobalPos target = deathLocation.get();
        if (target.dimension() != serverPlayer.level().dimension()) {
            return;
        }

        Vec3 playerPos = serverPlayer.position();
        Vec3 targetPos = Vec3.atCenterOf(target.pos());
        double dx = playerPos.x - targetPos.x;
        double dz = playerPos.z - targetPos.z;
        if (dx * dx + dz * dz > ARRIVAL_RADIUS_SQUARED) {
            return;
        }

        serverPlayer.setLastDeathLocation(Optional.empty());
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        PacketDistributor.sendToPlayer(
                serverPlayer,
                new ServerHelloS2CPayload(
                        GraceboundGameRules.showOthersGuidanceEnabled(serverPlayer.level()),
                        GraceboundGameRules.encodeDefaultGuidanceMode(GraceboundGameRules.defaultGuidanceMode(serverPlayer.level())),
                        GraceboundGameRules.showDeathGuidanceWithoutRecoveryCompassEnabled(serverPlayer.level()),
                        GraceboundGameRules.wipeDeathLocationOnArrivalEnabled(serverPlayer.level())
                )
        );

        for (ServerPlayer onlinePlayer : serverPlayer.server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new SetGuidanceVisibilityS2CPayload(
                            onlinePlayer.getUUID(),
                            isServerVisible(onlinePlayer.getUUID()),
                            encodeTrailStyle(serverTrailStyle(onlinePlayer.getUUID()))
                    )
            );
        }
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer observer)) {
            return;
        }
        if (!(event.getTarget() instanceof ServerPlayer targetPlayer)) {
            return;
        }
        PacketDistributor.sendToPlayer(
                observer,
                new SetGuidanceVisibilityS2CPayload(
                        targetPlayer.getUUID(),
                        isServerVisible(targetPlayer.getUUID()),
                        encodeTrailStyle(serverTrailStyle(targetPlayer.getUUID()))
                )
        );
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        Boolean wasHidden = serverHiddenPlayers.remove(playerId);
        serverTrailStyles.remove(playerId);
        if (Boolean.TRUE.equals(wasHidden)) {
            PacketDistributor.sendToAllPlayers(new SetGuidanceVisibilityS2CPayload(playerId, true, encodeTrailStyle(TrailStyle.CLASSIC)));
        }
    }

    private static boolean isServerVisible(UUID playerId) {
        return !serverHiddenPlayers.getOrDefault(playerId, false);
    }

    private static void setServerVisible(UUID playerId, boolean visible) {
        if (visible) {
            serverHiddenPlayers.remove(playerId);
        } else {
            serverHiddenPlayers.put(playerId, true);
        }
    }

    private static TrailStyle serverTrailStyle(UUID playerId) {
        return serverTrailStyles.getOrDefault(playerId, TrailStyle.CLASSIC);
    }

    private static void setServerTrailStyle(UUID playerId, TrailStyle trailStyle) {
        if (trailStyle == TrailStyle.CLASSIC) {
            serverTrailStyles.remove(playerId);
        } else {
            serverTrailStyles.put(playerId, trailStyle);
        }
    }

    private static int encodeTrailStyle(TrailStyle trailStyle) {
        return trailStyle.ordinal();
    }

    private static TrailStyle decodeTrailStyle(int encodedTrailStyle) {
        TrailStyle[] values = TrailStyle.values();
        if (encodedTrailStyle < 0 || encodedTrailStyle >= values.length) {
            return TrailStyle.CLASSIC;
        }
        return values[encodedTrailStyle];
    }

    public record SetGuidanceVisibilityC2SPayload(boolean visible, int trailStyle) implements CustomPacketPayload {
        public static final Type<SetGuidanceVisibilityC2SPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(Gracebound.MOD_ID, "set_guidance_visibility_c2s")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SetGuidanceVisibilityC2SPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL,
                SetGuidanceVisibilityC2SPayload::visible,
                ByteBufCodecs.VAR_INT,
                SetGuidanceVisibilityC2SPayload::trailStyle,
                SetGuidanceVisibilityC2SPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetGuidanceVisibilityS2CPayload(UUID playerId, boolean visible, int trailStyle) implements CustomPacketPayload {
        public static final Type<SetGuidanceVisibilityS2CPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(Gracebound.MOD_ID, "set_guidance_visibility_s2c")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SetGuidanceVisibilityS2CPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC,
                SetGuidanceVisibilityS2CPayload::playerId,
                ByteBufCodecs.BOOL,
                SetGuidanceVisibilityS2CPayload::visible,
                ByteBufCodecs.VAR_INT,
                SetGuidanceVisibilityS2CPayload::trailStyle,
                SetGuidanceVisibilityS2CPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ServerHelloS2CPayload(
            boolean showOthersGuidance,
            int defaultGuidanceMode,
            boolean showDeathGuidanceWithoutRecoveryCompass,
            boolean wipeDeathLocationOnArrival
    ) implements CustomPacketPayload {
        public static final Type<ServerHelloS2CPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(Gracebound.MOD_ID, "server_hello_s2c")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, ServerHelloS2CPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL,
                ServerHelloS2CPayload::showOthersGuidance,
                ByteBufCodecs.VAR_INT,
                ServerHelloS2CPayload::defaultGuidanceMode,
                ByteBufCodecs.BOOL,
                ServerHelloS2CPayload::showDeathGuidanceWithoutRecoveryCompass,
                ByteBufCodecs.BOOL,
                ServerHelloS2CPayload::wipeDeathLocationOnArrival,
                ServerHelloS2CPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record WipeDeathLocationOnArrivalC2SPayload() implements CustomPacketPayload {
        public static final Type<WipeDeathLocationOnArrivalC2SPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(Gracebound.MOD_ID, "wipe_death_location_on_arrival_c2s")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, WipeDeathLocationOnArrivalC2SPayload> STREAM_CODEC =
                StreamCodec.unit(new WipeDeathLocationOnArrivalC2SPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
