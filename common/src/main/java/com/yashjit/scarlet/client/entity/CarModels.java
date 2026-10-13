package com.yashjit.scarlet.client.entity;

import com.mojang.blaze3d.platform.NativeImage;
import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.entity.CarShape;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.platform.Services;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The cars as they are drawn, one for every era, built of boxes in sixteenths of a block from the middle of the car on the
 * ground: across to its right, up, and along to its tail, its nose toward negative along. Each is drawn in layers: its
 * paint, which takes the car's color; its trim, its glass and its lights; a wheel, drawn at each corner turning as it
 * rolls; and a steering wheel that turns as it is turned. Every box is painted onto one picture made here, each on a
 * patch of its own, so each of its faces is painted for what it is: seams down the doors, gauges on the dash, the lens of
 * a lamp.
 */
final class CarModels {

    static final Identifier TEXTURE = Scarlet.id("textures/entity/car_generated");
    private static final int TEXTURE_WIDTH = 1024;

    /** What a car is drawn in. */
    enum Layer {
        PAINT, TRIM, GLASS, LIGHTS, WHEEL, STEERING
    }

    private enum Face {
        TOP, BOTTOM, RIGHT, FRONT, LEFT, REAR
    }

    /** What a box is made of, and so how it is painted. */
    private enum Mat {
        PAINT, WHITE, CHROME, BLACK, GLOSS_BLACK, RUBBER, GLASS, DARK_GLASS, SEAT, CARPET, DASH, WOOD, LENS, TAIL_LENS, AMBER, LED_WHITE,
        LED_RED, SCREEN, STEERING
    }

    /** What is painted over one face of a box. */
    private enum Decal {
        NONE, HEADLIGHT, TAILLIGHT, GRILLE, PLATE, WHEEL, GAUGES, PLEATS
    }

    /** A car of an era, ready to draw. */
    record Built(CarShape shape, ModelPart paint, ModelPart trim, ModelPart glass, ModelPart lights, ModelPart wheel, ModelPart steering,
                 float steeringX, float steeringY, float steeringZ) {
    }

    private static Built @Nullable [] built;

    private CarModels() {
    }

    static Built get(Era era) {
        if (built == null) {
            build();
        }
        return built[era.ordinal()];
    }

    // ---------------------------------------------------------------- the boxes

    private static final class Box {
        final Layer layer;
        final Mat mat;
        /** Its corners, or for a turned box, its corners before it is turned, from where it turns. */
        final float x0;
        final float y0;
        final float z0;
        final float x1;
        final float y1;
        final float z1;
        final int era;
        /** Where it turns from, how far its top leans toward the tail, and how far it turns about its length, in degrees. */
        float pivotX;
        float pivotY;
        float pivotZ;
        float tilt;
        float roll;
        boolean turned;
        Decal decal = Decal.NONE;
        @Nullable Face decalFace;
        /** Where along it its doors meet, for seams down its sides. */
        float[] seams = new float[0];
        int u;
        int v;

        Box(int era, Layer layer, Mat mat, float x0, float y0, float z0, float x1, float y1, float z1) {
            this.era = era;
            this.layer = layer;
            this.mat = mat;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
        }

        float w() {
            return x1 - x0;
        }

        float h() {
            return y1 - y0;
        }

        float d() {
            return z1 - z0;
        }

        Box decal(Decal decal, Face face) {
            this.decal = decal;
            this.decalFace = face;
            return this;
        }

        Box seams(float... seams) {
            this.seams = seams;
            return this;
        }

        int texWidth() {
            return Mth.ceil(2 * (w() + d())) + 1;
        }

        int texHeight() {
            return Mth.ceil(d() + h()) + 1;
        }
    }

    // ---------------------------------------------------------------- one era's car

    private static final class Car {
        final int era;
        final CarShape s;
        final List<Box> boxes = new ArrayList<>();
        final float hl;
        final float hw;
        final float sill;
        final float hood;
        final float belt;
        final float roof;
        final float glassTop;
        final float trunk;
        final float archTop;
        final float wellHalf;
        final float[][] arches;
        final float cabinFrom;
        final float cabinTo;
        final float bPillar;
        final float rearDoor;
        final float seatTop;
        final float steeringX;
        final float steeringY;
        final float steeringZ;

        Car(int era) {
            this.era = era;
            s = CarShape.of(Era.byIndex(era));
            hl = s.length() / 2;
            hw = s.width() / 2;
            sill = s.sill();
            hood = s.hood();
            belt = s.belt();
            roof = s.roof();
            glassTop = roof - 1.2F;
            trunk = hood + 0.5F;
            float r = s.wheelRadius();
            archTop = 2 * r + 1.2F;
            wellHalf = s.track() - s.wheelWidth() / 2 - 0.3F;
            arches = new float[][] {{s.frontAxle() - r - 1.2F, s.frontAxle() + r + 1.2F}, {s.rearAxle() - r - 1.2F, s.rearAxle() + r + 1.2F}};
            cabinFrom = s.windshield() - 1;
            cabinTo = s.kind() == CarShape.Kind.SEDAN ? s.backlight() : hl - 2.5F;
            bPillar = s.seat() + 6;
            rearDoor = s.kind() == CarShape.Kind.HATCHBACK ? bPillar : Math.min(bPillar + 14, cabinTo - 2);
            seatTop = (s.seatHeight() + 0.15F) * 16;
            steeringX = -s.seatAcross();
            steeringY = belt - 1.6F;
            steeringZ = s.windshield() + 5.6F;
            body();
            cabin();
            interior();
            front();
            rear();
            sides();
            wheel();
            steering();
        }

        Box box(Layer layer, Mat mat, float x0, float y0, float z0, float x1, float y1, float z1) {
            Box box = new Box(era, layer, mat, x0, y0, z0, x1, y1, z1);
            boxes.add(box);
            return box;
        }

        /** A box on the car's right, and the same on its left. */
        Box[] pair(Layer layer, Mat mat, float x0, float y0, float z0, float x1, float y1, float z1) {
            return new Box[] {box(layer, mat, x0, y0, z0, x1, y1, z1), box(layer, mat, -x1, y0, z0, -x0, y1, z1)};
        }

        /** A box turned about where it is pivoted: its top leaning toward the tail by tilt, or turned about its length by roll. */
        Box turned(Layer layer, Mat mat, float px, float py, float pz, float tilt, float roll, float x0, float y0, float z0, float x1, float y1,
                   float z1) {
            Box box = box(layer, mat, x0, y0, z0, x1, y1, z1);
            box.pivotX = px;
            box.pivotY = py;
            box.pivotZ = pz;
            box.tilt = tilt;
            box.roll = roll;
            box.turned = true;
            return box;
        }

        /**
         * A pane standing on its foot and leaning: rising {@code rise} and leaning {@code lean} toward the tail by the top,
         * {@code thick} through.
         */
        Box pane(Layer layer, Mat mat, float x0, float x1, float footY, float footZ, float rise, float lean, float thick) {
            float length = (float) Math.sqrt(rise * rise + lean * lean);
            float tilt = (float) Math.toDegrees(Math.atan2(lean, rise));
            return turned(layer, mat, 0, footY, footZ, tilt, 0, x0, 0, -thick / 2, x1, length, thick / 2);
        }

        /** The same on the car's right and on its left. */
        void panes(Layer layer, Mat mat, float x0, float x1, float footY, float footZ, float rise, float lean, float thick) {
            pane(layer, mat, x0, x1, footY, footZ, rise, lean, thick);
            pane(layer, mat, -x1, -x0, footY, footZ, rise, lean, thick);
        }

        boolean chrome() {
            return era <= 2;
        }

        Mat pillarMat() {
            return era == 0 ? Mat.WHITE : era >= 3 ? Mat.GLOSS_BLACK : Mat.PAINT;
        }

        Layer layerOf(Mat mat) {
            return mat == Mat.PAINT ? Layer.PAINT : Layer.TRIM;
        }

        /**
         * Walks along the car from one place to another, in stretches either clear of its wheel arches or over one.
         */
        void along(float from, float to, Stretch stretch) {
            float cursor = from;
            for (float[] arch : arches) {
                float a0 = Math.max(arch[0], from);
                float a1 = Math.min(arch[1], to);
                if (a1 <= a0) {
                    continue;
                }
                if (a0 > cursor) {
                    stretch.run(cursor, a0, false);
                }
                stretch.run(a0, a1, true);
                cursor = a1;
            }
            if (cursor < to) {
                stretch.run(cursor, to, false);
            }
        }

        interface Stretch {
            void run(float from, float to, boolean overArch);
        }

        // ------------------------------------------------ the body

        void body() {
            // the nose and the engine under the hood, solid down to the arches the front wheels turn in
            solid(-hl + 1.5F, cabinFrom, hood);
            // the cowl the windshield stands on
            box(Layer.PAINT, Mat.PAINT, -hw, hood, cabinFrom - 0.5F, hw, belt, s.windshield() + 0.6F);
            // the cabin: its floor and its sides, open over the top for its glass
            along(cabinFrom, cabinTo, (from, to, overArch) -> {
                if (overArch) {
                    pair(Layer.PAINT, Mat.PAINT, hw - 1, archTop, from, hw, belt, to);
                    box(Layer.TRIM, Mat.BLACK, -wellHalf, sill, from, wellHalf, archTop, to);
                } else {
                    pair(Layer.PAINT, Mat.PAINT, hw - 1, sill, from, hw, belt, to);
                    box(Layer.PAINT, Mat.PAINT, -hw + 1, sill, from, hw - 1, sill + 1, to);
                    box(Layer.TRIM, Mat.CARPET, -hw + 1, sill + 1, from, hw - 1, sill + 1.4F, to);
                }
            });
            if (s.kind() == CarShape.Kind.SEDAN) {
                // the trunk
                solid(cabinTo, hl - 1.5F, trunk);
                // the shelf under the rear window
                box(Layer.PAINT, Mat.PAINT, -hw + 1, trunk, cabinTo - 2.5F, hw - 1, belt, cabinTo + 0.6F);
            } else {
                // the tailgate, or the hatch
                box(Layer.PAINT, Mat.PAINT, -hw, sill, hl - 2.5F, hw, belt, hl - 1.5F);
            }
            for (Box door : boxes) {
                if (door.layer == Layer.PAINT && door.mat == Mat.PAINT && Math.abs(door.x1 - door.x0 - 1) < 0.01F && door.y1 == belt) {
                    door.seams(s.windshield() + 0.5F, bPillar, rearDoor);
                }
            }
        }

        /** Solid body from one place along the car to another, cut away round the wheels. */
        void solid(float from, float to, float top) {
            along(from, to, (a, b, overArch) -> {
                if (overArch) {
                    box(Layer.PAINT, Mat.PAINT, -hw, archTop, a, hw, top, b);
                    box(Layer.TRIM, Mat.BLACK, -wellHalf, sill, a, wellHalf, archTop, b);
                } else {
                    box(Layer.PAINT, Mat.PAINT, -hw, sill, a, hw, top, b);
                }
            });
        }

        // ------------------------------------------------ the cabin

        void cabin() {
            float rise = glassTop - belt;
            float rakeFront = s.rakeFront();
            float rakeRear = s.rakeRear();
            float back = s.kind() == CarShape.Kind.SEDAN ? s.backlight() : hl - 2;
            Mat pillar = pillarMat();
            Layer pillarLayer = layerOf(pillar);
            // the windshield, and the pillars either side of it
            pane(Layer.GLASS, Mat.GLASS, -hw + 1.5F, hw - 1.5F, belt, s.windshield(), rise, rakeFront, 0.4F);
            panes(pillarLayer, pillar, hw - 1.7F, hw - 0.3F, belt, s.windshield(), rise + 0.8F, rakeFront, 1.2F);
            // the rear window, or the tailgate's
            pane(Layer.GLASS, era == 4 || era == 5 ? Mat.DARK_GLASS : Mat.GLASS, -hw + 1.5F, hw - 1.5F, belt, back, rise, -rakeRear, 0.4F);
            panes(pillarLayer, pillar, hw - 1.7F, hw - 0.3F, belt, back, rise + 0.8F, -rakeRear, 1.2F);
            // the roof
            float roofFrom = s.windshield() + rakeFront - 0.6F;
            float roofTo = back - rakeRear + 0.6F;
            if (era == 5) {
                // glass all over, framed in the paint
                box(Layer.PAINT, Mat.PAINT, -hw + 0.6F, glassTop, roofFrom, hw - 0.6F, roof - 0.6F, roofTo);
                box(Layer.TRIM, Mat.DARK_GLASS, -hw + 2.2F, roof - 0.6F, roofFrom + 1.5F, hw - 2.2F, roof, roofTo - 1.5F);
                pair(Layer.PAINT, Mat.PAINT, hw - 2.2F, roof - 0.6F, roofFrom, hw - 0.6F, roof, roofTo);
            } else {
                box(layerOf(era == 0 ? Mat.WHITE : Mat.PAINT), era == 0 ? Mat.WHITE : Mat.PAINT, -hw + 0.6F, glassTop, roofFrom, hw - 0.6F, roof, roofTo);
            }
            // the side windows, between the pillars
            float sideFrom = s.windshield() + rakeFront * 0.45F;
            float sideTo = back - rakeRear * 0.45F;
            if (era == 4) {
                pair(Layer.GLASS, Mat.GLASS, hw - 1.0F, belt, sideFrom, hw - 0.6F, glassTop, bPillar);
                pair(Layer.GLASS, Mat.DARK_GLASS, hw - 1.0F, belt, bPillar, hw - 0.6F, glassTop, sideTo);
            } else {
                pair(Layer.GLASS, Mat.GLASS, hw - 1.0F, belt, sideFrom, hw - 0.6F, glassTop, sideTo);
            }
            // the pillar between the doors, and on a wagon the one behind the back door
            pair(pillarLayer, pillar, hw - 1.3F, belt, bPillar - 0.8F, hw - 0.3F, glassTop, bPillar + 0.8F);
            if (s.kind() == CarShape.Kind.WAGON || s.kind() == CarShape.Kind.VAN) {
                pair(pillarLayer, pillar, hw - 1.3F, belt, rearDoor - 0.6F, hw - 0.3F, glassTop, rearDoor + 0.8F);
            }
            // trim along the tops of the doors
            pair(Layer.TRIM, chrome() ? Mat.CHROME : Mat.BLACK, hw - 0.25F, belt - 0.4F, s.windshield(), hw + 0.1F, belt + 0.15F, back);
            // a mirror either side, at the feet of the windshield's pillars
            pair(Layer.PAINT, Mat.PAINT, hw + 0.1F, belt + 0.4F, s.windshield() + 0.8F, hw + 1.7F, belt + 1.9F, s.windshield() + 2.4F);
            pair(Layer.TRIM, Mat.CHROME, hw + 0.2F, belt + 0.55F, s.windshield() + 2.4F, hw + 1.6F, belt + 1.75F, s.windshield() + 2.55F);
        }

        // ------------------------------------------------ inside

        void interior() {
            float wind = s.windshield();
            // the dash, its gauges facing the driver, and its top under the foot of the windshield
            box(Layer.TRIM, Mat.DASH, -hw + 1, belt - 4.5F, wind - 1, hw - 1, belt - 0.3F, wind + 4).decal(Decal.GAUGES, Face.REAR);
            box(Layer.TRIM, Mat.DASH, -hw + 1, belt - 0.3F, wind + 1.2F, hw - 1, belt + 0.9F, wind + 4);
            if (era == 5) {
                // a screen standing up from the middle of it
                box(Layer.LIGHTS, Mat.SCREEN, -2.2F, belt + 0.9F, wind + 3.3F, 2.2F, belt + 2.6F, wind + 3.8F);
            }
            // the rear-view mirror, up at the top of the windshield
            box(Layer.TRIM, Mat.BLACK, -1.6F, glassTop - 1.6F, wind + s.rakeFront() - 0.3F, 1.6F, glassTop - 0.6F, wind + s.rakeFront() + 0.5F);
            float seat = s.seat();
            float across = s.seatAcross();
            boolean bench = era <= 2;
            if (bench) {
                box(Layer.TRIM, Mat.SEAT, -hw + 1.5F, sill + 1.4F, seat - 3.5F, hw - 1.5F, seatTop, seat + 3.5F).decal(Decal.PLEATS, Face.TOP);
                pane(Layer.TRIM, Mat.SEAT, -hw + 1.5F, hw - 1.5F, seatTop - 0.5F, seat + 4.5F, 8.5F, 1.6F, 2.0F).decal(Decal.PLEATS, Face.REAR);
            } else {
                pair(Layer.TRIM, Mat.SEAT, across - 5, sill + 1.4F, seat - 3.5F, across + 5, seatTop, seat + 3.5F);
                for (int side = -1; side <= 1; side += 2) {
                    float x0 = side > 0 ? across - 5 : -across - 5;
                    pane(Layer.TRIM, Mat.SEAT, x0, x0 + 10, seatTop - 0.5F, seat + 4.5F, 9, 1.6F, 2.0F).decal(Decal.PLEATS, Face.REAR);
                    // a headrest over it
                    pane(Layer.TRIM, Mat.SEAT, x0 + 2.5F, x0 + 7.5F, seatTop + 8.6F, seat + 6.3F, 3, 0.4F, 1.6F);
                }
            }
            // a bench across the back, where there's room for one
            float rearSeat = seat + 12;
            if (rearSeat + 4.5F <= cabinTo) {
                box(Layer.TRIM, Mat.SEAT, -hw + 1.5F, sill + 1.4F, rearSeat - 3.5F, hw - 1.5F, seatTop - 0.5F, rearSeat + 3.0F).decal(Decal.PLEATS, Face.TOP);
                pane(Layer.TRIM, Mat.SEAT, -hw + 1.5F, hw - 1.5F, seatTop - 1, rearSeat + 4.0F, 8, 1.4F, 1.8F).decal(Decal.PLEATS, Face.REAR);
            }
        }

        // ------------------------------------------------ the nose and the tail

        void front() {
            float face = -hl + 1.5F;
            Mat bumper = chrome() ? Mat.CHROME : Mat.BLACK;
            if (era == 5) {
                box(Layer.PAINT, Mat.PAINT, -hw - 0.3F, sill - 0.5F, -hl, hw + 0.3F, sill + 3.0F, face);
                box(Layer.TRIM, Mat.GLOSS_BLACK, -hw + 6, sill + 0.4F, -hl - 0.15F, hw - 6, sill + 2.2F, -hl);
            } else {
                box(Layer.TRIM, bumper, -hw - 0.4F, sill - 0.6F, -hl, hw + 0.4F, sill + 2.6F, face);
            }
            box(Layer.TRIM, Mat.BLACK, -4.2F, sill + 0.1F, -hl - 0.2F, 4.2F, sill + 2.5F, -hl).decal(Decal.PLATE, Face.FRONT);
            float lampTop = hood - 1.0F;
            switch (era) {
                case 0 -> {
                    for (Box lamp : pair(Layer.LIGHTS, Mat.LENS, hw - 5.5F, lampTop - 4, face - 0.3F, hw - 1.5F, lampTop, face)) {
                        lamp.decal(Decal.HEADLIGHT, Face.FRONT);
                    }
                }
                case 1 -> {
                    for (Box lamp : pair(Layer.LIGHTS, Mat.LENS, hw - 8.3F, lampTop - 3, face - 0.3F, hw - 5.3F, lampTop, face)) {
                        lamp.decal(Decal.HEADLIGHT, Face.FRONT);
                    }
                    for (Box lamp : pair(Layer.LIGHTS, Mat.LENS, hw - 4.8F, lampTop - 3, face - 0.3F, hw - 1.8F, lampTop, face)) {
                        lamp.decal(Decal.HEADLIGHT, Face.FRONT);
                    }
                }
                case 2 -> {
                    for (Box lamp : pair(Layer.LIGHTS, Mat.LENS, hw - 9.4F, lampTop - 2.6F, face - 0.3F, hw - 5.8F, lampTop, face)) {
                        lamp.decal(Decal.HEADLIGHT, Face.FRONT);
                    }
                    for (Box lamp : pair(Layer.LIGHTS, Mat.LENS, hw - 5.4F, lampTop - 2.6F, face - 0.3F, hw - 1.8F, lampTop, face)) {
                        lamp.decal(Decal.HEADLIGHT, Face.FRONT);
                    }
                }
                case 3 -> {
                    for (Box lamp : pair(Layer.LIGHTS, Mat.LENS, hw - 8.5F, lampTop - 2.6F, face - 0.3F, hw - 1.5F, lampTop, face)) {
                        lamp.decal(Decal.HEADLIGHT, Face.FRONT);
                    }
                }
                case 4 -> {
                    for (Box lamp : pair(Layer.LIGHTS, Mat.LENS, hw - 9, lampTop - 2.6F, face - 0.3F, hw - 1, lampTop + 0.4F, face)) {
                        lamp.decal(Decal.HEADLIGHT, Face.FRONT);
                    }
                }
                default -> {
                    pair(Layer.LIGHTS, Mat.LED_WHITE, hw - 9, lampTop - 0.8F, face - 0.3F, hw - 1, lampTop, face);
                    box(Layer.LIGHTS, Mat.LED_WHITE, -hw + 9, lampTop - 0.5F, face - 0.25F, hw - 9, lampTop - 0.2F, face);
                }
            }
            if (era >= 1 && era <= 4) {
                // turn signals under the lamps
                pair(Layer.LIGHTS, Mat.AMBER, hw - 4.2F, sill + 2.7F, face - 0.25F, hw - 1.6F, sill + 3.7F, face);
            }
            if (era <= 4) {
                float halfGrille = era <= 2 ? hw - (era == 0 ? 6.0F : 9.6F) : hw - 9;
                box(Layer.TRIM, era <= 2 ? Mat.CHROME : Mat.BLACK, -halfGrille, sill + 3, face - 0.25F, halfGrille, lampTop - (era == 0 ? 0.4F : 0.2F), face)
                        .decal(Decal.GRILLE, Face.FRONT);
            }
            // the hood's edge, catching the light
            if (chrome()) {
                box(Layer.TRIM, Mat.CHROME, -hw + 1, hood - 0.2F, face - 0.1F, hw - 1, hood + 0.1F, face + 0.8F);
            }
        }

        void rear() {
            float face = hl - 1.5F;
            Mat bumper = chrome() ? Mat.CHROME : Mat.BLACK;
            if (era == 5) {
                box(Layer.PAINT, Mat.PAINT, -hw - 0.3F, sill - 0.5F, face, hw + 0.3F, sill + 3.0F, hl);
            } else {
                box(Layer.TRIM, bumper, -hw - 0.4F, sill - 0.6F, face, hw + 0.4F, sill + 2.6F, hl);
            }
            box(Layer.TRIM, Mat.BLACK, -4.2F, sill + 2.9F, face, 4.2F, sill + 5.2F, face + 0.25F).decal(Decal.PLATE, Face.REAR);
            switch (era) {
                case 0 -> {
                    // the fins, stepping up toward the tail, a lamp in the end of each
                    pair(Layer.PAINT, Mat.PAINT, hw - 2.6F, trunk, hl - 22, hw, trunk + 1.6F, face);
                    pair(Layer.PAINT, Mat.PAINT, hw - 2.2F, trunk + 1.6F, hl - 12, hw, trunk + 3.2F, face);
                    for (Box lamp : pair(Layer.LIGHTS, Mat.TAIL_LENS, hw - 2.3F, trunk + 0.4F, face, hw - 0.1F, trunk + 2.9F, face + 0.3F)) {
                        lamp.decal(Decal.TAILLIGHT, Face.REAR);
                    }
                }
                case 1, 2 -> {
                    for (Box lamp : pair(Layer.LIGHTS, Mat.TAIL_LENS, hw - 6.5F, belt - 4.5F, face, hw - 0.6F, belt - 2.4F, face + 0.3F)) {
                        lamp.decal(Decal.TAILLIGHT, Face.REAR);
                    }
                }
                case 3 -> {
                    for (Box lamp : pair(Layer.LIGHTS, Mat.TAIL_LENS, hw - 5.5F, belt - 5, face, hw - 0.6F, belt - 1.4F, face + 0.3F)) {
                        lamp.decal(Decal.TAILLIGHT, Face.REAR);
                    }
                }
                case 4 -> {
                    for (Box lamp : pair(Layer.LIGHTS, Mat.TAIL_LENS, hw - 2.6F, belt - 4.5F, face, hw - 0.4F, roof - 7, face + 0.3F)) {
                        lamp.decal(Decal.TAILLIGHT, Face.REAR);
                    }
                }
                default -> box(Layer.LIGHTS, Mat.LED_RED, -hw + 1, belt - 2.5F, face, hw - 1, belt - 1.6F, face + 0.3F);
            }
        }

        // ------------------------------------------------ down the sides

        void sides() {
            switch (era) {
                case 0 -> {
                    // a chrome spear sweeping back, and chrome along the sills
                    pair(Layer.TRIM, Mat.CHROME, hw, belt - 4.6F, -hl + 8, hw + 0.15F, belt - 4.0F, hl - 6);
                    sideStrip(Mat.CHROME, sill, sill + 0.6F, -hl + 3, hl - 3, 0.1F);
                }
                case 1 -> pair(Layer.TRIM, Mat.CHROME, hw, belt - 2.6F, -hl + 4, hw + 0.15F, belt - 2.0F, hl - 4);
                case 2 -> {
                    // wood paneling framed in chrome, from behind the front wheels to the tail
                    sideStrip(Mat.WOOD, sill + 2.2F, belt - 1.7F, -hl + 13, hl - 3, 0.12F);
                    sideStrip(Mat.CHROME, belt - 1.7F, belt - 1.3F, -hl + 13, hl - 3, 0.16F);
                    sideStrip(Mat.CHROME, sill + 1.8F, sill + 2.2F, -hl + 13, hl - 3, 0.16F);
                }
                case 3 -> sideStrip(Mat.BLACK, sill + 4.4F, sill + 5.6F, -hl + 3, hl - 3, 0.25F);
                case 4 -> {
                    sideStrip(Mat.BLACK, sill, sill + 2.2F, -hl + 3, hl - 3, 0.2F);
                    // rails along the roof
                    pair(Layer.TRIM, Mat.BLACK, hw - 4, roof, s.windshield() + s.rakeFront() + 4, hw - 3, roof + 0.8F, hl - 6);
                }
                default -> sideStrip(Mat.GLOSS_BLACK, sill, sill + 2.4F, -hl + 3, hl - 3, 0.2F);
            }
            // handles on the doors
            Mat handle = era == 5 ? Mat.GLOSS_BLACK : Mat.CHROME;
            pair(Layer.TRIM, handle, hw, belt - 2.4F, bPillar - 4, hw + 0.3F, belt - 1.8F, bPillar - 2);
            if (rearDoor > bPillar + 4) {
                pair(Layer.TRIM, handle, hw, belt - 2.4F, rearDoor - 4, hw + 0.3F, belt - 1.8F, rearDoor - 2);
            }
        }

        /** A strip down each side, broken over the wheel arches where it runs below their tops. */
        void sideStrip(Mat mat, float y0, float y1, float from, float to, float proud) {
            along(from, to, (a, b, overArch) -> {
                float bottom = overArch ? Math.max(y0, archTop) : y0;
                if (y1 > bottom) {
                    pair(Layer.TRIM, mat, hw, bottom, a, hw + proud, y1, b);
                }
            });
        }

        // ------------------------------------------------ the wheels

        void wheel() {
            float r = s.wheelRadius();
            float half = s.wheelWidth() / 2;
            box(Layer.WHEEL, Mat.RUBBER, -half, -r, -r, half, r, r).decal(Decal.WHEEL, Face.RIGHT);
            turned(Layer.WHEEL, Mat.RUBBER, 0, 0, 0, 45, 0, -half + 0.15F, -r, -r, half - 0.15F, r, r).decal(Decal.WHEEL, Face.RIGHT);
        }

        /** A steering wheel round its own middle, facing its driver: a rim, spokes, a hub and the column down into the dash. */
        void steering() {
            float rim = 2.8F;
            for (int k = 0; k < 8; k++) {
                turned(Layer.STEERING, Mat.STEERING, 0, 0, 0, 0, k * 45, -1.25F, rim - 0.3F, -0.3F, 1.25F, rim + 0.3F, 0.3F);
            }
            for (int angle : new int[] {0, 180, 270}) {
                turned(Layer.STEERING, Mat.STEERING, 0, 0, 0, 0, angle, 0.7F, -0.25F, -0.2F, rim, 0.25F, 0.2F);
            }
            box(Layer.STEERING, era <= 2 ? Mat.CHROME : Mat.STEERING, -0.9F, -0.9F, -0.45F, 0.9F, 0.9F, 0.45F);
            box(Layer.STEERING, Mat.BLACK, -0.45F, -0.45F, -4.5F, 0.45F, 0.45F, -0.45F);
        }
    }

    // ---------------------------------------------------------------- built

    private static void build() {
        Car[] cars = new Car[Era.values().length];
        List<Box> all = new ArrayList<>();
        for (int era = 0; era < cars.length; era++) {
            cars[era] = new Car(era);
            all.addAll(cars[era].boxes);
        }
        int height = pack(all);
        NativeImage image = new NativeImage(TEXTURE_WIDTH, height, true);
        for (Box box : all) {
            paint(image, box);
        }
        if (Services.PLATFORM.isDevelopmentEnvironment()) {
            try {
                image.writeToFile(Path.of("scarlet_car_texture.png"));
            } catch (IOException e) {
                Scarlet.LOG.warn("Couldn't write the cars' picture out to look at", e);
            }
        }
        Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(() -> "Scarlet cars", image));
        Built[] result = new Built[cars.length];
        for (int era = 0; era < cars.length; era++) {
            Car car = cars[era];
            result[era] = new Built(car.s, bake(car, Layer.PAINT, height), bake(car, Layer.TRIM, height), bake(car, Layer.GLASS, height),
                    bake(car, Layer.LIGHTS, height), bake(car, Layer.WHEEL, height), bake(car, Layer.STEERING, height), car.steeringX,
                    car.steeringY, car.steeringZ);
        }
        built = result;
    }

    /** Lays every box's patch out across the picture in rows, tallest first, and says how tall the picture must be. */
    private static int pack(List<Box> boxes) {
        List<Box> sorted = new ArrayList<>(boxes);
        sorted.sort(Comparator.comparingInt(Box::texHeight).reversed());
        int x = 0;
        int y = 0;
        int row = 0;
        for (Box box : sorted) {
            int w = Math.min(box.texWidth(), TEXTURE_WIDTH);
            if (x + w > TEXTURE_WIDTH) {
                x = 0;
                y += row;
                row = 0;
            }
            box.u = x;
            box.v = y;
            x += w;
            row = Math.max(row, box.texHeight());
        }
        int used = y + row;
        int height = 64;
        while (height < used) {
            height *= 2;
        }
        return height;
    }

    private static ModelPart bake(Car car, Layer layer, int height) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder flat = CubeListBuilder.create();
        int turned = 0;
        for (Box box : car.boxes) {
            if (box.layer != layer) {
                continue;
            }
            // drawn the way entity models are, upside down and mirrored across, and turned back over as they are drawn
            if (box.turned) {
                root.addOrReplaceChild("turned" + turned++, CubeListBuilder.create().texOffs(box.u, box.v).addBox(-box.x1, -box.y1, box.z0, box.w(), box.h(),
                        box.d()), PartPose.offsetAndRotation(-box.pivotX, -box.pivotY, box.pivotZ, -box.tilt * Mth.DEG_TO_RAD, 0, box.roll * Mth.DEG_TO_RAD));
            } else {
                flat.texOffs(box.u, box.v).addBox(-box.x1, -box.y1, box.z0, box.w(), box.h(), box.d());
            }
        }
        root.addOrReplaceChild("flat", flat, PartPose.ZERO);
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, height).bakeRoot();
    }

    // ---------------------------------------------------------------- painted

    /** Where a face of a box lies in its patch, as entity models lay them out. */
    private static float[] rect(Box box, Face face) {
        float u = box.u;
        float v = box.v;
        float w = box.w();
        float h = box.h();
        float d = box.d();
        return switch (face) {
            case TOP -> new float[] {u + d, v, w, d};
            case BOTTOM -> new float[] {u + d + w, v, w, d};
            case RIGHT -> new float[] {u, v + d, d, h};
            case FRONT -> new float[] {u + d, v + d, w, h};
            case LEFT -> new float[] {u + d + w, v + d, d, h};
            case REAR -> new float[] {u + 2 * d + w, v + d, w, h};
        };
    }

    private static void paint(NativeImage image, Box box) {
        for (Face face : Face.values()) {
            float[] rect = rect(box, face);
            int x0 = Mth.floor(rect[0]);
            int y0 = Mth.floor(rect[1]);
            int x1 = Math.max(x0 + 1, Mth.ceil(rect[0] + rect[2]));
            int y1 = Math.max(y0 + 1, Mth.ceil(rect[1] + rect[3]));
            int fw = x1 - x0;
            int fh = y1 - y0;
            for (int j = 0; j < fh; j++) {
                for (int i = 0; i < fw; i++) {
                    int x = x0 + i;
                    int y = y0 + j;
                    if (x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight()) {
                        image.setPixel(x, y, texel(box, face, i, j, fw, fh));
                    }
                }
            }
        }
    }

    private static float noise(int i, int j, int seed) {
        int h = i * 374761393 + j * 668265263 + seed * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0F;
    }

    private static int grey(int value, int alpha) {
        int c = Mth.clamp(value, 0, 255);
        return ARGB.color(alpha, c, c, c);
    }

    private static int rgb(int rgb, float k) {
        return ARGB.color(255, Mth.clamp(Math.round((rgb >> 16 & 0xFF) * k), 0, 255), Mth.clamp(Math.round((rgb >> 8 & 0xFF) * k), 0, 255),
                Mth.clamp(Math.round((rgb & 0xFF) * k), 0, 255));
    }

    private static boolean side(Face face) {
        return face == Face.RIGHT || face == Face.LEFT || face == Face.FRONT || face == Face.REAR;
    }

    /** One texel of one face of a box: i across it as it is seen from outside, j down it. */
    private static int texel(Box box, Face face, int i, int j, int fw, int fh) {
        float n = noise(i, j, box.u * 31 + box.v);
        float down = fh <= 1 ? 0.5F : j / (float) (fh - 1);
        if (box.decal != Decal.NONE && box.decalFace != null && (face == box.decalFace || box.decal == Decal.WHEEL && face == Face.LEFT
                || box.decal == Decal.PLEATS && box.decalFace == Face.TOP && side(face))) {
            Integer decal = decal(box, face, i, j, fw, fh, n);
            if (decal != null) {
                return decal;
            }
        }
        return switch (box.mat) {
            case PAINT -> paintTexel(box, face, i, j, fw, fh, n, 236);
            case WHITE -> {
                int base = paintTexel(box, face, i, j, fw, fh, n, 240);
                yield ARGB.color(255, ARGB.red(base), ARGB.green(base) - 2, ARGB.blue(base) - 10);
            }
            case CHROME -> {
                int value = face == Face.TOP ? 236 : Math.round(down < 0.35F ? Mth.lerp(down / 0.35F, 250, 168)
                        : down < 0.7F ? Mth.lerp((down - 0.35F) / 0.35F, 168, 228) : Mth.lerp((down - 0.7F) / 0.3F, 228, 150));
                value += Math.round((n - 0.5F) * 8);
                yield ARGB.color(255, Mth.clamp(value - 4, 0, 255), Mth.clamp(value, 0, 255), Mth.clamp(value + 6, 0, 255));
            }
            case BLACK -> grey(34 + Math.round((n - 0.5F) * 8), 255);
            case GLOSS_BLACK -> grey(face == Face.TOP ? 44 : 24 + Math.round((1 - down) * 14 + (n - 0.5F) * 4), 255);
            case RUBBER -> grey(i % 3 == 0 ? 20 : 30 + Math.round((n - 0.5F) * 6), 255);
            case GLASS -> glass(i, j, fw, fh, 44, 178, 204, 222);
            case DARK_GLASS -> glass(i, j, fw, fh, 196, 38, 46, 56);
            case SEAT -> rgb(seatColor(box.era), 0.92F + (n - 0.5F) * 0.08F);
            case CARPET -> rgb(seatColor(box.era), 0.38F + (n - 0.5F) * 0.08F);
            case DASH -> grey(face == Face.TOP ? 40 : 52 + Math.round((n - 0.5F) * 6), 255);
            case WOOD -> {
                double grain = Math.sin(i * 0.33 + j * 1.9 + Math.sin(i * 0.11 + j) * 2.2);
                float k = grain > 0.72 ? 0.72F : grain < -0.85 ? 0.86F : 1.0F;
                yield rgb(0x8A5A2E, k * (0.94F + n * 0.1F));
            }
            case LENS -> ARGB.color(255, 250, 248, 232);
            case TAIL_LENS -> ARGB.color(255, 196, 26, 30);
            case AMBER -> ARGB.color(255, 255, 160 + Math.round(n * 30), 40);
            case LED_WHITE -> ARGB.color(255, 236, 246, 255);
            case LED_RED -> ARGB.color(255, 255, 38 + Math.round(n * 20), 40);
            case SCREEN -> i > 0 && j > 0 && i < fw - 1 && j < fh - 1 ? ARGB.color(255, 40 + Math.round(n * 30), 110 + Math.round(down * 60), 200)
                    : grey(20, 255);
            case STEERING -> box.era == 0 ? ARGB.color(255, 232, 222, 196) : grey(30 + Math.round((n - 0.5F) * 6), 255);
        };
    }

    /** Body paint: lit from above, shading toward the sill, with a crease along the body and seams where its doors meet. */
    private static int paintTexel(Box box, Face face, int i, int j, int fw, int fh, float n, int base) {
        float value = base + (n - 0.5F) * 5;
        if (face == Face.TOP) {
            value += 6;
        } else if (face == Face.BOTTOM) {
            value -= 80;
        } else {
            float down = fh <= 1 ? 0.5F : j / (float) (fh - 1);
            value -= down * 22;
            if (j == fh - 1 && fh > 2) {
                value -= 22;
            }
            if (j == 0 && fh > 3) {
                value += 8;
            }
        }
        if ((face == Face.RIGHT || face == Face.LEFT) && box.seams.length > 0 && fw > 2) {
            for (float seam : box.seams) {
                if (seam <= box.z0 + 0.5F || seam >= box.z1 - 0.5F) {
                    continue;
                }
                float along = face == Face.RIGHT ? (box.z1 - seam) / box.d() : (seam - box.z0) / box.d();
                if (Math.abs(i + 0.5F - along * fw) < 0.6F && j < fh - 1) {
                    value -= 70;
                }
            }
            // the body's crease: a highlight over a shadow, two-thirds of the way up the doors
            int crease = Math.round(fh * 0.35F);
            if (j == crease) {
                value += 12;
            } else if (j == crease + 1) {
                value -= 16;
            }
        }
        return grey(Math.round(value), 255);
    }

    /** Glass: clear enough to see out of, its edges darker, with a faint glint across it. */
    private static int glass(int i, int j, int fw, int fh, int alpha, int r, int g, int b) {
        boolean edge = i == 0 || j == 0 || i == fw - 1 || j == fh - 1;
        boolean glint = Math.floorMod(i + j, 29) == 0;
        int a = edge ? Math.min(255, alpha + 50) : glint ? Math.min(255, alpha + 26) : alpha;
        int lift = glint ? 34 : 0;
        return ARGB.color(a, Math.min(255, r + lift), Math.min(255, g + lift), Math.min(255, b + lift));
    }

    private static int seatColor(int era) {
        return switch (era) {
            case 0 -> 0xB22A2E;
            case 1 -> 0x3FA3A0;
            case 2 -> 0xAC8254;
            case 3 -> 0x6E6E78;
            case 4 -> 0x9A978E;
            default -> 0x2C2C30;
        };
    }

    /** What is painted over a face: null where it lets the face's own material show. */
    private static @Nullable Integer decal(Box box, Face face, int i, int j, int fw, int fh, float n) {
        float cx = (i + 0.5F) / fw - 0.5F;
        float cy = (j + 0.5F) / fh - 0.5F;
        return switch (box.decal) {
            case HEADLIGHT -> {
                if (box.era <= 1) {
                    float r = (float) Math.sqrt(cx * cx + cy * cy);
                    yield r > 0.5F ? grey(200, 255) : r > 0.38F ? ARGB.color(255, 236, 236, 240) : r < 0.14F ? ARGB.color(255, 255, 255, 250)
                            : ARGB.color(255, 250, 246, 226);
                }
                boolean rim = i == 0 || j == 0 || i == fw - 1 || j == fh - 1;
                yield rim ? grey(box.era == 2 ? 210 : 70, 255) : Math.abs(cx) < 0.18F ? ARGB.color(255, 255, 255, 252) : ARGB.color(255, 246, 244, 232);
            }
            case TAILLIGHT -> {
                boolean rim = i == 0 || j == 0 || i == fw - 1 || j == fh - 1;
                if (box.era == 0) {
                    float r = (float) Math.sqrt(cx * cx + cy * cy);
                    yield r > 0.5F ? grey(210, 255) : r < 0.2F ? ARGB.color(255, 255, 110, 96) : ARGB.color(255, 214, 30, 34);
                }
                yield rim ? ARGB.color(255, 120, 14, 18) : j == fh - 2 && box.era <= 3 ? ARGB.color(255, 255, 196, 120)
                        : ARGB.color(255, 214 + Math.round(n * 30), 30, 34);
            }
            case GRILLE -> grille(box.era, i, j, fw, fh);
            case PLATE -> plate(box.era, i, j, fw, fh);
            case WHEEL -> face == Face.RIGHT || face == Face.LEFT ? wheel(box.era, cx, cy) : null;
            case GAUGES -> gauges(box, i, j, fw, fh);
            case PLEATS -> {
                boolean seam = face == Face.TOP ? i % 3 == 0 : j % 3 == 0;
                int color = seatColor(box.era);
                if (box.era == 0 && !seam && (face == Face.TOP ? i / 3 : j / 3) % 2 == 1) {
                    color = 0xEDE6D8;
                }
                yield rgb(color, seam ? 0.7F : 0.95F + (n - 0.5F) * 0.06F);
            }
            case NONE -> null;
        };
    }

    private static int grille(int era, int i, int j, int fw, int fh) {
        boolean rim = i == 0 || j == 0 || i == fw - 1 || j == fh - 1;
        return switch (era) {
            case 0 -> rim ? grey(236, 255) : i % 2 == 1 ? grey(54, 255) : grey(212 - j * 6, 255);
            case 1 -> rim ? grey(232, 255) : j % 2 == 1 ? grey(46, 255) : grey(220, 255);
            case 2 -> rim ? grey(232, 255) : i % 3 == 0 || j % 2 == 0 ? grey(200, 255) : grey(40, 255);
            default -> rim ? grey(58, 255) : j % 2 == 1 ? grey(22, 255) : grey(48, 255);
        };
    }

    /** A plate in its era's colors, its number a row of dots with a gap in the middle. */
    private static int plate(int era, int i, int j, int fw, int fh) {
        int[][] colors = {{0x1C1C1E, 0xE8C34A}, {0xE8C34A, 0x1C1C1E}, {0xF2F0EA, 0x2B4E8C}, {0xF2F0EA, 0xB3282E}, {0xF2F0EA, 0x2B4E8C},
                {0xF4F6F8, 0x1F3A6B}};
        boolean rim = i == 0 || j == 0 || i == fw - 1 || j == fh - 1;
        if (rim) {
            return grey(70, 255);
        }
        boolean letter = j == fh / 2 && i > 1 && i < fw - 2 && i != fw / 2;
        return rgb(letter ? colors[era][1] : colors[era][0], 1.0F);
    }

    /** A wheel seen side on: its tire round the rim, and the rim of its era. */
    private static int wheel(int era, float cx, float cy) {
        float r = (float) Math.sqrt(cx * cx + cy * cy) * 2;
        if (r > 0.97F) {
            return grey(20, 255);
        }
        if (r > 0.8F) {
            return grey(30, 255);
        }
        if (era == 0 && r > 0.62F) {
            return ARGB.color(255, 240, 238, 230);
        }
        double angle = Math.atan2(cy, cx);
        return switch (era) {
            case 0, 1 -> r < 0.15F ? grey(250, 255) : r > 0.5F ? grey(r > 0.56F ? 214 : 150, 255) : grey(224 - Math.round(r * 60), 255);
            case 2 -> r < 0.15F ? grey(80, 255) : Math.abs(r - 0.42F) < 0.05F ? grey(120, 255) : grey(210 - Math.round(r * 40), 255);
            case 3 -> {
                boolean hole = r > 0.3F && r < 0.5F && Math.cos(angle * 6) > 0.6;
                yield hole ? grey(16, 255) : r < 0.14F ? grey(150, 255) : grey(70, 255);
            }
            case 4 -> {
                boolean spoke = Math.cos(angle * 5) > 0.55 || r < 0.22F;
                yield spoke ? grey(196, 255) : grey(36, 255);
            }
            default -> r > 0.66F ? grey(186, 255) : r < 0.12F ? grey(150, 255) : grey(Math.cos(angle * 7) > 0.8 ? 64 : 40, 255);
        };
    }

    /** The dash's face toward the driver: round dials in front of them, a radio in the middle on an older car. */
    private static @Nullable Integer gauges(Box box, int i, int j, int fw, int fh) {
        float x = box.x0 + (i + 0.5F) / fw * box.w();
        float y = box.y1 - (j + 0.5F) / fh * box.h();
        CarShape s = CarShape.of(Era.byIndex(box.era));
        float driver = -s.seatAcross();
        float middle = s.belt() - 2.4F;
        for (float dial : new float[] {driver - 1.8F, driver + 1.8F}) {
            float dx = x - dial;
            float dy = y - middle;
            float r = (float) Math.sqrt(dx * dx + dy * dy);
            if (r < 1.6F) {
                if (r > 1.25F) {
                    return box.era <= 2 ? grey(214, 255) : grey(90, 255);
                }
                boolean needle = Math.abs(dx + dy) < 0.22F && dy > 0;
                return needle ? ARGB.color(255, 230, 60, 40) : ARGB.color(255, 236, 232, 214);
            }
        }
        if (box.era <= 2 && Math.abs(x) < 3 && Math.abs(y - middle) < 1) {
            return Math.abs(y - middle) < 0.4F ? ARGB.color(255, 238, 210, 120) : grey(200, 255);
        }
        return null;
    }
}
