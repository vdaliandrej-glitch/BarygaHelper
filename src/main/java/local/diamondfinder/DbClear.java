package local.diamondfinder;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class DbClear {
    private static final long CONFIRM_MS = 4000L;
    public static final int WIDTH = 112;
    private static long armedUntil;
    private static FinderScreen screen;

    private DbClear() {
    }

    public static int draw(FinderScreen var0, GuiGraphicsExtractor var1, int var2, int var3) {
        screen = var0;
        boolean var4 = System.currentTimeMillis() < armedUntil;
        String var5 = var4 ? "Точно снести?" : "Снести базу";
        var2 -= 118;
        boolean var6 = var0.inside(var2, var3, 112, 22);
        int var7 = var4 ? (var6 ? -3656899 : -5232077) : (var6 ? -8772048 : -10740188);
        var1.fill(var2, var3, var2 + 112, var3 + 22, var7);
        var1.fill(var2, var3 + 20, var2 + 112, var3 + 22, var4 ? -42406 : -6211006);
        String var8 = var0.shorten(var5, 104);
        var0.text(var1, var8, var2 + (112 - var0.textWidth(var8)) / 2, var3 + 7, var4 ? -1 : -12080);
        DbTab.area(var0, var2, var3, 112, 22, DbClear::click);
        return var2;
    }

    private static void click() {
        long var0 = System.currentTimeMillis();
        if (var0 >= armedUntil) {
            armedUntil = var0 + 4000L;
        } else {
            armedUntil = 0L;
            clearAll();
        }
    }

    private static void clearAll() {
        MenuScanner var0 = DiamondFinder.scanner;
        if (var0 != null) {
            if (var0.running()) {
                var0.stop("Сканирование остановлено: очистка базы");
            }

            var0.results.clear();
            var0.total = 0;
            var0.checked = 0;
            var0.errors = 0;
            var0.message = "База игроков очищена";
            FinderScreen var1 = screen;
            if (var1 != null) {
                var1.detail = null;
                var1.resultsPage = 0;
            }

            try {
                Field var2 = Class.forName("local.diamondfinder.ProfileDb").getDeclaredField("FILE");
                var2.setAccessible(true);
                Files.deleteIfExists((Path)var2.get(null));
            } catch (Throwable var3) {
            }
        }
    }
}
