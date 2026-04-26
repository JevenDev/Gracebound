package com.jvn.gracebound.guidance;

import net.minecraft.network.chat.Component;

public enum GuidanceMode {
    ON_DEATH("message.gracebound.mode.on_death"),
    ALWAYS("message.gracebound.mode.always"),
    OFF("message.gracebound.mode.off");

    private final String messageKey;

    GuidanceMode(String messageKey) {
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
}
