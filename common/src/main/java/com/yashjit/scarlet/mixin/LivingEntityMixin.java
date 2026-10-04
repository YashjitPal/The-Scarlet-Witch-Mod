package com.yashjit.scarlet.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yashjit.scarlet.hex.HexTape;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A creature killed inside a standing Hex goes on its tape whole, with what it drops, to get back up in a rewind. One
 * brought back after someone picked up what it dropped has nothing left to drop.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {

    @WrapMethod(method = "die")
    private void scarlet$tapeDeath(DamageSource source, Operation<Void> original) {
        HexTape.Death death = HexTape.dying((LivingEntity) (Object) this);
        try {
            original.call(source);
        } finally {
            HexTape.died(death);
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void scarlet$nothingLeftToDrop(ServerLevel level, DamageSource source, CallbackInfo ci) {
        if (((LivingEntity) (Object) this).entityTags().contains(HexTape.SPENT_TAG)) {
            ci.cancel();
        }
    }
}
