import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.stream.IntStream;

/**
 * Ray traces the mod's tiara texture as a block of voxels, one per texel, for its project icon: a dark studio, a soft
 * key light with a softbox to reflect, a scarlet rim light, a glossy black floor, glowing highlight texels and a few
 * pixel sparks, then bloom and filmic tone mapping.
 *
 * <p>Usage: java tools/icon/IconRender.java common/src/main/resources/assets/scarlet/textures/item/witch_tiara.png icon.png 1024 96
 */
public class IconRender {

    record V(double x, double y, double z) {
        V add(V o) { return new V(x + o.x, y + o.y, z + o.z); }
        V sub(V o) { return new V(x - o.x, y - o.y, z - o.z); }
        V mul(double s) { return new V(x * s, y * s, z * s); }
        V mul(V o) { return new V(x * o.x, y * o.y, z * o.z); }
        double dot(V o) { return x * o.x + y * o.y + z * o.z; }
        V cross(V o) { return new V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
        V norm() { double l = Math.sqrt(dot(this)); return new V(x / l, y / l, z / l); }
        double lum() { return 0.2126 * x + 0.7152 * y + 0.0722 * z; }
    }

    static final V BLACK = new V(0, 0, 0);

    // ---------------------------------------------------------------- the tiara, extruded DEPTH texels deep
    static final int N = 16;
    static final int DEPTH = 2;
    static final boolean[][] SOLID = new boolean[N][N];
    static final V[][] ALBEDO = new V[N][N];
    static final double[][] GLOW = new double[N][N];

    // world = P + S * R * (model - C)
    static final double S = 1.0 / 16.0;
    static final V C = new V(N / 2.0, N / 2.0, DEPTH / 2.0);
    static final V P = new V(0, 0.54, 0);
    static double[][] rotation;
    static final double FLOOR = 0.0;
    /** At an icon's size a floor's reflection reads as noise, so the crown floats in the dark instead. */
    static final boolean FLOOR_SHOWN = false;

    // ---------------------------------------------------------------- lights: center, two half-extents, color
    record Light(V center, V u, V v, V color) {}

    static final Light[] LIGHTS = {
        // key: a big warm softbox high to the left and in front
        new Light(new V(-2.4, 2.9, 2.6), new V(0.9, 0, -0.5), new V(0, 0.9, 0), new V(3.4, 3.15, 2.95)),
        // rim: scarlet, behind and to the right
        new Light(new V(2.8, 1.25, -1.3), new V(0.4, 0, 0.6), new V(0, 0.6, 0), new V(4.6, 0.55, 0.85)),
        // fill: dim and cool, low on the right
        new Light(new V(2.6, 0.7, 2.2), new V(0.4, 0, -0.4), new V(0, 0.4, 0), new V(0.22, 0.24, 0.32)),
    };

    static final double METAL = 0.6;
    static final double ROUGH = 0.16;
    static final double SHININESS = 2 / (ROUGH * ROUGH) - 2;

    static double srgbToLinear(int c) {
        double v = c / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static V lerp(V a, V b, double t) {
        return a.mul(1 - t).add(b.mul(t));
    }

    static V rot(V v) {
        double[][] r = rotation;
        return new V(r[0][0] * v.x + r[0][1] * v.y + r[0][2] * v.z, r[1][0] * v.x + r[1][1] * v.y + r[1][2] * v.z,
                r[2][0] * v.x + r[2][1] * v.y + r[2][2] * v.z);
    }

    static V rotT(V v) {
        double[][] r = rotation;
        return new V(r[0][0] * v.x + r[1][0] * v.y + r[2][0] * v.z, r[0][1] * v.x + r[1][1] * v.y + r[2][1] * v.z,
                r[0][2] * v.x + r[1][2] * v.y + r[2][2] * v.z);
    }

    static boolean solid(int i, int j, int k) {
        return i >= 0 && i < N && j >= 0 && j < N && k >= 0 && k < DEPTH && SOLID[i][j];
    }

    // ---------------------------------------------------------------- tracing
    static final class Hit {
        double t;
        V n;
        int i;
        int j;
    }

    /** The first voxel a ray meets, by an Amanatides-Woo walk of the grid, or null. */
    static Hit traceTiara(V o, V d, double limit) {
        V om = C.add(rotT(o.sub(P)).mul(1 / S));
        V dm = rotT(d).mul(1 / S);
        double[] oo = {om.x, om.y, om.z};
        double[] dd = {dm.x, dm.y, dm.z};
        double[] hi = {N, N, DEPTH};
        double t0 = 0;
        double t1 = limit;
        int axis = -1;
        for (int a = 0; a < 3; a++) {
            if (Math.abs(dd[a]) < 1e-12) {
                if (oo[a] < 0 || oo[a] > hi[a]) {
                    return null;
                }
                continue;
            }
            double ta = (0 - oo[a]) / dd[a];
            double tb = (hi[a] - oo[a]) / dd[a];
            if (ta > tb) {
                double swap = ta;
                ta = tb;
                tb = swap;
            }
            if (ta > t0) {
                t0 = ta;
                axis = a;
            }
            t1 = Math.min(t1, tb);
            if (t0 > t1) {
                return null;
            }
        }
        double start = t0 + 1e-9;
        double[] p = {oo[0] + dd[0] * start, oo[1] + dd[1] * start, oo[2] + dd[2] * start};
        int[] size = {N, N, DEPTH};
        int[] cell = new int[3];
        int[] step = new int[3];
        double[] next = new double[3];
        double[] delta = new double[3];
        for (int a = 0; a < 3; a++) {
            cell[a] = clamp((int) Math.floor(p[a]), 0, size[a] - 1);
            if (dd[a] > 0) {
                step[a] = 1;
                next[a] = start + (cell[a] + 1 - p[a]) / dd[a];
                delta[a] = 1 / dd[a];
            } else if (dd[a] < 0) {
                step[a] = -1;
                next[a] = start + (cell[a] - p[a]) / dd[a];
                delta[a] = -1 / dd[a];
            } else {
                next[a] = Double.POSITIVE_INFINITY;
                delta[a] = Double.POSITIVE_INFINITY;
            }
        }
        double enter = t0;
        while (true) {
            if (solid(cell[0], cell[1], cell[2])) {
                Hit hit = new Hit();
                hit.t = enter;
                hit.i = cell[0];
                hit.j = cell[1];
                double[] n = {0, 0, 0};
                if (axis < 0) {
                    n[2] = 1;
                } else {
                    n[axis] = -Math.signum(dd[axis]);
                }
                hit.n = rot(new V(n[0], n[1], n[2]));
                return hit;
            }
            int a = next[0] < next[1] ? (next[0] < next[2] ? 0 : 2) : (next[1] < next[2] ? 1 : 2);
            enter = next[a];
            if (enter > t1) {
                return null;
            }
            cell[a] += step[a];
            if (cell[a] < 0 || cell[a] >= size[a]) {
                return null;
            }
            next[a] += delta[a];
            axis = a;
        }
    }

    // ---------------------------------------------------------------- random numbers, per pixel
    static final class Rng {
        long s;

        Rng(long seed) {
            s = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
            if (s == 0) {
                s = 1;
            }
        }

        double next() {
            s ^= s << 13;
            s ^= s >>> 7;
            s ^= s << 17;
            return ((s >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
        }
    }

    static V jitter(V dir, double amount, Rng rng) {
        V j = new V(rng.next() - 0.5, rng.next() - 0.5, rng.next() - 0.5).mul(2 * amount);
        return dir.add(j).norm();
    }

    static V cosineHemisphere(V n, Rng rng) {
        double r1 = rng.next();
        double r2 = rng.next();
        double phi = 2 * Math.PI * r1;
        double r = Math.sqrt(r2);
        V a = Math.abs(n.x) > 0.5 ? new V(0, 1, 0) : new V(1, 0, 0);
        V t = a.cross(n).norm();
        V b = n.cross(t);
        return t.mul(r * Math.cos(phi)).add(b.mul(r * Math.sin(phi))).add(n.mul(Math.sqrt(1 - r2))).norm();
    }

    // ---------------------------------------------------------------- what lights the scene
    /** The studio as reflections see it: near black, the key's softbox and a scarlet strip behind. */
    static V environment(V d) {
        V col = new V(0.010, 0.003, 0.005);
        V key = new V(-0.55, 0.62, 0.56).norm();
        double k = d.dot(key);
        col = col.add(new V(2.6, 2.45, 2.3).mul(smooth(0.86, 0.95, k)));
        V rim = new V(0.62, 0.32, -0.71).norm();
        col = col.add(new V(4.5, 0.5, 0.8).mul(smooth(0.80, 0.93, d.dot(rim))));
        // a tall strip light to the front left, which the crown's faces turned to the camera mirror as a sheen
        V strip = new V(-0.72, 0.10, 0.69).norm();
        col = col.add(new V(0.9, 0.84, 0.8).mul(smooth(0.88, 0.97, d.dot(strip))));
        return col;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static V direct(V p, V n, V view, V diffuseColor, V specularColor, double shininess, Rng rng) {
        V sum = BLACK;
        for (Light light : LIGHTS) {
            V q = light.center().add(light.u().mul(2 * rng.next() - 1)).add(light.v().mul(2 * rng.next() - 1));
            V toLight = q.sub(p);
            double dist = Math.sqrt(toLight.dot(toLight));
            V l = toLight.mul(1 / dist);
            double ndl = n.dot(l);
            if (ndl <= 0) {
                continue;
            }
            if (traceTiara(p.add(n.mul(1e-4)), l, dist) != null) {
                continue;
            }
            V h = l.add(view).norm();
            double spec = Math.pow(Math.max(0, n.dot(h)), shininess) * (shininess + 8) / (8 * Math.PI);
            sum = sum.add(light.color().mul(diffuseColor.mul(ndl).add(specularColor.mul(spec * ndl))));
        }
        return sum;
    }

    static double occlusion(V p, V n, Rng rng, int samples, double reach) {
        int open = 0;
        for (int s = 0; s < samples; s++) {
            if (traceTiara(p.add(n.mul(1e-4)), cosineHemisphere(n, rng), reach) == null) {
                open++;
            }
        }
        return open / (double) samples;
    }

    static V radiance(V o, V d, int depth, Rng rng, V background) {
        Hit hit = traceTiara(o, d, 1e9);
        double tFloor = d.y < -1e-9 ? (FLOOR - o.y) / d.y : -1;
        if (hit != null && (tFloor < 0 || hit.t < tFloor)) {
            return shadeTiara(o.add(d.mul(hit.t)), hit, d, depth, rng);
        }
        if (FLOOR_SHOWN && tFloor > 1e-6) {
            V p = o.add(d.mul(tFloor));
            V floor = shadeFloor(p, d, depth, rng);
            double fade = Math.exp(-(p.x * p.x + p.z * p.z) / (0.62 * 0.62));
            return floor.mul(fade).add(background.mul(1 - fade));
        }
        return background;
    }

    static V shadeTiara(V p, Hit hit, V d, int depth, Rng rng) {
        V albedo = ALBEDO[hit.i][hit.j];
        V n = hit.n;
        V view = d.mul(-1);
        V f0 = lerp(new V(0.04, 0.04, 0.04), albedo, METAL);
        V col = albedo.mul(GLOW[hit.i][hit.j] + 0.025);
        col = col.add(direct(p, n, view, albedo.mul(1 - METAL), f0, SHININESS, rng));
        double ao = depth == 0 ? occlusion(p, n, rng, 2, 3 * S) : 1;
        col = col.add(albedo.mul(new V(0.05, 0.02, 0.03)).mul(ao));
        if (depth < 2) {
            V r = d.sub(n.mul(2 * d.dot(n)));
            r = jitter(r, ROUGH * 0.6, rng);
            if (r.dot(n) > 0) {
                double cos = Math.max(0, n.dot(view));
                V fresnel = f0.add(new V(1, 1, 1).sub(f0).mul(Math.pow(1 - cos, 5)));
                col = col.add(radiance(p.add(n.mul(1e-4)), r, depth + 1, rng, environment(r)).mul(fresnel).mul(ao));
            }
        }
        return col;
    }

    static V shadeFloor(V p, V d, int depth, Rng rng) {
        V n = new V(0, 1, 0);
        V view = d.mul(-1);
        V base = new V(0.006, 0.004, 0.005);
        V col = direct(p, n, view, base, new V(0.02, 0.02, 0.02), 40, rng);
        col = col.mul(depth == 0 ? occlusion(p, n, rng, 2, 0.5) : 1);
        if (depth < 1) {
            V r = d.sub(n.mul(2 * d.dot(n)));
            r = jitter(r, 0.035, rng);
            double cos = Math.max(0, n.dot(view));
            double fresnel = 0.04 + 0.96 * Math.pow(1 - cos, 5);
            col = col.add(radiance(p.add(n.mul(1e-4)), r, depth + 1, rng, environment(r)).mul(0.25 + 0.75 * fresnel));
        }
        return col;
    }

    /** The backdrop for rays from the camera: a dark studio with a faint scarlet glow behind the crown. */
    static V backdrop(double u, double v) {
        double dx = u - 0.5;
        double dy = v - 0.49;
        double r2 = dx * dx + dy * dy;
        V outer = new V(0.0035, 0.0012, 0.0018);
        V inner = new V(0.055, 0.010, 0.018);
        V halo = new V(0.20, 0.02, 0.05);
        return lerp(outer, inner, Math.exp(-r2 / 0.11)).add(halo.mul(Math.exp(-r2 / 0.018)));
    }

    // ---------------------------------------------------------------- sparks: squares of light, as the magic's pixels are
    static final double[][] SPARKS = {
        // u, v, size, r, g, b
        {0.135, 0.255, 0.030, 5.5, 0.55, 0.95},
        {0.090, 0.415, 0.016, 3.2, 0.25, 0.50},
        {0.205, 0.130, 0.014, 4.0, 2.4, 2.8},
        {0.840, 0.205, 0.026, 5.0, 0.48, 0.85},
        {0.905, 0.380, 0.014, 3.6, 2.2, 2.6},
        {0.765, 0.095, 0.012, 2.6, 0.20, 0.40},
        {0.500, 0.075, 0.016, 4.4, 0.45, 0.80},
        {0.300, 0.060, 0.010, 2.4, 0.16, 0.34},
        {0.700, 0.560, 0.012, 2.8, 0.22, 0.42},
    };

    public static void main(String[] args) throws Exception {
        BufferedImage texture = ImageIO.read(new File(args[0]));
        File out = new File(args[1]);
        int size = Integer.parseInt(args[2]);
        int samples = Integer.parseInt(args[3]);
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                int argb = texture.getRGB(i, N - 1 - j);
                SOLID[i][j] = (argb >>> 24) > 127;
                ALBEDO[i][j] = new V(srgbToLinear((argb >> 16) & 255), srgbToLinear((argb >> 8) & 255), srgbToLinear(argb & 255));
                double lum = ALBEDO[i][j].lum();
                GLOW[i][j] = lum > 0.30 ? 2.2 * (lum - 0.30) / 0.70 + 0.35 : 0;
            }
        }
        double yaw = Math.toRadians(-24);
        double pitch = Math.toRadians(4);
        double[][] ry = {{Math.cos(yaw), 0, Math.sin(yaw)}, {0, 1, 0}, {-Math.sin(yaw), 0, Math.cos(yaw)}};
        double[][] rx = {{1, 0, 0}, {0, Math.cos(pitch), -Math.sin(pitch)}, {0, Math.sin(pitch), Math.cos(pitch)}};
        rotation = new double[3][3];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                for (int k = 0; k < 3; k++) {
                    rotation[r][c] += ry[r][k] * rx[k][c];
                }
            }
        }

        V eye = new V(0.05, 0.96, 3.05);
        V target = new V(0, 0.535, 0);
        V forward = target.sub(eye).norm();
        V right = forward.cross(new V(0, 1, 0)).norm();
        V up = right.cross(forward);
        double half = Math.tan(Math.toRadians(25) / 2);

        float[] hdr = new float[size * size * 3];
        float[] subject = new float[size * size];
        int grid = (int) Math.ceil(Math.sqrt(samples));
        IntStream.range(0, size).parallel().forEach(y -> {
            for (int x = 0; x < size; x++) {
                Rng rng = new Rng((long) y * size + x + 1);
                V sum = BLACK;
                int onTiara = 0;
                for (int s = 0; s < samples; s++) {
                    double jx = ((s % grid) + rng.next()) / grid;
                    double jy = ((s / grid % grid) + rng.next()) / grid;
                    double u = (x + jx) / size;
                    double v = (y + jy) / size;
                    V dir = forward.add(right.mul((2 * u - 1) * half)).add(up.mul((1 - 2 * v) * half)).norm();
                    if (traceTiara(eye, dir, 1e9) != null) {
                        onTiara++;
                    }
                    sum = sum.add(radiance(eye, dir, 0, rng, backdrop(u, v)));
                }
                V c = sum.mul(1.0 / samples);
                int at = (y * size + x) * 3;
                hdr[at] = (float) c.x;
                hdr[at + 1] = (float) c.y;
                hdr[at + 2] = (float) c.z;
                subject[y * size + x] = onTiara / (float) samples;
            }
        });

        // sparks, behind the crown
        for (double[] spark : SPARKS) {
            int half2 = (int) Math.round(spark[2] * size / 2);
            int cx = (int) Math.round(spark[0] * size);
            int cy = (int) Math.round(spark[1] * size);
            for (int y = cy - half2; y < cy + half2; y++) {
                for (int x = cx - half2; x < cx + half2; x++) {
                    if (x < 0 || y < 0 || x >= size || y >= size) {
                        continue;
                    }
                    float free = 1 - subject[y * size + x];
                    int at = (y * size + x) * 3;
                    hdr[at] += (float) (spark[3] * free);
                    hdr[at + 1] += (float) (spark[4] * free);
                    hdr[at + 2] += (float) (spark[5] * free);
                }
            }
        }

        // bloom: what is brighter than white bleeds light, near and far
        float[] bright = new float[hdr.length];
        for (int i = 0; i < hdr.length; i++) {
            bright[i] = Math.max(0, hdr[i] - 0.85f);
        }
        float[] nearGlow = blur(bright, size, size * 0.010);
        float[] farGlow = blur(bright, size, size * 0.040);
        for (int i = 0; i < hdr.length; i++) {
            hdr[i] += 0.55f * nearGlow[i] + 0.45f * farGlow[i];
        }

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Rng dither = new Rng(7);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = (x + 0.5) / size - 0.5;
                double dy = (y + 0.5) / size - 0.47;
                double vignette = Math.max(0, 1 - 0.55 * (dx * dx + dy * dy) / 0.5);
                int at = (y * size + x) * 3;
                int rgb = 0;
                for (int c = 0; c < 3; c++) {
                    double v = aces(hdr[at + c] * 1.12 * vignette);
                    v = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
                    int b = clamp((int) Math.round(v * 255 + (dither.next() - 0.5)), 0, 255);
                    rgb = (rgb << 8) | b;
                }
                image.setRGB(x, y, rgb);
            }
        }
        ImageIO.write(image, "png", out);
    }

    static double aces(double x) {
        double v = (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14);
        return Math.max(0, Math.min(1, v));
    }

    /** A separable Gaussian blur of an RGB float image. */
    static float[] blur(float[] src, int size, double sigma) {
        int radius = (int) Math.ceil(sigma * 3);
        float[] kernel = new float[2 * radius + 1];
        float total = 0;
        for (int i = -radius; i <= radius; i++) {
            kernel[i + radius] = (float) Math.exp(-(i * i) / (2 * sigma * sigma));
            total += kernel[i + radius];
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= total;
        }
        float[] tmp = new float[src.length];
        float[] dst = new float[src.length];
        IntStream.range(0, size).parallel().forEach(y -> {
            for (int x = 0; x < size; x++) {
                for (int c = 0; c < 3; c++) {
                    float acc = 0;
                    for (int k = -radius; k <= radius; k++) {
                        int xx = clamp(x + k, 0, size - 1);
                        acc += kernel[k + radius] * src[(y * size + xx) * 3 + c];
                    }
                    tmp[(y * size + x) * 3 + c] = acc;
                }
            }
        });
        IntStream.range(0, size).parallel().forEach(y -> {
            for (int x = 0; x < size; x++) {
                for (int c = 0; c < 3; c++) {
                    float acc = 0;
                    for (int k = -radius; k <= radius; k++) {
                        int yy = clamp(y + k, 0, size - 1);
                        acc += kernel[k + radius] * tmp[(yy * size + x) * 3 + c];
                    }
                    dst[(y * size + x) * 3 + c] = acc;
                }
            }
        });
        return dst;
    }
}
