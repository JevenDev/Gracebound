package com.jvn.gracebound.client;

import com.jvn.gracebound.config.GraceboundConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class GraceboundClientMessages {
    private static final int CROSS_DIMENSION_COOLDOWN_TICKS = 100;
    private static int nextCrossDimensionMessageTick;

    private GraceboundClientMessages() {
    }

    public static void crossDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!GraceboundConfig.showMessages || minecraft.player == null) {
            return;
        }

        int tick = minecraft.player.tickCount;
        if (tick >= nextCrossDimensionMessageTick) {
            minecraft.player.displayClientMessage(Component.translatable("message.gracebound.cross_dimension"), true);
            nextCrossDimensionMessageTick = tick + CROSS_DIMENSION_COOLDOWN_TICKS;
        }
    }
}
