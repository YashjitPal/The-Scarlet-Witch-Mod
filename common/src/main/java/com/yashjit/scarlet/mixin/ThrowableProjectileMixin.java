package com.yashjit.scarlet.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.yashjit.scarlet.entity.ChaosBolt;
import com.yashjit.scarlet.magic.AgathaMagic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrowableProjectile.class)
abstract class ThrowableProjectileMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ThrowableProjectile;setPos(Lnet/minecraft/world/phys/Vec3;)V"), cancellable = true)
    private void scarlet$extinguish(CallbackInfo ci, @Local(ordinal = 0) Vec3 destination) {
        var shot = (ThrowableProjectile)(Object)this;
        if (!(shot instanceof ChaosBolt || shot instanceof AbstractThrownPotion) || !(shot.level() instanceof ServerLevel level)) return;
        Vec3 rim = AgathaMagic.intercept(level, shot.getOwner(), shot.position(), destination);
        if (rim != null) {
            shot.setPos(rim);
            shot.discard();
            ci.cancel();
        }
    }
}
