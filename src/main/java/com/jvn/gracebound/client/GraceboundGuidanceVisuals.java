package com.jvn.gracebound.client;

import com.jvn.gracebound.config.GraceboundConfig;
import com.jvn.gracebound.guidance.GuidanceTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class GraceboundGuidanceVisuals {
    private static final DustParticleOptions GOLD_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.72F, 0.18F), 0.82F);
    private static final DustParticleOptions PALE_GOLD_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.9F, 0.48F), 0.56F);
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    private static Optional<GuidanceTarget> lastTarget = Optional.empty();
    private static float visibility;

    private GraceboundGuidanceVisuals() {
    }

    public static void tick(Player player, Optional<GuidanceTarget> target) {
        updateVisibility(target.isPresent());
        target.ifPresent(value -> lastTarget = Optional.of(value));

        if (visibility <= 0.01F || lastTarget.isEmpty() || !(player.level() instanceof ClientLevel level)) {
            if (visibility <= 0.01F) {
                lastTarget = Optional.empty();
            }
            return;
        }

        spawnGuidanceParticles(level, player, lastTarget.get(), visibility);
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

    private static void spawnGuidanceParticles(ClientLevel level, Player player, GuidanceTarget target, float intensity) {
        Vec3 eye = player.getEyePosition();
        Vec3 destination = Vec3.atCenterOf(target.pos().pos());
        Vec3 direction = destination.subtract(eye);
        if (direction.lengthSqr() < 0.0001D) {
            return;
        }

        Vec3 forward = direction.normalize();
        double distance = Math.min(GraceboundConfig.maxBeamDistance, Math.sqrt(direction.lengthSqr()));
        Vec3 origin = eye.add(forward.scale(GraceboundConfig.beamOriginOffset)).add(0.0D, GraceboundConfig.beamVerticalOffset, 0.0D);
        double startDistance = Math.min(distance, GraceboundConfig.beamStartDistance);
        double streamLength = Math.max(0.1D, distance - startDistance);
        int count = Math.max(1, (int)Math.ceil(streamLength * GraceboundConfig.beamDensity * intensity));
        RandomSource random = player.getRandom();
        Vec3 right = forward.cross(UP);
        if (right.lengthSqr() < 0.0001D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 lift = right.cross(forward).normalize();

        for (int i = 0; i < count; i++) {
            double progress = ((double)i + random.nextDouble()) / count;
            double taper = 1.0D - progress;
            double travel = startDistance + progress * streamLength;
            double swirl = Math.sin((level.getGameTime() + i * 7) * 0.24D + progress * Math.PI * 2.0D) * 0.18D * taper;
            double drift = (random.nextDouble() - 0.5D) * 0.22D * taper;
            Vec3 pos = origin
                    .add(forward.scale(travel))
                    .add(right.scale(swirl + drift))
                    .add(lift.scale((random.nextDouble() - 0.25D) * 0.18D * taper));
            Vec3 velocity = forward.scale(0.012D + 0.018D * taper)
                    .add(right.scale(swirl * 0.015D))
                    .add(0.0D, 0.006D * taper, 0.0D);
            level.addParticle(i % 4 == 0 ? PALE_GOLD_DUST : GOLD_DUST, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
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
        double distance = Math.min(GraceboundConfig.maxBeamDistance, Math.sqrt(direction.lengthSqr()));
        double startDistance = Math.min(distance, GraceboundConfig.beamStartDistance);
        double streamLength = Math.max(0.1D, distance - startDistance);
        Vec3 origin = eye.add(forward.scale(GraceboundConfig.beamOriginOffset)).add(0.0D, GraceboundConfig.beamVerticalOffset, 0.0D);
        Vec3 start = origin.add(forward.scale(startDistance));

        Vec3 right = forward.cross(UP);
        if (right.lengthSqr() < 0.0001D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 lift = right.cross(forward).normalize();
        int segments = Math.max(8, (int)Math.ceil(streamLength * 1.6D));
        double time = level.getGameTime() + partialTick;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = poseStack.last().pose();

        VertexConsumer consumer = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.lightning());
        for (int i = 0; i < segments; i++) {
            float t0 = (float)i / segments;
            float t1 = (float)(i + 1) / segments;

            Vec3 c0 = ribbonPoint(start, forward, right, lift, streamLength, time, t0);
            Vec3 c1 = ribbonPoint(start, forward, right, lift, streamLength, time, t1);
            Vec3 side0 = ribbonSide(forward, right, c0, camera);
            Vec3 side1 = ribbonSide(forward, right, c1, camera);

            float width0 = (0.12F + (1.0F - t0) * 0.22F) * intensity;
            float width1 = (0.12F + (1.0F - t1) * 0.22F) * intensity;
            float alpha0 = (0.22F + (1.0F - t0) * 0.45F) * intensity;
            float alpha1 = (0.22F + (1.0F - t1) * 0.45F) * intensity;

            addRibbonQuad(consumer, matrix, c0, c1, side0, side1, width0 * 1.55F, width1 * 1.55F, t0, t1, alpha0 * 0.5F, alpha1 * 0.5F);
            addRibbonQuad(consumer, matrix, c0, c1, side0, side1, width0, width1, t0, t1, alpha0, alpha1);
        }

        Minecraft.getInstance().renderBuffers().bufferSource().endBatch(RenderType.lightning());
        poseStack.popPose();
    }

    private static Vec3 ribbonPoint(Vec3 start, Vec3 forward, Vec3 right, Vec3 lift, double streamLength, double time, float progress) {
        double taper = 1.0D - progress;
        double travel = progress * streamLength;
        double swirl = Math.sin(time * 0.28D + progress * 13.0D) * 0.16D * taper;
        double arch = Math.sin(progress * Math.PI) * 0.1D;
        double flutter = Math.cos(time * 0.19D + progress * 8.0D) * 0.05D * taper;
        return start
                .add(forward.scale(travel))
                .add(right.scale(swirl))
                .add(lift.scale(arch + flutter));
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
        Vec3 sL = start.subtract(sideStart.scale(widthStart));
        Vec3 sR = start.add(sideStart.scale(widthStart));
        Vec3 eL = end.subtract(sideEnd.scale(widthEnd));
        Vec3 eR = end.add(sideEnd.scale(widthEnd));

        int aStart = Mth.clamp((int)(alphaStart * 255.0F), 0, 255);
        int aEnd = Mth.clamp((int)(alphaEnd * 255.0F), 0, 255);
        int gStart = Mth.clamp((int)(184.0F + (1.0F - progressStart) * 58.0F), 0, 255);
        int gEnd = Mth.clamp((int)(184.0F + (1.0F - progressEnd) * 58.0F), 0, 255);
        int bStart = Mth.clamp((int)(76.0F + (1.0F - progressStart) * 70.0F), 0, 255);
        int bEnd = Mth.clamp((int)(76.0F + (1.0F - progressEnd) * 70.0F), 0, 255);

        consumer.addVertex(matrix, (float)sL.x, (float)sL.y, (float)sL.z).setColor(255, gStart, bStart, aStart);
        consumer.addVertex(matrix, (float)eL.x, (float)eL.y, (float)eL.z).setColor(255, gEnd, bEnd, aEnd);
        consumer.addVertex(matrix, (float)eR.x, (float)eR.y, (float)eR.z).setColor(255, gEnd, bEnd, aEnd);
        consumer.addVertex(matrix, (float)sR.x, (float)sR.y, (float)sR.z).setColor(255, gStart, bStart, aStart);

        consumer.addVertex(matrix, (float)sR.x, (float)sR.y, (float)sR.z).setColor(255, gStart, bStart, aStart);
        consumer.addVertex(matrix, (float)eR.x, (float)eR.y, (float)eR.z).setColor(255, gEnd, bEnd, aEnd);
        consumer.addVertex(matrix, (float)eL.x, (float)eL.y, (float)eL.z).setColor(255, gEnd, bEnd, aEnd);
        consumer.addVertex(matrix, (float)sL.x, (float)sL.y, (float)sL.z).setColor(255, gStart, bStart, aStart);
    }
}
