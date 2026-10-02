package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.hex.Residents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Hex's townspeople mean no one harm and no one means them any, they don't burn in the sitcom sun, and they turn
 * back once they find themselves outside.
 */
@Mixin(Mob.class)
abstract class MobMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void scarlet$keepThePeace(@Nullable LivingEntity target, CallbackInfo ci) {
        if (Residents.keepsPeace((Mob) (Object) this, target)) {
            ci.cancel();
        }
    }

    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void scarlet$noBurningInTheSitcomSun(CallbackInfoReturnable<Boolean> cir) {
        if (Residents.isResident((Mob) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void scarlet$stillInside(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (self.tickCount % 20 == 0 && self.level() instanceof ServerLevel level) {
            Residents.checkStillInside(level, self);
        }
    }
}
