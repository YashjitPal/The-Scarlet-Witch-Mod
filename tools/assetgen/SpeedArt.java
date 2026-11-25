import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Tommy's Young Avengers kit. Face-local UVs follow the same player layout as the other costumes. */
final class SpeedArt {
    private static final int GREEN = 0x339454, LIGHT = 0x60B966, PINE = 0x245240, INK = 0x173934;
    private static final int WHITE = 0xF6F4DF, SHADE = 0xBACEBE;
    private static final int[] FRAME = {0x202C35, 0x364651, 0x98ABA9, 0xD6DFD5, 0x344958, 0x547780, 0x9AC3C1, 0xE2EBDC};

    public static void main(String[] args) throws IOException {
        generate(Path.of("common/src/main/resources/assets/scarlet"));
    }

    static void generate(Path assets) throws IOException {
        for (boolean slim : new boolean[]{false, true}) {
            BufferedImage suit = uniform(slim);
            hair(suit);
            AssetGen.writePng(suit, assets.resolve("textures/entity/costume/speed" + (slim ? "_slim" : "") + ".png"));
        }
        AssetGen.writePng(icon(true), assets.resolve("textures/item/speed_goggles.png"));
        AssetGen.writePng(icon(false), assets.resolve("textures/item/speed_suit.png"));
        BufferedImage swatches = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) swatches.setRGB(x, y, FRAME[x / 2] | 0xff000000);
        }
        AssetGen.writePng(swatches, assets.resolve("textures/item/speed_goggles_frame.png"));
        AssetGen.writeText(goggles(), assets.resolve("models/item/speed_goggles_worn.json"));
    }

    // Each character is one native skin texel. Separate faces keep cuffs, soles and the lightning
    // flash legible when Minecraft bends the arms and legs.
    private static final String[] CHEST = {
            "mhsddslw", "mllssgww", "hlllgwwp", "lllgwwps", "gggwwpgs", "sgwwpgms",
            "smwwwwps", "smmppwps", "dsggwwms", "dsgwwgms", "dgwpgmss", "gwpmmssd"};
    private static final String[] BACK = {
            "mlsddslm", "gllllllg", "spllllps", "sppggpps", "sgppppgs", "dsgppgsd",
            "dsmggmsd", "dsmllmsd", "dsggggsd", "dgmssmgd", "ssmggmss", "ddsmmsdd"};
    private static final String[] RIBS = {
            "mllg", "gllm", "sggp", "sspp", "dsps", "dsgs", "dsgs", "dsgs", "dsgs", "dsgs", "sdss", "ddss"};
    private static final String[] ARM = {
            "mlll", "ghhl", "mllg", "sgml", "dsmm", "dsgm", "dsgs", "sspw", "ppww", "pwwp", "wppw", "pssw"};
    private static final String[] OUTER_ARM = {
            "lhhl", "lllg", "wwlg", "pwgg", "psgm", "sgmm", "sgms", "pmss", "wwpp", "pwwp", "sppw", "sppp"};
    private static final String[] INNER_ARM = {
            "mgms", "sgms", "sggs", "dsgs", "dsgs", "dsss", "dsss", "dssp", "sppp", "pssp", "pppp", "spps"};
    private static final String[] THIGH = {
            "smgm", "mlgg", "hlgm", "llgm", "lgms", "mgms", "ssmd", "psms", "wwpp", "ppsw", "pwww", "ssss"};
    private static final String[] OUTER_LEG = {
            "ssmm", "mlgm", "mlgg", "mllg", "wmlg", "pwmm", "spws", "dspw", "sspp", "wppp", "pwww", "dssd"};
    private static final String[] CALF = {
            "mggs", "mggs", "smlg", "smgg", "dsgm", "ddss", "dsss", "psss", "pwwp", "pspp", "spps", "dssd"};

    private static BufferedImage uniform(boolean slim) {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (CostumeArt.Part part : CostumeArt.Part.values()) {
            for (CostumeArt.Side side : CostumeArt.Side.values()) {
                int[] face = CostumeArt.box(part, false, slim).face(side);
                int[] overlay = CostumeArt.box(part, true, slim).face(side);
                boolean torso = part == CostumeArt.Part.BODY;
                boolean arm = part == CostumeArt.Part.LEFT_ARM || part == CostumeArt.Part.RIGHT_ARM;
                boolean left = part == CostumeArt.Part.LEFT_ARM || part == CostumeArt.Part.LEFT_LEG;
                boolean outside = side == (left ? CostumeArt.Side.LEFT : CostumeArt.Side.RIGHT);
                for (int y = 0; y < face[3]; y++) {
                    for (int x = 0; x < face[2]; x++) {
                        char pixel;
                        boolean raised = false;
                        if (side == CostumeArt.Side.TOP) {
                            pixel = torso && x >= 2 && x <= 5 ? 'd' : y < 2 ? 'l' : 'g';
                        } else if (side == CostumeArt.Side.BOTTOM) {
                            pixel = arm ? (x == 0 ? 's' : 'p') : 'd';
                        } else if (torso) {
                            String[] rows = side == CostumeArt.Side.FRONT ? CHEST : side == CostumeArt.Side.BACK ? BACK : RIBS;
                            pixel = rows[y].charAt(x);
                            raised = y == 0 && (pixel == 's' || pixel == 'd');
                        } else {
                            int column = face[2] == 3 && x >= 1 ? x + 1 : x;
                            if (left && (side == CostumeArt.Side.FRONT || side == CostumeArt.Side.BACK)) column = 3 - column;
                            String[] rows = arm ? (outside ? OUTER_ARM : side == CostumeArt.Side.FRONT ? ARM : INNER_ARM)
                                    : (outside ? OUTER_LEG : side == CostumeArt.Side.FRONT ? THIGH : CALF);
                            pixel = rows[y].charAt(column);
                            raised = arm ? y == 8 || y == 9 : y >= 10;
                        }
                        int rgb = fabric(pixel);
                        image.setRGB(face[0] + x, face[1] + y, rgb);
                        if (raised) image.setRGB(overlay[0] + x, overlay[1] + y, rgb);
                    }
                }
            }
        }
        return image;
    }

    private static int fabric(char texel) {
        int rgb = switch (texel) {
            case 'd' -> INK; case 's' -> PINE; case 'm' -> 0x26734B; case 'g' -> GREEN;
            case 'l' -> LIGHT; case 'h' -> 0x9BCE78; case 'w' -> WHITE; case 'p' -> SHADE;
            default -> throw new IllegalArgumentException("Unknown fabric texel " + texel);
        };
        return 0xff000000 | rgb;
    }

    private static void hair(BufferedImage img) {
        String[] top = {"bbccdddc","bccdeeed","ccdeeedc","cdeeedcb","deeedcbb","eeedccbc","eedccbcc","ddccbccd"};
        String[] front = {"ccdddeed","cdddeedc","dddeedcb","ddeedcbb","dc..c..b","b......."};
        String[] temple = {"ccddeeed","cddeeedc","ddeeedcb","deeedccc","eedccbbb","ddccbb..","ccbbb...","bbb....."};
        String[] back = {"dccddeed","ccddeedc","cddeedcc","ddeedccc","dedcccbb","ddcccbbc","cccbbccb","bbccbbcb"};
        paintHair(img, 40, 0, top, false);
        paintHair(img, 40, 8, front, false);
        paintHair(img, 32, 8, temple, false);
        paintHair(img, 48, 8, temple, true);
        paintHair(img, 56, 8, back, false);
        // The locks share an unused area at the top of the atlas.
        paintHair(img, 24, 0, new String[]{"ccccddddeeeedccc","ccddddeeeeddccbb","cddddeeeeddccbbc",
                "ddddeeeeddccbbcc","dddeeeeddccbbccc","ddeeeeddccbbcccd","deeeeddccbbcccdd","eeeeddccbbcccddd"}, false);
    }

    private static void paintHair(BufferedImage img, int u, int v, String[] rows, boolean mirror) {
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                int rgb = switch (rows[y].charAt(x)) {
                    case 'b' -> 0x839599; case 'c' -> 0xADBCC0; case 'd' -> 0xD6DEDA; case 'e' -> 0xF5F4E8; default -> 0;
                };
                if (rgb != 0) img.setRGB(u + (mirror ? rows[y].length() - 1 - x : x), v + y, 0xff000000 | rgb);
            }
        }
    }

    private static String goggles() {
        List<String> pieces = new ArrayList<>();
        box(pieces, -1.0, 1.9, -5.14, 1.0, 2.9, -4.78, 4);
        box(pieces, -1.0, 2.65, -5.16, 1.0, 3.05, -4.78, 1);
        for (int dir : new int[]{-1, 1}) {
            // A single shield with a nose notch, a rising outside edge and short ear stems.
            lens(pieces, dir, 0.70, 1.02, 2.58, 3.05, -5.23, 4);
            lens(pieces, dir, 2.57, 1.40, 3.65, 3.32, -5.14, 4);
            lens(pieces, dir, 3.64, 1.86, 4.55, 3.50, -5.03, 4);
            lens(pieces, dir, 0.70, 2.60, 2.58, 3.05, -5.27, 1);
            lens(pieces, dir, 2.57, 2.88, 3.65, 3.32, -5.18, 1);
            lens(pieces, dir, 3.64, 3.05, 4.55, 3.50, -5.07, 1);
            lens(pieces, dir, 0.72, 1.45, 2.56, 2.60, -5.26, 5);
            lens(pieces, dir, 1.64, 1.95, 2.57, 2.60, -5.29, 6);
            lens(pieces, dir, 2.57, 2.25, 3.65, 2.88, -5.17, 6);
            lens(pieces, dir, 3.65, 2.52, 4.52, 3.05, -5.06, 7);
            box(pieces, dir > 0 ? 4.62 : -4.84, 2.35, -4.85, dir > 0 ? 4.84 : -4.62, 3.32, 2.25, 1);
            box(pieces, dir > 0 ? 4.63 : -4.85, 1.68, 2.23, dir > 0 ? 4.85 : -4.63, 2.65, 3.65, 0);
            box(pieces, dir > 0 ? 4.85 : -4.9, 2.66, -4.2, dir > 0 ? 4.9 : -4.85, 3.15, -2.4, 3);
        }
        return """
                {
                  "textures": {"metal":"scarlet:item/speed_goggles_frame","particle":"#metal"},
                  "display":{"head":{"rotation":[0,0,0],"translation":[0,0,0],"scale":[1,1,1]}},
                  "elements":[
                %s
                  ]
                }
                """.formatted(String.join(",\n", pieces));
    }

    private static void lens(List<String> pieces, int dir, double x0, double y0, double x1, double y1, double z, int color) {
        box(pieces, dir > 0 ? x0 : -x1, y0, z, dir > 0 ? x1 : -x0, y1, -4.66, color);
    }

    private static void box(List<String> pieces, double x0, double y0, double z0, double x1, double y1, double z1, int color) {
        int u = color * 2;
        pieces.add(AssetGen.Crown.element(x0, y0, z0, x1, y1, z1, "[" + u + ",2," + (u + 1) + ",3]"));
    }

    private static BufferedImage icon(boolean glasses) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        String[] art = glasses ? new String[]{
                " d            d ", " sd          ds ", "  sd        ds  ", "mmssmmmmmmmmssmm",
                "dhaassssssssaahd", " dhaaggggggaahd ", "  dgaagmmgaagd  ", "   dggd  dggd   ", "    dd    dd    "}
                : new String[]{"    ddddddd    ", "   slllwlwgs   ", "  slgggwwgggs  ", " slgggwpwggggs ",
                " sgggwpwwwgggs ", " dggwpwggwwggd ", " dgwppgggggwwd ", " dgggggggggggd ", " dsssssssssssd ",
                "  sggwwwggggd  ", "  dssssssssd   ", "   dddddddd    "};
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < art.length; y++) for (int x = 0; x < art[y].length(); x++) {
            int rgb = switch (art[y].charAt(x)) {
                case 's' -> glasses ? FRAME[2] : PINE; case 'd' -> FRAME[0]; case 'm' -> FRAME[1];
                case 'h' -> FRAME[7]; case 'a' -> FRAME[6]; case 'g' -> glasses ? FRAME[5] : GREEN;
                case 'l' -> LIGHT; case 'w' -> WHITE; case 'p' -> SHADE; default -> -1;
            };
            if (rgb >= 0) { g.setColor(new Color(rgb)); g.fillRect(x, y + (glasses ? 3 : 2), 1, 1); }
        }
        g.dispose();
        return img;
    }
}
