package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.magic.AgathaMagic;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.monster.illager.SpellcasterIllager$SpellcasterUseSpellGoal")
abstract class IllagerSpellGoalMixin {
    @Shadow @Final private SpellcasterIllager this$0;
    @Shadow protected int attackWarmupDelay;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void scarlet$breakIncantation(CallbackInfo ci) {
        if (AgathaMagic.silenced(this$0)) {
            attackWarmupDelay = 0;
            ci.cancel();
        }
    }
}
