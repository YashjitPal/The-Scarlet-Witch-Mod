package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.hex.HexEjection;
import com.yashjit.scarlet.hex.HexRipples;
import com.yashjit.scarlet.hex.Residents;
import com.yashjit.scarlet.magic.MindControl;
import com.yashjit.scarlet.magic.RuneTraps;
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
 * back once they find themselves outside. A creature held with Mind Control does nothing of its own accord, and one
 * set free stays loyal for a while. One bound by a Rune Trap only strains against it.
 */
@Mixin(Mob.class)
abstract class MobMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void scarlet$keepThePeace(@Nullable LivingEntity target, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (Residents.keepsPeace(self, target) || MindControl.keepsFaith(self, target)) {
            ci.cancel();
        }
    }

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void scarlet$heldMind(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (MindControl.steers(self)) {
            MindControl.steer(self);
            ci.cancel();
        } else if (RuneTraps.binds(self)) {
            RuneTraps.strain(self);
            ci.cancel();
        } else if (HexEjection.carries(self)) {
            ci.cancel();
        }
    }

    /**
     * A creature someone is steering from inside never wanders off out of the world, however far it is from anyone.
     */
    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void scarlet$staysWhileSteered(CallbackInfo ci) {
        if (MindControl.steers((Mob) (Object) this)) {
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
        if (self.level() instanceof ServerLevel level) {
            HexRipples.watch(self);
            if (self.tickCount % 20 == 0) {
                Residents.checkStillInside(level, self);
            }
        }
    }
}
