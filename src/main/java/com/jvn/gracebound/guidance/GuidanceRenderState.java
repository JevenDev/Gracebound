package com.jvn.gracebound.guidance;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GuidanceRenderState {
    private static final Map<UUID, Boolean> remoteVisibility = new HashMap<>();
    private static boolean localVisible = true;

    private GuidanceRenderState() {
    }

    public static boolean isLocalVisible() {
        return localVisible;
    }

    public static boolean toggleLocalVisible() {
        localVisible = !localVisible;
        return localVisible;
    }

    public static void setLocalVisible(boolean visible) {
        localVisible = visible;
    }

    public static boolean isRemoteVisible(UUID playerId) {
        return remoteVisibility.getOrDefault(playerId, true);
    }

    public static void setRemoteVisible(UUID playerId, boolean visible) {
        remoteVisibility.put(playerId, visible);
    }

    public static void resetClientState() {
        localVisible = true;
        remoteVisibility.clear();
    }
}
