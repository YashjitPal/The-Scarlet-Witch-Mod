package com.yashjit.scarlet.client.entity;

import com.yashjit.scarlet.entity.CarShape;
import com.yashjit.scarlet.entity.ParkedCar;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Finds the car under the crosshair by the whole of its body, turned the way it faces: its box is only as long as it is
 * wide, so its nose and tail would otherwise be clicked straight through.
 */
public final class CarPicking {

    private CarPicking() {
    }

    public static void pick(Minecraft minecraft, float partialTick) {
        Entity viewer = minecraft.getCameraEntity();
        if (!(viewer instanceof Player player) || minecraft.level == null) {
            return;
        }
        double reach = player.entityInteractionRange();
        Vec3 eye = viewer.getEyePosition(partialTick);
        Vec3 look = viewer.getViewVector(partialTick);
        HitResult current = minecraft.hitResult;
        double best = current == null || current.getType() == HitResult.Type.MISS ? reach : current.getLocation().distanceTo(eye);
        ParkedCar found = null;
        for (ParkedCar car : minecraft.level.getEntitiesOfClass(ParkedCar.class, viewer.getBoundingBox().expandTowards(look.scale(reach)).inflate(3.0))) {
            if (car == viewer.getVehicle()) {
                continue;
            }
            double along = along(car, eye, look, partialTick);
            if (along >= 0.0 && along < best) {
                best = along;
                found = car;
            }
        }
        if (found != null) {
            minecraft.hitResult = new EntityHitResult(found, eye.add(look.scale(best)));
            minecraft.crosshairPickEntity = found;
        }
    }

    /** How far along a line of sight it meets a car's body, or -1 if it misses it. */
    private static double along(ParkedCar car, Vec3 eye, Vec3 look, float partialTick) {
        CarShape shape = car.shape();
        double height = shape.height();
        float yaw = Mth.rotLerp(partialTick, car.yRotO, car.getYRot()) * Mth.DEG_TO_RAD;
        // into the car's own frame, across it, up and along it
        Vec3 from = eye.subtract(car.getPosition(partialTick).add(0.0, height / 2, 0.0)).yRot(yaw);
        Vec3 way = look.yRot(yaw);
        double[] start = {from.x, from.y, from.z};
        double[] step = {way.x, way.y, way.z};
        double[] half = {shape.halfWidth(), height / 2, shape.halfLength()};
        double near = Double.NEGATIVE_INFINITY;
        double far = Double.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(step[axis]) < 1.0E-9) {
                if (Math.abs(start[axis]) > half[axis]) {
                    return -1.0;
                }
                continue;
            }
            double a = (-half[axis] - start[axis]) / step[axis];
            double b = (half[axis] - start[axis]) / step[axis];
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
        }
        if (far < Math.max(near, 0.0)) {
            return -1.0;
        }
        return Math.max(near, 0.0);
    }
}
