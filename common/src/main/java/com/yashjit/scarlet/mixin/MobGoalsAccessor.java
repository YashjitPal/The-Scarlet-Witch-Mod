package com.yashjit.scarlet.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches a mob's goals, to give a Hex's townspeople their sitcom routines.
 */
@Mixin(Mob.class)
public interface MobGoalsAccessor {

    @Accessor("goalSelector")
    GoalSelector scarlet$goalSelector();
}
