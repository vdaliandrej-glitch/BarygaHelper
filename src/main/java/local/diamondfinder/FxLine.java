package local.diamondfinder;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class FxLine {
    private FxLine() {
    }

    public static void draw(FinderScreen var0, GuiGraphicsExtractor var1, MenuScanner.Result var2, String var3, int var4, int var5, int var6) {
        if (var2 != null && var2.effects != null && var3 != null) {
            Object var7 = var2.effects.get(var3);
            if (var7 != null) {
                String var8 = var7.toString().trim();
                if (!var8.isEmpty()) {
                    var0.text(var1, var0.shorten("Эффект: " + var8, var6 - 47), var4 + 36, var5 + 28, -14494738);
                }
            }
        }
    }
}
