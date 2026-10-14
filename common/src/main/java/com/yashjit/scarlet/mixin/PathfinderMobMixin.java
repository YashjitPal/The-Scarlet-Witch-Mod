package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.hex.Residents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nothing comes out of the dark into a Hex, as {@link MobMixin} has it: monsters ask here, not there, whether they may
 * turn up where they would.
 */
@Mixin(PathfinderMob.class)
abstract class PathfinderMobMixin {

    @Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
    private void scarlet$notOutOfTheDark(LevelAccessor level, EntitySpawnReason reason, CallbackInfoReturnable<Boolean> cir) {
        if (Residents.keptFromSpawning((Mob) (Object) this, level, reason)) {
            cir.setReturnValue(false);
        }
    }
}
