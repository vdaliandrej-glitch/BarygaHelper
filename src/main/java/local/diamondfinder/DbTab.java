package local.diamondfinder;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class DbTab {
    static final int TEXT = -1379332;
    static final int MUTED = -8152657;
    static final int CYAN = -14363163;
    static final int ROW = -15589846;
    static final int ROW_HOVER = -14996933;
    static final int ROW_SELECTED = -14009008;
    static final int LINE = -13614249;
    static final String[] SORTS = new String[]{"Совпадения", "Косметика", "Уровень", "Дата регистрации", "Ник"};
    private static boolean matchAll;
    private static int sortMode;
    private static boolean sortDesc = true;
    private static String nickText = "";
    static EditBox nickBox;
    private static Constructor<?> areaCtor;

    private DbTab() {
    }

    public static void init(FinderScreen var0) {
        try {
            EditBox var1 = new EditBox(var0.fontRef(), 0, 0, 100, 18, Component.literal("Ник"));
            var1.setBordered(false);
            var1.setTextColor(-1379332);
            var1.setHint(Component.literal("Ник или привилегия…"));
            var1.setMaxLength(32);
            var1.setValue(nickText);
            var1.setResponder(var1x -> {
                nickText = var1x == null ? "" : var1x;
                var0.resultsPage = 0;
            });
            var0.addBox(var1);
            nickBox = var1;
        } catch (Throwable var2) {
            nickBox = null;
        }
    }

    public static boolean anyFocused(EditBox var0) {
        return var0.isFocused() || nickBox != null && nickBox.isFocused();
    }

    static void area(FinderScreen var0, int var1, int var2, int var3, int var4, Runnable var5) {
        try {
            if (areaCtor == null) {
                areaCtor = Class.forName("local.diamondfinder.FinderScreen$Area").getDeclaredConstructors()[0];
                areaCtor.setAccessible(true);
            }

            var0.areas.add((FinderScreen.Area)areaCtor.newInstance(var1, var2, var3, var4, var5));
        } catch (Throwable var7) {
        }
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
                int var2 = Integer.parseInt(var1.group(3));
                if (var2 < 100) {
                    var2 += 2000;
                }

                return var2 * 10000L + Integer.parseInt(var1.group(2)) * 100L + Integer.parseInt(var1.group(1));
            }

            var1 = Pattern.compile("(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})").matcher(var0);
            if (var1.find()) {
                return Integer.parseInt(var1.group(1)) * 10000L + Integer.parseInt(var1.group(2)) * 100L + Integer.parseInt(var1.group(3));
            }
        } catch (Exception var3) {
        }

        return 0L;
    }

    private static List<MenuScanner.Result> players(Catalog var0) {
        ArrayList<MenuScanner.Result> var1 = new ArrayList<>();
        Set<String> var2 = var0.selected;
        String var3 = nickText.trim().toLowerCase(Locale.ROOT);
        Iterator<MenuScanner.Result> var4 = DiamondFinder.scanner.results.iterator();

        while (true) {
            MenuScanner.Result var5;
            while (true) {
                if (!var4.hasNext()) {
                    Comparator<MenuScanner.Result> var9 = switch (sortMode) {
                        case 1 -> Comparator.comparingInt((MenuScanner.Result var0x) -> var0x.owned == null ? 0 : var0x.owned.size());
                        case 2 -> Comparator.comparingInt(DbTab::level);
                        case 3 -> Comparator.comparingLong((MenuScanner.Result var0x) -> dateKey(var0x.regDate));
                        case 4 -> (MenuScanner.Result var0x, MenuScanner.Result var1x) -> var1x.player.compareToIgnoreCase(var0x.player);
                        default -> Comparator.comparingInt((MenuScanner.Result var0x) -> var0x.found.size());
                    };
                    if (sortDesc) {
                        var9 = var9.reversed();
                    }

                    var1.sort(var9.thenComparing((MenuScanner.Result var0x, MenuScanner.Result var1x) -> var0x.player.compareToIgnoreCase(var1x.player)));
                    return var1;
                }

                var5 = (MenuScanner.Result)var4.next();
                if (var5.complete
                    && (
                        var3.isEmpty()
                            || var5.player.toLowerCase(Locale.ROOT).contains(var3)
                            || var5.rank != null && var5.rank.toLowerCase(Locale.ROOT).contains(var3)
                    )) {
                    if (var2.isEmpty()) {
                        break;
                    }

                    boolean var6 = true;
                    if (matchAll) {
                        for (String var8 : var2) {
                            if (!ProfileDb.has(var5, var8)) {
                                var6 = false;
                                break;
                            }
                        }
                    } else {
                        var6 = !var5.found.isEmpty();
                    }

                    if (var6) {
                        break;
                    }
                }
            }

            var1.add(var5);
        }
    }

    private static void pager(FinderScreen var0, GuiGraphicsExtractor var1, int var2, int var3, int var4, int var5, Runnable var6, Runnable var7) {
        String var8 = var4 + 1 + " / " + var5;
        Ui.btn(var0, var1, "<", var2, var3, 24, 22, false, var4 > 0 ? var6 : () -> {});
        var0.text(var1, var8, var2 + 32, var3 + 7, -8152657);
        Ui.btn(var0, var1, ">", var2 + 40 + var0.textWidth(var8), var3, 24, 22, false, var4 < var5 - 1 ? var7 : () -> {});
    }

    public static void draw(FinderScreen var0, GuiGraphicsExtractor var1) {
        DbTab2.draw(var0, var1);
    }
}
