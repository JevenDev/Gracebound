package com.jvn.gracebound.client;

import com.jvn.gracebound.config.GraceboundConfig;
import com.jvn.gracebound.guidance.GuidanceTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

public final class GraceboundGuidanceVisuals {
    private static final double ABSOLUTE_MAX_STREAM_DISTANCE = 6.0D;
    private static final double FULL_REACH_DISTANCE = 16.0D;
    private static final double FULL_REACH_BLEND_DISTANCE = 6.0D;
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final RenderType GRACE_RENDER_TYPE = RenderType.create(
            "gracebound_guidance",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            512,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderType.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderType.NO_CULL)
                    .setDepthTestState(RenderType.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderType.COLOR_WRITE)
                    .createCompositeState(false)
    );

    private static Optional<GuidanceTarget> lastTarget = Optional.empty();
    private static float visibility;
    private static boolean trailInitialized;
    private static Vec3 delayedOrigin = Vec3.ZERO;
    private static Vec3 delayedForward = new Vec3(0.0D, 0.0D, 1.0D);

    private GraceboundGuidanceVisuals() {
    }

    public static void tick(Player player, Optional<GuidanceTarget> target) {
        updateVisibility(target.isPresent());
        target.ifPresent(value -> lastTarget = Optional.of(value));

        if (visibility <= 0.01F || lastTarget.isEmpty()) {
            if (visibility <= 0.01F) {
                lastTarget = Optional.empty();
                trailInitialized = false;
            }
        }
    }

    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || visibility <= 0.01F || lastTarget.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        GuidanceTarget target = lastTarget.get();
        if (!target.pos().dimension().equals(minecraft.player.level().dimension())) {
            return;
        }

        renderGuidanceRibbon(event, minecraft.player, target, visibility);
    }

    private static void updateVisibility(boolean hasTarget) {
        if (!GraceboundConfig.enableFade) {
            visibility = hasTarget ? 1.0F : 0.0F;
            return;
        }

        if (hasTarget) {
            int fadeIn = Math.max(1, GraceboundConfig.fadeInTicks);
            visibility = Math.min(1.0F, visibility + 1.0F / fadeIn);
        } else {
            int fadeOut = Math.max(1, GraceboundConfig.fadeOutTicks);
            visibility = Math.max(0.0F, visibility - 1.0F / fadeOut);
        }
    }

    private static void renderGuidanceRibbon(RenderLevelStageEvent event, Player player, GuidanceTarget target, float intensity) {
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
        Minecraft minecraft = Minecraft.getInstance();
        boolean firstPerson = minecraft.getCameraEntity() == player && minecraft.options.getCameraType().isFirstPerson();
        double startDistance = 0.0D;
        double streamLength = Math.max(0.1D, distance);
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
                    .add(0.0D, -0.35D, 0.0D);
        }
        Vec3 start = origin.add(forward.scale(startDistance));
        if (!trailInitialized) {
            delayedOrigin = start;
            delayedForward = forward;
            trailInitialized = true;
        } else if (delayedOrigin.distanceToSqr(start) > 6.25D) {
            delayedOrigin = start;
            delayedForward = forward;
        }
        Vec3 previousDelayedOrigin = delayedOrigin;
        Vec3 previousDelayedForward = delayedForward;
        delayedOrigin = delayedOrigin.lerp(start, 0.08D);
        Vec3 mixedForward = delayedForward.scale(0.92D).add(forward.scale(0.08D));
        delayedForward = mixedForward.lengthSqr() > 0.0001D ? mixedForward.normalize() : forward;

        Vec3 lagOffset = previousDelayedOrigin.subtract(start);
        Vec3 lagLateral = lagOffset.subtract(forward.scale(lagOffset.dot(forward)));
        Vec3 forwardDelta = previousDelayedForward.subtract(forward);
        Vec3 turnLateral = forwardDelta.subtract(forward.scale(forwardDelta.dot(forward))).scale(streamLength * 0.35D);
        Vec3 bendVector = lagLateral.add(turnLateral);

        int strandCount = Mth.clamp((int)Math.round(4.0D + GraceboundConfig.beamDensity * 4.0D), 4, 9);
        int segments = Math.max(24, (int)Math.ceil(streamLength * (4.5D + GraceboundConfig.beamDensity * 3.0D)));
        double time = level.getGameTime() + partialTick;
        double bendStrength = Mth.clamp(player.getDeltaMovement().length() * 4.0D + lagLateral.length() * 2.8D, 0.0D, 1.0D);

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = poseStack.last().pose();

        VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(GRACE_RENDER_TYPE);
        for (int strand = 0; strand < strandCount; strand++) {
            float strandT = strandCount == 1 ? 0.5F : (float)strand / (strandCount - 1);
            float centerBias = 1.0F - Math.abs(strandT - 0.5F) * 2.0F;
            double strandPhase = strand * 1.73D;
            double strandLateral = (strandT - 0.5D) * 0.12D;
            float strandAlphaScale = 0.55F + centerBias * 0.95F;

            for (int i = 0; i < segments; i++) {
                float t0 = (float)i / segments;
                float t1 = (float)(i + 1) / segments;

                Vec3 c0 = ribbonPoint(start, forward, right, lift, bendVector, bendStrength, streamLength, time, t0, strandPhase, strandLateral);
                Vec3 c1 = ribbonPoint(start, forward, right, lift, bendVector, bendStrength, streamLength, time, t1, strandPhase, strandLateral);
                Vec3 side0 = ribbonSide(forward, right, c0, camera);
                Vec3 side1 = ribbonSide(forward, right, c1, camera);

                float width0 = (0.008F + (1.0F - t0) * 0.018F) * intensity * (0.75F + centerBias * 0.35F);
                float width1 = (0.008F + (1.0F - t1) * 0.018F) * intensity * (0.75F + centerBias * 0.35F);
                float alpha0 = (0.14F + (1.0F - t0) * 0.28F) * intensity * strandAlphaScale;
                float alpha1 = (0.14F + (1.0F - t1) * 0.28F) * intensity * strandAlphaScale;
                float headFade0 = Mth.clamp(t0 / 0.14F, 0.0F, 1.0F);
                float headFade1 = Mth.clamp(t1 / 0.14F, 0.0F, 1.0F);
                headFade0 *= headFade0;
                headFade1 *= headFade1;
                float tailFade0 = Mth.clamp((1.0F - t0) / 0.3F, 0.0F, 1.0F);
                float tailFade1 = Mth.clamp((1.0F - t1) / 0.3F, 0.0F, 1.0F);
                tailFade0 *= tailFade0;
                tailFade1 *= tailFade1;
                alpha0 *= headFade0 * tailFade0;
                alpha1 *= headFade1 * tailFade1;
                width0 *= (0.8F + 0.2F * headFade0) * (0.7F + 0.3F * tailFade0);
                width1 *= (0.8F + 0.2F * headFade1) * (0.7F + 0.3F * tailFade1);

                addRibbonQuad(consumer, matrix, c0, c1, side0, side1, width0, width1, t0, t1, alpha0, alpha1);

                if (centerBias > 0.92F) {
                    addRibbonQuadTint(
                            consumer,
                            matrix,
                            c0,
                            c1,
                            side0,
                            side1,
                            width0 * 0.62F,
                            width1 * 0.62F,
                            255,
                            248,
                            236,
                            alpha0 * 1.12F,
                            alpha1 * 1.12F
                    );
                }

                if (centerBias > 0.62F) {
                    float pulse = Mth.clamp((float)Math.sin((t0 + t1) * 37.0F + strandPhase * 1.9D) * 0.5F + 0.5F, 0.0F, 1.0F);
                    float whiteAlpha0 = alpha0 * (0.18F + centerBias * 0.4F) * pulse;
                    float whiteAlpha1 = alpha1 * (0.18F + centerBias * 0.4F) * pulse;
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
            }
        }

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
            double streamLength,
            double time,
            float progress,
            double strandPhase,
            double strandLateral) {
        double taper = 1.0D - progress;
        double mid = Math.sin(progress * Math.PI);
        double travel = progress * streamLength;
        Vec3 base = start.add(forward.scale(travel));
        double bendShape = mid * (1.0D - progress * 0.45D);
        Vec3 curve = bendVector.scale(bendShape * (0.35D + bendStrength * 0.9D));
        double rise = mid * (0.08D + streamLength * 0.055D);
        double meander = Math.sin(time * 0.22D + progress * 9.5D + strandPhase) * 0.055D * taper;
        double flutter = Math.cos(time * 0.31D + progress * 14.0D + strandPhase * 1.7D) * 0.018D * taper;
        double strandSpread = strandLateral * (0.72D + taper * 0.28D);
        double verticalBend = Math.sin(progress * Math.PI * 1.2D + strandPhase) * 0.015D;
        return base
                .add(curve)
                .add(UP.scale(rise))
                .add(right.scale(strandSpread + meander))
                .add(lift.scale(flutter + verticalBend));
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
            float alphaEnd) {
        int gStart = Mth.clamp((int)(214.0F + (1.0F - progressStart) * 24.0F), 0, 255);
        int gEnd = Mth.clamp((int)(214.0F + (1.0F - progressEnd) * 24.0F), 0, 255);
        int bStart = Mth.clamp((int)(128.0F + (1.0F - progressStart) * 42.0F), 0, 255);
        int bEnd = Mth.clamp((int)(128.0F + (1.0F - progressEnd) * 42.0F), 0, 255);
        addRibbonQuadTint(consumer, matrix, start, end, sideStart, sideEnd, widthStart, widthEnd, 255, gStart, bStart, alphaStart, alphaEnd, gEnd, bEnd);
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
            int r,
            int gStart,
            int bStart,
            float alphaStart,
            float alphaEnd,
            int gEnd,
            int bEnd) {
        Vec3 sL = start.subtract(sideStart.scale(widthStart));
        Vec3 sR = start.add(sideStart.scale(widthStart));
        Vec3 eL = end.subtract(sideEnd.scale(widthEnd));
        Vec3 eR = end.add(sideEnd.scale(widthEnd));

        int aStart = Mth.clamp((int)(alphaStart * 255.0F), 0, 255);
        int aEnd = Mth.clamp((int)(alphaEnd * 255.0F), 0, 255);

        consumer.addVertex(matrix, (float)sL.x, (float)sL.y, (float)sL.z).setColor(r, gStart, bStart, aStart);
        consumer.addVertex(matrix, (float)eL.x, (float)eL.y, (float)eL.z).setColor(r, gEnd, bEnd, aEnd);
        consumer.addVertex(matrix, (float)eR.x, (float)eR.y, (float)eR.z).setColor(r, gEnd, bEnd, aEnd);
        consumer.addVertex(matrix, (float)sR.x, (float)sR.y, (float)sR.z).setColor(r, gStart, bStart, aStart);

        consumer.addVertex(matrix, (float)sR.x, (float)sR.y, (float)sR.z).setColor(r, gStart, bStart, aStart);
        consumer.addVertex(matrix, (float)eR.x, (float)eR.y, (float)eR.z).setColor(r, gEnd, bEnd, aEnd);
        consumer.addVertex(matrix, (float)eL.x, (float)eL.y, (float)eL.z).setColor(r, gEnd, bEnd, aEnd);
        consumer.addVertex(matrix, (float)sL.x, (float)sL.y, (float)sL.z).setColor(r, gStart, bStart, aStart);
    }
}
