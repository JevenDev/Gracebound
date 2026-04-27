package com.jvn.gracebound.world;

import com.jvn.gracebound.guidance.GuidanceMode;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

public final class GraceboundGameRules {
    public static final int DEFAULT_GUIDANCE_MODE_ON_DEATH = 0;
    public static final int DEFAULT_GUIDANCE_MODE_ALWAYS = 1;
    public static final int DEFAULT_GUIDANCE_MODE_OFF = 2;

    public static final GameRules.Key<GameRules.BooleanValue> SHOW_OTHERS_GUIDANCE = GameRules.register(
            "graceboundShowOthersGuidance",
            GameRules.Category.PLAYER,
            GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.IntegerValue> DEFAULT_GUIDANCE_MODE = GameRules.register(
            "graceboundDefaultGuidanceMode",
            GameRules.Category.PLAYER,
            GameRules.IntegerValue.create(DEFAULT_GUIDANCE_MODE_ON_DEATH, (server, value) -> {
                int normalized = normalizeDefaultGuidanceRaw(value.get());
                if (normalized != value.get()) {
                    value.set(normalized, server);
                }
            })
    );
    public static final GameRules.Key<GameRules.BooleanValue> SHOW_DEATH_GUIDANCE_WITHOUT_RECOVERY_COMPASS = GameRules.register(
            "graceboundShowDeathGuidanceWithoutRecoveryCompass",
            GameRules.Category.PLAYER,
            GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> WIPE_DEATH_LOCATION_ON_ARRIVAL = GameRules.register(
            "graceboundWipeDeathLocationOnArrival",
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
        return decodeDefaultGuidanceMode(level.getGameRules().getInt(DEFAULT_GUIDANCE_MODE));
    }

    public static GuidanceMode decodeDefaultGuidanceMode(int raw) {
        int normalized = normalizeDefaultGuidanceRaw(raw);
        return switch (normalized) {
            case DEFAULT_GUIDANCE_MODE_ALWAYS -> GuidanceMode.ALWAYS;
            case DEFAULT_GUIDANCE_MODE_OFF -> GuidanceMode.OFF;
            default -> GuidanceMode.ON_DEATH;
        };
    }

    public static int encodeDefaultGuidanceMode(GuidanceMode mode) {
        return switch (mode) {
            case ALWAYS -> DEFAULT_GUIDANCE_MODE_ALWAYS;
            case OFF -> DEFAULT_GUIDANCE_MODE_OFF;
            case ON_DEATH -> DEFAULT_GUIDANCE_MODE_ON_DEATH;
        };
    }

    public static int normalizeDefaultGuidanceRaw(int raw) {
        return switch (raw) {
            case DEFAULT_GUIDANCE_MODE_ALWAYS, DEFAULT_GUIDANCE_MODE_OFF -> raw;
            default -> DEFAULT_GUIDANCE_MODE_ON_DEATH;
        };
    }

    public static boolean showDeathGuidanceWithoutRecoveryCompassEnabled(Level level) {
        return level.getGameRules().getBoolean(SHOW_DEATH_GUIDANCE_WITHOUT_RECOVERY_COMPASS);
    }

    public static boolean wipeDeathLocationOnArrivalEnabled(Level level) {
        return level.getGameRules().getBoolean(WIPE_DEATH_LOCATION_ON_ARRIVAL);
    }
}
