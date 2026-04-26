package com.jvn.gracebound.guidance;

import com.jvn.gracebound.config.GraceboundConfig;

public final class RuntimeGuidanceState {
    private static GuidanceMode mode = GuidanceMode.ON_DEATH;

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

    public static void resetToConfigMode() {
        mode = GraceboundConfig.guidanceMode;
    }
}
