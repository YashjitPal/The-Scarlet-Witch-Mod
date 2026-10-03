import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Darkhold's art: its inventory icon, the tome held in the hand (closed, and lying open while it is read) and the
 * veins its corruption sends crawling in at the edges of the view.
 *
 * <p>The book is bound in black-crimson leather with tarnished iron at its corners and spine, held shut by a strap, and
 * carries a horned iron crest on its cover around a stone that glows sickly red. Its pages are old and yellowed, written
 * close in a dark hand around red sigils.
 *
 * <p>Run with everything else from {@link AssetGen}, or on its own: {@code java tools/assetgen/DarkholdArt.java}.
 */
final class DarkholdArt {

    static final int VOID = 0x0B0004;
    static final int LEATHER_DARK = 0x15060A;
    static final int LEATHER = 0x23080F;
    static final int LEATHER_LIGHT = 0x3B1119;
    static final int IRON_DARK = 0x29232A;
    static final int IRON = 0x4C434B;
    static final int IRON_LIGHT = 0x7A6E76;
    static final int PAPER = 0xB4A88D;
    static final int PAPER_DARK = 0x887C65;
    static final int INK = 0x3A2A24;
    static final int WINE = 0x5C0717;
    static final int SICKLY = 0x9B1B30;
    static final int GLOW = 0xE0143C;
    static final int HOT = 0xFF7A8C;
    static final int ABYSS = 0x3A0010;
    static final int DEEP = 0x6A0B20;

    /** The crest on the cover, row by row: I iron, L light iron, D dark iron, W wine, S sickly, G glow, H hot. */
    private static final String[] CREST = {
            ".L..L.",
            ".ILLI.",
            "IWSSWI",
            "ISGHSI",
            "IWGGWI",
            ".IWWI.",
            "..II..",
            "..DD..",
    };

    private DarkholdArt() {
    }

    public static void main(String[] args) throws IOException {
        generate(Path.of(args.length > 0 ? args[0] : "common/src/main/resources/assets/scarlet"), Path.of("build/assetgen"));
    }

    static void generate(Path assets, Path preview) throws IOException {
        BufferedImage icon = icon();
        BufferedImage book = bookTexture();
        BufferedImage veins = veins();
        AssetGen.writePng(icon, assets.resolve("textures/item/darkhold.png"));
        AssetGen.writePng(book, assets.resolve("textures/item/darkhold_book.png"));
        AssetGen.writeText(iconModel(), assets.resolve("models/item/darkhold.json"));
        AssetGen.writeText(closedModel(), assets.resolve("models/item/darkhold_closed.json"));
        AssetGen.writeText(openModel(), assets.resolve("models/item/darkhold_open.json"));
        AssetGen.writeText(itemDefinition(), assets.resolve("items/darkhold.json"));
        AssetGen.writePng(veins, assets.resolve("textures/gui/sprites/hud/darkhold_veins.png"));
        AssetGen.writePng(AssetGen.zoomSheet(List.of(icon, book), 256), preview.resolve("darkhold.png"));
        AssetGen.writePng(veins, preview.resolve("darkhold_veins.png"));
        System.out.println("Darkhold assets written to " + assets.toAbsolutePath());
    }

    // ---------------------------------------------------------------- the icon

    /**
     * The tome face on, its spine to the left and its page edges to the right.
     */
    static BufferedImage icon() {
        Pixels p = new Pixels(16, 16);
        Random random = new Random(1666);
        for (int y = 1; y <= 14; y++) {
            for (int x = 4; x <= 13; x++) {
                p.set(x, y, leather(random, x - 4, y - 1, 10, 14));
            }
            p.set(2, y, LEATHER_DARK);
            p.set(3, y, LEATHER);
        }
        // the edges of the pages, between the covers
        for (int y = 2; y <= 13; y++) {
            p.set(14, y, y % 2 == 0 ? PAPER : PAPER_DARK);
        }
        p.set(14, 1, LEATHER_DARK);
        p.set(14, 14, LEATHER_DARK);
        // a groove pressed into the leather all around the cover
        for (int x = 5; x <= 12; x++) {
            p.set(x, 2, LEATHER_DARK);
            p.set(x, 13, LEATHER_DARK);
        }
        for (int y = 2; y <= 13; y++) {
            p.set(5, y, LEATHER_DARK);
            p.set(12, y, LEATHER_DARK);
        }
        // iron bands across the spine
        for (int y : new int[] {3, 12}) {
            p.set(2, y, IRON);
            p.set(3, y, IRON_LIGHT);
        }
        // iron at the corners of the cover, each with a spike
        corner(p, 4, 1, 1, 1);
        corner(p, 13, 1, -1, 1);
        corner(p, 4, 14, 1, -1);
        corner(p, 13, 14, -1, -1);
        // the strap holding it shut, across the page edges
        p.set(13, 7, IRON);
        p.set(14, 7, IRON_LIGHT);
        p.set(13, 8, IRON_DARK);
        p.set(14, 8, IRON);
        crest(p, 6, 4);
        return p.outlined(VOID);
    }

    private static void corner(Pixels p, int x, int y, int dx, int dy) {
        p.set(x, y, IRON_LIGHT);
        p.set(x + dx, y, IRON);
        p.set(x, y + dy, IRON);
    }

    private static void crest(Pixels p, int x0, int y0) {
        for (int row = 0; row < CREST.length; row++) {
            for (int col = 0; col < CREST[row].length(); col++) {
                int color = switch (CREST[row].charAt(col)) {
                    case 'I' -> IRON;
                    case 'L' -> IRON_LIGHT;
                    case 'D' -> IRON_DARK;
                    case 'W' -> WINE;
                    case 'S' -> SICKLY;
                    case 'G' -> GLOW;
                    case 'H' -> HOT;
                    default -> -1;
                };
                if (color >= 0) {
                    p.set(x0 + col, y0 + row, color);
                }
            }
        }
    }

    /**
     * Old leather, mottled, a little lighter toward the top left where the light falls.
     */
    private static int leather(Random random, int x, int y, int width, int height) {
        double light = 0.5 * (1.0 - (x / (double) width + y / (double) height) / 2.0) + 0.35 * random.nextDouble();
        return light > 0.62 ? LEATHER_LIGHT : light < 0.22 ? LEATHER_DARK : LEATHER;
    }

    // ---------------------------------------------------------------- the tome in the hand

    /** Regions of the 32x32 book texture, in pixels: x, y, width, height. */
    private static final int[] FRONT = {0, 0, 10, 14};
    private static final int[] BACK = {10, 0, 10, 14};
    private static final int[] SPINE = {20, 0, 4, 14};
    private static final int[] EDGE = {24, 0, 3, 13};
    private static final int[] TOP = {0, 14, 9, 3};
    private static final int[] IRON_REGION = {10, 14, 4, 4};
    private static final int[] CREST_REGION = {14, 14, 6, 8};
    private static final int[] STRAP = {20, 14, 2, 4};
    private static final int[] PLAIN = {22, 14, 4, 4};
    private static final int[] LEFT_PAGE = {0, 22, 9, 10};
    private static final int[] RIGHT_PAGE = {9, 22, 9, 10};

    static BufferedImage bookTexture() {
        Pixels p = new Pixels(32, 32);
        Random random = new Random(1666);
        for (int[] cover : new int[][] {FRONT, BACK}) {
            for (int y = 0; y < cover[3]; y++) {
                for (int x = 0; x < cover[2]; x++) {
                    p.set(cover[0] + x, cover[1] + y, leather(random, x, y, cover[2], cover[3]));
                }
            }
            for (int x = 1; x < cover[2] - 1; x++) {
                p.set(cover[0] + x, cover[1] + 1, LEATHER_DARK);
                p.set(cover[0] + x, cover[1] + cover[3] - 2, LEATHER_DARK);
            }
            for (int y = 1; y < cover[3] - 1; y++) {
                p.set(cover[0] + 1, cover[1] + y, LEATHER_DARK);
                p.set(cover[0] + cover[2] - 2, cover[1] + y, LEATHER_DARK);
            }
        }
        crest(p, FRONT[0] + 2, FRONT[1] + 3);
        for (int y = 0; y < SPINE[3]; y++) {
            for (int x = 0; x < SPINE[2]; x++) {
                boolean band = y == 2 || y == 3 || y == SPINE[3] - 4 || y == SPINE[3] - 3;
                p.set(SPINE[0] + x, SPINE[1] + y, band ? (y % 2 == 0 ? IRON_LIGHT : IRON) : x == 0 || x == SPINE[2] - 1 ? LEATHER_DARK : LEATHER);
            }
        }
        // the page edges: one line for each leaf
        for (int y = 0; y < EDGE[3]; y++) {
            for (int x = 0; x < EDGE[2]; x++) {
                p.set(EDGE[0] + x, EDGE[1] + y, x % 2 == 0 ? PAPER : PAPER_DARK);
            }
        }
        for (int y = 0; y < TOP[3]; y++) {
            for (int x = 0; x < TOP[2]; x++) {
                p.set(TOP[0] + x, TOP[1] + y, y % 2 == 0 ? PAPER : PAPER_DARK);
            }
        }
        for (int y = 0; y < IRON_REGION[3]; y++) {
            for (int x = 0; x < IRON_REGION[2]; x++) {
                p.set(IRON_REGION[0] + x, IRON_REGION[1] + y, x + y < 2 ? IRON_LIGHT : x + y > 4 ? IRON_DARK : IRON);
            }
        }
        crest(p, CREST_REGION[0], CREST_REGION[1]);
        for (int y = 0; y < CREST_REGION[3]; y++) {
            for (int x = 0; x < CREST_REGION[2]; x++) {
                if (!p.inked(CREST_REGION[0] + x, CREST_REGION[1] + y)) {
                    p.set(CREST_REGION[0] + x, CREST_REGION[1] + y, IRON_DARK);
                }
            }
        }
        for (int y = 0; y < STRAP[3]; y++) {
            for (int x = 0; x < STRAP[2]; x++) {
                p.set(STRAP[0] + x, STRAP[1] + y, y == 1 ? IRON_LIGHT : LEATHER_DARK);
            }
        }
        for (int y = 0; y < PLAIN[3]; y++) {
            for (int x = 0; x < PLAIN[2]; x++) {
                p.set(PLAIN[0] + x, PLAIN[1] + y, (x + y) % 3 == 0 ? LEATHER_LIGHT : LEATHER);
            }
        }
        page(p, LEFT_PAGE, random, false);
        page(p, RIGHT_PAGE, random, true);
        return p.image();
    }

    /**
     * A page written close in a dark hand, darker toward the gutter, with a red sigil on the right-hand page.
     */
    private static void page(Pixels p, int[] region, Random random, boolean right) {
        for (int y = 0; y < region[3]; y++) {
            for (int x = 0; x < region[2]; x++) {
                int fromGutter = right ? x : region[2] - 1 - x;
                int color = fromGutter == 0 ? PAPER_DARK : PAPER;
                boolean writing = y % 2 == 1 && y < region[3] - 1 && fromGutter >= 1 && fromGutter < region[2] - 1 && random.nextFloat() < 0.7F;
                p.set(region[0] + x, region[1] + y, writing ? INK : color);
            }
        }
        if (right) {
            int cx = region[0] + region[2] / 2;
            int cy = region[1] + region[3] / 2;
            int[][] sigil = {{0, -3}, {-1, -2}, {1, -2}, {-2, -1}, {2, -1}, {-2, 0}, {0, 0}, {2, 0}, {-2, 1}, {2, 1}, {-1, 2}, {1, 2}, {0, 3}};
            for (int[] at : sigil) {
                p.set(cx + at[0], cy + at[1], at[0] == 0 && at[1] == 0 ? GLOW : SICKLY);
            }
        }
    }

    static String iconModel() {
        return """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "scarlet:item/darkhold"
                  }
                }
                """;
    }

    /**
     * Standing on its tail, its cover facing south: covers, the block of pages, the spine, iron at the corners, the
     * raised crest and the strap.
     */
    static String closedModel() {
        List<String> elements = new ArrayList<>();
        elements.add(box(3, 1, 5.5, 13, 15, 6, face("north", BACK), sides(PLAIN, "east", "west", "up", "down")));
        elements.add(box(3, 1, 9, 13, 15, 9.5, face("south", FRONT), sides(PLAIN, "east", "west", "up", "down")));
        elements.add(box(3.5, 1.5, 6, 12.5, 14.5, 9, face("east", EDGE), face("up", TOP), face("down", TOP)));
        elements.add(box(2.5, 1, 5.5, 3.5, 15, 9.5, face("west", SPINE), sides(PLAIN, "north", "south", "up", "down")));
        for (double[] corner : new double[][] {{3, 1}, {11.5, 1}, {3, 13.5}, {11.5, 13.5}}) {
            elements.add(box(corner[0], corner[1], 9.5, corner[0] + 1.5, corner[1] + 1.5, 9.85,
                    sides(IRON_REGION, "north", "south", "east", "west", "up", "down")));
        }
        elements.add(box(5.5, 4, 9.5, 10.5, 11, 9.9, face("south", CREST_REGION), sides(IRON_REGION, "east", "west", "up", "down")));
        elements.add(box(12.6, 7, 5.3, 13.7, 9, 9.7, sides(STRAP, "east", "north", "south", "up", "down")));
        // in the third person hand, before these: +y points on from the fist, +z along the arm, +x across it; carried
        // upright at the side, the cover facing out
        return model(elements, """
                    "thirdperson_righthand": { "rotation": [ 90, 90, 0 ], "translation": [ 0.5, 0.5, -2 ], "scale": [ 0.5, 0.5, 0.5 ] },
                    "thirdperson_lefthand": { "rotation": [ 90, -90, 0 ], "translation": [ -0.5, 0.5, -2 ], "scale": [ 0.5, 0.5, 0.5 ] },
                    "firstperson_righthand": { "rotation": [ 0, -20, 0 ], "translation": [ -1.5, 5, -1 ], "scale": [ 0.4, 0.4, 0.4 ] },
                    "firstperson_lefthand": { "rotation": [ 0, 20, 0 ], "translation": [ 1.5, 5, -1 ], "scale": [ 0.4, 0.4, 0.4 ] },
                    "ground": { "translation": [ 0, 2, 0 ], "scale": [ 0.45, 0.45, 0.45 ] },
                    "fixed": { "rotation": [ 0, 180, 0 ], "scale": [ 0.9, 0.9, 0.9 ] },
                    "head": { "translation": [ 0, 13, 7 ], "scale": [ 0.8, 0.8, 0.8 ] }""");
    }

    /**
     * Lying open, its pages up and its spine running north to south down the middle.
     */
    static String openModel() {
        List<String> elements = new ArrayList<>();
        elements.add(box(1, 0, 2, 8, 0.5, 14, sides(PLAIN, "north", "south", "east", "west", "up", "down")));
        elements.add(box(8, 0, 2, 15, 0.5, 14, sides(PLAIN, "north", "south", "east", "west", "up", "down")));
        elements.add(box(1.5, 0.5, 2.5, 8, 2, 13.5, face("up", LEFT_PAGE), sides(TOP, "north", "south", "west")));
        elements.add(box(8, 0.5, 2.5, 14.5, 2.4, 13.5, face("up", RIGHT_PAGE), sides(TOP, "north", "south", "east")));
        elements.add(box(7.5, -0.4, 2, 8.5, 0.5, 14, sides(SPINE, "north", "south", "east", "west", "down")));
        // pages up toward the reader's eyes, the book drawn back onto the hands and in between them; tilted toward the
        // view in the first person
        return model(elements, """
                    "thirdperson_righthand": { "rotation": [ 70, 0, 0 ], "translation": [ -1.5, 1.5, 4.5 ], "scale": [ 0.5, 0.5, 0.5 ] },
                    "thirdperson_lefthand": { "rotation": [ 70, 0, 0 ], "translation": [ 1.5, 1.5, 4.5 ], "scale": [ 0.5, 0.5, 0.5 ] },
                    "firstperson_righthand": { "rotation": [ 62, 0, 0 ], "translation": [ -7, 8, 0 ], "scale": [ 0.5, 0.5, 0.5 ] },
                    "firstperson_lefthand": { "rotation": [ 62, 0, 0 ], "translation": [ 7, 8, 0 ], "scale": [ 0.5, 0.5, 0.5 ] },
                    "ground": { "translation": [ 0, 2, 0 ], "scale": [ 0.45, 0.45, 0.45 ] },
                    "fixed": { "rotation": [ -90, 0, 0 ], "scale": [ 0.9, 0.9, 0.9 ] }""");
    }

    static String itemDefinition() {
        return """
                {
                  "model": {
                    "type": "minecraft:select",
                    "property": "minecraft:display_context",
                    "cases": [
                      {
                        "when": "gui",
                        "model": { "type": "minecraft:model", "model": "scarlet:item/darkhold" }
                      }
                    ],
                    "fallback": {
                      "type": "minecraft:condition",
                      "property": "minecraft:using_item",
                      "on_true": { "type": "minecraft:model", "model": "scarlet:item/darkhold_open" },
                      "on_false": { "type": "minecraft:model", "model": "scarlet:item/darkhold_closed" }
                    }
                  }
                }
                """;
    }

    private static String model(List<String> elements, String display) {
        return "{\n  \"textures\": {\n    \"book\": \"scarlet:item/darkhold_book\",\n    \"particle\": \"scarlet:item/darkhold_book\"\n  },\n"
                + "  \"elements\": [\n" + String.join(",\n", elements) + "\n  ],\n  \"display\": {\n" + display + "\n  }\n}\n";
    }

    private static String box(double x0, double y0, double z0, double x1, double y1, double z1, String... faces) {
        List<String> all = new ArrayList<>();
        for (String face : faces) {
            if (!face.isEmpty()) {
                all.add(face);
            }
        }
        return "    {\n      \"from\": [ " + AssetGen.num(x0) + ", " + AssetGen.num(y0) + ", " + AssetGen.num(z0) + " ],\n      \"to\": [ "
                + AssetGen.num(x1) + ", " + AssetGen.num(y1) + ", " + AssetGen.num(z1) + " ],\n      \"faces\": {\n"
                + String.join(",\n", all) + "\n      }\n    }";
    }

    private static String face(String side, int[] region) {
        // 32 pixel texture: two pixels to each unit of uv
        return "        \"" + side + "\": { \"uv\": [ " + AssetGen.num(region[0] / 2.0) + ", " + AssetGen.num(region[1] / 2.0) + ", "
                + AssetGen.num((region[0] + region[2]) / 2.0) + ", " + AssetGen.num((region[1] + region[3]) / 2.0) + " ], \"texture\": \"#book\" }";
    }

    private static String sides(int[] region, String... sides) {
        List<String> faces = new ArrayList<>();
        for (String side : sides) {
            faces.add(face(side, region));
        }
        return String.join(",\n", faces);
    }

    // ---------------------------------------------------------------- the veins

    private static final int VEIN_WIDTH = 480;
    private static final int VEIN_HEIGHT = 270;

    /**
     * Dark veins reaching in from the edges of the view, thickest at the corners, over a soft darkening at the very
     * edge: black at their skins, a deep sickly red down their middles, thinning to nothing as they reach in.
     */
    static BufferedImage veins() {
        int w = VEIN_WIDTH;
        int h = VEIN_HEIGHT;
        float[] vein = new float[w * h];
        float[] core = new float[w * h];
        Random random = new Random(1666);
        for (int i = 0; i < 46; i++) {
            grow(vein, core, random, root(random, w, h), 2.2 + random.nextDouble() * 2.2, 70 + random.nextDouble() * 90, 0);
        }
        // fine threads among them, close to the edge
        for (int i = 0; i < 90; i++) {
            grow(vein, core, random, root(random, w, h), 0.9 + random.nextDouble() * 0.7, 18 + random.nextDouble() * 34, 2);
        }
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double edge = Math.min(Math.min(x, w - 1 - x) / (double) w, Math.min(y, h - 1 - y) / (double) h * h / w);
                double shade = Math.pow(Math.max(0.0, 1.0 - edge / 0.09), 2.0) * 0.6;
                float v = vein[y * w + x];
                double alpha = Math.max(shade, Math.min(1.0, v));
                int rgb = v > shade ? mix(mix(0x120106, DEEP, core[y * w + x]), ABYSS, 0.15) : VOID;
                int a = (int) Math.round(Math.clamp(alpha, 0.0, 1.0) * 255);
                image.setRGB(x, y, (a << 24) | rgb);
            }
        }
        return image;
    }

    /**
     * Where a vein starts: just outside an edge, more of them toward the corners, heading in.
     */
    private static double[] root(Random random, int w, int h) {
        int side = random.nextInt(4);
        // toward the corners: the distribution of a cube root bunches at both ends
        double t = 0.5 + 0.5 * Math.cbrt(random.nextDouble() * 2.0 - 1.0);
        double x;
        double y;
        double angle;
        switch (side) {
            case 0 -> {
                x = t * w;
                y = -2;
                angle = Math.PI / 2;
            }
            case 1 -> {
                x = t * w;
                y = h + 2;
                angle = -Math.PI / 2;
            }
            case 2 -> {
                x = -2;
                y = t * h;
                angle = 0;
            }
            default -> {
                x = w + 2;
                y = t * h;
                angle = Math.PI;
            }
        }
        return new double[] {x, y, angle + (random.nextDouble() - 0.5) * 1.2};
    }

    /**
     * Grows a vein from {@code start} (x, y, heading), wandering as it goes and branching now and then, thinning to its
     * tip.
     */
    private static void grow(float[] vein, float[] core, Random random, double[] start, double thickness, double length, int depth) {
        double x = start[0];
        double y = start[1];
        double angle = start[2];
        double step = 1.2;
        for (double s = 0; s < length; s += step) {
            double left = 1.0 - s / length;
            double radius = Math.max(0.35, thickness * Math.pow(left, 0.7)) / 2.0;
            stamp(vein, core, x, y, radius, 0.35 + 0.65 * left);
            angle += (random.nextDouble() - 0.5) * 0.32;
            x += Math.cos(angle) * step;
            y += Math.sin(angle) * step;
            if (depth < 3 && random.nextDouble() < 0.028 && left > 0.25) {
                double turn = (random.nextBoolean() ? 1 : -1) * (0.4 + random.nextDouble() * 0.5);
                grow(vein, core, random, new double[] {x, y, angle + turn}, thickness * left * 0.65, length * left * 0.6, depth + 1);
            }
        }
    }

    private static void stamp(float[] vein, float[] core, double cx, double cy, double radius, double strength) {
        int w = VEIN_WIDTH;
        int h = VEIN_HEIGHT;
        int x0 = (int) Math.floor(cx - radius - 1);
        int x1 = (int) Math.ceil(cx + radius + 1);
        int y0 = (int) Math.floor(cy - radius - 1);
        int y1 = (int) Math.ceil(cy + radius + 1);
        for (int y = Math.max(0, y0); y <= Math.min(h - 1, y1); y++) {
            for (int x = Math.max(0, x0); x <= Math.min(w - 1, x1); x++) {
                double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                double cover = Math.clamp(radius + 0.5 - d, 0.0, 1.0) * strength;
                int i = y * w + x;
                vein[i] = (float) Math.max(vein[i], cover);
                double middle = Math.clamp(1.0 - d / Math.max(0.5, radius * 0.5), 0.0, 1.0) * strength;
                core[i] = (float) Math.max(core[i], middle);
            }
        }
    }

    private static int mix(int from, int to, double amount) {
        double t = Math.clamp(amount, 0.0, 1.0);
        int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    // ---------------------------------------------------------------- pixels

    /** A small image painted pixel by pixel; -1 is clear. */
    private static final class Pixels {
        final int width;
        final int height;
        final int[] color;

        Pixels(int width, int height) {
            this.width = width;
            this.height = height;
            this.color = new int[width * height];
            java.util.Arrays.fill(color, -1);
        }

        void set(int x, int y, int rgb) {
            if (x >= 0 && y >= 0 && x < width && y < height) {
                color[y * width + x] = rgb;
            }
        }

        boolean inked(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height && color[y * width + x] >= 0;
        }

        BufferedImage image() {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = color[y * width + x];
                    if (rgb >= 0) {
                        image.setRGB(x, y, 0xFF000000 | rgb);
                    }
                }
            }
            return image;
        }

        /** With a dark line around everything painted, so it reads on any background. */
        BufferedImage outlined(int outline) {
            BufferedImage image = image();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (!inked(x, y) && (inked(x - 1, y) || inked(x + 1, y) || inked(x, y - 1) || inked(x, y + 1))) {
                        image.setRGB(x, y, 0xEB000000 | outline);
                    }
                }
            }
            return image;
        }
    }
}
