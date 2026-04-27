package com.jvn.gracebound.guidance;

import com.jvn.gracebound.world.GraceboundGameRules;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class RuntimeGuidanceState {
    private static final double ARRIVAL_RADIUS_BLOCKS = 2.5D;
    private static final double ARRIVAL_RADIUS_SQUARED = ARRIVAL_RADIUS_BLOCKS * ARRIVAL_RADIUS_BLOCKS;

    private static GuidanceMode mode = GuidanceMode.ON_DEATH;
    private static Optional<GlobalPos> lastObservedDeathLocation = Optional.empty();
    private static Optional<GlobalPos> activeDeathGuidanceTarget = Optional.empty();
    private static int lastObservedPlayerEntityId = Integer.MIN_VALUE;
    private static boolean wipeDeathLocationRequestPending;

    private RuntimeGuidanceState() {
    }

    public static GuidanceMode mode() {
        return mode;
    }

    public static void setMode(GuidanceMode newMode) {
        mode = newMode;
        if (mode == GuidanceMode.ON_DEATH) {
            lastObservedDeathLocation.ifPresent(RuntimeGuidanceState::activateDeathGuidance);
        }
    }

    public static void clientTick(Player player, Optional<GlobalPos> currentDeathLocation) {
        if (currentDeathLocation.isEmpty() && lastObservedDeathLocation.isPresent()) {
            activeDeathGuidanceTarget = Optional.empty();
            wipeDeathLocationRequestPending = false;
        }

        boolean playerEntityChanged = lastObservedPlayerEntityId != player.getId();
        if (playerEntityChanged && mode == GuidanceMode.ON_DEATH) {
            currentDeathLocation.ifPresent(RuntimeGuidanceState::activateDeathGuidance);
        }

        currentDeathLocation.ifPresent(deathLocation -> {
            if (!lastObservedDeathLocation.equals(currentDeathLocation)) {
                activateDeathGuidance(deathLocation);
                wipeDeathLocationRequestPending = false;
            }
        });

        if (mode == GuidanceMode.ON_DEATH
                && GraceboundGameRules.wipeDeathLocationOnArrivalEnabled(player.level())
                && activeDeathGuidanceTarget.isPresent()
                && !wipeDeathLocationRequestPending) {
            GlobalPos target = activeDeathGuidanceTarget.get();
            if (target.dimension() == player.level().dimension() && hasArrivedAtDeathLocation(player, target)) {
                activeDeathGuidanceTarget = Optional.empty();
                wipeDeathLocationRequestPending = true;
            }
        }

        lastObservedDeathLocation = currentDeathLocation;
        lastObservedPlayerEntityId = player.getId();
    }

    public static Optional<GlobalPos> defaultDeathGuidanceTarget() {
        if (mode == GuidanceMode.ALWAYS) {
            return lastObservedDeathLocation;
        }

        if (mode == GuidanceMode.ON_DEATH) {
            return activeDeathGuidanceTarget;
        }

        return Optional.empty();
    }

    public static void resetToDefaultMode(GuidanceMode defaultMode) {
        setMode(defaultMode);
    }

    public static void clearClientSession() {
        lastObservedDeathLocation = Optional.empty();
        activeDeathGuidanceTarget = Optional.empty();
        lastObservedPlayerEntityId = Integer.MIN_VALUE;
        wipeDeathLocationRequestPending = false;
    }

    public static boolean consumeWipeDeathLocationRequest() {
        if (!wipeDeathLocationRequestPending) {
            return false;
        }
        wipeDeathLocationRequestPending = false;
        return true;
    }

    private static void activateDeathGuidance(GlobalPos deathLocation) {
        activeDeathGuidanceTarget = Optional.of(deathLocation);
    }

    private static boolean hasArrivedAtDeathLocation(Player player, GlobalPos target) {
        Vec3 playerPos = player.position();
        Vec3 targetPos = Vec3.atCenterOf(target.pos());
        double dx = playerPos.x - targetPos.x;
        double dz = playerPos.z - targetPos.z;
        return dx * dx + dz * dz <= ARRIVAL_RADIUS_SQUARED;
    }
}
