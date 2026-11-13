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

    public static void skip(Deque<String> queue, List<MenuScanner.Result> results) {
        Set<String> nicks = new HashSet<>();

        for (String nick : queue) {
            nicks.add(nick.toLowerCase(Locale.ROOT));
        }

        last = nicks;
        loaded = true;
        save();
        ProfileDb.skip(queue, results);
    }

    public static List<MenuScanner.Result> filter(List<MenuScanner.Result> results) {
        load();
        List<MenuScanner.Result> filtered = new ArrayList<>();

        for (MenuScanner.Result result : results) {
            if (result.player != null && last.contains(result.player.toLowerCase(Locale.ROOT)) && !Blacklist.isBlocked(result.player)) {
                filtered.add(result);
            }
        }

        return filtered;
    }

    private static void load() {
        if (!loaded && DiamondFinder.catalog != null) {
            loaded = true;

            try {
                Path path = file();
                if (Files.exists(path)) {
                    for (String line : Files.readAllLines(path)) {
                        if (!line.isBlank()) {
                            last.add(line.trim());
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean known(String nick) {
        load();
        return last.contains(nick.toLowerCase(Locale.ROOT));
    }

    public static void add(String nick) {
        load();
        if (last.add(nick.toLowerCase(Locale.ROOT))) {
            save();
        }
    }

    private static void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, String.join("\n", last) + "\n");
        } catch (Throwable ignored) {
        }
    }

    private static Path file() {
        return DiamondFinder.catalog.dir.resolve("last-scan.txt");
    }
}
