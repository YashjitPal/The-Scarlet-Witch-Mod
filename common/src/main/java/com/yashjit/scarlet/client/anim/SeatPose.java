package com.yashjit.scarlet.client.anim;

import com.yashjit.scarlet.entity.Seat;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Sat on a couch or an armchair: the hips on the cushion and the legs reaching down to the floor in front of it,
 * however high the cushion is, rather than stuck straight out the way someone sits in a boat.
 */
public final class SeatPose {

    /** How long a leg is, from the hip to the sole, in blocks. */
    private static final float LEG = 0.75F;
    /** Steepest the legs slope down to the floor, in radians below level. */
    private static final float STEEPEST = 0.7F;
    /** How far apart the knees fall, and the feet turn out. */
    private static final float SPLAY = 0.12F;
    private static final float TURN_OUT = 0.03F;

    private SeatPose() {
    }

    /** How high over the floor the hips of someone sat on a seat are, in blocks, or 0 if they aren't sat on one. */
    public static float hip(Entity entity) {
        if (!(entity.getVehicle() instanceof Seat seat)) {
            return 0.0F;
        }
        double y = seat.getY();
        return (float) (y - Math.floor(y));
    }

    /**
     * Called once the model is posed as vanilla poses a rider: the legs brought down from level to the floor.
     */
    public static void apply(PlayerModel model, float hip) {
        if (hip <= 0.0F) {
            return;
        }
        float slope = Math.min(STEEPEST, (float) Math.asin(Math.min(1.0F, hip / LEG)));
        float xRot = slope - Mth.HALF_PI;
        model.rightLeg.xRot = xRot;
        model.rightLeg.yRot = SPLAY;
        model.rightLeg.zRot = TURN_OUT;
        model.leftLeg.xRot = xRot;
        model.leftLeg.yRot = -SPLAY;
        model.leftLeg.zRot = -TURN_OUT;
    }
}
