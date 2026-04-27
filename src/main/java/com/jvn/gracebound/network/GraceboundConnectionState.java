package com.jvn.gracebound.network;

public final class GraceboundConnectionState {
    private static volatile boolean serverHasGracebound;

    private GraceboundConnectionState() {
    }

    public static boolean serverHasGracebound() {
        return serverHasGracebound;
    }

    public static boolean isServerEnhanced() {
        return serverHasGracebound();
    }

    public static boolean isClientOnlyFallback() {
        return !serverHasGracebound();
    }

    public static void setServerHasGracebound(boolean value) {
        serverHasGracebound = value;
    }

    public static void clear() {
        serverHasGracebound = false;
    }
}
