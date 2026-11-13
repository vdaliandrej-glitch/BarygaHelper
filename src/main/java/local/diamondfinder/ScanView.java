package local.diamondfinder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ScanView {
    private static Set<String> last = new HashSet<>();
    private static boolean loaded;

    private ScanView() {
    }

    public static void skip(Deque<String> var0, List<MenuScanner.Result> var1) {
        HashSet var2 = new HashSet();

        for (String var4 : var0) {
            var2.add(var4.toLowerCase(Locale.ROOT));
        }

        last = var2;
        loaded = true;
        save();
        ProfileDb.skip(var0, var1);
    }

    public static List<MenuScanner.Result> filter(List<MenuScanner.Result> var0) {
        load();
        ArrayList var1 = new ArrayList();

        for (MenuScanner.Result var3 : var0) {
            if (var3.player != null && last.contains(var3.player.toLowerCase(Locale.ROOT)) && !Blacklist.isBlocked(var3.player)) {
                var1.add(var3);
            }
        }

        return var1;
    }

    private static void load() {
        if (!loaded && DiamondFinder.catalog != null) {
            loaded = true;

            try {
                Path var0 = file();
                if (Files.exists(var0)) {
                    for (String var2 : Files.readAllLines(var0)) {
                        if (!var2.isBlank()) {
                            last.add(var2.trim());
                        }
                    }
                }
            } catch (Throwable var3) {
            }
        }
    }

    public static boolean known(String var0) {
        load();
        return last.contains(var0.toLowerCase(Locale.ROOT));
    }

    public static void add(String var0) {
        load();
        if (last.add(var0.toLowerCase(Locale.ROOT))) {
            save();
        }
    }

    private static void save() {
        try {
            Path var0 = file();
            Files.createDirectories(var0.getParent());
            Files.writeString(var0, String.join("\n", last) + "\n");
        } catch (Throwable var1) {
        }
    }

    private static Path file() {
        return DiamondFinder.catalog.dir.resolve("last-scan.txt");
    }
}
