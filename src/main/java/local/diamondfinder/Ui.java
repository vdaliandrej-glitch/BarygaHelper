package local.diamondfinder;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;

public final class Ui {
    static final int BACKDROP = 0xF5111925;
    static final int PANEL = 0xFF0D1620;
    static final int PANEL2 = 0xFF10202B;
    static final int CARD = 0xFF174959;
    static final int CARD_H = 0xFF216273;
    static final int ACT = 0xFF1B2A39;
    static final int ACT_H = 0xFF2D4154;
    static final int LINE = 0xFF304357;
    static final int CYAN = 0xFF24D5E5;
    static final int TEXT = 0xFFEAF3FC;
    static final int MUTED = 0xFF8399AF;
    private static final int ERROR = 0xFFFF6B6B;
    private static final String CREDIT = "БарыгаХелпер by Koodryaaaa";
    private static FinderScreen last;
    private static final int[] ORDER = new int[]{2, 0, 3, 1};
    private static final String[] NAMES = new String[]{"Каталог", "Скан", "База", "Найдено"};

    private Ui() {
    }

    static void btn(FinderScreen screen, GuiGraphicsExtractor g, String label, int x, int y, int width, int height, boolean active, Runnable action) {
        boolean hover = screen.inside(x, y, width, height);
        g.fill(x, y, x + width, y + height, active ? (hover ? ACT_H : ACT) : (hover ? CARD_H : CARD));
        if (active) {
            g.fill(x, y + height - 2, x + width, y + height, CYAN);
        }

        String text = screen.shorten(label, Math.max(8, width - 8));
        screen.text(g, text, x + (width - screen.textWidth(text)) / 2, y + (height - 8) / 2, active ? CYAN : TEXT);
        DbTab.area(screen, x, y, width, height, action);
    }

    static void field(FinderScreen screen, GuiGraphicsExtractor g, int x, int y, int width, EditBox box, String id) {
        boolean focused = box != null && box.isFocused();
        g.fill(x - 1, y - 1, x + width + 1, y + 27, focused ? CYAN : LINE);
        g.fill(x, y, x + width, y + 26, PANEL2);
        if (box != null) {
            box.setX(x + 10);
            box.setY(y + 4);
            box.setWidth(Math.max(40, width - 20));
            box.setVisible(true);
        }
    }

    private static void label(FinderScreen screen, GuiGraphicsExtractor g, String text, int x, int y) {
        screen.text(g, text, x, y, MUTED);
    }

    private static void switchTab(FinderScreen screen, int tab) {
        screen.tab = tab;
        screen.page = 0;
        screen.resultsPage = 0;
        screen.detail = null;
        screen.setFocused(null);
    }

    public static void render(FinderScreen screen, GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        screen.mx = mouseX;
        screen.my = mouseY;
        screen.areas.clear();
        if (screen != last) {
            last = screen;
            screen.tab = 2;
            screen.page = 0;
            screen.resultsPage = 0;
            screen.detail = null;
        }

        Catalog catalog = DiamondFinder.catalog;
        MenuScanner scanner = DiamondFinder.scanner;
        boolean compact = screen.panelH < 430;
        if (screen.search != null) {
            screen.search.setVisible(false);
        }

        if (DbTab.nickBox != null) {
            DbTab.nickBox.setVisible(false);
        }

        int left = screen.left;
        int top = screen.top;
        g.fill(0, 0, screen.width, screen.height, BACKDROP);
        g.fill(left - 1, top - 1, left + screen.panelW + 1, top + screen.panelH + 1, LINE);
        g.fill(left, top, left + screen.panelW, top + screen.panelH, PANEL);
        g.fill(left, top, left + screen.panelW, top + 44, PANEL2);
        g.fill(left, top + 44, left + screen.panelW, top + 45, LINE);
        screen.text(g, "BARYGA", left + 16, top + 18, CYAN);
        screen.text(g, "HELPER", left + 16 + screen.textWidth("BARYGA") + 3, top + 18, TEXT);
        int tabX = left + screen.sidebar + 10;

        for (int tab : ORDER) {
            String name = NAMES[tab] + (tab == 3 ? " (" + ProfileDb.foundKeys().size() + ")" : "");
            int tabWidth = screen.textWidth(name) + 28;
            boolean selected = screen.tab == tab;
            boolean hover = screen.inside(tabX, top + 12, tabWidth, 33);
            g.fill(tabX, top + 12, tabX + tabWidth, top + 45, selected ? PANEL : (hover ? CARD_H : PANEL2));
            if (selected) {
                g.fill(tabX, top + 12, tabX + tabWidth, top + 14, CYAN);
            }

            screen.text(g, name, tabX + 14, top + 24, selected || hover ? TEXT : MUTED);
            DbTab.area(screen, tabX, top + 12, tabWidth, 33, () -> switchTab(screen, tab));
            tabX += tabWidth + 4;
        }

        btn(screen, g, "×", left + screen.panelW - 40, top + 10, 28, 26, false, screen::onClose);
        int sideX = left + 8;
        int sideW = screen.sidebar - 16;
        int y = top + 56;
        int buttonH = compact ? 19 : 24;
        int gap = compact ? 2 : 3;
        g.fill(left + screen.sidebar, top + 45, left + screen.sidebar + 1, top + screen.panelH - 38, LINE);
        if (!compact) {
            label(screen, g, "РЕДКОСТЬ", sideX + 2, y);
            y += 13;
        }

        int[] counts = new int[CosmeticText.Rarity.values().length];
        int total = 0;

        for (Catalog.Entry entry : catalog.entries.values()) {
            if (!ProfileDb.isFound(entry.key)) {
                CosmeticText.Rarity rarity = entry.rarityValue();
                if (rarity != null) {
                    counts[rarity.ordinal()]++;
                }

                total++;
            }
        }

        btn(screen, g, "Вся косметика  " + total, sideX, y, sideW, buttonH, screen.filter == null, () -> {
            screen.filter = null;
            screen.page = 0;
        });
        y += buttonH + gap;

        for (CosmeticText.Rarity rarity : CosmeticText.Rarity.values()) {
            if (rarity.tradable()) {
                boolean active = screen.filter == rarity;
                int rarityW = sideW - 26;
                btn(screen, g, rarity.label + "  " + counts[rarity.ordinal()], sideX, y, rarityW, buttonH, active, () -> {
                    screen.filter = active ? null : rarity;
                    screen.page = 0;
                });
                g.fill(sideX, y, sideX + 3, y + buttonH, rarity.color);
                btn(screen, g, "+", sideX + rarityW + 2, y, 24, buttonH, false, () -> new SelectRarity(rarity).run());
                y += buttonH + gap;
            }
        }

        y += compact ? 4 : 8;
        if (!compact) {
            label(screen, g, "ВЫБОР", sideX + 2, y);
            y += 13;
        }

        screen.text(g, "Выбрано: " + catalog.selected.size(), sideX + 2, y + 2, catalog.selected.isEmpty() ? MUTED : CYAN);
        y += 14;
        btn(screen, g, "Только выбранные", sideX, y, sideW, buttonH, screen.selectedOnly, () -> {
            screen.selectedOnly = !screen.selectedOnly;
            screen.page = 0;
        });
        y += buttonH + gap;
        btn(screen, g, "Снять выбор", sideX, y, sideW, buttonH, false, () -> {
            catalog.selected.clear();
            catalog.dirty();
        });
        y += buttonH + gap + (compact ? 4 : 8);
        if (!compact) {
            label(screen, g, "ЭФФЕКТ НА КОСМЕТИКЕ", sideX + 2, y);
            y += 13;
        }

        int effectW = (sideW - 4) / 3;
        int effectMode = ProfileDb.effectMode();
        String[] effectLabels = new String[]{"Любой", "Есть", "Нет"};

        for (int i = 0; i < 3; i++) {
            int mode = i;
            btn(screen, g, effectLabels[i], sideX + i * (effectW + 2), y, effectW, buttonH - 2, effectMode == i, () -> ProfileDb.setEffectMode(mode));
        }

        boolean noDetail = screen.detail == null;
        int contentX = left + screen.sidebar + 10;
        int contentRight = left + screen.panelW - 10;
        switch (screen.tab) {
            case 0:
                field(screen, g, contentX, top + 52, contentRight - contentX, screen.search, "cat");
                screen.drawCatalog(g);
                break;
            case 1:
                if (noDetail) {
                    screen.drawResults(g);
                } else {
                    screen.drawDetails(g);
                }
                break;
            case 2:
                if (noDetail) {
                    DbTab.draw(screen, g);
                } else {
                    screen.drawDetails(g);
                }
                break;
            default:
                FoundTab.draw(screen, g);
        }

        int footerY = top + screen.panelH - 38;
        g.fill(left, footerY, left + screen.panelW, top + screen.panelH, PANEL2);
        g.fill(left, footerY, left + screen.panelW, footerY + 1, LINE);
        Credit.draw(screen, g, CREDIT, left + 16, footerY + 8, 0);
        int inBase = 0;

        for (MenuScanner.Result result : scanner.results) {
            if (result.complete) {
                inBase++;
            }
        }

        screen.text(g, "Каталог: " + catalog.entries.size() + "  ·  в базе: " + inBase, left + 16, footerY + 22, MUTED);
        boolean running = scanner.running();
        int scanW = 156;
        int scanX = left + screen.panelW - 16 - scanW;
        btn(screen, g, running ? "Остановить" : "Добавить в базу", scanX, footerY + 7, scanW, 24, running, () -> {
            if (scanner.running()) {
                scanner.stop("Остановлено");
            } else {
                scanner.start();
            }
        });
        boolean hasError = catalog.error != null && !catalog.error.isEmpty();
        String status = hasError ? catalog.error : scanner.message;
        int statusX = left + 16 + Math.max(screen.textWidth(CREDIT), screen.textWidth("Каталог: 000  ·  в базе: 0000")) + 24;
        if (status != null && !status.isEmpty()) {
            screen.text(g, screen.shorten(status, Math.max(40, scanX - statusX - 12)), statusX, footerY + 15, hasError ? ERROR : MUTED);
        }
    }
}
