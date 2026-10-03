/**
 * Painted detail shared by the era decorations: screens off and on, knobs, grilles, dials and little displays.
 */
final class DecorPaint {

    private DecorPaint() {
    }

    /**
     * Lays a material under a painted face, from where that face would have taken it by its place on the block, so
     * the grain runs on from the faces around it.
     */
    static void under(Decor.Tex t, String material, int u0, int v0) {
        Decor.Tex m = Decor.TEXTURES.get("decor/" + material);
        for (int y = 0; y < t.h; y++) {
            for (int x = 0; x < t.w; x++) {
                t.px(x, y, m.get(Math.floorMod(u0 + x, 16), Math.floorMod(v0 + y, 16)));
            }
        }
    }

    /** The material's color, as one texel of it. */
    static int color(String material) {
        Decor.Tex m = Decor.TEXTURES.get("decor/" + material);
        return m.get(7, 7);
    }

    // ---------------------------------------------------------------- screens

    /**
     * A picture tube or panel switched off: dark glass, a little lighter toward the middle, with the room caught in a
     * soft highlight up in one corner. Rounded corners show the bezel behind.
     */
    static void screenOff(Decor.Tex t, int glass, int bezel, boolean rounded) {
        for (int y = 0; y < t.h; y++) {
            for (int x = 0; x < t.w; x++) {
                double dx = (x + 0.5) / t.w - 0.5;
                double dy = (y + 0.5) / t.h - 0.5;
                double k = 1.18 - (dx * dx + dy * dy) * 0.9;
                t.px(x, y, Decor.shade(Decor.hex(glass), k));
            }
        }
        // the reflection: a short diagonal streak up and left
        for (int k = 0; k < Math.min(t.w, t.h) / 2; k++) {
            t.px(1 + k, Math.min(t.h - 2, 1 + k / 2), Decor.alpha(0xFFFFFF, 46));
        }
        t.px(1, 1, Decor.alpha(0xFFFFFF, 70));
        if (rounded) {
            corners(t, Decor.hex(bezel));
        }
    }

    static void corners(Decor.Tex t, int bezel) {
        t.px(0, 0, bezel).px(t.w - 1, 0, bezel).px(0, t.h - 1, bezel).px(t.w - 1, t.h - 1, bezel);
    }

    /**
     * What is on: a show in the look of its era, frame by frame. Black and white sitcoms in the 1950s and 1960s, a
     * game show in warm 1970s color, a videotape in the 1980s, a bright talk show in the 2000s, and a streaming menu
     * today.
     */
    static void show(Decor.Tex t, int frame, int frames, int era, int bezel, boolean rounded) {
        switch (era) {
            case 0 -> sitcom(t, frame);
            case 1 -> cartoon(t, frame);
            case 2 -> gameShow(t, frame);
            case 3 -> videotape(t, frame, frames);
            case 4 -> talkShow(t, frame, frames);
            default -> streaming(t, frame, frames);
        }
        if (era <= 1) {
            // a picture tube: rows that flicker, a dark band rolling slowly down, and the edges falling off
            int band = Math.floorMod(frame * 2, t.h + 4) - 2;
            for (int y = 0; y < t.h; y++) {
                double row = Decor.noise(frame * 31L + era, 0, y) > 0.8 ? 1.12 : 1.0;
                if (Math.abs(y - band) <= 1) {
                    row *= 0.82;
                }
                for (int x = 0; x < t.w; x++) {
                    int c = t.get(x, y);
                    t.px[(t.frame * t.h + y) * t.w + x] = Decor.shade(c, row * (0.94 + Decor.noise(frame * 7L + era, x, y) * 0.12));
                }
            }
            t.grayscaleFrame();
            t.vignette(0, 0, t.w, t.h, 0.45);
        } else if (era <= 3) {
            t.vignette(0, 0, t.w, t.h, 0.3);
        }
        if (rounded) {
            corners(t, Decor.hex(bezel));
        }
    }

    /** A living room set: a couch along the bottom, two people on it turning to each other, a lamp beside them. */
    private static void sitcom(Decor.Tex t, int frame) {
        t.fill(Decor.hex(0x8C8C8C));
        int floor = t.h - 2;
        t.rect(0, floor, t.w, 2, Decor.hex(0x5E5E5E));
        // the back wall's paneling
        for (int x = 1; x < t.w; x += 3) {
            t.vline(x, 0, floor - 1, Decor.hex(0x9E9E9E));
        }
        // the couch
        t.rect(1, floor - 3, t.w - 2, 3, Decor.hex(0x3A3A3A));
        t.rect(1, floor - 4, 1, 1, Decor.hex(0x3A3A3A));
        t.rect(t.w - 2, floor - 4, 1, 1, Decor.hex(0x3A3A3A));
        // two heads and shoulders, nodding in turn
        int left = t.w / 3;
        int right = t.w - 1 - t.w / 3;
        int bobL = frame % 4 < 2 ? 0 : 1;
        int bobR = frame % 4 < 2 ? 1 : 0;
        t.rect(left - 1, floor - 5 + bobL, 3, 2, Decor.hex(0x202020));
        t.rect(left, floor - 7 + bobL, 1, 2, Decor.hex(0xD8D8D8));
        t.rect(right - 1, floor - 5 + bobR, 3, 2, Decor.hex(0xF0F0F0));
        t.rect(right, floor - 7 + bobR, 1, 2, Decor.hex(0xD0D0D0));
        t.px(right, floor - 8 + bobR, Decor.hex(0x303030));
        // a lamp
        if (t.w >= 9) {
            t.vline(t.w - 1, floor - 5, floor - 1, Decor.hex(0x404040));
            t.rect(t.w - 2, floor - 7, 2, 2, Decor.hex(0xF4F4F4));
        }
    }

    /** A cartoon: a smiling face that blinks, with stars twinkling round it. */
    private static void cartoon(Decor.Tex t, int frame) {
        t.fill(Decor.hex(0x707070));
        double cx = t.w / 2.0;
        double cy = t.h / 2.0 + 0.5;
        double r = Math.min(t.w, t.h) * 0.36;
        t.disc(cx, cy, r, Decor.hex(0xE6E6E6));
        t.ring(cx, cy, r - 0.7, r + 0.3, Decor.hex(0x202020));
        boolean blink = frame % 8 == 5;
        int eyeY = (int) Math.floor(cy - r * 0.3);
        int eyeL = (int) Math.floor(cx - r * 0.4);
        int eyeR = (int) Math.floor(cx + r * 0.3);
        if (blink) {
            t.px(eyeL, eyeY, Decor.hex(0x202020)).px(eyeR, eyeY, Decor.hex(0x202020));
        } else {
            t.rect(eyeL, eyeY - 1, 1, 2, Decor.hex(0x202020)).rect(eyeR, eyeY - 1, 1, 2, Decor.hex(0x202020));
        }
        int mouth = (int) Math.floor(cy + r * 0.35);
        t.hline((int) (cx - r * 0.35), (int) (cx + r * 0.3), mouth, Decor.hex(0x202020));
        t.px((int) (cx - r * 0.45), mouth - 1, Decor.hex(0x202020)).px((int) (cx + r * 0.4), mouth - 1, Decor.hex(0x202020));
        int[][] stars = {{1, 1}, {t.w - 2, 2}, {2, t.h - 2}, {t.w - 3, t.h - 3}};
        for (int k = 0; k < stars.length; k++) {
            if ((frame + k) % 3 != 0) {
                t.px(stars[k][0], stars[k][1], Decor.hex(0xFFFFFF));
            }
        }
    }

    /** A game show: a stage in orange and brown, the bulbs round its sign chasing, a host in the middle. */
    private static void gameShow(Decor.Tex t, int frame) {
        t.fill(Decor.hex(0x7A3B16));
        t.rect(0, 0, t.w, t.h / 2, Decor.hex(0xB35A1F));
        // the sign, its bulbs chasing round it
        int sx = 1;
        int sy = 1;
        int sw = t.w - 2;
        int sh = Math.max(3, t.h / 2 - 1);
        t.rect(sx, sy, sw, sh, Decor.hex(0xE8A33B));
        int k = 0;
        for (int x = sx; x < sx + sw; x++, k++) {
            t.px(x, sy, (k + frame) % 3 == 0 ? Decor.hex(0xFFF4C2) : Decor.hex(0xC97A22));
        }
        for (int x = sx + sw - 1; x >= sx; x--, k++) {
            t.px(x, sy + sh - 1, (k + frame) % 3 == 0 ? Decor.hex(0xFFF4C2) : Decor.hex(0xC97A22));
        }
        // the host
        int hx = t.w / 2;
        int floor = t.h - 1;
        t.rect(hx - 1, floor - 2, 3, 3, Decor.hex(0x3B2A6E));
        t.rect(hx, floor - 4, 1, 2, Decor.hex(0xE7B48A));
        // podiums
        t.rect(1, floor - 1, 2, 2, Decor.hex(0x2F6F8F));
        t.rect(t.w - 3, floor - 1, 2, 2, Decor.hex(0x2F6F8F));
        t.px(1 + frame % 2, floor - 1, Decor.hex(0xFFE27A));
    }

    /** A videotape: a neon sunset over a grid rolling toward the viewer, PLAY blinking in the corner, tracking noise. */
    private static void videotape(Decor.Tex t, int frame, int frames) {
        int horizon = t.h / 2;
        for (int y = 0; y < horizon; y++) {
            int c = Decor.mix(Decor.hex(0x2A0F4A), Decor.hex(0xFF4FA3), y / (double) Math.max(1, horizon - 1));
            t.hline(0, t.w - 1, y, c);
        }
        // the sun, cut by bands
        double sx = t.w / 2.0;
        t.disc(sx, horizon, Math.max(2, t.w * 0.22), Decor.hex(0xFFC24A));
        for (int y = horizon - 2; y < horizon; y += 2) {
            t.hline(0, t.w - 1, y, Decor.hex(0xE0427E));
        }
        t.rect(0, horizon, t.w, t.h - horizon, Decor.hex(0x14061F));
        // the grid, its rows coming on toward the viewer
        int shift = frame % 3;
        for (int y = horizon + shift; y < t.h; y += 3) {
            t.hline(0, t.w - 1, y, Decor.hex(0x34E0E8));
        }
        for (int x = 0; x <= t.w; x += 3) {
            t.line(sx + (x - sx) * 0.3, horizon, x, t.h - 1, Decor.hex(0x2BB7C0));
        }
        if (frame % 4 < 2) {
            t.px(1, 1, Decor.hex(0xFFFFFF)).px(1, 2, Decor.hex(0xFFFFFF)).px(2, 1, Decor.hex(0xFFFFFF)).px(2, 2, Decor.hex(0xFFFFFF))
                    .px(3, 2, Decor.hex(0xFFFFFF)).px(1, 3, Decor.hex(0xFFFFFF));
        }
        // a band of tracking noise drifting up
        int noisy = t.h - 1 - Math.floorMod(frame * 2, t.h);
        for (int x = 0; x < t.w; x++) {
            if (Decor.noise(frame * 13L, x, noisy) > 0.4) {
                t.px(x, noisy, Decor.alpha(0xFFFFFF, 140));
            }
        }
    }

    /** A talk show: a bright blue set, the host at the desk and a guest on the couch, a ticker crawling along the bottom. */
    private static void talkShow(Decor.Tex t, int frame, int frames) {
        t.fill(Decor.hex(0x2F74D0));
        for (int x = 0; x < t.w; x += 4) {
            t.vline(x, 0, t.h - 3, Decor.hex(0x3D86E3));
        }
        // a skyline behind them
        for (int x = 0; x < t.w; x++) {
            int hgt = 1 + (int) (Decor.noise(5, x / 2, 0) * 3);
            t.vline(x, t.h - 3 - hgt, t.h - 4, Decor.hex(0x1D4E96));
        }
        int desk = t.w / 3;
        t.rect(desk - 2, t.h - 5, 4, 2, Decor.hex(0x7A4A26));
        t.rect(desk, t.h - 8, 1, 3, Decor.hex(0x1F1F1F));
        t.rect(desk, t.h - 9, 1, 1, Decor.hex(0xE8B98F));
        int guest = t.w - 1 - t.w / 4;
        t.rect(guest - 1, t.h - 5, 3, 2, Decor.hex(0xC8303A));
        t.rect(guest, t.h - 7 + (frame % 4 == 1 ? 1 : 0), 1, 2, Decor.hex(0xE0A27A));
        // the ticker
        t.rect(0, t.h - 2, t.w, 2, Decor.hex(0xF4F4F4));
        t.rect(0, t.h - 2, 2, 2, Decor.hex(0xC8303A));
        for (int x = 2; x < t.w; x++) {
            if (Math.floorMod(x + frame, 5) < 3) {
                t.px(x, t.h - 1, Decor.hex(0x1F1F1F));
            }
        }
    }

    /** A streaming menu: a show's banner over a row of others, the highlight stepping along them. */
    private static void streaming(Decor.Tex t, int frame, int frames) {
        t.fill(Decor.hex(0x0E0F14));
        int bannerH = Math.max(3, t.h / 2);
        for (int y = 0; y < bannerH; y++) {
            for (int x = 0; x < t.w; x++) {
                double k = x / (double) Math.max(1, t.w - 1);
                int c = Decor.mix(Decor.hex(0x7A1F3D), Decor.hex(0xE0592A), k * 0.8 + y * 0.03);
                t.px(x, y, c);
            }
        }
        t.rect(1, bannerH - 2, Math.min(5, t.w - 2), 1, Decor.hex(0xFFFFFF));
        int tiles = Math.max(2, (t.w - 1) / 3);
        int lit = frame / 2 % tiles;
        int[] colors = {0x2E7D6B, 0x6B4FA0, 0xB8862E, 0x2F6FB0, 0xA0384C};
        for (int k = 0; k < tiles; k++) {
            int x = 1 + k * 3;
            int y = bannerH + 1;
            if (x + 1 >= t.w) {
                break;
            }
            t.rect(x, y, 2, Math.max(1, t.h - y - 1), Decor.hex(colors[k % colors.length]));
            if (k == lit) {
                t.frameRect(x - 1 < 0 ? 0 : x - 1, y - 1, 4 > t.w - x + 1 ? t.w - x + 1 : 4, Math.max(2, t.h - y + 1), Decor.hex(0xFFFFFF));
            }
        }
    }

    // ---------------------------------------------------------------- controls

    /** A round knob two texels across, lit from above. */
    static void knob(Decor.Tex t, int x, int y, int color) {
        t.px(x, y, Decor.tint(Decor.hex(color), 0.35)).px(x + 1, y, Decor.hex(color)).px(x, y + 1, Decor.hex(color))
                .px(x + 1, y + 1, Decor.shade(Decor.hex(color), 0.6));
    }

    /** A single texel button with a highlight. */
    static void button(Decor.Tex t, int x, int y, int color) {
        t.px(x, y, Decor.hex(color));
    }

    /** Parallel lines across a region, a grille over a speaker. */
    static void slots(Decor.Tex t, int x, int y, int w, int h, int line, int every, boolean vertical) {
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if ((vertical ? i : j) % every == 0) {
                    t.px(x + i, y + j, Decor.hex(line));
                }
            }
        }
    }

    /** A round speaker: its cone dark in the middle, a bright ring round it. */
    static void speaker(Decor.Tex t, double cx, double cy, double r, int ring, int cone) {
        t.disc(cx, cy, r, Decor.hex(cone));
        t.ring(cx, cy, r - 0.8, r, Decor.hex(ring));
        t.disc(cx, cy, Math.max(0.6, r * 0.3), Decor.shade(Decor.hex(cone), 1.6));
    }

    /** Glowing digits, as on a clock radio or a microwave. */
    static void digits(Decor.Tex t, int x, int y, String text, int color) {
        t.text(x, y, text, Decor.hex(color));
    }

    /** A tuning dial: a lit window with marks along it and a red needle. */
    static void dial(Decor.Tex t, int x, int y, int w, int h, int glass, int marks, int needleAt) {
        t.rect(x, y, w, h, Decor.hex(glass));
        for (int i = x; i < x + w; i += 2) {
            t.px(i, y, Decor.hex(marks));
        }
        t.vline(x + needleAt, y, y + h - 1, Decor.hex(0xD8392B));
    }

    /** A clock face with its hands at ten past ten. */
    static void clockFace(Decor.Tex t, double cx, double cy, double r, int face, int hands, int marks, boolean numerals) {
        t.disc(cx, cy, r, Decor.hex(face));
        if (numerals) {
            for (int k = 0; k < 12; k++) {
                double a = k * Math.PI / 6;
                int x = (int) Math.floor(cx + Math.sin(a) * (r - 1.2));
                int y = (int) Math.floor(cy - Math.cos(a) * (r - 1.2));
                t.px(x, y, Decor.hex(marks));
            }
        }
        double hour = Math.PI * 2 * (10 + 10 / 60.0) / 12;
        double minute = Math.PI * 2 * 10 / 60.0;
        t.line(cx - 0.5, cy - 0.5, cx - 0.5 + Math.sin(hour) * r * 0.5, cy - 0.5 - Math.cos(hour) * r * 0.5, Decor.hex(hands));
        t.line(cx - 0.5, cy - 0.5, cx - 0.5 + Math.sin(minute) * r * 0.8, cy - 0.5 - Math.cos(minute) * r * 0.8, Decor.hex(hands));
        t.px((int) Math.floor(cx - 0.5), (int) Math.floor(cy - 0.5), Decor.hex(0xC8303A));
    }
}
