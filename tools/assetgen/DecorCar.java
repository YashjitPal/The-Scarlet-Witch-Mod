import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The car parked in a home's driveway, a different one every era: a two-tone sedan with tailfins in the 1950s, a
 * station wagon in the 1960s, a wood-paneled wagon in the 1970s, a boxy hatchback in the 1980s, a minivan in the 2000s
 * and an electric crossover today.
 *
 * <p>A car is an entity, drawn from block models: it is longer than one model can reach, so each is authored whole,
 * facing north and centered on the origin, then cut across the middle into a front half and a rear half, each moved
 * to fit within a model's reach. The renderer puts them back together. Its body takes its paint as a tint.
 */
final class DecorCar {

    static final Decor.Face N = Decor.Face.NORTH;
    static final Decor.Face S = Decor.Face.SOUTH;
    static final Decor.Face W = Decor.Face.WEST;
    static final Decor.Face E = Decor.Face.EAST;
    static final Decor.Face U = Decor.Face.UP;

    /** How far each half is moved along its length to fit a model, in sixteenths; the renderer moves it back. */
    static final float FRONT_SHIFT = 21;
    static final float REAR_SHIFT = -5;
    /** How far across each half is moved, so that the car's middle line falls on the middle of a block. */
    static final float ACROSS_SHIFT = 8;

    private DecorCar() {
    }

    static Decor.Piece piece() {
        return new CarBody();
    }

    static String m(String name) {
        return Decor.mat(name);
    }

    static final class CarBody extends Decor.Piece {
        CarBody() {
            super("car_body");
            property("part", "front", "rear");
        }

        @Override
        void build() {
            for (int era = 0; era < Decor.ERAS.length; era++) {
                Decor.Model whole = car(era);
                List<Decor.Model> halves = halves(whole);
                put(Decor.ERAS[era] + "_front", halves.get(0));
                put(Decor.ERAS[era] + "_rear", halves.get(1));
                // an icon: the whole car at a third of its size
                Decor.Model icon = whole.scaled(0.3F, 0, 0, 0);
                for (Decor.Element e : icon.elements) {
                    for (int k : new int[] {0, 2}) {
                        e.from[k] += 8;
                        e.to[k] += 8;
                        if (e.origin != null) {
                            e.origin[k] += 8;
                        }
                    }
                }
                icon.culling = false;
                icon.guiScale = 1.0F;
                put(Decor.ERAS[era] + "_item", icon);
            }
        }

        @Override
        String model(String era, Map<String, String> values) {
            return era + "_" + values.get("part");
        }

        @Override
        String itemModel() {
            return "present_item";
        }

        @Override
        String blockstate() {
            List<String> variants = new ArrayList<>();
            for (String era : Decor.ERAS) {
                for (String part : new String[] {"front", "rear"}) {
                    variants.add("    \"era=" + era + ",part=" + part + "\": { \"model\": \"" + Decor.NS + ":block/car_body/" + era + "_" + part + "\" }");
                }
            }
            return "{\n  \"variants\": {\n" + String.join(",\n", variants) + "\n  }\n}\n";
        }
    }

    /** Cuts a whole car across its middle, each half moved within a model's reach and cut along its tiles. */
    static List<Decor.Model> halves(Decor.Model whole) {
        Decor.Model front = new Decor.Model();
        Decor.Model rear = new Decor.Model();
        front.particle = rear.particle = whole.particle;
        front.culling = rear.culling = false;
        for (Decor.Element e : whole.elements) {
            if (e.to[2] <= 0) {
                front.elements.add(e.moved(ACROSS_SHIFT, 0, FRONT_SHIFT));
            } else if (e.from[2] >= 0) {
                rear.elements.add(e.moved(ACROSS_SHIFT, 0, REAR_SHIFT));
            } else {
                Decor.Element a = e.copy();
                a.to[2] = 0;
                a.faces.remove(S);
                Decor.Element b = e.copy();
                b.from[2] = 0;
                b.faces.remove(N);
                front.elements.add(a.moved(ACROSS_SHIFT, 0, FRONT_SHIFT));
                rear.elements.add(b.moved(ACROSS_SHIFT, 0, REAR_SHIFT));
            }
        }
        return List.of(front.subdivided(), rear.subdivided());
    }

    // ---------------------------------------------------------------- the cars

    /**
     * How a car of an era is shaped: its length and width, how high its body and roof, where its cabin runs, and what
     * it is trimmed in.
     */
    record Shape(float length, float width, float sill, float belt, float roof, float cabinFrom, float cabinTo, String trim,
                 String roofMaterial, boolean roofTinted, String wheel) {
    }

    static Decor.Model car(int era) {
        Decor.Model model = new Decor.Model();
        model.culling = false;
        Shape s = switch (era) {
            case 0 -> new Shape(72, 30, 3, 11, 20, -12, 14, "chrome", "car_white", false, "whitewall");
            case 1 -> new Shape(70, 30, 3, 11, 20, -10, 31, "chrome", "car_paint", true, "hubcap");
            case 2 -> new Shape(72, 30, 3, 11.5F, 20.5F, -10, 32, "chrome", "car_paint", true, "hubcap");
            case 3 -> new Shape(60, 28, 3, 11, 20, -9, 24, "plastic_black", "car_paint", true, "steel");
            case 4 -> new Shape(70, 30, 3, 12, 24, -20, 32, "plastic_black", "car_paint", true, "alloy");
            default -> new Shape(68, 30, 4, 12, 21, -13, 25, "plastic_black", "glass_car", false, "aero");
        };
        float hl = s.length() / 2;
        float hw = s.width() / 2;
        String paint = m("car_paint");
        // the body, painted
        model.box(-hw, s.sill(), -hl, hw, s.belt(), hl, paint).tint();
        // the cabin: glass all round, under its roof, with pillars between the windows
        float cw = hw - 1.5F;
        model.box(-cw, s.belt(), s.cabinFrom(), cw, s.roof(), s.cabinTo(), m("glass_car"));
        Decor.Element roof = model.box(-cw - 0.25F, s.roof(), s.cabinFrom() + 0.5F, cw + 0.25F, s.roof() + 1, s.cabinTo() - 0.25F, m(s.roofMaterial()));
        if (s.roofTinted()) {
            roof.tint();
        }
        float[] pillars = era == 0 ? new float[] {s.cabinFrom(), 0, s.cabinTo() - 2} : era == 4
                ? new float[] {s.cabinFrom(), 0, 14, s.cabinTo() - 2} : new float[] {s.cabinFrom(), 1, s.cabinTo() - 2};
        for (float z : pillars) {
            Decor.Element pillar = model.box(-cw - 0.1F, s.belt(), z, cw + 0.1F, s.roof(), z + 2, era == 0 ? m("car_white") : paint);
            pillar.only(W, E);
            if (era != 0) {
                pillar.tint();
            }
        }
        // bumpers
        model.box(-hw - 0.5F, s.sill() - 1, -hl - 1, hw + 0.5F, s.sill() + 2, -hl + 1, m(s.trim()));
        model.box(-hw - 0.5F, s.sill() - 1, hl - 1, hw + 0.5F, s.sill() + 2, hl + 1, m(s.trim()));
        // a strip along each side
        if (era <= 2) {
            model.box(-hw - 0.1F, s.belt() - 3.5F, -hl + 2, -hw, s.belt() - 3, hl - 2, m("chrome")).only(W);
            model.box(hw, s.belt() - 3.5F, -hl + 2, hw + 0.1F, s.belt() - 3, hl - 2, m("chrome")).only(E);
        }
        lights(model, s, era);
        wheels(model, s, era);
        switch (era) {
            case 0 -> {
                // tailfins rising over the trunk, and a white top
                model.box(-hw, s.belt(), hl - 12, -hw + 2, s.belt() + 3, hl, paint).tint();
                model.box(hw - 2, s.belt(), hl - 12, hw, s.belt() + 3, hl, paint).tint();
            }
            case 2 -> {
                // wood paneling down both sides, framed in chrome
                model.box(-hw - 0.15F, s.sill() + 2, -hl + 14, -hw, s.belt() - 1, hl - 4, m("veneer")).only(W);
                model.box(hw, s.sill() + 2, -hl + 14, hw + 0.15F, s.belt() - 1, hl - 4, m("veneer")).only(E);
            }
            case 4 -> {
                // roof rails
                model.box(-cw + 1, s.roof() + 1, s.cabinFrom() + 4, -cw + 2, s.roof() + 1.75F, s.cabinTo() - 3, m("black_metal"));
                model.box(cw - 2, s.roof() + 1, s.cabinFrom() + 4, cw - 1, s.roof() + 1.75F, s.cabinTo() - 3, m("black_metal"));
            }
            default -> {
            }
        }
        model.particle(paint);
        return model;
    }

    /** Headlights and a grille across the front, taillights at the back. */
    private static void lights(Decor.Model model, Shape s, int era) {
        float hl = s.length() / 2;
        float hw = s.width() / 2;
        float y0 = s.sill() + 2;
        float y1 = s.belt() - 1;
        // the grille, in two halves to keep each painted face within a block
        for (int side = -1; side <= 1; side += 2) {
            float x0 = side < 0 ? -hw + 5 : 0;
            float x1 = side < 0 ? 0 : hw - 5;
            Decor.Element grille = model.box(x0, y0, -hl - 0.2F, x1, y1, -hl);
            grille.only(N);
            model.paint(grille, N, Decor.detail("car_body", era, "grille"), t -> grille(t, era));
        }
        for (int side = -1; side <= 1; side += 2) {
            float x0 = side < 0 ? -hw + 0.5F : hw - 4.5F;
            Decor.Element lamp = model.box(x0, y0, -hl - 0.3F, x0 + 4, y1, -hl);
            lamp.only(N);
            model.paint(lamp, N, Decor.detail("car_body", era, "headlight"), t -> headlight(t, era));
            Decor.Element tail = model.box(x0, y0, hl, x0 + 4, y1, hl + 0.3F);
            tail.only(S);
            model.paint(tail, S, Decor.detail("car_body", era, "taillight"), t -> taillight(t, era));
        }
        if (era == 5) {
            // a light bar across the front and another across the back
            model.box(-hw + 4.5F, y1 - 1, -hl - 0.35F, hw - 4.5F, y1 - 0.5F, -hl - 0.2F, m("led_white")).only(N).glow(6);
            model.box(-hw + 4.5F, y1 - 1, hl + 0.2F, hw - 4.5F, y1 - 0.5F, hl + 0.35F, m("led_red")).only(S).glow(6);
        }
    }

    private static void wheels(Decor.Model model, Shape s, int era) {
        float hl = s.length() / 2;
        float hw = s.width() / 2;
        float axle = hl - 13;
        for (float z : new float[] {-axle, axle}) {
            for (int side = -1; side <= 1; side += 2) {
                float x0 = side < 0 ? -hw - 0.5F : hw - 2.5F;
                Decor.Element wheel = model.box(x0, 0, z - 6, x0 + 3, 12, z + 6, m("rubber"));
                Decor.Face outside = side < 0 ? W : E;
                model.paint(wheel, outside, Decor.detail("car_body", era, "wheel"), t -> wheel(t, s.wheel()));
                wheel.faces.remove(side < 0 ? E : W);
            }
        }
    }

    // ---------------------------------------------------------------- painted parts

    private static void grille(Decor.Tex t, int era) {
        switch (era) {
            case 0, 1 -> {
                DecorPaint.under(t, "chrome", 0, 0);
                for (int x = 1; x < t.w; x += 2) {
                    t.vline(x, 1, t.h - 2, Decor.hex(0x3A3D42));
                }
            }
            case 2 -> {
                DecorPaint.under(t, "chrome", 0, 0);
                for (int y = 1; y < t.h - 1; y += 2) {
                    t.hline(1, t.w - 2, y, Decor.hex(0x2A2C30));
                }
            }
            case 3, 4 -> {
                t.fill(Decor.hex(0x1F1F22));
                for (int y = 1; y < t.h - 1; y += 2) {
                    t.hline(0, t.w - 1, y, Decor.hex(0x3A3B40));
                }
            }
            default -> t.fill(Decor.hex(0x18181B));
        }
    }

    private static void headlight(Decor.Tex t, int era) {
        t.fill(Decor.hex(era <= 2 ? DecorPaint.color("chrome") : 0x1F1F22));
        if (era <= 1) {
            t.disc(t.w / 2.0, t.h / 2.0, Math.min(t.w, t.h) / 2.0 - 0.3, Decor.hex(0xF4F2E8));
            t.px(t.w / 2 - 1, t.h / 2 - 1, Decor.hex(0xFFFFFF));
        } else if (era == 2) {
            t.disc(t.w * 0.3, t.h / 2.0, 1.2, Decor.hex(0xF4F2E8));
            t.disc(t.w * 0.75, t.h / 2.0, 1.2, Decor.hex(0xF4F2E8));
        } else if (era == 5) {
            t.hline(0, t.w - 1, 1, Decor.hex(0xF4FBFF));
        } else {
            t.rect(0, 1, t.w, t.h - 2, Decor.hex(0xE8ECEE));
            if (era == 4) {
                t.rect(0, t.h - 2, t.w, 1, Decor.hex(0xFFB347));
            }
        }
    }

    private static void taillight(Decor.Tex t, int era) {
        t.fill(Decor.hex(0x3A0A0A));
        if (era == 0) {
            t.disc(t.w / 2.0, t.h / 2.0, 1.4, Decor.hex(0xE0322B));
        } else if (era == 5) {
            t.hline(0, t.w - 1, 1, Decor.hex(0xFF3A3A));
        } else {
            t.rect(0, 0, t.w, t.h, Decor.hex(0xC8282B));
            t.rect(0, t.h - 1, t.w, 1, Decor.hex(0xF2A33A));
        }
    }

    /** A wheel seen side on: its tire round its hub, the corners cut away. */
    private static void wheel(Decor.Tex t, String kind) {
        double c = t.w / 2.0;
        t.disc(c, c, c, Decor.hex(0x161618));
        t.ring(c, c, c - 1.4, c - 0.6, Decor.hex(0x222226));
        switch (kind) {
            case "whitewall" -> {
                t.ring(c, c, c - 3.2, c - 1.8, Decor.hex(0xF2F0EA));
                t.disc(c, c, c - 3.6, Decor.hex(DecorPaint.color("chrome")));
                t.disc(c, c, 1.2, Decor.hex(0xE8ECEE));
            }
            case "hubcap" -> {
                t.disc(c, c, c - 2.2, Decor.hex(DecorPaint.color("chrome")));
                t.ring(c, c, c - 3.2, c - 2.6, Decor.hex(0x8A8D93));
                t.disc(c, c, 1.0, Decor.hex(0x5A5D62));
            }
            case "steel" -> {
                t.disc(c, c, c - 2.4, Decor.hex(0x3A3B40));
                for (int k = 0; k < 6; k++) {
                    double a = k * Math.PI / 3;
                    t.px((int) Math.floor(c + Math.cos(a) * 2.2), (int) Math.floor(c + Math.sin(a) * 2.2), Decor.hex(0x8A8D93));
                }
                t.disc(c, c, 1.0, Decor.hex(0x8A8D93));
            }
            case "alloy" -> {
                t.disc(c, c, c - 2.2, Decor.hex(0xB5B9BE));
                for (int k = 0; k < 5; k++) {
                    double a = k * Math.PI * 2 / 5;
                    t.line(c - 0.5, c - 0.5, c - 0.5 + Math.cos(a) * (c - 2.6), c - 0.5 + Math.sin(a) * (c - 2.6), Decor.hex(0x6A6D72));
                }
                t.disc(c, c, 0.9, Decor.hex(0x3A3B40));
            }
            default -> {
                // an aero wheel: a smooth dark disc with a bright rim
                t.disc(c, c, c - 2.2, Decor.hex(0x2A2B30));
                t.ring(c, c, c - 2.6, c - 2.2, Decor.hex(0xB5B9BE));
                t.disc(c, c, 0.9, Decor.hex(0x8A8D93));
            }
        }
    }
}
