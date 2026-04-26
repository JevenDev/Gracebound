package com.jvn.gracebound.world;

import com.jvn.gracebound.guidance.GuidanceMode;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

public final class GraceboundGameRules {
    public static final GameRules.Key<GameRules.BooleanValue> SHOW_OTHERS_GUIDANCE = GameRules.register(
            "graceboundShowOthersGuidance",
            GameRules.Category.PLAYER,
            GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.IntegerValue> DEFAULT_GUIDANCE_MODE = GameRules.register(
            "graceboundDefaultGuidanceMode",
            GameRules.Category.PLAYER,
            GameRules.IntegerValue.create(0)
    );
    public static final GameRules.Key<GameRules.BooleanValue> SHOW_DEATH_GUIDANCE_WITHOUT_RECOVERY_COMPASS = GameRules.register(
            "graceboundShowDeathGuidanceWithoutRecoveryCompass",
            GameRules.Category.PLAYER,
            GameRules.BooleanValue.create(true)
    );

    private GraceboundGameRules() {
    }

    public static void bootstrap() {
        // No-op: invoking this method ensures this class is loaded early and registers gamerules.
    }

    public static boolean showOthersGuidanceEnabled(Level level) {
        return level.getGameRules().getBoolean(SHOW_OTHERS_GUIDANCE);
    }

    public static GuidanceMode defaultGuidanceMode(Level level) {
        int raw = level.getGameRules().getInt(DEFAULT_GUIDANCE_MODE);
        return switch (raw) {
            case 1 -> GuidanceMode.ALWAYS;
            case 2 -> GuidanceMode.OFF;
            default -> GuidanceMode.ON_DEATH;
        };
    }

    public static boolean showDeathGuidanceWithoutRecoveryCompassEnabled(Level level) {
        return level.getGameRules().getBoolean(SHOW_DEATH_GUIDANCE_WITHOUT_RECOVERY_COMPASS);
    }
}
