import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Three human faces and hairstyles, independent of cloth color and coven rank. */
final class CovenSkins {
    private static final int[][] SKIN = {{0xF1BB94,0xD79C79,0xA66C5A},{0xB77654,0x955B45,0x743F36},{0xDCA780,0xBC8969,0x97695B}};
    private static final int[][] HAIR = {{0x52322F,0x734639,0x936452},{0x291F2C,0x46313D,0x604453},{0x645C6B,0x958994,0xC7BAC0}};
    private static final int[][] CLOTH = {{0x193B40,0x285458,0x397174,0x79BAA9,0xD0DDAC},{0x443338,0x66494A,0x927354,0xD6B77A,0xF8E7A6}};
    private static final Path DEST = Path.of("common/src/main/resources/assets/scarlet/textures/entity/coven");

    public static void main(String[] args) throws Exception {
        for (int dye=0;dye<2;dye++) for (int tier=1;tier<=3;tier++) for (int face=0;face<3;face++) {
            BufferedImage im=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=im.createGraphics();
            head(g,face);
            clothes(g,dye,tier,face);
            g.dispose();
            String file=(dye==1?"gold_":"teal_")+tier+(face==0?"":"_"+face)+".png";
            ImageIO.write(im,"PNG",DEST.resolve(file).toFile());
        }
    }

    private static void rect(Graphics2D g,int x,int y,int w,int h,int color) {
        g.setColor(new Color(color)); g.fillRect(x,y,w,h);
    }

    private static void head(Graphics2D g,int f) {
        int[] hair=HAIR[f], skin=SKIN[f];
        rect(g,0,0,32,16,hair[0]);
        rect(g,8,8,8,8,skin[0]);
        int[] fringe = switch(f) {
            case 0 -> new int[]{4,3,3,2,1,1,2,3};
            case 1 -> new int[]{3,2,1,1,2,2,3,4};
            default -> new int[]{3,2,1,2,2,1,2,3};
        };
        for(int x=0;x<8;x++) {
            rect(g,8+x,8,1,fringe[x],hair[1]);
            if(x%3!=1) rect(g,40+x,8,1,Math.max(1,fringe[x]-1),hair[x%2+1]);
        }
        for(int x=0;x<32;x++) {
            if(x>=8 && x<16) continue;
            int shade=(x*3+x/5)%7<2?2:1;
            rect(g,x,9+x%3,1,3+x%4,hair[shade]);
            if (x%4==1) rect(g,32+x,10,1,3+x%3,hair[shade]);
        }
        for(int x=8;x<16;x+=2) rect(g,x,2+x%3,1,4,hair[1]);
        rect(g,9,11,2,1,hair[0]); rect(g,13,11,2,1,hair[0]);
        rect(g,9,12,2,1,0xEDE2CD); rect(g,13,12,2,1,0xEDE2CD);
        int iris=f==0?0x40574F:f==1?0x453133:0x53636C;
        rect(g,10,12,1,1,iris); rect(g,13,12,1,1,iris);
        rect(g,12,13,1,1,skin[1]); rect(g,11,14,2,1,skin[2]);
        rect(g,8,14,1,2,skin[1]); rect(g,15,14,1,2,skin[1]);
        if(f==0) { rect(g,8,12,1,3,hair[1]); rect(g,15,12,1,2,hair[1]); }
        rect(g,4,12,2,2,skin[1]); rect(g,18,12,2,2,skin[1]);
        rect(g,5,14,1,1,0xD0AF70); rect(g,18,14,1,1,0xD0AF70);
    }

    private static void clothes(Graphics2D g,int dye,int rank,int f) {
        int[] c=CLOTH[dye], skin=SKIN[f], hair=HAIR[f];
        rect(g,16,16,24,16,c[0]);
        rect(g,20,20,8,12,c[1]);
        rect(g,32,20,8,12,c[1]);
        // A linen neckline inside a folded mantle, a leather belt, and a small metal clasp.
        rect(g,22,20,4,3,0xD4C3A1); rect(g,23,20,2,1,skin[1]);
        for(int x=0;x<8;x++) {
            int depth=1+Math.min(x,7-x);
            rect(g,20+x,36,1,depth,c[2]); rect(g,20+x,35+depth,1,1,c[3]);
            rect(g,32+x,36,1,depth+1,c[2]); rect(g,32+x,37+depth,1,1,c[3]);
        }
        rect(g,21,38,2,1,c[4]); rect(g,22,39,1,1,c[4]);
        rect(g,16,27,24,1,0x413334); rect(g,24,27,2,1,c[4]);
        rect(g,25,28,2,4,c[2]); rect(g,26,30,1,1,c[3]);
        // Profile-specific locks on the collar and the back of the mantle.
        if(f!=0) {
            for(int row=0;row<(f==1?7:4);row++) {
                int x=20+(f==1?1+row/2%2:5+row%2);
                rect(g,x,36+row,2,1,hair[row%2+1]);
            }
            rect(g,33,36,5,3,hair[1]); rect(g,35,39,3,2,hair[2]);
        }
        for(int arm=0;arm<2;arm++) {
            int x=arm==0?40:32, y=arm==0?16:48;
            rect(g,x,y,14,16,c[1]); rect(g,x,y+11,14,1,c[3]);
            rect(g,x,y+12,14,4,skin[0]); rect(g,x,y+15,14,1,skin[1]);
            rect(g,x+4,y+13,1,2,skin[1]);
            int sx=arm==0?40:48, sy=arm==0?32:48;
            rect(g,sx,sy,14,7,c[2]);
            for(int band=0;band<rank;band++) rect(g,sx,sy+5+band*2,14,1,c[3]);
        }
        for(int leg=0;leg<2;leg++) {
            int x=leg==0?0:16,y=leg==0?16:48;
            rect(g,x,y,16,16,c[0]); rect(g,x+4,y+4,4,8,c[1]);
            rect(g,x+5+leg,y+4,1,6,c[2]); rect(g,x,y+11,16,1,c[3]);
            rect(g,x,y+12,16,4,0x403539); rect(g,x+4,y+13,4,1,0x665047);
        }
    }
}
