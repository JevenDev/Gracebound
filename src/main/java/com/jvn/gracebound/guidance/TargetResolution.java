package com.jvn.gracebound.guidance;

import java.util.Optional;
import net.minecraft.core.GlobalPos;

public record TargetResolution(Optional<GuidanceTarget> target, Optional<GlobalPos> crossDimensionTarget, Optional<GuidanceTarget.Source> source) {
    public static TargetResolution found(GuidanceTarget target) {
        return new TargetResolution(Optional.of(target), Optional.empty(), Optional.of(target.source()));
    }

    public static TargetResolution crossDimension(GlobalPos target, GuidanceTarget.Source source) {
        return new TargetResolution(Optional.empty(), Optional.of(target), Optional.of(source));
    }

    public static TargetResolution none() {
        return new TargetResolution(Optional.empty(), Optional.empty(), Optional.empty());
    }
}
