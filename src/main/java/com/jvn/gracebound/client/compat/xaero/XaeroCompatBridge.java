package com.jvn.gracebound.client.compat.xaero;

import com.jvn.gracebound.Gracebound;
import com.jvn.gracebound.guidance.GuidanceTarget;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

public final class XaeroCompatBridge {
    private static final boolean XAERO_MINIMAP_LOADED = ModList.get().isLoaded("xaerominimap");
    private static boolean loggedFailure;

    private XaeroCompatBridge() {
    }

    public static void clientTick(Player player, Optional<GuidanceTarget> target) {
        if (!XAERO_MINIMAP_LOADED) {
            return;
        }

        try {
            XaeroCompatIntegration.clientTick(player, target);
        } catch (NoClassDefFoundError | ExceptionInInitializerError exception) {
            if (!loggedFailure) {
                loggedFailure = true;
                Gracebound.LOGGER.warn("Gracebound Xaero compat disabled due to missing classes.", exception);
            }
        }
    }

    public static void clear() {
        if (!XAERO_MINIMAP_LOADED) {
            return;
        }

        try {
            XaeroCompatIntegration.clear();
        } catch (NoClassDefFoundError | ExceptionInInitializerError exception) {
            if (!loggedFailure) {
                loggedFailure = true;
                Gracebound.LOGGER.warn("Gracebound Xaero compat disabled due to missing classes.", exception);
            }
        }
    }
}
