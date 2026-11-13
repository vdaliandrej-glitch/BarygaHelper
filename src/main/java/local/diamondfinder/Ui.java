package local.diamondfinder;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;

public final class Ui {
    static final int BACKDROP = -183428827;
    static final int PANEL = -15919584;
    static final int PANEL2 = -15720405;
    static final int CARD = -15251111;
    static final int CARD_H = -14589325;
    static final int ACT = -14996935;
    static final int ACT_H = -13811372;
    static final int LINE = -13614249;
    static final int CYAN = -14363163;
    static final int TEXT = -1379332;
    static final int MUTED = -8152657;
    private static FinderScreen last;
    private static final int[] ORDER = new int[]{2, 0, 3, 1};
    private static final String[] NAMES = new String[]{"Каталог", "Скан", "База", "Найдено"};

    private Ui() {
    }

    static void btn(FinderScreen var0, GuiGraphicsExtractor var1, String var2, int var3, int var4, int var5, int var6, boolean var7, Runnable var8) {
        boolean var9 = var0.inside(var3, var4, var5, var6);
        var1.fill(var3, var4, var3 + var5, var4 + var6, var7 ? (var9 ? -13811372 : -14996935) : (var9 ? -14589325 : -15251111));
        if (var7) {
            var1.fill(var3, var4 + var6 - 2, var3 + var5, var4 + var6, -14363163);
        }

        String var10 = var0.shorten(var2, Math.max(8, var5 - 8));
        var0.text(var1, var10, var3 + (var5 - var0.textWidth(var10)) / 2, var4 + (var6 - 8) / 2, var7 ? -14363163 : -1379332);
        DbTab.area(var0, var3, var4, var5, var6, var8);
    }

    static void field(FinderScreen var0, GuiGraphicsExtractor var1, int var2, int var3, int var4, EditBox var5, String var6) {
        boolean var7 = var5 != null && var5.isFocused();
        var1.fill(var2 - 1, var3 - 1, var2 + var4 + 1, var3 + 27, var7 ? -14363163 : -13614249);
        var1.fill(var2, var3, var2 + var4, var3 + 26, -15720405);
        if (var5 != null) {
            var5.setX(var2 + 10);
            var5.setY(var3 + 4);
            var5.setWidth(Math.max(40, var4 - 20));
            var5.setVisible(true);
        }
    }

    private static void label(FinderScreen var0, GuiGraphicsExtractor var1, String var2, int var3, int var4) {
        var0.text(var1, var2, var3, var4, -8152657);
    }

    private static void switchTab(FinderScreen var0, int var1) {
        var0.tab = var1;
        var0.page = 0;
        var0.resultsPage = 0;
        var0.detail = null;
        var0.setFocused(null);
    }

    public static void render(FinderScreen var0, GuiGraphicsExtractor var1, int var2, int var3, float var4) {
        var0.mx = var2;
        var0.my = var3;
        var0.areas.clear();
        if (var0 != last) {
            last = var0;
            var0.tab = 2;
            var0.page = 0;
            var0.resultsPage = 0;
            var0.detail = null;
        }

        Catalog var5 = DiamondFinder.catalog;
        MenuScanner var6 = DiamondFinder.scanner;
        boolean var7 = var0.panelH < 430;
        if (var0.search != null) {
            var0.search.setVisible(false);
        }

        if (DbTab.nickBox != null) {
            DbTab.nickBox.setVisible(false);
        }

        var1.fill(0, 0, var0.width, var0.height, -183428827);
        var1.fill(var0.left - 1, var0.top - 1, var0.left + var0.panelW + 1, var0.top + var0.panelH + 1, -13614249);
        var1.fill(var0.left, var0.top, var0.left + var0.panelW, var0.top + var0.panelH, -15919584);
        var1.fill(var0.left, var0.top, var0.left + var0.panelW, var0.top + 44, -15720405);
        var1.fill(var0.left, var0.top + 44, var0.left + var0.panelW, var0.top + 45, -13614249);
        var0.text(var1, "BARYGA", var0.left + 16, var0.top + 18, -14363163);
        var0.text(var1, "HELPER", var0.left + 16 + var0.textWidth("BARYGA") + 3, var0.top + 18, -1379332);
        int var8 = var0.left + var0.sidebar + 10;

        for (int var12 : ORDER) {
            String var13 = NAMES[var12] + (var12 == 3 ? " (" + ProfileDb.foundKeys().size() + ")" : "");
            int var14 = var0.textWidth(var13) + 28;
            boolean var15 = var0.tab == var12;
            boolean var16 = var0.inside(var8, var0.top + 12, var14, 33);
            var1.fill(var8, var0.top + 12, var8 + var14, var0.top + 45, var15 ? -15919584 : (var16 ? -14589325 : -15720405));
            if (var15) {
                var1.fill(var8, var0.top + 12, var8 + var14, var0.top + 14, -14363163);
            }

            var0.text(var1, var13, var8 + 14, var0.top + 24, var15 ? -1379332 : (var16 ? -1379332 : -8152657));
            int var17 = var12;
            DbTab.area(var0, var8, var0.top + 12, var14, 33, () -> switchTab(var0, var17));
            var8 += var14 + 4;
        }

        btn(var0, var1, "×", var0.left + var0.panelW - 40, var0.top + 10, 28, 26, false, var0::onClose);
        int var29 = var0.left + 8;
        int var30 = var0.sidebar - 16;
        int var31 = var0.top + 56;
        int var37 = var7 ? 19 : 24;
        int var38 = var7 ? 2 : 3;
        var1.fill(var0.left + var0.sidebar, var0.top + 45, var0.left + var0.sidebar + 1, var0.top + var0.panelH - 38, -13614249);
        if (!var7) {
            label(var0, var1, "РЕДКОСТЬ", var29 + 2, var31);
            var31 += 13;
        }

        int[] var39 = new int[CosmeticText.Rarity.values().length];
        int var40 = 0;

        for (Catalog.Entry var44 : var5.entries.values()) {
            if (!ProfileDb.isFound(var44.key)) {
                CosmeticText.Rarity var18 = var44.rarityValue();
                if (var18 != null) {
                    var39[var18.ordinal()]++;
                }

                var40++;
            }
        }

        btn(var0, var1, "Вся косметика  " + var40, var29, var31, var30, var37, var0.filter == null, () -> {
            var0.filter = null;
            var0.page = 0;
        });
        var31 += var37 + var38;

        for (CosmeticText.Rarity var19 : CosmeticText.Rarity.values()) {
            if (var19.tradable()) {
                boolean var20 = var0.filter == var19;
                int var21 = var30 - 26;
                btn(var0, var1, var19.label + "  " + var39[var19.ordinal()], var29, var31, var21, var37, var20, () -> {
                    var0.filter = var20 ? null : var19;
                    var0.page = 0;
                });
                var1.fill(var29, var31, var29 + 3, var31 + var37, var19.color);
                btn(var0, var1, "+", var29 + var21 + 2, var31, 24, var37, false, () -> new SelectRarity(var19).run());
                var31 += var37 + var38;
            }
        }

        var31 += var7 ? 4 : 8;
        if (!var7) {
            label(var0, var1, "ВЫБОР", var29 + 2, var31);
            var31 += 13;
        }

        var0.text(var1, "Выбрано: " + var5.selected.size(), var29 + 2, var31 + 2, var5.selected.isEmpty() ? -8152657 : -14363163);
        var31 += 14;
        btn(var0, var1, "Только выбранные", var29, var31, var30, var37, var0.selectedOnly, () -> {
            var0.selectedOnly = !var0.selectedOnly;
            var0.page = 0;
        });
        var31 += var37 + var38;
        btn(var0, var1, "Снять выбор", var29, var31, var30, var37, false, () -> {
            var5.selected.clear();
            var5.dirty();
        });
        var31 += var37 + var38 + (var7 ? 4 : 8);
        if (!var7) {
            label(var0, var1, "ЭФФЕКТ НА КОСМЕТИКЕ", var29 + 2, var31);
            var31 += 13;
        }

        int var43 = (var30 - 4) / 3;
        int var46 = ProfileDb.effectMode();
        String[] var48 = new String[]{"Любой", "Есть", "Нет"};

        for (int var49 = 0; var49 < 3; var49++) {
            int var51 = var49;
            btn(var0, var1, var48[var49], var29 + var49 * (var43 + 2), var31, var43, var37 - 2, var46 == var49, () -> ProfileDb.setEffectMode(var51));
        }

        boolean var50 = var0.detail == null;
        int var52 = var0.left + var0.sidebar + 10;
        int var53 = var0.left + var0.panelW - 10;
        switch (var0.tab) {
            case 0:
                field(var0, var1, var52, var0.top + 52, var53 - var52, var0.search, "cat");
                var0.drawCatalog(var1);
                break;
            case 1:
                if (var50) {
                    var0.drawResults(var1);
                } else {
                    var0.drawDetails(var1);
                }
                break;
            case 2:
                if (var50) {
                    DbTab.draw(var0, var1);
                } else {
                    var0.drawDetails(var1);
                }
                break;
            default:
                FoundTab.draw(var0, var1);
        }

        int var22 = var0.top + var0.panelH - 38;
        var1.fill(var0.left, var22, var0.left + var0.panelW, var0.top + var0.panelH, -15720405);
        var1.fill(var0.left, var22, var0.left + var0.panelW, var22 + 1, -13614249);
        Credit.draw(var0, var1, "БарыгаХелпер by Koodryaaaa", var0.left + 16, var22 + 8, 0);
        int var23 = 0;

        for (MenuScanner.Result var25 : var6.results) {
            if (var25.complete) {
                var23++;
            }
        }

        var0.text(var1, "Каталог: " + var5.entries.size() + "  ·  в базе: " + var23, var0.left + 16, var22 + 22, -8152657);
        boolean var54 = var6.running();
        short var55 = 156;
        int var26 = var0.left + var0.panelW - 16 - var55;
        btn(var0, var1, var54 ? "Остановить" : "Добавить в базу", var26, var22 + 7, var55, 24, var54, () -> {
            if (var6.running()) {
                var6.stop("Остановлено");
            } else {
                var6.start();
            }
        });
        String var27 = var5.error != null && !var5.error.isEmpty() ? var5.error : var6.message;
        int var28 = var0.left + 16 + Math.max(var0.textWidth("БарыгаХелпер by Koodryaaaa"), var0.textWidth("Каталог: 000  ·  в базе: 0000")) + 24;
        if (var27 != null && !var27.isEmpty()) {
            var0.text(
                var1, var0.shorten(var27, Math.max(40, var26 - var28 - 12)), var28, var22 + 15, var5.error != null && !var5.error.isEmpty() ? -38037 : -8152657
            );
        }
    }
}
