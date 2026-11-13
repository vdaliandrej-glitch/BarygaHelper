package local.diamondfinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public final class FoundTab {
    private static final int ROW_HEIGHT = 28;

    private FoundTab() {
    }

    public static void draw(FinderScreen screen, GuiGraphicsExtractor g) {
        Catalog catalog = DiamondFinder.catalog;
        int x = screen.left + screen.sidebar + 10;
        int right = screen.left + screen.panelW - 10;
        int width = right - x;
        int searchY = screen.top + 52;
        int headerY = screen.top + 84;
        int listTop = screen.top + 112;
        int listBottom = screen.top + screen.panelH - 74;
        Ui.field(screen, g, x, searchY, width, screen.search, "found");
        List<String> found = ProfileDb.foundKeys();
        String query = screen.query == null ? "" : screen.query.trim().toLowerCase(Locale.ROOT);
        List<String> shown = new ArrayList<>();

        for (String key : found) {
            Catalog.Entry entry = catalog.entries.get(key);
            String name = entry != null ? entry.name : key;
            if (query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query)) {
                shown.add(key);
            }
        }

        screen.text(g, "Найдено: " + found.size() + (query.isEmpty() ? "" : "  ·  показано " + shown.size()), x, headerY + 7, Ui.TEXT);
        String hint = "Эти предметы скрыты из поиска и фильтров";
        screen.text(g, screen.shorten(hint, width / 2), right - Math.min(screen.textWidth(hint), width / 2), headerY + 7, Ui.MUTED);
        int perPage = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int pages = Math.max(1, (shown.size() + perPage - 1) / perPage);
        screen.page = Math.max(0, Math.min(screen.page, pages - 1));
        if (shown.isEmpty()) {
            screen.text(
                g,
                found.isEmpty() ? "Пока пусто. Во вкладке «База» нажмите «Нашёл» у косметики, которую вы уже нашли." : "Ничего не найдено.",
                x + 4,
                listTop + 6,
                Ui.MUTED
            );
        }

        for (int row = 0; row < perPage; row++) {
            int index = screen.page * perPage + row;
            if (index >= shown.size()) {
                break;
            }

            String key = shown.get(index);
            Catalog.Entry entry = catalog.entries.get(key);
            int y = listTop + row * ROW_HEIGHT;
            boolean hover = screen.inside(x, y, width, ROW_HEIGHT - 2);
            g.fill(x, y, right, y + ROW_HEIGHT - 2, hover ? DbTab.ROW_HOVER : DbTab.ROW);
            if (entry != null) {
                CosmeticText.Rarity rarity = entry.rarityValue();
                if (rarity != null) {
                    g.fill(x, y, x + 3, y + ROW_HEIGHT - 2, rarity.color);
                }

                ItemStack stack = entry.stack;
                if (stack != null && !stack.isEmpty()) {
                    g.item(stack, x + 10, y + 4);
                }
            }

            screen.text(g, screen.shorten(entry != null ? entry.name : key, width - 160), x + 34, y + 9, Ui.TEXT);
            if (entry != null && entry.rarityValue() != null) {
                String label = entry.rarityValue().label;
                screen.text(g, label, right - 110 - screen.textWidth(label), y + 9, Ui.MUTED);
            }

            Ui.btn(screen, g, "Вернуть", right - 100, y + 2, 94, ROW_HEIGHT - 6, false, () -> ProfileDb.unmarkFound(key));
        }

        int lastPage = pages;
        String pageText = screen.page + 1 + " / " + pages;
        int bottomY = screen.top + screen.panelH - 68;
        Ui.btn(screen, g, "<", x, bottomY, 24, 22, false, screen.page > 0 ? () -> screen.page = Math.max(0, screen.page - 1) : () -> {});
        screen.text(g, pageText, x + 32, screen.top + screen.panelH - 61, Ui.MUTED);
        Ui.btn(
            screen,
            g,
            ">",
            x + 40 + screen.textWidth(pageText),
            bottomY,
            24,
            22,
            false,
            screen.page < pages - 1 ? () -> screen.page = Math.min(lastPage - 1, screen.page + 1) : () -> {}
        );
        Ui.btn(screen, g, "Проверить инвентарь", right - 150, bottomY, 150, 22, true, SelfImport::recheck);
    }
}
