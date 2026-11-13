package local.diamondfinder;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Credit {
    private static final int FROM = 6412196;
    private static final int TO = 6818731;

    private Credit() {
    }

    public static void draw(FinderScreen var0, GuiGraphicsExtractor var1, String var2, int var3, int var4, int var5) {
        long var6 = System.currentTimeMillis();
        int var8 = var2.length();

        for (int var9 = 0; var9 < var8; var9++) {
            char var10 = var2.charAt(var9);
            if (var10 != ' ') {
                int var11 = var3 + var0.textWidth(var2.substring(0, var9));
                double var12 = Math.sin(var6 / 300.0 - var9 * 0.5);
                int var14 = var12 > 0.85 ? -1 : 0;
                double var15 = 0.5 + 0.5 * Math.sin(var6 / 700.0 - var9 * 0.28);
                var0.text(var1, String.valueOf(var10), var11, var4 + var14, mix(6412196, 6818731, (float)var15));
            }
        }
    }

    private static int mix(int var0, int var1, float var2) {
        int var3 = (int)((var0 >> 16 & 0xFF) * (1.0F - var2) + (var1 >> 16 & 0xFF) * var2 + 0.5F);
        int var4 = (int)((var0 >> 8 & 0xFF) * (1.0F - var2) + (var1 >> 8 & 0xFF) * var2 + 0.5F);
        int var5 = (int)((var0 & 0xFF) * (1.0F - var2) + (var1 & 0xFF) * var2 + 0.5F);
        return 0xFF000000 | var3 << 16 | var4 << 8 | var5;
    }
}
