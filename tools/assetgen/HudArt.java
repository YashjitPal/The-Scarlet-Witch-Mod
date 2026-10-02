import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pixel art for the casting HUD and the spell wheel: a soft glow, the wheel's slot badge and lock, and an icon for
 * every spell. Icons are composed from simple shapes, supersampled, snapped to crisp pixels and outlined in the darkest
 * palette color so they read on any background.
 */
final class HudArt {

    static final int CORE = 0xFFE6EC;
    static final int BRIGHT = 0xFF3355;
    static final int SCARLET = 0xE0143C;
    static final int CRIMSON = 0xA50D2C;
    static final int WINE = 0x5C0717;
    static final int SHADOW = 0x1C0307;

    private static final int SAMPLES = 6;

    private HudArt() {
    }

    // ---------------------------------------------------------------- sprites

    static BufferedImage glow() {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double r = Math.hypot(x + 0.5 - size / 2.0, y + 0.5 - size / 2.0) / (size / 2.0);
                double a = r >= 1 ? 0 : Math.pow(1 - r, 2.2);
                image.setRGB(x, y, argb(a, 0xFFFFFF));
            }
        }
        return image;
    }

    /**
     * A dark round badge with a white rim; the rim takes the tint, the body stays dark.
     */
    static BufferedImage slot() {
        int size = 24;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double cover = 0;
                double rim = 0;
                for (int sy = 0; sy < SAMPLES; sy++) {
                    for (int sx = 0; sx < SAMPLES; sx++) {
                        double r = Math.hypot(x + (sx + 0.5) / SAMPLES - 12, y + (sy + 0.5) / SAMPLES - 12);
                        if (r < 11.5) {
                            cover++;
                        }
                        if (r >= 10.4 && r < 11.5) {
                            rim++;
                        }
                    }
                }
                double n = SAMPLES * SAMPLES;
                if (rim / n >= 0.45) {
                    // brighter toward the top left, like light falling on a raised edge
                    double light = 0.72 + 0.28 * Math.clamp((24 - x - y) / 24.0, 0, 1);
                    image.setRGB(x, y, argb(1, gray(light)));
                } else if (cover / n >= 0.5) {
                    image.setRGB(x, y, argb(0.78, 0x241014));
                }
            }
        }
        return image;
    }

    static BufferedImage lock() {
        Canvas c = new Canvas(8);
        c.ring(4, 3.3, 1.9, 1.0, 0xFFFFFF);
        c.rect(1.2, 3.6, 6.8, 7.6, 0xFFFFFF);
        c.disc(4, 5.4, 0.7, 0x8A7A80);
        return c.outlined(SHADOW);
    }

    // ---------------------------------------------------------------- spell icons

    static Map<String, BufferedImage> spellIcons() {
        Map<String, BufferedImage> icons = new LinkedHashMap<>();
        icons.put("chaos_bolt", chaosBolt());
        icons.put("chaos_shield", chaosShield());
        icons.put("levitation", levitation());
        icons.put("telekinesis", telekinesis());
        icons.put("red_mist", redMist());
        icons.put("shockwave", shockwave());
        icons.put("mind_control", mindControl());
        icons.put("rune_trap", runeTrap());
        icons.put("hex", hex());
        return icons;
    }

    /**
     * The Hex seen from above, flat walls north and south as in the world, around a little sitcom house.
     */
    private static BufferedImage hex() {
        Canvas c = new Canvas(16);
        List<double[]> wall = polygon(8, 8, 6.6, 6, 0);
        c.outline(wall, 1.2, SCARLET);
        c.segment(wall.get(1)[0], wall.get(1)[1], wall.get(2)[0], wall.get(2)[1], 1.2, BRIGHT);
        c.segment(wall.get(2)[0], wall.get(2)[1], wall.get(3)[0], wall.get(3)[1], 1.2, BRIGHT);
        c.rect(5.8, 8.4, 10.2, 11.4, BRIGHT);
        c.segment(5.2, 8.9, 8.0, 6.1, 1.2, CORE);
        c.segment(8.0, 6.1, 10.8, 8.9, 1.2, CORE);
        c.rect(7.3, 9.6, 8.7, 11.4, WINE);
        return c.outlined(SHADOW);
    }

    private static BufferedImage chaosBolt() {
        Canvas c = new Canvas(16);
        c.segment(2.6, 13.4, 7.6, 8.4, 1.3, CRIMSON);
        c.segment(4.6, 11.6, 9.6, 6.6, 2.0, SCARLET);
        c.disc(10.6, 5.4, 2.7, BRIGHT);
        c.disc(10.9, 5.1, 1.35, CORE);
        c.disc(13.6, 2.6, 0.65, CORE);
        c.disc(6.8, 3.6, 0.6, BRIGHT);
        c.disc(13.0, 9.6, 0.6, SCARLET);
        return c.outlined(SHADOW);
    }

    private static BufferedImage chaosShield() {
        Canvas c = new Canvas(16);
        List<double[]> outer = polygon(8, 8, 6.2, 6, Math.PI / 2);
        c.outline(outer, 1.7, SCARLET);
        c.segment(outer.get(1)[0], outer.get(1)[1], outer.get(2)[0], outer.get(2)[1], 1.7, BRIGHT);
        c.segment(outer.get(2)[0], outer.get(2)[1], outer.get(3)[0], outer.get(3)[1], 1.7, BRIGHT);
        c.outline(polygon(8, 8, 3.4, 6, Math.PI / 2), 1.0, CRIMSON);
        c.disc(8, 8, 1.2, CORE);
        return c.outlined(SHADOW);
    }

    private static BufferedImage levitation() {
        Canvas c = new Canvas(16);
        int[] colors = {BRIGHT, SCARLET, CRIMSON};
        for (int i = 0; i < 3; i++) {
            double y = 3.6 + i * 3.6;
            c.segment(3.4, y + 3.0, 8, y, 1.5, colors[i]);
            c.segment(8, y, 12.6, y + 3.0, 1.5, colors[i]);
        }
        c.disc(8, 2.0, 0.7, CORE);
        return c.outlined(SHADOW);
    }

    private static BufferedImage telekinesis() {
        Canvas c = new Canvas(16);
        c.arc(8, 8, 5.7, 0.95, Math.toRadians(-20), Math.toRadians(150), CRIMSON);
        c.arc(8, 8, 5.7, 0.95, Math.toRadians(175), Math.toRadians(330), CRIMSON);
        c.disc(8, 8, 2.5, BRIGHT);
        c.disc(7.7, 7.6, 1.15, CORE);
        c.disc(8 + 5.7 * Math.cos(Math.toRadians(160)), 8 + 5.7 * Math.sin(Math.toRadians(160)), 1.2, SCARLET);
        c.disc(8 + 5.7 * Math.cos(Math.toRadians(340)), 8 + 5.7 * Math.sin(Math.toRadians(340)), 1.2, SCARLET);
        return c.outlined(SHADOW);
    }

    private static BufferedImage redMist() {
        Canvas c = new Canvas(16);
        c.arc(7.2, 8.6, 4.8, 1.6, Math.toRadians(150), Math.toRadians(420), SCARLET);
        c.arc(9.0, 7.8, 2.4, 1.3, Math.toRadians(-40), Math.toRadians(250), BRIGHT);
        c.disc(9.0, 7.8, 0.75, CORE);
        c.disc(2.6, 4.2, 0.65, CRIMSON);
        c.disc(13.4, 12.6, 0.65, CRIMSON);
        c.disc(13.2, 3.4, 0.55, SCARLET);
        return c.outlined(SHADOW);
    }

    private static BufferedImage shockwave() {
        Canvas c = new Canvas(16);
        for (double start : new double[] {-55, 125}) {
            c.arc(8, 8, 4.0, 1.25, Math.toRadians(start), Math.toRadians(start + 110), SCARLET);
            c.arc(8, 8, 6.5, 1.05, Math.toRadians(start + 8), Math.toRadians(start + 102), CRIMSON);
        }
        c.disc(8, 8, 1.8, BRIGHT);
        c.disc(8, 8, 0.85, CORE);
        return c.outlined(SHADOW);
    }

    private static BufferedImage mindControl() {
        Canvas c = new Canvas(16);
        c.lens(8, 8, 6.6, 3.4, 1.15, SCARLET);
        c.disc(8, 8, 2.6, BRIGHT);
        c.disc(8, 8, 1.15, SHADOW);
        c.disc(8.9, 7.1, 0.6, CORE);
        c.segment(8, 1.4, 8, 3.0, 0.9, CRIMSON);
        c.segment(3.4, 2.8, 4.5, 4.0, 0.9, CRIMSON);
        c.segment(12.6, 2.8, 11.5, 4.0, 0.9, CRIMSON);
        return c.outlined(SHADOW);
    }

    private static BufferedImage runeTrap() {
        Canvas c = new Canvas(16);
        c.ring(8, 8, 6.4, 1.2, SCARLET);
        List<double[]> star = polygon(8, 8, 4.9, 5, -Math.PI / 2);
        for (int i = 0; i < 5; i++) {
            double[] a = star.get(i);
            double[] b = star.get((i + 2) % 5);
            c.segment(a[0], a[1], b[0], b[1], 0.9, BRIGHT);
        }
        c.disc(8, 8, 0.8, CORE);
        return c.outlined(SHADOW);
    }

    private static List<double[]> polygon(double cx, double cy, double r, int sides, double start) {
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < sides; i++) {
            double angle = start + Math.PI * 2 * i / sides;
            points.add(new double[] {cx + Math.cos(angle) * r, cy - Math.sin(angle) * r});
        }
        return points;
    }

    // ---------------------------------------------------------------- painting

    /**
     * Shapes painted in order; each texel takes the last shape covering at least half of it.
     */
    private static final class Canvas {
        final int size;
        final int[] color;
        final boolean[] ink;

        Canvas(int size) {
            this.size = size;
            this.color = new int[size * size];
            this.ink = new boolean[size * size];
        }

        interface Shape {
            boolean contains(double x, double y);
        }

        void paint(Shape shape, int rgb) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int covered = 0;
                    for (int sy = 0; sy < SAMPLES; sy++) {
                        for (int sx = 0; sx < SAMPLES; sx++) {
                            if (shape.contains(x + (sx + 0.5) / SAMPLES, y + (sy + 0.5) / SAMPLES)) {
                                covered++;
                            }
                        }
                    }
                    if (covered * 2 >= SAMPLES * SAMPLES) {
                        color[y * size + x] = rgb;
                        ink[y * size + x] = true;
                    }
                }
            }
        }

        void disc(double cx, double cy, double r, int rgb) {
            paint((x, y) -> Math.hypot(x - cx, y - cy) <= r, rgb);
        }

        void ring(double cx, double cy, double r, double width, int rgb) {
            paint((x, y) -> Math.abs(Math.hypot(x - cx, y - cy) - r) <= width / 2, rgb);
        }

        void arc(double cx, double cy, double r, double width, double from, double to, int rgb) {
            paint((x, y) -> {
                if (Math.abs(Math.hypot(x - cx, y - cy) - r) > width / 2) {
                    return false;
                }
                double angle = Math.atan2(-(y - cy), x - cx);
                for (double a = angle - Math.PI * 4; a <= angle + Math.PI * 4; a += Math.PI * 2) {
                    if (a >= from && a <= to) {
                        return true;
                    }
                }
                return false;
            }, rgb);
        }

        void segment(double x0, double y0, double x1, double y1, double width, int rgb) {
            paint((x, y) -> distanceToSegment(x, y, x0, y0, x1, y1) <= width / 2, rgb);
        }

        void outline(List<double[]> points, double width, int rgb) {
            for (int i = 0; i < points.size(); i++) {
                double[] a = points.get(i);
                double[] b = points.get((i + 1) % points.size());
                segment(a[0], a[1], b[0], b[1], width, rgb);
            }
        }

        void rect(double x0, double y0, double x1, double y1, int rgb) {
            paint((x, y) -> x >= x0 && x <= x1 && y >= y0 && y <= y1, rgb);
        }

        /**
         * The outline of an almond (an eye): the edge of two overlapping discs.
         */
        void lens(double cx, double cy, double halfWidth, double halfHeight, double width, int rgb) {
            // the upper edge is an arc of a circle centered below the eye, the lower edge of one centered above it
            double radius = (halfWidth * halfWidth + halfHeight * halfHeight) / (2 * halfHeight);
            double offset = radius - halfHeight;
            paint((x, y) -> {
                double fromBelow = Math.hypot(x - cx, y - (cy + offset));
                double fromAbove = Math.hypot(x - cx, y - (cy - offset));
                return fromBelow <= radius && fromAbove <= radius && (radius - fromBelow <= width || radius - fromAbove <= width);
            }, rgb);
        }

        BufferedImage outlined(int outlineRgb) {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if (ink[y * size + x]) {
                        image.setRGB(x, y, argb(1, color[y * size + x]));
                    } else if (touches(x, y)) {
                        image.setRGB(x, y, argb(0.92, outlineRgb));
                    }
                }
            }
            return image;
        }

        private boolean touches(int x, int y) {
            return inkAt(x - 1, y) || inkAt(x + 1, y) || inkAt(x, y - 1) || inkAt(x, y + 1);
        }

        private boolean inkAt(int x, int y) {
            return x >= 0 && y >= 0 && x < size && y < size && ink[y * size + x];
        }
    }

    private static double distanceToSegment(double px, double py, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = dx * dx + dy * dy;
        double t = length == 0 ? 0 : Math.clamp(((px - x0) * dx + (py - y0) * dy) / length, 0, 1);
        return Math.hypot(px - (x0 + t * dx), py - (y0 + t * dy));
    }

    private static int gray(double level) {
        int v = (int) Math.round(Math.clamp(level, 0, 1) * 255);
        return (v << 16) | (v << 8) | v;
    }

    private static int argb(double alpha, int rgb) {
        int a = (int) Math.round(Math.clamp(alpha, 0, 1) * 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
