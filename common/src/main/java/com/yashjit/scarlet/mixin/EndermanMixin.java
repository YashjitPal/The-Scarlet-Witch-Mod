package com.yashjit.scarlet.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yashjit.scarlet.hex.Residents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An enderman living in a Hex as a townsperson slips from place to place without a sound, and doesn't stare back with
 * its cry at whoever looks at it.
 */
@Mixin(Enderman.class)
abstract class EndermanMixin {

    @WrapOperation(method = "teleport(DDD)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private void scarlet$quietlyAway(Level level, @Nullable Entity except, double x, double y, double z, SoundEvent sound, SoundSource source,
                                     float volume, float pitch, Operation<Void> original) {
        if (!Residents.isResident((Enderman) (Object) this)) {
            original.call(level, except, x, y, z, sound, source, volume, pitch);
        }
    }

    @Inject(method = "setBeingStaredAt", at = @At("HEAD"), cancellable = true)
    private void scarlet$noStareBack(CallbackInfo ci) {
        if (Residents.isResident((Enderman) (Object) this)) {
            ci.cancel();
        }
    }
}
