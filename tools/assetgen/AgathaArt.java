import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/** Agatha's road coat, cravat, carved cameo and curls, in the two-density player UV layout. */
final class AgathaArt {
    private static final String KEYS = "inbcvVpaewgGkKdsShMrfTtLqjXuUFYzZ";
    private static final int[] COLORS = {
            0x222136, 0x303348, 0x404561, 0x535D7D, 0x58426B, 0x82618D,
            0x583447, 0x855463, 0xC7AFA4, 0xECE0C5, 0x826447, 0xCCA677,
            0x24202B, 0x423440, 0x271E26, 0x422D31, 0x65443F, 0x845E50,
            0xAA7F79, 0x713B4B, 0xAF6875, 0x426160, 0x648582, 0x8FA6A0,
            0x251C36, 0x382342, 0x56315F, 0x764481, 0x965EA1, 0xB285B5,
            0x74617F, 0x989094, 0xBFB8AD
    };

    // Two texels per skin pixel. The lapels taper past an ivory collar and a wine waistcoat.
    private static final String[] FRONT = {
            "bccvveewweevvccb", "bcvVeewwwweeVvcb", "bcVveewwweeVvccb", "bbcvVeewweVvcbbb",
            "nbccvVeeweVvccbn", "nbbcvVaeeaVvcbbn", "nbbcvVpaapVvcbbn", "nbcbcvVapVvcbbbn",
            "nbbcbvVpgVvbcbbn", "nbncbcvpaVvbcnbn", "nbnbbcvpavcbbnbn", "nbnbbcvpavcbbnbn",
            "nbnnbcvpgvcbbnbn", "nbnnbcvpavcbnnbn", "nbnnbcvpavcbnnbn", "nbnnbcvpavcbnnbn",
            "nbbcbcvpgvcbcbbn", "nbvcbcvpavcbcvbn", "nbnbbcvpavcbbnbn", "nbnbbcvkKvcbbnbn",
            "nbnnbcvkKvcbbnbn", "nbnnbcvkkvcbbnbn", "nbnnbcvkkvcbbnbn", "nbnnbcvkkvcbbnbn"
    };
    private static final String[] HAIRLINE = {
            "ssSShhSdsSSShSss", "sSShhSSdssShhSSs", "ShhSSsd.ssSSShSs", "ShSSsd...dsShhSs",
            "hSSds.....dsSShS", "SSs........dsSSh", "ss..........dsSS", "s............dsS",
            "s.............ss", "...............s"
    };
    private static final String[] ICON = {
            "......gggg......", "....gGGwwGGg....", "...gGggGGggGg...", "..gGgvVVVVvgGg..",
            ".gGgvVwVwVVvgGg.", ".gGgVweweVwVgGg.", "gGgvVVeeVweVvgGg", "gGgvVewVeeVVvgGg",
            "gGgvVweVewwVvgGg", "gGgvweeVeewevgGg", ".gGgweeweewVgGg.", ".gGgvVeeeeVvgGg.",
            "..gGgvvVVvvgGg..", "...gGggggggGg...", "....gGGGGGGg....", "......gggg......"
    };
    private static final String[] CAMEO = {".gGGg.", "GvwevG", "gwvewg", "geweeg", "GveevG", ".gGGg."};

    public static void main(String[] args) throws IOException {
        generate(Path.of("common/src/main/resources/assets/scarlet"));
    }

    static void generate(Path assets) throws IOException {
        for (boolean slim : new boolean[]{false, true}) {
            for (String form : new String[]{"agatha", "agatha_agnes", "agatha_finale"}) {
                BufferedImage texture = new BufferedImage(128, 256, BufferedImage.TYPE_INT_ARGB);
                CostumeArt.CostumePainter painter = switch (form) {
                    case "agatha_agnes" -> AgathaArt::cardigan;
                    case "agatha_finale" -> AgathaArt::gown;
                    default -> AgathaArt::cloth;
                };
                var graphics = texture.createGraphics();
                graphics.drawImage(CostumeArt.costume(slim, painter), 0, 0, null);
                graphics.dispose();
                boolean finale = form.equals("agatha_finale");
                hair(texture, finale);
                box(texture, new CostumeArt.Box(0, 64, 3, 4, 3), (side, x, y, w, h) ->
                        wave(x, y, w, side, finale && side == CostumeArt.Side.LEFT));
                if (finale) {
                    box(texture, new CostumeArt.Box(14, 64, 3, 4, 3), (side, x, y, w, h) -> {
                        int streak = 1 + (y / 3 + 1) % 3;
                        if (side == CostumeArt.Side.FRONT && x == streak) return y % 5 < 2 ? 'Z' : 'z';
                        return wave(x, y, w, side, false);
                    });
                }
                if (form.equals("agatha")) {
                    box(texture, new CostumeArt.Box(0, 0, 4, 8, 1), AgathaArt::tail);
                    box(texture, new CostumeArt.Box(10, 0, 1, 7, 5), AgathaArt::tail);
                } else if (finale) {
                    robePanels(texture, false);
                } else {
                    box(texture, new CostumeArt.Box(0, 77, 5, 7, 5), (side, x, y, w, h) ->
                            y == h - 1 ? 'T' : y == h - 2 ? 'e' : plaid(x, y));
                }
                cameo(texture);
                AssetGen.writePng(texture, assets.resolve("textures/entity/costume/" + form + (slim ? "_slim" : "") + ".png"));
            }
        }
        BufferedImage shine = new BufferedImage(128, 256, BufferedImage.TYPE_INT_ARGB);
        robePanels(shine, true);
        AssetGen.writePng(shine, assets.resolve("textures/entity/costume/agatha_stitchlight.png"));
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) icon.setRGB(x, y, color(ICON[y].charAt(x)));
        }
        AssetGen.writePng(icon, assets.resolve("textures/item/agatha_brooch.png"));
    }

    private static void cameo(BufferedImage texture) {
        box(texture, new CostumeArt.Box(22, 0, 3, 3, 1), (side, x, y, w, h) ->
                side == CostumeArt.Side.FRONT || side == CostumeArt.Side.BACK ? CAMEO[y].charAt(x)
                        : x == 0 || y == 0 || x == w - 1 || y == h - 1 ? '.' : 'g');
    }

    private static int cloth(CostumeArt.Part part, boolean outer, CostumeArt.Side side, double fx, double fy, int width) {
        int x = (int) (fx * 2), y = (int) (fy * 2), w = width * 2;
        boolean cap = side == CostumeArt.Side.TOP || side == CostumeArt.Side.BOTTOM;
        if (part == CostumeArt.Part.BODY) {
            if (cap) return outer ? 0 : color(side == CostumeArt.Side.TOP ? 'b' : 'n');
            if (side == CostumeArt.Side.FRONT) {
                char pixel = FRONT[y].charAt(x);
                return outer && "vVew".indexOf(pixel) < 0 ? 0 : color(pixel);
            }
            if (outer) return 0;
            int edge = Math.min(x, w - 1 - x);
            if (side == CostumeArt.Side.BACK) {
                if (y < 5 && y == 1 + edge / 3) return color('v');
                if (y >= 14 && y <= 15) return color(x == 4 || x == 11 ? 'g' : 'n');
                if (edge == 3 + (y < 10 ? y / 4 : (23 - y) / 4)) return color('c');
            }
            return color(edge <= 1 ? 'n' : 'b');
        }
        boolean arm = part == CostumeArt.Part.LEFT_ARM || part == CostumeArt.Part.RIGHT_ARM;
        if (arm) {
            if (side == CostumeArt.Side.BOTTOM || (!cap && y >= 20)) return 0;
            if (outer && (cap || y < 15 || y >= 19)) return 0;
            if (cap) return color(x == 1 ? 'c' : 'b');
            if (y >= 15) {
                if (y == 15 || y == 18) return color('V');
                if (x == 2 && side == CostumeArt.Side.BACK) return color('G');
                return color(y == 19 ? 'e' : 'v');
            }
            return color(x == 0 ? 'n' : x == 2 + (y / 4) % 2 ? 'c' : 'b');
        }
        if (outer && (cap || y < 14)) return 0;
        if (cap) return color('k');
        if (y < 14) return color(x == 2 || x == 5 ? 'K' : 'k');
        if (y == 14 || y == 22) return color('s');
        if (y == 17 && side == CostumeArt.Side.FRONT) return color(x == 2 ? 'g' : 's');
        return color(x == 1 && y < 22 ? 'K' : 'k');
    }

    private static char tail(CostumeArt.Side side, int x, int y, int w, int h) {
        if (side == CostumeArt.Side.TOP || side == CostumeArt.Side.BOTTOM) return 'n';
        if (side == CostumeArt.Side.FRONT) return y == h - 2 ? 'V' : 'v';
        if (y + 1 == h) return 'n';
        if (y + 2 == h) return 'v';
        return x == w - 2 ? 'c' : x < 2 ? 'n' : 'b';
    }

    private static void hair(BufferedImage image, boolean finale) {
        // A shallow fringe between the skin's hat and our raised curls covers painted bangs up to the brow.
        box(image, new CostumeArt.Box(0, 110, 8, 8, 8), (side, x, y, w, h) -> {
            if (side != CostumeArt.Side.FRONT) return '.';
            int depth = x < 7 || x >= 13 ? 8 : x < 9 || x >= 11 ? 7 : 6;
            return y < depth ? wave(x, y, w, side, false) : '.';
        });
        box(image, new CostumeArt.Box(32, 0, 8, 8, 8), (side, x, y, w, h) -> {
            if (side == CostumeArt.Side.FRONT) return y < HAIRLINE.length ? HAIRLINE[y].charAt(x) : '.';
            if (side == CostumeArt.Side.BOTTOM) return '.';
            return wave(x, y, w, side, finale);
        });
    }

    private static char wave(int x, int y, int w, CostumeArt.Side side, boolean silver) {
        int bend = (int) Math.round(1.8 * Math.sin(y * 0.52 + x / 7));
        if (side == CostumeArt.Side.TOP) bend = y / 2;
        int strand = Math.floorMod(x + bend + side.ordinal(), 8);
        if (silver && x > w / 2 && strand == 3 && y % 11 < 8) return y % 5 < 2 ? 'Z' : 'z';
        return "dssSShSs".charAt(strand);
    }

    private static boolean cap(CostumeArt.Side side) {
        return side == CostumeArt.Side.TOP || side == CostumeArt.Side.BOTTOM;
    }

    private static char plaid(int x, int y) {
        if (x % 8 == 1 || y % 8 == 2) return (x % 8 == 1 && y % 8 == 2) ? 'w' : 'L';
        return x % 8 < 4 ? (y % 8 < 4 ? 'T' : 't') : (y % 8 < 4 ? 't' : 'L');
    }

    private static int cardigan(CostumeArt.Part part, boolean outer, CostumeArt.Side side, double fx, double fy, int width) {
        int x = (int) (fx * 2), y = (int) (fy * 2), w = width * 2;
        boolean front = side == CostumeArt.Side.FRONT;
        int edge = Math.min(x, w - 1 - x);
        if (part == CostumeArt.Part.BODY) {
            if (cap(side)) return outer ? 0 : color('r');
            if (front && y < 7 && edge >= 2 && edge >= y && edge <= 9 - y / 2) {
                return color(edge + y == 9 ? 'e' : 'w');
            }
            if (outer) return 0;
            if (y > 20) return color(plaid(x, y));
            if (y >= 18) return color(front && y == 19 && edge == 7 ? 'G' : 'r');
            if (front && edge == 7) return color(x == 8 && y % 5 == 2 ? 'w' : 'r');
            return color(edge < 2 ? 'r' : x % 6 == 1 && y % 5 == 2 ? 'M' : 'f');
        }
        boolean arm = part == CostumeArt.Part.LEFT_ARM || part == CostumeArt.Part.RIGHT_ARM;
        if (arm) {
            if (side == CostumeArt.Side.BOTTOM || !cap(side) && y >= 19) return 0;
            if (outer && (cap(side) || y < 15)) return 0;
            return color(y >= 15 ? (x % 3 == 0 ? 'M' : 'r') : edge == 0 ? 'r' : 'f');
        }
        if (outer) return 0;
        if (cap(side)) return color('k');
        if (y < 14) return color(plaid(x, y));
        if (y < 20) return color(y == 18 && front ? 'K' : 'Y');
        return color(y == 20 && front && edge > 1 ? 'Y' : 'k');
    }

    // Interlocking grape and ink layers, a pinched waist, and a branching motif over the bodice.
    private static final String[] BODICE = {
            "jjXXuuTTTTuuXXjj","jXXuuTtLLtTuuXXj","XXuUutLttLtuUuXX","XuUUuTtLLtTuUUuX",
            "XuUuXjTttTjXuUuX","jXuUXjqTTqjXUuXj","jXuuUXjTTjXUuuXj","qjXUuUXjjXUuUXjq",
            "qjXFuUXuuXUuFXjq","qjXuFUjXXjUFuXjq","jqjuUFXjjXFUujqj","XjqjXUFuuFUXjqjX",
            "uXjqjXuFFuXjqjXu","UuXjqjXuuXjqjXuU","XuUXjqjXXjqjXUuX","jXuUXjqjjqjXUuXj",
            "qjXUUXjqqjXUUXjq","qqjXuUjqqjUuXjqq","jqqjXuUjjUuXjqqj","XjqqjXuUUuXjqqjX",
            "uXjqqjXuuXjqqjXu","XuXjqqjXXjqqjXuX","jXuXjqqjjqqjXuXj","qjXuXjqqqqjXuXjq"
    };

    private static int gown(CostumeArt.Part part, boolean outer, CostumeArt.Side side, double fx, double fy, int width) {
        int x = (int) (fx * 2), y = (int) (fy * 2), w = width * 2;
        int edge = Math.min(x, w - 1 - x);
        if (part == CostumeArt.Part.BODY) {
            if (cap(side)) return outer ? 0 : color('j');
            if (side == CostumeArt.Side.FRONT) {
                char pixel = BODICE[y].charAt(x);
                return outer && "FUtL".indexOf(pixel) < 0 ? 0 : color(pixel);
            }
            if (outer) return 0;
            int piping = (y / 3 + edge) % 7;
            return color(piping == 2 ? 'U' : piping < 2 ? 'X' : 'j');
        }
        if (part == CostumeArt.Part.LEFT_ARM || part == CostumeArt.Part.RIGHT_ARM) {
            if (side == CostumeArt.Side.BOTTOM || !cap(side) && y >= 21) return 0;
            if (outer && (cap(side) || y > 7 && y < 16)) return 0;
            if (y >= 17) return color(y == 19 ? 'U' : 'q');
            int fold = Math.floorMod(y / 2 - edge, 7);
            return color(fold == 1 ? 'u' : fold < 4 ? 'X' : 'j');
        }
        if (outer) return 0;
        return color(!cap(side) && y < 21 ? (edge == 1 ? 'X' : 'j') : 'k');
    }

    private static void robePanels(BufferedImage image, boolean light) {
        // The luminous stitches share UVs with their threads on both arm widths.
        Paint petals = (side, x, y, w, h) -> {
            char pixel = 'j';
            if (!cap(side)) {
                int edge = Math.min(x, w - 1 - x);
                int stem = 2 + (int) Math.round(1.1 * Math.sin(y * 0.43));
                if (y + 1 == h && (x + 2) % 5 < 2) pixel = '.';
                else if (y >= h - 2) pixel = 'u';
                else if (edge == stem && w >= 6) pixel = y % 6 == 2 ? 'F' : 'u';
                else if (edge == stem + 1 && y % 6 <= 1) pixel = 'U';
                else pixel = edge == 0 ? 'q' : edge % 4 == 2 ? 'X' : 'j';
            }
            return light ? (pixel == 'F' ? 'U' : '.') : pixel;
        };
        box(image, new CostumeArt.Box(22, 78, 5, 12, 1), petals);
        box(image, new CostumeArt.Box(0, 92, 5, 10, 5), petals);
        box(image, new CostumeArt.Box(28, 94, 3, 8, 1), petals);
        box(image, new CostumeArt.Box(40, 78, 3, 4, 1), petals);
        box(image, new CostumeArt.Box(35, 108, 3, 10, 1), (side, x, y, w, h) -> {
            if (light) return '.';
            int edge = Math.min(x, w - 1 - x);
            if (y + edge >= h) return '.';
            if (edge == 0) return 'X';
            return y % 6 == edge + 1 ? 't' : edge == 1 ? 'T' : 'j';
        });
        box(image, new CostumeArt.Box(37, 93, 4, 5, 5), (side, x, y, w, h) -> {
            if (light) return '.';
            if (cap(side)) return 'X';
            int edge = Math.min(x, w - 1 - x);
            int hem = h - 4 + Math.min(3, edge);
            if (y > hem) return '.';
            if (y == hem) return 'U';
            return x % 4 == 0 ? 'u' : edge == 1 ? 'j' : 'X';
        });
    }

    private interface Paint {
        char pixel(CostumeArt.Side side, int x, int y, int w, int h);
    }

    private static void box(BufferedImage image, CostumeArt.Box box, Paint paint) {
        for (CostumeArt.Side side : CostumeArt.Side.values()) {
            int[] f = box.face(side);
            int w = 2 * f[2], h = 2 * f[3];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    image.setRGB(2 * f[0] + x, 2 * f[1] + y, color(paint.pixel(side, x, y, w, h)));
                }
            }
        }
    }

    private static int color(char pixel) {
        return pixel == '.' ? 0 : 0xff000000 | COLORS[KEYS.indexOf(pixel)];
    }
}
