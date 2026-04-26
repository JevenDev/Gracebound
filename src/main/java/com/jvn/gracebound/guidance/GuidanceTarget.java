package com.jvn.gracebound.guidance;

import net.minecraft.core.GlobalPos;

public record GuidanceTarget(GlobalPos pos, Source source) {
    public enum Source {
        LODESTONE_COMPASS,
        RECOVERY_COMPASS,
        REGULAR_COMPASS,
        DEATH_GUIDANCE
    }
}
