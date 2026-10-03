import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Generates the era decorations: furniture that changes model with the Hex's era. Every piece is authored here as
 * boxes of material, facing north, with painted detail where a face needs it (a screen, a dial, a clock face), and
 * written out as block models, blockstates, item definitions, loot tables and textures.
 *
 * <p>Materials are tileable 16x16 textures laid over boxes by position, the way vanilla's own blocks are, so wood grain
 * and fabric run on across a piece at one texel per model unit. Details are painted at exactly the size of the face
 * they go on, so they keep that density too.
 *
 * <p>Run from the repository root: {@code java tools/assetgen/Decor.java}, or as part of {@code AssetGen}.
 */
public final class Decor {

    static final String NS = "scarlet";
    static final String[] ERAS = {"1950s", "1960s", "1970s", "1980s", "2000s", "present"};
    static final String[] FACINGS = {"north", "east", "south", "west"};

    /** Every texture to write, by its path under {@code textures/block/}. */
    static final Map<String, Tex> TEXTURES = new LinkedHashMap<>();
    /** Animated textures' frame times, by path. */
    static final Map<String, Integer> ANIMATED = new LinkedHashMap<>();
    /** Every model to write, by its path under {@code models/block/}. */
    static final Map<String, Model> MODELS = new LinkedHashMap<>();

    private Decor() {
    }

    public static void main(String[] args) throws IOException {
        Path resources = Path.of(args.length > 0 ? args[0] : "common/src/main/resources");
        generate(resources);
    }

    static void generate(Path resources) throws IOException {
        Path assets = resources.resolve("assets/" + NS);
        Path data = resources.resolve("data/" + NS);
        DecorMaterials.register();
        List<Piece> pieces = new ArrayList<>();
        pieces.addAll(DecorLiving.pieces());
        pieces.addAll(DecorKitchen.pieces());
        pieces.addAll(DecorWall.pieces());
        Piece car = DecorCar.piece();
        car.hasItem = false;
        pieces.add(car);
        for (Piece piece : pieces) {
            piece.build();
        }
        // only what some model uses is written
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Model model : MODELS.values()) {
            if (model.particle != null) {
                used.add(model.particle);
            }
            for (Element element : model.elements) {
                for (FaceDef face : element.faces.values()) {
                    used.add(face.texture());
                }
            }
        }
        Path textures = assets.resolve("textures/block");
        deleteTree(textures.resolve("decor"));
        for (Map.Entry<String, Tex> entry : TEXTURES.entrySet()) {
            if (!used.contains(NS + ":block/" + entry.getKey())) {
                continue;
            }
            Path png = textures.resolve(entry.getKey() + ".png");
            AssetGen.writePng(entry.getValue().image(), png);
            Integer frametime = ANIMATED.get(entry.getKey());
            if (frametime != null) {
                AssetGen.writeText("{\n  \"animation\": {\n    \"frametime\": " + frametime + "\n  }\n}\n", textures.resolve(entry.getKey() + ".png.mcmeta"));
            }
        }
        Path models = assets.resolve("models/block");
        for (Piece piece : pieces) {
            deleteTree(models.resolve(piece.id));
        }
        for (Map.Entry<String, Model> entry : MODELS.entrySet()) {
            AssetGen.writeText(entry.getValue().json(), models.resolve(entry.getKey() + ".json"));
        }
        for (Piece piece : pieces) {
            AssetGen.writeText(piece.blockstate(), assets.resolve("blockstates/" + piece.id + ".json"));
            if (piece.hasItem) {
                AssetGen.writeText(piece.itemDefinition(), assets.resolve("items/" + piece.id + ".json"));
                AssetGen.writeText(piece.lootTable(), data.resolve("loot_table/blocks/" + piece.id + ".json"));
            }
        }
        // the car's body is no block anyone puts down: the parked car is an entity, and its item shows the whole car
        AssetGen.writeText(car.itemDefinition(), assets.resolve("items/parked_car.json"));
        recipes(data);
        System.out.println(pieces.size() + " era decorations, " + MODELS.size() + " models and " + used.size() + " textures written to "
                + assets.toAbsolutePath());
    }

    /**
     * How each piece is made in survival, from things a house has about it, and the advancement that teaches it once
     * its main ingredient is in hand.
     */
    private static void recipes(Path data) throws IOException {
        record Recipe(String id, int count, String unlock, String[] pattern, String... keys) {
        }
        List<Recipe> recipes = List.of(
                new Recipe("television", 1, "minecraft:redstone", new String[] {"PPP", "PGP", "PRP"},
                        "P", "#minecraft:planks", "G", "minecraft:glass_pane", "R", "minecraft:redstone"),
                new Recipe("radio", 1, "minecraft:note_block", new String[] {"PPP", "PNP"}, "P", "#minecraft:planks", "N", "minecraft:note_block"),
                new Recipe("telephone", 1, "minecraft:copper_ingot", new String[] {"RCR", "SSS"},
                        "R", "minecraft:redstone", "C", "minecraft:copper_ingot", "S", "minecraft:smooth_stone_slab"),
                new Recipe("couch", 3, "#minecraft:wool", new String[] {"WWW", "PPP"}, "W", "#minecraft:wool", "P", "#minecraft:planks"),
                new Recipe("armchair", 1, "#minecraft:wool", new String[] {"W W", "WWW", "P P"}, "W", "#minecraft:wool", "P", "#minecraft:planks"),
                new Recipe("lamp", 1, "minecraft:torch", new String[] {" P ", "PTP", " S "},
                        "P", "minecraft:paper", "T", "minecraft:torch", "S", "minecraft:smooth_stone_slab"),
                new Recipe("refrigerator", 1, "minecraft:ice", new String[] {"III", "ICI", "III"}, "I", "minecraft:iron_ingot", "C", "minecraft:ice"),
                new Recipe("stove", 1, "minecraft:furnace", new String[] {"III", "IFI", "III"}, "I", "minecraft:iron_ingot", "F", "minecraft:furnace"),
                new Recipe("toaster", 1, "minecraft:iron_nugget", new String[] {"NNN", "NRN"}, "N", "minecraft:iron_nugget", "R", "minecraft:redstone"),
                new Recipe("wall_clock", 1, "minecraft:clock", new String[] {" S ", "SCS", " S "}, "S", "minecraft:stick", "C", "minecraft:clock"),
                new Recipe("picture_frame", 1, "minecraft:painting", new String[] {"SSS", "SPS", "SSS"}, "S", "minecraft:stick", "P", "minecraft:painting"),
                new Recipe("poster", 2, "minecraft:paper", new String[] {"PR", "PP"}, "P", "minecraft:paper", "R", "minecraft:red_dye"),
                new Recipe("parked_car", 1, "minecraft:iron_ingot", new String[] {"GGG", "III", "C C"},
                        "G", "minecraft:glass_pane", "I", "minecraft:iron_ingot", "C", "minecraft:coal"));
        for (Recipe recipe : recipes) {
            List<String> keys = new ArrayList<>();
            for (int k = 0; k < recipe.keys().length; k += 2) {
                keys.add("    \"" + recipe.keys()[k] + "\": \"" + recipe.keys()[k + 1] + "\"");
            }
            List<String> rows = new ArrayList<>();
            for (String row : recipe.pattern()) {
                rows.add("    \"" + row + "\"");
            }
            String json = """
                    {
                      "type": "minecraft:crafting_shaped",
                      "category": "misc",
                      "key": {
                    %s
                      },
                      "pattern": [
                    %s
                      ],
                      "result": {
                        "count": %d,
                        "id": "%s:%s"
                      }
                    }
                    """.formatted(String.join(",\n", keys), String.join(",\n", rows), recipe.count(), NS, recipe.id());
            AssetGen.writeText(json, data.resolve("recipe/" + recipe.id() + ".json"));
            String unlock = recipe.unlock().startsWith("#")
                    ? "{ \"items\": \"" + recipe.unlock() + "\" }" : "{ \"items\": \"" + recipe.unlock() + "\" }";
            String advancement = """
                    {
                      "parent": "minecraft:recipes/root",
                      "criteria": {
                        "has_ingredient": {
                          "conditions": {
                            "items": [ %s ]
                          },
                          "trigger": "minecraft:inventory_changed"
                        },
                        "has_the_recipe": {
                          "conditions": { "recipes": "%s:%s" },
                          "trigger": "minecraft:recipe_unlocked"
                        }
                      },
                      "requirements": [ [ "has_the_recipe", "has_ingredient" ] ],
                      "rewards": { "recipes": [ "%s:%s" ] }
                    }
                    """.formatted(unlock, NS, recipe.id(), NS, recipe.id());
            AssetGen.writeText(advancement, data.resolve("advancement/recipes/decorations/" + recipe.id() + ".json"));
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    // ---------------------------------------------------------------- color

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    static int hex(int rgb) {
        return 0xFF000000 | rgb;
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static int r(int c) {
        return c >> 16 & 0xFF;
    }

    static int g(int c) {
        return c >> 8 & 0xFF;
    }

    static int b(int c) {
        return c & 0xFF;
    }

    static int a(int c) {
        return c >>> 24;
    }

    /** A color lighter or darker by a factor, 1 being itself. */
    static int shade(int c, double k) {
        return a(c) << 24 | clamp((int) Math.round(r(c) * k)) << 16 | clamp((int) Math.round(g(c) * k)) << 8 | clamp((int) Math.round(b(c) * k));
    }

    /** Toward white by a share. */
    static int tint(int c, double t) {
        return mix(c, 0xFFFFFFFF, t);
    }

    static int mix(int a, int b, double t) {
        return Math.max(a(a), a(b)) << 24 | clamp((int) Math.round(r(a) + (r(b) - r(a)) * t)) << 16
                | clamp((int) Math.round(g(a) + (g(b) - g(a)) * t)) << 8 | clamp((int) Math.round(b(a) + (b(b) - b(a)) * t));
    }

    static int alpha(int c, int alpha) {
        return alpha << 24 | c & 0xFFFFFF;
    }

    static int gray(int c) {
        int l = (int) Math.round(r(c) * 0.3 + g(c) * 0.59 + b(c) * 0.11);
        return a(c) << 24 | l << 16 | l << 8 | l;
    }

    /** A steady hash noise in [0, 1). */
    static double noise(long seed, int x, int y) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xBF58476D1CE4E5B9L + y * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        return (h >>> 11) / (double) (1L << 53);
    }

    // ---------------------------------------------------------------- painting

    /**
     * A texture being painted: one or more frames, each {@code w} by {@code h}, stacked down the image for animation.
     */
    static final class Tex {
        final int w;
        final int h;
        final int frames;
        final int[] px;
        /** The frame painting goes to. */
        int frame;

        Tex(int w, int h) {
            this(w, h, 1);
        }

        Tex(int w, int h, int frames) {
            this.w = w;
            this.h = h;
            this.frames = frames;
            this.px = new int[w * h * frames];
        }

        Tex frame(int frame) {
            this.frame = frame;
            return this;
        }

        int get(int x, int y) {
            if (x < 0 || y < 0 || x >= w || y >= h) {
                return 0;
            }
            return px[(frame * h + y) * w + x];
        }

        Tex px(int x, int y, int c) {
            if (x >= 0 && y >= 0 && x < w && y < h) {
                int i = (frame * h + y) * w + x;
                px[i] = a(c) == 255 || a(px[i]) == 0 ? c : over(px[i], c);
            }
            return this;
        }

        private static int over(int under, int c) {
            double t = a(c) / 255.0;
            return mix(under, c | 0xFF000000, t) | 0xFF000000;
        }

        Tex fill(int c) {
            return rect(0, 0, w, h, c);
        }

        Tex rect(int x, int y, int rw, int rh, int c) {
            for (int j = y; j < y + rh; j++) {
                for (int i = x; i < x + rw; i++) {
                    px(i, j, c);
                }
            }
            return this;
        }

        /** An outline one texel wide. */
        Tex frameRect(int x, int y, int rw, int rh, int c) {
            hline(x, x + rw - 1, y, c);
            hline(x, x + rw - 1, y + rh - 1, c);
            vline(x, y, y + rh - 1, c);
            vline(x + rw - 1, y, y + rh - 1, c);
            return this;
        }

        /** A rectangle with its corners rounded off by a texel, filled. */
        Tex round(int x, int y, int rw, int rh, int c) {
            rect(x + 1, y, rw - 2, rh, c);
            rect(x, y + 1, 1, rh - 2, c);
            rect(x + rw - 1, y + 1, 1, rh - 2, c);
            return this;
        }

        Tex hline(int x0, int x1, int y, int c) {
            for (int i = Math.min(x0, x1); i <= Math.max(x0, x1); i++) {
                px(i, y, c);
            }
            return this;
        }

        Tex vline(int x, int y0, int y1, int c) {
            for (int j = Math.min(y0, y1); j <= Math.max(y0, y1); j++) {
                px(x, j, c);
            }
            return this;
        }

        Tex line(double x0, double y0, double x1, double y1, int c) {
            int n = (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 2) + 1;
            for (int k = 0; k <= n; k++) {
                double t = k / (double) n;
                px((int) Math.floor(x0 + (x1 - x0) * t), (int) Math.floor(y0 + (y1 - y0) * t), c);
            }
            return this;
        }

        /** A filled disc centered on a point, which may sit between texels. */
        Tex disc(double cx, double cy, double radius, int c) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double dx = x + 0.5 - cx;
                    double dy = y + 0.5 - cy;
                    if (dx * dx + dy * dy <= radius * radius) {
                        px(x, y, c);
                    }
                }
            }
            return this;
        }

        /** A ring between two radii. */
        Tex ring(double cx, double cy, double inner, double outer, int c) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double dx = x + 0.5 - cx;
                    double dy = y + 0.5 - cy;
                    double d = dx * dx + dy * dy;
                    if (d <= outer * outer && d >= inner * inner) {
                        px(x, y, c);
                    }
                }
            }
            return this;
        }

        /** Each texel lighter or darker by up to {@code amount}, steadily. */
        Tex noise(long seed, double amount) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = get(x, y);
                    if (a(c) == 0) {
                        continue;
                    }
                    double k = 1.0 + (Decor.noise(seed + frame * 7919L, x, y) * 2 - 1) * amount;
                    px[(frame * h + y) * w + x] = shade(c, k);
                }
            }
            return this;
        }

        /** Darkens toward the edges of a region, like a screen's curve or a vignette. */
        Tex vignette(int x, int y, int rw, int rh, double amount) {
            double cx = x + rw / 2.0;
            double cy = y + rh / 2.0;
            for (int j = y; j < y + rh; j++) {
                for (int i = x; i < x + rw; i++) {
                    double dx = (i + 0.5 - cx) / (rw / 2.0);
                    double dy = (j + 0.5 - cy) / (rh / 2.0);
                    double d = Math.min(1.0, dx * dx * 0.5 + dy * dy * 0.5);
                    int c = get(i, j);
                    if (a(c) != 0) {
                        px[(frame * h + j) * w + i] = shade(c, 1.0 - amount * d);
                    }
                }
            }
            return this;
        }

        /** Words in a three-by-five texel font, starting at a corner. */
        Tex text(int x, int y, String words, int c) {
            int at = x;
            for (char ch : words.toUpperCase(Locale.ROOT).toCharArray()) {
                String[] glyph = Font.glyph(ch);
                for (int j = 0; j < glyph.length; j++) {
                    for (int i = 0; i < glyph[j].length(); i++) {
                        if (glyph[j].charAt(i) == '#') {
                            px(at + i, y + j, c);
                        }
                    }
                }
                at += glyph[0].length() + 1;
            }
            return this;
        }

        static int textWidth(String words) {
            int width = 0;
            for (char ch : words.toUpperCase(Locale.ROOT).toCharArray()) {
                width += Font.glyph(ch)[0].length() + 1;
            }
            return Math.max(0, width - 1);
        }

        /** Copies this frame's texels to every other frame, to animate from. */
        Tex everyFrame() {
            int size = w * h;
            for (int f = 0; f < frames; f++) {
                if (f != frame) {
                    System.arraycopy(px, frame * size, px, f * size, size);
                }
            }
            return this;
        }

        /** Applies a painting to every frame in turn. */
        Tex eachFrame(java.util.function.IntConsumer paint) {
            for (int f = 0; f < frames; f++) {
                frame(f);
                paint.accept(f);
            }
            return frame(0);
        }

        /** Grayscale, the frame being painted only. */
        Tex grayscaleFrame() {
            int size = w * h;
            for (int i = frame * size; i < (frame + 1) * size; i++) {
                if (a(px[i]) != 0) {
                    px[i] = gray(px[i]);
                }
            }
            return this;
        }

        /** Grayscale, every frame. */
        Tex grayscale() {
            for (int i = 0; i < px.length; i++) {
                if (a(px[i]) != 0) {
                    px[i] = gray(px[i]);
                }
            }
            return this;
        }

        /** The image, padded out to a square of a power of two the atlas takes, frames stacked. */
        BufferedImage image() {
            int side = 16;
            while (side < w || side < h) {
                side *= 2;
            }
            BufferedImage image = new BufferedImage(side, side * frames, BufferedImage.TYPE_INT_ARGB);
            for (int f = 0; f < frames; f++) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        image.setRGB(x, f * side + y, px[(f * h + y) * w + x]);
                    }
                }
            }
            return image;
        }

        /** The side of the square image this is written as. */
        int side() {
            int side = 16;
            while (side < w || side < h) {
                side *= 2;
            }
            return side;
        }
    }

    /** A tiny pixel font for signs, displays and posters. */
    static final class Font {
        private static final Map<Character, String[]> GLYPHS = new LinkedHashMap<>();

        static {
            glyph('A', ".#.", "#.#", "###", "#.#", "#.#");
            glyph('B', "##.", "#.#", "##.", "#.#", "##.");
            glyph('C', ".##", "#..", "#..", "#..", ".##");
            glyph('D', "##.", "#.#", "#.#", "#.#", "##.");
            glyph('E', "###", "#..", "##.", "#..", "###");
            glyph('F', "###", "#..", "##.", "#..", "#..");
            glyph('G', ".##", "#..", "#.#", "#.#", ".##");
            glyph('H', "#.#", "#.#", "###", "#.#", "#.#");
            glyph('I', "###", ".#.", ".#.", ".#.", "###");
            glyph('J', "..#", "..#", "..#", "#.#", ".#.");
            glyph('K', "#.#", "#.#", "##.", "#.#", "#.#");
            glyph('L', "#..", "#..", "#..", "#..", "###");
            glyph('M', "#.#", "###", "###", "#.#", "#.#");
            glyph('N', "##.", "#.#", "#.#", "#.#", "#.#");
            glyph('O', ".#.", "#.#", "#.#", "#.#", ".#.");
            glyph('P', "##.", "#.#", "##.", "#..", "#..");
            glyph('Q', ".#.", "#.#", "#.#", "##.", ".##");
            glyph('R', "##.", "#.#", "##.", "#.#", "#.#");
            glyph('S', ".##", "#..", ".#.", "..#", "##.");
            glyph('T', "###", ".#.", ".#.", ".#.", ".#.");
            glyph('U', "#.#", "#.#", "#.#", "#.#", "###");
            glyph('V', "#.#", "#.#", "#.#", "#.#", ".#.");
            glyph('W', "#.#", "#.#", "###", "###", "#.#");
            glyph('X', "#.#", "#.#", ".#.", "#.#", "#.#");
            glyph('Y', "#.#", "#.#", ".#.", ".#.", ".#.");
            glyph('Z', "###", "..#", ".#.", "#..", "###");
            glyph('0', "###", "#.#", "#.#", "#.#", "###");
            glyph('1', ".#.", "##.", ".#.", ".#.", "###");
            glyph('2', "##.", "..#", ".#.", "#..", "###");
            glyph('3', "##.", "..#", ".#.", "..#", "##.");
            glyph('4', "#.#", "#.#", "###", "..#", "..#");
            glyph('5', "###", "#..", "##.", "..#", "##.");
            glyph('6', ".##", "#..", "###", "#.#", "###");
            glyph('7', "###", "..#", ".#.", ".#.", ".#.");
            glyph('8', "###", "#.#", "###", "#.#", "###");
            glyph('9', "###", "#.#", "###", "..#", "##.");
            glyph(':', ".", "#", ".", "#", ".");
            glyph('!', "#", "#", "#", ".", "#");
            glyph('.', ".", ".", ".", ".", "#");
            glyph(' ', "..", "..", "..", "..", "..");
            glyph('-', "...", "...", "###", "...", "...");
            glyph('?', "##.", "..#", ".#.", "...", ".#.");
            glyph('&', ".#.", "#.#", ".#.", "#.#", ".##");
        }

        private static void glyph(char c, String... rows) {
            GLYPHS.put(c, rows);
        }

        static String[] glyph(char c) {
            return GLYPHS.getOrDefault(c, GLYPHS.get('?'));
        }
    }

    // ---------------------------------------------------------------- textures

    /** Registers a texture under {@code textures/block/<path>}, returning its full id. */
    static String texture(String path, Tex tex) {
        TEXTURES.put(path, tex);
        return NS + ":block/" + path;
    }

    static String animated(String path, Tex tex, int frametime) {
        ANIMATED.put(path, frametime);
        return texture(path, tex);
    }

    /** A material by its name under {@code decor/}. */
    static String mat(String name) {
        String path = "decor/" + name;
        if (!TEXTURES.containsKey(path)) {
            throw new IllegalArgumentException("No material " + name);
        }
        return NS + ":block/" + path;
    }

    // ---------------------------------------------------------------- models

    enum Face {
        DOWN, UP, NORTH, SOUTH, WEST, EAST;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * What a face of a box shows: a texture, with its region, or the region worked out from where the face sits.
     */
    record FaceDef(String texture, float[] uv, int rotation) {
    }

    /** One box of a model. */
    static final class Element {
        final float[] from;
        final float[] to;
        final Map<Face, FaceDef> faces = new EnumMap<>(Face.class);
        String axis;
        float angle;
        float[] origin;
        int light;
        boolean shade = true;
        /** Whether its faces take the tint a renderer colors them with, a car's paint. */
        boolean tinted;

        Element(float x0, float y0, float z0, float x1, float y1, float z1) {
            this.from = new float[] {x0, y0, z0};
            this.to = new float[] {x1, y1, z1};
        }

        /** Every face in a material, laid on by position. */
        Element all(String texture) {
            for (Face face : Face.values()) {
                faces.put(face, new FaceDef(texture, null, 0));
            }
            return this;
        }

        Element face(Face face, String texture) {
            faces.put(face, new FaceDef(texture, null, 0));
            return this;
        }

        Element face(Face face, String texture, float[] uv) {
            faces.put(face, new FaceDef(texture, uv, 0));
            return this;
        }

        Element face(Face face, String texture, float[] uv, int rotation) {
            faces.put(face, new FaceDef(texture, uv, rotation));
            return this;
        }

        /** The sides in one material, the top and bottom in another. */
        Element sides(String texture) {
            for (Face face : new Face[] {Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST}) {
                faces.put(face, new FaceDef(texture, null, 0));
            }
            return this;
        }

        Element top(String texture) {
            return face(Face.UP, texture);
        }

        Element bottom(String texture) {
            return face(Face.DOWN, texture);
        }

        /** Leaves only these faces. */
        Element only(Face... keep) {
            java.util.Set<Face> kept = java.util.EnumSet.noneOf(Face.class);
            kept.addAll(List.of(keep));
            faces.keySet().retainAll(kept);
            return this;
        }

        Element without(Face... drop) {
            for (Face face : drop) {
                faces.remove(face);
            }
            return this;
        }

        Element rotate(String axis, float angle, float ox, float oy, float oz) {
            this.axis = axis;
            this.angle = angle;
            this.origin = new float[] {ox, oy, oz};
            return this;
        }

        Element glow(int light) {
            this.light = light;
            return this;
        }

        Element flat() {
            this.shade = false;
            return this;
        }

        Element tint() {
            this.tinted = true;
            return this;
        }

        float width(Face face) {
            return switch (face) {
                case NORTH, SOUTH, UP, DOWN -> to[0] - from[0];
                case WEST, EAST -> to[2] - from[2];
            };
        }

        float height(Face face) {
            return switch (face) {
                case NORTH, SOUTH, WEST, EAST -> to[1] - from[1];
                case UP, DOWN -> to[2] - from[2];
            };
        }

        Element copy() {
            Element copy = new Element(from[0], from[1], from[2], to[0], to[1], to[2]);
            copy.faces.putAll(faces);
            copy.axis = axis;
            copy.angle = angle;
            copy.origin = origin == null ? null : origin.clone();
            copy.light = light;
            copy.shade = shade;
            copy.tinted = tinted;
            return copy;
        }

        Element moved(float dx, float dy, float dz) {
            Element copy = copy();
            for (int k = 0; k < 3; k++) {
                float d = k == 0 ? dx : k == 1 ? dy : dz;
                copy.from[k] += d;
                copy.to[k] += d;
            }
            if (copy.origin != null) {
                copy.origin[0] += dx;
                copy.origin[1] += dy;
                copy.origin[2] += dz;
            }
            return copy;
        }

        /** The same box mirrored across the middle of the block, west to east. */
        Element mirroredX() {
            Element copy = copy();
            copy.from[0] = 16 - to[0];
            copy.to[0] = 16 - from[0];
            FaceDef west = faces.get(Face.WEST);
            FaceDef east = faces.get(Face.EAST);
            copy.faces.remove(Face.WEST);
            copy.faces.remove(Face.EAST);
            if (west != null) {
                copy.faces.put(Face.EAST, west);
            }
            if (east != null) {
                copy.faces.put(Face.WEST, east);
            }
            if (copy.origin != null) {
                copy.origin[0] = 16 - copy.origin[0];
                copy.angle = axis != null && !axis.equals("x") ? -angle : angle;
            }
            return copy;
        }
    }

    /** A block model being authored, facing north. */
    static final class Model {
        final List<Element> elements = new ArrayList<>();
        /** Texture keys to ids, in order of first use. */
        final Map<String, String> textures = new LinkedHashMap<>();
        String particle;
        /** How it shows in an inventory: a scale for its size, and whether to lift it to the middle of the slot. */
        float guiScale = 0.625F;
        float guiLift;
        boolean ambientOcclusion = true;
        /** Whether faces on the block's edge are left out against a solid neighbor; never for what an entity draws. */
        boolean culling = true;

        Element box(float x0, float y0, float z0, float x1, float y1, float z1, String texture) {
            Element element = new Element(x0, y0, z0, x1, y1, z1).all(texture);
            elements.add(element);
            if (particle == null) {
                particle = texture;
            }
            return element;
        }

        Element box(float x0, float y0, float z0, float x1, float y1, float z1) {
            Element element = new Element(x0, y0, z0, x1, y1, z1);
            elements.add(element);
            return element;
        }

        Element add(Element element) {
            elements.add(element);
            return element;
        }

        Model particle(String texture) {
            this.particle = texture;
            return this;
        }

        /**
         * Paints a face's own detail at its exact size and puts it on the face, as a texture of its own.
         */
        Element paint(Element element, Face face, String path, Consumer<Tex> painter) {
            int w = (int) Math.ceil(element.width(face) - 0.001);
            int h = (int) Math.ceil(element.height(face) - 0.001);
            if (w > 16 || h > 16) {
                throw new IllegalArgumentException(path + " is painted over more than a block: " + w + "x" + h);
            }
            Tex tex = new Tex(Math.max(1, w), Math.max(1, h));
            painter.accept(tex);
            String id = texture(path, tex);
            return element.face(face, id, new float[] {0, 0, element.width(face), element.height(face)});
        }

        /** The same, animated: the painter is given the frame number for each frame. */
        Element paintAnimated(Element element, Face face, String path, int frames, int frametime, java.util.function.BiConsumer<Tex, Integer> painter) {
            int w = (int) Math.ceil(element.width(face) - 0.001);
            int h = (int) Math.ceil(element.height(face) - 0.001);
            if (w > 16 || h > 16) {
                throw new IllegalArgumentException(path + " is painted over more than a block: " + w + "x" + h);
            }
            Tex tex = new Tex(Math.max(1, w), Math.max(1, h), frames);
            for (int f = 0; f < frames; f++) {
                tex.frame(f);
                painter.accept(tex, f);
            }
            tex.frame(0);
            String id = animated(path, tex, frametime);
            return element.face(face, id, new float[] {0, 0, element.width(face), element.height(face)});
        }

        float[] bounds() {
            float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (Element e : elements) {
                for (int k = 0; k < 3; k++) {
                    b[k] = Math.min(b[k], e.from[k]);
                    b[k + 3] = Math.max(b[k + 3], e.to[k]);
                }
            }
            return b;
        }

        Model copy() {
            Model copy = new Model();
            for (Element e : elements) {
                copy.elements.add(e.copy());
            }
            copy.particle = particle;
            copy.guiScale = guiScale;
            copy.guiLift = guiLift;
            copy.ambientOcclusion = ambientOcclusion;
            copy.culling = culling;
            return copy;
        }

        /**
         * Cuts every box at each sixteenth along the given axes, so that each piece lies in one tile of its materials
         * and they run on unbroken across the cuts. Painted faces must already lie within one tile.
         */
        Model subdivided() {
            Model out = copy();
            out.elements.clear();
            for (Element e : elements) {
                List<Element> pieces = new ArrayList<>(List.of(e));
                if (e.axis == null || e.angle == 0) {
                    for (int axis : new int[] {0, 2}) {
                        List<Element> next = new ArrayList<>();
                        for (Element p : pieces) {
                            next.addAll(cut(p, axis));
                        }
                        pieces = next;
                    }
                }
                out.elements.addAll(pieces);
            }
            return out;
        }

        private static List<Element> cut(Element e, int axis) {
            List<Element> out = new ArrayList<>();
            float from = e.from[axis];
            float to = e.to[axis];
            float at = from;
            boolean painted = false;
            for (FaceDef def : e.faces.values()) {
                painted |= def.uv() != null;
            }
            if (painted) {
                return List.of(e);
            }
            while (at < to) {
                float next = Math.min(to, (float) (Math.floor(at / 16.0 + 1e-4) + 1) * 16);
                Element piece = e.copy();
                piece.from[axis] = at;
                piece.to[axis] = next;
                // inner faces of a cut would only be hidden inside the box
                if (at > from) {
                    piece.faces.remove(axis == 0 ? Face.WEST : Face.NORTH);
                }
                if (next < to) {
                    piece.faces.remove(axis == 0 ? Face.EAST : Face.SOUTH);
                }
                out.add(piece);
                at = next;
            }
            return out;
        }

        /** The same model shrunk about a point, for an inventory icon of something bigger than a block. */
        Model scaled(float k, float cx, float cy, float cz) {
            Model out = copy();
            float[] c = {cx, cy, cz};
            for (Element e : out.elements) {
                for (int i = 0; i < 3; i++) {
                    e.from[i] = c[i] + (e.from[i] - c[i]) * k;
                    e.to[i] = c[i] + (e.to[i] - c[i]) * k;
                    if (e.origin != null) {
                        e.origin[i] = c[i] + (e.origin[i] - c[i]) * k;
                    }
                }
            }
            return out;
        }

        String json() {
            textures.clear();
            List<String> lines = new ArrayList<>();
            for (Element e : elements) {
                // a box cut down to its inside, with no face left to show
                if (!e.faces.isEmpty()) {
                    lines.add(elementJson(e));
                }
            }
            StringBuilder out = new StringBuilder("{\n  \"parent\": \"minecraft:block/block\",\n");
            if (!ambientOcclusion) {
                out.append("  \"ambientocclusion\": false,\n");
            }
            out.append("  \"textures\": {\n");
            List<String> keys = new ArrayList<>();
            keys.add("    \"particle\": \"" + (particle != null ? particle : textures.values().stream().findFirst().orElse("minecraft:block/stone")) + "\"");
            for (Map.Entry<String, String> entry : textures.entrySet()) {
                keys.add("    \"" + entry.getKey() + "\": \"" + entry.getValue() + "\"");
            }
            out.append(String.join(",\n", keys)).append("\n  },\n");
            out.append(displayJson()).append(",\n");
            out.append("  \"elements\": [\n").append(String.join(",\n", lines)).append("\n  ]\n}\n");
            return out.toString();
        }

        private String key(String texture) {
            for (Map.Entry<String, String> entry : textures.entrySet()) {
                if (entry.getValue().equals(texture)) {
                    return entry.getKey();
                }
            }
            String key = "t" + textures.size();
            textures.put(key, texture);
            return key;
        }

        private String elementJson(Element e) {
            StringBuilder s = new StringBuilder("    {\n      \"from\": ").append(vec(e.from)).append(",\n      \"to\": ").append(vec(e.to));
            if (e.axis != null && e.angle != 0) {
                s.append(",\n      \"rotation\": { \"angle\": ").append(num(e.angle)).append(", \"axis\": \"").append(e.axis)
                        .append("\", \"origin\": ").append(vec(e.origin)).append(" }");
            }
            if (e.light > 0) {
                s.append(",\n      \"light_emission\": ").append(e.light);
            }
            if (!e.shade) {
                // lit as evenly as a top face, so a screen or a lamp shade reads the same from every side
                s.append(",\n      \"shade_direction_override\": \"up\"");
            }
            s.append(",\n      \"faces\": {\n");
            List<String> faces = new ArrayList<>();
            for (Map.Entry<Face, FaceDef> entry : e.faces.entrySet()) {
                Face face = entry.getKey();
                FaceDef def = entry.getValue();
                float[] uv = def.uv() != null ? def.uv() : autoUv(e, face);
                StringBuilder f = new StringBuilder("        \"").append(face.id()).append("\": { \"uv\": ").append(vec(uv))
                        .append(", \"texture\": \"#").append(key(def.texture())).append("\"");
                if (def.rotation() != 0) {
                    f.append(", \"rotation\": ").append(def.rotation());
                }
                if (e.tinted) {
                    f.append(", \"tintindex\": 0");
                }
                String cull = culling ? cullface(e, face) : null;
                if (cull != null) {
                    f.append(", \"cullface\": \"").append(cull).append("\"");
                }
                faces.add(f.append(" }").toString());
            }
            s.append(String.join(",\n", faces)).append("\n      }\n    }");
            return s.toString();
        }

        /** Where on its texture a face falls by where it sits in the block, as vanilla works it out. */
        private static float[] autoUv(Element e, Face face) {
            float x0 = e.from[0], y0 = e.from[1], z0 = e.from[2];
            float x1 = e.to[0], y1 = e.to[1], z1 = e.to[2];
            float[] uv = switch (face) {
                case DOWN -> new float[] {x0, 16 - z1, x1, 16 - z0};
                case UP -> new float[] {x0, z0, x1, z1};
                case NORTH -> new float[] {16 - x1, 16 - y1, 16 - x0, 16 - y0};
                case SOUTH -> new float[] {x0, 16 - y1, x1, 16 - y0};
                case WEST -> new float[] {z0, 16 - y1, z1, 16 - y0};
                case EAST -> new float[] {16 - z1, 16 - y1, 16 - z0, 16 - y0};
            };
            // keep within one tile wherever the box sits, beyond the block too
            for (int axis = 0; axis < 2; axis++) {
                float lo = Math.min(uv[axis], uv[axis + 2]);
                float hi = Math.max(uv[axis], uv[axis + 2]);
                float shift = (float) -Math.floor(lo / 16.0) * 16;
                if (hi - lo > 16) {
                    lo = 0;
                    hi = 16;
                    shift = 0;
                }
                if (hi + shift > 16) {
                    shift -= 16;
                    if (lo + shift < 0) {
                        // straddles a tile edge: squeeze it into one tile
                        float span = hi - lo;
                        lo = 0;
                        hi = span;
                        shift = 0;
                    }
                }
                uv[axis] = lo + shift;
                uv[axis + 2] = hi + shift;
            }
            return uv;
        }

        private static String cullface(Element e, Face face) {
            if (e.axis != null && e.angle != 0) {
                return null;
            }
            return switch (face) {
                case DOWN -> e.from[1] == 0 ? "down" : null;
                case UP -> e.to[1] == 16 ? "up" : null;
                case NORTH -> e.from[2] == 0 ? "north" : null;
                case SOUTH -> e.to[2] == 16 ? "south" : null;
                case WEST -> e.from[0] == 0 ? "west" : null;
                case EAST -> e.to[0] == 16 ? "east" : null;
            };
        }

        private String displayJson() {
            float[] b = bounds();
            float size = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
            // a small piece fills more of its slot than a whole block would, a tall one less
            float scale = Math.min(0.85F, guiScale * Math.min(1.5F, 16.0F / Math.max(6.0F, size)));
            float lift = guiLift != 0 ? guiLift : (8 - (b[1] + b[4]) / 2) * scale;
            float ground = Math.min(0.5F, 0.25F * 16.0F / Math.max(6.0F, size));
            return """
                      "display": {
                        "gui": { "rotation": [30, 225, 0], "translation": [0, %s, 0], "scale": [%s, %s, %s] },
                        "ground": { "rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [%s, %s, %s] },
                        "fixed": { "rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [%s, %s, %s] }
                      }""".formatted(num(lift), num(scale), num(scale), num(scale), num(ground), num(ground), num(ground),
                    num(scale * 0.8F), num(scale * 0.8F), num(scale * 0.8F));
        }
    }

    /**
     * Cuts a model two blocks tall into its lower and upper halves, for a block in two parts like a door: boxes across
     * the middle are split, and the upper half moved down into its own block.
     */
    static Model[] split(Model whole) {
        Model lower = new Model();
        Model upper = new Model();
        lower.particle = upper.particle = whole.particle;
        for (Element e : whole.elements) {
            if (e.axis != null && e.angle != 0) {
                (e.from[1] >= 16 || (e.from[1] + e.to[1]) / 2 >= 16 ? upper : lower).elements.add(e.from[1] >= 16 || (e.from[1] + e.to[1]) / 2 >= 16 ? e.moved(0, -16, 0) : e.copy());
                continue;
            }
            if (e.to[1] <= 16) {
                lower.elements.add(e.copy());
            } else if (e.from[1] >= 16) {
                upper.elements.add(e.moved(0, -16, 0));
            } else {
                Element bottom = e.copy();
                bottom.to[1] = 16;
                Element top = e.moved(0, -16, 0);
                top.from[1] = 0;
                splitFaces(e, bottom, top);
                bottom.faces.remove(Face.UP);
                top.faces.remove(Face.DOWN);
                lower.elements.add(bottom);
                upper.elements.add(top);
            }
        }
        return new Model[] {lower, upper};
    }

    /** Side faces painted whole are cut where the box is: the lower part keeps the bottom of the painting. */
    private static void splitFaces(Element whole, Element bottom, Element top) {
        for (Face face : new Face[] {Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST}) {
            FaceDef def = whole.faces.get(face);
            if (def == null || def.uv() == null) {
                continue;
            }
            float h = whole.to[1] - whole.from[1];
            float cut = 16 - whole.from[1];
            float[] uv = def.uv();
            float v0 = uv[1], v1 = uv[3];
            float vCut = v0 + (v1 - v0) * (h - cut) / h;
            top.faces.put(face, new FaceDef(def.texture(), new float[] {uv[0], v0, uv[2], vCut}, def.rotation()));
            bottom.faces.put(face, new FaceDef(def.texture(), new float[] {uv[0], vCut, uv[2], v1}, def.rotation()));
        }
    }

    static String vec(float[] v) {
        StringBuilder s = new StringBuilder("[");
        for (int k = 0; k < v.length; k++) {
            if (k > 0) {
                s.append(", ");
            }
            s.append(num(v[k]));
        }
        return s.append("]").toString();
    }

    static String num(double value) {
        return AssetGen.num(value);
    }

    // ---------------------------------------------------------------- pieces

    /**
     * One decoration: its block id, the properties its blockstate varies by besides facing and era, and how to build
     * the model for each combination.
     */
    abstract static class Piece {
        final String id;
        /** Extra properties besides facing and era, each with its values. */
        final Map<String, String[]> properties = new LinkedHashMap<>();
        /** Whether only the lower half drops, for a piece two blocks tall. */
        boolean twoTall;
        /** Whether it stands against a wall, facing out from it. */
        boolean onWall;
        /** Whether it is a block with an item of its own, to be put down and broken. */
        boolean hasItem = true;

        Piece(String id) {
            this.id = id;
        }

        Piece property(String name, String... values) {
            properties.put(name, values);
            return this;
        }

        /** Builds and registers every model this piece uses. */
        abstract void build();

        /** The model for a combination of property values, by path under {@code models/block/}. */
        abstract String model(String era, Map<String, String> values);

        /** The model its item shows. */
        String itemModel() {
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, String[]> entry : properties.entrySet()) {
                values.put(entry.getKey(), entry.getValue()[0]);
            }
            return model("present", values);
        }

        void put(String path, Model model) {
            MODELS.put(id + "/" + path, model);
        }

        String blockstate() {
            List<String> variants = new ArrayList<>();
            List<Map<String, String>> combos = new ArrayList<>();
            combos.add(new LinkedHashMap<>());
            for (Map.Entry<String, String[]> entry : properties.entrySet()) {
                List<Map<String, String>> next = new ArrayList<>();
                for (Map<String, String> combo : combos) {
                    for (String value : entry.getValue()) {
                        Map<String, String> more = new LinkedHashMap<>(combo);
                        more.put(entry.getKey(), value);
                        next.add(more);
                    }
                }
                combos = next;
            }
            for (String era : ERAS) {
                for (int f = 0; f < FACINGS.length; f++) {
                    for (Map<String, String> combo : combos) {
                        StringBuilder key = new StringBuilder("era=").append(era).append(",facing=").append(FACINGS[f]);
                        for (Map.Entry<String, String> entry : combo.entrySet()) {
                            key.append(",").append(entry.getKey()).append("=").append(entry.getValue());
                        }
                        String model = NS + ":block/" + id + "/" + model(era, combo);
                        String y = f == 0 ? "" : ", \"y\": " + f * 90;
                        variants.add("    \"" + key + "\": { \"model\": \"" + model + "\"" + y + " }");
                    }
                }
            }
            return "{\n  \"variants\": {\n" + String.join(",\n", variants) + "\n  }\n}\n";
        }

        String itemDefinition() {
            return """
                    {
                      "model": {
                        "type": "minecraft:model",
                        "model": "%s:block/%s/%s"
                      }
                    }
                    """.formatted(NS, id, itemModel());
        }

        String lootTable() {
            String condition = twoTall ? """
                    ,
                              "conditions": [
                                {
                                  "condition": "minecraft:block_state_property",
                                  "block": "%s:%s",
                                  "properties": { "half": "lower" }
                                }
                              ]""".formatted(NS, id) : "";
            return """
                    {
                      "type": "minecraft:block",
                      "pools": [
                        {
                          "bonus_rolls": 0.0,
                          "conditions": [ { "condition": "minecraft:survives_explosion" } ],
                          "entries": [
                            {
                              "type": "minecraft:item",
                              "name": "%s:%s"%s
                            }
                          ],
                          "rolls": 1.0
                        }
                      ],
                      "random_sequence": "%s:blocks/%s"
                    }
                    """.formatted(NS, id, condition, NS, id);
        }
    }

    /** A piece with one model per era, and maybe a lit or switched-on version of each. */
    abstract static class EraPiece extends Piece {
        final boolean switched;

        EraPiece(String id, boolean switched) {
            super(id);
            this.switched = switched;
            if (switched) {
                property("lit", "false", "true");
            }
        }

        /** The model of the piece in an era, switched on or not. */
        abstract Model model(int era, boolean on);

        @Override
        void build() {
            for (int era = 0; era < ERAS.length; era++) {
                put(ERAS[era], model(era, false));
                if (switched) {
                    put(ERAS[era] + "_on", model(era, true));
                }
            }
        }

        @Override
        String model(String era, Map<String, String> values) {
            return era + ("true".equals(values.get("lit")) ? "_on" : "");
        }

        @Override
        String itemModel() {
            return "present" + (switched ? "_on" : "");
        }
    }

    // ---------------------------------------------------------------- shared pieces of art

    /** The path a piece's own painted detail goes under. */
    static String detail(String piece, int era, String name) {
        return "decor/" + piece + "/" + ERAS[era] + "_" + name;
    }
}
