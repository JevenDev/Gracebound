package com.jvn.gracebound.network;

import com.jvn.gracebound.guidance.GuidanceMode;
import com.jvn.gracebound.world.GraceboundGameRules;

public final class GraceboundServerRuntimeSettings {
    private static boolean showOthersGuidance = true;
    private static GuidanceMode defaultGuidanceMode = GuidanceMode.ON_DEATH;
    private static boolean showDeathGuidanceWithoutRecoveryCompass = true;
    private static boolean wipeDeathLocationOnArrival = true;

    private GraceboundServerRuntimeSettings() {
    }

    public static boolean showOthersGuidance() {
        return showOthersGuidance;
    }

    public static GuidanceMode defaultGuidanceMode() {
        return defaultGuidanceMode;
    }

    public static boolean showDeathGuidanceWithoutRecoveryCompass() {
        return showDeathGuidanceWithoutRecoveryCompass;
    }

    public static boolean wipeDeathLocationOnArrival() {
        return wipeDeathLocationOnArrival;
    }

    public static void updateFromServerSync(
            boolean syncedShowOthersGuidance,
            int syncedDefaultGuidanceMode,
            boolean syncedShowDeathGuidanceWithoutRecoveryCompass,
            boolean syncedWipeDeathLocationOnArrival
    ) {
        showOthersGuidance = syncedShowOthersGuidance;
        defaultGuidanceMode = GraceboundGameRules.decodeDefaultGuidanceMode(syncedDefaultGuidanceMode);
        showDeathGuidanceWithoutRecoveryCompass = syncedShowDeathGuidanceWithoutRecoveryCompass;
        wipeDeathLocationOnArrival = syncedWipeDeathLocationOnArrival;
    }

    public static void clear() {
        showOthersGuidance = true;
        defaultGuidanceMode = GuidanceMode.ON_DEATH;
        showDeathGuidanceWithoutRecoveryCompass = true;
        wipeDeathLocationOnArrival = true;
    }
}
