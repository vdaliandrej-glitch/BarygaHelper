package local.diamondfinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class DbTab {
    static final int TEXT = 0xFFEAF3FC;
    static final int MUTED = 0xFF8399AF;
    static final int CYAN = 0xFF24D5E5;
    static final int ROW = 0xFF121E2A;
    static final int ROW_HOVER = 0xFF1B2A3B;
    static final int ROW_SELECTED = 0xFF2A3D50;
    static final int LINE = 0xFF304357;
    static final String[] SORTS = new String[]{"Совпадения", "Косметика", "Уровень", "Дата регистрации", "Ник"};
    private static final Pattern DMY = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})");
    private static final Pattern YMD = Pattern.compile("(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})");
    private static boolean matchAll;
    private static int sortMode;
    private static boolean sortDesc = true;
    private static String nickText = "";
    static EditBox nickBox;

    private DbTab() {
    }

    public static void init(FinderScreen screen) {
        try {
            EditBox box = new EditBox(screen.fontRef(), 0, 0, 100, 18, Component.literal("Ник"));
            box.setBordered(false);
            box.setTextColor(TEXT);
            box.setHint(Component.literal("Ник или привилегия…"));
            box.setMaxLength(32);
            box.setValue(nickText);
            box.setResponder(value -> {
                nickText = value == null ? "" : value;
                screen.resultsPage = 0;
            });
            screen.addBox(box);
            nickBox = box;
        } catch (Throwable e) {
            nickBox = null;
        }
    }

    public static boolean anyFocused(EditBox search) {
        return search.isFocused() || nickBox != null && nickBox.isFocused();
    }

    static void area(FinderScreen screen, int x, int y, int width, int height, Runnable action) {
        screen.areas.add(new FinderScreen.Area(x, y, width, height, action));
    }

    private static int level(MenuScanner.Result result) {
        try {
            return result.level == null ? -1 : Integer.parseInt(result.level.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static long dateKey(String date) {
        if (date == null) {
            return 0L;
        }

        try {
            Matcher m = DMY.matcher(date);
            if (m.find()) {
                int year = Integer.parseInt(m.group(3));
                if (year < 100) {
                    year += 2000;
                }

                return year * 10000L + Integer.parseInt(m.group(2)) * 100L + Integer.parseInt(m.group(1));
            }

            m = YMD.matcher(date);
            if (m.find()) {
                return Integer.parseInt(m.group(1)) * 10000L + Integer.parseInt(m.group(2)) * 100L + Integer.parseInt(m.group(3));
            }
        } catch (Exception ignored) {
        }

        return 0L;
    }

    private static boolean matches(MenuScanner.Result result, Set<String> selected) {
        if (selected.isEmpty()) {
            return true;
        }

        if (!matchAll) {
            return !result.found.isEmpty();
        }

        for (String key : selected) {
            if (!ProfileDb.has(result, key)) {
                return false;
            }
        }

        return true;
    }

    private static List<MenuScanner.Result> players(Catalog catalog) {
        List<MenuScanner.Result> players = new ArrayList<>();
        Set<String> selected = catalog.selected;
        String query = nickText.trim().toLowerCase(Locale.ROOT);

        for (MenuScanner.Result result : DiamondFinder.scanner.results) {
            if (!result.complete) {
                continue;
            }

            boolean nameMatches = query.isEmpty()
                || result.player.toLowerCase(Locale.ROOT).contains(query)
                || result.rank != null && result.rank.toLowerCase(Locale.ROOT).contains(query);
            if (nameMatches && matches(result, selected)) {
                players.add(result);
            }
        }

        Comparator<MenuScanner.Result> order = switch (sortMode) {
            case 1 -> Comparator.comparingInt((MenuScanner.Result r) -> r.owned == null ? 0 : r.owned.size());
            case 2 -> Comparator.comparingInt(DbTab::level);
            case 3 -> Comparator.comparingLong((MenuScanner.Result r) -> dateKey(r.regDate));
            case 4 -> (MenuScanner.Result a, MenuScanner.Result b) -> b.player.compareToIgnoreCase(a.player);
            default -> Comparator.comparingInt((MenuScanner.Result r) -> r.found.size());
        };
        if (sortDesc) {
            order = order.reversed();
        }

        players.sort(order.thenComparing((MenuScanner.Result a, MenuScanner.Result b) -> a.player.compareToIgnoreCase(b.player)));
        return players;
    }

    private static void pager(FinderScreen screen, GuiGraphicsExtractor g, int x, int y, int page, int pages, Runnable prev, Runnable next) {
        String text = page + 1 + " / " + pages;
        Ui.btn(screen, g, "<", x, y, 24, 22, false, page > 0 ? prev : () -> {});
        screen.text(g, text, x + 32, y + 7, MUTED);
        Ui.btn(screen, g, ">", x + 40 + screen.textWidth(text), y, 24, 22, false, page < pages - 1 ? next : () -> {});
    }

    public static void draw(FinderScreen screen, GuiGraphicsExtractor g) {
        DbTab2.draw(screen, g);
    }
}
