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
import java.util.Map.Entry;

public final class ProfileDb {
    private static final Path DIR = Path.of("config", "diamond-finder");
    private static final Path FILE = DIR.resolve("players.db");
    private static final Path FOUND_FILE = DIR.resolve("found.txt");
    private static final long TTL = 604800000L;
    private static final long TTL_HIDDEN = 86400000L;
    private static final int MAX_SCANS_FOR_GAPS = 3;
    private static final char SET = '\u0001';
    private static final char KV = '\u0002';
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

    public static void setEffectMode(int var0) {
        effectMode = var0;
        sig = Integer.MIN_VALUE;
    }

    public static boolean has(MenuScanner.Result var0, String var1) {
        if (var0.owned == null || !var0.owned.contains(var1)) {
            return false;
        }

        if (effectMode == 0) {
            return true;
        }

        boolean var2 = var0.effects != null && var0.effects.containsKey(var1);
        return effectMode == 1 ? var2 : !var2;
    }

    private static void ensureFound() {
        if (!foundLoaded) {
            foundLoaded = true;

            try {
                if (Files.exists(FOUND_FILE)) {
                    for (String var1 : Files.readAllLines(FOUND_FILE, StandardCharsets.UTF_8)) {
                        if (!var1.isBlank()) {
                            FOUND.add(var1.trim());
                        }
                    }
                }
            } catch (Throwable var2) {
            }
        }
    }

    private static void saveFound() {
        try {
            Files.createDirectories(DIR);
            Files.writeString(FOUND_FILE, String.join("\n", FOUND) + "\n", StandardCharsets.UTF_8);
        } catch (Throwable var1) {
        }
    }

    public static boolean isFound(String var0) {
        ensureFound();
        return FOUND.contains(var0);
    }

    public static List<String> foundKeys() {
        ensureFound();
        return new ArrayList<>(FOUND);
    }

    public static void markFound(String var0) {
        ensureFound();
        if (FOUND.add(var0)) {
            saveFound();
        }

        Catalog var1 = DiamondFinder.catalog;
        if (var1 != null) {
            var1.selected.remove(var0);
            var1.dirty();
        }

        sig = Integer.MIN_VALUE;
    }

    public static void unmarkFound(String var0) {
        ensureFound();
        if (FOUND.remove(var0)) {
            saveFound();
        }

        sig = Integer.MIN_VALUE;
    }

    public static List<Catalog.Entry> hideFound(List<Catalog.Entry> var0) {
        ensureFound();
        if (!FOUND.isEmpty() && var0 != null) {
            ArrayList var1 = new ArrayList(var0.size());

            for (Catalog.Entry var3 : var0) {
                if (!FOUND.contains(var3.key)) {
                    var1.add(var3);
                }
            }

            return var1;
        } else {
            return var0;
        }
    }

    public static void beforeScan(List<MenuScanner.Result> var0) {
        var0.removeIf(var0x -> !var0x.complete);
        skipped = 0;
    }

    private static boolean expired(MenuScanner.Result var0, long var1) {
        long var3 = var0.coverage != null && var0.coverage.hidden() ? 86400000L : 604800000L;
        return var0.time <= 0L || var1 - var0.time > var3;
    }

    public static boolean incomplete(MenuScanner.Result var0) {
        if (var0.scans >= 3) {
            return false;
        }

        boolean var1 = var0.coverage != null && !var0.coverage.hidden() && !var0.coverage.full();
        return var0.rank == null || var0.regDate == null || var1;
    }

    public static void skip(Deque<String> var0, List<MenuScanner.Result> var1) {
        try {
            long var2 = System.currentTimeMillis();
            HashSet var4 = new HashSet();

            for (MenuScanner.Result var6 : var1) {
                if (var6.complete && !expired(var6, var2) && !incomplete(var6)) {
                    var4.add(var6.player.toLowerCase(Locale.ROOT));
                }
            }

            int var8 = var0.size();
            var0.removeIf(var1x -> var4.contains(var1x.toLowerCase(Locale.ROOT)));
            skipped = var8 - var0.size();
        } catch (Throwable var7) {
            skipped = 0;
        }
    }

    public static String note(String var0) {
        return skipped > 0 && var0 != null ? var0 + " · из базы пропущено: " + skipped : var0;
    }

    public static void onFinish(MenuScanner.Result var0, boolean var1, Set<String> var2, List<MenuScanner.Result> var3) {
        if (var0 != null && var1) {
            try {
                MenuScanner.Result var4 = null;

                for (MenuScanner.Result var6 : var3) {
                    if (var6 != var0 && var6.player.equalsIgnoreCase(var0.player)) {
                        var4 = var6;
                    }
                }

                var0.scans = (var4 != null ? var4.scans : 0) + 1;
                if (var4 != null) {
                    if (var0.rank == null) {
                        var0.rank = var4.rank;
                    }

                    if (var0.regDate == null) {
                        var0.regDate = var4.regDate;
                    }

                    if (var0.level == null) {
                        var0.level = var4.level;
                    }
                }

                var0.owned = new HashSet<>(var0.found);
                var0.ownedEq = new HashSet<>(var0.equipped);
                Map var8 = ProfileInfo.takeEffects(var0.player);
                var8.keySet().retainAll(var0.owned);
                var0.effects = var8;
                var0.complete = true;
                var0.found.retainAll(var2);
                var0.equipped.retainAll(var2);
                var3.removeIf(var1x -> var1x != var0 && var1x.player.equalsIgnoreCase(var0.player));
                Files.createDirectories(DIR);
                Files.writeString(FILE, encode(var0) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                sig = Integer.MIN_VALUE;
            } catch (Throwable var7) {
            }
        }
    }

    public static void load(List<MenuScanner.Result> var0, Set<String> var1) {
        try {
            if (!Files.exists(FILE)) {
                return;
            }

            LinkedHashMap<String, MenuScanner.Result> var2 = new LinkedHashMap<>();

            for (String var4 : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                try {
                    MenuScanner.Result var5 = decode(var4);
                    if (var5 != null) {
                        String var6 = var5.player.toLowerCase(Locale.ROOT);
                        var2.remove(var6);
                        var2.put(var6, var5);
                    }
                } catch (Throwable var7) {
                }
            }

            var0.addAll(var2.values());
            sig = Integer.MIN_VALUE;
            StringBuilder var9 = new StringBuilder();

            for (MenuScanner.Result var11 : var2.values()) {
                var9.append(encode(var11)).append('\n');
            }

            Files.writeString(FILE, var9.toString(), StandardCharsets.UTF_8);
        } catch (Throwable var8) {
        }
    }

    public static void refresh(List<MenuScanner.Result> var0, Set<String> var1) {
        int var2 = var1.hashCode() * 31 + var1.size() + effectMode * 1000003;
        if (var2 != sig) {
            sig = var2;

            for (MenuScanner.Result var4 : var0) {
                if (var4.complete && var4.owned != null) {
                    var4.found.clear();

                    for (String var6 : var1) {
                        if (has(var4, var6)) {
                            var4.found.add(var6);
                        }
                    }

                    var4.equipped.clear();
                    if (var4.ownedEq != null) {
                        var4.equipped.addAll(var4.ownedEq);
                        var4.equipped.retainAll(var4.found);
                    }
                }
            }
        }
    }

    private static String esc(String var0) {
        return var0 == null ? "" : var0.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static String join(Collection<String> var0) {
        return var0 == null ? "" : String.join(String.valueOf('\u0001'), var0);
    }

    private static Set<String> split(String var0) {
        HashSet var1 = new HashSet();
        if (!var0.isEmpty()) {
            var1.addAll(Arrays.asList(var0.split(String.valueOf('\u0001'))));
        }

        return var1;
    }

    private static String nul(String var0) {
        return var0.isEmpty() ? null : var0;
    }

    private static Object field(Object var0, String var1) throws Exception {
        Field var2 = var0.getClass().getDeclaredField(var1);
        var2.setAccessible(true);
        return var2.get(var0);
    }

    private static List<String> names(Object var0) {
        ArrayList var1 = new ArrayList();

        for (Object var3 : (Collection)var0) {
            var1.add(((Enum)var3).name());
        }

        return var1;
    }

    private static String encodeCoverage(ScanCoverage var0) {
        try {
            return ((Boolean)field(var0, "showcase") ? "1" : "0")
                + "|"
                + ((Boolean)field(var0, "hidden") ? "1" : "0")
                + "|"
                + String.join(",", names(field(var0, "completed")))
                + "|"
                + String.join(",", names(field(var0, "attempted")));
        } catch (Throwable var2) {
            return "0|0||";
        }
    }

    private static void decodeCoverage(ScanCoverage var0, String var1) throws Exception {
        String[] var2 = var1.split("\\|", -1);
        if (var2.length >= 4) {
            Class var3 = Class.forName("local.diamondfinder.ScanCoverage$Category");
            Field var4 = var0.getClass().getDeclaredField("showcase");
            var4.setAccessible(true);
            var4.set(var0, var2[0].equals("1"));
            var4 = var0.getClass().getDeclaredField("hidden");
            var4.setAccessible(true);
            var4.set(var0, var2[1].equals("1"));

            for (int var5 = 2; var5 <= 3; var5++) {
                Collection var6 = (Collection)field(var0, var5 == 2 ? "completed" : "attempted");
                if (!var2[var5].isEmpty()) {
                    for (String var10 : var2[var5].split(",")) {
                        var6.add(Enum.valueOf(var3, var10));
                    }
                }
            }
        }
    }

    private static String encodeEffects(Map<String, String> var0) {
        if (var0 != null && !var0.isEmpty()) {
            ArrayList var1 = new ArrayList();

            for (Entry var3 : var0.entrySet()) {
                var1.add((String)var3.getKey() + esc((String)var3.getValue()).replace(String.valueOf('\u0001'), " "));
            }

            return String.join(String.valueOf('\u0001'), var1);
        } else {
            return "";
        }
    }

    private static Map<String, String> decodeEffects(String var0) {
        HashMap var1 = new HashMap();
        if (var0.isEmpty()) {
            return var1;
        }

        for (String var5 : var0.split(String.valueOf('\u0001'))) {
            int var6 = var5.indexOf(2);
            if (var6 > 0) {
                var1.put(var5.substring(0, var6), var5.substring(var6 + 1));
            }
        }

        return var1;
    }

    private static String encode(MenuScanner.Result var0) {
        return String.join(
            "\t",
            "v2",
            esc(var0.player),
            String.valueOf(var0.time),
            esc(var0.status),
            esc(var0.rank),
            esc(var0.regDate),
            esc(var0.level),
            join(var0.owned),
            join(var0.ownedEq),
            encodeCoverage(var0.coverage),
            encodeEffects(var0.effects),
            String.valueOf(var0.scans)
        );
    }

    private static MenuScanner.Result decode(String var0) throws Exception {
        String[] var1 = var0.split("\t", -1);
        boolean var2 = var1.length >= 12 && var1[0].equals("v2");
        if ((var2 || var1.length >= 10 && var1[0].equals("v1")) && !var1[1].isEmpty()) {
            MenuScanner.Result var3 = new MenuScanner.Result(var1[1]);
            var3.time = Long.parseLong(var1[2]);
            var3.status = nul(var1[3]);
            var3.rank = nul(var1[4]);
            var3.regDate = nul(var1[5]);
            var3.level = nul(var1[6]);
            var3.owned = split(var1[7]);
            var3.ownedEq = split(var1[8]);
            decodeCoverage(var3.coverage, var1[9]);
            var3.effects = var2 ? decodeEffects(var1[10]) : new HashMap();
            var3.scans = var2 ? Integer.parseInt(var1[11]) : 1;
            var3.complete = true;
            return var3;
        } else {
            return null;
        }
    }
}
