package com.jvn.gracebound.command;

import com.jvn.gracebound.Gracebound;
import com.jvn.gracebound.guidance.GuidanceMode;
import com.jvn.gracebound.world.GraceboundGameRules;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Gracebound.MOD_ID)
public final class GraceboundGameRuleCommandCompat {
    private static final String GAMERULE_COMMAND = "gamerule";
    private static final String WIPE_DEATH_LOCATION_ON_ARRIVAL_ALIAS = "wipeDeathLocationOnArrival";

    private GraceboundGameRuleCommandCompat() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandNode<CommandSourceStack> gameruleNode = event.getDispatcher().getRoot().getChild(GAMERULE_COMMAND);
        if (gameruleNode == null) {
            return;
        }

        gameruleNode.addChild(
                Commands.literal(GraceboundGameRules.DEFAULT_GUIDANCE_MODE.getId())
                        .executes(context -> queryDefaultGuidanceMode(context.getSource()))
                        .then(modeLiteral(GuidanceMode.ON_DEATH))
                        .then(modeLiteral(GuidanceMode.ALWAYS))
                        .then(modeLiteral(GuidanceMode.OFF))
                        .build()
        );
        gameruleNode.addChild(
                Commands.literal(WIPE_DEATH_LOCATION_ON_ARRIVAL_ALIAS)
                        .executes(context -> queryWipeDeathLocationOnArrival(context.getSource()))
                        .then(Commands.literal("true").executes(context -> setWipeDeathLocationOnArrival(context.getSource(), true)))
                        .then(Commands.literal("false").executes(context -> setWipeDeathLocationOnArrival(context.getSource(), false)))
                        .build()
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> modeLiteral(GuidanceMode mode) {
        return Commands.literal(mode.getSerializedName())
                .executes(context -> setDefaultGuidanceMode(context.getSource(), mode));
    }

    private static int setDefaultGuidanceMode(CommandSourceStack source, GuidanceMode mode) {
        GameRules.IntegerValue ruleValue = source.getServer().getGameRules().getRule(GraceboundGameRules.DEFAULT_GUIDANCE_MODE);
        int encoded = GraceboundGameRules.encodeDefaultGuidanceMode(mode);
        ruleValue.set(encoded, source.getServer());
        source.sendSuccess(
                () -> Component.translatable("commands.gamerule.set", GraceboundGameRules.DEFAULT_GUIDANCE_MODE.getId(), mode.getSerializedName()),
                true
        );
        return encoded;
    }

    private static int queryDefaultGuidanceMode(CommandSourceStack source) {
        GuidanceMode mode = GraceboundGameRules.decodeDefaultGuidanceMode(
                source.getServer().getGameRules().getInt(GraceboundGameRules.DEFAULT_GUIDANCE_MODE)
        );
        source.sendSuccess(
                () -> Component.translatable("commands.gamerule.query", GraceboundGameRules.DEFAULT_GUIDANCE_MODE.getId(), mode.getSerializedName()),
                false
        );
        return GraceboundGameRules.encodeDefaultGuidanceMode(mode);
    }

    private static int setWipeDeathLocationOnArrival(CommandSourceStack source, boolean enabled) {
        GameRules.BooleanValue ruleValue = source.getServer().getGameRules().getRule(GraceboundGameRules.WIPE_DEATH_LOCATION_ON_ARRIVAL);
        ruleValue.set(enabled, source.getServer());
        source.sendSuccess(
                () -> Component.translatable("commands.gamerule.set", WIPE_DEATH_LOCATION_ON_ARRIVAL_ALIAS, Boolean.toString(enabled)),
                true
        );
        return enabled ? 1 : 0;
    }

    private static int queryWipeDeathLocationOnArrival(CommandSourceStack source) {
        boolean enabled = source.getServer().getGameRules().getBoolean(GraceboundGameRules.WIPE_DEATH_LOCATION_ON_ARRIVAL);
        source.sendSuccess(
                () -> Component.translatable("commands.gamerule.query", WIPE_DEATH_LOCATION_ON_ARRIVAL_ALIAS, Boolean.toString(enabled)),
                false
        );
        return enabled ? 1 : 0;
    }
}
