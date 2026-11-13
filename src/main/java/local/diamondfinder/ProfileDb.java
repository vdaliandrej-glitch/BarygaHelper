package local.diamondfinder;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ProfileDb {
    private static final Path DIR = Path.of("config", "diamond-finder");
    private static final Path FILE = DIR.resolve("players.db");
    private static final Path FOUND_FILE = DIR.resolve("found.txt");
    private static final long TTL = 7L * 24 * 60 * 60 * 1000;
    private static final long TTL_HIDDEN = 24L * 60 * 60 * 1000;
    private static final int MAX_SCANS_FOR_GAPS = 3;
    private static final char SET = '\u0001';
    private static final char KV = '\u0002';
    private static final String SET_SEP = String.valueOf(SET);
    private static final Set<String> FOUND = new LinkedHashSet<>();
    private static boolean foundLoaded;
    private static int sig = Integer.MIN_VALUE;
    private static int skipped;
    private static int effectMode;

    private ProfileDb() {
    }

    public static int effectMode() {
        return effectMode;
    }

    public static void setEffectMode(int mode) {
        effectMode = mode;
        sig = Integer.MIN_VALUE;
    }

    public static boolean has(MenuScanner.Result result, String key) {
        if (result.owned == null || !result.owned.contains(key)) {
            return false;
        }

        if (effectMode == 0) {
            return true;
        }

        boolean hasEffect = result.effects != null && result.effects.containsKey(key);
        return effectMode == 1 ? hasEffect : !hasEffect;
    }

    private static void ensureFound() {
        if (foundLoaded) {
            return;
        }

        foundLoaded = true;

        try {
            if (Files.exists(FOUND_FILE)) {
                for (String line : Files.readAllLines(FOUND_FILE, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        FOUND.add(line.trim());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void saveFound() {
        try {
            Files.createDirectories(DIR);
            Files.writeString(FOUND_FILE, String.join("\n", FOUND) + "\n", StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    public static boolean isFound(String key) {
        ensureFound();
        return FOUND.contains(key);
    }

    public static List<String> foundKeys() {
        ensureFound();
        return new ArrayList<>(FOUND);
    }

    public static void markFound(String key) {
        ensureFound();
        if (FOUND.add(key)) {
            saveFound();
        }

        Catalog catalog = DiamondFinder.catalog;
        if (catalog != null) {
            catalog.selected.remove(key);
            catalog.dirty();
        }

        sig = Integer.MIN_VALUE;
    }

    public static void unmarkFound(String key) {
        ensureFound();
        if (FOUND.remove(key)) {
            saveFound();
        }

        sig = Integer.MIN_VALUE;
    }

    public static List<Catalog.Entry> hideFound(List<Catalog.Entry> entries) {
        ensureFound();
        if (FOUND.isEmpty() || entries == null) {
            return entries;
        }

        List<Catalog.Entry> visible = new ArrayList<>(entries.size());

        for (Catalog.Entry entry : entries) {
            if (!FOUND.contains(entry.key)) {
                visible.add(entry);
            }
        }

        return visible;
    }

    public static void beforeScan(List<MenuScanner.Result> results) {
        results.removeIf(result -> !result.complete);
        skipped = 0;
    }

    private static boolean expired(MenuScanner.Result result, long now) {
        long ttl = result.coverage != null && result.coverage.hidden() ? TTL_HIDDEN : TTL;
        return result.time <= 0L || now - result.time > ttl;
    }

    public static boolean incomplete(MenuScanner.Result result) {
        if (result.scans >= MAX_SCANS_FOR_GAPS) {
            return false;
        }

        boolean partial = result.coverage != null && !result.coverage.hidden() && !result.coverage.full();
        return result.rank == null || result.regDate == null || partial;
    }

    public static void skip(Deque<String> queue, List<MenuScanner.Result> results) {
        try {
            long now = System.currentTimeMillis();
            Set<String> fresh = new HashSet<>();

            for (MenuScanner.Result result : results) {
                if (result.complete && !expired(result, now) && !incomplete(result)) {
                    fresh.add(result.player.toLowerCase(Locale.ROOT));
                }
            }

            int before = queue.size();
            queue.removeIf(nick -> fresh.contains(nick.toLowerCase(Locale.ROOT)));
            skipped = before - queue.size();
        } catch (Throwable e) {
            skipped = 0;
        }
    }

    public static String note(String message) {
        return skipped > 0 && message != null ? message + " · из базы пропущено: " + skipped : message;
    }

    public static void onFinish(MenuScanner.Result result, boolean success, Set<String> selected, List<MenuScanner.Result> results) {
        if (result == null || !success) {
            return;
        }

        try {
            MenuScanner.Result previous = null;

            for (MenuScanner.Result other : results) {
                if (other != result && other.player.equalsIgnoreCase(result.player)) {
                    previous = other;
                }
            }

            result.scans = (previous != null ? previous.scans : 0) + 1;
            if (previous != null) {
                if (result.rank == null) {
                    result.rank = previous.rank;
                }

                if (result.regDate == null) {
                    result.regDate = previous.regDate;
                }

                if (result.level == null) {
                    result.level = previous.level;
                }
            }

            result.owned = new HashSet<>(result.found);
            result.ownedEq = new HashSet<>(result.equipped);
            Map<String, String> effects = ProfileInfo.takeEffects(result.player);
            effects.keySet().retainAll(result.owned);
            result.effects = effects;
            result.complete = true;
            result.found.retainAll(selected);
            result.equipped.retainAll(selected);
            results.removeIf(other -> other != result && other.player.equalsIgnoreCase(result.player));
            Files.createDirectories(DIR);
            Files.writeString(FILE, encode(result) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            sig = Integer.MIN_VALUE;
        } catch (Throwable ignored) {
        }
    }

    public static void load(List<MenuScanner.Result> results, Set<String> selected) {
        try {
            if (!Files.exists(FILE)) {
                return;
            }

            Map<String, MenuScanner.Result> byNick = new LinkedHashMap<>();

            for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                try {
                    MenuScanner.Result result = decode(line);
                    if (result != null) {
                        String nick = result.player.toLowerCase(Locale.ROOT);
                        byNick.remove(nick);
                        byNick.put(nick, result);
                    }
                } catch (Throwable ignored) {
                }
            }

            results.addAll(byNick.values());
            sig = Integer.MIN_VALUE;
            StringBuilder compacted = new StringBuilder();

            for (MenuScanner.Result result : byNick.values()) {
                compacted.append(encode(result)).append('\n');
            }

            Files.writeString(FILE, compacted.toString(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    public static void refresh(List<MenuScanner.Result> results, Set<String> selected) {
        int current = selected.hashCode() * 31 + selected.size() + effectMode * 1000003;
        if (current == sig) {
            return;
        }

        sig = current;

        for (MenuScanner.Result result : results) {
            if (result.complete && result.owned != null) {
                result.found.clear();

                for (String key : selected) {
                    if (has(result, key)) {
                        result.found.add(key);
                    }
                }

                result.equipped.clear();
                if (result.ownedEq != null) {
                    result.equipped.addAll(result.ownedEq);
                    result.equipped.retainAll(result.found);
                }
            }
        }
    }

    private static String esc(String text) {
        return text == null ? "" : text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static String join(Collection<String> values) {
        return values == null ? "" : String.join(SET_SEP, values);
    }

    private static Set<String> split(String text) {
        Set<String> values = new HashSet<>();
        if (!text.isEmpty()) {
            values.addAll(Arrays.asList(text.split(SET_SEP)));
        }

        return values;
    }

    private static String nul(String text) {
        return text.isEmpty() ? null : text;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static List<String> names(Object values) {
        List<String> names = new ArrayList<>();

        for (Object value : (Collection<?>) values) {
            names.add(((Enum<?>) value).name());
        }

        return names;
    }

    private static String encodeCoverage(ScanCoverage coverage) {
        try {
            return ((Boolean) field(coverage, "showcase") ? "1" : "0")
                + "|"
                + ((Boolean) field(coverage, "hidden") ? "1" : "0")
                + "|"
                + String.join(",", names(field(coverage, "completed")))
                + "|"
                + String.join(",", names(field(coverage, "attempted")));
        } catch (Throwable e) {
            return "0|0||";
        }
    }

    @SuppressWarnings("unchecked")
    private static void decodeCoverage(ScanCoverage coverage, String text) throws Exception {
        String[] parts = text.split("\\|", -1);
        if (parts.length < 4) {
            return;
        }

        Field showcase = coverage.getClass().getDeclaredField("showcase");
        showcase.setAccessible(true);
        showcase.set(coverage, parts[0].equals("1"));
        Field hidden = coverage.getClass().getDeclaredField("hidden");
        hidden.setAccessible(true);
        hidden.set(coverage, parts[1].equals("1"));

        for (int i = 2; i <= 3; i++) {
            Collection<ScanCoverage.Category> categories = (Collection<ScanCoverage.Category>) field(coverage, i == 2 ? "completed" : "attempted");
            if (!parts[i].isEmpty()) {
                for (String name : parts[i].split(",")) {
                    categories.add(ScanCoverage.Category.valueOf(name));
                }
            }
        }
    }

    private static String encodeEffects(Map<String, String> effects) {
        if (effects == null || effects.isEmpty()) {
            return "";
        }

        List<String> pairs = new ArrayList<>();

        for (Map.Entry<String, String> entry : effects.entrySet()) {
            pairs.add(entry.getKey() + KV + esc(entry.getValue()).replace(SET_SEP, " "));
        }

        return String.join(SET_SEP, pairs);
    }

    private static Map<String, String> decodeEffects(String text) {
        Map<String, String> effects = new HashMap<>();
        if (text.isEmpty()) {
            return effects;
        }

        for (String pair : text.split(SET_SEP)) {
            int split = pair.indexOf(KV);
            if (split > 0) {
                effects.put(pair.substring(0, split), pair.substring(split + 1));
            }
        }

        return effects;
    }

    private static String encode(MenuScanner.Result result) {
        return String.join(
            "\t",
            "v2",
            esc(result.player),
            String.valueOf(result.time),
            esc(result.status),
            esc(result.rank),
            esc(result.regDate),
            esc(result.level),
            join(result.owned),
            join(result.ownedEq),
            encodeCoverage(result.coverage),
            encodeEffects(result.effects),
            String.valueOf(result.scans)
        );
    }

    private static MenuScanner.Result decode(String line) throws Exception {
        String[] parts = line.split("\t", -1);
        boolean v2 = parts.length >= 12 && parts[0].equals("v2");
        boolean v1 = parts.length >= 10 && parts[0].equals("v1");
        if (!(v2 || v1) || parts[1].isEmpty()) {
            return null;
        }

        MenuScanner.Result result = new MenuScanner.Result(parts[1]);
        result.time = Long.parseLong(parts[2]);
        result.status = nul(parts[3]);
        result.rank = nul(parts[4]);
        result.regDate = nul(parts[5]);
        result.level = nul(parts[6]);
        result.owned = split(parts[7]);
        result.ownedEq = split(parts[8]);
        decodeCoverage(result.coverage, parts[9]);
        result.effects = v2 ? decodeEffects(parts[10]) : new HashMap<>();
        result.scans = v2 ? Integer.parseInt(parts[11]) : 1;
        result.complete = true;
        return result;
    }
}
