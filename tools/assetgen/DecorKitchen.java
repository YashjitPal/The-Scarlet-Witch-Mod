import java.util.List;
import java.util.Map;

/**
 * The kitchen: a refrigerator two blocks tall, a range whose burners glow when it is on, and a toaster for the
 * counter, from the rounded pastel 1950s to stainless steel today.
 */
final class DecorKitchen {

    static final Decor.Face N = Decor.Face.NORTH;
    static final Decor.Face U = Decor.Face.UP;

    private DecorKitchen() {
    }

    static List<Decor.Piece> pieces() {
        return List.of(new Refrigerator(), new Stove(), new Toaster());
    }

    static String m(String name) {
        return Decor.mat(name);
    }

    // ---------------------------------------------------------------- refrigerator

    static final class Refrigerator extends Decor.Piece {
        Refrigerator() {
            super("refrigerator");
            property("half", "lower", "upper");
            // open while anyone has it open: its door swung out, the light on inside
            property("open", "false", "true");
            twoTall = true;
        }

        @Override
        void build() {
            for (int era = 0; era < Decor.ERAS.length; era++) {
                Decor.Model whole = whole(era, false);
                Decor.Model[] halves = Decor.split(whole);
                put(Decor.ERAS[era] + "_lower", halves[0]);
                put(Decor.ERAS[era] + "_upper", halves[1]);
                put(Decor.ERAS[era] + "_item", whole);
                Decor.Model[] open = Decor.split(whole(era, true));
                put(Decor.ERAS[era] + "_lower_open", open[0]);
                put(Decor.ERAS[era] + "_upper_open", open[1]);
            }
        }

        @Override
        String model(String era, Map<String, String> values) {
            return era + "_" + values.get("half") + ("true".equals(values.get("open")) ? "_open" : "");
        }

        @Override
        String itemModel() {
            return "present_item";
        }

        /** The whole refrigerator, both blocks of it, its painted faces kept each within one block. */
        private Decor.Model whole(int era, boolean open) {
            Decor.Model model = new Decor.Model();
            switch (era) {
                case 0 -> fifties(model, open);
                case 1 -> topFreezer(model, 1, "enamel_turquoise", "chrome", "black_metal", open);
                case 2 -> topFreezer(model, 2, "enamel_gold", "veneer", "black_ash", open);
                case 3 -> sideBySide(model, 3, "enamel_almond", "plastic_almond", false, open);
                case 4 -> sideBySide(model, 4, "enamel_black", "brushed_steel", true, open);
                default -> frenchDoor(model, open);
            }
            return model;
        }

        // ---------------------------------------------------------------- an open door

        /** Where a door hangs: on the side of the opening at the lower x, or the higher. */
        private enum Hinge {
            LOW_X, HIGH_X
        }

        /**
         * A door swung wide open on its hinge: a slab standing out square from the front at the edge it hangs from, its
         * outside in the body's enamel with its handle near the far edge, its inside lined with door shelves of bottles;
         * and the insides of the refrigerator lit where it was, a block at a time, as everything painted must be.
         *
         * @param x0 x1 across the opening, y0 y1 up it
         */
        private static void openDoor(Decor.Model model, int era, String name, Hinge hinge, float x0, float x1, float y0, float y1, String enamel,
                                     String handle, float handleFrom, float handleTo) {
            float width = x1 - x0;
            float slab0 = hinge == Hinge.LOW_X ? x0 - 1 : x1;
            float slab1 = slab0 + 1;
            Decor.Face out = hinge == Hinge.LOW_X ? Decor.Face.WEST : Decor.Face.EAST;
            Decor.Face in = hinge == Hinge.LOW_X ? Decor.Face.EAST : Decor.Face.WEST;
            for (float[] span : blocks(y0, y1)) {
                float a = span[0];
                float b = span[1];
                String half = a >= 16 ? "_upper" : "_lower";
                // the door itself
                Decor.Element door = model.box(slab0, a, 3 - width, slab1, b, 3, m(enamel));
                model.paint(door, in, Decor.detail("refrigerator", era, name + "_door" + half), t -> doorLining(t, era, a, b, y0, y1));
                if (a > y0) {
                    door.without(Decor.Face.DOWN);
                }
                if (b < y1) {
                    door.without(Decor.Face.UP);
                }
                // the insides, where the door was
                Decor.Element inside = model.box(x0, a, 2.98F, x1, b, 2.99F);
                model.paint(inside, N, Decor.detail("refrigerator", era, name + "_inside" + half), t -> interior(t, era, a, b, y0, y1));
                inside.only(N);
                inside.glow(9).flat();
                if (handleFrom < b && handleTo > a) {
                    // the handle near the door's far edge, out on its outside
                    float h0 = Math.max(handleFrom, a);
                    float h1 = Math.min(handleTo, b);
                    float side = out == Decor.Face.WEST ? slab0 - 0.8F : slab1;
                    model.box(side, h0, 3 - width + 1, side + 0.8F, h1, 3 - width + 2, m(handle));
                }
            }
        }

        /** A span of height cut where one block ends and the next begins. */
        private static List<float[]> blocks(float y0, float y1) {
            if (y0 >= 16 || y1 <= 16) {
                return List.of(new float[] {y0, y1});
            }
            return List.of(new float[] {y0, 16}, new float[] {16, y1});
        }

        /** How light the inside of each era's refrigerator is lined. */
        private static int liner(int era) {
            return era <= 2 ? 0xEFEADB : 0xF5F7F8;
        }

        /**
         * The lit insides behind an open door, the part of them from {@code a} to {@code b} up the opening from
         * {@code y0} to {@code y1}: a gasket round the edge, the light at the top, shelves, the crisper drawers at the
         * bottom, and something on every shelf.
         */
        private static void interior(Decor.Tex t, int era, float a, float b, float y0, float y1) {
            int lining = Decor.hex(liner(era));
            int gasket = Decor.hex(era == 4 ? 0x2A2B2E : 0xB9BBBA);
            int shelf = Decor.hex(era <= 2 ? 0x8E8E8A : 0xC3DCE4);
            int[] food = {0xF7F7F2, 0xC8303A, 0x5E9C3A, 0xE8C34A, 0xF08A24, 0xF0E6D0, 0x8A3A6E};
            t.fill(lining);
            for (int r = 0; r < t.h; r++) {
                // the height of this row of texels, measured down from the top of the whole opening
                float y = b - r - 0.5F;
                float down = y1 - y;
                for (int c = 0; c < t.w; c++) {
                    boolean edge = c == 0 || c == t.w - 1 || y > y1 - 1 || y < y0 + 1;
                    if (edge) {
                        t.px(c, r, gasket);
                    }
                }
                if (down > 1 && down < 2) {
                    // the light
                    int middle = t.w / 2;
                    t.px(middle - 1, r, Decor.hex(0xFFF6D2)).px(middle, r, Decor.hex(0xFFFFFF));
                } else if ((int) down % 5 == 0 && down > 4 && y > y0 + 4) {
                    t.hline(1, t.w - 2, r, shelf);
                } else if ((int) down % 5 == 3 && y > y0 + 4) {
                    // something stood on the shelf below
                    for (int c = 2; c < t.w - 2; c += 3) {
                        t.px(c, r, Decor.hex(food[Math.floorMod(c * 3 + (int) down + era, food.length)]));
                    }
                } else if (y < y0 + 4 && y > y0 + 1) {
                    // the crisper drawers, side by side
                    int split = t.w / 2;
                    t.px(split, r, gasket);
                    if (y > y0 + 3) {
                        t.hline(1, t.w - 2, r, Decor.shade(lining, 0.86));
                    }
                }
            }
        }

        /** The inside of a door: its lining, and shelves across it with bottles standing on them. */
        private static void doorLining(Decor.Tex t, int era, float a, float b, float y0, float y1) {
            int lining = Decor.hex(liner(era));
            int bin = Decor.shade(lining, 0.82);
            int[] bottles = {0x5E9C3A, 0xF7F7F2, 0xC8303A, 0xE8C34A, 0x3D6FB6};
            t.fill(Decor.shade(lining, 0.96));
            for (int r = 0; r < t.h; r++) {
                float down = y1 - (b - r - 0.5F);
                if ((int) down % 6 == 5 && down > 3) {
                    t.hline(1, t.w - 2, r, bin);
                } else if ((int) down % 6 >= 3 && (int) down % 6 <= 4 && down > 3) {
                    for (int c = 2; c < t.w - 2; c += 2) {
                        t.px(c, r, Decor.hex(bottles[Math.floorMod(c + (int) down / 6 + era, bottles.length)]));
                    }
                }
                t.px(0, r, bin).px(t.w - 1, r, bin);
            }
        }

        /** The 1950s: one big door in mint enamel under a rounded top, a chrome lever and nameplate. */
        private void fifties(Decor.Model model, boolean open) {
            String enamel = m("enamel_mint");
            model.box(1.5F, 0, 4, 14.5F, 1, 14, m("chrome"));
            Decor.Element lower = model.box(1, 1, 3, 15, 16, 15, enamel);
            Decor.Element upper = model.box(1, 16, 3, 15, 28, 15, enamel);
            int mint = DecorPaint.color("enamel_mint");
            model.paint(lower, N, Decor.detail("refrigerator", 0, "lower"), t -> {
                DecorPaint.under(t, "enamel_mint", 1, 0);
                t.vline(0, 0, t.h - 1, Decor.shade(Decor.hex(mint), 0.8)).vline(t.w - 1, 0, t.h - 1, Decor.shade(Decor.hex(mint), 0.8));
                t.hline(0, t.w - 1, t.h - 1, Decor.shade(Decor.hex(mint), 0.75));
            });
            model.paint(upper, N, Decor.detail("refrigerator", 0, "upper"), t -> {
                DecorPaint.under(t, "enamel_mint", 1, 4);
                t.vline(0, 0, t.h - 1, Decor.shade(Decor.hex(mint), 0.8)).vline(t.w - 1, 0, t.h - 1, Decor.shade(Decor.hex(mint), 0.8));
                t.hline(0, t.w - 1, 0, Decor.shade(Decor.hex(mint), 0.8));
                t.rect(4, 2, 6, 1, Decor.hex(DecorPaint.color("chrome")));
                t.px(5, 2, Decor.hex(0xFFFFFF));
            });
            model.box(1.5F, 28, 3.25F, 14.5F, 29, 15, enamel);
            model.box(2.5F, 29, 3.75F, 13.5F, 29.5F, 15, enamel);
            if (open) {
                // the one big door, hung opposite its lever
                openDoor(model, 0, "fridge", Hinge.LOW_X, 1, 15, 1, 28, "enamel_mint", "chrome", 14, 19);
            } else {
                model.box(11, 14, 2.2F, 12.5F, 19, 3, m("chrome"));
                model.box(11.25F, 18, 1.6F, 12.25F, 19, 2.2F, m("chrome"));
            }
        }

        /** A freezer door over the fridge door, two handles; the 1960s in turquoise, the 1970s in harvest gold. */
        private void topFreezer(Decor.Model model, int era, String enamel, String handle, String plinth, boolean open) {
            model.box(1.5F, 0, 4, 14.5F, 0.5F, 14, m(plinth));
            Decor.Element lower = model.box(1, 0.5F, 3, 15, 16, 15, m(enamel));
            Decor.Element upper = model.box(1, 16, 3, 15, 30, 15, m(enamel));
            int base = DecorPaint.color(enamel);
            int seam = Decor.shade(Decor.hex(base), 0.72);
            model.paint(lower, N, Decor.detail("refrigerator", era, "lower"), t -> {
                DecorPaint.under(t, enamel, 1, 0);
                t.vline(0, 0, t.h - 1, seam).vline(t.w - 1, 0, t.h - 1, seam).hline(0, t.w - 1, t.h - 1, seam);
            });
            model.paint(upper, N, Decor.detail("refrigerator", era, "upper"), t -> {
                DecorPaint.under(t, enamel, 1, 2);
                t.vline(0, 0, t.h - 1, seam).vline(t.w - 1, 0, t.h - 1, seam).hline(0, t.w - 1, 0, seam);
                // the freezer door's seam, eight texels down from the top
                t.hline(0, t.w - 1, 8, seam);
                if (era == 2) {
                    t.hline(1, t.w - 2, 9, Decor.shade(Decor.hex(base), 1.08));
                }
            });
            if (era == 2) {
                model.box(11.5F, 23, 2, 12.5F, 29, 3, m(handle));
                model.box(11.5F, 28.5F, 1.9F, 12.5F, 29, 3, m("chrome"));
                if (!open) {
                    model.box(11.5F, 7, 2, 12.5F, 20, 3, m(handle));
                    model.box(11.5F, 7, 1.9F, 12.5F, 7.5F, 3, m("chrome"));
                }
            } else {
                model.box(12, 23, 2.3F, 13, 28, 3, m(handle));
                if (!open) {
                    model.box(12, 10, 2.3F, 13, 20, 3, m(handle));
                }
            }
            if (open) {
                // the fridge door under the freezer's seam; the freezer stays shut
                openDoor(model, era, "fridge", Hinge.LOW_X, 1, 15, 0.5F, 21.5F, enamel, handle, era == 2 ? 7 : 10, 20);
            }
        }

        /** Side by side, freezer on the left: the 1980s in almond, the 2000s in black with water and ice in the door. */
        private void sideBySide(Decor.Model model, int era, String enamel, String handle, boolean dispenser, boolean open) {
            Decor.Element lower = model.box(0.5F, 0.5F, 3, 15.5F, 16, 15, m(enamel));
            Decor.Element upper = model.box(0.5F, 16, 3, 15.5F, 30, 15, m(enamel));
            model.box(1, 0, 4, 15, 0.5F, 14, m("plastic_black"));
            int base = DecorPaint.color(enamel);
            int seam = Decor.shade(Decor.hex(base), era == 4 ? 1.6 : 0.72);
            model.paint(lower, N, Decor.detail("refrigerator", era, "lower"), t -> {
                DecorPaint.under(t, enamel, 0, 0);
                t.vline(7, 0, t.h - 1, seam);
                t.vline(0, 0, t.h - 1, seam).vline(t.w - 1, 0, t.h - 1, seam);
                // the grille at the toe
                for (int x = 1; x < t.w - 1; x += 2) {
                    t.px(x, t.h - 2, Decor.shade(Decor.hex(base), 0.55));
                }
            });
            model.paint(upper, N, Decor.detail("refrigerator", era, "upper"), t -> {
                DecorPaint.under(t, enamel, 0, 2);
                t.vline(7, 0, t.h - 1, seam);
                t.vline(0, 0, t.h - 1, seam).vline(t.w - 1, 0, t.h - 1, seam).hline(0, t.w - 1, 0, seam);
            });
            model.box(9, 6, 2.2F, 9.75F, 26, 3, m(handle));
            if (open) {
                // the fridge's half, on the right as you face it, hung from its outer edge; the freezer's stays shut
                openDoor(model, era, "fridge", Hinge.LOW_X, 0.5F, 8, 0.5F, 30, enamel, handle, 6, 26);
            } else {
                model.box(6.25F, 6, 2.2F, 7, 26, 3, m(handle));
            }
            if (dispenser) {
                Decor.Element nook = model.box(10, 17, 2.8F, 14, 22, 3);
                model.paint(nook, N, Decor.detail("refrigerator", era, "dispenser"), t -> {
                    t.fill(Decor.hex(0x2A2C30));
                    t.rect(0, 0, t.w, 1, Decor.hex(0x4A90E0));
                    t.px(1, 0, Decor.hex(0xBFE3FF)).px(2, 0, Decor.hex(0xBFE3FF));
                    t.rect(1, 2, 2, 2, Decor.hex(0x15171A));
                    t.hline(0, t.w - 1, t.h - 1, Decor.hex(0x5A5D62));
                });
                nook.only(N);
                model.box(10, 17, 2.4F, 14, 17.5F, 3, m("plastic_grey"));
                model.box(10, 21.5F, 2.7F, 14, 22, 2.8F, m("led_blue")).glow(6);
            }
        }

        /** Today: stainless steel French doors over a freezer drawer, long bar handles, a touch panel lit blue. */
        private void frenchDoor(Decor.Model model, boolean open) {
            String steel = m("stainless");
            Decor.Element lower = model.box(0, 0.5F, 3, 16, 16, 15, steel);
            Decor.Element upper = model.box(0, 16, 3, 16, 31, 15, steel);
            model.box(0.5F, 0, 4, 15.5F, 0.5F, 14, m("plastic_black"));
            int base = DecorPaint.color("stainless");
            int seam = Decor.shade(Decor.hex(base), 0.6);
            model.paint(lower, N, Decor.detail("refrigerator", 5, "lower"), t -> {
                DecorPaint.under(t, "stainless", 0, 0);
                // the freezer drawer's top, five texels down, the doors above it meeting in the middle
                t.hline(0, t.w - 1, 4, seam);
                t.vline(8, 0, 3, seam);
            });
            model.paint(upper, N, Decor.detail("refrigerator", 5, "upper"), t -> {
                DecorPaint.under(t, "stainless", 0, 1);
                t.vline(8, 0, t.h - 1, seam);
                t.hline(0, t.w - 1, 0, seam);
            });
            model.box(3, 8.5F, 2, 13, 9.25F, 3, steel);
            if (open) {
                // both doors over the freezer drawer, each hung from its outer edge
                openDoor(model, 5, "fridge_right", Hinge.LOW_X, 0, 8, 11.5F, 31, "stainless", "stainless", 13, 28);
                openDoor(model, 5, "fridge_left", Hinge.HIGH_X, 8, 16, 11.5F, 31, "stainless", "stainless", 13, 28);
                return;
            }
            model.box(6.25F, 13, 2, 7, 28, 3, steel);
            model.box(9, 13, 2, 9.75F, 28, 3, steel);
            Decor.Element panel = model.box(10.5F, 21, 2.8F, 14, 25, 3);
            model.paint(panel, N, Decor.detail("refrigerator", 5, "panel"), t -> {
                t.fill(Decor.hex(0x121316));
                t.px(1, 1, Decor.hex(0x48A8FF)).px(2, 1, Decor.hex(0x48A8FF));
                t.px(0, 3, Decor.hex(0x2A6FB0)).px(2, 3, Decor.hex(0x2A6FB0)).px(3, 3, Decor.hex(0x8FD3FF));
            });
            panel.only(N).glow(5);
        }
    }

    // ---------------------------------------------------------------- stove

    static final class Stove extends Decor.EraPiece {
        Stove() {
            super("stove", true);
        }

        @Override
        Decor.Model model(int era, boolean on) {
            Decor.Model model = new Decor.Model();
            String[] bodies = {"enamel_white", "enamel_pink", "enamel_avocado", "enamel_almond", "plastic_white", "stainless"};
            String body = bodies[era];
            Decor.Element box = model.box(0, 0, 2, 16, 15.5F, 16, m(body));
            int base = DecorPaint.color(body);
            boolean glassDoor = era >= 2;
            boolean frontKnobs = era >= 4;
            model.paint(box, N, Decor.detail("stove", era, "front"), t -> {
                DecorPaint.under(t, body, 0, 0);
                int edge = Decor.shade(Decor.hex(base), 0.75);
                // the drawer at the toe, the oven door over it
                t.hline(0, 15, 13, edge);
                t.frameRect(1, frontKnobs ? 4 : 2, 14, frontKnobs ? 9 : 11, edge);
                int windowTop = frontKnobs ? 6 : 5;
                int window = glassDoor ? 0x15181C : 0x2A2D31;
                if (glassDoor) {
                    t.rect(2, frontKnobs ? 5 : 3, 12, frontKnobs ? 7 : 9, Decor.hex(0x15181C));
                }
                t.rect(4, windowTop, 8, 4, Decor.hex(window));
                t.frameRect(3, windowTop - 1, 10, 6, Decor.hex(era <= 1 ? DecorPaint.color("chrome") : Decor.shade(Decor.hex(window), 1.6)));
                t.px(5, windowTop, Decor.alpha(0xFFFFFF, 60)).px(6, windowTop, Decor.alpha(0xFFFFFF, 40));
                if (era <= 1) {
                    // a chrome band along the top
                    t.hline(0, 15, 0, Decor.hex(DecorPaint.color("chrome")));
                }
                if (era == 5) {
                    t.rect(6, 1, 4, 1, Decor.hex(0x121316));
                    t.px(7, 1, Decor.hex(0x48A8FF)).px(8, 1, Decor.hex(0x48A8FF));
                }
            });
            model.box(3, frontKnobs ? 11 : 12, 1.2F, 13, frontKnobs ? 11.75F : 12.75F, 2, m(era == 5 ? "stainless" : era >= 2 ? "plastic_black" : "chrome"));
            if (frontKnobs) {
                for (int k = 0; k < 4; k++) {
                    float x = 2 + k * 3.5F;
                    model.box(x, 13.5F, 1.25F, x + 1.5F, 15, 2, m(era == 5 ? "stainless" : "plastic_silver"));
                }
            }
            cooktop(model, era, on);
            if (era <= 3) {
                backguard(model, era, on);
            }
            return model;
        }

        /** The burners: coils over drip pans, a smooth glass top, or gas grates, glowing as they heat. */
        private void cooktop(Decor.Model model, int era, boolean on) {
            float[][] burners = {{2, 3.5F}, {9.5F, 3.5F}, {2, 9}, {9.5F, 9}};
            if (era == 4 || era == 5) {
                Decor.Element glass = model.box(0.25F, 15.5F, 2.25F, 15.75F, 15.75F, 15.75F, m("glass_dark"));
                glass.only(U, N, Decor.Face.SOUTH, Decor.Face.WEST, Decor.Face.EAST);
            }
            for (int k = 0; k < burners.length; k++) {
                float x = burners[k][0];
                float z = burners[k][1];
                float y = era >= 4 ? 15.75F : 15.5F;
                Decor.Element burner = model.box(x, y, z, x + 4.5F, y + 0.25F, z + 4.5F);
                String path = Decor.detail("stove", era, on ? "burner_on" : "burner");
                model.paint(burner, U, path, t -> burner(t, era, on));
                burner.only(U);
                if (on) {
                    burner.glow(era == 5 ? 9 : 11).flat();
                }
                if (era == 5) {
                    // a grate over each burner
                    model.box(x, y + 0.25F, z + 2, x + 4.5F, y + 0.75F, z + 2.5F, m("black_metal"));
                    model.box(x + 2, y + 0.25F, z, x + 2.5F, y + 0.75F, z + 4.5F, m("black_metal"));
                }
            }
        }

        private static void burner(Decor.Tex t, int era, boolean on) {
            double c = t.w / 2.0;
            if (era == 4) {
                t.fill(Decor.hex(0x15181C));
                t.ring(c, c, c - 1, c - 0.2, on ? Decor.hex(0xE5452B) : Decor.hex(0x3A3D42));
                if (on) {
                    t.ring(c, c, c - 2.2, c - 1.4, Decor.hex(0xB8301E));
                }
                return;
            }
            if (era == 5) {
                t.fill(Decor.hex(0x15181C));
                t.ring(c, c, 0.8, 1.6, on ? Decor.hex(0x3FA0FF) : Decor.hex(0x2A2C30));
                if (on) {
                    t.ring(c, c, 1.6, 2.1, Decor.hex(0x9FD8FF));
                }
                return;
            }
            // a coil over a chrome drip pan
            t.fill(Decor.hex(0x000000) & 0x00FFFFFF);
            t.disc(c, c, c, Decor.hex(DecorPaint.color("chrome")));
            int coil = on ? 0xE5452B : 0x2A2A2C;
            t.ring(c, c, c - 1.2, c - 0.5, Decor.hex(coil));
            t.ring(c, c, 0.4, 1.0, Decor.hex(coil));
            t.px((int) c, (int) c - 2, Decor.hex(coil));
        }

        /** Behind the burners, the backguard: a clock, the knobs or buttons, maybe a lit display. */
        private void backguard(Decor.Model model, int era, boolean on) {
            String[] bodies = {"enamel_white", "enamel_pink", "enamel_avocado", "enamel_almond"};
            Decor.Element back = model.box(0, 15.5F, 14, 16, 18.5F, 16, m(bodies[era]));
            model.paint(back, N, Decor.detail("stove", era, on ? "back_on" : "back"), t -> {
                DecorPaint.under(t, bodies[era], 0, 0);
                if (era == 0) {
                    t.disc(8, 1.5, 1.5, Decor.hex(DecorPaint.color("chrome")));
                    t.px(8, 1, Decor.hex(0x1C1A19)).px(7, 1, Decor.hex(0x1C1A19));
                    DecorPaint.knob(t, 2, 1, 0x1C1A19);
                    DecorPaint.knob(t, 4, 1, 0x1C1A19);
                    DecorPaint.knob(t, 11, 1, 0x1C1A19);
                    DecorPaint.knob(t, 13, 1, 0x1C1A19);
                } else if (era == 1) {
                    for (int x = 2; x < 14; x += 2) {
                        t.px(x, 1, Decor.hex(x == 6 || x == 8 ? 0xEDEDE8 : 0x1C1A19));
                    }
                } else {
                    t.rect(6, 0, 4, 2, Decor.hex(0x121316));
                    int digit = era == 2 ? 0xFFA43A : 0x5BEA76;
                    t.hline(6, 9, 1, on ? Decor.hex(digit) : Decor.shade(Decor.hex(digit), 0.55));
                    DecorPaint.knob(t, 1, 0, 0x1C1A19);
                    DecorPaint.knob(t, 3, 0, 0x1C1A19);
                    DecorPaint.knob(t, 11, 0, 0x1C1A19);
                    DecorPaint.knob(t, 13, 0, 0x1C1A19);
                }
            });
        }
    }

    // ---------------------------------------------------------------- toaster

    static final class Toaster extends Decor.EraPiece {
        Toaster() {
            // on while it toasts: its lever down and its slots glowing
            super("toaster", true);
        }

        @Override
        String itemModel() {
            // waiting for bread, as it is bought
            return "present";
        }

        @Override
        Decor.Model model(int era, boolean on) {
            Decor.Model model = new Decor.Model();
            switch (era) {
                case 0 -> {
                    model.box(4, 0, 6, 12, 0.75F, 10, m("bakelite_black"));
                    model.box(4, 0.75F, 6, 12, 5, 10, m("chrome"));
                    slots(model, model.box(4.5F, 5, 6.5F, 11.5F, 5.75F, 9.5F, m("chrome")), 0, 2, on);
                    lever(model, 12, 12.75F, 5, on, m("bakelite_black"));
                    model.box(7, 1.5F, 5.6F, 9, 2.5F, 6, m("bakelite_black"));
                }
                case 1 -> {
                    slots(model, model.box(4, 0, 6, 12, 5, 10, m("chrome")), 1, 2, on);
                    model.box(3.5F, 0, 6, 4, 5, 10, m("plastic_black"));
                    model.box(12, 0, 6, 12.5F, 5, 10, m("plastic_black"));
                    lever(model, 12.5F, 13.25F, 5, on, m("plastic_black"));
                }
                case 2 -> {
                    slots(model, model.box(4, 0, 6, 12, 5, 10, m("chrome")), 2, 2, on);
                    model.box(4.25F, 0.5F, 5.9F, 11.75F, 4.5F, 6, m("veneer")).only(N);
                    model.box(4.25F, 0.5F, 10, 11.75F, 4.5F, 10.1F, m("veneer")).only(Decor.Face.SOUTH);
                    lever(model, 12, 12.75F, 5, on, m("plastic_black"));
                }
                case 3 -> {
                    slots(model, model.box(4, 0, 6, 12, 5.5F, 10, m("plastic_almond")), 3, 2, on);
                    model.box(4, 0, 6, 12, 0.75F, 10, m("plastic_brown"));
                    lever(model, 12, 12.75F, 5.5F, on, m("plastic_brown"));
                    model.box(7.25F, 1.5F, 5.6F, 8.75F, 3, 6, m("plastic_brown"));
                }
                case 4 -> {
                    slots(model, model.box(3, 0, 5, 13, 6, 11, m("brushed_steel")), 4, 4, on);
                    lever(model, 13, 13.75F, 6, on, m("plastic_black"));
                    model.box(6, 1, 4.8F, 10, 2, 5, m("led_blue")).only(N).glow(5);
                }
                default -> {
                    slots(model, model.box(4, 0, 6, 12, 6, 10, m("plastic_charcoal")), 5, 2, on);
                    lever(model, 12, 12.75F, 6, on, m("chrome"));
                    Decor.Element display = model.box(6.5F, 2, 5.8F, 9.5F, 3, 6);
                    model.paint(display, N, Decor.detail("toaster", 5, "display"), t -> {
                        t.fill(Decor.hex(0x121316));
                        t.px(0, 0, Decor.hex(0xF4FBFF)).px(2, 0, Decor.hex(0xF4FBFF));
                    });
                    display.only(N).glow(5);
                }
            }
            return model;
        }

        /** The lever on its side: up at the top of its slot while it waits, pushed down to the foot while it toasts. */
        private static void lever(Decor.Model model, float x0, float x1, float top, boolean on, String material) {
            float y = on ? 0.75F : top - 1.5F;
            model.box(x0, y, 7.5F, x1, y + 0.75F, 8.5F, material);
        }

        /** The slots on top, dark down into the toaster, and while it toasts the glow of its elements down in them. */
        private static void slots(Decor.Model model, Decor.Element top, int era, int count, boolean on) {
            String body = top.faces.get(U).texture();
            String material = body.substring(body.lastIndexOf('/') + 1);
            model.paint(top, U, Decor.detail("toaster", era, "slots"), t -> {
                DecorPaint.under(t, material, 0, 0);
                slotLines(t, count, Decor.hex(0x121214));
            });
            if (on) {
                Decor.Element heat = model.box(top.from[0], top.to[1] + 0.01F, top.from[2], top.to[0], top.to[1] + 0.02F, top.to[2]);
                model.paint(heat, U, Decor.detail("toaster", era, "heat"), t -> slotLines(t, count, Decor.hex(0xFF6A2A)));
                heat.only(U);
                heat.glow(9).flat();
            }
        }

        private static void slotLines(Decor.Tex t, int count, int color) {
            int rows = count == 4 ? 2 : 1;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < 2; c++) {
                    if (rows == 1) {
                        t.hline(1, t.w - 2, t.h / 2 - 1 + c, color);
                    } else {
                        int x0 = 1 + c * (t.w / 2);
                        t.hline(x0, x0 + t.w / 2 - 2, 1 + r * 3, color);
                    }
                }
            }
        }
    }
}
