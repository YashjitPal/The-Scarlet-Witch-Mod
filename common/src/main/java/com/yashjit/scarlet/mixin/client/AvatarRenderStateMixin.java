package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.anim.CastPoseState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
abstract class AvatarRenderStateMixin implements CastPoseState {

    @Unique
    private float scarlet$rightStrike;

    @Unique
    private float scarlet$leftStrike;

    @Unique
    private float scarlet$shield;

    @Unique
    private float scarlet$recoil;

    @Unique
    private float scarlet$levitate;

    @Unique
    private float scarlet$lean;

    @Unique
    private float scarlet$burst;

    @Unique
    private float scarlet$gather;

    @Unique
    private float scarlet$hold;

    @Unique
    private float scarlet$tear;

    @Unique
    private float scarlet$spread;

    @Unique
    private float scarlet$found;

    @Unique
    private float scarlet$control;

    @Unique
    private float scarlet$beam;

    @Unique
    private boolean scarlet$beamRight;

    @Unique
    private float scarlet$raise;

    @Unique
    private float scarlet$read;

    @Unique
    private float scarlet$sit;

    @Unique
    private float scarlet$lift;

    @Unique
    private float scarlet$meditationOffset;

    @Override
    public float scarlet$sit() {
        return scarlet$sit;
    }

    @Override
    public float scarlet$lift() {
        return scarlet$lift;
    }

    @Override
    public float scarlet$meditationOffset() {
        return scarlet$meditationOffset;
    }

    @Override
    public void scarlet$setMeditation(float sit, float lift, float offset) {
        scarlet$sit = sit;
        scarlet$lift = lift;
        scarlet$meditationOffset = offset;
    }

    @Override
    public float scarlet$read() {
        return scarlet$read;
    }

    @Override
    public void scarlet$setRead(float read) {
        scarlet$read = read;
    }

    @Override
    public float scarlet$beam() {
        return scarlet$beam;
    }

    @Override
    public boolean scarlet$beamRight() {
        return scarlet$beamRight;
    }

    @Override
    public float scarlet$raise() {
        return scarlet$raise;
    }

    @Override
    public void scarlet$setBeam(float beam, boolean right, float raise) {
        scarlet$beam = beam;
        scarlet$beamRight = right;
        scarlet$raise = raise;
    }

    @Override
    public float scarlet$found() {
        return scarlet$found;
    }

    @Override
    public float scarlet$control() {
        return scarlet$control;
    }

    @Override
    public void scarlet$setControl(float control) {
        scarlet$control = control;
    }

    @Override
    public void scarlet$setFound(float found) {
        scarlet$found = found;
    }

    @Override
    public float scarlet$tear() {
        return scarlet$tear;
    }

    @Override
    public float scarlet$spread() {
        return scarlet$spread;
    }

    @Override
    public float scarlet$burst() {
        return scarlet$burst;
    }

    @Override
    public float scarlet$gather() {
        return scarlet$gather;
    }

    @Override
    public float scarlet$hold() {
        return scarlet$hold;
    }

    @Override
    public float scarlet$rightStrike() {
        return scarlet$rightStrike;
    }

    @Override
    public float scarlet$leftStrike() {
        return scarlet$leftStrike;
    }

    @Override
    public float scarlet$shield() {
        return scarlet$shield;
    }

    @Override
    public float scarlet$recoil() {
        return scarlet$recoil;
    }

    @Override
    public float scarlet$levitate() {
        return scarlet$levitate;
    }

    @Override
    public float scarlet$lean() {
        return scarlet$lean;
    }

    @Override
    public void scarlet$set(float rightStrike, float leftStrike, float shield, float recoil, float levitate, float lean, float burst, float gather,
                            float hold, float tear, float spread) {
        scarlet$hold = hold;
        scarlet$tear = tear;
        scarlet$spread = spread;
        scarlet$rightStrike = rightStrike;
        scarlet$leftStrike = leftStrike;
        scarlet$shield = shield;
        scarlet$recoil = recoil;
        scarlet$levitate = levitate;
        scarlet$lean = lean;
        scarlet$burst = burst;
        scarlet$gather = gather;
    }
}
