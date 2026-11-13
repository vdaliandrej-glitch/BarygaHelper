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

    public static boolean isBlocked(String nick) {
        if (nick == null) {
            return false;
        }

        load();
        return !NICKS.isEmpty() && NICKS.containsKey(nick.toLowerCase(Locale.ROOT));
    }

    public static void add(String nick) {
        if (nick != null && !nick.isBlank()) {
            load();
            NICKS.putIfAbsent(nick.trim().toLowerCase(Locale.ROOT), nick.trim());
            save();
        }
    }

    public static void remove(String nick) {
        if (nick != null) {
            load();
            if (NICKS.remove(nick.trim().toLowerCase(Locale.ROOT)) != null) {
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
        List<String> nicks = new ArrayList<>(NICKS.values());
        nicks.sort(String.CASE_INSENSITIVE_ORDER);
        return nicks;
    }

    private static Path file() {
        return DiamondFinder.catalog.dir.resolve("blacklist.txt");
    }

    private static void load() {
        if (!loaded && DiamondFinder.catalog != null) {
            loaded = true;

            try {
                Path path = file();
                if (Files.exists(path)) {
                    for (String line : Files.readAllLines(path)) {
                        String nick = line.trim();
                        if (!nick.isEmpty()) {
                            NICKS.putIfAbsent(nick.toLowerCase(Locale.ROOT), nick);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, String.join("\n", NICKS.values()) + "\n");
        } catch (Throwable ignored) {
        }
    }
}
