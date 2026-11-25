package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.Pixels;
import com.yashjit.scarlet.magic.AgathaMagic;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

/** The inscription stays locked to the floor grid. Only its ink and standing letters animate. */
final class AgathaCircle {
    private static final int HALF = 64;
    private static final float TEXEL = (float)AgathaMagic.RADIUS / HALF;
    private static final double TURN = 2 * Math.PI;
    private static final String[][] LETTERS = {
            {"..#..","..##.","..#.#","..##.",".##..","#.#..","..#.."},
            {"#...#",".#.#.","..#..","..#..","..##.","..#.#","..#.."},
            {"..#..",".#.#.","#...#",".#.#.","..#..","..##.","..#.."},
            {"#....","#.#..","##...","#.##.","#...#","#..#.","#...."},
            {"....#","...#.","..#..",".#.#.","#...#","..#..","..#.."},
            {".#...","..#..",".#.#.","#...#",".#.#.","..#..",".#..."}
    };
    private record Stroke(int x, int z, int length, int shade, double turn) {}
    private static final List<Stroke> FLOOR = engrave();
    private static final Pixels.Ramp INK = Pixels.Ramp.AGATHA;

    private static List<Stroke> engrave() {
        var strokes = new ArrayList<Stroke>();
        for (int z = -HALF; z < HALF; z++) {
            int start = -HALF, previous = -1;
            for (int x = -HALF; x <= HALF; x++) {
                int next = x == HALF ? -1 : ink(x + 0.5, z + 0.5);
                if (next == previous) continue;
                if (previous >= 0) strokes.add(new Stroke(start, z, x - start, previous, turn((start+x)*0.5, z+0.5)));
                start = x;
                previous = next;
            }
        }
        return List.copyOf(strokes);
    }

    private static double turn(double x, double z) {
        return (Math.atan2(x, -z) / TURN + 1) % 1;
    }

    private static int ink(double x, double z) {
        double r = Math.hypot(x,z), angle = turn(x,z) * TURN;
        double spoke = Math.rint(angle * 6 / TURN) * TURN / 6;
        double side = x*Math.cos(spoke) + z*Math.sin(spoke);
        double along = x*Math.sin(spoke) - z*Math.cos(spoke);
        // Each seal interrupts the inner track. Small lozenges alternate with the six larger cartouches.
        double bezel = Math.abs(side)*0.80 + Math.abs(along-49)*0.68;
        if (bezel < 7.2) {
            int gx = (int)Math.floor(side/1.7+2.5), gy = (int)Math.floor((49-along)/1.7+3.5);
            int letter = Math.floorMod((int)Math.round(spoke * 6 / TURN), 6);
            if (gx>=0 && gx<5 && gy>=0 && gy<7 && LETTERS[letter][gy].charAt(gx)=='#') return gy<2 ? 1 : 2;
            return bezel > 6.25 ? 4 : -1;
        }
        if (r >= 62 && r < 64) return 2;
        if (r >= 58 && r < 59.25) return 4;
        if (r >= 47.5 && r < 49) return 3;
        double tick = Math.abs(Math.sin(angle*18));
        if (r > 59.3 && r < 61.5 && tick < 0.20) return 2;
        if (Math.abs(side) + Math.abs(along-36.5) < 2.1) return 2;
        // Three interlaced pointed petals, with a small open lozenge at their common center.
        double local = Math.IEEEremainder(angle, TURN/3);
        double petal = 27*Math.max(0, Math.cos(local*1.5));
        if (r>7 && Math.abs(r-petal) < 0.95) return 3;
        if (Math.abs(Math.abs(x)+Math.abs(z)-6) < 0.8) return 2;
        return -1;
    }

    static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vector3f c, float age, float fade, int struck, float flash) {
        float written = Math.min(1, (age+1)/17);
        for (Stroke s : FLOOR) {
            if (s.turn > written) continue;
            double sweep = (age/130.0 - s.turn + 10) % 1;
            int shade = sweep < 0.035 ? Math.max(1,s.shade-1) : s.shade;
            float x = c.x+s.x*TEXEL, z = c.z+s.z*TEXEL;
            int color = Glow.withAlpha(INK.color(shade), fade);
            quad(pose,buffer,x,z,x+s.length*TEXEL,z+TEXEL,c.y,color);
        }
        for (int n=0;n<6;n++) {
            if (written < n/6F) continue;
            double a=n*TURN/6;
            float sin=(float)Math.sin(a), cos=(float)Math.cos(a);
            float lift=n==struck ? flash : 0;
            float radius=4.75F;
            float u=0.14F + 0.035F*lift;
            float baseY=c.y+0.32F + 0.35F*lift;
            for (int row=0;row<7;row++) for (int col=0;col<5;col++) {
                if (LETTERS[n][row].charAt(col)!='#') continue;
                float side=(col-2.5F)*u;
                float x=c.x+radius*sin+side*cos, z=c.z-radius*cos+side*sin;
                float y=baseY+(6-row)*u;
                int color=Glow.withAlpha(INK.color(lift>0.2F ? 1 : row<2 ? 2 : 3), fade*(0.68F+0.32F*lift));
                buffer.addVertex(pose,x,y,z).setColor(color);
                buffer.addVertex(pose,x+u*cos,y,z+u*sin).setColor(color);
                buffer.addVertex(pose,x+u*cos,y+u,z+u*sin).setColor(color);
                buffer.addVertex(pose,x,y+u,z).setColor(color);
            }
        }
    }

    static int nearest(Vector3f delta) {
        return Math.floorMod((int)Math.round(turn(delta.x,delta.z)*6),6);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer b,float x,float z,float right,float back,float y,int color) {
        b.addVertex(pose,x,y,z).setColor(color);
        b.addVertex(pose,x,y,back).setColor(color);
        b.addVertex(pose,right,y,back).setColor(color);
        b.addVertex(pose,right,y,z).setColor(color);
    }
}
