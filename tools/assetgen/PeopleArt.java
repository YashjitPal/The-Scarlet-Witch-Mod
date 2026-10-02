import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Paints the people of the Hex, in the 64x64 player skin layout at twice the density like the costumes.
 *
 * <ul>
 *     <li><b>Townspeople:</b> whole skins for the mobs a Hex rewrites, six for each era: faces, hair and skin tones,
 *     dressed for the decade.</li>
 *     <li><b>Era outfits:</b> what players wear inside a Hex, four for each era, fitted over their own skin like the
 *     costume is. Casual and gender neutral; transparent wherever their own skin should show.</li>
 * </ul>
 *
 * <p>Clothes are built from pieces (a top, sleeves, perhaps a jacket, a tie or pearls, trousers or a skirt, shoes),
 * each painted in face-local skin pixels like the costume art.
 */
final class PeopleArt {

    static final int DENSITY = CostumeArt.DENSITY;
    static final int SIZE = CostumeArt.SIZE;
    static final String[] ERAS = {"fifties", "sixties", "seventies", "eighties", "two_thousands", "present"};

    enum Top { TEE, SHIRT, SWEATER, TURTLENECK, DRESS, HOODIE, POLO, BLOUSE }

    enum Sleeves { NONE, SHORT, LONG }

    enum Bottom { TROUSERS, FLARES, JEANS, SKIRT, LEGGINGS, SHORTS }

    enum Pattern { NONE, STRIPES, DOTS, CHECKS, BLOCKS }

    enum Extra { NONE, TIE, PEARLS, BELT, APRON, SCARF, HEADBAND }

    enum Hair { SIDE_PART, CREW, CURLS, BOB, LONG, BUN, AFRO, PONYTAIL, SHAG }

    /**
     * @param jacket 0 for none, otherwise the color of a jacket worn open over the top
     */
    record Outfit(Top top, int topColor, int trim, Sleeves sleeves, Pattern pattern, int patternColor, int jacket, Extra extra, int extraColor,
                  Bottom bottom, int bottomColor, int shoes) {
    }

    record Person(int skin, int hairColor, Hair hair, int eyes, int lips, boolean slim) {
    }

    // ---------------------------------------------------------------- the wardrobes

    /** What the townspeople wear in each era: three in trousers, three in dresses or skirts. */
    static Outfit[][] residentWardrobes() {
        return new Outfit[][] {
                {
                        new Outfit(Top.SHIRT, 0xF2EFE8, 0xD8D2C8, Sleeves.LONG, Pattern.NONE, 0, 0x6C6A70, Extra.TIE, 0x2A2A34, Bottom.TROUSERS, 0x5E5C62, 0x1E1A18),
                        new Outfit(Top.SWEATER, 0x9EB6CC, 0x7E96AC, Sleeves.LONG, Pattern.NONE, 0, 0, Extra.NONE, 0, Bottom.TROUSERS, 0x8A7A64, 0x3A2A20),
                        new Outfit(Top.SHIRT, 0xE8E4DA, 0xC8C2B6, Sleeves.LONG, Pattern.NONE, 0, 0, Extra.BELT, 0x3A2A22, Bottom.TROUSERS, 0x4A4E5C, 0x221C1A),
                        new Outfit(Top.DRESS, 0x6E8AB0, 0xF4F0EA, Sleeves.SHORT, Pattern.DOTS, 0xF4F0EA, 0, Extra.APRON, 0xF6F4F0, Bottom.SKIRT, 0x6E8AB0, 0xF4F0EA),
                        new Outfit(Top.BLOUSE, 0xF4EEE6, 0xE0D6CA, Sleeves.SHORT, Pattern.NONE, 0, 0xD898A8, Extra.PEARLS, 0xF8F6F2, Bottom.SKIRT, 0x5C6E8C, 0x2A2224),
                        new Outfit(Top.DRESS, 0xD86A7A, 0xF0D8DC, Sleeves.SHORT, Pattern.NONE, 0, 0, Extra.BELT, 0x2A1E22, Bottom.SKIRT, 0xD86A7A, 0x2A1E22)
                },
                {
                        new Outfit(Top.SHIRT, 0xF4F4F0, 0xDADAD4, Sleeves.LONG, Pattern.NONE, 0, 0x2E3440, Extra.TIE, 0x15171C, Bottom.TROUSERS, 0x2E3440, 0x101012),
                        new Outfit(Top.TURTLENECK, 0x222226, 0x101012, Sleeves.LONG, Pattern.NONE, 0, 0, Extra.NONE, 0, Bottom.TROUSERS, 0x7E7A70, 0x1A1614),
                        new Outfit(Top.POLO, 0x5CB8A8, 0x3E9888, Sleeves.SHORT, Pattern.STRIPES, 0xF2F2EC, 0, Extra.NONE, 0, Bottom.TROUSERS, 0xE6DCC4, 0x6A4A30),
                        new Outfit(Top.DRESS, 0xF08A2A, 0xF8F0E0, Sleeves.NONE, Pattern.BLOCKS, 0xF8F0E0, 0, Extra.NONE, 0, Bottom.SKIRT, 0xF08A2A, 0xF8F6F0),
                        new Outfit(Top.TURTLENECK, 0xF2F0EA, 0xD8D4CC, Sleeves.LONG, Pattern.NONE, 0, 0, Extra.NONE, 0, Bottom.SKIRT, 0x2A2A2E, 0x0E0E10),
                        new Outfit(Top.DRESS, 0x8ED0C6, 0x5EA89E, Sleeves.SHORT, Pattern.STRIPES, 0xF6F2EA, 0, Extra.HEADBAND, 0xF6F2EA, Bottom.SKIRT, 0x8ED0C6, 0xF6F2EA)
                },
                {
                        new Outfit(Top.SHIRT, 0xD9A24A, 0xA0702A, Sleeves.LONG, Pattern.STRIPES, 0x8A4A1E, 0x6E4422, Extra.NONE, 0, Bottom.FLARES, 0x6A4A2E, 0x3A2416),
                        new Outfit(Top.TURTLENECK, 0xB8622A, 0x8A4418, Sleeves.LONG, Pattern.NONE, 0, 0, Extra.NONE, 0, Bottom.FLARES, 0x4A5A7A, 0x3A2416),
                        new Outfit(Top.SWEATER, 0x8C7A3A, 0x6C5C26, Sleeves.LONG, Pattern.STRIPES, 0xD8B060, 0, Extra.NONE, 0, Bottom.FLARES, 0x7A4A2A, 0x2A1A10),
                        new Outfit(Top.DRESS, 0xC8682E, 0xE8B060, Sleeves.LONG, Pattern.STRIPES, 0xE8B060, 0, Extra.BELT, 0x5A3418, Bottom.SKIRT, 0xC8682E, 0x5A3418),
                        new Outfit(Top.BLOUSE, 0xF0DCB0, 0xD8BC88, Sleeves.LONG, Pattern.NONE, 0, 0x9A5A2A, Extra.SCARF, 0xD06A30, Bottom.FLARES, 0x3E5A86, 0x4A2E1A),
                        new Outfit(Top.DRESS, 0x6E7A34, 0x4E5A20, Sleeves.LONG, Pattern.DOTS, 0xE8D8A0, 0, Extra.NONE, 0, Bottom.SKIRT, 0x6E7A34, 0x5A3418)
                },
                {
                        new Outfit(Top.HOODIE, 0x28C8D8, 0xF04AA0, Sleeves.LONG, Pattern.BLOCKS, 0xF04AA0, 0, Extra.NONE, 0, Bottom.TROUSERS, 0x28C8D8, 0xF8F8F8),
                        new Outfit(Top.TEE, 0xF8F8F4, 0xDADAD4, Sleeves.SHORT, Pattern.NONE, 0, 0x3A4C8C, Extra.NONE, 0, Bottom.JEANS, 0x4E6CA8, 0xF2F2F2),
                        new Outfit(Top.POLO, 0xF2E25A, 0xD0BE30, Sleeves.SHORT, Pattern.NONE, 0, 0, Extra.HEADBAND, 0xF04AA0, Bottom.SHORTS, 0x2E3C78, 0xF8F8F8),
                        new Outfit(Top.BLOUSE, 0xF04AA0, 0xC82A80, Sleeves.LONG, Pattern.NONE, 0, 0x7A3AB8, Extra.BELT, 0x18181C, Bottom.SKIRT, 0x18181C, 0x18181C),
                        new Outfit(Top.SWEATER, 0x8A4AD0, 0x6A2AB0, Sleeves.LONG, Pattern.BLOCKS, 0x28C8D8, 0, Extra.HEADBAND, 0x28C8D8, Bottom.LEGGINGS, 0x18181C, 0xF8F8F8),
                        new Outfit(Top.DRESS, 0x2AA8E8, 0x1A88C8, Sleeves.SHORT, Pattern.DOTS, 0xF8F030, 0, Extra.BELT, 0xF04AA0, Bottom.SKIRT, 0x2AA8E8, 0xF04AA0)
                },
                {
                        new Outfit(Top.HOODIE, 0x6A6E74, 0x4A4E54, Sleeves.LONG, Pattern.NONE, 0, 0, Extra.NONE, 0, Bottom.JEANS, 0x4A6A98, 0x2A2A2E),
                        new Outfit(Top.POLO, 0x3A6A9A, 0x2A5078, Sleeves.SHORT, Pattern.NONE, 0, 0, Extra.BELT, 0x4A3424, Bottom.TROUSERS, 0xCCB890, 0x5A3E28),
                        new Outfit(Top.TEE, 0xC23A3A, 0x9A2A2A, Sleeves.SHORT, Pattern.BLOCKS, 0xF2F2EE, 0, Extra.NONE, 0, Bottom.JEANS, 0x3E5A86, 0xE8E8E8),
                        new Outfit(Top.TEE, 0xF2B8C8, 0xD898AA, Sleeves.NONE, Pattern.NONE, 0, 0, Extra.BELT, 0xE8DCC8, Bottom.SKIRT, 0x5A7AAA, 0xE8DCC8),
                        new Outfit(Top.BLOUSE, 0xF4F2EE, 0xDCD8D0, Sleeves.SHORT, Pattern.NONE, 0, 0x8A9AB0, Extra.NONE, 0, Bottom.JEANS, 0x34507A, 0x2A2A2E),
                        new Outfit(Top.DRESS, 0x4A4A8A, 0x34346A, Sleeves.NONE, Pattern.NONE, 0, 0, Extra.NONE, 0, Bottom.SKIRT, 0x4A4A8A, 0x1E1E22)
                },
                {
                        new Outfit(Top.TEE, 0xE8E6E0, 0xCECAC2, Sleeves.SHORT, Pattern.NONE, 0, 0, Extra.NONE, 0, Bottom.TROUSERS, 0x34383E, 0xF2F2F0),
                        new Outfit(Top.SHIRT, 0x8A3A34, 0x5E2420, Sleeves.LONG, Pattern.CHECKS, 0x2A2A2E, 0, Extra.NONE, 0, Bottom.JEANS, 0x2E3A52, 0x6A4A30),
                        new Outfit(Top.HOODIE, 0x5E6E5A, 0x44523F, Sleeves.LONG, Pattern.NONE, 0, 0, Extra.NONE, 0, Bottom.TROUSERS, 0xA8A090, 0xEDEDEB),
                        new Outfit(Top.SWEATER, 0xD8CDB8, 0xBDB09A, Sleeves.LONG, Pattern.NONE, 0, 0, Extra.NONE, 0, Bottom.TROUSERS, 0x2A2A2E, 0x1A1A1C),
                        new Outfit(Top.TEE, 0x2A2A2E, 0x18181A, Sleeves.SHORT, Pattern.NONE, 0, 0x6A8AB0, Extra.NONE, 0, Bottom.JEANS, 0x24262C, 0xF0F0EE),
                        new Outfit(Top.DRESS, 0xB89A7A, 0x9A7E60, Sleeves.LONG, Pattern.NONE, 0, 0, Extra.BELT, 0x4A3424, Bottom.SKIRT, 0xB89A7A, 0x3A2A20)
                }
        };
    }

    /** What players wear in each era: casual, well-fitting and the same whoever wears them. */
    static Outfit[][] playerWardrobes() {
        Outfit[][] residents = residentWardrobes();
        Outfit[][] players = new Outfit[residents.length][];
        for (int era = 0; era < residents.length; era++) {
            List<Outfit> picked = new ArrayList<>();
            for (Outfit outfit : residents[era]) {
                if (outfit.bottom() != Bottom.SKIRT && outfit.top() != Top.DRESS && outfit.extra() != Extra.APRON) {
                    picked.add(outfit);
                }
            }
            // a fourth, from the era's palette, for variety
            Outfit first = residents[era][0];
            picked.add(new Outfit(Top.SWEATER, residents[era][3].topColor(), residents[era][3].trim(), Sleeves.LONG, Pattern.NONE, 0, 0, Extra.NONE,
                    0, first.bottom() == Bottom.FLARES ? Bottom.FLARES : Bottom.TROUSERS, first.bottomColor(), first.shoes()));
            players[era] = picked.subList(0, 4).toArray(Outfit[]::new);
        }
        return players;
    }

    /** The townspeople themselves: who wears each outfit of an era. */
    static Person[] people(int era) {
        int[] skins = {0xF2CDB0, 0xD9A882, 0xB97E58, 0x8A5A3A, 0x6A4028, 0xF0C4A4};
        int[] hairs = {0x2A1C14, 0x6A4424, 0xC8A060, 0x1A1412, 0x8A3A1E, 0x4A3020};
        Hair[][] styles = {
                {Hair.SIDE_PART, Hair.CREW, Hair.SIDE_PART, Hair.CURLS, Hair.BUN, Hair.CURLS},
                {Hair.SIDE_PART, Hair.CREW, Hair.SIDE_PART, Hair.BOB, Hair.BUN, Hair.BOB},
                {Hair.SHAG, Hair.AFRO, Hair.SHAG, Hair.LONG, Hair.LONG, Hair.AFRO},
                {Hair.SHAG, Hair.CREW, Hair.CURLS, Hair.AFRO, Hair.PONYTAIL, Hair.CURLS},
                {Hair.CREW, Hair.SIDE_PART, Hair.CREW, Hair.LONG, Hair.PONYTAIL, Hair.LONG},
                {Hair.SIDE_PART, Hair.CREW, Hair.CURLS, Hair.BUN, Hair.BOB, Hair.LONG}
        };
        Person[] people = new Person[6];
        for (int i = 0; i < 6; i++) {
            int skin = skins[(i * 5 + era * 3) % skins.length];
            int hair = hairs[(i * 7 + era) % hairs.length];
            boolean slim = i >= 3;
            int eyes = new int[] {0x3A5A8A, 0x5A3A22, 0x3A6A4A, 0x2A2420}[(i + era) % 4];
            int lips = slim ? CostumeArt.shade(0xC8505A, (era % 3) * 0.08) : CostumeArt.shade(skin, -0.18);
            people[i] = new Person(skin, hair, styles[era][i], eyes, lips, slim);
        }
        return people;
    }

    // ---------------------------------------------------------------- whole skins and outfits

    static BufferedImage resident(Person person, Outfit outfit) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        paintHead(image, person);
        for (CostumeArt.Part part : CostumeArt.Part.values()) {
            for (boolean outer : new boolean[] {false, true}) {
                paint(image, part, outer, person.slim(), (side, x, y, w) -> {
                    int cloth = garment(outfit, part, outer, side, x, y, w, person.slim());
                    if (cloth != 0 || outer) {
                        return cloth;
                    }
                    return bare(person, part, side, x, y);
                });
            }
        }
        return image;
    }

    static BufferedImage outfit(Outfit outfit, boolean slim) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (CostumeArt.Part part : CostumeArt.Part.values()) {
            for (boolean outer : new boolean[] {false, true}) {
                paint(image, part, outer, slim, (side, x, y, w) -> garment(outfit, part, outer, side, x, y, w, slim));
            }
        }
        return image;
    }

    /**
     * A person seen from the front and the back, head included, for checking the art.
     */
    static BufferedImage preview(BufferedImage skin, boolean slim, int scale) {
        int arm = slim ? 3 : 4;
        int figure = 8 + arm * 2;
        int gap = 4;
        int k = DENSITY * scale;
        BufferedImage sheet = new BufferedImage((figure * 2 + gap * 3) * k, (32 + gap * 2) * k, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = sheet.createGraphics();
        g.setColor(new java.awt.Color(0x6B7A8F));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int view = 0; view < 2; view++) {
            boolean back = view == 1;
            int ox = gap + view * (figure + gap);
            int oy = gap;
            CostumeArt.Side side = back ? CostumeArt.Side.BACK : CostumeArt.Side.FRONT;
            for (boolean outer : new boolean[] {false, true}) {
                CostumeArt.Box head = new CostumeArt.Box(outer ? 32 : 0, 0, 8, 8, 8);
                blit(g, skin, head.face(side), ox + arm, oy, k);
                blit(g, skin, CostumeArt.box(back ? CostumeArt.Part.LEFT_ARM : CostumeArt.Part.RIGHT_ARM, outer, slim).face(side), ox, oy + 8, k);
                blit(g, skin, CostumeArt.box(CostumeArt.Part.BODY, outer, slim).face(side), ox + arm, oy + 8, k);
                blit(g, skin, CostumeArt.box(back ? CostumeArt.Part.RIGHT_ARM : CostumeArt.Part.LEFT_ARM, outer, slim).face(side), ox + arm + 8, oy + 8, k);
                blit(g, skin, CostumeArt.box(back ? CostumeArt.Part.LEFT_LEG : CostumeArt.Part.RIGHT_LEG, outer, slim).face(side), ox + arm, oy + 20, k);
                blit(g, skin, CostumeArt.box(back ? CostumeArt.Part.RIGHT_LEG : CostumeArt.Part.LEFT_LEG, outer, slim).face(side), ox + arm + 4, oy + 20, k);
            }
        }
        g.dispose();
        return sheet;
    }

    private static void blit(java.awt.Graphics2D g, BufferedImage source, int[] face, int x, int y, int k) {
        g.drawImage(source, x * k, y * k, (x + face[2]) * k, (y + face[3]) * k,
                face[0] * DENSITY, face[1] * DENSITY, (face[0] + face[2]) * DENSITY, (face[1] + face[3]) * DENSITY, null);
    }

    @FunctionalInterface
    interface Painter {
        /** @return ARGB, or 0 to leave the texel clear */
        int paint(CostumeArt.Side side, double x, double y, int w);
    }

    private static void paint(BufferedImage image, CostumeArt.Part part, boolean outer, boolean slim, Painter painter) {
        for (CostumeArt.Side side : CostumeArt.Side.values()) {
            int[] f = CostumeArt.box(part, outer, slim).face(side);
            for (int ty = 0; ty < f[3] * DENSITY; ty++) {
                for (int tx = 0; tx < f[2] * DENSITY; tx++) {
                    double x = (tx + 0.5) / DENSITY;
                    double y = (ty + 0.5) / DENSITY;
                    int argb = painter.paint(side, x, y, f[2]);
                    if (argb != 0) {
                        int px = f[0] * DENSITY + tx;
                        int py = f[1] * DENSITY + ty;
                        image.setRGB(px, py, weave(argb, px, py));
                    }
                }
            }
        }
    }

    /** A faint weave on cloth, so flat color reads as fabric. */
    private static int weave(int argb, int px, int py) {
        double grain = (CostumeArt.noise(px, py) - 0.5) * 0.05 + ((px + py) % 2 == 0 ? 0.012 : -0.012);
        return (argb & 0xFF000000) | (CostumeArt.shade(argb & 0xFFFFFF, grain) & 0xFFFFFF);
    }

    // ---------------------------------------------------------------- people

    /** Skin showing where clothes don't cover: hands, arms below short sleeves, legs below skirts. */
    private static int bare(Person person, CostumeArt.Part part, CostumeArt.Side side, double x, double y) {
        double shade = side == CostumeArt.Side.FRONT ? 0.0 : side == CostumeArt.Side.BACK ? -0.06 : -0.03;
        return CostumeArt.opaque(CostumeArt.shade(person.skin(), shade - y * 0.004));
    }

    private static void paintHead(BufferedImage image, Person p) {
        // the head: 8x8x8 at (0, 0) in the skin layout; the hat layer at (32, 0)
        CostumeArt.Box head = new CostumeArt.Box(0, 0, 8, 8, 8);
        CostumeArt.Box hat = new CostumeArt.Box(32, 0, 8, 8, 8);
        for (CostumeArt.Side side : CostumeArt.Side.values()) {
            int[] f = head.face(side);
            int[] h = hat.face(side);
            for (int ty = 0; ty < 8 * DENSITY; ty++) {
                for (int tx = 0; tx < 8 * DENSITY; tx++) {
                    double x = (tx + 0.5) / DENSITY;
                    double y = (ty + 0.5) / DENSITY;
                    image.setRGB(f[0] * DENSITY + tx, f[1] * DENSITY + ty, headPixel(p, side, x, y));
                    int over = hatPixel(p, side, x, y);
                    if (over != 0) {
                        image.setRGB(h[0] * DENSITY + tx, h[1] * DENSITY + ty, over);
                    }
                }
            }
        }
    }

    private static int headPixel(Person p, CostumeArt.Side side, double x, double y) {
        int skin = CostumeArt.shade(p.skin(), side == CostumeArt.Side.FRONT ? 0.0 : -0.05);
        int hair = hairShade(p.hairColor(), x, y);
        boolean longHair = p.hair() == Hair.LONG || p.hair() == Hair.BOB || p.hair() == Hair.SHAG || p.hair() == Hair.AFRO;
        switch (side) {
            case TOP -> {
                return CostumeArt.opaque(hair);
            }
            case BOTTOM -> {
                return CostumeArt.opaque(CostumeArt.shade(skin, -0.1));
            }
            case BACK -> {
                double reach = longHair ? 8.0 : p.hair() == Hair.CREW ? 4.5 : 5.5;
                return CostumeArt.opaque(y < reach ? hair : CostumeArt.shade(skin, -0.08));
            }
            case LEFT, RIGHT -> {
                // the side of the head: hair over the top and down behind the ear
                boolean front = side == CostumeArt.Side.RIGHT ? x > 6.0 : x < 2.0;
                double reach = longHair ? (front ? 4.0 : 8.0) : p.hair() == Hair.CREW ? 2.0 : 3.0;
                if (y < reach || !front && y < reach + 1.5) {
                    return CostumeArt.opaque(hair);
                }
                boolean ear = y > 3.5 && y < 5.5 && Math.abs(x - 4.0) < 1.0;
                return CostumeArt.opaque(ear ? CostumeArt.shade(skin, -0.12) : skin);
            }
            default -> {
                return CostumeArt.opaque(face(p, x, y, skin, hair));
            }
        }
    }

    private static int face(Person p, double x, double y, int skin, int hair) {
        // the hairline across the forehead
        double line = switch (p.hair()) {
            case CREW -> 1.0;
            case SIDE_PART -> x < 2.5 ? 2.0 : 1.5;
            case BOB, SHAG -> 2.5 - (x > 4.0 ? 0.5 : 0.0);
            case AFRO, CURLS -> 2.0;
            case LONG -> Math.abs(x - 4.0) < 0.6 ? 1.0 : 2.0;
            default -> 1.5;
        };
        if (y < line) {
            return hair;
        }
        boolean frame = (p.hair() == Hair.LONG || p.hair() == Hair.BOB || p.hair() == Hair.SHAG) && (x < 0.75 || x > 7.25) && y < 6.0;
        if (frame) {
            return hair;
        }
        // brows
        if (y >= 3.0 && y < 3.5 && (x >= 1.5 && x < 3.0 || x >= 5.0 && x < 6.5)) {
            return CostumeArt.shade(p.hairColor(), -0.1);
        }
        // eyes: a white, an iris and a glint
        if (y >= 4.0 && y < 5.0) {
            for (double ex : new double[] {1.5, 5.0}) {
                if (x >= ex && x < ex + 1.5) {
                    boolean inner = ex < 4.0 ? x >= ex + 0.5 : x < ex + 1.0;
                    if (!inner) {
                        return 0xF2EEEA;
                    }
                    return y < 4.5 && (ex < 4.0 ? x < ex + 1.0 : x >= ex + 0.5) ? CostumeArt.shade(p.eyes(), 0.25) : p.eyes();
                }
            }
        }
        // a hint of nose, cheeks and a mouth
        if (y >= 5.0 && y < 5.5 && x >= 3.5 && x < 4.5) {
            return CostumeArt.shade(skin, -0.1);
        }
        if (y >= 6.0 && y < 6.5 && x >= 3.0 && x < 5.0) {
            return p.lips();
        }
        if (y >= 5.0 && y < 6.0 && (x < 1.5 || x > 6.5) && p.slim()) {
            return CostumeArt.gradient(skin, 0xE08080, 0.15);
        }
        return CostumeArt.shade(skin, (y - 4.0) * -0.006);
    }

    private static int hatPixel(Person p, CostumeArt.Side side, double x, double y) {
        int hair = hairShade(p.hairColor(), x + 13, y + 7);
        switch (p.hair()) {
            case AFRO -> {
                // a full halo of curls, open over the face
                if (side == CostumeArt.Side.FRONT) {
                    return y < 2.5 || x < 1.0 || x > 7.0 ? curl(hair, x, y) : 0;
                }
                return side == CostumeArt.Side.BOTTOM ? 0 : curl(hair, x, y);
            }
            case BUN -> {
                // hair swept up into a bun high at the back
                if (side == CostumeArt.Side.TOP) {
                    return Math.hypot(x - 4.0, y - 5.5) < 2.0 ? CostumeArt.opaque(CostumeArt.shade(hair, 0.06)) : 0;
                }
                if (side == CostumeArt.Side.BACK) {
                    return Math.hypot(x - 4.0, y - 1.5) < 1.8 ? CostumeArt.opaque(hair) : 0;
                }
                return 0;
            }
            case PONYTAIL -> {
                if (side == CostumeArt.Side.BACK) {
                    return Math.abs(x - 4.0) < 1.2 && y > 2.0 ? CostumeArt.opaque(CostumeArt.shade(hair, -y * 0.01)) : 0;
                }
                return 0;
            }
            case CURLS -> {
                // set curls: a little volume over the crown
                if (side == CostumeArt.Side.TOP) {
                    return curl(hair, x, y);
                }
                if (side == CostumeArt.Side.FRONT || side == CostumeArt.Side.BACK || side == CostumeArt.Side.LEFT || side == CostumeArt.Side.RIGHT) {
                    return y < 1.5 ? curl(hair, x, y) : 0;
                }
                return 0;
            }
            case SHAG, LONG -> {
                if (side == CostumeArt.Side.BACK || side == CostumeArt.Side.LEFT || side == CostumeArt.Side.RIGHT) {
                    boolean faceSide = side == CostumeArt.Side.RIGHT ? x > 6.0 : side == CostumeArt.Side.LEFT && x < 2.0;
                    return !faceSide && y > 5.0 ? CostumeArt.opaque(hair) : 0;
                }
                return 0;
            }
            default -> {
                return 0;
            }
        }
    }

    private static int curl(int hair, double x, double y) {
        double n = CostumeArt.noise((int) (x * 2), (int) (y * 2));
        return n < 0.12 ? 0 : CostumeArt.opaque(CostumeArt.shade(hair, (n - 0.5) * 0.25));
    }

    private static int hairShade(int hair, double x, double y) {
        double strand = Math.sin(x * 3.1 + Math.sin(y * 1.3) * 0.8) * 0.06;
        return CostumeArt.shade(hair, strand + (CostumeArt.noise((int) (x * 2) + 40, (int) (y * 2) + 9) - 0.5) * 0.08);
    }

    // ---------------------------------------------------------------- clothes

    /**
     * The color of the clothes at a point, or 0 where nothing covers it.
     */
    private static int garment(Outfit o, CostumeArt.Part part, boolean outer, CostumeArt.Side side, double x, double y, int w, boolean slim) {
        return switch (part) {
            case BODY -> outer ? torsoOver(o, side, x, y, w) : torso(o, side, x, y, w);
            case RIGHT_ARM, LEFT_ARM -> outer ? 0 : arm(o, side, x, y, w);
            case RIGHT_LEG, LEFT_LEG -> outer ? legOver(o, side, x, y) : leg(o, side, x, y, w);
        };
    }

    private static int torso(Outfit o, CostumeArt.Side side, double x, double y, int w) {
        double c = Math.abs(x - w / 2.0);
        boolean front = side == CostumeArt.Side.FRONT;
        if (side == CostumeArt.Side.TOP) {
            return CostumeArt.opaque(o.topColor());
        }
        if (side == CostumeArt.Side.BOTTOM) {
            return CostumeArt.opaque(o.top() == Top.DRESS ? o.topColor() : o.bottomColor());
        }
        // the waist down to the hips: trousers, unless a dress or skirt
        double waist = 9.0;
        if (y >= waist && o.top() != Top.DRESS) {
            if (o.extra() == Extra.BELT && y < waist + 0.5) {
                return CostumeArt.opaque(front && c < 0.75 ? 0xC8B068 : o.extraColor());
            }
            return CostumeArt.opaque(fold(o.bottomColor(), x, y));
        }
        // the neckline
        if (front && neckOpen(o.top(), c, y)) {
            if (o.extra() == Extra.PEARLS && y < neckDepth(o.top()) + 0.5 && y > neckDepth(o.top()) - 0.6) {
                return CostumeArt.opaque(((int) (x * 2)) % 2 == 0 ? o.extraColor() : CostumeArt.shade(o.extraColor(), -0.25));
            }
            return 0;
        }
        // an open jacket over it all
        if (o.jacket() != 0 && (side != CostumeArt.Side.FRONT || c > 1.5)) {
            boolean lapel = front && c < 2.3 && y < 5.0;
            return CostumeArt.opaque(lapel ? CostumeArt.shade(o.jacket(), 0.1) : fold(o.jacket(), x, y));
        }
        int base = o.topColor();
        // collars on shirts, blouses and polos
        if (front && (o.top() == Top.SHIRT || o.top() == Top.POLO || o.top() == Top.BLOUSE) && y < 1.5 && c < 2.5) {
            return CostumeArt.opaque(CostumeArt.shade(o.trim(), 0.08));
        }
        if (o.top() == Top.TURTLENECK && y < 1.0) {
            return CostumeArt.opaque(CostumeArt.shade(base, -0.06));
        }
        if (o.top() == Top.HOODIE) {
            if (front && y > 6.0 && y < 8.5 && c < 2.5) {
                return CostumeArt.opaque(CostumeArt.shade(base, -0.1));
            }
            if (side == CostumeArt.Side.BACK && y < 2.5) {
                return CostumeArt.opaque(CostumeArt.shade(base, -0.12));
            }
        }
        if (o.extra() == Extra.TIE && front && c < 0.6 && y >= 1.0 && y < 7.5) {
            return CostumeArt.opaque(y < 1.6 ? CostumeArt.shade(o.extraColor(), 0.15) : o.extraColor());
        }
        if (o.extra() == Extra.APRON && front && y > 4.0 && c < 3.0) {
            return CostumeArt.opaque(y < 4.5 ? CostumeArt.shade(o.extraColor(), -0.1) : o.extraColor());
        }
        if (o.extra() == Extra.SCARF && y < 2.0) {
            return CostumeArt.opaque(o.extraColor());
        }
        if (o.extra() == Extra.BELT && o.top() == Top.DRESS && y >= 7.0 && y < 7.5) {
            return CostumeArt.opaque(o.extraColor());
        }
        if (front && (o.top() == Top.SHIRT || o.top() == Top.BLOUSE) && c < 0.25 && ((int) (y * 2)) % 3 == 0) {
            return CostumeArt.opaque(CostumeArt.shade(base, -0.25));
        }
        return CostumeArt.opaque(pattern(o, fold(base, x, y), x, y));
    }

    private static boolean neckOpen(Top top, double c, double y) {
        return switch (top) {
            case TEE, SWEATER, HOODIE -> y < 0.75 && c < 1.5;
            case DRESS, BLOUSE -> y < 1.5 + (1.5 - c) && c < 2.0;
            case POLO, SHIRT -> y < 1.0 && c < 0.75;
            default -> false;
        };
    }

    private static double neckDepth(Top top) {
        return top == Top.DRESS || top == Top.BLOUSE ? 2.5 : 1.0;
    }

    /** Over the torso: lapels and a jacket's edges stand off the shirt; a skirt flares over the hips. */
    private static int torsoOver(Outfit o, CostumeArt.Side side, double x, double y, int w) {
        double c = Math.abs(x - w / 2.0);
        if (side == CostumeArt.Side.BOTTOM || side == CostumeArt.Side.TOP) {
            return 0;
        }
        if (o.jacket() != 0 && side == CostumeArt.Side.FRONT && c > 1.5 && c < 2.3 && y < 5.0) {
            return CostumeArt.opaque(CostumeArt.shade(o.jacket(), 0.14));
        }
        if (o.bottom() == Bottom.SKIRT && y > 10.0) {
            return CostumeArt.opaque(fold(o.top() == Top.DRESS ? o.topColor() : o.bottomColor(), x, y));
        }
        return 0;
    }

    private static int arm(Outfit o, CostumeArt.Side side, double x, double y, int w) {
        if (side == CostumeArt.Side.BOTTOM) {
            return 0;
        }
        int sleeve = o.jacket() != 0 ? o.jacket() : o.topColor();
        Sleeves sleeves = o.jacket() != 0 ? Sleeves.LONG : o.sleeves();
        double reach = switch (sleeves) {
            case NONE -> 0.0;
            case SHORT -> 4.0;
            case LONG -> 10.0;
        };
        if (side == CostumeArt.Side.TOP) {
            return sleeves == Sleeves.NONE ? 0 : CostumeArt.opaque(sleeve);
        }
        if (y >= reach) {
            return 0;
        }
        if (sleeves == Sleeves.LONG && y > reach - 0.75) {
            // a cuff
            return CostumeArt.opaque(o.jacket() != 0 ? CostumeArt.shade(o.topColor(), 0.05) : CostumeArt.shade(sleeve, -0.1));
        }
        if (sleeves == Sleeves.SHORT && y > reach - 0.5) {
            return CostumeArt.opaque(CostumeArt.shade(sleeve, -0.08));
        }
        return CostumeArt.opaque(o.jacket() != 0 ? fold(sleeve, x, y) : pattern(o, fold(sleeve, x, y), x, y));
    }

    private static int leg(Outfit o, CostumeArt.Side side, double x, double y, int w) {
        if (side == CostumeArt.Side.TOP) {
            return CostumeArt.opaque(o.top() == Top.DRESS ? o.topColor() : o.bottomColor());
        }
        // shoes
        if (y >= 10.5 || side == CostumeArt.Side.BOTTOM) {
            boolean sole = y > 11.5 || side == CostumeArt.Side.BOTTOM;
            return CostumeArt.opaque(sole ? CostumeArt.shade(o.shoes(), -0.25) : o.shoes());
        }
        return switch (o.bottom()) {
            case SKIRT -> y < 4.0 ? CostumeArt.opaque(fold(o.top() == Top.DRESS ? o.topColor() : o.bottomColor(), x, y)) : 0;
            case SHORTS -> y < 4.5 ? CostumeArt.opaque(fold(o.bottomColor(), x, y)) : 0;
            case JEANS -> {
                boolean seam = side == CostumeArt.Side.LEFT || side == CostumeArt.Side.RIGHT ? Math.abs(x - 2.0) < 0.25 : false;
                int denim = CostumeArt.shade(o.bottomColor(), (CostumeArt.noise((int) (x * 2) + 7, (int) (y * 2) + 3) - 0.5) * 0.12);
                yield CostumeArt.opaque(seam ? CostumeArt.shade(0xC89A50, -0.1) : denim);
            }
            case LEGGINGS -> CostumeArt.opaque(CostumeArt.shade(o.bottomColor(), side == CostumeArt.Side.FRONT ? 0.04 : -0.04));
            default -> {
                boolean crease = side == CostumeArt.Side.FRONT && Math.abs(x - 2.0) < 0.25 && y < 10.0;
                yield CostumeArt.opaque(crease ? CostumeArt.shade(o.bottomColor(), -0.12) : fold(o.bottomColor(), x, y));
            }
        };
    }

    /** Over the legs: a skirt's flare at the hip, or bell-bottoms flaring at the ankle. */
    private static int legOver(Outfit o, CostumeArt.Side side, double x, double y) {
        if (side == CostumeArt.Side.TOP || side == CostumeArt.Side.BOTTOM) {
            return 0;
        }
        if (o.bottom() == Bottom.SKIRT && y < 5.0) {
            int color = o.top() == Top.DRESS ? o.topColor() : o.bottomColor();
            return CostumeArt.opaque(y > 4.5 ? CostumeArt.shade(color, -0.12) : pattern(o, fold(color, x, y), x, y + 12.0));
        }
        if (o.bottom() == Bottom.FLARES && y > 7.5 && y < 10.5) {
            return CostumeArt.opaque(CostumeArt.shade(o.bottomColor(), y > 10.0 ? -0.15 : 0.0));
        }
        return 0;
    }

    private static int fold(int rgb, double x, double y) {
        return CostumeArt.shade(rgb, Math.sin(x * 1.7 + y * 0.4) * 0.035 - Math.max(0.0, y - 8.0) * 0.01);
    }

    private static int pattern(Outfit o, int rgb, double x, double y) {
        return switch (o.pattern()) {
            case NONE -> rgb;
            case STRIPES -> ((int) Math.floor(y)) % 2 == 0 ? o.patternColor() : rgb;
            case DOTS -> {
                double fx = x % 2.0 - 1.0;
                double fy = (y + (Math.floor(x / 2.0) % 2) * 1.0) % 2.0 - 1.0;
                yield fx * fx + fy * fy < 0.22 ? o.patternColor() : rgb;
            }
            case CHECKS -> (((int) Math.floor(x / 1.5)) + ((int) Math.floor(y / 1.5))) % 2 == 0 ? CostumeArt.gradient(rgb, o.patternColor(), 0.45) : rgb;
            case BLOCKS -> y > 4.0 && y < 7.0 ? o.patternColor() : rgb;
        };
    }
}
