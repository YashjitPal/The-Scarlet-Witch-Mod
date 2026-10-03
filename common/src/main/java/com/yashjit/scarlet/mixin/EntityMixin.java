package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.hex.Residents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Hex's townspeople sound like people, not the monsters they are underneath.
 */
@Mixin(Entity.class)
abstract class EntityMixin {

    @Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true)
    private void scarlet$soundLikePeople(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof Mob && !self.level().isClientSide() && Residents.isResident(self) && Residents.voice(self, sound, volume, pitch)) {
            ci.cancel();
        }
    }
}
