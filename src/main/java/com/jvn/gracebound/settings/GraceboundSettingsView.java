package com.jvn.gracebound.settings;

import com.jvn.gracebound.guidance.GuidanceMode;
import com.jvn.gracebound.network.GraceboundConnectionState;
import com.jvn.gracebound.network.GraceboundServerRuntimeSettings;

public final class GraceboundSettingsView {
    private static final boolean FALLBACK_SHOW_OTHERS_GUIDANCE = false;
    private static final GuidanceMode FALLBACK_DEFAULT_GUIDANCE_MODE = GuidanceMode.ON_DEATH;
    private static final boolean FALLBACK_SHOW_DEATH_GUIDANCE_WITHOUT_RECOVERY_COMPASS = true;
    private static final boolean FALLBACK_WIPE_DEATH_LOCATION_ON_ARRIVAL = false;

    private GraceboundSettingsView() {
    }

    public static boolean showOthersGuidanceEnabled() {
        if (GraceboundConnectionState.isServerEnhanced()) {
            return GraceboundServerRuntimeSettings.showOthersGuidance();
        }
        return FALLBACK_SHOW_OTHERS_GUIDANCE;
    }

    public static GuidanceMode defaultGuidanceMode() {
        if (GraceboundConnectionState.isServerEnhanced()) {
            return GraceboundServerRuntimeSettings.defaultGuidanceMode();
        }
        return FALLBACK_DEFAULT_GUIDANCE_MODE;
    }

    public static boolean showDeathGuidanceWithoutRecoveryCompassEnabled() {
        if (GraceboundConnectionState.isServerEnhanced()) {
            return GraceboundServerRuntimeSettings.showDeathGuidanceWithoutRecoveryCompass();
        }
        return FALLBACK_SHOW_DEATH_GUIDANCE_WITHOUT_RECOVERY_COMPASS;
    }

    public static boolean wipeDeathLocationOnArrivalEnabled() {
        if (GraceboundConnectionState.isServerEnhanced()) {
            return GraceboundServerRuntimeSettings.wipeDeathLocationOnArrival();
        }
        return FALLBACK_WIPE_DEATH_LOCATION_ON_ARRIVAL;
    }
}
