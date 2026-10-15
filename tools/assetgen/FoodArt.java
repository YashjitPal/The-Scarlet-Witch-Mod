import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Food the furniture makes: a slice of toast, out of the toaster.
 *
 * <p>Run from the repository root: {@code java tools/assetgen/FoodArt.java}. Writes into
 * {@code common/src/main/resources/assets/scarlet}.
 */
public final class FoodArt {

    private FoodArt() {
    }

    public static void main(String[] args) throws IOException {
        Path assets = Path.of(args.length > 0 ? args[0] : "common/src/main/resources/assets/scarlet");
        AssetGen.writePng(toast(), assets.resolve("textures/item/toast.png"));
        AssetGen.writeText("""
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "scarlet:item/toast"
                  }
                }
                """, assets.resolve("models/item/toast.json"));
        AssetGen.writeText("""
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "scarlet:item/toast"
                  }
                }
                """, assets.resolve("items/toast.json"));
    }

    /** Which columns of each row the slice covers, from and to: its top domed like a loaf's, its foot a little narrower. */
    private static final int[][] SLICE = {
            null, null, {5, 10}, {3, 12}, {2, 13}, {2, 13}, {2, 13}, {2, 13}, {2, 13}, {2, 13}, {2, 13}, {2, 13}, {2, 13}, {2, 13}, {3, 12}, null
    };

    /** A slice of bread toasted golden brown, its crust darker round the edge, flecked lighter and darker where it browned. */
    static BufferedImage toast() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int crust = 0xFF6B3C16;
        int crustLit = 0xFF87521F;
        int inside = 0xFFC98B3F;
        int lighter = 0xFFDBA457;
        int darker = 0xFFA66A29;
        for (int y = 0; y < 16; y++) {
            if (SLICE[y] == null) {
                continue;
            }
            for (int x = SLICE[y][0]; x <= SLICE[y][1]; x++) {
                boolean edge = !in(x - 1, y) || !in(x + 1, y) || !in(x, y - 1) || !in(x, y + 1);
                int color;
                if (edge) {
                    // the crust, catching the light along its top and left
                    color = !in(x, y - 1) || !in(x - 1, y) ? crustLit : crust;
                } else {
                    int fleck = (x * 73856093 ^ y * 19349663) * 0x9E3779B1 >>> 27;
                    color = fleck < 4 ? lighter : fleck < 7 ? darker : inside;
                    if (!in(x - 2, y) || !in(x + 2, y) || !in(x, y - 2) || !in(x, y + 2)) {
                        // browner just inside the crust, where it toasted most
                        color = fleck < 12 ? darker : inside;
                    }
                }
                image.setRGB(x, y, color);
            }
        }
        return image;
    }

    private static boolean in(int x, int y) {
        return y >= 0 && y < 16 && SLICE[y] != null && x >= SLICE[y][0] && x <= SLICE[y][1];
    }
}
