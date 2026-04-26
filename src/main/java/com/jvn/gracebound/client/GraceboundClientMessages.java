package com.jvn.gracebound.client;

import com.jvn.gracebound.config.GraceboundConfig;
import com.jvn.gracebound.guidance.TargetResolution;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class GraceboundClientMessages {
    private static final int CROSS_DIMENSION_COOLDOWN_TICKS = 100;
    private static int nextCrossDimensionMessageTick;
    private static boolean wasCrossDimensionActive;
    private static String lastCrossDimensionKey = "";

    private GraceboundClientMessages() {
    }

    public static void updateCrossDimension(TargetResolution resolution) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            clearState();
            return;
        }

        if (resolution.crossDimensionTarget().isEmpty()) {
            wasCrossDimensionActive = false;
            lastCrossDimensionKey = "";
            return;
        }

        int tick = minecraft.player.tickCount;
        String sourceKey = resolution.source().map(Enum::name).orElse("UNKNOWN");
        String dimensionKey = resolution.crossDimensionTarget().get().dimension().location().toString();
        String currentKey = sourceKey + "|" + dimensionKey;
        boolean sourceOrTargetChanged = !wasCrossDimensionActive || !lastCrossDimensionKey.equals(currentKey);

        if (GraceboundConfig.showMessages && (sourceOrTargetChanged || tick >= nextCrossDimensionMessageTick)) {
            minecraft.player.displayClientMessage(Component.translatable("message.gracebound.cross_dimension"), true);
            nextCrossDimensionMessageTick = tick + CROSS_DIMENSION_COOLDOWN_TICKS;
        }

        wasCrossDimensionActive = true;
        lastCrossDimensionKey = currentKey;
    }

    public static void clearState() {
        nextCrossDimensionMessageTick = 0;
        wasCrossDimensionActive = false;
        lastCrossDimensionKey = "";
    }
}
