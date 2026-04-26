package com.jvn.gracebound.client;

import com.jvn.gracebound.config.GraceboundConfig;
import com.jvn.gracebound.guidance.GuidanceTarget;
import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
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
        int count = Math.max(1, (int)Math.ceil(distance * GraceboundConfig.beamDensity * intensity));
        RandomSource random = player.getRandom();
        Vec3 right = forward.cross(UP);
        if (right.lengthSqr() < 0.0001D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 lift = right.cross(forward).normalize();
        Vec3 origin = eye.add(forward.scale(0.75D)).add(0.0D, -0.18D, 0.0D);

        for (int i = 0; i < count; i++) {
            double progress = ((double)i + random.nextDouble()) / count;
            double taper = 1.0D - progress;
            double travel = 1.2D + progress * Math.max(0.0D, distance - 1.2D);
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
}
