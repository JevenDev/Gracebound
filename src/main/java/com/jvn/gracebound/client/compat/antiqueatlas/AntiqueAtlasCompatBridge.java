package com.jvn.gracebound.client.compat.antiqueatlas;

import com.jvn.gracebound.Gracebound;
import com.jvn.gracebound.guidance.GuidanceTarget;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

public final class AntiqueAtlasCompatBridge {
    private static final boolean ANTIQUE_ATLAS_LOADED = ModList.get().isLoaded("antique_atlas");
    private static boolean loggedFailure;

    private AntiqueAtlasCompatBridge() {
    }

    public static void clientTick(Player player, Optional<GuidanceTarget> target) {
        if (!ANTIQUE_ATLAS_LOADED) {
            return;
        }

        try {
            AntiqueAtlasCompatIntegration.clientTick(player, target);
        } catch (NoClassDefFoundError | ExceptionInInitializerError | RuntimeException exception) {
            if (!loggedFailure) {
                loggedFailure = true;
                Gracebound.LOGGER.warn("Gracebound Antique Atlas compat disabled due to missing classes.", exception);
            }
        }
    }

    public static void clear() {
        if (!ANTIQUE_ATLAS_LOADED) {
            return;
        }

        try {
            AntiqueAtlasCompatIntegration.clear();
        } catch (NoClassDefFoundError | ExceptionInInitializerError | RuntimeException exception) {
            if (!loggedFailure) {
                loggedFailure = true;
                Gracebound.LOGGER.warn("Gracebound Antique Atlas compat disabled due to missing classes.", exception);
            }
        }
    }
}
