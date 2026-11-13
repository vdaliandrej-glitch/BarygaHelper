package local.diamondfinder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Blacklist {
    private static final Map<String, String> NICKS = new LinkedHashMap<>();
    private static boolean loaded;

    private Blacklist() {
    }

    public static boolean isBlocked(String var0) {
        if (var0 == null) {
            return false;
        }

        load();
        return !NICKS.isEmpty() && NICKS.containsKey(var0.toLowerCase(Locale.ROOT));
    }

    public static void add(String var0) {
        if (var0 != null && !var0.isBlank()) {
            load();
            NICKS.putIfAbsent(var0.trim().toLowerCase(Locale.ROOT), var0.trim());
            save();
        }
    }

    public static void remove(String var0) {
        if (var0 != null) {
            load();
            if (NICKS.remove(var0.trim().toLowerCase(Locale.ROOT)) != null) {
                save();
            }
        }
    }

    public static int size() {
        load();
        return NICKS.size();
    }

    public static List<String> list() {
        load();
        ArrayList var0 = new ArrayList<>(NICKS.values());
        var0.sort(String.CASE_INSENSITIVE_ORDER);
        return var0;
    }

    private static Path file() {
        return DiamondFinder.catalog.dir.resolve("blacklist.txt");
    }

    private static void load() {
        if (!loaded && DiamondFinder.catalog != null) {
            loaded = true;

            try {
                Path var0 = file();
                if (Files.exists(var0)) {
                    for (String var2 : Files.readAllLines(var0)) {
                        String var3 = var2.trim();
                        if (!var3.isEmpty()) {
                            NICKS.putIfAbsent(var3.toLowerCase(Locale.ROOT), var3);
                        }
                    }
                }
            } catch (Throwable var4) {
            }
        }
    }

    private static void save() {
        try {
            Path var0 = file();
            Files.createDirectories(var0.getParent());
            Files.writeString(var0, String.join("\n", NICKS.values()) + "\n");
        } catch (Throwable var1) {
        }
    }
}
