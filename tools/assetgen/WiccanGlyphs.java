import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.io.IOException;

/** Sixteen-pixel engraved silver and lapis sigils, one silhouette per spell. */
final class WiccanGlyphs {
    public static void main(String[] args) throws IOException {
        Path assets = Path.of("common/src/main/resources/assets/scarlet/textures");
        Path spells = assets.resolve("gui/sprites/spell");
        glyph(spells, "wiccan_dreamwalk", "2,12 4,8 6,7 10,7 12,8 14,12", "5,12 8,10 11,12", "6,4 8,2 10,4 8,6 6,4");
        glyph(spells, "wiccan_telekinesis", "2,12 5,10 6,7 6,4 8,4 8,8 10,6 13,6 12,10 8,14 3,14", "10,2 12,1 14,3 12,5 10,3 10,2");
        glyph(spells, "wiccan_mind_control", "1,8 4,5 8,4 12,5 15,8 12,11 8,12 4,11 1,8", "8,6 10,8 8,10 6,8 8,6", "3,2 5,3", "11,3 13,2");
        glyph(spells, "wiccan_shockwave", "1,11 4,9 6,10 8,3 10,10 12,9 15,11", "2,13 6,12 8,13 10,12 14,13");
        glyph(spells, "starfire", "2,13 6,7 6,10 13,2 10,9 8,9 3,14", "9,4 12,4 12,7");
        glyph(spells, "arc_beam", "1,13 5,7 7,9 10,4 11,6 15,1", "3,14 6,11 8,12 13,6");
        glyph(spells, "weave", "1,10 8,6 15,10 8,14 1,10", "3,8 8,5 13,8", "4,6 8,3 12,6");
        glyph(spells, "second_sight", "1,8 4,5 8,4 12,5 15,8 12,11 8,12 4,11 1,8", "8,5 10,8 8,10 6,8 8,5");
        glyph(spells, "astral", "3,13 6,10 10,11 13,8", "5,9 5,6 8,4 11,6 11,8 8,10 5,9", "10,3 12,1 14,3");
        glyph(spells, "waygate", "3,14 3,7 5,4 8,2 11,4 13,7 13,14", "6,13 6,8 8,6 10,8 10,13");
        glyph(spells, "demiurge", "1,6 5,9 8,2 11,9 15,6 13,13 3,13 1,6", "4,11 12,11");
        glyph(spells, "fate", "2,6 5,3 8,3 10,5 6,9 8,12 11,12 14,9", "2,10 5,13 8,13 10,10 7,6 10,3 13,4");
        glyph(spells, "runic_domain", "8,1 14,6 12,13 4,13 2,6 8,1", "8,2 12,12 2,6 14,6 4,12 8,2");
        glyph(spells, "witches_road", "3,14 5,10 10,7 10,5 8,4", "7,14 9,11 13,8 12,5", "2,4 5,4 5,1", "8,2 9,1");
        glyph(spells, "dreambind", "1,8 4,5 6,5 9,8 6,11 4,11 1,8", "7,8 10,5 12,5 15,8 12,11 10,11 7,8");
        glyph(spells, "transmute", "2,6 5,3 8,6 5,9 2,6", "8,11 11,8 14,11 11,14 8,11", "9,3 13,3 13,7");
        glyph(spells, "glamour", "2,4 7,5 7,11 5,13 2,10 2,4", "8,3 14,2 14,9 11,12 9,10", "3,7 5,8", "10,6 13,5");
        glyph(spells, "aegis", "2,3 8,4 14,3 13,9 11,12 8,14 4,11 2,7 2,3", "5,6 8,7 11,6 10,10 8,12 6,10 5,6");
        for (String[] leaf : new String[][] {{"stone_leaf","transmute"},{"dream_leaf","dreambind"},{"mirror_leaf","glamour"}}) {
            BufferedImage leafIcon = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = leafIcon.createGraphics();
            g.setColor(new Color(0x272743)); g.fillRect(2,2,12,13);
            g.setColor(new Color(0x9CA9BE)); g.fillRect(3,1,10,13);
            g.setColor(new Color(0x344368)); g.fillRect(4,3,8,9);
            g.drawImage(javax.imageio.ImageIO.read(spells.resolve(leaf[1]+".png").toFile()),4,3,8,9,null);
            g.setColor(new Color(0x6B365B)); g.fillRect(10,10,2,5); g.dispose();
            AssetGen.writePng(leafIcon,assets.resolve("item/"+leaf[0]+".png"));
        }
        BufferedImage tile = new BufferedImage(16,16,BufferedImage.TYPE_INT_RGB);
        for (int y=0;y<16;y++) for (int x=0;x<16;x++) {
            int r = Math.abs(x-7)+Math.abs(y-7);
            int c = x==0 || y==0 || x==15 || y==15 ? 0x5289CA : r==6 ? 0x76BCE5 : r==2 ? 0xB9EFFF : 0x1B3358 + ((x*3+y*7)%5)*0x010203;
            tile.setRGB(x,y,c);
        }
        AssetGen.writePng(tile,assets.resolve("block/starlight_weave.png"));
    }

    private static void glyph(Path dir, String name, String... paths) throws IOException {
        var img=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=img.createGraphics();
        for(int pass=0;pass<2;pass++) for (int n=0;n<paths.length;n++) {
            String[] points=paths[n].split(" ");
            for (int i=1;i<points.length;i++) {
                String[] a=points[i-1].split(","), b=points[i].split(",");
                int dy=1-pass;
                g.setColor(new Color(pass==0?0x203C77:n==0?(i%3==0?0xE5FAFF:0x96DFFF):0x559FEA));
                g.drawLine(Integer.parseInt(a[0]),Integer.parseInt(a[1])+dy,Integer.parseInt(b[0]),Integer.parseInt(b[1])+dy);
            }
        }
        g.dispose(); AssetGen.writePng(img,dir.resolve(name+".png"));
    }
}
