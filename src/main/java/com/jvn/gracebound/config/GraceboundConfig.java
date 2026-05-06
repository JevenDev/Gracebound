package com.jvn.gracebound.config;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.TranslatableEnum;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class GraceboundConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ENABLE_FADE = BUILDER
            .comment("Whether the guidance effect fades in and out.")
            .define("enableFade", true);

    private static final ModConfigSpec.IntValue FADE_IN_TICKS = BUILDER
            .comment("Ticks used to fade guidance in.")
            .defineInRange("fadeInTicks", 10, 0, 200);

    private static final ModConfigSpec.IntValue FADE_OUT_TICKS = BUILDER
            .comment("Ticks used to fade guidance out.")
            .defineInRange("fadeOutTicks", 20, 0, 200);

    private static final ModConfigSpec.DoubleValue BEAM_DENSITY = BUILDER
            .comment("Controls how many wispy strands are drawn in the guidance stream.")
            .defineInRange("beamDensity", 0.62D, 0.1D, 4.0D);

    private static final ModConfigSpec.DoubleValue BEAM_ORIGIN_OFFSET = BUILDER
            .comment("Forward offset from your eyes where the stream is anchored.")
            .defineInRange("beamOriginOffset", 0.0D, 0.0D, 2.0D);

    private static final ModConfigSpec.DoubleValue BEAM_START_DISTANCE = BUILDER
            .comment("Extra distance from the anchor before particles begin rendering.")
            .defineInRange("beamStartDistance", 0.0D, 0.0D, 3.0D);

    private static final ModConfigSpec.DoubleValue BEAM_VERTICAL_OFFSET = BUILDER
            .comment("Vertical offset from eye level where the stream originates. Negative values lower it toward torso height.")
            .defineInRange("beamVerticalOffset", -0.92D, -2.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue MAX_BEAM_DISTANCE = BUILDER
            .comment("Maximum visual length of the guidance stream in blocks.")
            .defineInRange("maxBeamDistance", 6.0D, 4.0D, 96.0D);

    private static final ModConfigSpec.EnumValue<TrailStyle> TRAIL_STYLE = BUILDER
            .comment("Visual style used for the guidance trail.")
            .defineEnum("trailStyle", TrailStyle.CLASSIC);

    private static final ModConfigSpec.BooleanValue USE_THIRD_PERSON_TRAIL_IN_FIRST_PERSON = BUILDER
            .comment("Whether first-person guidance uses the fuller third-person trail presentation instead of the unobtrusive first-person presentation.")
            .define("useThirdPersonTrailInFirstPerson", false);

    private static final ModConfigSpec.BooleanValue SHOW_MESSAGES = BUILDER
            .comment("Whether Gracebound shows small client-side guidance messages.")
            .define("showMessages", true);

    private static final ModConfigSpec.BooleanValue SHOW_LODESTONE_GUIDANCE = BUILDER
            .comment("Whether lodestone-compass guidance is rendered.")
            .define("showLodestoneGuidance", true);

    private static final ModConfigSpec.BooleanValue SHOW_RECOVERY_COMPASS_GUIDANCE = BUILDER
            .comment("Whether recovery-compass guidance is rendered.")
            .define("showRecoveryCompassGuidance", true);

    private static final ModConfigSpec.BooleanValue SHOW_REGULAR_COMPASS_GUIDANCE = BUILDER
            .comment("Whether regular-compass guidance (spawn guidance) is rendered.")
            .define("showRegularCompassGuidance", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean enableFade = true;
    public static int fadeInTicks = 10;
    public static int fadeOutTicks = 20;
    public static double beamDensity = 0.62D;
    public static double beamOriginOffset = 0.0D;
    public static double beamStartDistance = 0.0D;
    public static double beamVerticalOffset = -0.92D;
    public static double maxBeamDistance = 6.0D;
    public static TrailStyle trailStyle = TrailStyle.CLASSIC;
    public static boolean useThirdPersonTrailInFirstPerson = false;
    public static boolean showMessages = true;
    public static boolean showLodestoneGuidance = true;
    public static boolean showRecoveryCompassGuidance = true;
    public static boolean showRegularCompassGuidance = true;

    private GraceboundConfig() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(GraceboundConfig::onLoad);
    }

    private static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            bake();
        }
    }

    private static void bake() {
        enableFade = ENABLE_FADE.get();
        fadeInTicks = FADE_IN_TICKS.get();
        fadeOutTicks = FADE_OUT_TICKS.get();
        beamDensity = BEAM_DENSITY.get();
        beamOriginOffset = BEAM_ORIGIN_OFFSET.get();
        beamStartDistance = BEAM_START_DISTANCE.get();
        beamVerticalOffset = BEAM_VERTICAL_OFFSET.get();
        maxBeamDistance = MAX_BEAM_DISTANCE.get();
        trailStyle = TRAIL_STYLE.get();
        useThirdPersonTrailInFirstPerson = USE_THIRD_PERSON_TRAIL_IN_FIRST_PERSON.get();
        showMessages = SHOW_MESSAGES.get();
        showLodestoneGuidance = SHOW_LODESTONE_GUIDANCE.get();
        showRecoveryCompassGuidance = SHOW_RECOVERY_COMPASS_GUIDANCE.get();
        showRegularCompassGuidance = SHOW_REGULAR_COMPASS_GUIDANCE.get();
    }

    public enum TrailStyle implements TranslatableEnum {
        CLASSIC,
        ENCHANTED,
        AURORA,
        SIGIL;

        @Override
        public Component getTranslatedName() {
            return Component.translatable("gracebound.configuration.trailStyle." + name().toLowerCase());
        }
    }
}
