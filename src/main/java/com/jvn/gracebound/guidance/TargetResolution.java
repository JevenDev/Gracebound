package com.jvn.gracebound.guidance;

import java.util.Optional;
import net.minecraft.core.GlobalPos;

public record TargetResolution(Optional<GuidanceTarget> target, Optional<GlobalPos> crossDimensionTarget) {
    public static TargetResolution found(GuidanceTarget target) {
        return new TargetResolution(Optional.of(target), Optional.empty());
    }

    public static TargetResolution crossDimension(GlobalPos target) {
        return new TargetResolution(Optional.empty(), Optional.of(target));
    }

    public static TargetResolution none() {
        return new TargetResolution(Optional.empty(), Optional.empty());
    }
}
