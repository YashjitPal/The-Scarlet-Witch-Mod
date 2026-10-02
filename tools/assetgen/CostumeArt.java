import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Paints the suit-up costumes in the 64x64 player skin layout at twice the density (128x128), plus the reveal masks
 * that drive the transformation.
 *
 * <ul>
 *     <li><b>Witch:</b> a deep crimson corset shaded in many reds, a sweetheart neckline under a sheer dark yoke, a
 *     bright center busk and curving seams, black side panels, and an open crimson coat whose lapels frame the corset
 *     down to the hem. A black belt with a silver buckle, red gauntlets, black trousers and boots, and an ankle-length
 *     cape with a patterned magenta lining.</li>
 *     <li><b>Warlock:</b> a rich crimson tunic quilted in faint diamonds, open at the throat in a V over a dark
 *     undershirt and trimmed in gold, with a wide gold belt and a red gem buckle, black sleeves under gold-trimmed
 *     gauntlets.</li>
 * </ul>
 *
 * <p>Every design function works in face-local skin pixels: {@code x} across the face, {@code y} down it.
 */
final class CostumeArt {

    static final int DENSITY = 2;
    static final int SIZE = 64 * DENSITY;

    record Look(boolean warlock, int red, int redLight, int redDark, int redBright, int wine, int piping,
                int black, int blackLight, int silver, int silverDark, int gold, int goldLight, int goldDark,
                int gem, int gemLight, int gemDark, int stripe, int boot,
                int capeOuter, int capeOuterDark, int capeLining, int capeLiningLine) {

        static Look ofWitch() {
            return new Look(false, 0x8E1022, 0xAE1A2E, 0x620A17, 0xD02238, 0x3A0610, 0x2B0A12,
                    0x150C0F, 0x26181C, 0xCFC9CE, 0x8D868C, 0xCFC9CE, 0xE6E1E5, 0x8D868C,
                    0xD02238, 0xFF8A9A, 0x5A0A16, 0x6E6670, 0x140D10,
                    0x7A1424, 0x5A0E1A, 0xB0306A, 0x6E1A42);
        }

        static Look ofWarlock() {
            return new Look(true, 0xA8182E, 0xC42A40, 0x74101F, 0xE03A50, 0x400810, 0x2A0810,
                    0x140E10, 0x241A1E, 0xC2BCC2, 0x7C767C, 0xD9A441, 0xF2D27A, 0x9A6A22,
                    0xE0103A, 0xFF7A8E, 0x7A0820, 0x8A848E, 0x0F0A0C,
                    0x5E0F1C, 0x430A14, 0x8E2448, 0x561331);
        }

        /** Trim on cuffs and gauntlets: dark piping on the Witch, gold on the Warlock. */
        int trim() {
            return warlock ? gold : piping;
        }
    }

    enum Side { TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK }

    enum Part { BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    record Box(int u, int v, int w, int h, int d) {
        int[] face(Side side) {
            return switch (side) {
                case TOP -> new int[] {u + d, v, w, d};
                case BOTTOM -> new int[] {u + d + w, v, w, d};
                case RIGHT -> new int[] {u, v + d, d, h};
                case FRONT -> new int[] {u + d, v + d, w, h};
                case LEFT -> new int[] {u + d + w, v + d, d, h};
                case BACK -> new int[] {u + d + w + d, v + d, w, h};
            };
        }
    }

    static Box box(Part part, boolean outer, boolean slim) {
        int arm = slim ? 3 : 4;
        return switch (part) {
            case BODY -> new Box(16, outer ? 32 : 16, 8, 12, 4);
            case RIGHT_ARM -> new Box(40, outer ? 32 : 16, arm, 12, 4);
            case LEFT_ARM -> new Box(outer ? 48 : 32, 48, arm, 12, 4);
            case RIGHT_LEG -> new Box(0, outer ? 32 : 16, 4, 12, 4);
            case LEFT_LEG -> new Box(outer ? 0 : 16, 48, 4, 12, 4);
        };
    }

    // ---------------------------------------------------------------- costume

    static BufferedImage costume(Look look, boolean slim) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (Part part : Part.values()) {
            for (boolean outer : new boolean[] {false, true}) {
                for (Side side : Side.values()) {
                    int[] f = box(part, outer, slim).face(side);
                    for (int ty = 0; ty < f[3] * DENSITY; ty++) {
                        for (int tx = 0; tx < f[2] * DENSITY; tx++) {
                            double x = (tx + 0.5) / DENSITY;
                            double y = (ty + 0.5) / DENSITY;
                            int argb = switch (part) {
                                case BODY -> outer ? torsoOuter(look, side, x, y, f[2]) : torso(look, side, x, y, f[2]);
                                case RIGHT_ARM, LEFT_ARM -> outer ? armOuter(look, side, x, y, f[2]) : arm(look, side, x, y, f[2]);
                                case RIGHT_LEG, LEFT_LEG -> outer ? legOuter(look, side, x, y, f[2]) : leg(look, side, x, y, f[2]);
                            };
                            if (argb != 0) {
                                image.setRGB(f[0] * DENSITY + tx, f[1] * DENSITY + ty, fabric(argb, f[0] * DENSITY + tx, f[1] * DENSITY + ty));
                            }
                        }
                    }
                }
            }
        }
        return image;
    }

    // ---------------------------------------------------------------- torso

    private static int torso(Look l, Side side, double x, double y, int w) {
        double c = Math.abs(x - w / 2.0);
        if (side == Side.TOP) {
            return c < 2.4 ? opaque(l.black()) : opaque(mottle(l.red(), x, y, 0.05));
        }
        if (side == Side.BOTTOM) {
            return opaque(l.black());
        }
        if (l.warlock()) {
            return switch (side) {
                case FRONT -> warlockFront(l, x, y, c);
                case BACK -> warlockBack(l, x, y, c);
                default -> warlockSide(l, x, y);
            };
        }
        return switch (side) {
            case FRONT -> witchFront(l, x, y, c);
            case BACK -> witchBack(l, x, y, c);
            default -> witchSide(l, y);
        };
    }

    private static int torsoOuter(Look l, Side side, double x, double y, int w) {
        double c = Math.abs(x - w / 2.0);
        if (side == Side.BOTTOM) {
            return 0;
        }
        if (l.warlock()) {
            return warlockOuter(l, side, x, y, c);
        }
        return witchOuter(l, side, x, y, c);
    }

    private static int witchFront(Look l, double x, double y, double c) {
        if (y < 1.0) {
            return c < 2.4 ? opaque(y < 0.5 ? l.blackLight() : l.black()) : opaque(mottle(l.red(), x, y, 0.05));
        }
        if (y >= 8.9 && y < 9.8) {
            return opaque(l.black());
        }
        if (y >= 9.8) {
            // the corset's pointed hem over black trousers
            double edge = (12.4 - y) * 1.55;
            if (c > edge + 0.35) {
                return opaque(l.black());
            }
            if (c > edge - 0.2) {
                return opaque(l.wine());
            }
            return opaque(mottle(gradient(l.red(), l.redDark(), (y - 9.8) / 2.2), x, y, 0.06));
        }
        // a sheer dark yoke above a sweetheart neckline that dips between two rises
        double neckline = 1.75 + 0.85 * Math.pow(Math.min(1.0, Math.abs(c - 1.3) / 1.3), 1.4);
        if (y < neckline) {
            return opaque(mottle(0x2A0C12, x, y, 0.05));
        }
        if (y < neckline + 0.22) {
            return opaque(l.wine());
        }
        if (c >= 2.6) {
            // black side panels, edged with a fine crimson cord
            return opaque(c < 2.75 ? l.redDark() : l.black());
        }
        double seam = 1.85 - 0.6 * clamp((y - 2.0) / 6.9);
        if (Math.abs(c - seam) < 0.09) {
            return opaque(l.wine());
        }
        if (c < 0.3) {
            return opaque(l.redBright());
        }
        if (c < 0.42) {
            return opaque(l.wine());
        }
        // darker toward the sides and the waist, lit just outside each seam where the panel turns
        double shade = 0.22 * Math.pow(c / 2.6, 2) + 0.12 * (y - 2.0) / 7.0;
        int base = gradient(l.redLight(), l.redDark(), shade);
        if (c > seam && c < seam + 0.24) {
            base = gradient(base, l.redLight(), 0.45);
        }
        return opaque(mottle(base, x, y, 0.07));
    }

    private static int witchBack(Look l, double x, double y, double c) {
        if (y < 1.0) {
            return opaque(c < 2.4 ? l.black() : mottle(l.red(), x, y, 0.05));
        }
        if (y >= 8.9 && y < 9.8) {
            return opaque(l.black());
        }
        if (y >= 9.8) {
            return opaque(y < 10.6 ? l.redDark() : l.black());
        }
        if (c < 0.65) {
            // laced up the back: silver cord crossing over a black panel
            double phase = (y * 1.25) % 1.0;
            boolean lace = Math.abs(c - Math.abs(phase - 0.5) * 1.2) < 0.11;
            return opaque(lace ? l.silverDark() : l.black());
        }
        if (c < 0.78) {
            return opaque(l.wine());
        }
        return opaque(mottle(gradient(l.red(), l.redDark(), clamp((y - 1.0) / 9.0) * 0.5 + 0.2 * Math.pow(c / 4.0, 2)), x, y, 0.07));
    }

    private static int witchSide(Look l, double y) {
        if (y >= 9.8) {
            return opaque(y < 10.4 ? l.wine() : l.black());
        }
        return opaque(l.black());
    }

    private static int witchOuter(Look l, Side side, double x, double y, double c) {
        if (side == Side.TOP) {
            // the coat across the shoulders, open at the neck
            return c > 1.75 ? opaque(mottle(l.redLight(), x, y, 0.05)) : 0;
        }
        boolean belt = y >= 8.85 && y < 9.85;
        if (side == Side.FRONT) {
            if (y < 1.2 && c < 2.9) {
                // the coat's standing collar, fastened with silver clasps, open at the throat
                if (c < 1.75) {
                    return 0;
                }
                if (Math.abs(c - 1.95) < 0.22 && y > 0.35 && y < 1.0) {
                    return opaque(y < 0.7 ? l.silver() : l.silverDark());
                }
                return opaque(y < 0.35 ? l.redLight() : mottle(l.red(), x, y, 0.05));
            }
            if (belt) {
                return beltWithBuckle(l, y, c);
            }
            // the open coat: lapels frame the corset from the collar to the hem
            double edge = lapelEdge(y);
            if (c >= edge) {
                if (c < edge + 0.14) {
                    return opaque(l.wine());
                }
                if (c < edge + 0.38) {
                    return opaque(l.redBright());
                }
                double fold = clamp((c - edge) / (4.0 - edge));
                return opaque(mottle(gradient(l.redLight(), l.redDark(), 0.15 + 0.5 * fold), x, y, 0.06));
            }
            return 0;
        }
        if (side == Side.BACK) {
            if (y < 1.2 && c < 2.9) {
                return opaque(y < 0.35 ? l.redLight() : mottle(l.red(), x, y, 0.05));
            }
            return belt ? opaque(y < 9.05 ? l.blackLight() : l.black()) : 0;
        }
        // the coat wraps the sides, split by a seam
        if (y < 0.8) {
            return 0;
        }
        if (belt) {
            return opaque(y < 9.05 ? l.blackLight() : l.black());
        }
        if (Math.abs(x - 2.0) < 0.1) {
            return opaque(l.wine());
        }
        return opaque(mottle(gradient(l.red(), l.redDark(), 0.25 + 0.3 * clamp(y / 12.0)), x, y, 0.06));
    }

    /**
     * How far from the center line the coat's front edge lies: close in under the collar, opening wider down to the
     * waist and the hem.
     */
    private static double lapelEdge(double y) {
        if (y < 4.2) {
            return 2.2 + 1.05 * smooth(clamp((y - 1.2) / 3.0));
        }
        return 3.25 + 0.05 * (y - 4.2) / 7.8;
    }

    private static int beltWithBuckle(Look l, double y, double c) {
        if (c < 1.05) {
            double ry = (y - 9.35) / 0.62;
            double r = Math.hypot(c / 1.05, ry);
            if (r > 1.0) {
                return opaque(l.black());
            }
            if (r > 0.78) {
                return opaque(l.silverDark());
            }
            boolean emblem = Math.abs(ry) < 0.22 || Math.abs(c - 0.45) < 0.14;
            return opaque(emblem ? l.silverDark() : l.silver());
        }
        return opaque(y < 9.05 ? l.blackLight() : l.black());
    }

    private static int warlockFront(Look l, double x, double y, double c) {
        if (y < 1.0) {
            return c < 2.4 ? opaque(y < 0.5 ? l.blackLight() : l.black()) : opaque(mottle(l.red(), x, y, 0.06));
        }
        // a V opening over a dark undershirt, trimmed in gold
        double half = 2.1 * (1.0 - (y - 1.0) / 3.4);
        if (y < 4.4 && c < half) {
            boolean rib = ((int) Math.floor(x * DENSITY)) % 2 == 0;
            return opaque(mottle(rib ? l.blackLight() : l.black(), x, y, 0.04));
        }
        if (y < 4.6 && c < half + 0.26) {
            return opaque(gradient(l.gold(), l.goldLight(), 0.3));
        }
        if (y >= 8.6 && y < 9.9) {
            return goldBelt(l, x, y);
        }
        if (y >= 9.9) {
            return y >= 11.55 ? opaque(l.goldDark()) : opaque(mottle(gradient(l.red(), l.redDark(), 0.25 * (y - 9.9) / 1.65), x, y, 0.06));
        }
        return opaque(tunic(l, x, y, c));
    }

    private static int warlockBack(Look l, double x, double y, double c) {
        if (y < 1.0) {
            return opaque(c < 2.4 ? l.black() : mottle(l.red(), x, y, 0.06));
        }
        if (y >= 8.6 && y < 9.9) {
            return goldBelt(l, x, y);
        }
        if (y >= 9.9) {
            return y >= 11.55 ? opaque(l.goldDark()) : opaque(mottle(l.redDark(), x, y, 0.06));
        }
        if (c < 0.12) {
            return opaque(l.wine());
        }
        return opaque(tunic(l, x, y, c));
    }

    private static int warlockSide(Look l, double x, double y) {
        if (y < 1.0) {
            return opaque(mottle(l.red(), x, y, 0.06));
        }
        if (y >= 8.6 && y < 9.9) {
            return goldBelt(l, x, y);
        }
        if (y >= 9.9) {
            return y >= 11.55 ? opaque(l.goldDark()) : opaque(mottle(l.redDark(), x, y, 0.06));
        }
        return opaque(tunic(l, x, y, 2.6));
    }

    /**
     * Crimson cloth quilted in faint diamonds, darker toward the sides and the belt.
     */
    private static int tunic(Look l, double x, double y, double c) {
        double shade = 0.18 * Math.pow(c / 4.0, 2) + 0.1 * (y - 1.0) / 7.6;
        boolean cell = (Math.floorMod((int) Math.floor((x + y) / 1.6), 2) + Math.floorMod((int) Math.floor((x - y + 16) / 1.6), 2)) % 2 == 0;
        return mottle(gradient(l.redLight(), l.redDark(), shade + (cell ? 0.07 : 0.0)), x, y, 0.06);
    }

    /**
     * A wide gold belt: dark rims, a lit upper edge and a row of studs.
     */
    private static int goldBelt(Look l, double x, double y) {
        if (y < 8.75 || y >= 9.75) {
            return opaque(l.goldDark());
        }
        if (y < 8.95) {
            return opaque(l.goldLight());
        }
        boolean stud = y > 9.1 && y < 9.45 && (x % 1.4) < 0.3;
        return opaque(stud ? l.goldLight() : mottle(l.gold(), x, y, 0.05));
    }

    private static int warlockOuter(Look l, Side side, double x, double y, double c) {
        if (side == Side.TOP) {
            return c > 1.8 ? opaque(mottle(l.redDark(), x, y, 0.05)) : 0;
        }
        if (y < 1.2 && c < 3.0 && side != Side.RIGHT && side != Side.LEFT) {
            // a standing collar edged in gold, open at the throat in front
            if (side == Side.FRONT && c < 1.8) {
                return 0;
            }
            return opaque(y < 0.25 ? l.gold() : mottle(l.redDark(), x, y, 0.05));
        }
        if (side == Side.FRONT) {
            // a red gem in a gold mount, raised off the belt
            double sx = x - 4.0;
            double r = Math.hypot(sx / 1.05, (y - 9.25) / 0.75);
            if (r < 1.0) {
                if (r > 0.72) {
                    return opaque(r > 0.9 ? l.goldDark() : l.gold());
                }
                if (Math.hypot((sx + 0.3) / 0.32, (y - 9.05) / 0.22) < 1.0) {
                    return opaque(l.gemLight());
                }
                return opaque(r > 0.55 ? l.gemDark() : l.gem());
            }
        }
        return 0;
    }

    // ---------------------------------------------------------------- arms and legs

    private static int arm(Look l, Side side, double x, double y, int w) {
        if (side == Side.TOP) {
            return opaque(l.red());
        }
        if (side == Side.BOTTOM) {
            return opaque(l.redDark());
        }
        if (y < 5.0) {
            int sleeve = l.warlock() ? l.blackLight() : l.redDark();
            return opaque(y > 4.6 ? l.trim() : gradient(sleeve, shade(sleeve, -0.12), y / 5));
        }
        // gauntlet: cuff, a diagonal accent and the wrist trim
        if (y < 5.6 || (y > 10.2 && y < 10.6)) {
            return opaque(l.trim());
        }
        boolean diagonal = (side == Side.FRONT || side == Side.RIGHT || side == Side.LEFT)
                && Math.abs((y - 5.6) - (x * 1.1)) < 0.2 && y < 9.6;
        if (diagonal) {
            return opaque(l.warlock() ? l.goldDark() : l.piping());
        }
        return opaque(mottle(gradient(l.redLight(), l.red(), (y - 5.6) / 6.4), x, y, 0.05));
    }

    private static int armOuter(Look l, Side side, double x, double y, int w) {
        // structured shoulders, part of the coat or tunic
        double cx = Math.abs(x - w / 2.0);
        if (side == Side.TOP) {
            if (cx >= w / 2.0 - 0.6) {
                return opaque(l.warlock() ? l.gold() : l.wine());
            }
            return opaque(mottle(l.redLight(), x, y, 0.05));
        }
        if (side != Side.BOTTOM && y < 3.0) {
            if (y > 2.55) {
                return opaque(l.warlock() ? l.gold() : l.wine());
            }
            if (y > 0.5 && y < 2.15 && cx < w / 2.0 - 0.55) {
                return opaque(side == Side.LEFT ? l.redDark() : mottle(l.redLight(), x, y, 0.05));
            }
            return opaque(mottle(l.red(), x, y, 0.05));
        }
        // raised gauntlet cuff
        if (side != Side.TOP && side != Side.BOTTOM && y >= 5.0 && y < 5.9) {
            return opaque(y < 5.35 ? (l.warlock() ? l.goldLight() : l.redLight()) : l.trim());
        }
        return 0;
    }

    private static int leg(Look l, Side side, double x, double y, int w) {
        if (side == Side.TOP) {
            return opaque(l.black());
        }
        if (side == Side.BOTTOM) {
            return opaque(l.boot());
        }
        if (y >= 8.0) {
            if (y < 8.4) {
                return opaque(l.redDark());
            }
            return opaque(gradient(shade(l.boot(), 0.08), l.boot(), (y - 8.4) / 3.6));
        }
        // trouser stripes: a chevron across the thigh and one above the knee
        double across = side == Side.FRONT || side == Side.BACK ? x : w - x;
        if (Math.abs(y - (2.8 + across * 0.35)) < 0.14 || Math.abs(y - 6.2) < 0.12) {
            return opaque(l.stripe());
        }
        return opaque(gradient(l.blackLight(), l.black(), y / 8));
    }

    private static int legOuter(Look l, Side side, double x, double y, int w) {
        // the corset's hip panels, only on the Witch
        if (l.warlock() || (side != Side.RIGHT && side != Side.LEFT) || y > 3.4) {
            return 0;
        }
        if (y > 3.0) {
            return opaque(l.wine());
        }
        return opaque(mottle(gradient(l.red(), l.redDark(), y / 3), x, y, 0.06));
    }

    // ---------------------------------------------------------------- cape

    /**
     * Cape box 10x22x1 at texture offset 0,0 on a 64x64 layout: the box front is the outside (it faces backward once
     * the cape is turned around), the box back is the lining.
     */
    static BufferedImage cape(Look l) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        paintRect(image, 1, 1, 10, 22, (x, y) -> capeOuter(l, x, y));
        paintRect(image, 12, 1, 10, 22, (x, y) -> capeLining(l, x, y));
        paintRect(image, 0, 1, 1, 22, (x, y) -> opaque(l.capeOuterDark()));
        paintRect(image, 11, 1, 1, 22, (x, y) -> opaque(l.capeOuterDark()));
        paintRect(image, 1, 0, 10, 1, (x, y) -> opaque(l.capeOuterDark()));
        paintRect(image, 11, 0, 10, 1, (x, y) -> opaque(l.capeOuterDark()));
        return image;
    }

    private static int capeOuter(Look l, double x, double y) {
        double fold = 0.5 + 0.5 * Math.sin(x * 1.9 + Math.sin(y * 0.35) * 0.6);
        int base = gradient(l.capeOuter(), l.capeOuterDark(), clamp(y / 22) * 0.7 + fold * 0.3);
        if (x < 0.35 || x > 9.65 || y > 21.6) {
            return opaque(l.capeOuterDark());
        }
        return opaque(base);
    }

    private static int capeLining(Look l, double x, double y) {
        if (x < 0.5 || x > 9.5 || y > 21.5) {
            return opaque(l.capeOuterDark());
        }
        // stepped zigzag bands like the lining in the reference photos
        double band = (y + Math.abs(((x * 1.4) % 4) - 2) * 1.2) % 3.2;
        if (band < 0.45) {
            return opaque(l.capeLiningLine());
        }
        return opaque(gradient(l.capeLining(), shade(l.capeLining(), -0.18), clamp(y / 22)));
    }

    // ---------------------------------------------------------------- reveal masks

    /**
     * Alpha encodes when each texel appears during the transformation: 0 first, 1 last. The costume rises from the
     * feet to the collar with a ragged, noisy front.
     */
    static BufferedImage costumeReveal(boolean slim) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (Part part : Part.values()) {
            for (boolean outer : new boolean[] {false, true}) {
                for (Side side : Side.values()) {
                    int[] f = box(part, outer, slim).face(side);
                    for (int ty = 0; ty < f[3] * DENSITY; ty++) {
                        for (int tx = 0; tx < f[2] * DENSITY; tx++) {
                            double y = (ty + 0.5) / DENSITY;
                            double height = heightOnBody(part, side, y);
                            int px = f[0] * DENSITY + tx;
                            int py = f[1] * DENSITY + ty;
                            double t = 0.04 + 0.86 * height / 24 + 0.05 * noise(px, py);
                            image.setRGB(px, py, alphaMask(t));
                        }
                    }
                }
            }
        }
        return image;
    }

    /**
     * The cape unfurls from the shoulders down, late in the transformation.
     */
    static BufferedImage capeReveal() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < 24 * DENSITY; py++) {
            for (int px = 0; px < 24 * DENSITY; px++) {
                double y = Math.max(0, (py + 0.5) / DENSITY - 1);
                image.setRGB(px, py, alphaMask(0.6 + 0.34 * y / 22 + 0.03 * noise(px, py)));
            }
        }
        return image;
    }

    private static double heightOnBody(Part part, Side side, double y) {
        double top = part == Part.RIGHT_LEG || part == Part.LEFT_LEG ? 12 : 24;
        return switch (side) {
            case TOP -> top;
            case BOTTOM -> top - 12;
            default -> top - y;
        };
    }

    private static int alphaMask(double threshold) {
        int a = (int) Math.round(Math.clamp(threshold, 0.02, 0.97) * 255);
        return (a << 24) | 0xFFFFFF;
    }

    // ---------------------------------------------------------------- preview

    /**
     * Front, back (with cape) and side views of the costume laid out like the player model, for reviewing the art.
     */
    static BufferedImage preview(BufferedImage costume, BufferedImage cape, boolean slim, int scale) {
        int armW = slim ? 3 : 4;
        int figureW = 8 + armW * 2;
        int gap = 6;
        BufferedImage sheet = new BufferedImage((figureW * 2 + 4 + gap * 4) * DENSITY * scale, (24 + gap) * DENSITY * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(0x6B7A8F));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        int ox = gap / 2;
        int oy = gap / 2;
        // front: viewer's left is the player's right
        for (boolean outer : new boolean[] {false, true}) {
            blitFace(g, costume, box(Part.RIGHT_ARM, outer, slim).face(Side.FRONT), ox, oy, scale);
            blitFace(g, costume, box(Part.BODY, outer, slim).face(Side.FRONT), ox + armW, oy, scale);
            blitFace(g, costume, box(Part.LEFT_ARM, outer, slim).face(Side.FRONT), ox + armW + 8, oy, scale);
            blitFace(g, costume, box(Part.RIGHT_LEG, outer, slim).face(Side.FRONT), ox + armW, oy + 12, scale);
            blitFace(g, costume, box(Part.LEFT_LEG, outer, slim).face(Side.FRONT), ox + armW + 4, oy + 12, scale);
        }
        int bx = ox + figureW + gap;
        for (boolean outer : new boolean[] {false, true}) {
            blitFace(g, costume, box(Part.LEFT_ARM, outer, slim).face(Side.BACK), bx, oy, scale);
            blitFace(g, costume, box(Part.BODY, outer, slim).face(Side.BACK), bx + armW, oy, scale);
            blitFace(g, costume, box(Part.RIGHT_ARM, outer, slim).face(Side.BACK), bx + armW + 8, oy, scale);
            blitFace(g, costume, box(Part.LEFT_LEG, outer, slim).face(Side.BACK), bx + armW, oy + 12, scale);
            blitFace(g, costume, box(Part.RIGHT_LEG, outer, slim).face(Side.BACK), bx + armW + 4, oy + 12, scale);
        }
        blitFace(g, cape, new int[] {1, 1, 10, 22}, bx + armW - 1, oy, scale);
        int sx = bx + figureW + gap;
        for (boolean outer : new boolean[] {false, true}) {
            blitFace(g, costume, box(Part.BODY, outer, slim).face(Side.RIGHT), sx, oy, scale);
            blitFace(g, costume, box(Part.RIGHT_ARM, outer, slim).face(Side.RIGHT), sx, oy, scale);
            blitFace(g, costume, box(Part.RIGHT_LEG, outer, slim).face(Side.RIGHT), sx, oy + 12, scale);
        }
        g.dispose();
        return sheet;
    }

    private static void blitFace(Graphics2D g, BufferedImage source, int[] face, int skinX, int skinY, int scale) {
        int k = DENSITY * scale;
        g.drawImage(source,
                skinX * k, skinY * k, (skinX + face[2]) * k, (skinY + face[3]) * k,
                face[0] * DENSITY, face[1] * DENSITY, (face[0] + face[2]) * DENSITY, (face[1] + face[3]) * DENSITY, null);
    }

    // ---------------------------------------------------------------- color helpers

    interface Painter {
        int at(double x, double y);
    }

    private static void paintRect(BufferedImage image, int u, int v, int w, int h, Painter painter) {
        for (int ty = 0; ty < h * DENSITY; ty++) {
            for (int tx = 0; tx < w * DENSITY; tx++) {
                int argb = painter.at((tx + 0.5) / DENSITY, (ty + 0.5) / DENSITY);
                int px = u * DENSITY + tx;
                int py = v * DENSITY + ty;
                image.setRGB(px, py, fabric(argb, px, py));
            }
        }
    }

    /**
     * Woven mesh: a faint checker of slightly darker texels across every fabric surface.
     */
    private static int fabric(int argb, int px, int py) {
        if (((px + py) & 1) == 0) {
            return argb;
        }
        return (argb & 0xFF000000) | shade(argb & 0xFFFFFF, -0.05);
    }

    /**
     * Painterly variation in the cloth: soft blotches of lighter and darker red, so large panels never look flat.
     */
    private static int mottle(int rgb, double x, double y, double amount) {
        double n = 0.65 * smoothNoise(x * 1.3, y * 1.3) + 0.35 * smoothNoise(x * 3.1 + 7.7, y * 3.1 + 2.3);
        return shade(rgb, n * amount);
    }

    static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    static int gradient(int from, int to, double t) {
        t = clamp(t);
        int r = (int) Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        int g = (int) Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        int b = (int) Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return (r << 16) | (g << 8) | b;
    }

    static int shade(int rgb, double amount) {
        return amount < 0 ? gradient(rgb, 0x000000, -amount) : gradient(rgb, 0xFFFFFF, amount);
    }

    static double clamp(double t) {
        return Math.clamp(t, 0, 1);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /**
     * Smooth value noise in [-1, 1] on texel coordinates.
     */
    static double noise(int x, int y) {
        return smoothNoise(x / 3.0, y / 3.0);
    }

    private static double smoothNoise(double fx, double fy) {
        int ix = (int) Math.floor(fx);
        int iy = (int) Math.floor(fy);
        double u = smooth(fx - ix);
        double v = smooth(fy - iy);
        double a = hash(ix, iy);
        double b = hash(ix + 1, iy);
        double c = hash(ix, iy + 1);
        double d = hash(ix + 1, iy + 1);
        return (a + (b - a) * u) * (1 - v) + (c + (d - c) * u) * v;
    }

    private static double hash(int x, int y) {
        long h = x * 374761393L + y * 668265263L;
        h = (h ^ (h >> 13)) * 1274126177L;
        return ((h ^ (h >> 16)) & 0xFFFF) / 32767.5 - 1;
    }
}
