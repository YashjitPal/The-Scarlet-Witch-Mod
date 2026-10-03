/**
 * The materials era decorations are made of: tileable 16x16 textures, each a surface that runs on across a piece the
 * way wood grain or upholstery does. Every era has its own palette: pastels and walnut in the 1950s, teak and
 * turquoise in the 1960s, harvest gold, avocado and wood veneer in the 1970s, almond, mauve and black in the 1980s,
 * silver and beige in the 2000s, and stainless steel, charcoal and light oak today.
 */
final class DecorMaterials {

    private DecorMaterials() {
    }

    static void register() {
        // woods
        wood("walnut", 0x5B3A24, 0x3E2717, 0x7A5134, 11);
        wood("teak", 0x9C5A2E, 0x74401E, 0xBC7442, 23);
        wood("veneer", 0x7A4A26, 0x55311A, 0x9A6236, 37);
        wood("oak", 0xB88A55, 0x8E663C, 0xD2A672, 41);
        wood("maple", 0xC9935A, 0xA4733F, 0xE0AD74, 53);
        wood("ash", 0xD5B88C, 0xB89A6C, 0xE6CDA3, 61);
        wood("black_ash", 0x2A2523, 0x1A1615, 0x3C3431, 71);
        wood("cherry", 0x8A3B26, 0x632818, 0xA8513A, 83);
        // plastics and bakelite
        plastic("bakelite_black", 0x1C1A19, 0.06, 101);
        plastic("bakelite_brown", 0x5A2E1B, 0.05, 103);
        plastic("bakelite_ivory", 0xE9DFC4, 0.025, 107);
        plastic("plastic_white", 0xEDEDE8, 0.02, 109);
        plastic("plastic_almond", 0xE2D5B8, 0.025, 113);
        plastic("plastic_black", 0x1F1F22, 0.05, 127);
        plastic("plastic_charcoal", 0x3B3D41, 0.04, 131);
        plastic("plastic_grey", 0x8A8D93, 0.03, 137);
        plastic("plastic_silver", 0xBFC3C8, 0.035, 139);
        plastic("plastic_mint", 0xA8D8C8, 0.025, 149);
        plastic("plastic_pink", 0xF0AFC2, 0.025, 151);
        plastic("plastic_red", 0xC8303A, 0.03, 157);
        plastic("plastic_teal", 0x2E9C9A, 0.03, 163);
        plastic("plastic_gold", 0xD9A43B, 0.03, 167);
        plastic("plastic_avocado", 0x7E8C3A, 0.03, 173);
        plastic("plastic_brown", 0x6B4423, 0.04, 179);
        plastic("plastic_cream", 0xF2EAD3, 0.02, 181);
        plastic("plastic_mauve", 0xB5838D, 0.03, 191);
        plastic("plastic_neon_pink", 0xFF4FA3, 0.02, 193);
        plastic("plastic_yellow", 0xF7D23E, 0.02, 197);
        plastic("plastic_orange", 0xE07A2E, 0.03, 199);
        plastic("plastic_blue", 0x3D6FB6, 0.03, 211);
        // enamel and glossy finishes
        enamel("enamel_white", 0xF1EFE8, 223);
        enamel("enamel_mint", 0xA9DCC9, 227);
        enamel("enamel_pink", 0xF2B6C6, 229);
        enamel("enamel_turquoise", 0x4FC1BA, 233);
        enamel("enamel_yellow", 0xF2DE95, 239);
        enamel("enamel_gold", 0xD3A03A, 241);
        enamel("enamel_avocado", 0x7A8838, 251);
        enamel("enamel_almond", 0xE4D7BB, 257);
        enamel("enamel_black", 0x18181B, 263);
        enamel("enamel_blue", 0x9FC4DE, 269);
        enamel("enamel_red", 0xC23A3A, 271);
        enamel("ceramic_turquoise", 0x3FB2B0, 277);
        enamel("ceramic_cream", 0xEFE3C8, 281);
        // metals
        chrome("chrome", 0xC9D2DA, 293);
        brushed("brushed_steel", 0xB5B9BE, 307);
        brushed("stainless", 0xC7CCD1, 311);
        brushed("nickel", 0xA9ACAD, 313);
        plastic("black_metal", 0x26272A, 0.05, 317);
        chrome("brass", 0xC9A54E, 331);
        chrome("gold", 0xD8B04A, 337);
        chrome("copper", 0xC27C4E, 347);
        // fabrics
        weave("fabric_powder_blue", 0x9FC1DA, 401);
        tufted("tufted_powder_blue", 0x9FC1DA, 409);
        weave("fabric_teal", 0x2F8F8B, 419);
        weave("fabric_mustard", 0xD5A33A, 421);
        weave("fabric_orange", 0xD06B28, 431);
        plaid("plaid_brown", 0x7A4E2A, 0xC8742E, 0xE3C79A, 433);
        corduroy("corduroy_brown", 0x7B5233, 439);
        corduroy("corduroy_rust", 0xA0502A, 443);
        weave("fabric_rose", 0xD39BA4, 449);
        memphis("fabric_memphis", 0xE7C0C9, 457);
        weave("fabric_mauve", 0xA97A85, 461);
        microfiber("microfiber_beige", 0xD3C2A1, 463);
        microfiber("microfiber_taupe", 0x9E8B74, 467);
        linen("linen_grey", 0x9A9C9F, 479);
        linen("linen_light", 0xC9C6BF, 487);
        linen("linen_mustard", 0xC99A3E, 491);
        linen("linen_sage", 0x9DAF91, 499);
        leather("leather_brown", 0x5E3A22, 503);
        leather("leather_black", 0x232022, 509);
        leather("leather_tan", 0xA87446, 521);
        rattan("rattan", 0xC79A5C, 523);
        knit("knit_charcoal", 0x45474B, 541);
        knit("knit_light", 0xC8C6C2, 547);
        weave("fabric_cream", 0xEDE2C8, 557);
        weave("fabric_red", 0xB8323A, 563);
        // speaker cloth and grilles
        speakerCloth("cloth_gold", 0xC8A65C, 0x8C6A2E, 601);
        speakerCloth("cloth_brown", 0x6E4C30, 0x4A311E, 607);
        mesh("mesh_black", 0x1C1C1F, 0x3A3A3F, 613);
        mesh("mesh_silver", 0x9DA1A6, 0xC8CCD0, 617);
        // glass and screens
        darkGlass("glass_dark", 0x15181C, 631);
        darkGlass("glass_smoke", 0x2A2D31, 641);
        plastic("rubber", 0x18181A, 0.08, 643);
        // lamp shades, plain and lit
        shade("shade_cream", 0xEFE2C2, 653);
        shade("shade_white", 0xF4F2EC, 659);
        shade("shade_pink", 0xF2A5BE, 661);
        shade("shade_linen", 0xE3DCCB, 673);
        plastic("paper_white", 0xF6F4EE, 0.015, 677);
        plastic("burner", 0x2A2A2C, 0.07, 683);
        plastic("burner_hot", 0xD8401E, 0.08, 691);
        plastic("led_blue", 0x48A8FF, 0.0, 701);
        plastic("led_green", 0x5BEA76, 0.0, 709);
        plastic("led_red", 0xFF4A3A, 0.0, 719);
        plastic("led_orange", 0xFFA43A, 0.0, 727);
        plastic("led_white", 0xF4FBFF, 0.0, 733);
        plastic("glow_warm", 0xFFE3A6, 0.0, 739);
        // a car's paint, pale so the tint it takes shows true, a sheen along it
        carPaint("car_paint", 0xEDEDED, 751);
        carPaint("car_white", 0xF4F3EE, 757);
        darkGlass("glass_car", 0x24313B, 761);
    }

    /** Paint with a sheen: the light caught along it in a soft band, and the faintest orange peel. */
    static void carPaint(String name, int base, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16);
        for (int y = 0; y < 16; y++) {
            double band = Math.sin(y * Math.PI * 2 / 16) * 0.035;
            for (int x = 0; x < 16; x++) {
                t.px(x, y, Decor.shade(Decor.hex(base), 1 + band + (Decor.noise(seed, x, y) - 0.5) * 0.02));
            }
        }
        put(name, t);
    }

    private static String put(String name, Decor.Tex tex) {
        return Decor.texture("decor/" + name, tex);
    }

    /** Grain running up the board: streaks that wander from side to side, flecks, and a little noise. */
    static void wood(String name, int base, int dark, int light, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16);
        double[] streaks = new double[5];
        for (int k = 0; k < streaks.length; k++) {
            streaks[k] = k * 16.0 / streaks.length + Decor.noise(seed, k, 0) * 2.5;
        }
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double nearest = 9;
                for (int k = 0; k < streaks.length; k++) {
                    double wave = Math.sin((y + k * 3) * Math.PI * 2 / 16) * 0.8 + Math.sin((y * 2 + k) * Math.PI * 2 / 16) * 0.35;
                    double dx = Math.abs(x + 0.5 - streaks[k] - wave);
                    dx = Math.min(dx, 16 - dx);
                    nearest = Math.min(nearest, dx);
                }
                int c = Decor.hex(base);
                if (nearest < 0.55) {
                    c = Decor.mix(c, Decor.hex(dark), 0.85);
                } else if (nearest < 1.2) {
                    c = Decor.mix(c, Decor.hex(dark), 0.35);
                } else if (Decor.noise(seed + 5, x, y) > 0.93) {
                    c = Decor.mix(c, Decor.hex(light), 0.6);
                }
                t.px(x, y, c);
            }
        }
        t.noise(seed + 9, 0.035);
        put(name, t);
    }

    static void plastic(String name, int base, double amount, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16).fill(Decor.hex(base));
        if (amount > 0) {
            t.noise(seed, amount);
        }
        put(name, t);
    }

    /** Glossy and smooth, with a faint mottle where the light catches it. */
    static void enamel(String name, int base, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16).fill(Decor.hex(base));
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double m = Math.sin((x + y) * Math.PI * 2 / 16) * 0.012 + (Decor.noise(seed, x, y) - 0.5) * 0.025;
                t.px[y * 16 + x] = Decor.shade(t.get(x, y), 1 + m);
            }
        }
        put(name, t);
    }

    /** Polished metal: bands of sky and shadow mirrored across it, bright to dark and back. */
    static void chrome(String name, int base, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16);
        for (int y = 0; y < 16; y++) {
            double band = Math.sin(y * Math.PI * 2 / 16) * 0.22 + Math.sin(y * Math.PI * 4 / 16 + 1) * 0.1;
            for (int x = 0; x < 16; x++) {
                int c = Decor.shade(Decor.hex(base), 1.0 + band);
                if (y == 3 || y == 4) {
                    c = Decor.tint(c, 0.45);
                }
                t.px(x, y, c);
            }
        }
        t.noise(seed, 0.015);
        put(name, t);
    }

    /** Fine streaks along it, each row a shade off the next. */
    static void brushed(String name, int base, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16);
        for (int y = 0; y < 16; y++) {
            double row = (Decor.noise(seed, 0, y) - 0.5) * 0.08;
            for (int x = 0; x < 16; x++) {
                double streak = (Decor.noise(seed + 1, x / 3, y) - 0.5) * 0.05;
                t.px(x, y, Decor.shade(Decor.hex(base), 1 + row + streak));
            }
        }
        put(name, t);
    }

    /** A plain weave: threads over and under, a checker of light and shade. */
    static void weave(String name, int base, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double k = ((x + y) % 2 == 0 ? 1.035 : 0.965) + (Decor.noise(seed, x, y) - 0.5) * 0.05;
                t.px(x, y, Decor.shade(Decor.hex(base), k));
            }
        }
        put(name, t);
    }

    /** Upholstery buttoned down every few texels, the cloth puckering in toward each button. */
    static void tufted(String name, int base, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                // buttons on a diamond grid four texels apart
                int bx = Math.floorMod(x - (y / 4 % 2 == 0 ? 0 : 2), 4);
                int by = Math.floorMod(y, 4);
                double d = Math.hypot(bx - 1.5, by - 1.5);
                double k = 1.06 - d * 0.035 + (Decor.noise(seed, x, y) - 0.5) * 0.04;
                t.px(x, y, Decor.shade(Decor.hex(base), k));
            }
        }
        for (int y = 0; y < 16; y += 4) {
            for (int x = (y / 4 % 2 == 0 ? 0 : 2); x < 16; x += 4) {
                t.px(x, y, Decor.shade(Decor.hex(base), 0.7));
            }
        }
        put(name, t);
    }

    static void plaid(String name, int base, int stripe, int thread, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean sx = x % 8 < 2;
                boolean sy = y % 8 < 2;
                int c = Decor.hex(base);
                if (sx && sy) {
                    c = Decor.mix(Decor.hex(stripe), Decor.hex(0x000000), 0.25);
                } else if (sx || sy) {
                    c = Decor.hex(stripe);
                } else if (x % 8 == 5 || y % 8 == 5) {
                    c = Decor.mix(c, Decor.hex(thread), 0.55);
                }
                t.px(x, y, Decor.shade(c, (x + y) % 2 == 0 ? 1.03 : 0.97));
            }
        }
        t.noise(seed, 0.02);
        put(name, t);
    }

    static void corduroy(String name, int base, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double k = x % 2 == 0 ? 1.08 : 0.9;
                t.px(x, y, Decor.shade(Decor.hex(base), k + (Decor.noise(seed, x, y) - 0.5) * 0.04));
            }
        }
        put(name, t);
    }

    /** The 1980s: confetti of squiggles, triangles and dots in teal, yellow and black over pink. */
    static void memphis(String name, int base, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16).fill(Decor.hex(base));
        t.noise(seed, 0.02);
        int teal = Decor.hex(0x2E9C9A);
        int yellow = Decor.hex(0xF2C94C);
        int black = Decor.hex(0x262426);
        // a squiggle
        t.px(1, 2, teal).px(2, 1, teal).px(3, 2, teal).px(4, 1, teal).px(5, 2, teal);
        t.px(9, 10, teal).px(10, 9, teal).px(11, 10, teal).px(12, 9, teal).px(13, 10, teal);
        // triangles
        t.px(11, 3, yellow).px(10, 4, yellow).px(11, 4, yellow).px(12, 4, yellow);
        t.px(3, 12, yellow).px(2, 13, yellow).px(3, 13, yellow).px(4, 13, yellow);
        // dots
        t.px(7, 6, black).px(14, 14, black).px(6, 15, black).px(14, 6, black).px(1, 8, black);
        put(name, t);
    }

    static void microfiber(String name, int base, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16).fill(Decor.hex(base));
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double nap = Math.sin((x * 2 + y) * Math.PI * 2 / 16) * 0.02;
                t.px[y * 16 + x] = Decor.shade(t.get(x, y), 1 + nap + (Decor.noise(seed, x, y) - 0.5) * 0.03);
            }
        }
        put(name, t);
    }

    /** Loose threads across and along it, slubs here and there. */
    static void linen(String name, int base, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16);
        for (int y = 0; y < 16; y++) {
            double row = (Decor.noise(seed, 0, y) - 0.5) * 0.07;
            for (int x = 0; x < 16; x++) {
                double col = (Decor.noise(seed + 3, x, 0) - 0.5) * 0.06;
                double slub = Decor.noise(seed + 7, x, y) > 0.95 ? 0.07 : 0;
                t.px(x, y, Decor.shade(Decor.hex(base), 1 + row + col + slub));
            }
        }
        put(name, t);
    }

    static void leather(String name, int base, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double m = Math.sin(x * Math.PI * 2 / 16 + Math.sin(y * Math.PI * 2 / 16)) * 0.05;
                double crease = Decor.noise(seed, x, y) > 0.94 ? -0.08 : 0;
                t.px(x, y, Decor.shade(Decor.hex(base), 1 + m + crease + (Decor.noise(seed + 1, x, y) - 0.5) * 0.04));
            }
        }
        put(name, t);
    }

    static void rattan(String name, int base, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean over = ((x / 2) + (y / 2)) % 2 == 0;
                double k = over ? 1.08 : 0.84;
                if ((x + y) % 4 == 0) {
                    k -= 0.08;
                }
                t.px(x, y, Decor.shade(Decor.hex(base), k + (Decor.noise(seed, x, y) - 0.5) * 0.05));
            }
        }
        put(name, t);
    }

    /** Knitted: little vees in columns. */
    static void knit(String name, int base, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int col = x % 4;
                int row = y % 3;
                boolean stitch = (col == 1 || col == 2) && row != 2 && (col == 1 ? row == 0 : row == 1) || (col == 0 || col == 3) && row == 1;
                double k = stitch ? 1.1 : 0.92;
                t.px(x, y, Decor.shade(Decor.hex(base), k + (Decor.noise(seed, x, y) - 0.5) * 0.04));
            }
        }
        put(name, t);
    }

    /** Woven cloth over a speaker, a thread of brighter color every other row. */
    static void speakerCloth(String name, int base, int dark, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = (y % 2 == 0) ^ (x % 2 == 0) ? Decor.hex(base) : Decor.mix(Decor.hex(base), Decor.hex(dark), 0.5);
                if (y % 4 == 3) {
                    c = Decor.mix(c, Decor.hex(dark), 0.4);
                }
                t.px(x, y, c);
            }
        }
        t.noise(seed, 0.03);
        put(name, t);
    }

    static void mesh(String name, int base, int dot, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16).fill(Decor.hex(base));
        for (int y = 0; y < 16; y += 2) {
            for (int x = (y / 2) % 2; x < 16; x += 2) {
                t.px(x, y, Decor.hex(dot));
            }
        }
        t.noise(seed, 0.03);
        put(name, t);
    }

    /** Dark glass with the room reflected faintly across it. */
    static void darkGlass(String name, int base, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16).fill(Decor.hex(base));
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int d = Math.floorMod(x + y, 16);
                if (d == 5 || d == 6) {
                    t.px[y * 16 + x] = Decor.tint(t.get(x, y), d == 5 ? 0.12 : 0.07);
                }
            }
        }
        t.noise(seed, 0.03);
        put(name, t);
    }

    /** A lamp shade's cloth, pleated, and the same lit from within. */
    static void shade(String name, int base, long seed) {
        Decor.Tex t = new Decor.Tex(16, 16);
        Decor.Tex lit = new Decor.Tex(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double pleat = x % 2 == 0 ? 1.03 : 0.95;
                int c = Decor.shade(Decor.hex(base), pleat + (Decor.noise(seed, x, y) - 0.5) * 0.03);
                t.px(x, y, c);
                lit.px(x, y, Decor.mix(Decor.tint(c, 0.25), Decor.hex(0xFFE6A8), 0.35));
            }
        }
        put(name, t);
        put(name + "_lit", lit);
    }
}
