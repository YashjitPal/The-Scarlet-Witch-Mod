import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Generates the crown art from vector outlines: high-detail sprites worn on the head (extruded into 3D by vanilla's
 * item model generator), 16x16 inventory icons, and the worn item models.
 *
 * <p>Shapes are authored in "head pixels": coordinates on the front of an 8x8x8 player head, origin at its center,
 * +y up. On a typical skin the brows sit around y = 0.5 to 1, the eyes at y = -1 to 0 and the cheekbones near y = -2.
 *
 * <p>Run from the repository root: {@code java tools/assetgen/AssetGen.java}. Writes into
 * {@code common/src/main/resources/assets/scarlet} and zoomed previews into {@code build/assetgen}.
 */
public final class AssetGen {

    static final int SHADOW = 0x1C0307;

    /**
     * Texels per head pixel on the worn crown: the costume's density, so the crown's pixels match the suit's.
     */
    static final int WORN_DENSITY = CostumeArt.DENSITY;

    public static void main(String[] args) throws IOException {
        Path assets = Path.of(args.length > 0 ? args[0] : "common/src/main/resources/assets/scarlet");
        Path preview = Path.of(args.length > 1 ? args[1] : "build/assetgen");
        Files.createDirectories(preview);

        List<BufferedImage> sheet = new ArrayList<>();
        for (Crown crown : List.of(Crown.witch(), Crown.warlock())) {
            BufferedImage worn = crown.render((int) Math.round(crown.regionSize * WORN_DENSITY), false);
            BufferedImage icon = crown.render(16, true);
            writePng(worn, assets.resolve("textures/item/" + crown.name + "_worn.png"));
            writePng(icon, assets.resolve("textures/item/" + crown.name + ".png"));
            writeText(crown.wornModelJson(), assets.resolve("models/item/" + crown.name + "_worn.json"));
            writeText(crown.sidesModelJson(), assets.resolve("models/item/" + crown.name + "_worn_sides.json"));
            writeText(crown.iconModelJson(), assets.resolve("models/item/" + crown.name + ".json"));
            writeText(crown.itemDefinitionJson(), assets.resolve("items/" + crown.name + ".json"));
            sheet.add(worn);
            sheet.add(icon);
        }
        writePng(zoomSheet(sheet, 320), preview.resolve("crowns.png"));

        Path costumes = assets.resolve("textures/entity/costume");
        for (CostumeArt.Look look : List.of(CostumeArt.Look.ofWitch(), CostumeArt.Look.ofWarlock())) {
            String name = look.warlock() ? "warlock" : "witch";
            BufferedImage wide = CostumeArt.costume(look, false);
            BufferedImage slim = CostumeArt.costume(look, true);
            BufferedImage cape = CostumeArt.cape(look);
            writePng(wide, costumes.resolve(name + ".png"));
            writePng(slim, costumes.resolve(name + "_slim.png"));
            writePng(cape, costumes.resolve(name + "_cape.png"));
            writePng(CostumeArt.preview(wide, cape, false, 6), preview.resolve("costume_" + name + ".png"));
        }
        writePng(CostumeArt.costumeReveal(false), costumes.resolve("reveal.png"));
        writePng(CostumeArt.costumeReveal(true), costumes.resolve("reveal_slim.png"));
        writePng(CostumeArt.capeReveal(), costumes.resolve("cape_reveal.png"));

        Path sprites = assets.resolve("textures/gui/sprites");
        writePng(HudArt.glow(), sprites.resolve("hud/glow.png"));
        writePng(HudArt.slot(), sprites.resolve("wheel/slot.png"));
        writePng(HudArt.lock(), sprites.resolve("wheel/lock.png"));
        List<BufferedImage> icons = new ArrayList<>();
        for (var icon : HudArt.spellIcons().entrySet()) {
            writePng(icon.getValue(), sprites.resolve("spell/" + icon.getKey() + ".png"));
            icons.add(icon.getValue());
        }
        writePng(zoomSheet(icons, 128), preview.resolve("spell_icons.png"));

        Path entity = assets.resolve("textures/entity");
        PeopleArt.Outfit[][] residents = PeopleArt.residentWardrobes();
        PeopleArt.Outfit[][] outfits = PeopleArt.playerWardrobes();
        for (int era = 0; era < PeopleArt.ERAS.length; era++) {
            String name = PeopleArt.ERAS[era];
            PeopleArt.Person[] people = PeopleArt.people(era);
            List<BufferedImage> row = new ArrayList<>();
            for (int i = 0; i < people.length; i++) {
                BufferedImage skin = PeopleArt.resident(people[i], residents[era][i]);
                writePng(skin, entity.resolve("resident/" + name + "_" + i + ".png"));
                row.add(PeopleArt.preview(skin, people[i].slim(), 3));
            }
            for (int i = 0; i < outfits[era].length; i++) {
                BufferedImage wide = PeopleArt.outfit(outfits[era][i], false);
                writePng(wide, entity.resolve("outfit/" + name + "_" + i + ".png"));
                writePng(PeopleArt.outfit(outfits[era][i], true), entity.resolve("outfit/" + name + "_" + i + "_slim.png"));
                row.add(PeopleArt.preview(wide, false, 3));
            }
            writePng(strip(row), preview.resolve("people_" + name + ".png"));
        }
        System.out.println("Crown, costume and people assets written to " + assets.toAbsolutePath());
    }

    // ---------------------------------------------------------------- crowns

    record Pt(double x, double y) {
    }

    record Tints(int base, int light, int specular, int shade, int groove) {
    }

    /**
     * Pieces that wrap the temples on the sides of the head, continuing the face-framing strips around the corner. Each
     * row is a vertical band: bottom, top, and how far back from the front corner it reaches (all in head pixels).
     */
    record SideWrap(double[][] bands) {
    }

    /**
     * A symmetric crown: the right half of its outline (from the bottom center, around the outside, to the top center),
     * engraved grooves on the right half, and the square region of the head it is drawn over.
     */
    static final class Crown {
        final String name;
        final List<Pt> outline;
        final List<List<Pt>> grooves;
        final double regionSize;
        final double regionCenterY;
        final Tints tints;
        final SideWrap sides;

        /**
         * {@code tipScale} squashes everything above the top of the head and {@code stripScale} everything below the
         * eyes, so proportions can be tuned without redrawing the outline.
         */
        Crown(String name, List<Pt> rightHalf, List<List<Pt>> rightGrooves, double tipScale, double stripScale,
              double regionSize, double regionCenterY, Tints tints, SideWrap sides) {
            this.name = name;
            this.outline = mirrorOutline(rightHalf.stream().map(p -> proportion(p, tipScale, stripScale)).toList());
            this.grooves = new ArrayList<>();
            for (List<Pt> groove : rightGrooves) {
                List<Pt> smooth = catmullRom(groove.stream().map(p -> proportion(p, tipScale, stripScale)).toList(), 12);
                this.grooves.add(smooth);
                this.grooves.add(smooth.stream().map(p -> new Pt(-p.x(), p.y())).toList());
            }
            this.regionSize = regionSize;
            this.regionCenterY = regionCenterY;
            this.tints = tints;
            this.sides = sides;
        }

        private static Pt proportion(Pt p, double tipScale, double stripScale) {
            if (p.y() > 4) {
                return new Pt(p.x(), 4 + (p.y() - 4) * tipScale);
            }
            if (p.y() < -1) {
                return new Pt(p.x(), -1 + (p.y() + 1) * stripScale);
            }
            return p;
        }

        /**
         * The WandaVision tiara: a downward chevron at the center of the forehead, swept wings with flame-like grooves,
         * two tall points at the temples, and strips framing the face down to the cheekbones.
         */
        static Crown witch() {
            List<Pt> right = List.of(
                    new Pt(0.0, 1.0),    // widow's peak, just above the brows
                    new Pt(0.45, 1.55),
                    new Pt(1.0, 1.95),   // the lower edge arches over the brow
                    new Pt(1.7, 2.2),
                    new Pt(2.4, 2.25),
                    new Pt(2.95, 2.0),
                    new Pt(3.25, 1.5),   // and turns down into the temple strip
                    new Pt(3.36, 0.6),
                    new Pt(3.4, -0.7),
                    new Pt(3.48, -1.8),
                    new Pt(3.82, -2.8),  // strip point at the cheekbone
                    new Pt(4.15, -1.8),
                    new Pt(4.2, 0.0),
                    new Pt(4.22, 2.0),
                    new Pt(4.3, 3.6),
                    new Pt(4.42, 4.9),
                    new Pt(4.45, 5.9),
                    new Pt(4.28, 6.6),
                    new Pt(4.0, 7.15),   // tall point above the temple
                    new Pt(3.7, 6.45),   // the upper edge falls steeply, stepping where each strand ends
                    new Pt(3.35, 5.75),
                    new Pt(2.95, 5.2),
                    new Pt(2.75, 5.05),
                    new Pt(2.62, 4.8),
                    new Pt(2.3, 4.55),
                    new Pt(1.95, 4.25),
                    new Pt(1.75, 4.1),
                    new Pt(1.62, 3.86),
                    new Pt(1.25, 3.65),
                    new Pt(0.8, 3.38),
                    new Pt(0.38, 3.0),
                    new Pt(0.0, 2.6)     // notch at the top center
            );
            // swirl strands: each groove sweeps out from the center and ends where its strand steps down
            List<List<Pt>> grooves = List.of(
                    List.of(new Pt(0.25, 2.45), new Pt(0.9, 3.05), new Pt(1.3, 3.45), new Pt(1.62, 3.86)),
                    List.of(new Pt(0.8, 2.35), new Pt(1.5, 2.9), new Pt(2.1, 3.75), new Pt(2.62, 4.8)),
                    List.of(new Pt(1.9, 2.55), new Pt(2.6, 3.2), new Pt(3.15, 4.2), new Pt(3.55, 5.3), new Pt(3.85, 6.5)),
                    List.of(new Pt(3.86, 4.6), new Pt(3.84, 2.0), new Pt(3.8, -0.4), new Pt(3.74, -1.9))
            );
            SideWrap sides = new SideWrap(new double[][] {
                    {-2.1, -1.65, 0.25}, {-1.65, -1.0, 0.5}, {-1.0, 0.2, 0.8}, {0.2, 1.6, 1.1}, {1.6, 3.3, 1.55},
                    {3.3, 4.6, 0.95}, {4.6, 5.3, 0.45}
            });
            return new Crown("witch_tiara", right, grooves, 0.72, 0.78, 10.0, 1.8,
                    new Tints(0x941A2B, 0xBE3344, 0xEC7C87, 0x5A0E1A, 0x24060B), sides);
        }

        /**
         * The Warlock's counterpart in the same design language: an upward blade at the center, horns that curve outward,
         * an angular brow band and strips that run down to the jaw.
         */
        static Crown warlock() {
            List<Pt> right = List.of(
                    new Pt(0.0, 0.65),   // sharper point between the brows
                    new Pt(0.5, 1.35),
                    new Pt(1.3, 1.9),
                    new Pt(2.5, 2.0),
                    new Pt(3.15, 1.55),
                    new Pt(3.35, 0.5),
                    new Pt(3.38, -1.3),
                    new Pt(3.45, -2.75),
                    new Pt(3.85, -3.6),  // strip point at the jaw
                    new Pt(4.27, -2.5),
                    new Pt(4.3, 0.0),
                    new Pt(4.32, 2.3),
                    new Pt(4.4, 3.9),
                    new Pt(4.75, 4.95),  // horn sweeps outward
                    new Pt(5.1, 5.85),
                    new Pt(5.35, 6.75),
                    new Pt(5.4, 7.5),    // horn tip
                    new Pt(4.75, 6.45),
                    new Pt(4.25, 5.55),
                    new Pt(3.7, 4.75),
                    new Pt(3.05, 4.1),
                    new Pt(2.3, 3.7),
                    new Pt(1.5, 3.45),
                    new Pt(0.85, 3.5),
                    new Pt(0.45, 4.2),
                    new Pt(0.0, 5.35)    // central blade
            );
            List<List<Pt>> grooves = List.of(
                    List.of(new Pt(0.45, 2.25), new Pt(1.35, 2.65), new Pt(2.45, 2.75), new Pt(3.25, 3.25), new Pt(3.95, 4.3), new Pt(4.55, 5.4), new Pt(4.95, 6.4)),
                    List.of(new Pt(3.86, 3.0), new Pt(3.85, 0.5), new Pt(3.84, -1.3), new Pt(3.82, -2.8)),
                    List.of(new Pt(0.0, 1.45), new Pt(0.0, 2.9), new Pt(0.0, 4.3))
            );
            SideWrap sides = new SideWrap(new double[][] {
                    {-2.9, -2.4, 0.25}, {-2.4, -1.4, 0.55}, {-1.4, 0.2, 0.85}, {0.2, 1.7, 1.2}, {1.7, 3.6, 1.7},
                    {3.6, 5.0, 1.15}, {5.0, 5.9, 0.6}
            });
            return new Crown("warlock_crown", right, grooves, 0.9, 0.77, 11.5, 1.8,
                    new Tints(0x6E0F1E, 0x9C1A2D, 0xD0505C, 0x3A0812, 0x14030A), sides);
        }

        /**
         * Worn sprites cover the region the head transform expects; icons crop tightly around the crown. Coverage is
         * supersampled so thin parts survive at low resolution, and grooves are traced as continuous one-texel lines.
         */
        BufferedImage render(int size, boolean icon) {
            double extent = icon ? iconExtent() : regionSize;
            double centerY = icon ? iconCenterY() : regionCenterY;
            double texel = extent / size;
            double left = -extent / 2;
            double top = centerY + extent / 2;
            int samples = 4;
            boolean[][] solid = new boolean[size][size];
            for (int v = 0; v < size; v++) {
                for (int u = 0; u < size; u++) {
                    int covered = 0;
                    for (int sy = 0; sy < samples; sy++) {
                        for (int sx = 0; sx < samples; sx++) {
                            Pt p = new Pt(left + (u + (sx + 0.5) / samples) * texel, top - (v + (sy + 0.5) / samples) * texel);
                            if (inside(outline, p)) {
                                covered++;
                            }
                        }
                    }
                    solid[v][u] = covered * 2 >= samples * samples;
                }
            }
            // grooves only cut where the metal is thick enough to keep color on both sides of them
            boolean[][] groove = new boolean[size][size];
            List<List<Pt>> traced = icon ? grooves.subList(0, 2) : grooves;
            for (List<Pt> line : traced) {
                for (Pt p : resample(line, texel / 4)) {
                    int u = (int) Math.floor((p.x() - left) / texel);
                    int v = (int) Math.floor((top - p.y()) / texel);
                    if (interior(solid, u, v, size)) {
                        groove[v][u] = true;
                    }
                }
            }
            int grooveColor = icon ? tints.groove() : tints.shade();
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            for (int v = 0; v < size; v++) {
                for (int u = 0; u < size; u++) {
                    if (groove[v][u]) {
                        set(image, u, v, grooveColor);
                    } else if (solid[v][u]) {
                        set(image, u, v, shade(solid, groove, u, v, size));
                    } else if (icon && touches(solid, u, v, size)) {
                        set(image, u, v, SHADOW);
                    }
                }
            }
            return image;
        }

        private int shade(boolean[][] solid, boolean[][] groove, int u, int v, int size) {
            boolean openAbove = !metal(solid, groove, u, v - 1, size);
            boolean openLeft = !metal(solid, groove, u - 1, v, size);
            boolean openBelow = !metal(solid, groove, u, v + 1, size);
            boolean grooveAbove = v > 0 && groove[v - 1][u];
            if (openAbove && openLeft && !solidAt(solid, u, v - 1, size)) {
                return tints.specular();
            }
            if (openAbove || openLeft || grooveAbove) {
                return tints.light();
            }
            if (openBelow) {
                return tints.shade();
            }
            return tints.base();
        }

        private static boolean metal(boolean[][] solid, boolean[][] groove, int u, int v, int size) {
            return solidAt(solid, u, v, size) && !groove[v][u];
        }

        private static boolean solidAt(boolean[][] solid, int u, int v, int size) {
            return u >= 0 && v >= 0 && u < size && v < size && solid[v][u];
        }

        private static boolean interior(boolean[][] solid, int u, int v, int size) {
            for (int dv = -1; dv <= 1; dv++) {
                for (int du = -1; du <= 1; du++) {
                    if (!solidAt(solid, u + du, v + dv, size)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private static boolean touches(boolean[][] solid, int u, int v, int size) {
            return solidAt(solid, u + 1, v, size) || solidAt(solid, u - 1, v, size)
                    || solidAt(solid, u, v + 1, size) || solidAt(solid, u, v - 1, size);
        }

        private double iconExtent() {
            double width = 0;
            double minY = Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (Pt p : outline) {
                width = Math.max(width, Math.abs(p.x()) * 2);
                minY = Math.min(minY, p.y());
                maxY = Math.max(maxY, p.y());
            }
            return Math.max(width, maxY - minY) * 16 / 14.5;
        }

        private double iconCenterY() {
            double minY = Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (Pt p : outline) {
                minY = Math.min(minY, p.y());
                maxY = Math.max(maxY, p.y());
            }
            return (minY + maxY) / 2;
        }

        /**
         * The worn model: vanilla extrudes the sprite into a thin 3D piece; the head transform lays it over the face.
         * In the head display context one model unit is 0.625 head pixels at scale 1.
         */
        String wornModelJson() {
            double scale = regionSize / 10.0;
            double thickness = 0.32;
            double z = -(4.0 + thickness / 2 + 0.03);
            String head = String.format(Locale.ROOT,
                    "{ \"rotation\": [0, 180, 0], \"translation\": [0, %s, %s], \"scale\": [%s, %s, %s] }",
                    num(regionCenterY / 0.625), num(z / 0.625), num(scale), num(scale), num(thickness / 0.625));
            return """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": {
                        "layer0": "scarlet:item/%s_worn"
                      },
                      "display": {
                        "head": %s,
                        "thirdperson_righthand": { "rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": [0.6, 0.6, 0.6] },
                        "thirdperson_lefthand": { "rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": [0.6, 0.6, 0.6] },
                        "firstperson_righthand": { "rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.75, 0.75, 0.75] },
                        "firstperson_lefthand": { "rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.75, 0.75, 0.75] }
                      }
                    }
                    """.formatted(name, head);
        }

        /**
         * Thin plates on both sides of the head, textured with the strip area of the worn sprite. In the head display
         * context with an identity transform, model units are {@code 8 + 1.6 * headPixels}.
         */
        String sidesModelJson() {
            double stripX = 3.8;
            double u0 = (stripX - 0.3 + regionSize / 2) / regionSize * 16;
            double u1 = (stripX + 0.3 + regionSize / 2) / regionSize * 16;
            double top = regionCenterY + regionSize / 2;
            double v0 = (top - 2.0) / regionSize * 16;
            double v1 = (top + 1.0) / regionSize * 16;
            String uv = "[" + num(u0) + ", " + num(v0) + ", " + num(u1) + ", " + num(v1) + "]";
            List<String> elements = new ArrayList<>();
            for (int side : new int[] {1, -1}) {
                for (double[] band : sides.bands()) {
                    double inner = side * 4.02;
                    double outer = side * 4.3;
                    double front = -4.35;
                    double back = -4.0 + band[2];
                    elements.add(element(Math.min(inner, outer), band[0], front, Math.max(inner, outer), band[1], back, uv));
                }
            }
            return """
                    {
                      "textures": {
                        "metal": "scarlet:item/%s_worn",
                        "particle": "scarlet:item/%s_worn"
                      },
                      "display": {
                        "head": { "rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1] }
                      },
                      "elements": [
                    %s
                      ]
                    }
                    """.formatted(name, name, String.join(",\n", elements));
        }

        private static String element(double x0, double y0, double z0, double x1, double y1, double z1, String uv) {
            String face = "{ \"uv\": " + uv + ", \"texture\": \"#metal\" }";
            return "    { \"from\": " + vec(x0, y0, z0) + ", \"to\": " + vec(x1, y1, z1) + ", \"faces\": { "
                    + "\"north\": " + face + ", \"south\": " + face + ", \"east\": " + face + ", \"west\": " + face
                    + ", \"up\": " + face + ", \"down\": " + face + " } }";
        }

        private static String vec(double x, double y, double z) {
            return "[" + num(8 + x * 1.6) + ", " + num(8 + y * 1.6) + ", " + num(8 + z * 1.6) + "]";
        }

        String iconModelJson() {
            return """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": {
                        "layer0": "scarlet:item/%s"
                      }
                    }
                    """.formatted(name);
        }

        /**
         * Flat icon in inventories, the full wrap when worn, and the face piece alone everywhere else.
         */
        String itemDefinitionJson() {
            return """
                    {
                      "model": {
                        "type": "minecraft:select",
                        "property": "minecraft:display_context",
                        "cases": [
                          {
                            "when": "gui",
                            "model": { "type": "minecraft:model", "model": "scarlet:item/%1$s" }
                          },
                          {
                            "when": "head",
                            "model": {
                              "type": "minecraft:composite",
                              "models": [
                                { "type": "minecraft:model", "model": "scarlet:item/%1$s_worn" },
                                { "type": "minecraft:model", "model": "scarlet:item/%1$s_worn_sides" }
                              ]
                            }
                          }
                        ],
                        "fallback": { "type": "minecraft:model", "model": "scarlet:item/%1$s_worn" }
                      }
                    }
                    """.formatted(name);
        }
    }

    // ---------------------------------------------------------------- geometry

    static List<Pt> mirrorOutline(List<Pt> rightHalf) {
        List<Pt> full = new ArrayList<>(rightHalf);
        for (int i = rightHalf.size() - 2; i >= 1; i--) {
            Pt p = rightHalf.get(i);
            full.add(new Pt(-p.x(), p.y()));
        }
        return full;
    }

    static boolean inside(List<Pt> polygon, Pt p) {
        boolean in = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            Pt a = polygon.get(i);
            Pt b = polygon.get(j);
            if ((a.y() > p.y()) != (b.y() > p.y())
                    && p.x() < (b.x() - a.x()) * (p.y() - a.y()) / (b.y() - a.y()) + a.x()) {
                in = !in;
            }
        }
        return in;
    }

    static List<Pt> catmullRom(List<Pt> points, int steps) {
        List<Pt> out = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            Pt p0 = points.get(Math.max(i - 1, 0));
            Pt p1 = points.get(i);
            Pt p2 = points.get(i + 1);
            Pt p3 = points.get(Math.min(i + 2, points.size() - 1));
            for (int s = 0; s < steps; s++) {
                double t = s / (double) steps;
                double t2 = t * t;
                double t3 = t2 * t;
                double x = 0.5 * (2 * p1.x() + (-p0.x() + p2.x()) * t + (2 * p0.x() - 5 * p1.x() + 4 * p2.x() - p3.x()) * t2 + (-p0.x() + 3 * p1.x() - 3 * p2.x() + p3.x()) * t3);
                double y = 0.5 * (2 * p1.y() + (-p0.y() + p2.y()) * t + (2 * p0.y() - 5 * p1.y() + 4 * p2.y() - p3.y()) * t2 + (-p0.y() + 3 * p1.y() - 3 * p2.y() + p3.y()) * t3);
                out.add(new Pt(x, y));
            }
        }
        out.add(points.getLast());
        return out;
    }

    /**
     * Points along a polyline no more than {@code step} apart.
     */
    static List<Pt> resample(List<Pt> line, double step) {
        List<Pt> out = new ArrayList<>();
        for (int i = 0; i < line.size() - 1; i++) {
            Pt a = line.get(i);
            Pt b = line.get(i + 1);
            int n = Math.max(1, (int) Math.ceil(Math.hypot(b.x() - a.x(), b.y() - a.y()) / step));
            for (int s = 0; s < n; s++) {
                double t = s / (double) n;
                out.add(new Pt(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t));
            }
        }
        out.add(line.getLast());
        return out;
    }

    // ---------------------------------------------------------------- output

    static void set(BufferedImage image, int x, int y, int rgb) {
        image.setRGB(x, y, 0xFF000000 | rgb);
    }

    /**
     * Each image drawn into a square cell of {@code cell} pixels with nearest-neighbor scaling, on a checkerboard.
     */
    static BufferedImage zoomSheet(List<BufferedImage> images, int cell) {
        int pad = 12;
        BufferedImage sheet = new BufferedImage(pad + images.size() * (cell + pad), cell + pad * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int y = 0; y < sheet.getHeight(); y += 10) {
            for (int x = 0; x < sheet.getWidth(); x += 10) {
                g.setColor(((x + y) / 10) % 2 == 0 ? new Color(0xD8D2CC) : new Color(0xC4BDB6));
                g.fillRect(x, y, 10, 10);
            }
        }
        int x = pad;
        for (BufferedImage image : images) {
            g.drawImage(image, x, pad, cell, cell, null);
            x += cell + pad;
        }
        g.dispose();
        return sheet;
    }

    /** Images side by side, at their own size. */
    static BufferedImage strip(List<BufferedImage> images) {
        int width = images.stream().mapToInt(BufferedImage::getWidth).sum();
        int height = images.stream().mapToInt(BufferedImage::getHeight).max().orElse(1);
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        int x = 0;
        for (BufferedImage image : images) {
            g.drawImage(image, x, 0, null);
            x += image.getWidth();
        }
        g.dispose();
        return sheet;
    }

    static void writePng(BufferedImage image, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
    }

    static void writeText(String text, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }

    static String num(double value) {
        String s = String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.equals("-0") ? "0" : s;
    }
}
