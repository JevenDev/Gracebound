package com.jvn.gracebound.guidance;

import java.util.Optional;
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
        TargetResolution heldTarget = resolveHeldCompasses(player);
        if (heldTarget.target().isPresent() || heldTarget.crossDimensionTarget().isPresent()) {
            return heldTarget;
        }

        if (RuntimeGuidanceState.mode() != GuidanceMode.OFF) {
            return RuntimeGuidanceState.defaultDeathGuidanceTarget()
                    .map(pos -> inSameDimension(player, pos)
                            ? TargetResolution.found(new GuidanceTarget(pos, GuidanceTarget.Source.DEATH_GUIDANCE))
                            : TargetResolution.crossDimension(pos))
                    .orElseGet(TargetResolution::none);
        }

        return TargetResolution.none();
    }

    private static TargetResolution resolveHeldCompasses(Player player) {
        TargetResolution mainHand = resolveCompassStack(player, player.getItemInHand(InteractionHand.MAIN_HAND));
        if (mainHand.target().isPresent() || mainHand.crossDimensionTarget().isPresent()) {
            return mainHand;
        }

        return resolveCompassStack(player, player.getItemInHand(InteractionHand.OFF_HAND));
    }

    private static TargetResolution resolveCompassStack(Player player, ItemStack stack) {
        TargetResolution lodestone = resolveLodestoneCompass(player, stack);
        if (lodestone.target().isPresent() || lodestone.crossDimensionTarget().isPresent()) {
            return lodestone;
        }

        if (stack.is(Items.RECOVERY_COMPASS)) {
            return player.getLastDeathLocation()
                    .map(pos -> inSameDimension(player, pos)
                            ? TargetResolution.found(new GuidanceTarget(pos, GuidanceTarget.Source.RECOVERY_COMPASS))
                            : TargetResolution.crossDimension(pos))
                    .orElseGet(TargetResolution::none);
        }

        if (stack.is(Items.COMPASS)) {
            GlobalPos spawn = CompassItem.getSpawnPosition(player.level());
            if (spawn != null) {
                return inSameDimension(player, spawn)
                        ? TargetResolution.found(new GuidanceTarget(spawn, GuidanceTarget.Source.REGULAR_COMPASS))
                        : TargetResolution.crossDimension(spawn);
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
                        : TargetResolution.crossDimension(pos))
                .orElseGet(TargetResolution::none);
    }

    private static boolean inSameDimension(Player player, GlobalPos target) {
        Level level = player.level();
        return target.dimension() == level.dimension();
    }
}
