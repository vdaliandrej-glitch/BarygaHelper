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
    private static final int TEXT = 0xFFEAF3FC;
    private static final int MUTED = 0xFF8399AF;
    private static final int CYAN = 0xFF24D5E5;
    private static final int ROW = 0xFF121E2A;
    private static final int ROW_HOVER = 0xFF1B2A3B;
    private static final int ROW_SELECTED = 0xFF2A3D50;
    private static final String[] SORTS = new String[]{"Совпадения", "Редкость", "Косметика", "Уровень", "Дата рег.", "Ник"};
    private static final int NICK_SORT = 5;
    private static final int COSMETIC_ROW = 24;
    private static final int PLAYER_ROW = 40;
    private static final int BLOCKED_ROW = 26;
    private static final Pattern DMY = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})");
    private static final Pattern YMD = Pattern.compile("(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})");
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
            Matcher dmy = DMY.matcher(date);
            if (dmy.find()) {
                int year = Integer.parseInt(dmy.group(3));
                if (year < 100) {
                    year += 2000;
                }

                return year * 10000L + Integer.parseInt(dmy.group(2)) * 100L + Integer.parseInt(dmy.group(1));
            }

            Matcher ymd = YMD.matcher(date);
            if (ymd.find()) {
                return Integer.parseInt(ymd.group(1)) * 10000L + Integer.parseInt(ymd.group(2)) * 100L + Integer.parseInt(ymd.group(3));
            }
        } catch (Exception ignored) {
        }

        return 0L;
    }

    /** Количество косметики по редкостям: [обычная, редкая, эпическая, легендарная, мифическая, всего]. */
    private static int[] counts(MenuScanner.Result result, Catalog catalog) {
        if (catalog.entries.size() != cacheCatalogSize || CACHE.size() > 4000) {
            CACHE.clear();
            cacheCatalogSize = catalog.entries.size();
        }

        int owned = result.owned == null ? 0 : result.owned.size();
        int[] counts = CACHE.get(result);
        if (counts != null && counts[5] == owned) {
            return counts;
        }

        counts = new int[6];
        counts[5] = owned;
        if (result.owned != null) {
            for (Object key : result.owned) {
                Catalog.Entry entry = catalog.entries.get(key);
                if (entry == null) {
                    continue;
                }

                CosmeticText.Rarity rarity = entry.rarityValue();
                if (rarity == CosmeticText.Rarity.COMMON) {
                    counts[0]++;
                } else if (rarity == CosmeticText.Rarity.RARE) {
                    counts[1]++;
                } else if (rarity == CosmeticText.Rarity.EPIC) {
                    counts[2]++;
                } else if (rarity == CosmeticText.Rarity.LEGENDARY) {
                    counts[3]++;
                } else if (rarity == CosmeticText.Rarity.MYTHIC) {
                    counts[4]++;
                }
            }
        }

        CACHE.put(result, counts);
        return counts;
    }

    private static int compareRarity(MenuScanner.Result a, MenuScanner.Result b, Catalog catalog) {
        int[] countsA = counts(a, catalog);
        int[] countsB = counts(b, catalog);

        for (int i = 4; i >= 0; i--) {
            if (countsA[i] != countsB[i]) {
                return Integer.compare(countsA[i], countsB[i]);
            }
        }

        return 0;
    }

    private static CosmeticText.Rarity top(MenuScanner.Result result, Catalog catalog) {
        int[] counts = counts(result, catalog);

        for (int i = 4; i >= 0; i--) {
            if (counts[i] > 0) {
                return RANKS[i];
            }
        }

        return null;
    }

    private static int mix(int from, int to, float t) {
        int r1 = from >> 16 & 0xFF;
        int g1 = from >> 8 & 0xFF;
        int b1 = from & 0xFF;
        int r2 = to >> 16 & 0xFF;
        int g2 = to >> 8 & 0xFF;
        int b2 = to & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static List<String> effectExtras(MenuScanner.Result result, Set<String> foundKeys) {
        List<String> extras = new ArrayList<>();
        if (ProfileDb.effectMode() == 1 && result.owned != null && result.effects != null && !result.effects.isEmpty()) {
            for (String key : foundKeys) {
                if (result.owned.contains(key) && result.effects.containsKey(key) && (result.found == null || !result.found.contains(key))) {
                    extras.add(key);
                }
            }
        }

        return extras;
    }

    private static int matches(MenuScanner.Result result, Set<String> foundKeys) {
        return result.found.size() + effectExtras(result, foundKeys).size();
    }

    private static String nickQuery() {
        return DbTab.nickBox == null ? "" : DbTab.nickBox.getValue();
    }

    private static List<MenuScanner.Result> players(Catalog catalog) {
        List<MenuScanner.Result> players = new ArrayList<>();
        Set<String> selected = catalog.selected;
        Set<String> foundKeys = new HashSet<>(ProfileDb.foundKeys());
        boolean effectsOnly = ProfileDb.effectMode() == 1;
        String query = nickQuery().trim().toLowerCase(Locale.ROOT);

        for (MenuScanner.Result result : DiamondFinder.scanner.results) {
            if (!result.complete || Blacklist.isBlocked(result.player)) {
                continue;
            }

            boolean nameMatches = query.isEmpty()
                || result.player.toLowerCase(Locale.ROOT).contains(query)
                || result.rank != null && result.rank.toLowerCase(Locale.ROOT).contains(query);
            if (!nameMatches) {
                continue;
            }

            boolean cosmeticMatches = !selected.isEmpty()
                ? !result.found.isEmpty() || !effectExtras(result, foundKeys).isEmpty()
                : !effectsOnly || result.effects != null && !result.effects.isEmpty();
            if (cosmeticMatches) {
                players.add(result);
            }
        }

        Comparator<MenuScanner.Result> order = switch (sortMode) {
            case 1 -> (MenuScanner.Result a, MenuScanner.Result b) -> compareRarity(a, b, catalog);
            case 2 -> Comparator.comparingInt((MenuScanner.Result r) -> r.owned == null ? 0 : r.owned.size());
            case 3 -> Comparator.comparingInt(DbTab2::level);
            case 4 -> Comparator.comparingLong((MenuScanner.Result r) -> dateKey(r.regDate));
            case NICK_SORT -> (MenuScanner.Result a, MenuScanner.Result b) -> a.player.compareToIgnoreCase(b.player);
            default -> Comparator.comparingInt((MenuScanner.Result r) -> matches(r, foundKeys));
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
        Catalog catalog = DiamondFinder.catalog;
        int left = screen.left + screen.sidebar + 10;
        int right = screen.left + screen.panelW - 10;
        int searchY = screen.top + 52;
        int headerY = screen.top + 84;
        int listTop = screen.top + 112;
        int listBottom = screen.top + screen.panelH - 74;
        int pagerY = screen.top + screen.panelH - 68;
        int cosmeticW = Math.max(170, (right - left) * 40 / 100);
        int playersX = left + cosmeticW + 12;
        int playersW = right - playersX;
        Ui.field(screen, g, left, searchY, cosmeticW, screen.search, "cos");
        Ui.field(screen, g, playersX, searchY, playersW, DbTab.nickBox, "nick");

        // Левая колонка: косметика из каталога
        List<Catalog.Entry> cosmetics = screen.filtered();
        int perPage = Math.max(1, (listBottom - listTop) / COSMETIC_ROW);
        int pages = Math.max(1, (cosmetics.size() + perPage - 1) / perPage);
        screen.page = Math.max(0, Math.min(screen.page, pages - 1));
        screen.text(g, "Косметика  ·  выбрано " + catalog.selected.size(), left, headerY + 7, TEXT);
        if (cosmetics.isEmpty()) {
            screen.text(
                g, catalog.entries.isEmpty() ? "Каталог пока пуст — откройте список косметики на сервере" : "Ничего не найдено", left + 4, listTop + 6, MUTED
            );
        }

        for (int row = 0; row < perPage; row++) {
            int index = screen.page * perPage + row;
            if (index >= cosmetics.size()) {
                break;
            }

            Catalog.Entry entry = cosmetics.get(index);
            int y = listTop + row * COSMETIC_ROW;
            boolean selected = catalog.selected.contains(entry.key);
            int foundW = 50;
            boolean hover = screen.inside(left, y, cosmeticW - foundW - 4, COSMETIC_ROW - 2);
            g.fill(left, y, left + cosmeticW, y + COSMETIC_ROW - 2, selected ? ROW_SELECTED : (hover ? ROW_HOVER : ROW));
            CosmeticText.Rarity rarity = entry.rarityValue();
            if (rarity != null) {
                g.fill(left, y, left + 3, y + COSMETIC_ROW - 2, rarity.color);
            }

            if (selected) {
                g.fill(left + 3, y + COSMETIC_ROW - 3, left + cosmeticW, y + COSMETIC_ROW - 2, CYAN);
            }

            ItemStack stack = entry.stack;
            if (stack != null && !stack.isEmpty()) {
                g.item(stack, left + 8, y + 2);
            }

            screen.text(g, screen.shorten(entry.name, cosmeticW - foundW - 40), left + 30, y + 7, selected ? CYAN : TEXT);
            String key = entry.key;
            Ui.btn(screen, g, "Нашёл", left + cosmeticW - foundW - 2, y + 2, foundW, COSMETIC_ROW - 6, false, () -> ProfileDb.markFound(key));
            DbTab.area(screen, left, y, cosmeticW - foundW - 4, COSMETIC_ROW - 2, () -> DiamondFinder.catalog.toggle(key));
        }

        pager(screen, g, left, pagerY, screen.page, pages, () -> screen.page = Math.max(0, screen.page - 1), () -> screen.page = Math.min(pages - 1, screen.page + 1));

        // Правая колонка: игроки из базы
        List<MenuScanner.Result> players = showBlocked ? new ArrayList<>() : players(catalog);
        int inBase = 0;

        for (MenuScanner.Result result : DiamondFinder.scanner.results) {
            if (result.complete && !Blacklist.isBlocked(result.player)) {
                inBase++;
            }
        }

        float scale = Math.min(1.0F, Math.max(0.72F, (playersW - 120.0F) / 272.0F));
        int blockedW = (int) (64.0F * scale);
        int orderW = (int) (96.0F * scale);
        int sortW = (int) (104.0F * scale);
        int buttonX = right - blockedW;
        Ui.btn(screen, g, showBlocked ? "К базе" : "ЧС (" + Blacklist.size() + ")", buttonX, headerY, blockedW, 22, showBlocked, () -> {
            showBlocked = !showBlocked;
            screen.resultsPage = 0;
        });
        if (!showBlocked) {
            int orderX = buttonX - (6 + orderW);
            String orderLabel = sortMode == NICK_SORT ? (sortDesc ? "Я–А" : "А–Я") : (sortDesc ? "По убыванию" : "По возрастанию");
            Ui.btn(screen, g, orderLabel, orderX, headerY, orderW, 22, false, () -> {
                sortDesc = !sortDesc;
                screen.resultsPage = 0;
            });
            buttonX = orderX - (6 + sortW);
            Ui.btn(screen, g, "Сорт: " + SORTS[sortMode], buttonX, headerY, sortW, 22, true, () -> {
                sortMode = (sortMode + 1) % SORTS.length;
                sortDesc = sortMode != NICK_SORT;
                screen.resultsPage = 0;
            });
        }

        String header;
        if (showBlocked) {
            header = "Чёрный список: " + Blacklist.size();
        } else {
            header = catalog.selected.isEmpty() ? "В базе: " + inBase : "Найдено " + players.size() + " из " + inBase;
            if (!nickQuery().isBlank()) {
                header = header + "  ·  по запросу";
            }
        }

        screen.text(g, screen.shorten(header, Math.max(40, buttonX - playersX - 10)), playersX, headerY + 7, TEXT);
        if (showBlocked) {
            drawBlocked(screen, g, playersX, playersW, listTop, listBottom, pagerY, right);
            return;
        }

        int playersPerPage = Math.max(1, (listBottom - listTop) / PLAYER_ROW);
        int playerPages = Math.max(1, (players.size() + playersPerPage - 1) / playersPerPage);
        screen.resultsPage = Math.max(0, Math.min(screen.resultsPage, playerPages - 1));
        if (players.isEmpty()) {
            String empty = inBase == 0
                ? "База пуста — нажмите «Добавить в базу», чтобы просканировать игроков."
                : (catalog.selected.isEmpty() ? "Никого не найдено." : "Игроков с такой косметикой в базе нет.");
            screen.text(g, screen.shorten(empty, playersW - 8), playersX + 4, listTop + 6, MUTED);
        }

        int blockW = 40;
        Set<String> foundKeys = new HashSet<>(ProfileDb.foundKeys());

        for (int row = 0; row < playersPerPage; row++) {
            int index = screen.resultsPage * playersPerPage + row;
            if (index >= players.size()) {
                break;
            }

            MenuScanner.Result player = players.get(index);
            int y = listTop + row * PLAYER_ROW;
            boolean hover = screen.inside(playersX, y, playersW, PLAYER_ROW - 3);
            CosmeticText.Rarity best = top(player, catalog);
            int background = hover ? ROW_HOVER : ROW;
            if (best != null) {
                int color = best.color;
                g.fill(playersX, y, playersX + playersW, y + PLAYER_ROW - 3, mix(background, color, hover ? 0.3F : 0.2F));
                g.fill(playersX, y + PLAYER_ROW - 4, playersX + playersW, y + PLAYER_ROW - 3, mix(background, color, 0.55F));
                g.fill(playersX, y, playersX + (hover ? 4 : 3), y + PLAYER_ROW - 3, color);
            } else {
                g.fill(playersX, y, playersX + playersW, y + PLAYER_ROW - 3, background);
                if (hover) {
                    g.fill(playersX, y, playersX + 2, y + PLAYER_ROW - 3, CYAN);
                }
            }

            Extra.nameText(screen, g, screen.shorten(player.player, 130), playersX + 10, y + 6, TEXT);
            ProfileInfo.draw(screen, g, player, playersX + 10, y + 6, playersW - 20 - blockW - 8);
            int selectedCount = catalog.selected.size();
            String summary;
            if (selectedCount == 0) {
                int[] counts = counts(player, catalog);
                summary = "Косметики в базе: " + counts[5];
                if (best != null) {
                    summary = summary + "  ·  лучшая: " + best.label + " ×" + counts[best.ordinal()];
                }
            } else {
                boolean effectsOnly = ProfileDb.effectMode() == 1;
                List<String> matched = new ArrayList<>(player.found);
                if (effectsOnly) {
                    matched.addAll(effectExtras(player, foundKeys));
                }

                StringBuilder text = new StringBuilder(
                    effectsOnly ? "С эффектом " + matched.size() + ": " : "Совпало " + matched.size() + " из " + selectedCount + ": "
                );
                int listed = 0;

                for (String key : matched) {
                    Catalog.Entry entry = catalog.entries.get(key);
                    if (entry == null) {
                        continue;
                    }

                    if (listed++ > 0) {
                        text.append(", ");
                    }

                    text.append(entry.name);
                    Object effect = player.effects == null ? null : player.effects.get(key);
                    if (effect != null) {
                        text.append(" (").append(effect).append(')');
                    }

                    if (listed >= 3) {
                        if (matched.size() > 3) {
                            text.append(" …");
                        }
                        break;
                    }
                }

                summary = text.toString();
            }

            screen.text(g, screen.shorten(summary, playersW - 20), playersX + 10, y + 21, MUTED);
            String nick = player.player;
            Ui.btn(screen, g, "В ЧС", playersX + playersW - blockW - 6, y + 3, blockW, 16, false, () -> Blacklist.add(nick));
            DbTab.area(screen, playersX, y, playersW - blockW - 12, PLAYER_ROW - 3, () -> screen.detail = player);
        }

        pager(
            screen,
            g,
            playersX,
            pagerY,
            screen.resultsPage,
            playerPages,
            () -> screen.resultsPage = Math.max(0, screen.resultsPage - 1),
            () -> screen.resultsPage = Math.min(playerPages - 1, screen.resultsPage + 1)
        );
        if (playersW >= 260) {
            DbClear.draw(screen, g, right + 6, pagerY);
        }
    }

    private static void drawBlocked(FinderScreen screen, GuiGraphicsExtractor g, int x, int width, int listTop, int listBottom, int pagerY, int right) {
        List<String> nicks = Blacklist.list();
        int perPage = Math.max(1, (listBottom - listTop) / BLOCKED_ROW);
        int pages = Math.max(1, (nicks.size() + perPage - 1) / perPage);
        screen.resultsPage = Math.max(0, Math.min(screen.resultsPage, pages - 1));
        if (nicks.isEmpty()) {
            screen.text(g, "Чёрный список пуст. Игроков можно добавить кнопкой «В ЧС» в списке базы.", x + 4, listTop + 6, MUTED);
        }

        int removeW = 64;

        for (int row = 0; row < perPage; row++) {
            int index = screen.resultsPage * perPage + row;
            if (index >= nicks.size()) {
                break;
            }

            String nick = nicks.get(index);
            int y = listTop + row * BLOCKED_ROW;
            g.fill(x, y, x + width, y + BLOCKED_ROW - 3, ROW);
            screen.text(g, screen.shorten(nick, width - removeW - 30), x + 10, y + 8, TEXT);
            Ui.btn(screen, g, "Убрать", x + width - removeW - 6, y + 2, removeW, BLOCKED_ROW - 7, false, () -> Blacklist.remove(nick));
        }

        pager(
            screen,
            g,
            x,
            pagerY,
            screen.resultsPage,
            pages,
            () -> screen.resultsPage = Math.max(0, screen.resultsPage - 1),
            () -> screen.resultsPage = Math.min(pages - 1, screen.resultsPage + 1)
        );
    }
}
