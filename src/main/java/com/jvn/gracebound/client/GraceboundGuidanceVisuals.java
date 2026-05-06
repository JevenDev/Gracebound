package com.jvn.gracebound.client;

import com.jvn.gracebound.config.GraceboundConfig;
import com.jvn.gracebound.config.GraceboundConfig.TrailStyle;
import com.jvn.gracebound.guidance.GuidanceTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

public final class GraceboundGuidanceVisuals {
    private static final double ABSOLUTE_MAX_STREAM_DISTANCE = 6.0D;
    private static final double FULL_REACH_DISTANCE = 16.0D;
    private static final double FULL_REACH_BLEND_DISTANCE = 6.0D;
    private static final double THIRD_PERSON_CHEST_HEIGHT_RATIO = 0.66D;
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final ResourceLocation TRAIL_SHADER = ResourceLocation.fromNamespaceAndPath("gracebound", "gracebound_trail");
    private static final RenderType GRACE_RENDER_TYPE = RenderType.create(
            "gracebound_guidance",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            512,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GraceboundGuidanceVisuals::trailShader))
                    .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderType.NO_CULL)
                    .setDepthTestState(RenderType.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderType.COLOR_WRITE)
                    .createCompositeState(false)
    );

    private static final Map<UUID, TrailState> trailStates = new HashMap<>();
    private static ShaderInstance trailShader;

    private static final class TrailState {
        Optional<GuidanceTarget> lastTarget = Optional.empty();
        Optional<GuidanceTarget> pendingTarget = Optional.empty();
        float visibility;
        boolean trailInitialized;
        Vec3 delayedOrigin = Vec3.ZERO;
        Vec3 delayedForward = new Vec3(0.0D, 0.0D, 1.0D);
    }

    private GraceboundGuidanceVisuals() {
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), TRAIL_SHADER.toString(), DefaultVertexFormat.POSITION_TEX_COLOR),
                    shader -> trailShader = shader
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load Gracebound trail shader", exception);
        }
    }

    private static ShaderInstance trailShader() {
        return trailShader;
    }

    public static void tick(Player player, Optional<GuidanceTarget> target) {
        TrailState state = trailStates.computeIfAbsent(player.getUUID(), ignored -> new TrailState());
        if (state.lastTarget.isEmpty()) {
            if (target.isPresent()) {
                state.lastTarget = target;
                state.pendingTarget = Optional.empty();
                updateVisibility(state, true);
            } else {
                updateVisibility(state, false);
                if (state.visibility <= 0.01F) {
                    state.trailInitialized = false;
                }
            }
            pruneInactiveState(player, state);
            return;
        }

        if (target.isPresent() && target.get().equals(state.lastTarget.get())) {
            state.pendingTarget = Optional.empty();
            updateVisibility(state, true);
            return;
        }

        if (target.isPresent()) {
            state.pendingTarget = target;
        } else {
            state.pendingTarget = Optional.empty();
        }

        // Retract current stream before switching targets to avoid abrupt jumps.
        updateVisibility(state, false);
        if (state.visibility <= 0.01F) {
            if (state.pendingTarget.isPresent()) {
                state.lastTarget = state.pendingTarget;
                state.pendingTarget = Optional.empty();
                updateVisibility(state, true);
            } else {
                state.lastTarget = Optional.empty();
                state.trailInitialized = false;
            }
        }
        pruneInactiveState(player, state);
    }

    public static void clearOtherPlayers(Player localPlayer) {
        UUID localId = localPlayer.getUUID();
        trailStates.keySet().removeIf(playerId -> !playerId.equals(localId));
    }

    public static void clearAll() {
        trailStates.clear();
    }

    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || trailStates.isEmpty() || trailShader == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Iterator<Map.Entry<UUID, TrailState>> iterator = trailStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrailState> entry = iterator.next();
            Player player = minecraft.level.getPlayerByUUID(entry.getKey());
            TrailState state = entry.getValue();
            if (player == null) {
                iterator.remove();
                continue;
            }

            if (state.visibility <= 0.01F || state.lastTarget.isEmpty()) {
                continue;
            }

            GuidanceTarget target = state.lastTarget.get();
            if (!target.pos().dimension().equals(player.level().dimension())) {
                continue;
            }

            renderGuidanceRibbon(event, player, target, state);
        }
    }

    private static void updateVisibility(TrailState state, boolean hasTarget) {
        if (!GraceboundConfig.enableFade) {
            state.visibility = hasTarget ? 1.0F : 0.0F;
            return;
        }

        if (hasTarget) {
            int fadeIn = Math.max(1, GraceboundConfig.fadeInTicks);
            state.visibility = Math.min(1.0F, state.visibility + 1.0F / fadeIn);
        } else {
            int fadeOut = Math.max(1, GraceboundConfig.fadeOutTicks);
            state.visibility = Math.max(0.0F, state.visibility - 1.0F / fadeOut);
        }
    }

    private static void pruneInactiveState(Player player, TrailState state) {
        if (state.visibility <= 0.01F && state.lastTarget.isEmpty() && state.pendingTarget.isEmpty()) {
            trailStates.remove(player.getUUID());
        }
    }

    private static void renderGuidanceRibbon(RenderLevelStageEvent event, Player player, GuidanceTarget target, TrailState state) {
        if (!(player.level() instanceof ClientLevel level)) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 destination = Vec3.atCenterOf(target.pos().pos());
        Vec3 direction = destination.subtract(eye);
        if (direction.lengthSqr() < 0.0001D) {
            return;
        }

        Vec3 forward = direction.normalize();
        double rawDistance = Math.sqrt(direction.lengthSqr());
        double maxDistance = Math.min(GraceboundConfig.maxBeamDistance, ABSOLUTE_MAX_STREAM_DISTANCE);
        double distance;
        if (rawDistance <= FULL_REACH_DISTANCE) {
            distance = rawDistance;
        } else if (rawDistance >= FULL_REACH_DISTANCE + FULL_REACH_BLEND_DISTANCE) {
            distance = Math.min(maxDistance, rawDistance);
        } else {
            double t = 1.0D - ((rawDistance - FULL_REACH_DISTANCE) / FULL_REACH_BLEND_DISTANCE);
            double blended = maxDistance + (rawDistance - maxDistance) * t;
            distance = Math.min(rawDistance, blended);
        }
        double arcDistanceFactor = Mth.clamp(rawDistance / FULL_REACH_DISTANCE, 0.25D, 1.0D);
        if (rawDistance > FULL_REACH_DISTANCE) {
            arcDistanceFactor = 1.0D + Mth.clamp((rawDistance - FULL_REACH_DISTANCE) / 20.0D, 0.0D, 1.4D);
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean firstPerson = minecraft.getCameraEntity() == player && minecraft.options.getCameraType().isFirstPerson();
        float intensity = state.visibility;
        double startDistance = 0.0D;
        double streamLength = firstPerson
                ? Math.max(0.35D, Math.min(distance, 1.8D))
                : Math.max(0.1D, distance);
        Vec3 origin = eye.add(0.0D, GraceboundConfig.beamVerticalOffset, 0.0D);

        Vec3 right = forward.cross(UP);
        if (right.lengthSqr() < 0.0001D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 lift = right.cross(forward).normalize();
        if (firstPerson) {
            origin = origin
                    .add(0.0D, -0.25D, 0.0D);
        } else {
            Vec3 bodyPos = player.getPosition(partialTick);
            double chestY = bodyPos.y + player.getBbHeight() * THIRD_PERSON_CHEST_HEIGHT_RATIO;
            origin = new Vec3(origin.x, chestY, origin.z);
        }
        Vec3 start = origin.add(forward.scale(startDistance));
        if (!state.trailInitialized) {
            state.delayedOrigin = start;
            state.delayedForward = forward;
            state.trailInitialized = true;
        } else if (state.delayedOrigin.distanceToSqr(start) > 6.25D) {
            state.delayedOrigin = start;
            state.delayedForward = forward;
        }
        Vec3 previousDelayedOrigin = state.delayedOrigin;
        Vec3 previousDelayedForward = state.delayedForward;
        state.delayedOrigin = state.delayedOrigin.lerp(start, 0.08D);
        Vec3 mixedForward = state.delayedForward.scale(0.92D).add(forward.scale(0.08D));
        state.delayedForward = mixedForward.lengthSqr() > 0.0001D ? mixedForward.normalize() : forward;

        Vec3 lagOffset = previousDelayedOrigin.subtract(start);
        Vec3 lagLateral = lagOffset.subtract(forward.scale(lagOffset.dot(forward)));
        Vec3 forwardDelta = previousDelayedForward.subtract(forward);
        Vec3 turnLateral = forwardDelta.subtract(forward.scale(forwardDelta.dot(forward))).scale(streamLength * 0.35D);
        Vec3 bendVector = lagLateral.add(turnLateral);

        TrailStyle trailStyle = GraceboundConfig.trailStyle;
        int strandCount = trailStyle == TrailStyle.ENCHANTED
                ? (firstPerson ? 4 : Mth.clamp((int)Math.round(6.0D + GraceboundConfig.beamDensity * 5.0D), 6, 12))
                : trailStyle == TrailStyle.AURORA
                ? (firstPerson ? 5 : Mth.clamp((int)Math.round(5.0D + GraceboundConfig.beamDensity * 4.0D), 5, 11))
                : (firstPerson ? 3 : Mth.clamp((int)Math.round(4.0D + GraceboundConfig.beamDensity * 4.0D), 4, 9));
        int segments = trailStyle == TrailStyle.ENCHANTED
                ? (firstPerson ? 20 : Math.max(32, (int)Math.ceil(streamLength * (6.0D + GraceboundConfig.beamDensity * 3.8D))))
                : trailStyle == TrailStyle.AURORA
                ? (firstPerson ? 22 : Math.max(36, (int)Math.ceil(streamLength * (6.4D + GraceboundConfig.beamDensity * 3.6D))))
                : (firstPerson ? 16 : Math.max(24, (int)Math.ceil(streamLength * (4.5D + GraceboundConfig.beamDensity * 3.0D))));
        double time = level.getGameTime() + partialTick;
        double baseBendStrength = Mth.clamp(player.getDeltaMovement().length() * 4.0D + lagLateral.length() * 2.8D, 0.0D, 1.0D);
        double bendStrength = firstPerson ? baseBendStrength * 0.75D : baseBendStrength;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = poseStack.last().pose();

        VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(GRACE_RENDER_TYPE);
        for (int strand = 0; strand < strandCount; strand++) {
            float strandT = strandCount == 1 ? 0.5F : (float)strand / (strandCount - 1);
            float centerBias = 1.0F - Math.abs(strandT - 0.5F) * 2.0F;
            double strandPhase = trailStyle == TrailStyle.ENCHANTED ? strand * 2.37D : trailStyle == TrailStyle.AURORA ? strand * 2.91D : strand * 1.73D;
            double strandLateral = (strandT - 0.5D) * (trailStyle == TrailStyle.ENCHANTED ? 0.18D : trailStyle == TrailStyle.AURORA ? 0.24D : 0.12D);
            float strandAlphaScale = trailStyle == TrailStyle.ENCHANTED ? 0.38F + centerBias * 0.9F : trailStyle == TrailStyle.AURORA ? 0.3F + centerBias * 0.74F : 0.55F + centerBias * 0.95F;

            for (int i = 0; i < segments; i++) {
                float t0 = (float)i / segments;
                float t1 = (float)(i + 1) / segments;

                Vec3 c0 = ribbonPoint(start, forward, right, lift, bendVector, bendStrength, arcDistanceFactor, streamLength, time, t0, strandPhase, strandLateral, firstPerson, trailStyle);
                Vec3 c1 = ribbonPoint(start, forward, right, lift, bendVector, bendStrength, arcDistanceFactor, streamLength, time, t1, strandPhase, strandLateral, firstPerson, trailStyle);
                Vec3 side0 = ribbonSide(forward, right, c0, camera);
                Vec3 side1 = ribbonSide(forward, right, c1, camera);

                float styleWidthScale = trailStyle == TrailStyle.ENCHANTED ? 0.82F + centerBias * 0.42F : trailStyle == TrailStyle.AURORA ? 1.1F + centerBias * 0.34F : 0.75F + centerBias * 0.35F;
                float styleAlphaBase = trailStyle == TrailStyle.ENCHANTED ? 0.1F : trailStyle == TrailStyle.AURORA ? 0.08F : 0.14F;
                float styleAlphaReach = trailStyle == TrailStyle.ENCHANTED ? 0.36F : trailStyle == TrailStyle.AURORA ? 0.26F : 0.28F;
                float width0 = (0.008F + (1.0F - t0) * (trailStyle == TrailStyle.ENCHANTED ? 0.014F : trailStyle == TrailStyle.AURORA ? 0.026F : 0.018F)) * intensity * styleWidthScale;
                float width1 = (0.008F + (1.0F - t1) * (trailStyle == TrailStyle.ENCHANTED ? 0.014F : trailStyle == TrailStyle.AURORA ? 0.026F : 0.018F)) * intensity * styleWidthScale;
                float alpha0 = (styleAlphaBase + (1.0F - t0) * styleAlphaReach) * intensity * strandAlphaScale;
                float alpha1 = (styleAlphaBase + (1.0F - t1) * styleAlphaReach) * intensity * strandAlphaScale;
                float headFade0 = Mth.clamp(t0 / (firstPerson ? 0.08F : 0.14F), 0.0F, 1.0F);
                float headFade1 = Mth.clamp(t1 / (firstPerson ? 0.08F : 0.14F), 0.0F, 1.0F);
                headFade0 *= headFade0;
                headFade1 *= headFade1;
                float tailFade0 = Mth.clamp((1.0F - t0) / (firstPerson ? 0.22F : 0.3F), 0.0F, 1.0F);
                float tailFade1 = Mth.clamp((1.0F - t1) / (firstPerson ? 0.22F : 0.3F), 0.0F, 1.0F);
                tailFade0 *= tailFade0;
                tailFade1 *= tailFade1;
                alpha0 *= headFade0 * tailFade0;
                alpha1 *= headFade1 * tailFade1;
                width0 *= (0.8F + 0.2F * headFade0) * (0.7F + 0.3F * tailFade0);
                width1 *= (0.8F + 0.2F * headFade1) * (0.7F + 0.3F * tailFade1);
                if (firstPerson) {
                    float fpTipFade0 = Mth.clamp((1.0F - t0) / 0.14F, 0.0F, 1.0F);
                    float fpTipFade1 = Mth.clamp((1.0F - t1) / 0.14F, 0.0F, 1.0F);
                    fpTipFade0 = fpTipFade0 * fpTipFade0 * fpTipFade0;
                    fpTipFade1 = fpTipFade1 * fpTipFade1 * fpTipFade1;
                    alpha0 *= fpTipFade0;
                    alpha1 *= fpTipFade1;
                    width0 *= (0.45F + 0.55F * fpTipFade0);
                    width1 *= (0.45F + 0.55F * fpTipFade1);
                }

                float shimmer = Mth.clamp((float)Math.sin(time * 0.38D + strandPhase + (t0 + t1) * 18.0F) * 0.5F + 0.5F, 0.0F, 1.0F);
                addRibbonQuad(consumer, matrix, c0, c1, side0, side1, width0, width1, t0, t1, alpha0, alpha1, trailStyle, shimmer);

                if (centerBias > 0.92F) {
                    float fpHighlight = firstPerson ? 0.6F : 1.0F;
                    int highlightR = trailStyle == TrailStyle.ENCHANTED ? 236 : 255;
                    int highlightG = trailStyle == TrailStyle.ENCHANTED ? 255 : 248;
                    int highlightB = trailStyle == TrailStyle.ENCHANTED ? 246 : 236;
                    addRibbonQuadTint(
                            consumer,
                            matrix,
                            c0,
                            c1,
                            side0,
                            side1,
                            width0 * 0.62F,
                            width1 * 0.62F,
                            highlightR,
                            highlightG,
                            highlightB,
                            alpha0 * 1.12F * fpHighlight,
                            alpha1 * 1.12F * fpHighlight
                    );
                }

                if (centerBias > 0.62F) {
                    float fpHighlight = firstPerson ? 0.6F : 1.0F;
                    float pulse = Mth.clamp((float)Math.sin((t0 + t1) * 37.0F + strandPhase * 1.9D) * 0.5F + 0.5F, 0.0F, 1.0F);
                    float whiteAlpha0 = alpha0 * (0.18F + centerBias * 0.4F) * pulse * fpHighlight;
                    float whiteAlpha1 = alpha1 * (0.18F + centerBias * 0.4F) * pulse * fpHighlight;
                    if (whiteAlpha0 > 0.004F || whiteAlpha1 > 0.004F) {
                        addRibbonQuadTint(
                                consumer,
                                matrix,
                                c0,
                                c1,
                                side0,
                                side1,
                                width0 * 0.35F,
                                width1 * 0.35F,
                                255,
                                252,
                                246,
                                whiteAlpha0,
                                whiteAlpha1
                        );
                    }
                }

                if (trailStyle == TrailStyle.ENCHANTED && centerBias > 0.48F && i % 5 == strand % 5) {
                    float glint = Mth.clamp((float)Math.sin(time * 0.72D + strandPhase * 2.0D + t0 * 41.0F) * 0.5F + 0.5F, 0.0F, 1.0F);
                    if (glint > 0.5F) {
                        Vec3 glintSide = side0.add(side1);
                        glintSide = glintSide.lengthSqr() > 0.0001D ? glintSide.normalize() : side0;
                        addEnchantedGlint(
                                consumer,
                                matrix,
                                c0.lerp(c1, 0.5D),
                                glintSide,
                                lift,
                                (0.018F + centerBias * 0.018F) * intensity * glint,
                                alpha0 * (0.45F + glint * 0.35F) * (firstPerson ? 0.45F : 1.0F),
                                shimmer
                        );
                    }
                }

                if (trailStyle == TrailStyle.AURORA && centerBias > 0.35F && i % 7 == (strand * 2) % 7) {
                    float mote = Mth.clamp((float)Math.sin(time * 0.45D + strandPhase * 1.3D + t0 * 23.0F) * 0.5F + 0.5F, 0.0F, 1.0F);
                    if (mote > 0.45F) {
                        Vec3 moteSide = side0.add(side1);
                        moteSide = moteSide.lengthSqr() > 0.0001D ? moteSide.normalize() : side0;
                        addEnchantedGlint(
                                consumer,
                                matrix,
                                c0.lerp(c1, 0.35D + mote * 0.3D),
                                moteSide,
                                lift,
                                (0.014F + centerBias * 0.02F) * intensity * mote,
                                alpha0 * (0.28F + mote * 0.32F) * (firstPerson ? 0.35F : 0.82F),
                                shimmer
                        );
                    }
                }
            }
        }

        trailShader.safeGetUniform("TrailTime").set((float)(time * 0.05D));
        minecraft.renderBuffers().bufferSource().endBatch(GRACE_RENDER_TYPE);
        poseStack.popPose();
    }

    private static Vec3 ribbonPoint(
            Vec3 start,
            Vec3 forward,
            Vec3 right,
            Vec3 lift,
            Vec3 bendVector,
            double bendStrength,
            double arcDistanceFactor,
            double streamLength,
            double time,
            float progress,
            double strandPhase,
            double strandLateral,
            boolean firstPerson,
            TrailStyle trailStyle) {
        double taper = 1.0D - progress;
        double mid = Math.sin(progress * Math.PI);
        double travel = progress * streamLength;
        Vec3 base = start.add(forward.scale(travel));
        double fpCurveScale = firstPerson ? 0.65D : 1.0D;
        double fpDisplaceScale = firstPerson ? 0.75D : 1.0D;
        double bendShape = mid * (1.0D - progress * 0.45D);
        Vec3 curve = bendVector.scale(bendShape * (0.35D + bendStrength * 0.9D) * fpCurveScale);
        double rise = mid * (0.02D + streamLength * (0.015D + 0.05D * arcDistanceFactor) + arcDistanceFactor * 0.06D) * (firstPerson ? 0.6D : 1.0D);
        double meander = Math.sin(time * 0.22D + progress * 9.5D + strandPhase) * 0.055D * taper * fpDisplaceScale;
        double flutter = Math.cos(time * 0.31D + progress * 14.0D + strandPhase * 1.7D) * 0.018D * taper * fpDisplaceScale;
        double strandSpread = strandLateral * (0.72D + taper * 0.28D) * (firstPerson ? 0.7D : 1.0D);
        double verticalBend = Math.sin(progress * Math.PI * 1.2D + strandPhase) * 0.015D * fpDisplaceScale;
        Vec3 point = base
                .add(curve)
                .add(UP.scale(rise))
                .add(right.scale(strandSpread + meander))
                .add(lift.scale(flutter + verticalBend));
        if (trailStyle != TrailStyle.ENCHANTED) {
            if (trailStyle != TrailStyle.AURORA) {
                return point;
            }

            double curtain = Math.sin(progress * Math.PI * 1.7D + time * 0.055D + strandPhase * 0.55D);
            double fold = Math.sin(progress * Math.PI * 6.5D - time * 0.095D + strandPhase);
            double auroraScale = (0.035D + 0.055D * mid) * fpDisplaceScale;
            return point
                    .add(right.scale(curtain * auroraScale * (0.8D + taper * 0.4D)))
                    .add(lift.scale(fold * auroraScale * 0.45D))
                    .add(UP.scale(mid * (0.045D + 0.035D * Math.sin(time * 0.04D + strandPhase))));
        }

        double helix = Math.sin(progress * Math.PI * 5.2D + time * 0.16D + strandPhase);
        double counterHelix = Math.cos(progress * Math.PI * 4.0D - time * 0.11D + strandPhase * 0.7D);
        double spellBreath = 0.5D + 0.5D * Math.sin(time * 0.08D + strandPhase);
        double enchantScale = (0.022D + 0.026D * spellBreath) * mid * fpDisplaceScale;
        return point
                .add(right.scale(helix * enchantScale))
                .add(lift.scale(counterHelix * enchantScale * 0.72D))
                .add(UP.scale(Math.sin(progress * Math.PI * 2.4D + strandPhase) * 0.018D * mid));
    }

    private static Vec3 ribbonSide(Vec3 forward, Vec3 fallbackRight, Vec3 center, Vec3 camera) {
        Vec3 toCamera = camera.subtract(center);
        Vec3 side = forward.cross(toCamera);
        if (side.lengthSqr() < 0.0001D) {
            return fallbackRight;
        }
        return side.normalize();
    }

    private static void addRibbonQuad(
            VertexConsumer consumer,
            Matrix4f matrix,
            Vec3 start,
            Vec3 end,
            Vec3 sideStart,
            Vec3 sideEnd,
            float widthStart,
            float widthEnd,
            float progressStart,
            float progressEnd,
            float alphaStart,
            float alphaEnd,
            TrailStyle trailStyle,
            float shimmer) {
        if (trailStyle == TrailStyle.ENCHANTED) {
            float hueShiftStart = Mth.clamp(progressStart + shimmer * 0.22F, 0.0F, 1.0F);
            float hueShiftEnd = Mth.clamp(progressEnd + shimmer * 0.22F, 0.0F, 1.0F);
            int rStart = Mth.clamp((int)(104.0F + shimmer * 88.0F + hueShiftStart * 48.0F), 0, 255);
            int gStart = Mth.clamp((int)(210.0F + (1.0F - progressStart) * 32.0F), 0, 255);
            int bStart = Mth.clamp((int)(224.0F + shimmer * 28.0F), 0, 255);
            int rEnd = Mth.clamp((int)(126.0F + shimmer * 68.0F + hueShiftEnd * 54.0F), 0, 255);
            int gEnd = Mth.clamp((int)(176.0F + (1.0F - progressEnd) * 52.0F), 0, 255);
            int bEnd = Mth.clamp((int)(244.0F + shimmer * 11.0F), 0, 255);
            addRibbonQuadTint(consumer, matrix, start, end, sideStart, sideEnd, widthStart, widthEnd, rStart, gStart, bStart, alphaStart, alphaEnd, rEnd, gEnd, bEnd, progressStart, progressEnd, trailStyle);
            return;
        }

        if (trailStyle == TrailStyle.AURORA) {
            int rStart = Mth.clamp((int)(62.0F + shimmer * 54.0F + progressStart * 42.0F), 0, 255);
            int gStart = Mth.clamp((int)(178.0F + (1.0F - progressStart) * 64.0F), 0, 255);
            int bStart = Mth.clamp((int)(194.0F + shimmer * 38.0F), 0, 255);
            int rEnd = Mth.clamp((int)(118.0F + shimmer * 64.0F + progressEnd * 48.0F), 0, 255);
            int gEnd = Mth.clamp((int)(148.0F + (1.0F - progressEnd) * 76.0F), 0, 255);
            int bEnd = Mth.clamp((int)(228.0F + shimmer * 22.0F), 0, 255);
            addRibbonQuadTint(consumer, matrix, start, end, sideStart, sideEnd, widthStart, widthEnd, rStart, gStart, bStart, alphaStart * 0.82F, alphaEnd * 0.82F, rEnd, gEnd, bEnd, progressStart, progressEnd, trailStyle);
            return;
        }

        int gStart = Mth.clamp((int)(214.0F + (1.0F - progressStart) * 24.0F), 0, 255);
        int gEnd = Mth.clamp((int)(214.0F + (1.0F - progressEnd) * 24.0F), 0, 255);
        int bStart = Mth.clamp((int)(128.0F + (1.0F - progressStart) * 42.0F), 0, 255);
        int bEnd = Mth.clamp((int)(128.0F + (1.0F - progressEnd) * 42.0F), 0, 255);
        addRibbonQuadTint(consumer, matrix, start, end, sideStart, sideEnd, widthStart, widthEnd, 255, gStart, bStart, alphaStart, alphaEnd, 255, gEnd, bEnd, progressStart, progressEnd, trailStyle);
    }

    private static void addRibbonQuadTint(
            VertexConsumer consumer,
            Matrix4f matrix,
            Vec3 start,
            Vec3 end,
            Vec3 sideStart,
            Vec3 sideEnd,
            float widthStart,
            float widthEnd,
            int r,
            int g,
            int b,
            float alphaStart,
            float alphaEnd) {
        addRibbonQuadTint(consumer, matrix, start, end, sideStart, sideEnd, widthStart, widthEnd, r, g, b, alphaStart, alphaEnd, g, b);
    }

    private static void addRibbonQuadTint(
            VertexConsumer consumer,
            Matrix4f matrix,
            Vec3 start,
            Vec3 end,
            Vec3 sideStart,
            Vec3 sideEnd,
            float widthStart,
            float widthEnd,
            int rStart,
            int gStart,
            int bStart,
            float alphaStart,
            float alphaEnd,
            int rEnd,
            int gEnd,
            int bEnd) {
        addRibbonQuadTint(consumer, matrix, start, end, sideStart, sideEnd, widthStart, widthEnd, rStart, gStart, bStart, alphaStart, alphaEnd, rEnd, gEnd, bEnd, 0.0F, 1.0F, GraceboundConfig.trailStyle);
    }

    private static void addRibbonQuadTint(
            VertexConsumer consumer,
            Matrix4f matrix,
            Vec3 start,
            Vec3 end,
            Vec3 sideStart,
            Vec3 sideEnd,
            float widthStart,
            float widthEnd,
            int rStart,
            int gStart,
            int bStart,
            float alphaStart,
            float alphaEnd,
            int rEnd,
            int gEnd,
            int bEnd,
            float progressStart,
            float progressEnd,
            TrailStyle trailStyle) {
        Vec3 sL = start.subtract(sideStart.scale(widthStart));
        Vec3 sR = start.add(sideStart.scale(widthStart));
        Vec3 eL = end.subtract(sideEnd.scale(widthEnd));
        Vec3 eR = end.add(sideEnd.scale(widthEnd));

        int aStart = Mth.clamp((int)(alphaStart * 255.0F), 0, 255);
        int aEnd = Mth.clamp((int)(alphaEnd * 255.0F), 0, 255);
        float styleBase = trailStyle.ordinal();
        float uvLeft = styleBase + 0.02F;
        float uvRight = styleBase + 0.22F;

        consumer.addVertex(matrix, (float)sL.x, (float)sL.y, (float)sL.z).setUv(progressStart, uvLeft).setColor(rStart, gStart, bStart, aStart);
        consumer.addVertex(matrix, (float)eL.x, (float)eL.y, (float)eL.z).setUv(progressEnd, uvLeft).setColor(rEnd, gEnd, bEnd, aEnd);
        consumer.addVertex(matrix, (float)eR.x, (float)eR.y, (float)eR.z).setUv(progressEnd, uvRight).setColor(rEnd, gEnd, bEnd, aEnd);
        consumer.addVertex(matrix, (float)sR.x, (float)sR.y, (float)sR.z).setUv(progressStart, uvRight).setColor(rStart, gStart, bStart, aStart);

        consumer.addVertex(matrix, (float)sR.x, (float)sR.y, (float)sR.z).setUv(progressStart, uvRight).setColor(rStart, gStart, bStart, aStart);
        consumer.addVertex(matrix, (float)eR.x, (float)eR.y, (float)eR.z).setUv(progressEnd, uvRight).setColor(rEnd, gEnd, bEnd, aEnd);
        consumer.addVertex(matrix, (float)eL.x, (float)eL.y, (float)eL.z).setUv(progressEnd, uvLeft).setColor(rEnd, gEnd, bEnd, aEnd);
        consumer.addVertex(matrix, (float)sL.x, (float)sL.y, (float)sL.z).setUv(progressStart, uvLeft).setColor(rStart, gStart, bStart, aStart);
    }

    private static void addRibbonQuadTint(
            VertexConsumer consumer,
            Matrix4f matrix,
            Vec3 start,
            Vec3 end,
            Vec3 sideStart,
            Vec3 sideEnd,
            float widthStart,
            float widthEnd,
            int r,
            int gStart,
            int bStart,
            float alphaStart,
            float alphaEnd,
            int gEnd,
            int bEnd) {
        addRibbonQuadTint(consumer, matrix, start, end, sideStart, sideEnd, widthStart, widthEnd, r, gStart, bStart, alphaStart, alphaEnd, r, gEnd, bEnd);
    }

    private static void addEnchantedGlint(
            VertexConsumer consumer,
            Matrix4f matrix,
            Vec3 center,
            Vec3 side,
            Vec3 lift,
            float radius,
            float alpha,
            float shimmer) {
        int r = Mth.clamp((int)(178.0F + shimmer * 58.0F), 0, 255);
        int g = Mth.clamp((int)(238.0F + shimmer * 17.0F), 0, 255);
        int b = 255;
        addRibbonQuadTint(
                consumer,
                matrix,
                center.subtract(lift.scale(radius)),
                center.add(lift.scale(radius)),
                side,
                side,
                radius * 0.62F,
                radius * 0.62F,
                r,
                g,
                b,
                alpha,
                alpha * 0.2F
        );
        addRibbonQuadTint(
                consumer,
                matrix,
                center.subtract(side.scale(radius)),
                center.add(side.scale(radius)),
                lift,
                lift,
                radius * 0.42F,
                radius * 0.42F,
                255,
                244,
                192,
                alpha * 0.5F,
                alpha * 0.1F
        );
    }
}
