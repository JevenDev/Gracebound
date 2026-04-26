package com.jvn.gracebound.guidance;

import com.jvn.gracebound.config.GraceboundConfig;
import java.util.Optional;
import net.minecraft.core.GlobalPos;

public final class RuntimeGuidanceState {
    private static GuidanceMode mode = GuidanceMode.ON_DEATH;
    private static Optional<GlobalPos> lastObservedDeathLocation = Optional.empty();
    private static Optional<GlobalPos> activeDeathGuidanceTarget = Optional.empty();
    private static int deathGuidanceTicksRemaining;

    private RuntimeGuidanceState() {
    }

    public static GuidanceMode mode() {
        return mode;
    }

    public static void setMode(GuidanceMode newMode) {
        mode = newMode;
    }

    public static GuidanceMode cycleMode() {
        mode = mode.next();
        return mode;
    }

    public static void clientTick(Optional<GlobalPos> currentDeathLocation) {
        currentDeathLocation.ifPresent(deathLocation -> {
            if (!lastObservedDeathLocation.equals(currentDeathLocation)) {
                activateDeathGuidance(deathLocation);
            }
        });

        lastObservedDeathLocation = currentDeathLocation;
        if (deathGuidanceTicksRemaining > 0) {
            deathGuidanceTicksRemaining--;
        }
    }

    public static Optional<GlobalPos> defaultDeathGuidanceTarget() {
        if (mode == GuidanceMode.ALWAYS) {
            return lastObservedDeathLocation;
        }

        if (mode == GuidanceMode.ON_DEATH && deathGuidanceTicksRemaining > 0) {
            return activeDeathGuidanceTarget;
        }

        return Optional.empty();
    }

    public static int deathGuidanceTicksRemaining() {
        return deathGuidanceTicksRemaining;
    }

    public static void resetToDefaultMode(GuidanceMode defaultMode) {
        mode = defaultMode;
    }

    private static void activateDeathGuidance(GlobalPos deathLocation) {
        activeDeathGuidanceTarget = Optional.of(deathLocation);
        deathGuidanceTicksRemaining = GraceboundConfig.visibleDurationTicks;
    }
}
