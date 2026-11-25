import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Native-resolution inventory glyphs. Shared spells keep their familiar outline in the brooch's palette. */
final class AgathaSpellArt {
    private static final int[] VIOLET = {0x352039,0x653488,0x9954CA,0xCD8AEE,0xEBC5FF,0xFFF1DC};
    private static final String[] SIGILS = {"violet_blast","siphon","binding","witch_rune","malediction","double","nightmare","familiar","witch_sight"};
    public static void main(String[] args) throws IOException { generate(Path.of("common/src/main/resources/assets/scarlet")); }

    static void generate(Path assets) throws IOException {
        Path icons = assets.resolve("textures/gui/sprites/spell");
        for (String name : SIGILS) AssetGen.writePng(icon(name), icons.resolve("agatha_" + name + ".png"));
        for (String name : new String[]{"telekinesis","aegis","transmute","glamour","mind_control","dreambind","dreamwalk",
                "starfire","arc_beam","shockwave","weave","red_mist","chaos_shield"}) {
            BufferedImage im = ImageIO.read(icons.resolve(name + ".png").toFile());
            for (int y=0;y<im.getHeight();y++) for(int x=0;x<im.getWidth();x++) {
                int pixel=im.getRGB(x,y), peak=Math.max(pixel>>16&255,Math.max(pixel>>8&255,pixel&255));
                int luma=(peak*2+(pixel>>8&255)+(pixel>>16&255))/4;
                im.setRGB(x,y,(pixel&0xff000000)|VIOLET[Math.min(5,luma*6/256)]);
            }
            AssetGen.writePng(im, icons.resolve("agatha_" + name + ".png"));
        }
        BufferedImage ash = new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<32;y++) for(int x=0;x<32;x++) {
            int grain=Math.floorMod(x*7+y*11+(x/3)*(y/4)*3,17);
            int v=(y/4+x/6)%4==2 ? 110+grain : 144+grain;
            ash.setRGB(x,y,new Color(v,v+1,v+3).getRGB());
        }
        AssetGen.writePng(ash,assets.resolve("textures/entity/drained_ash.png"));
    }

    private static BufferedImage icon(String name) {
        var im=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        var g=im.createGraphics();
        switch(name) {
            case "violet_blast" -> {
                shape(g,2,1,14, 5,7, 7,7, 10,2, 15,1, 13,7, 9,10, 8,12);
                path(g,4,3,12,7,7,10,7,13,3); path(g,5,8,6,10,4,12,4);
            }
            case "siphon" -> {
                path(g,3,1,2,2,6,6,9,7,13,11,13,14,9);
                path(g,4,5,1,5,5,9,8,9,10); path(g,2,10,1,9,4,12,6,13,9);
                shape(g,5,8,10,10,8,12,10,10,12);
            }
            case "binding" -> {
                path(g,4,2,8,1,6,5,2,7,2,9,4,8,6,5,9,2,8);
                path(g,2,7,13,6,11,10,7,12,7,14,9,14,11,10,15,7,13);
                path(g,5,5,10,10,5);
            }
            case "witch_rune" -> {
                path(g,2,7,1,13,4,14,10,8,14,2,11,1,5,7,1);
                path(g,4,5,10,7,5,9,9,11,8); path(g,5,8,4,8,11);
            }
            case "malediction" -> {
                path(g,3,4,14,6,11,5,8,8,5,8,2);
                path(g,4,7,12,10,10,9,8,12,5,14,6); path(g,2,5,9,2,7,2,4); path(g,5,8,4,10,2);
            }
            case "double" -> {
                shape(g,1,2,3,6,3,6,7,2,7); shape(g,2,1,8,6,8,8,13,1,13);
                shape(g,4,9,2,13,2,13,6,9,6); shape(g,3,8,7,14,7,15,12,8,12);
                path(g,5,8,2,7,6,9,7,6,13);
            }
            case "nightmare", "witch_sight" -> {
                boolean night=name.equals("nightmare");
                path(g,night?3:4,1,8,5,5,10,5,14,8,10,11,5,11,1,8);
                path(g,5,8,6,8,9); path(g,2,4,4,3,2); path(g,3,11,4,13,2);
                if(night){path(g,3,4,11,5,14);path(g,2,11,10,12,13);}
                else {path(g,3,6,13,9,13);path(g,4,7,2,8,1);}
            }
            case "familiar" -> {
                shape(g,2,2,2,6,5,10,5,14,2,13,10,9,14,6,14,2,9);
                path(g,4,3,4,5,6);path(g,4,12,4,10,6);
                path(g,5,4,8,6,9);path(g,5,11,8,9,9);path(g,3,7,11,8,11);
            }
        }
        g.dispose(); return im;
    }
    private static void path(Graphics2D g,int shade,int... xy) {
        g.setColor(new Color(VIOLET[shade]));
        for(int i=2;i<xy.length;i+=2) g.drawLine(xy[i-2],xy[i-1],xy[i],xy[i+1]);
    }
    private static void shape(Graphics2D g,int shade,int... xy) {
        int n=xy.length/2; int[] x=new int[n],y=new int[n];
        for(int i=0;i<n;i++){x[i]=xy[i*2];y[i]=xy[i*2+1];}
        g.setColor(new Color(VIOLET[shade]));g.fillPolygon(x,y,n);
    }
}
