package local.diamondfinder;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class FinderScreen extends Screen {
    private static final int TEXT = -1379332;
    private static final int MUTED = -8152657;
    private static final int CYAN = -14363163;
    private static final int PANEL = -183428827;
    final List<FinderScreen.Area> areas = new ArrayList<>();
    EditBox search;
    int left;
    int top;
    int panelW;
    int panelH;
    int sidebar;
    int tab;
    int page;
    int mx;
    int my;
    String query = "";
    CosmeticText.Rarity filter;
    boolean selectedOnly;
    boolean hitsOnly = true;
    MenuScanner.Result detail;
    int resultsPage;

    public FinderScreen() {
        super(Component.literal("BarygaHelper"));
    }

    protected void init() {
        this.panelW = Math.min(940, this.width - 24);
        this.panelH = Math.min(540, this.height - 24);
        this.left = (this.width - this.panelW) / 2;
        this.top = (this.height - this.panelH) / 2;
        this.sidebar = this.panelW < 560 ? 98 : 132;
        this.search = new EditBox(
            this.font, this.left + this.sidebar + 18, this.top + 56, Math.max(70, this.panelW - this.sidebar - 34), 18, Component.literal("Поиск косметики")
        );
        this.search.setBordered(false);
        this.search.setTextColor(-1379332);
        this.search.setHint(Component.literal("Поиск по названию…"));
        this.search.setValue(this.query);
        this.search.setMaxLength(80);
        this.search.setResponder(s -> {
            this.query = s;
            this.page = 0;
        });
        this.addRenderableWidget(this.search);
        DbTab.init(this);
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) {
    }

    public void extractRenderState(GuiGraphicsExtractor var1, int var2, int var3, float var4) {
        Ui.render(this, var1, var2, var3, var4);
        super.extractRenderState(var1, var2, var3, var4);
    }

    List<Catalog.Entry> filtered() {
        String q = CosmeticText.normalize(this.query);
        return ProfileDb.hideFound(
            DiamondFinder.catalog
                .entries
                .values()
                .stream()
                .filter(e -> this.filter == null || e.rarityValue() == this.filter)
                .filter(e -> !this.selectedOnly || DiamondFinder.catalog.selected.contains(e.key))
                .filter(e -> CosmeticText.normalize(e.name).contains(q))
                .sorted(
                    Comparator.<Catalog.Entry, Integer>comparing(e -> e.rarityValue().ordinal())
                        .reversed()
                        .thenComparing(e -> e.name, String.CASE_INSENSITIVE_ORDER)
                )
                .toList()
        );
    }

    void drawCatalog(GuiGraphicsExtractor g) {
        List<Catalog.Entry> entries = this.filtered();
        int x = this.left + this.sidebar + 10;
        int y = this.top + 91;
        int w = this.panelW - this.sidebar - 20;
        if (entries.isEmpty()) {
            this.text(g, DiamondFinder.catalog.entries.isEmpty() ? "Каталог пока пуст" : "Ничего не найдено", x + 10, y + 20, -1379332);
            this.wrap(
                g,
                "Откройте серверный список косметики. Все предметы на странице добавятся автоматически — без наведения мыши.",
                x + 10,
                y + 44,
                w - 20,
                -8152657
            );
        } else {
            int cols = Math.max(2, Math.min(5, w / 130));
            int rows = Math.max(1, (this.panelH - 168) / 91);
            int perPage = rows * cols;
            this.page = Math.min(this.page, Math.max(0, (entries.size() - 1) / perPage));
            int cardW = (w - (cols - 1) * 8) / cols;

            for (int i = 0; i < perPage && this.page * perPage + i < entries.size(); i++) {
                Catalog.Entry entry = entries.get(this.page * perPage + i);
                int cx = x + i % cols * (cardW + 8);
                int cy = y + i / cols * 91;
                boolean chosen = DiamondFinder.catalog.selected.contains(entry.key);
                boolean hover = this.inside(cx, cy, cardW, 83);
                g.fill(cx, cy, cx + cardW, cy + 83, hover ? -14009008 : -14996933);
                g.fillGradient(cx, cy, cx + cardW, cy + 49, hover ? -13218978 : -14074802, -14668227);
                g.fill(cx, cy + 48, cx + cardW, cy + 50, entry.rarityValue().color);
                if (chosen) {
                    g.outline(cx, cy, cardW, 83, -14363163);
                }

                g.fill(cx + cardW - 16, cy + 6, cx + cardW - 6, cy + 16, chosen ? -14363163 : -15195602);
                if (chosen) {
                    this.text(g, "+", cx + cardW - 14, cy + 7, -15720405);
                }

                if (entry.stack != null && !entry.stack.isEmpty()) {
                    g.pose().pushMatrix();
                    g.pose().translate(cx + cardW / 2.0F - 16.0F, cy + 9);
                    g.pose().scale(2.0F, 2.0F);
                    g.item(entry.stack, 0, 0);
                    g.pose().popMatrix();
                }

                List<FormattedCharSequence> lines = this.font.split(Component.literal(entry.name), cardW - 12);

                for (int ln = 0; ln < Math.min(2, lines.size()); ln++) {
                    g.text(this.font, lines.get(ln), cx + 6, cy + 55 + ln * 10, -1379332, false);
                }

                this.areas.add(new FinderScreen.Area(cx, cy, cardW, 83, () -> DiamondFinder.catalog.toggle(entry.key)));
                if (hover) {
                    g.setTooltipForNextFrame(
                        this.font,
                        List.of(
                            Component.literal(entry.name),
                            Component.literal(entry.rarityValue().label),
                            Component.literal(chosen ? "Нажмите, чтобы снять выбор" : "Нажмите, чтобы выбрать")
                        ),
                        Optional.empty(),
                        this.mx,
                        this.my
                    );
                }
            }

            this.pager(g, entries.size(), perPage, x, this.top + this.panelH - 65, w);
        }
    }

    void drawResults(GuiGraphicsExtractor g) {
        int x = this.left + this.sidebar + 12;
        int y = this.top + 58;
        int w = this.panelW - this.sidebar - 24;
        this.button(g, this.hitsOnly ? "Только совпадения" : "Все проверки", x, y, 136, 23, this.hitsOnly, () -> {
            this.hitsOnly = !this.hitsOnly;
            this.page = 0;
        });
        List<MenuScanner.Result> results = ScanView.filter(DiamondFinder.scanner.results).stream().filter(rx -> !this.hitsOnly || !rx.found.isEmpty()).toList();
        int count = Math.max(1, (this.panelH - 159) / 53);
        this.page = Math.min(this.page, Math.max(0, (results.size() - 1) / count));
        if (results.isEmpty()) {
            this.wrap(g, "Здесь появятся игроки с выбранной косметикой. Для просмотра ошибок включите «Все проверки».", x, y + 46, w, -8152657);
        }

        for (int i = 0; i < count && this.page * count + i < results.size(); i++) {
            MenuScanner.Result r = results.get(this.page * count + i);
            int cy = y + 33 + i * 53;
            g.fill(x, cy, x + w, cy + 47, -14997192);
            g.fill(x, cy, x + 2, cy + 47, r.found.isEmpty() ? -10192499 : -14363163);
            Extra.nameText(this, g, this.shorten(r.player, w - 185), x + 9, cy + 7, -1379332);
            ProfileInfo.draw(this, g, r, x + 9, cy + 7, w - 185);
            String time = r.time == 0L ? "" : Instant.ofEpochMilli(r.time).atZone(ZoneId.systemDefault()).toLocalTime().withNano(0).toString();
            this.text(g, this.shorten(r.status + " " + time, w - 185), x + 9, cy + 26, -8152657);
            this.button(g, "Найденная косметика (" + r.found.size() + ")", x + w - 169, cy + 5, 161, 22, !r.found.isEmpty(), () -> {
                this.resultsPage = this.page;
                this.page = 0;
                this.detail = r;
            });
            this.button(g, "Копировать ник", x + w - 169, cy + 29, 161, 15, false, () -> this.minecraft.keyboardHandler.setClipboard(r.player));
        }

        this.pager(g, results.size(), count, x, this.top + this.panelH - 65, w);
    }

    void drawDetails(GuiGraphicsExtractor g) {
        int x = this.left + this.sidebar + 12;
        int y = this.top + 55;
        int w = this.panelW - this.sidebar - 24;
        this.button(g, "< К игрокам", x, y, 96, 23, false, () -> {
            this.detail = null;
            this.page = this.resultsPage;
        });
        Extra.nameText(this, g, this.shorten(this.detail.player, w - 114), x + 108, y + 7, -14363163);
        ProfileInfo.draw(this, g, this.detail, x + 108, y + 7, w - 114);
        this.text(g, this.shorten(this.detail.status + " · " + this.detail.coverage.summary(), w), x, y + 32, -8152657);
        ArrayList<String> keys = new ArrayList<>(this.detail.found);
        keys.sort(Comparator.comparing(k -> {
            Catalog.Entry e = DiamondFinder.catalog.entries.get(k);
            return (String)(e == null ? k : e.name);
        }, String.CASE_INSENSITIVE_ORDER));
        int perPage = Math.max(1, (this.panelH - 180) / 45);
        this.page = Math.min(this.page, Math.max(0, (keys.size() - 1) / perPage));
        if (keys.isEmpty()) {
            this.wrap(g, "В проверенной части выбранная косметика не найдена. Скрытый или недоступный инвентарь не считается пустым.", x, y + 61, w, -8152657);
        }

        for (int i = 0; i < perPage && this.page * perPage + i < keys.size(); i++) {
            String key = keys.get(this.page * perPage + i);
            Catalog.Entry entry = DiamondFinder.catalog.entries.get(key);
            int cy = y + 54 + i * 45;
            g.fill(x, cy, x + w, cy + 39, -14997192);
            g.fill(x, cy, x + 2, cy + 39, entry == null ? -14363163 : entry.rarityValue().color);
            if (entry != null && entry.stack != null && !entry.stack.isEmpty()) {
                g.item(entry.stack, x + 10, cy + 11);
            }

            this.text(g, this.shorten(entry == null ? key : entry.name, w - 47), x + 36, cy + 4, -1379332);
            String rarity = entry == null ? "" : entry.rarityValue().label + " · ";
            this.text(g, this.shorten(rarity + (this.detail.equipped.contains(key) ? "Надето / витрина" : "В инвентаре"), w - 47), x + 36, cy + 16, -8152657);
            FxLine.draw(this, g, this.detail, key, x, cy, w);
            if (entry != null && this.inside(x, cy, w, 39)) {
                g.setTooltipForNextFrame(
                    this.font, List.of(Component.literal(entry.name), Component.literal(entry.rarityValue().label)), Optional.empty(), this.mx, this.my
                );
            }
        }

        this.pager(g, keys.size(), perPage, x, this.top + this.panelH - 65, w);
    }

    private void pager(GuiGraphicsExtractor g, int count, int perPage, int x, int y, int w) {
        int max = Math.max(1, (count + perPage - 1) / perPage);
        this.text(g, this.page + 1 + " / " + max + "   ·   " + count + " записей", x + 5, y + 7, -8152657);
        this.button(g, "<", x + w - 56, y, 24, 22, false, () -> this.page = Math.max(0, this.page - 1));
        this.button(g, ">", x + w - 26, y, 24, 22, false, () -> this.page = Math.min(max - 1, this.page + 1));
    }

    void button(GuiGraphicsExtractor g, String label, int x, int y, int w, int h, boolean active, Runnable action) {
        boolean hover = this.inside(x, y, w, h);
        g.fill(x, y, x + w, y + h, active ? (hover ? -14589325 : -15251111) : (hover ? -13811372 : -14996935));
        if (active) {
            g.fill(x, y + h - 1, x + w, y + h, -14363163);
        }

        this.text(g, this.shorten(label, w - 8), x + 5, y + (h - 8) / 2, active ? -14363163 : -1379332);
        this.areas.add(new FinderScreen.Area(x, y, w, h, action));
    }

    boolean inside(int x, int y, int w, int h) {
        return this.mx >= x && this.mx < x + w && this.my >= y && this.my < y + h;
    }

    void text(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        g.text(this.font, text, x, y, color, false);
    }

    String shorten(String text, int width) {
        return this.font.width(text) <= width ? text : this.font.plainSubstrByWidth(text, Math.max(0, width - 9)) + "…";
    }

    private int wrap(GuiGraphicsExtractor g, String s, int x, int y, int w, int color) {
        for (FormattedCharSequence line : this.font.split(Component.literal(s), w)) {
            g.text(this.font, line, x, y, color, false);
            y += 12;
        }

        return y;
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (FinderScreen.Area area : this.areas) {
                if (area.contains(event.x(), event.y())) {
                    this.search.setFocused(false);
                    this.setFocused(null);
                    area.action.run();
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        this.page = Math.max(0, this.page + (dy < 0.0 ? 1 : -1));
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 73 && !DbTab.anyFocused(this.search)) {
            this.onClose();
            return true;
        } else if (event.key() == 297) {
            DiamondFinder.stopAll();
            return true;
        } else {
            return super.keyPressed(event);
        }
    }

    public void onClose() {
        if (this.detail != null) {
            this.detail = null;
            this.page = this.resultsPage;
        } else {
            DiamondFinder.catalog.save();
            super.onClose();
        }
    }

    int textWidth(String var1) {
        return this.font.width(var1);
    }

    Font fontRef() {
        return this.font;
    }

    void addBox(EditBox var1) {
        this.addRenderableWidget(var1);
    }

    record Area(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
        }
    }
}
