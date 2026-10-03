import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Darkhold's art: its inventory icon, the tome carried in the hand, the tome floating open while it is read, and the
 * veins its corruption sends crawling in at the edges of the view.
 *
 * <p>As in the films, it is an ancient grimoire bound in stone and metal more than leather: a slab of weathered dark
 * stone with a raised, chipped frame, the roots and branches of a great tree carved spreading from a stone set in bronze
 * that glows sickly red, tiles of garnet mosaic beside it, and a spine of tarnished bronze cut with hieroglyphs. Its
 * pages are old vellum, written close in a dark hand around red runes that burn as it is read.
 *
 * <p>Run with everything else from {@link AssetGen}, or on its own: {@code java tools/assetgen/DarkholdArt.java}.
 */
final class DarkholdArt {

    static final int VOID = 0x0B0507;
    static final int STONE_DEEP = 0x140E10;
    static final int STONE_DARK = 0x221A1C;
    static final int STONE = 0x32282B;
    static final int STONE_LIGHT = 0x4A3B3F;
    static final int STONE_EDGE = 0x6E5B5E;
    static final int BRONZE_DARK = 0x2B2017;
    static final int BRONZE = 0x54402A;
    static final int BRONZE_LIGHT = 0x8A6E45;
    static final int LINING_DARK = 0x16050A;
    static final int LINING = 0x260A11;
    static final int VELLUM = 0xAE9874;
    static final int VELLUM_DARK = 0x84705A;
    static final int INK = 0x3A2A24;
    static final int MOSAIC = 0x7A1626;
    static final int WINE = 0x5C0717;
    static final int SICKLY = 0x9B1B30;
    static final int GLOW = 0xE0143C;
    static final int HOT = 0xFF7A8C;
    static final int ABYSS = 0x3A0010;
    static final int DEEP = 0x6A0B20;

    /**
     * The front cover, 10 by 14: {@code T t S s d} stone from the lit raised edges down to the deep cuts, {@code ~} the
     * weathered field, {@code L B b} bronze, {@code m w} mosaic, {@code r G H} the stone burning from sickly to hot, and
     * {@code .} chipped away.
     */
    private static final String[] FRONT_COVER = {
            ".TTTTTTTt.",
            "TTd~~~~~td",
            "T~Td~~~tsd",
            "T~~Td~ts~d",
            "T~~~LBs~~d",
            "Tm~LHGb~md",
            "Tw~BGrb~wd",
            "T~~~Bbd~~d",
            "T~~~Ttd~~d",
            "T~~~Ttd~~d",
            "T~~~Ttd~~d",
            "T~~TTttd~d",
            "T~Td~~dtdd",
            ".dddddddd.",
    };

    /** The back cover: the frame and one carved ring, its middle dark wine. */
    private static final String[] BACK_COVER = {
            ".TTTTTTTt.",
            "T~~~~~~~~d",
            "T~~~~~~~~d",
            "T~~~~~~~~d",
            "T~~~TTt~~d",
            "T~~T~~~s~d",
            "T~~T~ww~sd",
            "T~~t~ww~sd",
            "T~~~s~~s~d",
            "T~~~~ss~~d",
            "T~~~~~~~~d",
            "T~~~~~~~~d",
            "T~~~~~~~~d",
            ".dddddddd.",
    };

    /** The spine, 4 by 14: bronze bands, and between them hieroglyphs, some of them red. */
    private static final String[] SPINE_GLYPHS = {
            "bLLb",
            "bBBb",
            "bdBb",
            "bBdb",
            "bLLb",
            "bBrb",
            "brBb",
            "bBrb",
            "bLLb",
            "bdBb",
            "bBdb",
            "bdBb",
            "bLLb",
            "bbbb",
    };

    /** The raised bronze setting of the stone on the carried tome's cover, 4 by 4. */
    private static final String[] SETTING = {
            "bLBb",
            "LHGb",
            "BGrb",
            "bBbb",
    };

    private DarkholdArt() {
    }

    public static void main(String[] args) throws IOException {
        generate(Path.of(args.length > 0 ? args[0] : "common/src/main/resources/assets/scarlet"), Path.of("build/assetgen"));
    }

    static void generate(Path assets, Path preview) throws IOException {
        BufferedImage icon = icon();
        BufferedImage book = bookTexture();
        BufferedImage open = OpenBook.texture(false);
        BufferedImage glow = OpenBook.texture(true);
        BufferedImage veins = veins();
        AssetGen.writePng(icon, assets.resolve("textures/item/darkhold.png"));
        AssetGen.writePng(book, assets.resolve("textures/item/darkhold_book.png"));
        AssetGen.writePng(open, assets.resolve("textures/entity/darkhold.png"));
        AssetGen.writePng(glow, assets.resolve("textures/entity/darkhold_glow.png"));
        AssetGen.writeText(iconModel(), assets.resolve("models/item/darkhold.json"));
        AssetGen.writeText(closedModel(), assets.resolve("models/item/darkhold_closed.json"));
        Files.deleteIfExists(assets.resolve("models/item/darkhold_open.json"));
        AssetGen.writeText(itemDefinition(), assets.resolve("items/darkhold.json"));
        AssetGen.writePng(veins, assets.resolve("textures/gui/sprites/hud/darkhold_veins.png"));
        AssetGen.writePng(AssetGen.zoomSheet(List.of(icon, book), 256), preview.resolve("darkhold.png"));
        AssetGen.writePng(zoom(open, 10), preview.resolve("darkhold_open.png"));
        AssetGen.writePng(zoom(glow, 10), preview.resolve("darkhold_open_glow.png"));
        AssetGen.writePng(veins, preview.resolve("darkhold_veins.png"));
        System.out.println("Darkhold assets written to " + assets.toAbsolutePath());
    }

    private static BufferedImage zoom(BufferedImage image, int factor) {
        BufferedImage zoomed = new BufferedImage(image.getWidth() * factor, image.getHeight() * factor, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = zoomed.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(image, 0, 0, zoomed.getWidth(), zoomed.getHeight(), null);
        g.dispose();
        return zoomed;
    }

    // ---------------------------------------------------------------- the icon

    /**
     * The tome face on: its spine to the left, the edges of its pages to the right.
     */
    static BufferedImage icon() {
        Pixels p = new Pixels(16, 16);
        Random random = new Random(1666);
        paint(p, 4, 1, FRONT_COVER, random);
        for (int y = 0; y < SPINE_GLYPHS.length; y++) {
            p.set(2, 1 + y, color(SPINE_GLYPHS[y].charAt(0), random));
            p.set(3, 1 + y, color(SPINE_GLYPHS[y].charAt(2), random));
        }
        for (int y = 2; y <= 13; y++) {
            p.set(14, y, y % 2 == 0 ? VELLUM : VELLUM_DARK);
        }
        return p.outlined(VOID);
    }

    /**
     * Paints a map of pixels at x0, y0; {@code ~} is weathered stone, mottled at random.
     */
    private static void paint(Pixels p, int x0, int y0, String[] rows, Random random) {
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                int color = color(rows[y].charAt(x), random);
                if (color >= 0) {
                    p.set(x0 + x, y0 + y, color);
                }
            }
        }
    }

    private static int color(char code, Random random) {
        return switch (code) {
            case 'T' -> STONE_EDGE;
            case 't' -> STONE_LIGHT;
            case 'S' -> STONE;
            case 's' -> STONE_DARK;
            case 'd' -> STONE_DEEP;
            case '~' -> weathered(random);
            case 'L' -> BRONZE_LIGHT;
            case 'B' -> BRONZE;
            case 'b' -> BRONZE_DARK;
            case 'm' -> MOSAIC;
            case 'w' -> WINE;
            case 'r' -> SICKLY;
            case 'G' -> GLOW;
            case 'H' -> HOT;
            case 'P' -> VELLUM;
            case 'p' -> VELLUM_DARK;
            case 'i' -> INK;
            case 'l' -> LINING;
            case 'k' -> LINING_DARK;
            default -> -1;
        };
    }

    /**
     * Old stone, mottled and pitted.
     */
    private static int weathered(Random random) {
        float roll = random.nextFloat();
        return roll < 0.18F ? STONE_DARK : roll > 0.93F ? STONE_LIGHT : STONE;
    }

    // ---------------------------------------------------------------- the tome carried in the hand

    /** Regions of the 32x32 texture of the tome carried shut, in pixels: x, y, width, height. */
    private static final int[] FRONT = {0, 0, 10, 14};
    private static final int[] BACK = {10, 0, 10, 14};
    private static final int[] SPINE = {20, 0, 4, 14};
    private static final int[] EDGE = {24, 0, 3, 13};
    private static final int[] TOP = {0, 14, 9, 3};
    private static final int[] CORNER = {10, 14, 4, 4};
    private static final int[] GEM = {14, 14, 4, 4};
    private static final int[] PLAIN = {22, 14, 4, 4};

    static BufferedImage bookTexture() {
        Pixels p = new Pixels(32, 32);
        Random random = new Random(1666);
        paint(p, FRONT[0], FRONT[1], FRONT_COVER, random);
        paint(p, BACK[0], BACK[1], BACK_COVER, random);
        // a chipped corner shows the stone beneath, not a hole
        for (int[] cover : new int[][] {FRONT, BACK}) {
            for (int[] corner : new int[][] {{0, 0}, {cover[2] - 1, 0}, {0, cover[3] - 1}, {cover[2] - 1, cover[3] - 1}}) {
                p.set(cover[0] + corner[0], cover[1] + corner[1], STONE_DEEP);
            }
        }
        paint(p, SPINE[0], SPINE[1], SPINE_GLYPHS, random);
        for (int y = 0; y < EDGE[3]; y++) {
            for (int x = 0; x < EDGE[2]; x++) {
                p.set(EDGE[0] + x, EDGE[1] + y, x % 2 == 0 ? VELLUM : VELLUM_DARK);
            }
        }
        for (int y = 0; y < TOP[3]; y++) {
            for (int x = 0; x < TOP[2]; x++) {
                p.set(TOP[0] + x, TOP[1] + y, y % 2 == 0 ? VELLUM : VELLUM_DARK);
            }
        }
        for (int y = 0; y < CORNER[3]; y++) {
            for (int x = 0; x < CORNER[2]; x++) {
                p.set(CORNER[0] + x, CORNER[1] + y, x + y < 2 ? BRONZE_LIGHT : x + y > 4 ? BRONZE_DARK : BRONZE);
            }
        }
        paint(p, GEM[0], GEM[1], SETTING, random);
        for (int y = 0; y < PLAIN[3]; y++) {
            for (int x = 0; x < PLAIN[2]; x++) {
                p.set(PLAIN[0] + x, PLAIN[1] + y, weathered(random));
            }
        }
        return p.image();
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
     * Standing on its tail, its cover facing south: the stone covers, the block of pages, the bronze spine, bronze at
     * the corners and the raised setting of the stone.
     */
    static String closedModel() {
        List<String> elements = new ArrayList<>();
        elements.add(box(3, 1, 5.5, 13, 15, 6, face("north", BACK), sides(PLAIN, "east", "west", "up", "down")));
        elements.add(box(3, 1, 9, 13, 15, 9.5, face("south", FRONT), sides(PLAIN, "east", "west", "up", "down")));
        elements.add(box(3.5, 1.5, 6, 12.5, 14.5, 9, face("east", EDGE), face("up", TOP), face("down", TOP)));
        elements.add(box(2.5, 1, 5.5, 3.5, 15, 9.5, face("west", SPINE), sides(PLAIN, "north", "south", "up", "down")));
        for (double[] corner : new double[][] {{3, 1}, {11.5, 1}, {3, 13.5}, {11.5, 13.5}}) {
            elements.add(box(corner[0], corner[1], 9.5, corner[0] + 1.5, corner[1] + 1.5, 9.85,
                    sides(CORNER, "north", "south", "east", "west", "up", "down")));
        }
        // over the stone painted on the cover, columns 3 to 6 and rows 4 to 7 of it
        elements.add(box(6, 7, 9.5, 10, 11, 9.9, face("south", GEM), sides(CORNER, "east", "west", "up", "down")));
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
     * Shut in the hand; while it is read it leaves the hand to float open before the reader, drawn by the mod.
     */
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
                      "on_true": { "type": "minecraft:empty" },
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

    // ---------------------------------------------------------------- the tome floating open

    /**
     * The 64x32 texture of the tome as it floats open, laid out for the boxes of the mod's model of it (each box's faces
     * unfolded around its texture offset, as entity models are), and the mask of what burns on it, clear elsewhere: the
     * stone on its cover, the red of its spine's glyphs and the runes on its pages.
     */
    static final class OpenBook {

        /** Covers 7 by 11 and 1 thick, the spine 6 across, page blocks 6 by 10 and 2 thick, and the single leaves. */
        private static final int LID_W = 7;
        private static final int LID_H = 11;
        private static final int PAGE_W = 6;
        private static final int PAGE_H = 10;
        private static final int BLOCK = 2;

        private static final String[] FRONT_SMALL = {
                ".TTTTt.",
                "TTd~~td",
                "T~Tdtsd",
                "T~~LBsd",
                "TmLHGbd",
                "TwBGrbd",
                "T~~Bbdd",
                "T~~Ttdd",
                "T~~Ttdd",
                "T~TTttd",
                ".ddddd.",
        };

        private static final String[] BACK_SMALL = {
                ".TTTTt.",
                "T~~~~~d",
                "T~~~~~d",
                "T~TTt~d",
                "T~Tws~d",
                "T~tss~d",
                "T~~~~~d",
                "T~~~~~d",
                "T~~~~~d",
                "T~~~~~d",
                ".ddddd.",
        };

        private static final String[] SPINE_SMALL = {
                "bLLLLb",
                "bBdBBb",
                "bBBdBb",
                "bLLLLb",
                "bBrBrb",
                "bBBrBb",
                "bBrBrb",
                "bLLLLb",
                "bdBBdb",
                "bBdBBb",
                "bbbbbb",
        };

        /** Inside the covers, a lining near black with a cut border. */
        private static final String[] LINING_MAP = {
                "kkkkkkk",
                "kllllkk",
                "klkkkkk",
                "klkkkkk",
                "klkkkkk",
                "klkkkkk",
                "klkkkkk",
                "klkkkkk",
                "klkkkkk",
                "kkkkkkk",
                "kkkkkkk",
        };

        /** The left page, written close, with red runes among the writing. */
        private static final String[] LEFT_PAGE = {
                "PPPPPP",
                "PiiPiP",
                "PPPPPP",
                "PiPiiP",
                "PPPPPP",
                "PrPiiP",
                "PPPPPP",
                "PiirPP",
                "PPPPPP",
                "PiPiiP",
        };

        /** The right page: a great sigil, an eye in a diamond, burning as it is read, writing above and below it. */
        private static final String[] RIGHT_PAGE = {
                "PPPPPP",
                "PiiiiP",
                "PPPPPP",
                "PPrrPP",
                "PrPPrP",
                "rPGGPr",
                "PrPPrP",
                "PPrrPP",
                "PPPPPP",
                "PiiPiP",
        };

        private static final String[] LEAF_A = {
                "PPPPPP",
                "PiPiiP",
                "PPPPPP",
                "PiiPrP",
                "PPPPPP",
                "PiPiiP",
                "PPPPPP",
                "PrPiPP",
                "PPPPPP",
                "PiiPiP",
        };

        private static final String[] LEAF_B = {
                "PPPPPP",
                "PiiPiP",
                "PPPPPP",
                "PPrrPP",
                "PrGGrP",
                "PPrrPP",
                "PPPPPP",
                "PiPiiP",
                "PPPPPP",
                "PiiPiP",
        };

        private OpenBook() {
        }

        /**
         * @param glow just what burns, bright on black; the rest left clear
         */
        static BufferedImage texture(boolean glow) {
            Pixels p = new Pixels(64, 32);
            Random random = new Random(1666);
            // the back cover, whose box sits behind the left pages: inside to the north, outside to the south
            lid(p, 0, 0, LINING_MAP, BACK_SMALL, random);
            // the front cover, its box behind the right pages: inside to the north, outside (with the stone) to the south
            lid(p, 16, 0, LINING_MAP, FRONT_SMALL, random);
            // the spine, its outside to the north
            box(p, 32, 0, 6, LID_H, 1, random);
            paint(p, 33, 1, SPINE_SMALL, random);
            // the blocks of pages, the left one read on its south face, the right on its north
            pages(p, 0, 16);
            paint(p, BLOCK * 2 + PAGE_W, 16 + BLOCK, LEFT_PAGE, random);
            pages(p, 16, 16);
            paint(p, 16 + BLOCK, 16 + BLOCK, RIGHT_PAGE, random);
            // a single leaf turning, a side to each face
            paint(p, 32, 16, LEAF_A, random);
            paint(p, 32 + PAGE_W, 16, LEAF_B, random);
            return glow ? p.glowing() : p.image();
        }

        /**
         * A cover's box: its edges stone, its inside and outside painted from the maps given.
         */
        private static void lid(Pixels p, int u, int v, String[] inside, String[] outside, Random random) {
            box(p, u, v, LID_W, LID_H, 1, random);
            paint(p, u + 1, v + 1, inside, random);
            paint(p, u + 1 + LID_W + 1, v + 1, outside, random);
            // a chipped corner shows the stone beneath, not a hole
            for (int[] corner : new int[][] {{0, 0}, {LID_W - 1, 0}, {0, LID_H - 1}, {LID_W - 1, LID_H - 1}}) {
                p.set(u + 1 + LID_W + 1 + corner[0], v + 1 + corner[1], STONE_DEEP);
            }
        }

        /**
         * Every face of a box of stone, unfolded around its texture offset.
         */
        private static void box(Pixels p, int u, int v, int w, int h, int d, Random random) {
            for (int y = 0; y < d + h; y++) {
                for (int x = 0; x < 2 * (w + d); x++) {
                    if (y < d && (x < d || x >= d + 2 * w)) {
                        continue;
                    }
                    p.set(u + x, v + y, weathered(random));
                }
            }
        }

        /**
         * A block of pages: vellum edges, a line to each leaf.
         */
        private static void pages(Pixels p, int u, int v) {
            for (int y = 0; y < BLOCK + PAGE_H; y++) {
                for (int x = 0; x < 2 * (PAGE_W + BLOCK); x++) {
                    if (y < BLOCK && (x < BLOCK || x >= BLOCK + 2 * PAGE_W)) {
                        continue;
                    }
                    boolean edgeAcross = y < BLOCK;
                    p.set(u + x, v + y, (edgeAcross ? y : x) % 2 == 0 ? VELLUM : VELLUM_DARK);
                }
            }
        }
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
                int rgb = v > shade ? mix(mix(0x120106, DEEP, core[y * w + x]), ABYSS, 0.15) : 0x0B0004;
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

        /** Only what burns, brighter, the rest clear: drawn over it unlit. */
        BufferedImage glowing() {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = color[y * width + x];
                    int light = rgb == HOT ? 0xFF8C9E : rgb == GLOW ? 0xFF2A4A : rgb == SICKLY ? 0xC21E3A : -1;
                    if (light >= 0) {
                        image.setRGB(x, y, 0xFF000000 | light);
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
