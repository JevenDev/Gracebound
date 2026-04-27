package com.jvn.gracebound.guidance;

import com.jvn.gracebound.config.GraceboundConfig;
import com.jvn.gracebound.world.GraceboundGameRules;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

public final class GuidanceTargetResolver {
    private GuidanceTargetResolver() {
    }

    public static TargetResolution resolve(Player player) {
        return resolve(player, true);
    }

    public static TargetResolution resolveHeldOnly(Player player) {
        return resolve(player, false);
    }

    private static TargetResolution resolve(Player player, boolean includeDeathFallback) {
        if (includeDeathFallback && RuntimeGuidanceState.mode() == GuidanceMode.OFF) {
            return TargetResolution.none();
        }

        TargetResolution heldTarget = resolveHeldCompasses(player);
        if (heldTarget.target().isPresent() || heldTarget.crossDimensionTarget().isPresent()) {
            return heldTarget;
        }

        if (!includeDeathFallback
                || !GraceboundGameRules.showDeathGuidanceWithoutRecoveryCompassEnabled(player.level())) {
            return TargetResolution.none();
        }

        return RuntimeGuidanceState.defaultDeathGuidanceTarget()
                .map(pos -> inSameDimension(player, pos)
                        ? TargetResolution.found(new GuidanceTarget(pos, GuidanceTarget.Source.DEATH_GUIDANCE))
                        : TargetResolution.crossDimension(pos, GuidanceTarget.Source.DEATH_GUIDANCE))
                .orElseGet(TargetResolution::none);
    }

    private static TargetResolution resolveHeldCompasses(Player player) {
        if (GraceboundConfig.showLodestoneGuidance) {
            TargetResolution lodestone = resolveHands(player, GuidanceTarget.Source.LODESTONE_COMPASS);
            if (lodestone.target().isPresent() || lodestone.crossDimensionTarget().isPresent()) {
                return lodestone;
            }
        }

        if (GraceboundConfig.showRecoveryCompassGuidance) {
            TargetResolution recovery = resolveHands(player, GuidanceTarget.Source.RECOVERY_COMPASS);
            if (recovery.target().isPresent() || recovery.crossDimensionTarget().isPresent()) {
                return recovery;
            }
        }

        if (!GraceboundConfig.showRegularCompassGuidance) {
            return TargetResolution.none();
        }

        return resolveHands(player, GuidanceTarget.Source.REGULAR_COMPASS);
    }

    private static TargetResolution resolveHands(Player player, GuidanceTarget.Source source) {
        TargetResolution mainHand = resolveCompassStack(player, player.getItemInHand(InteractionHand.MAIN_HAND), source);
        return mainHand.target().isPresent() || mainHand.crossDimensionTarget().isPresent()
                ? mainHand
                : resolveCompassStack(player, player.getItemInHand(InteractionHand.OFF_HAND), source);
    }

    private static TargetResolution resolveCompassStack(Player player, ItemStack stack, GuidanceTarget.Source source) {
        if (source == GuidanceTarget.Source.LODESTONE_COMPASS) {
            return resolveLodestoneCompass(player, stack);
        }

        if (source == GuidanceTarget.Source.RECOVERY_COMPASS && stack.is(Items.RECOVERY_COMPASS)) {
            return player.getLastDeathLocation()
                    .map(pos -> inSameDimension(player, pos)
                            ? TargetResolution.found(new GuidanceTarget(pos, GuidanceTarget.Source.RECOVERY_COMPASS))
                            : TargetResolution.crossDimension(pos, GuidanceTarget.Source.RECOVERY_COMPASS))
                    .orElseGet(TargetResolution::none);
        }

        if (source == GuidanceTarget.Source.REGULAR_COMPASS && stack.is(Items.COMPASS) && !stack.has(DataComponents.LODESTONE_TRACKER)) {
            GlobalPos spawn = CompassItem.getSpawnPosition(player.level());
            if (spawn != null) {
                return inSameDimension(player, spawn)
                        ? TargetResolution.found(new GuidanceTarget(spawn, GuidanceTarget.Source.REGULAR_COMPASS))
                        : TargetResolution.crossDimension(spawn, GuidanceTarget.Source.REGULAR_COMPASS);
            }

            // Vanilla regular compasses only have a real spawn target in natural dimensions (Overworld).
            // In Nether/End they still represent an "across-dimension" limitation to world spawn.
            if (player.level().dimension() != Level.OVERWORLD) {
                return TargetResolution.crossDimension(
                        GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO),
                        GuidanceTarget.Source.REGULAR_COMPASS
                );
            }
        }

        return TargetResolution.none();
    }

    private static TargetResolution resolveLodestoneCompass(Player player, ItemStack stack) {
        if (!stack.is(Items.COMPASS)) {
            return TargetResolution.none();
        }

        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        Optional<GlobalPos> target = tracker == null ? Optional.empty() : tracker.target();
        return target.map(pos -> inSameDimension(player, pos)
                        ? TargetResolution.found(new GuidanceTarget(pos, GuidanceTarget.Source.LODESTONE_COMPASS))
                        : TargetResolution.crossDimension(pos, GuidanceTarget.Source.LODESTONE_COMPASS))
                .orElseGet(TargetResolution::none);
    }

    private static boolean inSameDimension(Player player, GlobalPos target) {
        Level level = player.level();
        return target.dimension() == level.dimension();
    }
}
