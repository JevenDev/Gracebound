package com.jvn.gracebound.config;

import com.jvn.gracebound.Gracebound;
import com.jvn.gracebound.guidance.GuidanceMode;
import com.jvn.gracebound.guidance.RuntimeGuidanceState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class GraceboundConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.EnumValue<GuidanceMode> GUIDANCE_MODE = BUILDER
            .comment("Default guidance behavior.")
            .defineEnum("guidanceMode", GuidanceMode.ON_DEATH);

    private static final ModConfigSpec.BooleanValue ENABLE_FADE = BUILDER
            .comment("Whether the guidance effect fades in and out.")
            .define("enableFade", true);

    private static final ModConfigSpec.IntValue FADE_IN_TICKS = BUILDER
            .comment("Ticks used to fade guidance in.")
            .defineInRange("fadeInTicks", 10, 0, 200);

    private static final ModConfigSpec.IntValue VISIBLE_DURATION_TICKS = BUILDER
            .comment("Ticks death guidance remains fully visible before fading.")
            .defineInRange("visibleDurationTicks", 120, 1, 20 * 60 * 10);

    private static final ModConfigSpec.IntValue FADE_OUT_TICKS = BUILDER
            .comment("Ticks used to fade guidance out.")
            .defineInRange("fadeOutTicks", 20, 0, 200);

    private static final ModConfigSpec.DoubleValue BEAM_DENSITY = BUILDER
            .comment("Approximate particles per block along the short guidance stream.")
            .defineInRange("beamDensity", 0.85D, 0.1D, 4.0D);

    private static final ModConfigSpec.DoubleValue MAX_BEAM_DISTANCE = BUILDER
            .comment("Maximum visual length of the guidance stream in blocks.")
            .defineInRange("maxBeamDistance", 32.0D, 4.0D, 96.0D);

    private static final ModConfigSpec.BooleanValue SHOW_MESSAGES = BUILDER
            .comment("Whether Gracebound shows small client-side guidance messages.")
            .define("showMessages", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static GuidanceMode guidanceMode = GuidanceMode.ON_DEATH;
    public static boolean enableFade = true;
    public static int fadeInTicks = 10;
    public static int visibleDurationTicks = 120;
    public static int fadeOutTicks = 20;
    public static double beamDensity = 0.85D;
    public static double maxBeamDistance = 32.0D;
    public static boolean showMessages = true;

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
        guidanceMode = GUIDANCE_MODE.get();
        enableFade = ENABLE_FADE.get();
        fadeInTicks = FADE_IN_TICKS.get();
        visibleDurationTicks = VISIBLE_DURATION_TICKS.get();
        fadeOutTicks = FADE_OUT_TICKS.get();
        beamDensity = BEAM_DENSITY.get();
        maxBeamDistance = MAX_BEAM_DISTANCE.get();
        showMessages = SHOW_MESSAGES.get();
        RuntimeGuidanceState.resetToConfigMode();
    }
}
