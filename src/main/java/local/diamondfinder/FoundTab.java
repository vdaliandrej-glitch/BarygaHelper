package local.diamondfinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public final class FoundTab {
    private FoundTab() {
    }

    public static void draw(FinderScreen var0, GuiGraphicsExtractor var1) {
        Catalog var2 = DiamondFinder.catalog;
        int var3 = var0.left + var0.sidebar + 10;
        int var4 = var0.left + var0.panelW - 10;
        int var5 = var4 - var3;
        int var6 = var0.top + 52;
        int var7 = var0.top + 84;
        int var8 = var0.top + 112;
        int var9 = var0.top + var0.panelH - 74;
        byte var10 = 28;
        Ui.field(var0, var1, var3, var6, var5, var0.search, "found");
        List<String> var11 = ProfileDb.foundKeys();
        String var12 = var0.query == null ? "" : var0.query.trim().toLowerCase(Locale.ROOT);
        ArrayList<String> var13 = new ArrayList<>();

        for (String var15 : var11) {
            Catalog.Entry var16 = var2.entries.get(var15);
            String var17 = var16 != null ? var16.name : var15;
            if (var12.isEmpty() || var17.toLowerCase(Locale.ROOT).contains(var12)) {
                var13.add(var15);
            }
        }

        var0.text(var1, "Найдено: " + var11.size() + (var12.isEmpty() ? "" : "  ·  показано " + var13.size()), var3, var7 + 7, -1379332);
        String var25 = "Эти предметы скрыты из поиска и фильтров";
        var0.text(var1, var0.shorten(var25, var5 / 2), var4 - Math.min(var0.textWidth(var25), var5 / 2), var7 + 7, -8152657);
        int var26 = Math.max(1, (var9 - var8) / var10);
        int var27 = Math.max(1, (var13.size() + var26 - 1) / var26);
        var0.page = Math.max(0, Math.min(var0.page, var27 - 1));
        if (var13.isEmpty()) {
            var0.text(
                var1,
                var11.isEmpty() ? "Пока пусто. Во вкладке «База» нажмите «Нашёл» у косметики, которую вы уже нашли." : "Ничего не найдено.",
                var3 + 4,
                var8 + 6,
                -8152657
            );
        }

        for (int var28 = 0; var28 < var26; var28++) {
            int var18 = var0.page * var26 + var28;
            if (var18 >= var13.size()) {
                break;
            }

            String var19 = (String)var13.get(var18);
            Catalog.Entry var20 = var2.entries.get(var19);
            int var21 = var8 + var28 * var10;
            boolean var22 = var0.inside(var3, var21, var5, var10 - 2);
            var1.fill(var3, var21, var4, var21 + var10 - 2, var22 ? -14996933 : -15589846);
            if (var20 != null) {
                CosmeticText.Rarity var23 = var20.rarityValue();
                if (var23 != null) {
                    var1.fill(var3, var21, var3 + 3, var21 + var10 - 2, var23.color);
                }

                ItemStack var24 = var20.stack;
                if (var24 != null && !var24.isEmpty()) {
                    var1.item(var24, var3 + 10, var21 + 4);
                }
            }

            var0.text(var1, var0.shorten(var20 != null ? var20.name : var19, var5 - 160), var3 + 34, var21 + 9, -1379332);
            if (var20 != null && var20.rarityValue() != null) {
                String var31 = var20.rarityValue().label;
                var0.text(var1, var31, var4 - 110 - var0.textWidth(var31), var21 + 9, -8152657);
            }

            Ui.btn(var0, var1, "Вернуть", var4 - 100, var21 + 2, 94, var10 - 6, false, () -> ProfileDb.unmarkFound(var19));
        }

        int var29 = var27;
        String var30 = var0.page + 1 + " / " + var27;
        Ui.btn(var0, var1, "<", var3, var0.top + var0.panelH - 68, 24, 22, false, var0.page > 0 ? () -> var0.page = Math.max(0, var0.page - 1) : () -> {});
        var0.text(var1, var30, var3 + 32, var0.top + var0.panelH - 61, -8152657);
        Ui.btn(
            var0,
            var1,
            ">",
            var3 + 40 + var0.textWidth(var30),
            var0.top + var0.panelH - 68,
            24,
            22,
            false,
            var0.page < var27 - 1 ? () -> var0.page = Math.min(var29 - 1, var0.page + 1) : () -> {}
        );
        Ui.btn(var0, var1, "Проверить инвентарь", var4 - 150, var0.top + var0.panelH - 68, 150, 22, true, () -> SelfImport.recheck());
    }
}
