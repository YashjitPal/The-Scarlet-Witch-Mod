import java.util.List;

/**
 * What hangs on the walls: a clock, a framed picture and a poster advertising something of its day. Each stands
 * against the wall behind it, its back to the south when it faces north.
 */
final class DecorWall {

    static final Decor.Face N = Decor.Face.NORTH;

    private DecorWall() {
    }

    static List<Decor.Piece> pieces() {
        return List.of(new Clock(), new Picture(), new Poster());
    }

    static String m(String name) {
        return Decor.mat(name);
    }

    static abstract class WallPiece extends Decor.EraPiece {
        WallPiece(String id) {
            super(id, false);
            onWall = true;
        }
    }

    // ---------------------------------------------------------------- clock

    static final class Clock extends WallPiece {
        Clock() {
            super("wall_clock");
        }

        @Override
        Decor.Model model(int era, boolean on) {
            Decor.Model model = new Decor.Model();
            switch (era) {
                case 0 -> sunburst(model);
                case 1 -> ballClock(model);
                case 2 -> pendulum(model);
                case 3 -> round(model, 3, 3, 13, "plastic_neon_pink", 0xF6F4EE, 0x2E9C9A, 0x262426, false);
                case 4 -> round(model, 4, 3, 13, "brushed_steel", 0xF4F4F2, 0x1F1F22, 0x1F1F22, true);
                default -> round(model, 5, 1.5F, 14.5F, "plastic_black", 0xF2F2F0, 0x1A1A1C, 0xC8C8C4, false);
            }
            model.particle(model.elements.getFirst().faces.values().iterator().next().texture());
            return model;
        }

        /** The atomic sunburst: brass rays out from a cream face, a little ball at the end of every other one. */
        private void sunburst(Decor.Model model) {
            Decor.Element rays = model.box(0, 0, 15.4F, 16, 16, 15.6F);
            model.paint(rays, N, Decor.detail("wall_clock", 0, "rays"), t -> {
                int brass = DecorPaint.color("brass");
                for (int k = 0; k < 16; k++) {
                    double a = k * Math.PI * 2 / 16;
                    double len = k % 2 == 0 ? 7.6 : 6.0;
                    t.line(8, 8, 8 + Math.sin(a) * len, 8 - Math.cos(a) * len, k % 2 == 0 ? Decor.hex(brass) : Decor.shade(Decor.hex(brass), 0.8));
                    if (k % 2 == 0) {
                        t.px((int) Math.floor(8 + Math.sin(a) * 7.4), (int) Math.floor(8 - Math.cos(a) * 7.4), Decor.tint(Decor.hex(brass), 0.4));
                    }
                }
            });
            rays.only(N);
            Decor.Element face = model.box(5, 5, 14.75F, 11, 11, 15.75F, m("brass"));
            model.paint(face, N, Decor.detail("wall_clock", 0, "face"), t -> {
                t.disc(3, 3, 3, Decor.hex(0xC9A54E));
                DecorPaint.clockFace(t, 3, 3, 2.4, 0xF2EAD3, 0x1C1A19, 0x8C6A2E, false);
            });
        }

        /** A ball clock: spokes out from the middle, a colored ball at the end of each. */
        private void ballClock(Decor.Model model) {
            Decor.Element spokes = model.box(0, 0, 15.4F, 16, 16, 15.6F);
            model.paint(spokes, N, Decor.detail("wall_clock", 1, "spokes"), t -> {
                int[] balls = {0xE07A2E, 0x3FB8AF, 0xD9A93A, 0xC8303A, 0x3D6FB6, 0x7C8C3C};
                for (int k = 0; k < 12; k++) {
                    double a = k * Math.PI * 2 / 12;
                    double x = 8 + Math.sin(a) * 6.2;
                    double y = 8 - Math.cos(a) * 6.2;
                    t.line(8, 8, x, y, Decor.hex(0x3A3A3A));
                    t.disc(x, y, 1.2, Decor.hex(balls[k % balls.length]));
                }
            });
            spokes.only(N);
            Decor.Element hub = model.box(6, 6, 14.75F, 10, 10, 15.75F, m("teak"));
            model.paint(hub, N, Decor.detail("wall_clock", 1, "face"), t -> {
                t.disc(2, 2, 2, Decor.hex(DecorPaint.color("teak")));
                DecorPaint.clockFace(t, 2, 2, 1.6, 0xEDE6D1, 0x1C1A19, 0x1C1A19, false);
            });
        }

        /** A wooden wall clock with a pendulum swinging behind its glass. */
        private void pendulum(Decor.Model model) {
            Decor.Element body = model.box(4, 1, 14, 12, 15, 16, m("veneer"));
            model.paintAnimated(body, N, Decor.detail("wall_clock", 2, "front"), 8, 3, (t, f) -> {
                DecorPaint.under(t, "veneer", 4, 1);
                DecorPaint.clockFace(t, 4, 4, 3, 0xF2EAD3, 0x1C1A19, 0x6B4423, true);
                t.rect(2, 8, 4, 5, Decor.hex(0x2A1A10));
                double swing = Math.sin(f * Math.PI * 2 / 8) * 1.2;
                t.line(4, 8, 4 + swing * 0.6, 11, Decor.hex(0xC9A54E));
                t.disc(4 + swing, 11.5, 1.0, Decor.hex(0xD8B04A));
                t.px(2, 8, Decor.alpha(0xFFFFFF, 50)).px(3, 8, Decor.alpha(0xFFFFFF, 30));
            });
            model.box(3.5F, 14.5F, 13.5F, 12.5F, 15.5F, 16, m("veneer"));
        }

        /** A round clock: its rim, its face and hands, its marks. */
        private void round(Decor.Model model, int era, float from, float to, String rim, int face, int hands, int marks, boolean numerals) {
            // a disc: only its face, so no square edge shows round it from the side
            Decor.Element body = model.box(from, from, 15, to, to, 16, m(rim));
            body.only(N);
            model.paint(body, N, Decor.detail("wall_clock", era, "face"), t -> {
                double c = t.w / 2.0;
                t.disc(c, c, c, Decor.hex(DecorPaint.color(rim)));
                DecorPaint.clockFace(t, c, c, c - 1, face, hands, marks, true);
                if (numerals) {
                    t.px((int) c - 1, 1, Decor.hex(marks)).px((int) c, 1, Decor.hex(marks));
                }
            });
        }
    }

    // ---------------------------------------------------------------- picture

    static final class Picture extends WallPiece {
        Picture() {
            super("picture_frame");
        }

        @Override
        Decor.Model model(int era, boolean on) {
            Decor.Model model = new Decor.Model();
            String[] frames = {"gold", "chrome", "walnut", "chrome", "plastic_black", "ash"};
            Decor.Element frame = model.box(2, 3, 15, 14, 13, 16, m(frames[era]));
            frame.without(Decor.Face.SOUTH);
            model.paint(frame, N, Decor.detail("picture_frame", era, "picture"), t -> {
                DecorPaint.under(t, frames[era], 2, 3);
                int edge = Decor.shade(DecorPaint.color(frames[era]), 0.7);
                t.frameRect(0, 0, t.w, t.h, edge);
                picture(t, era);
            });
            return model;
        }

        /** What is in the frame, inside its edge. */
        private static void picture(Decor.Tex t, int era) {
            int x0 = era >= 4 ? 2 : 1;
            int y0 = era >= 4 ? 2 : 1;
            int w = t.w - x0 * 2;
            int h = t.h - y0 * 2;
            if (era >= 4) {
                t.rect(1, 1, t.w - 2, t.h - 2, Decor.hex(0xF6F4EE));
            }
            Decor.Tex p = new Decor.Tex(w, h);
            switch (era) {
                case 0 -> {
                    // a wedding portrait in black and white: the bride in white, the groom beside her
                    p.fill(Decor.hex(0x8A8A8A));
                    p.vignette(0, 0, w, h, 0.5);
                    p.rect(3, 3, 2, 5, Decor.hex(0xEDEDED)).rect(3, 2, 2, 1, Decor.hex(0xC8C8C8)).px(3, 1, Decor.hex(0xDADADA)).px(4, 1, Decor.hex(0xDADADA));
                    p.rect(6, 3, 2, 5, Decor.hex(0x2A2A2A)).rect(6, 2, 2, 1, Decor.hex(0xB8B8B8)).px(6, 1, Decor.hex(0x3A3A3A)).px(7, 1, Decor.hex(0x3A3A3A));
                    p.px(5, 5, Decor.hex(0xF6F6F6));
                }
                case 1 -> {
                    // a sailboat on a summer sea
                    p.fill(Decor.hex(0x8FC8E8));
                    p.rect(0, h - 3, w, 3, Decor.hex(0x2F6FB0));
                    p.hline(1, 3, h - 2, Decor.hex(0x8FC8E8)).hline(6, 8, h - 1, Decor.hex(0x8FC8E8));
                    p.disc(w - 2.5, 2, 1.2, Decor.hex(0xF7D23E));
                    p.vline(4, 1, h - 4, Decor.hex(0x5B3A24));
                    p.px(5, 2, Decor.hex(0xFFFFFF)).px(5, 3, Decor.hex(0xFFFFFF)).px(6, 3, Decor.hex(0xFFFFFF)).px(5, 4, Decor.hex(0xFFFFFF))
                            .px(6, 4, Decor.hex(0xFFFFFF)).px(7, 4, Decor.hex(0xFFFFFF));
                    p.hline(3, 6, h - 4, Decor.hex(0xC8303A));
                }
                case 2 -> {
                    // mountains under an orange sunset
                    for (int y = 0; y < h; y++) {
                        p.hline(0, w - 1, y, Decor.mix(Decor.hex(0xF2A33A), Decor.hex(0xC1601F), y / (double) h));
                    }
                    p.disc(w / 2.0, h / 2.0, 1.6, Decor.hex(0xFFE08A));
                    for (int x = 0; x < w; x++) {
                        int peak = (int) (h - 2 - Math.abs(Math.sin(x * 0.8)) * 3);
                        p.vline(x, peak, h - 1, Decor.hex(0x5A3A22));
                    }
                }
                case 3 -> {
                    // abstract shapes in the bold colors of the day
                    p.fill(Decor.hex(0xF6F4EE));
                    p.disc(3, 3, 2, Decor.hex(0xFF4FA3));
                    p.rect(6, 1, 3, 3, Decor.hex(0x2E9C9A));
                    p.px(2, h - 2, Decor.hex(0xF7D23E)).px(3, h - 2, Decor.hex(0xF7D23E)).px(2, h - 3, Decor.hex(0xF7D23E));
                    p.line(5, h - 2, w - 1, h - 4, Decor.hex(0x262426));
                }
                case 4 -> {
                    // the family at the beach
                    p.fill(Decor.hex(0x7EC8F0));
                    p.rect(0, h - 3, w, 3, Decor.hex(0xEAD6A0));
                    p.rect(0, h - 4, w, 1, Decor.hex(0x2F8FC8));
                    for (int k = 0; k < 3; k++) {
                        int x = 1 + k * 3;
                        int top = h - 6 + (k == 1 ? 1 : 0);
                        p.vline(x, top, h - 2, Decor.hex(new int[] {0xC8303A, 0x3D6FB6, 0xF7D23E}[k])).px(x, top - 1, Decor.hex(0xE0A27A));
                    }
                }
                default -> {
                    // line art: a face drawn in one stroke
                    p.fill(Decor.hex(0xE9E2D4));
                    p.line(2, 1, 1, 4, Decor.hex(0x2A2A2A));
                    p.line(1, 4, 3, h - 1, Decor.hex(0x2A2A2A));
                    p.line(3, h - 1, 6, h - 2, Decor.hex(0x2A2A2A));
                    p.line(5, 2, 6, 2, Decor.hex(0x2A2A2A));
                    p.disc(w - 2.5, h - 2.5, 1.3, Decor.hex(0xC46E4F));
                }
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    t.px(x0 + x, y0 + y, p.get(x, y));
                }
            }
        }
    }

    // ---------------------------------------------------------------- poster

    static final class Poster extends WallPiece {
        Poster() {
            super("poster");
        }

        @Override
        Decor.Model model(int era, boolean on) {
            Decor.Model model = new Decor.Model();
            Decor.Element sheet = model.box(0.5F, 0, 15.5F, 15.5F, 16, 16);
            model.paint(sheet, N, Decor.detail("poster", era, "ad"), t -> ad(t, era));
            sheet.only(N);
            Decor.Element back = model.box(0.5F, 0, 15.75F, 15.5F, 16, 16, m("paper_white"));
            back.only(Decor.Face.WEST, Decor.Face.EAST, Decor.Face.UP, Decor.Face.DOWN);
            model.particle(m("paper_white"));
            return model;
        }

        /** Words across the middle of the sheet. */
        private static void centered(Decor.Tex t, int y, String words, int color) {
            t.text((t.w - Decor.Tex.textWidth(words)) / 2, y, words, color);
        }

        /** An advertisement in the style of its day, for something nobody ever sold. */
        private static void ad(Decor.Tex t, int era) {
            switch (era) {
                case 0 -> {
                    // a cola: cream with a red banner and a bottle
                    t.fill(Decor.hex(0xF2E6C8));
                    t.rect(0, 0, t.w, 6, Decor.hex(0xC8303A));
                    centered(t, 1, "COLA", Decor.hex(0xFFFFFF));
                    t.rect(6, 8, 3, 6, Decor.hex(0x5A2E1B));
                    t.rect(7, 7, 1, 1, Decor.hex(0x5A2E1B));
                    t.px(7, 6, Decor.hex(0xC8303A));
                    t.hline(6, 8, 10, Decor.hex(0xC8303A));
                    t.hline(1, 13, 15, Decor.hex(0xC8303A));
                }
                case 1 -> {
                    // travel to the moon
                    t.fill(Decor.hex(0x12205A));
                    for (int k = 0; k < 12; k++) {
                        t.px((int) (Decor.noise(9, k, 0) * t.w), (int) (Decor.noise(9, k, 1) * 9), Decor.hex(0xFFFFFF));
                    }
                    t.disc(11, 4, 2.6, Decor.hex(0xEDEDE0));
                    t.px(10, 3, Decor.hex(0xBDBDB0)).px(12, 5, Decor.hex(0xBDBDB0));
                    t.rect(4, 5, 2, 5, Decor.hex(0xEDEDED)).px(4, 4, Decor.hex(0xC8303A)).px(5, 4, Decor.hex(0xC8303A));
                    t.px(3, 9, Decor.hex(0xC8303A)).px(6, 9, Decor.hex(0xC8303A)).px(4, 10, Decor.hex(0xF7A23E)).px(5, 11, Decor.hex(0xF7D23E));
                    centered(t, 11, "MOON", Decor.hex(0xF7D23E));
                }
                case 2 -> {
                    // a night of funk: a mirror ball over bands of brown and orange
                    for (int y = 0; y < t.h; y++) {
                        int[] bands = {0x6B4423, 0xC1601F, 0xD9A43B};
                        t.hline(0, t.w - 1, y, Decor.hex(bands[(y / 3) % 3]));
                    }
                    t.disc(7.5, 4.5, 3, Decor.hex(0xBFC3C8));
                    t.px(6, 3, Decor.hex(0xFFFFFF)).px(8, 5, Decor.hex(0xFFFFFF)).px(7, 6, Decor.hex(0x8A8D93)).px(9, 3, Decor.hex(0x8A8D93));
                    t.rect(0, 10, t.w, 6, Decor.hex(0x3A2210));
                    centered(t, 10, "FUNK", Decor.hex(0xF7D23E));
                }
                case 3 -> {
                    // the arcade: a neon grid under a sunset, GAME in pink
                    t.fill(Decor.hex(0x14061F));
                    t.disc(7.5, 6, 3.5, Decor.hex(0xFF8A3D));
                    for (int y = 4; y < 8; y += 2) {
                        t.hline(0, t.w - 1, y, Decor.hex(0x14061F));
                    }
                    t.rect(0, 8, t.w, 8, Decor.hex(0x14061F));
                    for (int y = 9; y < 16; y += 2) {
                        t.hline(0, t.w - 1, y, Decor.hex(0x34E0E8));
                    }
                    centered(t, 1, "GAME", Decor.hex(0xFF4FA3));
                }
                case 4 -> {
                    // a flip phone: TXT ME
                    for (int y = 0; y < t.h; y++) {
                        t.hline(0, t.w - 1, y, Decor.mix(Decor.hex(0x1E5AA8), Decor.hex(0x0C1E3C), y / 16.0));
                    }
                    t.text(0, 1, "TXT", Decor.hex(0xFFFFFF));
                    t.text(1, 7, "ME", Decor.hex(0x8FD3FF));
                    t.rect(10, 5, 4, 10, Decor.hex(0xBFC3C8));
                    t.rect(11, 6, 2, 3, Decor.hex(0x8FD3FF));
                    t.hline(10, 13, 10, Decor.hex(0x6A6D72));
                    t.px(11, 12, Decor.hex(0x3A3D42)).px(12, 13, Decor.hex(0x3A3D42));
                }
                default -> {
                    // a show coming soon: a figure in a red glow, the title over it
                    for (int y = 0; y < t.h; y++) {
                        t.hline(0, t.w - 1, y, Decor.mix(Decor.hex(0x0E0F14), Decor.hex(0x7A1F3D), y / 16.0));
                    }
                    t.disc(7.5, 9, 4, Decor.alpha(0xE0143C, 120));
                    t.rect(6, 7, 3, 7, Decor.hex(0x0A0A0C));
                    t.rect(6, 5, 3, 2, Decor.hex(0x0A0A0C));
                    centered(t, 0, "SHOW", Decor.hex(0xFFFFFF));
                    t.hline(4, 10, 15, Decor.hex(0xE0143C));
                }
            }
        }
    }
}
