import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Billy's finale costume: a wrought cobalt circlet, wine-red mantle and midnight panelwork. */
final class WiccanArt {
    private static final int INK = 0x141925, MIDNIGHT = 0x202B40, TWILL = 0x33435A, SATIN = 0x46596E;
    private static final int OXBLOOD = 0x4D1026, RUBY = 0x9F203E, FOLD = 0xCB3953, PEWTER = 0xA0ACBD;

    public static void main(String[] args) throws IOException {
        generate(Path.of("common/src/main/resources/assets/scarlet"), Path.of("build/assetgen"));
    }

    static void generate(Path assets, Path preview) throws IOException {
        AssetGen.Crown crown = circlet();
        BufferedImage worn = crown.render(40, false);
        AssetGen.writePng(worn, assets.resolve("textures/item/wiccan_crown_worn.png"));
        AssetGen.writePng(crown.render(16, true), assets.resolve("textures/item/wiccan_crown.png"));
        AssetGen.writeText(crown.itemDefinitionJson(), assets.resolve("items/wiccan_crown.json"));
        AssetGen.writeText(crown.wornModelJson(4.5), assets.resolve("models/item/wiccan_crown_worn.json"));
        AssetGen.writeText(crown.iconModelJson(), assets.resolve("models/item/wiccan_crown.json"));
        // Map a length of the engraved brow across each side and the back, clear of the skin's hair layer.
        String uv = "[2.8, 6.6, 13.2, 8.2]";
        String elements = String.join(",\n",
                AssetGen.Crown.element(-4.55, 1.15, -4.56, -4.51, 2.72, 4.51, uv),
                AssetGen.Crown.element(4.51, 1.15, -4.56, 4.55, 2.72, 4.51, uv),
                AssetGen.Crown.element(-4.55, 1.15, 4.51, 4.55, 2.72, 4.55, uv));
        AssetGen.writeText("""
                {
                  "textures": {"metal": "scarlet:item/wiccan_crown_worn", "particle": "#metal"},
                  "display": {"head": {"rotation": [0,0,0], "translation": [0,0,0], "scale": [1,1,1]}},
                  "elements": [
                %s
                  ]
                }
                """.formatted(elements), assets.resolve("models/item/wiccan_crown_worn_sides.json"));

        Path textures = assets.resolve("textures/entity/costume");
        for (boolean divine : new boolean[]{false, true}) {
            String form = divine ? "wiccan_astral" : "wiccan";
            BufferedImage cape = cape(divine);
            AssetGen.writePng(cape, textures.resolve(form + "_cape.png"));
            for (boolean slim : new boolean[]{false, true}) {
                BufferedImage suit = CostumeArt.costume(slim, divine ? WiccanArt::demiurge : WiccanArt::pixel);
                String name = form + (slim ? "_slim" : "");
                AssetGen.writePng(suit, textures.resolve(name + ".png"));
                AssetGen.writePng(CostumeArt.preview(suit, cape, slim, 6), preview.resolve("costume_" + name + ".png"));
            }
        }
    }

    private static AssetGen.Pt p(double x, double y) {
        return new AssetGen.Pt(x, y);
    }

    private static AssetGen.Crown circlet() {
        var rim = List.of(p(0, 0.75), p(0.65, 1.35), p(1.3, 1.12), p(2.3, 1.63), p(3.1, 1.52),
                p(3.65, 0.85), p(4.15, -0.25), p(4.55, 0.35), p(4.55, 2.72), p(4.0, 3.05),
                p(3.6, 3.65), p(2.7, 3.22), p(1.6, 2.85), p(0.8, 2.72), p(0, 2.48));
        var ribs = List.of(List.of(p(0, 1.0), p(0.9, 2.0), p(2.1, 2.65), p(3.55, 3.24)),
                List.of(p(1.2, 1.48), p(2, 2.15), p(3.05, 2.12), p(3.9, 2.7)),
                List.of(p(2.6, 2.7), p(3.65, 2.0), p(4.23, 0.45)));
        return new AssetGen.Crown("wiccan_crown", rim, ribs, 1, 1, 10, 1.7,
                new AssetGen.Tints(0x235398, 0x3A78BD, 0x94C6ED, 0x19376B, 0x0F2348),
                new AssetGen.SideWrap(new double[0][]));
    }

    private static int pixel(CostumeArt.Part part, boolean outer, CostumeArt.Side side, double x, double y, int w) {
        boolean top = side == CostumeArt.Side.TOP, bottom = side == CostumeArt.Side.BOTTOM;
        boolean front = side == CostumeArt.Side.FRONT, back = side == CostumeArt.Side.BACK;
        double c = Math.abs(x - w / 2.0);
        int color;
        switch (part) {
            case BODY -> {
                // Cloth drawn across the collarbone toward a fastening on Billy's left.
                double hem = front ? mantleHem(x) : back ? 2.5 : side == CostumeArt.Side.RIGHT ? 5.8 : 1.55;
                if (!bottom && (top || y < hem)) {
                    color = scarf(x, y, top);
                    if (front && !top && Math.abs(x - 6.6) + Math.abs(y - 1.15) < 1.05)
                        color = y < 1.2 ? 0x9DA9B9 : 0x30262F;
                } else if (bottom) {
                    color = INK;
                } else if (y > 9.4 && (y < 10.7 || Math.abs(y - 10.0 - x * 0.18) < 0.55)) {
                    double ring = Math.hypot((x - 3.15) / 0.96, (y - 10.0) / 0.83);
                    color = front && ring > 0.53 && ring < 1.25 ? (y < 10 ? PEWTER : 0x748397)
                            : y < 9.85 || Math.abs(y - 9.65 - x * 0.18) < 0.2 ? 0x52515C : 0x21212A;
                } else {
                    if (outer) return 0;
                    double seam = 2.24 + 0.74 * Math.cos((y - 3.0) * 0.49);
                    int panel = CostumeArt.gradient(MIDNIGHT, TWILL, 0.52 * Math.exp(-0.14 * (y - 4) * (y - 4)) + 0.16 * c / w);
                    color = front || back ? (c < seam - 0.25 ? panel : c < seam + 0.17 ? SATIN : TWILL) : TWILL;
                    if (front && c > seam + 0.3) color = stars(0x272D3A, x, y, 41, false);
                }
            }
            case RIGHT_ARM, LEFT_ARM -> {
                boolean right = part == CostumeArt.Part.RIGHT_ARM;
                double hem = right ? 6.2 + 0.35 * Math.cos(x) : 1.7 - 0.15 * x;
                if (!bottom && (top || y < hem)) {
                    color = scarf(x + (right ? -w : 8), y, top);
                } else {
                    if (outer && !(y > 8.5 && y < 11.5 && !bottom)) return 0;
                    // Open fingertips let the player's own skin show through the gloves.
                    if (bottom || y >= 11.5 && (front || c < 1.1)) return 0;
                    color = y > 8.5 ? INK : CostumeArt.gradient(0x13161E, 0x30323C, 0.65 - c * 0.28);
                    if (y > 8.5 && y < 10.75 && Math.abs(y - 9.5 - 0.35 * x) < 0.23) color = TWILL;
                }
            }
            case RIGHT_LEG, LEFT_LEG -> {
                boolean right = part == CostumeArt.Part.RIGHT_LEG;
                boolean sidePanel = side == (right ? CostumeArt.Side.RIGHT : CostumeArt.Side.LEFT)
                        || (front && (right ? x : w - x) < 1.05);
                double edge = 6.0 + 0.37 * (right ? x : w - x);
                if (outer && (top || bottom || y < edge || y > edge + 0.7)) return 0;
                if (top || bottom) color = INK;
                else if (y >= edge) color = y < edge + 0.6 ? SATIN : y > 11.4 ? INK
                        : front && c < 1.25 ? CostumeArt.gradient(TWILL, MIDNIGHT, (y - edge) * 0.24) : 0x222936;
                else {
                    color = sidePanel ? stars(0x2A3141, x, y, 9, false) : CostumeArt.gradient(TWILL, MIDNIGHT, c * 0.45);
                    if (right && front && x < 1.8 && Math.abs(Math.hypot(x - 0.45, y - 4.6) - 1.15) < 0.26)
                        color = PEWTER;
                }
            }
            default -> throw new AssertionError(part);
        }
        return cloth(color, x, y);
    }

    private static double mantleHem(double x) {
        return 1.5 + 4.8 * (1.0 - x / 8.0) + 0.5 * Math.sin(Math.PI * x / 8);
    }

    private static int scarf(double x, double y, boolean top) {
        double ridge = Math.cos((y + x * 0.52 - 0.68 * Math.sin(x * 0.45)) * 3.2);
        return CostumeArt.gradient(OXBLOOD, top ? FOLD : 0xBB3445, 0.53 + 0.44 * ridge);
    }

    private static int demiurge(CostumeArt.Part part, boolean outer, CostumeArt.Side side, double x, double y, int w) {
        boolean front = side == CostumeArt.Side.FRONT, back = side == CostumeArt.Side.BACK;
        boolean top = side == CostumeArt.Side.TOP, bottom = side == CostumeArt.Side.BOTTOM;
        double c = Math.abs(x - w * 0.5);
        int color = CostumeArt.gradient(0x10192E, 0x2D507B, (1 - c / (w * 0.55)) * 0.86);
        switch (part) {
            case BODY -> {
                double collar = front ? 2.8 - c * c * 0.105 : 2.2;
                if (top || !bottom && y < collar) {
                    color = silk(x, top ? 0 : y - collar);
                } else if (front && !bottom) {
                    double jewel = Math.hypot(c / 0.97, (y - 10.15) / 1.05);
                    double waist = 10.1 - 0.36 * c;
                    if (jewel < 1.1) color = jewel > 0.68 ? 0xD4EAF2 : y < 10.1 ? 0x426F98 : 0x152139;
                    else if (Math.abs(y - waist) < 0.52) color = y < waist ? 0xACCBD8 : 0x718C9F;
                    else {
                        if (outer) return 0;
                        // Two interlaced arcs open at the shoulders and converge on the waist setting.
                        double arc = 0.45 + 0.11 * Math.pow(Math.max(0, 9.9 - y), 1.84);
                        double crossing = 3.2 + 0.42 * c + 0.17 * c * c;
                        if (Math.abs(c - arc) < 0.28 || Math.abs(y - crossing) < 0.31)
                            color = y < 6 ? 0xA9DCEF : 0x7BB6D1;
                    }
                } else {
                    if (outer) return 0;
                    if (!bottom && Math.abs(y - 9.1) < 0.56) color = 0x869DB4;
                }
            }
            case LEFT_ARM, RIGHT_ARM -> {
                if (top || !bottom && y < 1.8 + c * 0.32) color = silk(x, y - 1.8);
                else if (!bottom && y > 8 && y < 10.8) {
                    double band = (y + 0.45 * x) % 1.4;
                    color = band < 0.52 ? 0xA0BDCF : 0x29466A;
                } else {
                    if (outer || bottom || y > 10.8) return 0;
                    if (front && Math.abs(c - 0.4 - Math.abs(y - 5.0) * 0.48) < 0.25) color = 0x679DBB;
                }
            }
            case LEFT_LEG, RIGHT_LEG -> {
                if (outer) return 0;
                double fromOutside = part == CostumeArt.Part.RIGHT_LEG ? x : w - x;
                if (front && !top && !bottom) {
                    double welt = 0.55 + 0.8 * Math.sin(y * Math.PI / 12);
                    if (Math.abs(fromOutside - welt) < 0.23) color = 0x527C9D;
                    if (y < 1.65 - fromOutside * 0.38) color = 0x637D95;
                    if (y > 8 + fromOutside * 0.48) color = CostumeArt.gradient(0x172137, 0x334A69, c / 2);
                }
            }
        }
        return cloth(color, x, y);
    }

    private static int silk(double x, double y) {
        return CostumeArt.gradient(0x70234F, 0xDD467B, 0.53 + 0.47 * Math.cos(y * 3.2 + x * 0.34));
    }

    private static int stars(int base, double x, double y, int seed, boolean bright) {
        int u = (int)(x * 2), v = (int)(y * 2);
        int n = Math.floorMod((u * 43 + v * 83 + seed) * (v + 7) + u * u, 97);
        return n < 3 ? (bright ? 0xBACFDC : SATIN) : n < 12 ? 0x637890 : base;
    }

    private static int cloth(int rgb, double x, double y) {
        return CostumeArt.opaque(CostumeArt.shade(rgb, CostumeArt.noise((int)(x * 2), (int)(y * 2)) * 0.035));
    }

    private static BufferedImage cape(boolean divine) {
        BufferedImage img = new BufferedImage(CostumeArt.SIZE, CostumeArt.SIZE, BufferedImage.TYPE_INT_ARGB);
        for (boolean lining : new boolean[]{false, true}) {
            CostumeArt.paintRect(img, lining ? 12 : 1, 1, 10, 22, (x, y) -> {
                double across = lining ? 10 - x : x;
                double hem = divine ? 21.8 - 0.075 * Math.pow(across - 5, 2)
                        : 20.2 + 0.17 * across - 0.46 * Math.cos(across * 1.4);
                if (y > hem) return 0;
                double phase = across * 2.22 + 0.52 * Math.sin(y * 0.28 + across * 0.47);
                double fold = 0.5 + 0.5 * Math.cos(phase);
                int color = divine ? CostumeArt.gradient(lining ? 0x502046 : 0x561838,
                        lining ? 0xC04196 : 0xE1467B, fold * 0.87 + 0.05)
                        : CostumeArt.gradient(lining ? 0x631D2C : OXBLOOD, lining ? 0xB54C43 : FOLD, fold * 0.8 + 0.08);
                if (y > hem - 0.55 || lining && Math.abs(Math.abs(across - 5) - 4.2) < 0.25)
                    color = divine ? 0xD8639B : 0xA85347;
                return cloth(color, across, y);
            });
        }
        CostumeArt.paintRect(img, 0, 1, 1, 22, (x, y) -> y < 8 ? CostumeArt.opaque(OXBLOOD) : 0);
        CostumeArt.paintRect(img, 11, 1, 1, 22, (x, y) -> y < 20 ? CostumeArt.opaque(OXBLOOD) : 0);
        CostumeArt.paintRect(img, 1, 0, 10, 1, (x, y) -> CostumeArt.opaque(RUBY));
        return img;
    }
}
