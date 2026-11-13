package local.diamondfinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public final class DbTab2 {
    private static final int TEXT = -1379332;
    private static final int MUTED = -8152657;
    private static final int CYAN = -14363163;
    private static final int ROW = -15589846;
    private static final int ROW_HOVER = -14996933;
    private static final int ROW_SELECTED = -14009008;
    private static final String[] SORTS = new String[]{"Совпадения", "Редкость", "Косметика", "Уровень", "Дата рег.", "Ник"};
    private static final int NICK_SORT = 5;
    private static final CosmeticText.Rarity[] RANKS = new CosmeticText.Rarity[]{
        CosmeticText.Rarity.COMMON, CosmeticText.Rarity.RARE, CosmeticText.Rarity.EPIC, CosmeticText.Rarity.LEGENDARY, CosmeticText.Rarity.MYTHIC
    };
    private static final Map<MenuScanner.Result, int[]> CACHE = new IdentityHashMap<>();
    private static int cacheCatalogSize = -1;
    private static int sortMode;
    private static boolean sortDesc = true;
    private static boolean showBlocked;

    private DbTab2() {
    }

    private static int level(MenuScanner.Result var0) {
        try {
            return var0.level == null ? -1 : Integer.parseInt(var0.level.trim());
        } catch (Exception var2) {
            return -1;
        }
    }

    private static long dateKey(String var0) {
        if (var0 == null) {
            return 0L;
        }

        try {
            Matcher var1 = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})").matcher(var0);
            if (var1.find()) {
                int var4 = Integer.parseInt(var1.group(3));
                if (var4 < 100) {
                    var4 += 2000;
                }

                return var4 * 10000L + Integer.parseInt(var1.group(2)) * 100L + Integer.parseInt(var1.group(1));
            }

            Matcher var2 = Pattern.compile("(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})").matcher(var0);
            if (var2.find()) {
                return Integer.parseInt(var2.group(1)) * 10000L + Integer.parseInt(var2.group(2)) * 100L + Integer.parseInt(var2.group(3));
            }
        } catch (Exception var3) {
        }

        return 0L;
    }

    private static int[] counts(MenuScanner.Result var0, Catalog var1) {
        if (var1.entries.size() != cacheCatalogSize || CACHE.size() > 4000) {
            CACHE.clear();
            cacheCatalogSize = var1.entries.size();
        }

        int var2 = var0.owned == null ? 0 : var0.owned.size();
        int[] var3 = CACHE.get(var0);
        if (var3 != null && var3[5] == var2) {
            return var3;
        }

        var3 = new int[6];
        var3[5] = var2;
        if (var0.owned != null) {
            for (Object var5 : var0.owned) {
                Catalog.Entry var6 = var1.entries.get(var5);
                if (var6 != null) {
                    CosmeticText.Rarity var7 = var6.rarityValue();
                    if (var7 != null) {
                        if (var7 == CosmeticText.Rarity.COMMON) {
                            var3[0]++;
                        } else if (var7 == CosmeticText.Rarity.RARE) {
                            var3[1]++;
                        } else if (var7 == CosmeticText.Rarity.EPIC) {
                            var3[2]++;
                        } else if (var7 == CosmeticText.Rarity.LEGENDARY) {
                            var3[3]++;
                        } else if (var7 == CosmeticText.Rarity.MYTHIC) {
                            var3[4]++;
                        }
                    }
                }
            }
        }

        CACHE.put(var0, var3);
        return var3;
    }

    private static int compareRarity(MenuScanner.Result var0, MenuScanner.Result var1, Catalog var2) {
        int[] var3 = counts(var0, var2);
        int[] var4 = counts(var1, var2);

        for (int var5 = 4; var5 >= 0; var5--) {
            if (var3[var5] != var4[var5]) {
                return Integer.compare(var3[var5], var4[var5]);
            }
        }

        return 0;
    }

    private static CosmeticText.Rarity top(MenuScanner.Result var0, Catalog var1) {
        int[] var2 = counts(var0, var1);

        for (int var3 = 4; var3 >= 0; var3--) {
            if (var2[var3] > 0) {
                return RANKS[var3];
            }
        }

        return null;
    }

    private static int mix(int var0, int var1, float var2) {
        int var3 = var0 >> 16 & 0xFF;
        int var4 = var0 >> 8 & 0xFF;
        int var5 = var0 & 0xFF;
        int var6 = var1 >> 16 & 0xFF;
        int var7 = var1 >> 8 & 0xFF;
        int var8 = var1 & 0xFF;
        int var9 = (int)(var3 + (var6 - var3) * var2);
        int var10 = (int)(var4 + (var7 - var4) * var2);
        int var11 = (int)(var5 + (var8 - var5) * var2);
        return 0xFF000000 | var9 << 16 | var10 << 8 | var11;
    }

    private static List<String> effectExtras(MenuScanner.Result var0, Set<String> var1) {
        ArrayList var2 = new ArrayList();
        if (ProfileDb.effectMode() == 1 && var0.owned != null && var0.effects != null && !var0.effects.isEmpty()) {
            for (String var4 : var1) {
                if (var0.owned.contains(var4) && var0.effects.containsKey(var4) && (var0.found == null || !var0.found.contains(var4))) {
                    var2.add(var4);
                }
            }

            return var2;
        } else {
            return var2;
        }
    }

    private static int matches(MenuScanner.Result var0, Set<String> var1) {
        return var0.found.size() + effectExtras(var0, var1).size();
    }

    private static String nickQuery() {
        return DbTab.nickBox == null ? "" : DbTab.nickBox.getValue();
    }

    private static List<MenuScanner.Result> players(Catalog var0) {
        ArrayList<MenuScanner.Result> var1 = new ArrayList<>();
        Set<String> var2 = var0.selected;
        HashSet<String> var3 = new HashSet<>(ProfileDb.foundKeys());
        boolean var4 = ProfileDb.effectMode() == 1;
        String var5 = nickQuery().trim().toLowerCase(Locale.ROOT);

        for (MenuScanner.Result var7 : DiamondFinder.scanner.results) {
            if (var7.complete
                && !Blacklist.isBlocked(var7.player)
                && (
                    var5.isEmpty()
                        || var7.player.toLowerCase(Locale.ROOT).contains(var5)
                        || var7.rank != null && var7.rank.toLowerCase(Locale.ROOT).contains(var5)
                )
                && (!var2.isEmpty() ? !var7.found.isEmpty() || !effectExtras(var7, var3).isEmpty() : !var4 || var7.effects != null && !var7.effects.isEmpty())) {
                var1.add(var7);
            }
        }
        Comparator<MenuScanner.Result> var8 = switch (sortMode) {
            case 1 -> (MenuScanner.Result var1x, MenuScanner.Result var2x) -> compareRarity(var1x, var2x, var0);
            case 2 -> Comparator.comparingInt((MenuScanner.Result var0x) -> var0x.owned == null ? 0 : var0x.owned.size());
            case 3 -> Comparator.comparingInt(DbTab2::level);
            case 4 -> Comparator.comparingLong((MenuScanner.Result var0x) -> dateKey(var0x.regDate));
            case 5 -> (MenuScanner.Result var0x, MenuScanner.Result var1x) -> var0x.player.compareToIgnoreCase(var1x.player);
            default -> Comparator.comparingInt((MenuScanner.Result var1x) -> matches(var1x, var3));
        };
        if (sortDesc) {
            var8 = var8.reversed();
        }

        var1.sort(var8.thenComparing((MenuScanner.Result var0x, MenuScanner.Result var1x) -> var0x.player.compareToIgnoreCase(var1x.player)));
        return var1;
    }

    private static void pager(FinderScreen var0, GuiGraphicsExtractor var1, int var2, int var3, int var4, int var5, Runnable var6, Runnable var7) {
        String var8 = var4 + 1 + " / " + var5;
        Ui.btn(var0, var1, "<", var2, var3, 24, 22, false, var4 > 0 ? var6 : () -> {});
        var0.text(var1, var8, var2 + 32, var3 + 7, -8152657);
        Ui.btn(var0, var1, ">", var2 + 40 + var0.textWidth(var8), var3, 24, 22, false, var4 < var5 - 1 ? var7 : () -> {});
    }

    public static void draw(FinderScreen var0, GuiGraphicsExtractor var1) {
        Catalog var2 = DiamondFinder.catalog;
        int var3 = var0.left + var0.sidebar + 10;
        int var4 = var0.left + var0.panelW - 10;
        int var5 = var0.top + 52;
        int var6 = var0.top + 84;
        int var7 = var0.top + 112;
        int var8 = var0.top + var0.panelH - 74;
        int var9 = var0.top + var0.panelH - 68;
        int var10 = Math.max(170, (var4 - var3) * 40 / 100);
        int var11 = var3 + var10 + 12;
        int var12 = var4 - var11;
        byte var13 = 24;
        byte var14 = 40;
        Ui.field(var0, var1, var3, var5, var10, var0.search, "cos");
        Ui.field(var0, var1, var11, var5, var12, DbTab.nickBox, "nick");
        List var15 = var0.filtered();
        int var16 = Math.max(1, (var8 - var7) / var13);
        int var17 = Math.max(1, (var15.size() + var16 - 1) / var16);
        var0.page = Math.max(0, Math.min(var0.page, var17 - 1));
        var0.text(var1, "Косметика  ·  выбрано " + var2.selected.size(), var3, var6 + 7, -1379332);
        if (var15.isEmpty()) {
            var0.text(
                var1, var2.entries.isEmpty() ? "Каталог пока пуст — откройте список косметики на сервере" : "Ничего не найдено", var3 + 4, var7 + 6, -8152657
            );
        }

        for (int var18 = 0; var18 < var16; var18++) {
            int var19 = var0.page * var16 + var18;
            if (var19 >= var15.size()) {
                break;
            }

            Catalog.Entry var20 = (Catalog.Entry)var15.get(var19);
            int var21 = var7 + var18 * var13;
            boolean var22 = var2.selected.contains(var20.key);
            byte var23 = 50;
            boolean var24 = var0.inside(var3, var21, var10 - var23 - 4, var13 - 2);
            var1.fill(var3, var21, var3 + var10, var21 + var13 - 2, var22 ? -14009008 : (var24 ? -14996933 : -15589846));
            CosmeticText.Rarity var25 = var20.rarityValue();
            if (var25 != null) {
                var1.fill(var3, var21, var3 + 3, var21 + var13 - 2, var25.color);
            }

            if (var22) {
                var1.fill(var3 + 3, var21 + var13 - 3, var3 + var10, var21 + var13 - 2, -14363163);
            }

            ItemStack var26 = var20.stack;
            if (var26 != null && !var26.isEmpty()) {
                var1.item(var26, var3 + 8, var21 + 2);
            }

            var0.text(var1, var0.shorten(var20.name, var10 - var23 - 40), var3 + 30, var21 + 7, var22 ? -14363163 : -1379332);
            String var27 = var20.key;
            Ui.btn(var0, var1, "Нашёл", var3 + var10 - var23 - 2, var21 + 2, var23, var13 - 6, false, () -> ProfileDb.markFound(var27));
            DbTab.area(var0, var3, var21, var10 - var23 - 4, var13 - 2, () -> DiamondFinder.catalog.toggle(var27));
        }

        pager(var0, var1, var3, var9, var0.page, var17, () -> var0.page = Math.max(0, var0.page - 1), () -> var0.page = Math.min(var17 - 1, var0.page + 1));
        List var47 = showBlocked ? new ArrayList() : players(var2);
        int var48 = 0;

        for (MenuScanner.Result var51 : DiamondFinder.scanner.results) {
            if (var51.complete && !Blacklist.isBlocked(var51.player)) {
                var48++;
            }
        }

        float var50 = Math.min(1.0F, Math.max(0.72F, (var12 - 120.0F) / 272.0F));
        int var52 = (int)(64.0F * var50);
        int var53 = (int)(96.0F * var50);
        int var54 = (int)(104.0F * var50);
        int var55 = var4;
        var55 -= var52;
        Ui.btn(var0, var1, showBlocked ? "К базе" : "ЧС (" + Blacklist.size() + ")", var55, var6, var52, 22, showBlocked, () -> {
            showBlocked = !showBlocked;
            var0.resultsPage = 0;
        });
        if (!showBlocked) {
            int var57 = var55 - (6 + var53);
            String var58 = sortMode == 5 ? (sortDesc ? "Я–А" : "А–Я") : (sortDesc ? "По убыванию" : "По возрастанию");
            Ui.btn(var0, var1, var58, var57, var6, var53, 22, false, () -> {
                sortDesc = !sortDesc;
                var0.resultsPage = 0;
            });
            var55 = var57 - (6 + var54);
            Ui.btn(var0, var1, "Сорт: " + SORTS[sortMode], var55, var6, var54, 22, true, () -> {
                sortMode = (sortMode + 1) % SORTS.length;
                sortDesc = sortMode != 5;
                var0.resultsPage = 0;
            });
        }

        String var59;
        if (showBlocked) {
            var59 = "Чёрный список: " + Blacklist.size();
        } else {
            var59 = var2.selected.isEmpty() ? "В базе: " + var48 : "Найдено " + var47.size() + " из " + var48;
            if (!nickQuery().isBlank()) {
                var59 = var59 + "  ·  по запросу";
            }
        }

        var0.text(var1, var0.shorten(var59, Math.max(40, var55 - var11 - 10)), var11, var6 + 7, -1379332);
        if (showBlocked) {
            drawBlocked(var0, var1, var11, var12, var7, var8, var9, var4);
        } else {
            int var60 = Math.max(1, (var8 - var7) / var14);
            int var61 = Math.max(1, (var47.size() + var60 - 1) / var60);
            var0.resultsPage = Math.max(0, Math.min(var0.resultsPage, var61 - 1));
            if (var47.isEmpty()) {
                String var28 = var48 == 0
                    ? "База пуста — нажмите «Добавить в базу», чтобы просканировать игроков."
                    : (var2.selected.isEmpty() ? "Никого не найдено." : "Игроков с такой косметикой в базе нет.");
                var0.text(var1, var0.shorten(var28, var12 - 8), var11 + 4, var7 + 6, -8152657);
            }

            byte var62 = 40;
            HashSet<String> var29 = new HashSet<>(ProfileDb.foundKeys());

            for (int var30 = 0; var30 < var60; var30++) {
                int var31 = var0.resultsPage * var60 + var30;
                if (var31 >= var47.size()) {
                    break;
                }

                MenuScanner.Result var32 = (MenuScanner.Result)var47.get(var31);
                int var33 = var7 + var30 * var14;
                boolean var34 = var0.inside(var11, var33, var12, var14 - 3);
                CosmeticText.Rarity var35 = top(var32, var2);
                int var36 = var34 ? -14996933 : -15589846;
                if (var35 != null) {
                    int var37 = var35.color;
                    var1.fill(var11, var33, var11 + var12, var33 + var14 - 3, mix(var36, var37, var34 ? 0.3F : 0.2F));
                    var1.fill(var11, var33 + var14 - 4, var11 + var12, var33 + var14 - 3, mix(var36, var37, 0.55F));
                    var1.fill(var11, var33, var11 + (var34 ? 4 : 3), var33 + var14 - 3, var37);
                } else {
                    var1.fill(var11, var33, var11 + var12, var33 + var14 - 3, var36);
                    if (var34) {
                        var1.fill(var11, var33, var11 + 2, var33 + var14 - 3, -14363163);
                    }
                }

                Extra.nameText(var0, var1, var0.shorten(var32.player, 130), var11 + 10, var33 + 6, -1379332);
                ProfileInfo.draw(var0, var1, var32, var11 + 10, var33 + 6, var12 - 20 - var62 - 8);
                int var63 = var2.selected.size();
                String var38;
                if (var63 == 0) {
                    int[] var39 = counts(var32, var2);
                    var38 = "Косметики в базе: " + var39[5];
                    if (var35 != null) {
                        var38 = var38 + "  ·  лучшая: " + var35.label + " ×" + var39[var35.ordinal()];
                    }
                } else {
                    List<String> var64 = effectExtras(var32, var29);
                    ArrayList<String> var40 = new ArrayList<>();
                    if (ProfileDb.effectMode() != 1) {
                        var40.addAll(var32.found);
                    } else {
                        for (String var42 : var32.found) {
                            var40.add(var42);
                        }

                        var40.addAll(var64);
                    }

                    StringBuilder var66 = new StringBuilder(
                        ProfileDb.effectMode() == 1 ? "С эффектом " + var40.size() + ": " : "Совпало " + var40.size() + " из " + var63 + ": "
                    );
                    int var67 = 0;

                    for (String var44 : var40) {
                        Catalog.Entry var45 = var2.entries.get(var44);
                        if (var45 != null) {
                            if (var67++ > 0) {
                                var66.append(", ");
                            }

                            var66.append(var45.name);
                            Object var46 = var32.effects == null ? null : var32.effects.get(var44);
                            if (var46 != null) {
                                var66.append(" (").append(var46).append(')');
                            }

                            if (var67 >= 3) {
                                if (var40.size() > 3) {
                                    var66.append(" …");
                                }
                                break;
                            }
                        }
                    }

                    var38 = var66.toString();
                }

                var0.text(var1, var0.shorten(var38, var12 - 20), var11 + 10, var33 + 21, -8152657);
                String var65 = var32.player;
                Ui.btn(var0, var1, "В ЧС", var11 + var12 - var62 - 6, var33 + 3, var62, 16, false, () -> Blacklist.add(var65));
                DbTab.area(var0, var11, var33, var12 - var62 - 12, var14 - 3, () -> var0.detail = var32);
            }

            pager(
                var0,
                var1,
                var11,
                var9,
                var0.resultsPage,
                var61,
                () -> var0.resultsPage = Math.max(0, var0.resultsPage - 1),
                () -> var0.resultsPage = Math.min(var61 - 1, var0.resultsPage + 1)
            );
            if (var12 >= 260) {
                DbClear.draw(var0, var1, var4 + 6, var9);
            }
        }
    }

    private static void drawBlocked(FinderScreen var0, GuiGraphicsExtractor var1, int var2, int var3, int var4, int var5, int var6, int var7) {
        List var8 = Blacklist.list();
        byte var9 = 26;
        int var10 = Math.max(1, (var5 - var4) / var9);
        int var11 = Math.max(1, (var8.size() + var10 - 1) / var10);
        var0.resultsPage = Math.max(0, Math.min(var0.resultsPage, var11 - 1));
        if (var8.isEmpty()) {
            var0.text(var1, "Чёрный список пуст. Игроков можно добавить кнопкой «В ЧС» в списке базы.", var2 + 4, var4 + 6, -8152657);
        }

        byte var12 = 64;

        for (int var13 = 0; var13 < var10; var13++) {
            int var14 = var0.resultsPage * var10 + var13;
            if (var14 >= var8.size()) {
                break;
            }

            String var15 = (String)var8.get(var14);
            int var16 = var4 + var13 * var9;
            var1.fill(var2, var16, var2 + var3, var16 + var9 - 3, -15589846);
            var0.text(var1, var0.shorten(var15, var3 - var12 - 30), var2 + 10, var16 + 8, -1379332);
            Ui.btn(var0, var1, "Убрать", var2 + var3 - var12 - 6, var16 + 2, var12, var9 - 7, false, () -> Blacklist.remove(var15));
        }

        pager(
            var0,
            var1,
            var2,
            var6,
            var0.resultsPage,
            var11,
            () -> var0.resultsPage = Math.max(0, var0.resultsPage - 1),
            () -> var0.resultsPage = Math.min(var11 - 1, var0.resultsPage + 1)
        );
    }
}
