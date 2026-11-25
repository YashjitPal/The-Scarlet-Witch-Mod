package com.yashjit.scarlet.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yashjit.scarlet.hex.Residents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A witch living in a Hex as a townsperson drinks from a bottle like anyone, not the way a witch does.
 */
@Mixin(Witch.class)
abstract class WitchMixin {

    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Witch;isAlive()Z"))
    private boolean scarlet$canBrew(Witch witch, Operation<Boolean> original) {
        return original.call(witch) && !com.yashjit.scarlet.magic.AgathaMagic.silenced(witch);
    }

    @Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
    private void scarlet$suppressPotion(CallbackInfo ci) {
        if (com.yashjit.scarlet.magic.AgathaMagic.silenced((Witch)(Object)this)) ci.cancel();
    }

    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private void scarlet$drinkLikeAnyone(Level level, @Nullable Entity except, double x, double y, double z, SoundEvent sound, SoundSource source,
                                        float volume, float pitch, Operation<Void> original) {
        Witch self = (Witch) (Object) this;
        if (!Residents.isResident(self) || !Residents.voice(self, sound, volume, pitch)) {
            original.call(level, except, x, y, z, sound, source, volume, pitch);
        }
    }
}
