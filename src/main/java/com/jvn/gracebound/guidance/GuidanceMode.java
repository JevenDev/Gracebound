package com.jvn.gracebound.guidance;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

public enum GuidanceMode implements StringRepresentable {
    ON_DEATH("on_death", "message.gracebound.mode.on_death"),
    ALWAYS("always", "message.gracebound.mode.always"),
    OFF("off", "message.gracebound.mode.off");

    public static final Codec<GuidanceMode> CODEC = StringRepresentable.fromEnum(GuidanceMode::values);

    private final String serializedName;
    private final String messageKey;

    GuidanceMode(String serializedName, String messageKey) {
        this.serializedName = serializedName;
        this.messageKey = messageKey;
    }

    public GuidanceMode next() {
        return switch (this) {
            case ON_DEATH -> ALWAYS;
            case ALWAYS -> OFF;
            case OFF -> ON_DEATH;
        };
    }

    public Component displayMessage() {
        return Component.translatable(messageKey);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
