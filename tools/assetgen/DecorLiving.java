import java.util.List;
import java.util.Map;

/**
 * The living room: a television, something to play music on, a telephone, a couch, an armchair and a lamp, in the
 * look of every era from the 1950s to today.
 */
final class DecorLiving {

    static final Decor.Face N = Decor.Face.NORTH;
    static final Decor.Face S = Decor.Face.SOUTH;
    static final Decor.Face U = Decor.Face.UP;
    static final Decor.Face D = Decor.Face.DOWN;
    static final Decor.Face W = Decor.Face.WEST;
    static final Decor.Face E = Decor.Face.EAST;

    private DecorLiving() {
    }

    static List<Decor.Piece> pieces() {
        return List.of(new Television(), new Radio(), new Telephone(), new Couch(), new Armchair(), new Lamp());
    }

    static String m(String name) {
        return Decor.mat(name);
    }

    /**
     * A box standing out from the front of another, over a region of that front's painting, given in the painting's
     * own texels: columns from the viewer's left, rows from the top.
     */
    static Decor.Element front(Decor.Model model, Decor.Element parent, float c0, float r0, float c1, float r1, float depth) {
        float x1 = parent.to[0];
        float y1 = parent.to[1];
        float z0 = parent.from[2];
        return model.box(x1 - c1, y1 - r1, z0 - depth, x1 - c0, y1 - r0, z0);
    }

    /** The material under a north face, from where on the block that face is. */
    static void underFront(Decor.Tex t, Decor.Element e, String material) {
        DecorPaint.under(t, material, Math.round(16 - e.to[0]), Math.round(16 - e.to[1]));
    }

    /** A screen on the front of a set: dark glass when off, and its era's show glowing on it when on. */
    static void screen(Decor.Model model, Decor.Element screen, String piece, int era, boolean on, int glass, int bezel, boolean rounded) {
        screen.only(N);
        if (on) {
            model.paintAnimated(screen, N, Decor.detail(piece, era, "screen_on"), 12, 3,
                    (t, f) -> DecorPaint.show(t, f, 12, era, bezel, rounded));
            screen.glow(11).flat();
        } else {
            model.paint(screen, N, Decor.detail(piece, era, "screen"), t -> DecorPaint.screenOff(t, glass, bezel, rounded));
        }
    }

    // ---------------------------------------------------------------- television

    static final class Television extends Decor.EraPiece {
        Television() {
            super("television", true);
        }

        @Override
        Decor.Model model(int era, boolean on) {
            Decor.Model model = new Decor.Model();
            switch (era) {
                case 0 -> fifties(model, on);
                case 1 -> sixties(model, on);
                case 2 -> seventies(model, on);
                case 3 -> eighties(model, on);
                case 4 -> twoThousands(model, on);
                default -> present(model, on);
            }
            return model;
        }

        /** A walnut console on tapered legs: a small round-cornered screen in a brass bezel, a cloth grille beside it. */
        private void fifties(Decor.Model model, boolean on) {
            String walnut = m("walnut");
            for (float[] leg : new float[][] {{2, 4}, {13, 4}, {2, 11}, {13, 11}}) {
                model.box(leg[0], 0, leg[1], leg[0] + 1, 3, leg[1] + 1, walnut);
                model.box(leg[0], 0, leg[1], leg[0] + 1, 0.5F, leg[1] + 1, m("brass"));
            }
            Decor.Element body = model.box(1, 3, 3, 15, 14, 13, walnut);
            model.box(0.5F, 13.5F, 2.5F, 15.5F, 14.5F, 13.5F, walnut);
            int brass = DecorPaint.color("brass");
            model.paint(body, N, Decor.detail("television", 0, "front"), t -> {
                underFront(t, body, "walnut");
                t.frameRect(0, 0, 10, 9, Decor.hex(brass));
                t.frameRect(1, 1, 8, 7, Decor.shade(Decor.hex(brass), 0.6));
                for (int y = 1; y < 9; y++) {
                    for (int x = 10; x < 13; x++) {
                        t.px(x, y, Decor.TEXTURES.get("decor/cloth_gold").get(x, y));
                    }
                }
                t.vline(11, 1, 8, Decor.shade(DecorPaint.color("walnut"), 1.2));
                t.frameRect(10, 0, 4, 10, Decor.shade(DecorPaint.color("walnut"), 0.75));
                DecorPaint.knob(t, 2, 9, 0xC9A54E);
                DecorPaint.knob(t, 6, 9, 0xC9A54E);
            });
            Decor.Element tube = front(model, body, 1, 1, 9, 8, 0.6F).sides(m("bakelite_black"));
            screen(model, tube, "television", 0, on, 0x2E3A35, 0x1C1A19, true);
        }

        /** A portable set on a wire stand, mint with an ivory front, its rabbit ears up. */
        private void sixties(Decor.Model model, boolean on) {
            String metal = m("black_metal");
            for (float[] leg : new float[][] {{3, 5}, {12.25F, 5}, {3, 11.25F}, {12.25F, 11.25F}}) {
                model.box(leg[0], 0, leg[1], leg[0] + 0.75F, 6, leg[1] + 0.75F, metal);
            }
            model.box(3, 5.25F, 5, 13, 6, 12, metal);
            Decor.Element body = model.box(2, 6, 4, 14, 14, 13, m("plastic_mint"));
            model.paint(body, N, Decor.detail("television", 1, "front"), t -> {
                t.fill(DecorPaint.color("bakelite_ivory"));
                t.frameRect(0, 0, 10, 8, Decor.shade(DecorPaint.color("bakelite_ivory"), 0.8));
                DecorPaint.slots(t, 10, 1, 2, 4, Decor.shade(DecorPaint.color("bakelite_ivory"), 0.55) & 0xFFFFFF, 2, false);
                DecorPaint.knob(t, 10, 5, 0x9C5A2E);
            });
            Decor.Element tube = front(model, body, 1, 1, 9, 7, 0.4F).sides(m("plastic_grey"));
            screen(model, tube, "television", 1, on, 0x34403C, 0xE9DFC4, true);
            model.box(7, 14, 8, 9, 14.5F, 10, metal);
            model.box(7.6F, 14.5F, 8.6F, 8.4F, 22.5F, 9.4F, m("chrome")).rotate("z", 22.5F, 8, 14.5F, 9);
            model.box(7.6F, 14.5F, 8.6F, 8.4F, 22.5F, 9.4F, m("chrome")).rotate("z", -22.5F, 8, 14.5F, 9);
        }

        /** A long, low wood-grain console: the screen at one end in a chrome bezel, speaker cloth and dials at the other. */
        private void seventies(Decor.Model model, boolean on) {
            model.box(1, 0, 3, 15, 1, 13, m("black_ash"));
            Decor.Element body = model.box(0, 1, 2, 16, 12, 14, m("veneer"));
            int chrome = DecorPaint.color("chrome");
            model.paint(body, N, Decor.detail("television", 2, "front"), t -> {
                underFront(t, body, "veneer");
                t.frameRect(1, 1, 10, 8, Decor.hex(chrome));
                for (int y = 1; y < 10; y++) {
                    for (int x = 12; x < 15; x++) {
                        t.px(x, y, Decor.TEXTURES.get("decor/cloth_brown").get(x, y));
                    }
                }
                t.vline(11, 1, 9, Decor.hex(chrome));
                DecorPaint.knob(t, 12, 2, 0xC9D2DA);
                DecorPaint.knob(t, 12, 5, 0xC9D2DA);
                t.hline(0, 15, 10, Decor.shade(DecorPaint.color("veneer"), 0.7));
            });
            Decor.Element tube = front(model, body, 2, 2, 10, 8, 0.5F).sides(m("plastic_black"));
            screen(model, tube, "television", 2, on, 0x2B2F2C, 0xC9D2DA, true);
        }

        /** A boxy black set on a cart, the video recorder under it blinking twelve o'clock. */
        private void eighties(Decor.Model model, boolean on) {
            Decor.Element cart = model.box(1, 0, 3, 15, 4, 13, m("black_ash"));
            model.paint(cart, N, Decor.detail("television", 3, "cart"), t -> {
                underFront(t, cart, "black_ash");
                t.vline(7, 0, 3, Decor.hex(0x111111));
                t.px(6, 2, Decor.hex(0xBFC3C8)).px(8, 2, Decor.hex(0xBFC3C8));
            });
            Decor.Element vcr = model.box(3, 4, 4, 13, 6, 12, m("plastic_black"));
            model.paintAnimated(vcr, N, Decor.detail("television", 3, "vcr"), 2, 12, (t, f) -> {
                t.fill(Decor.hex(0x202024));
                t.hline(1, 5, 0, Decor.hex(0x0A0A0B));
                if (f == 0) {
                    t.hline(7, 9, 1, Decor.hex(0x5BEA76));
                }
                t.px(9, 0, Decor.hex(0xC8303A));
            });
            Decor.Element body = model.box(2, 6, 4, 14, 15, 12, m("plastic_black"));
            model.paint(body, N, Decor.detail("television", 3, "front"), t -> {
                t.fill(DecorPaint.color("plastic_black"));
                t.noise(5, 0.05);
                DecorPaint.slots(t, 10, 1, 2, 5, 0x0A0A0B, 2, false);
                t.px(10, 7, Decor.hex(0x9A9DA1)).px(11, 7, Decor.hex(0x9A9DA1));
                t.px(11, 8, on ? Decor.hex(0xFF4A3A) : Decor.hex(0x5A1A16));
            });
            Decor.Element tube = front(model, body, 1, 1, 9, 8, 0.5F).sides(m("plastic_black"));
            screen(model, tube, "television", 3, on, 0x23272A, 0x1F1F22, true);
        }

        /** A deep silver picture tube on a black stand with a disc player inside. */
        private void twoThousands(Decor.Model model, boolean on) {
            Decor.Element stand = model.box(0, 0, 3, 16, 5, 13, m("plastic_black"));
            model.paint(stand, N, Decor.detail("television", 4, "stand"), t -> {
                t.fill(DecorPaint.color("plastic_black"));
                for (int y = 1; y < 4; y++) {
                    for (int x = 1; x < 15; x++) {
                        t.px(x, y, Decor.TEXTURES.get("decor/glass_smoke").get(x, y));
                    }
                }
                t.rect(4, 2, 8, 1, Decor.hex(0xBFC3C8));
                t.hline(5, 9, 2, Decor.hex(0x8A8D93));
                t.px(10, 2, Decor.hex(0x48A8FF));
            });
            model.box(3, 6, 8, 13, 14, 13, m("plastic_grey"));
            Decor.Element bezel = model.box(1, 5, 3, 15, 15, 8, m("plastic_silver"));
            model.paint(bezel, N, Decor.detail("television", 4, "front"), t -> {
                t.fill(DecorPaint.color("plastic_silver"));
                t.noise(9, 0.03);
                t.frameRect(0, 0, 14, 9, Decor.shade(DecorPaint.color("plastic_silver"), 0.85));
                DecorPaint.slots(t, 1, 9, 4, 1, 0x6A6D72, 2, true);
                DecorPaint.slots(t, 9, 9, 4, 1, 0x6A6D72, 2, true);
                t.px(7, 9, on ? Decor.hex(0x48A8FF) : Decor.hex(0x30505F));
            });
            Decor.Element tube = front(model, bezel, 1, 1, 13, 8, 0.4F).sides(m("plastic_black"));
            screen(model, tube, "television", 4, on, 0x272B30, 0xBFC3C8, true);
        }

        /** A thin flat screen on a low oak console, a soundbar under it. */
        private void present(Decor.Model model, boolean on) {
            String metal = m("black_metal");
            for (float[] leg : new float[][] {{1, 5}, {14, 5}, {1, 11}, {14, 11}}) {
                model.box(leg[0], 0, leg[1], leg[0] + 1, 1, leg[1] + 1, metal);
            }
            Decor.Element console = model.box(0, 1, 4, 16, 4, 13, m("ash"));
            model.paint(console, N, Decor.detail("television", 5, "console"), t -> {
                underFront(t, console, "ash");
                t.vline(5, 0, 2, Decor.shade(DecorPaint.color("ash"), 0.75));
                t.vline(10, 0, 2, Decor.shade(DecorPaint.color("ash"), 0.75));
                t.hline(2, 3, 1, Decor.hex(0x26272A));
                t.hline(12, 13, 1, Decor.hex(0x26272A));
            });
            model.box(3, 4, 5, 13, 5, 7, m("knit_charcoal"));
            model.box(5, 4, 8, 11, 4.5F, 10, metal);
            model.box(7, 4.5F, 8.75F, 9, 6, 9.25F, metal);
            model.box(0, 5.5F, 8.5F, 16, 15.5F, 9.5F, m("plastic_black"));
            Decor.Element glass = model.box(0.5F, 6, 8.4F, 15.5F, 15, 8.5F);
            screen(model, glass, "television", 5, on, 0x101216, 0x111111, false);
        }
    }

    // ---------------------------------------------------------------- radio

    static final class Radio extends Decor.EraPiece {
        Radio() {
            super("radio", true);
        }

        @Override
        Decor.Model model(int era, boolean on) {
            Decor.Model model = new Decor.Model();
            switch (era) {
                case 0 -> tubeRadio(model, on);
                case 1 -> recordPlayer(model, on);
                case 2 -> stereo(model, on);
                case 3 -> boombox(model, on);
                case 4 -> hifi(model, on);
                default -> smartSpeaker(model, on);
            }
            return model;
        }

        /** A bakelite tube radio with an arched top: cloth behind three ribs, a tuning dial that glows when it plays. */
        private void tubeRadio(Decor.Model model, boolean on) {
            String bakelite = m("bakelite_brown");
            Decor.Element body = model.box(3, 0, 6, 13, 7, 11, bakelite);
            model.box(4, 7, 6, 12, 8, 11, bakelite);
            model.box(5.5F, 8, 6, 10.5F, 8.5F, 11, bakelite);
            model.paint(body, N, Decor.detail("radio", 0, "front"), t -> {
                t.fill(DecorPaint.color("bakelite_brown"));
                t.noise(3, 0.05);
                for (int y = 1; y < 6; y++) {
                    for (int x = 1; x < 6; x++) {
                        t.px(x, y, Decor.TEXTURES.get("decor/cloth_gold").get(x, y));
                    }
                }
                for (int x = 2; x < 6; x += 2) {
                    t.vline(x, 1, 5, Decor.shade(DecorPaint.color("bakelite_brown"), 1.25));
                }
                DecorPaint.knob(t, 6, 4, 0x2A1810);
                DecorPaint.knob(t, 8, 4, 0x2A1810);
            });
            Decor.Element dial = front(model, body, 6, 1, 9, 3, 0.2F);
            dial.only(N);
            model.paint(dial, N, Decor.detail("radio", 0, on ? "dial_on" : "dial"), t ->
                    DecorPaint.dial(t, 0, 0, t.w, t.h, on ? 0xFFD98A : 0xD8C9A0, 0x6B4423, 1));
            if (on) {
                dial.glow(10).flat();
            }
        }

        /** A suitcase record player in red and cream, its lid up behind the turntable. */
        private void recordPlayer(Decor.Model model, boolean on) {
            Decor.Element base = model.box(1, 0, 2, 15, 4, 14, m("fabric_red"));
            base.top(m("plastic_cream"));
            model.paint(base, N, Decor.detail("radio", 1, "front"), t -> {
                t.fill(DecorPaint.color("fabric_red"));
                t.noise(4, 0.04);
                for (int y = 1; y < 3; y++) {
                    for (int x = 4; x < 10; x++) {
                        t.px(x, y, Decor.TEXTURES.get("decor/cloth_gold").get(x, y));
                    }
                }
                t.frameRect(3, 0, 8, 4, Decor.hex(DecorPaint.color("chrome")));
                DecorPaint.knob(t, 11, 1, 0xEDE6D1);
            });
            platter(model, 3, 4, 3, 11, 11, m("rubber"), "radio", 1, on);
            model.box(12, 4, 9, 13.5F, 5, 10.5F, m("chrome"));
            model.box(12.5F, 5, 4.5F, 13, 5.5F, 10, m("chrome"));
            model.box(12, 4.75F, 4, 13, 5.25F, 5, m("plastic_black"));
            Decor.Element lid = model.box(1, 4, 13, 15, 14, 14, m("fabric_red"));
            lid.face(N, m("plastic_cream"));
        }

        /** A receiver in brushed aluminum under a walnut turntable, its tuning dial lit blue. */
        private void stereo(Decor.Model model, boolean on) {
            Decor.Element receiver = model.box(1, 0, 3, 15, 4, 13, m("walnut"));
            model.paint(receiver, N, Decor.detail("radio", 2, "front"), t -> {
                DecorPaint.under(t, "brushed_steel", 0, 0);
                t.rect(2, 1, 8, 2, Decor.hex(0x15181C));
                DecorPaint.knob(t, 11, 1, 0x3A3A3F);
                t.px(13, 2, Decor.hex(0x3A3A3F));
            });
            Decor.Element dial = front(model, receiver, 2, 1, 10, 3, 0.15F);
            dial.only(N);
            model.paint(dial, N, Decor.detail("radio", 2, on ? "dial_on" : "dial"), t ->
                    DecorPaint.dial(t, 0, 0, t.w, t.h, on ? 0x2E6FD8 : 0x15181C, on ? 0xBFE3FF : 0x3A4A5C, 5));
            if (on) {
                dial.glow(9).flat();
            }
            model.box(2, 4, 4, 14, 5.5F, 12, m("walnut"));
            platter(model, 3, 5.5F, 4.5F, 10, 11.5F, m("brushed_steel"), "radio", 2, on);
            model.box(11.5F, 5.5F, 9.5F, 12.5F, 6.5F, 10.5F, m("brushed_steel"));
            model.box(11.75F, 6.5F, 5.5F, 12.25F, 7, 10, m("brushed_steel"));
        }

        /** A boombox: two speakers either side of the tape decks, a handle over the top, the antenna out. */
        private void boombox(Decor.Model model, boolean on) {
            Decor.Element body = model.box(1, 1, 6, 15, 9, 10, m("plastic_black"));
            model.paint(body, N, Decor.detail("radio", 3, "front"), t -> {
                t.fill(DecorPaint.color("plastic_black"));
                t.hline(0, 13, 0, Decor.hex(0xBFC3C8));
                DecorPaint.speaker(t, 3, 4.5, 2.6, 0xBFC3C8, 0x1A1A1C);
                DecorPaint.speaker(t, 11, 4.5, 2.6, 0xBFC3C8, 0x1A1A1C);
                t.rect(6, 2, 2, 2, Decor.hex(0x2A2A2E)).rect(6, 5, 2, 2, Decor.hex(0x2A2A2E));
                t.px(6, 3, Decor.hex(0x6A5A4A)).px(7, 3, Decor.hex(0x6A5A4A)).px(6, 6, Decor.hex(0x6A5A4A)).px(7, 6, Decor.hex(0x6A5A4A));
                t.hline(5, 8, 1, Decor.hex(0x8A8D93));
            });
            model.box(2, 0, 6.5F, 4, 1, 9.5F, m("rubber"));
            model.box(12, 0, 6.5F, 14, 1, 9.5F, m("rubber"));
            model.box(3, 9, 7.5F, 4, 10.5F, 8.5F, m("chrome"));
            model.box(12, 9, 7.5F, 13, 10.5F, 8.5F, m("chrome"));
            model.box(3, 10.5F, 7.5F, 13, 11.5F, 8.5F, m("chrome"));
            model.box(13.25F, 9, 9, 13.75F, 15, 9.5F, m("chrome")).rotate("z", -20, 13.5F, 9, 9.25F);
            Decor.Element eq = front(model, body, 5, 7, 9, 8, 0.1F);
            eq.only(N);
            if (on) {
                model.paintAnimated(eq, N, Decor.detail("radio", 3, "eq_on"), 8, 2, (t, f) -> {
                    t.fill(Decor.hex(0x111113));
                    for (int x = 0; x < t.w; x++) {
                        double level = Decor.noise(f * 17L, x, 0);
                        t.px(x, 0, level > 0.45 ? Decor.hex(x % 2 == 0 ? 0x5BEA76 : 0xFF4FA3) : Decor.hex(0x1D3A24));
                    }
                });
                eq.glow(8).flat();
            } else {
                model.paint(eq, N, Decor.detail("radio", 3, "eq"), t -> t.fill(Decor.hex(0x1D2A22)));
            }
        }

        /** A mini system: a silver unit with a disc tray and a blue display, a speaker either side. */
        private void hifi(Decor.Model model, boolean on) {
            for (float x : new float[] {0.5F, 11.5F}) {
                Decor.Element box = model.box(x, 0, 6, x + 4, 8, 11, m("black_ash"));
                model.paint(box, N, Decor.detail("radio", 4, "speaker"), t -> {
                    t.fill(DecorPaint.color("plastic_black"));
                    DecorPaint.speaker(t, 2, 5.5, 1.8, 0x8A8D93, 0x18181A);
                    DecorPaint.speaker(t, 2, 1.8, 0.9, 0x8A8D93, 0x18181A);
                });
            }
            Decor.Element unit = model.box(5, 0, 5, 11, 9, 12, m("plastic_silver"));
            model.paint(unit, N, Decor.detail("radio", 4, "front"), t -> {
                t.fill(DecorPaint.color("plastic_silver"));
                t.noise(6, 0.03);
                t.hline(1, 4, 1, Decor.hex(0x3A3D42));
                t.disc(3, 6.5, 1.6, Decor.hex(0x9DA1A6));
                t.ring(3, 6.5, 1.0, 1.6, Decor.hex(0xD8DCE0));
                t.px(1, 8, Decor.hex(0x6A6D72)).px(4, 8, Decor.hex(0x6A6D72));
            });
            Decor.Element display = front(model, unit, 1, 3, 5, 4, 0.15F);
            display.only(N);
            if (on) {
                model.paintAnimated(display, N, Decor.detail("radio", 4, "display_on"), 6, 3, (t, f) -> {
                    t.fill(Decor.hex(0x0F3B78));
                    for (int x = 0; x < t.w; x++) {
                        if (Decor.noise(f * 11L, x, 1) > 0.35) {
                            t.px(x, 0, Decor.hex(0x8FD3FF));
                        }
                    }
                });
                display.glow(9).flat();
            } else {
                model.paint(display, N, Decor.detail("radio", 4, "display"), t -> t.fill(Decor.hex(0x1A2430)));
            }
        }

        /** A smart speaker: a knitted column with a light ring round its top that turns as it plays. */
        private void smartSpeaker(Decor.Model model, boolean on) {
            model.box(5, 0, 6, 11, 9, 10, m("knit_charcoal"));
            model.box(6, 0, 5, 10, 9, 11, m("knit_charcoal"));
            Decor.Element top = model.box(6, 9, 6, 10, 9.5F, 10, m("plastic_black"));
            if (on) {
                model.paintAnimated(top, U, Decor.detail("radio", 5, "ring_on"), 8, 2, (t, f) -> {
                    t.fill(Decor.hex(0x18181A));
                    int[][] ring = {{1, 0}, {2, 0}, {3, 1}, {3, 2}, {2, 3}, {1, 3}, {0, 2}, {0, 1}};
                    for (int k = 0; k < ring.length; k++) {
                        int d = Math.floorMod(k - f, ring.length);
                        int c = d == 0 ? 0x9FF4FF : d == 1 ? 0x48C8E8 : d == 2 ? 0x2A6F8A : 0x1D3A48;
                        t.px(ring[k][0], ring[k][1], Decor.hex(c));
                    }
                });
                top.glow(10);
            } else {
                model.paint(top, U, Decor.detail("radio", 5, "ring"), t -> {
                    t.fill(Decor.hex(0x18181A));
                    t.frameRect(0, 0, 4, 4, Decor.hex(0x26272A));
                });
            }
        }

        /** A turntable's platter with a record on it, its label turning while it plays. */
        private static void platter(Decor.Model model, float x0, float y, float z0, float x1, float z1, String rim, String piece, int era, boolean on) {
            Decor.Element platter = model.box(x0, y, z0, x1, y + 0.5F, z1, rim);
            java.util.function.BiConsumer<Decor.Tex, Integer> record = (t, f) -> {
                double c = t.w / 2.0;
                t.fill(DecorPaint.color(rim.substring(rim.lastIndexOf('/') + 1)));
                t.disc(c, c, c, Decor.hex(0x161616));
                t.ring(c, c, c * 0.55, c * 0.6, Decor.hex(0x2C2C2C));
                t.ring(c, c, c * 0.8, c * 0.85, Decor.hex(0x262626));
                t.disc(c, c, c * 0.32, Decor.hex(era == 1 ? 0xC8303A : 0xE8A33B));
                double a = f * Math.PI * 2 / 8;
                t.px((int) Math.floor(c + Math.cos(a) * c * 0.2), (int) Math.floor(c + Math.sin(a) * c * 0.2), Decor.hex(0xF4F4F4));
                t.px((int) Math.floor(c), (int) Math.floor(c), Decor.hex(0xBFC3C8));
            };
            if (on) {
                model.paintAnimated(platter, U, Decor.detail(piece, era, "record_on"), 8, 1, record);
            } else {
                model.paint(platter, U, Decor.detail(piece, era, "record"), t -> record.accept(t, 0));
            }
            float mx = (x0 + x1) / 2;
            float mz = (z0 + z1) / 2;
            model.box(mx - 0.25F, y + 0.5F, mz - 0.25F, mx + 0.25F, y + 1.25F, mz + 0.25F, m("chrome"));
        }
    }

    // ---------------------------------------------------------------- telephone

    static final class Telephone extends Decor.EraPiece {
        Telephone() {
            super("telephone", false);
        }

        @Override
        Decor.Model model(int era, boolean on) {
            Decor.Model model = new Decor.Model();
            switch (era) {
                case 0 -> rotary(model);
                case 1 -> princess(model);
                case 2 -> pushButton(model);
                case 3 -> desk(model);
                case 4 -> cordless(model);
                default -> smartphone(model);
            }
            return model;
        }

        /** The black rotary desk phone: the dial on its front, the handset across its cradle. */
        private void rotary(Decor.Model model) {
            String bakelite = m("bakelite_black");
            Decor.Element base = model.box(4, 0, 5, 12, 4, 11, bakelite);
            model.box(4.5F, 4, 5.5F, 11.5F, 4.5F, 10.5F, bakelite);
            Decor.Element dial = front(model, base, 2, 0, 6, 4, 0.4F).sides(bakelite);
            model.paint(dial, N, Decor.detail("telephone", 0, "dial"), t -> {
                t.fill(Decor.hex(0x1C1A19));
                t.disc(2, 2, 2, Decor.hex(0xE9E4D6));
                t.px(1, 0, Decor.hex(0x1C1A19)).px(0, 1, Decor.hex(0x1C1A19)).px(3, 1, Decor.hex(0x1C1A19)).px(0, 2, Decor.hex(0x1C1A19));
                t.px(1, 3, Decor.hex(0x1C1A19)).px(2, 3, Decor.hex(0x1C1A19));
                t.px(1, 1, Decor.hex(0xF6F2E6)).px(2, 2, Decor.hex(0xC8303A));
            });
            model.box(5, 4.5F, 7, 6, 5.25F, 9, bakelite);
            model.box(10, 4.5F, 7, 11, 5.25F, 9, bakelite);
            handset(model, 4, 5.25F, 7.25F, 12, bakelite);
            model.box(12, 0, 7.75F, 12.5F, 3, 8.25F, bakelite);
        }

        /** A slim princess phone in pink, its little lit dial on top. */
        private void princess(Decor.Model model) {
            String pink = m("plastic_pink");
            model.box(5, 0, 5.5F, 11, 2.5F, 10.5F, pink);
            model.box(5.5F, 0, 5, 10.5F, 2.5F, 11, pink);
            Decor.Element dial = model.box(6.5F, 2.5F, 5.5F, 9.5F, 2.75F, 8.5F, m("plastic_cream"));
            model.paint(dial, U, Decor.detail("telephone", 1, "dial"), t -> {
                t.fill(Decor.hex(0xF2EAD3));
                t.px(1, 1, Decor.hex(0xC8A0A8));
                t.px(0, 0, Decor.hex(0xD8B4BC)).px(2, 0, Decor.hex(0xD8B4BC)).px(0, 2, Decor.hex(0xD8B4BC)).px(2, 2, Decor.hex(0xD8B4BC));
            });
            handset(model, 4.5F, 2.5F, 8.5F, 11.5F, pink);
        }

        /** A push-button phone in harvest gold, its keys in a block of cream. */
        private void pushButton(Decor.Model model) {
            String gold = m("plastic_gold");
            model.box(4, 0, 5, 12, 3, 11, gold);
            Decor.Element keys = model.box(5.5F, 3, 5.25F, 10.5F, 3.25F, 8.75F, m("plastic_brown"));
            model.paint(keys, U, Decor.detail("telephone", 2, "keys"), t -> {
                t.fill(Decor.hex(0x6B4423));
                for (int y = 0; y < 4; y++) {
                    for (int x = 0; x < 5; x += 2) {
                        t.px(x, y, Decor.hex(0xF2EAD3));
                    }
                }
            });
            handset(model, 4, 3, 8.75F, 12, gold);
        }

        /** An almond desk phone: a keypad and a little display, its handset at the back. */
        private void desk(Decor.Model model) {
            String almond = m("plastic_almond");
            model.box(4, 0, 5, 12, 2.5F, 11, almond);
            model.box(4, 2.5F, 7.5F, 12, 3.5F, 11, almond);
            Decor.Element pad = model.box(5, 2.5F, 5.5F, 11, 2.75F, 7.5F, almond);
            model.paint(pad, U, Decor.detail("telephone", 3, "keys"), t -> {
                t.fill(DecorPaint.color("plastic_almond"));
                t.hline(0, 2, 0, Decor.hex(0x4A5A3A));
                for (int x = 3; x < 6; x++) {
                    t.px(x, 0, Decor.hex(0x8A7A5E)).px(x, 1, (x % 2 == 0) ? Decor.hex(0x8A7A5E) : Decor.hex(0xB8A88A));
                }
                t.hline(0, 2, 1, Decor.hex(0x8A7A5E));
            });
            handset(model, 4.5F, 3.5F, 8, 11.5F, almond);
        }

        /** A cordless handset standing in its base, its screen lit blue over the keys. */
        private void cordless(Decor.Model model) {
            String black = m("plastic_black");
            model.box(5, 0, 6, 11, 1.5F, 11, black);
            model.box(6, 1.5F, 9, 10, 4, 11, black);
            Decor.Element handset = model.box(6.5F, 1, 7, 9.5F, 9, 9, m("plastic_silver"));
            model.paint(handset, N, Decor.detail("telephone", 4, "handset"), t -> {
                t.fill(DecorPaint.color("plastic_silver"));
                t.rect(0, 1, 3, 2, Decor.hex(0x5FA8E8));
                for (int y = 4; y < 8; y++) {
                    for (int x = 0; x < 3; x++) {
                        t.px(x, y, (x + y) % 2 == 0 ? Decor.hex(0x3A3D42) : Decor.hex(0x8A8D93));
                    }
                }
            });
            model.box(8.5F, 9, 7.5F, 9.25F, 10, 8.25F, black);
            model.box(8.75F, 1.5F, 10.9F, 9.25F, 2, 11.1F, m("led_green")).glow(6);
        }

        /** A phone lying face up on its charging pad, the time on its lock screen. */
        private void smartphone(Decor.Model model) {
            model.box(5.5F, 0, 5, 10.5F, 0.75F, 11, m("plastic_white"));
            model.box(5, 0, 5.5F, 11, 0.75F, 10.5F, m("plastic_white"));
            Decor.Element phone = model.box(5.5F, 0.75F, 4, 10.5F, 1.25F, 12, m("plastic_black"));
            model.paint(phone, U, Decor.detail("telephone", 5, "screen"), t -> {
                for (int y = 0; y < t.h; y++) {
                    t.hline(0, t.w - 1, y, Decor.mix(Decor.hex(0x2B3B78), Decor.hex(0x8A3B78), y / (double) t.h));
                }
                t.hline(1, 3, 2, Decor.hex(0xFFFFFF));
                t.hline(1, 2, 3, Decor.hex(0xD8E0FF));
                t.rect(1, 5, 3, 1, Decor.hex(0xE8ECF4));
                t.px(2, 7, Decor.hex(0xFFFFFF));
            });
            phone.glow(5);
        }

        /** A handset laid across its cradle: the handle and a cup at either end. */
        private static void handset(Decor.Model model, float x0, float y, float z, float x1, String material) {
            model.box(x0 + 1, y, z, x1 - 1, y + 1.25F, z + 1.5F, material);
            model.box(x0 - 0.5F, y - 0.5F, z - 0.5F, x0 + 1.5F, y + 1.5F, z + 2, material);
            model.box(x1 - 1.5F, y - 0.5F, z - 0.5F, x1 + 0.5F, y + 1.5F, z + 2, material);
        }
    }

    // ---------------------------------------------------------------- seating

    /**
     * How a sofa of an era is built: the height of its legs, seat and back, how wide and high its arms, and what it
     * is covered in.
     */
    record Sofa(float legs, String legMaterial, float seatBase, float seatTop, float backTop, float armTop, float armWidth,
                String frame, String cushion, String back, String arm, String pillow) {
    }

    static final Sofa[] SOFAS = {
            // a powder blue sofa, its back buttoned, rolled arms, short walnut legs
            new Sofa(2, "walnut", 4, 7, 14, 10, 2.5F, "fabric_powder_blue", "fabric_powder_blue", "tufted_powder_blue", "fabric_powder_blue", null),
            // low and slim on tapered teak legs, teak arms, teal cushions
            new Sofa(3, "teak", 4.5F, 6.5F, 12, 8.5F, 1.5F, "teak", "fabric_teal", "fabric_teal", "teak", "fabric_mustard"),
            // chunky plaid down to the floor, rust corduroy cushions
            new Sofa(0, null, 4, 7.5F, 14, 10.5F, 3, "plaid_brown", "corduroy_rust", "corduroy_rust", "plaid_brown", null),
            // dusty rose with bolster arms, the cushions in confetti print, black block feet
            new Sofa(1, "plastic_black", 4, 7, 13.5F, 9, 3, "fabric_rose", "fabric_memphis", "fabric_memphis", "fabric_rose", null),
            // beige microfiber, overstuffed, with throw pillows
            new Sofa(1, "black_ash", 4, 8, 15, 11, 3.5F, "microfiber_beige", "microfiber_beige", "microfiber_beige", "microfiber_beige", "fabric_red"),
            // a low grey linen modular on thin black legs, a mustard cushion
            new Sofa(2, "black_metal", 3.5F, 6.5F, 12, 8.5F, 2, "linen_grey", "linen_grey", "linen_grey", "linen_grey", "linen_mustard")
    };

    /**
     * One length of sofa, with an arm on its left, its right, both, or neither, where it runs on into the next.
     * Facing north, whoever sits on it faces north: their left is west.
     */
    static Decor.Model sofa(Sofa s, int era, boolean leftArm, boolean rightArm) {
        Decor.Model model = new Decor.Model();
        float x0 = leftArm ? s.armWidth() : 0;
        float x1 = rightArm ? 16 - s.armWidth() : 16;
        String frame = m(s.frame());
        model.box(x0, s.legs(), 1, x1, s.seatBase(), 15, frame);
        Decor.Element seat = model.box(x0, s.seatBase(), 1.5F, x1, s.seatTop(), 12.5F, m(s.cushion()));
        seat.face(N, m(s.cushion()));
        Decor.Element back = model.box(x0, s.seatBase(), 12.5F, x1, s.backTop(), 15.5F, m(s.back()));
        back.face(S, frame);
        if (era == 1) {
            // a loose back cushion on a teak frame
            model.box(x0, s.seatTop(), 11.5F, x1, s.backTop() - 0.5F, 12.5F, m(s.cushion()));
        }
        if (leftArm) {
            arm(model, s, era, 0, s.armWidth());
        }
        if (rightArm) {
            arm(model, s, era, 16 - s.armWidth(), 16);
        }
        if (s.legs() > 0) {
            String leg = m(s.legMaterial());
            float w = era == 5 ? 0.75F : 1;
            if (leftArm) {
                model.box(1, 0, 2, 1 + w, s.legs(), 2 + w, leg);
                model.box(1, 0, 13.5F - w, 1 + w, s.legs(), 13.5F, leg);
            }
            if (rightArm) {
                model.box(15 - w, 0, 2, 15, s.legs(), 2 + w, leg);
                model.box(15 - w, 0, 13.5F - w, 15, s.legs(), 13.5F, leg);
            }
        }
        if (s.pillow() != null && (leftArm ^ rightArm || leftArm) && era != 1) {
            // a throw pillow tucked into the corner by the arm
            String pillow = m(s.pillow());
            if (leftArm) {
                model.box(x0 + 0.25F, s.seatTop(), 10.5F, x0 + 4, s.seatTop() + 4, 12, pillow).rotate("y", -22.5F, x0 + 2, s.seatTop(), 11);
            }
            if (rightArm && !leftArm) {
                model.box(x1 - 4, s.seatTop(), 10.5F, x1 - 0.25F, s.seatTop() + 4, 12, pillow).rotate("y", 22.5F, x1 - 2, s.seatTop(), 11);
            }
        }
        if (era == 1 && s.pillow() != null && leftArm) {
            model.box(x0 + 0.5F, s.seatTop(), 10, x0 + 3.5F, s.seatTop() + 3, 11.5F, m(s.pillow()));
        }
        return model;
    }

    private static void arm(Decor.Model model, Sofa s, int era, float x0, float x1) {
        String arm = m(s.arm());
        switch (era) {
            case 0 -> {
                // rolled: the top swells out over the arm
                model.box(x0, s.legs(), 1, x1, s.armTop() - 1, 15.5F, arm);
                model.box(x0 - (x0 == 0 ? 0 : 0.25F), s.armTop() - 1.5F, 1, x1 + (x0 == 0 ? 0.25F : 0), s.armTop(), 14, arm);
            }
            case 1 -> model.box(x0, s.seatBase(), 2, x1, s.armTop(), 14.5F, arm);
            case 3 -> {
                // a bolster along the top
                model.box(x0, s.legs(), 1, x1, s.armTop() - 1.5F, 15.5F, arm);
                model.box(x0 + 0.25F, s.armTop() - 1.5F, 0.75F, x1 - 0.25F, s.armTop(), 15, m("fabric_memphis"));
            }
            case 4 -> {
                // overstuffed: the arm puffed out at the top
                model.box(x0, s.legs(), 1, x1, s.armTop() - 1, 15.5F, arm);
                model.box(x0 - 0.25F, s.armTop() - 2, 0.5F, x1 + 0.25F, s.armTop(), 15, arm);
            }
            default -> model.box(x0, s.legs(), 1, x1, s.armTop(), 15.5F, arm);
        }
    }

    static final class Couch extends Decor.Piece {
        static final String[] PARTS = {"single", "left", "middle", "right"};

        Couch() {
            super("couch");
            property("part", PARTS);
        }

        @Override
        void build() {
            for (int era = 0; era < Decor.ERAS.length; era++) {
                Sofa s = SOFAS[era];
                put(Decor.ERAS[era] + "_single", sofa(s, era, true, true));
                put(Decor.ERAS[era] + "_left", sofa(s, era, true, false));
                put(Decor.ERAS[era] + "_middle", sofa(s, era, false, false));
                put(Decor.ERAS[era] + "_right", sofa(s, era, false, true));
            }
        }

        @Override
        String model(String era, Map<String, String> values) {
            return era + "_" + values.get("part");
        }
    }

    static final class Armchair extends Decor.EraPiece {
        Armchair() {
            super("armchair", false);
        }

        @Override
        Decor.Model model(int era, boolean on) {
            return switch (era) {
                case 0 -> wingback();
                case 1 -> lounge();
                case 2 -> recliner();
                case 3 -> rattan();
                case 4 -> sofa(new Sofa(1, "black_ash", 4, 8, 15, 11, 3.5F, "microfiber_taupe", "microfiber_taupe", "microfiber_taupe",
                        "microfiber_taupe", null), 4, true, true);
                default -> accent();
            };
        }

        /** A wingback: tall, buttoned, its wings reaching forward beside the head. */
        private Decor.Model wingback() {
            Decor.Model model = sofa(new Sofa(2, "walnut", 4, 7, 15.5F, 10, 2.5F, "fabric_cream", "fabric_cream", "tufted_powder_blue",
                    "fabric_cream", null), 0, true, true);
            model.box(0.5F, 9, 10, 2.5F, 15.5F, 15.5F, m("fabric_cream"));
            model.box(13.5F, 9, 10, 15.5F, 15.5F, 15.5F, m("fabric_cream"));
            return model;
        }

        /** A lounge chair: a molded teak shell over black leather, on a swivel base. */
        private Decor.Model lounge() {
            Decor.Model model = new Decor.Model();
            String metal = m("black_metal");
            model.box(7, 0, 7, 9, 3, 9, metal);
            model.box(2.5F, 0, 7.5F, 13.5F, 0.75F, 8.5F, metal);
            model.box(7.5F, 0, 2.5F, 8.5F, 0.75F, 13.5F, metal);
            model.box(2, 3, 1.5F, 14, 4.5F, 12, m("teak"));
            model.box(2.5F, 4.5F, 2, 13.5F, 6, 11.5F, m("leather_black"));
            model.box(2, 4.5F, 11, 14, 14, 13, m("teak")).rotate("x", -15, 8, 4.5F, 12);
            model.box(2.5F, 5, 10, 13.5F, 13.5F, 11, m("leather_black")).rotate("x", -15, 8, 4.5F, 12);
            model.box(1.5F, 6, 3, 2.5F, 7, 10, m("leather_black"));
            model.box(13.5F, 6, 3, 14.5F, 7, 10, m("leather_black"));
            model.box(1.75F, 4.5F, 4, 2.25F, 6, 4.5F, metal);
            model.box(13.75F, 4.5F, 4, 14.25F, 6, 4.5F, metal);
            return model;
        }

        /** A recliner in brown corduroy, chunky, with its lever at the side. */
        private Decor.Model recliner() {
            Decor.Model model = sofa(new Sofa(0, null, 4, 7.5F, 15, 10.5F, 3, "corduroy_brown", "corduroy_brown", "corduroy_brown",
                    "corduroy_brown", null), 2, true, true);
            model.box(1, 0, 0.5F, 15, 4, 1.5F, m("corduroy_brown"));
            model.box(15.9F, 5, 5, 16.4F, 7, 5.5F, m("walnut"));
            return model;
        }

        /** A rattan armchair with rose cushions. */
        private Decor.Model rattan() {
            Decor.Model model = new Decor.Model();
            String cane = m("rattan");
            for (float[] leg : new float[][] {{1, 1.5F}, {13.5F, 1.5F}, {1, 12.5F}, {13.5F, 12.5F}}) {
                model.box(leg[0], 0, leg[1], leg[0] + 1.5F, 3, leg[1] + 1.5F, cane);
            }
            model.box(1, 3, 1.5F, 15, 4.5F, 14, cane);
            model.box(1, 4.5F, 12.5F, 15, 15, 14.5F, cane);
            model.box(3, 15, 12.5F, 13, 16, 14.5F, cane);
            model.box(1, 4.5F, 1.5F, 2.5F, 9, 12.5F, cane);
            model.box(13.5F, 4.5F, 1.5F, 15, 9, 12.5F, cane);
            model.box(2.5F, 4.5F, 2, 13.5F, 6, 12.5F, m("fabric_rose"));
            model.box(2.5F, 6, 11.5F, 13.5F, 13, 12.5F, m("fabric_rose"));
            return model;
        }

        /** A sage accent chair on slender oak legs, its back curving round to low arms. */
        private Decor.Model accent() {
            Decor.Model model = new Decor.Model();
            String oak = m("ash");
            model.box(2, 0, 2, 3, 4, 3, oak).rotate("x", 10, 2.5F, 4, 2.5F);
            model.box(13, 0, 2, 14, 4, 3, oak).rotate("x", 10, 13.5F, 4, 2.5F);
            model.box(2, 0, 12, 3, 4, 13, oak).rotate("x", -10, 2.5F, 4, 12.5F);
            model.box(13, 0, 12, 14, 4, 13, oak).rotate("x", -10, 13.5F, 4, 12.5F);
            String sage = m("linen_sage");
            model.box(1.5F, 4, 1.5F, 14.5F, 7, 13, sage);
            model.box(1.5F, 7, 12, 14.5F, 14, 14, sage);
            model.box(1.5F, 7, 2.5F, 3, 10, 12.5F, sage);
            model.box(13, 7, 2.5F, 14.5F, 10, 12.5F, sage);
            return model;
        }
    }

    // ---------------------------------------------------------------- lamp

    static final class Lamp extends Decor.EraPiece {
        Lamp() {
            super("lamp", true);
        }

        @Override
        Decor.Model model(int era, boolean on) {
            Decor.Model model = new Decor.Model();
            String lit = on ? "_lit" : "";
            switch (era) {
                case 0 -> {
                    // a turquoise ceramic lamp on a brass foot, a tall tapered shade
                    model.box(6, 0, 6, 10, 0.75F, 10, m("brass"));
                    model.box(6.5F, 0.75F, 6.5F, 9.5F, 6, 9.5F, m("ceramic_turquoise"));
                    model.box(6, 2, 6, 10, 4.5F, 10, m("ceramic_turquoise"));
                    model.box(7.5F, 6, 7.5F, 8.5F, 7.5F, 8.5F, m("brass"));
                    shade(model, model.box(4, 7.5F, 4, 12, 10, 12, m("shade_cream" + lit)), on);
                    shade(model, model.box(4.5F, 10, 4.5F, 11.5F, 13, 11.5F, m("shade_cream" + lit)), on);
                }
                case 1 -> {
                    // a mushroom lamp: a chrome stem under a white glass dome
                    model.box(6, 0, 6, 10, 0.5F, 10, m("chrome"));
                    model.box(7.5F, 0.5F, 7.5F, 8.5F, 6, 8.5F, m("chrome"));
                    shade(model, model.box(4.5F, 6, 4.5F, 11.5F, 8, 11.5F, m("shade_white" + lit)), on);
                    shade(model, model.box(5, 8, 5, 11, 9.5F, 11, m("shade_white" + lit)), on);
                    shade(model, model.box(6, 9.5F, 6, 10, 10.25F, 10, m("shade_white" + lit)), on);
                }
                case 2 -> lavaLamp(model, on);
                case 3 -> {
                    // a black column under a stepped pink cone, a teal ring and a yellow ball on top
                    model.box(6.5F, 0, 6.5F, 9.5F, 5, 9.5F, m("plastic_black"));
                    model.box(6, 4.5F, 6, 10, 5, 10, m("plastic_teal"));
                    shade(model, model.box(5, 5, 5, 11, 7, 11, m("shade_pink" + lit)), on);
                    shade(model, model.box(5.5F, 7, 5.5F, 10.5F, 9, 10.5F, m("shade_pink" + lit)), on);
                    shade(model, model.box(6.5F, 9, 6.5F, 9.5F, 10.5F, 9.5F, m("shade_pink" + lit)), on);
                    model.box(7.25F, 10.5F, 7.25F, 8.75F, 12, 8.75F, m("plastic_yellow"));
                }
                case 4 -> {
                    // a brushed nickel stick lamp with a white drum shade
                    model.box(6, 0, 6, 10, 0.5F, 10, m("nickel"));
                    model.box(7.5F, 0.5F, 7.5F, 8.5F, 8, 8.5F, m("nickel"));
                    shade(model, model.box(4.5F, 8, 4.5F, 11.5F, 13, 11.5F, m("shade_white" + lit)), on);
                }
                default -> {
                    // a white glass globe on a black foot
                    model.box(6, 0, 6, 10, 1, 10, m("black_metal"));
                    shade(model, model.box(6, 1, 6, 10, 1.5F, 10, m("shade_white" + lit)), on);
                    shade(model, model.box(5, 1.5F, 5, 11, 5.5F, 11, m("shade_white" + lit)), on);
                    shade(model, model.box(6, 5.5F, 6, 10, 6.5F, 10, m("shade_white" + lit)), on);
                }
            }
            return model;
        }

        private static void shade(Decor.Model model, Decor.Element shade, boolean on) {
            if (on) {
                shade.glow(14).flat();
            }
        }

        /** A lava lamp: a gold cone under a bottle of yellow, red wax rising and falling in it while it is on. */
        private void lavaLamp(Decor.Model model, boolean on) {
            String gold = m("gold");
            model.box(6, 0, 6, 10, 1, 10, gold);
            model.box(6.5F, 1, 6.5F, 9.5F, 3, 9.5F, gold);
            Decor.Element bottle = model.box(6.5F, 3, 6.5F, 9.5F, 10.5F, 9.5F);
            for (Decor.Face face : new Decor.Face[] {N, S, W, E}) {
                int offset = face.ordinal();
                if (on) {
                    model.paintAnimated(bottle, face, Decor.detail("lamp", 2, "lava_on"), 16, 3, (t, f) -> lava(t, f, true));
                } else {
                    model.paint(bottle, face, Decor.detail("lamp", 2, "lava"), t -> lava(t, offset, false));
                }
            }
            if (on) {
                bottle.glow(12).flat();
            }
            model.box(7, 10.5F, 7, 9, 12, 9, gold);
            model.box(7.5F, 12, 7.5F, 8.5F, 12.5F, 8.5F, gold);
        }

        private static void lava(Decor.Tex t, int f, boolean on) {
            int liquid = on ? 0xF6C33A : 0xB08A3A;
            int wax = on ? 0xE5452B : 0x8A3A2A;
            for (int y = 0; y < t.h; y++) {
                t.hline(0, t.w - 1, y, Decor.shade(Decor.hex(liquid), 1.05 - y * 0.02));
            }
            // two blobs, one rising as the other sinks
            double rise = (f % 16) / 16.0;
            double a = t.h - 1 - rise * (t.h - 1);
            double b = rise * (t.h - 1);
            t.disc(1.2, a, 1.1, Decor.hex(wax));
            t.disc(2.0, b, 0.9, Decor.hex(wax));
            t.rect(0, t.h - 1, t.w, 1, Decor.hex(wax));
        }
    }
}
