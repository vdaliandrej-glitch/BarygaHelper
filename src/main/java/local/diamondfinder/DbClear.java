package local.diamondfinder;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class DbClear {
    private static final long CONFIRM_MS = 4000L;
    public static final int WIDTH = 112;
    private static final int HEIGHT = 22;
    private static long armedUntil;
    private static FinderScreen screen;

    private DbClear() {
    }

    public static int draw(FinderScreen screen, GuiGraphicsExtractor g, int right, int y) {
        DbClear.screen = screen;
        boolean armed = System.currentTimeMillis() < armedUntil;
        String label = armed ? "Точно снести?" : "Снести базу";
        int x = right - 118;
        boolean hover = screen.inside(x, y, WIDTH, HEIGHT);
        int background = armed ? (hover ? 0xFFC8333D : 0xFFB02A33) : (hover ? 0xFF7A2630 : 0xFF5C1E24);
        g.fill(x, y, x + WIDTH, y + HEIGHT, background);
        g.fill(x, y + 20, x + WIDTH, y + HEIGHT, armed ? 0xFFFF5A5A : 0xFFA13A42);
        String text = screen.shorten(label, WIDTH - 8);
        screen.text(g, text, x + (WIDTH - screen.textWidth(text)) / 2, y + 7, armed ? 0xFFFFFFFF : 0xFFFFD0D0);
        DbTab.area(screen, x, y, WIDTH, HEIGHT, DbClear::click);
        return x;
    }

    private static void click() {
        long now = System.currentTimeMillis();
        if (now >= armedUntil) {
            armedUntil = now + CONFIRM_MS;
        } else {
            armedUntil = 0L;
            clearAll();
        }
    }

    private static void clearAll() {
        MenuScanner scanner = DiamondFinder.scanner;
        if (scanner != null) {
            if (scanner.running()) {
                scanner.stop("Сканирование остановлено: очистка базы");
            }

            scanner.results.clear();
            scanner.total = 0;
            scanner.checked = 0;
            scanner.errors = 0;
            scanner.message = "База игроков очищена";
            FinderScreen current = screen;
            if (current != null) {
                current.detail = null;
                current.resultsPage = 0;
            }

            try {
                Field field = ProfileDb.class.getDeclaredField("FILE");
                field.setAccessible(true);
                Files.deleteIfExists((Path) field.get(null));
            } catch (Throwable ignored) {
            }
        }
    }
}
