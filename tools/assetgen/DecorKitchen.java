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
            twoTall = true;
        }

        @Override
        void build() {
            for (int era = 0; era < Decor.ERAS.length; era++) {
                Decor.Model whole = whole(era);
                Decor.Model[] halves = Decor.split(whole);
                put(Decor.ERAS[era] + "_lower", halves[0]);
                put(Decor.ERAS[era] + "_upper", halves[1]);
                put(Decor.ERAS[era] + "_item", whole);
            }
        }

        @Override
        String model(String era, Map<String, String> values) {
            return era + "_" + values.get("half");
        }

        @Override
        String itemModel() {
            return "present_item";
        }

        /** The whole refrigerator, both blocks of it, its painted faces kept each within one block. */
        private Decor.Model whole(int era) {
            Decor.Model model = new Decor.Model();
            switch (era) {
                case 0 -> fifties(model);
                case 1 -> topFreezer(model, 1, "enamel_turquoise", "chrome", "black_metal");
                case 2 -> topFreezer(model, 2, "enamel_gold", "veneer", "black_ash");
                case 3 -> sideBySide(model, 3, "enamel_almond", "plastic_almond", false);
                case 4 -> sideBySide(model, 4, "enamel_black", "brushed_steel", true);
                default -> frenchDoor(model);
            }
            return model;
        }

        /** The 1950s: one big door in mint enamel under a rounded top, a chrome lever and nameplate. */
        private void fifties(Decor.Model model) {
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
            model.box(11, 14, 2.2F, 12.5F, 19, 3, m("chrome"));
            model.box(11.25F, 18, 1.6F, 12.25F, 19, 2.2F, m("chrome"));
        }

        /** A freezer door over the fridge door, two handles; the 1960s in turquoise, the 1970s in harvest gold. */
        private void topFreezer(Decor.Model model, int era, String enamel, String handle, String plinth) {
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
                model.box(11.5F, 7, 2, 12.5F, 20, 3, m(handle));
                model.box(11.5F, 28.5F, 1.9F, 12.5F, 29, 3, m("chrome"));
                model.box(11.5F, 7, 1.9F, 12.5F, 7.5F, 3, m("chrome"));
            } else {
                model.box(12, 23, 2.3F, 13, 28, 3, m(handle));
                model.box(12, 10, 2.3F, 13, 20, 3, m(handle));
            }
        }

        /** Side by side, freezer on the left: the 1980s in almond, the 2000s in black with water and ice in the door. */
        private void sideBySide(Decor.Model model, int era, String enamel, String handle, boolean dispenser) {
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
            model.box(6.25F, 6, 2.2F, 7, 26, 3, m(handle));
            model.box(9, 6, 2.2F, 9.75F, 26, 3, m(handle));
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
        private void frenchDoor(Decor.Model model) {
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
            super("toaster", false);
        }

        @Override
        Decor.Model model(int era, boolean on) {
            Decor.Model model = new Decor.Model();
            switch (era) {
                case 0 -> {
                    model.box(4, 0, 6, 12, 0.75F, 10, m("bakelite_black"));
                    model.box(4, 0.75F, 6, 12, 5, 10, m("chrome"));
                    slots(model, model.box(4.5F, 5, 6.5F, 11.5F, 5.75F, 9.5F, m("chrome")), 0, 2);
                    model.box(12, 2.5F, 7.5F, 12.75F, 3.25F, 8.5F, m("bakelite_black"));
                    model.box(7, 1.5F, 5.6F, 9, 2.5F, 6, m("bakelite_black"));
                }
                case 1 -> {
                    slots(model, model.box(4, 0, 6, 12, 5, 10, m("chrome")), 1, 2);
                    model.box(3.5F, 0, 6, 4, 5, 10, m("plastic_black"));
                    model.box(12, 0, 6, 12.5F, 5, 10, m("plastic_black"));
                    model.box(12.5F, 2.5F, 7.5F, 13.25F, 3.25F, 8.5F, m("plastic_black"));
                }
                case 2 -> {
                    slots(model, model.box(4, 0, 6, 12, 5, 10, m("chrome")), 2, 2);
                    model.box(4.25F, 0.5F, 5.9F, 11.75F, 4.5F, 6, m("veneer")).only(N);
                    model.box(4.25F, 0.5F, 10, 11.75F, 4.5F, 10.1F, m("veneer")).only(Decor.Face.SOUTH);
                    model.box(12, 2.5F, 7.5F, 12.75F, 3.25F, 8.5F, m("plastic_black"));
                }
                case 3 -> {
                    slots(model, model.box(4, 0, 6, 12, 5.5F, 10, m("plastic_almond")), 3, 2);
                    model.box(4, 0, 6, 12, 0.75F, 10, m("plastic_brown"));
                    model.box(12, 3, 7.5F, 12.75F, 3.75F, 8.5F, m("plastic_brown"));
                    model.box(7.25F, 1.5F, 5.6F, 8.75F, 3, 6, m("plastic_brown"));
                }
                case 4 -> {
                    slots(model, model.box(3, 0, 5, 13, 6, 11, m("brushed_steel")), 4, 4);
                    model.box(13, 3, 7.5F, 13.75F, 3.75F, 8.5F, m("plastic_black"));
                    model.box(6, 1, 4.8F, 10, 2, 5, m("led_blue")).only(N).glow(5);
                }
                default -> {
                    slots(model, model.box(4, 0, 6, 12, 6, 10, m("plastic_charcoal")), 5, 2);
                    model.box(12, 3.5F, 7.5F, 12.75F, 4.25F, 8.5F, m("chrome"));
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

        /** The slots on top, dark down into the toaster. */
        private static void slots(Decor.Model model, Decor.Element top, int era, int count) {
            String body = top.faces.get(U).texture();
            String material = body.substring(body.lastIndexOf('/') + 1);
            model.paint(top, U, Decor.detail("toaster", era, "slots"), t -> {
                DecorPaint.under(t, material, 0, 0);
                int rows = count == 4 ? 2 : 1;
                int cols = 2;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        int x0 = 1 + c * (t.w / 2);
                        int len = t.w / 2 - 2;
                        int y = rows == 1 ? (c == 0 ? t.h / 2 - 1 : t.h / 2) : 1 + r * 3;
                        if (rows == 1) {
                            t.hline(1, t.w - 2, t.h / 2 - 1 + c, Decor.hex(0x121214));
                        } else {
                            t.hline(x0, x0 + len, y, Decor.hex(0x121214));
                        }
                    }
                }
            });
        }
    }
}
