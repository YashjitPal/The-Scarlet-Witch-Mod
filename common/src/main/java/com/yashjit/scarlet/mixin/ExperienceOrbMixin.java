package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.hex.HexTape;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Experience let fall inside a standing Hex is counted on its tape against what let it fall, merged into another orb or
 * not, for a rewind to take back.
 */
@Mixin(ExperienceOrb.class)
abstract class ExperienceOrbMixin {

    @Inject(method = "awardWithDirection", at = @At("HEAD"))
    private static void scarlet$tapeExperience(ServerLevel level, Vec3 pos, Vec3 roughDirection, int amount, CallbackInfo ci) {
        HexTape.experience(level, pos, amount);
    }
}
