package local.diamondfinder;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MatchFix {
    private static int dumps;
    private static boolean migrated;

    private MatchFix() {
    }

    private static boolean mentionsEffect(List<String> var0) {
        if (var0 == null) {
            return false;
        }

        for (String var2 : var0) {
            if (CosmeticText.normalize(var2).contains("эффект")) {
                return true;
            }
        }

        return false;
    }

    private static boolean unavailable(String var0, List<String> var1) {
        if (CosmeticText.clean(var0).matches(".*[✗✘✖✕❌]\\s*$")) {
            return true;
        }

        if (var1 == null) {
            return false;
        }

        for (String var3 : var1) {
            String var4 = CosmeticText.normalize(var3);
            if (!var4.contains("эффект")
                && (var4.contains("не получен") || var4.contains("не приобретен") || var4.contains("не приобретён") || var4.contains("нет в наличии"))) {
                return true;
            }
        }

        return false;
    }

    private static String strip(String var0) {
        return var0.replaceFirst("\\s*\\([^)]*\\)\\s*$", "").replaceFirst("[^\\p{L}\\p{N}]+$", "").trim();
    }

    private static String exact(String var0, CosmeticText.Rarity var1, Map<String, String> var2, Set<String> var3) {
        if (var1.tradable()) {
            String var5 = var0 + "|" + var1.name();
            return var3.contains(var5) ? var5 : null;
        } else {
            String var4 = (String)var2.get(var0);
            return var4 != null && var3.contains(var4) ? var4 : null;
        }
    }

    private static String byName(String var0, Map<String, String> var1, Set<String> var2) {
        String var3 = (String)var1.get(var0);
        if (var3 != null && var2.contains(var3)) {
            return var3;
        }

        String var4 = null;
        int var5 = 0;
        String var6 = var0 + "|";

        for (String var8 : var2) {
            if (var8.startsWith(var6)) {
                var4 = var8;
                if (++var5 > 1) {
                    return null;
                }
            }
        }

        return var5 == 1 ? var4 : null;
    }

    public static String match(String var0, List<String> var1, Map<String, String> var2, Set<String> var3) {
        String var4 = null;
        String var5 = null;
        boolean var6 = mentionsEffect(var1);
        if (unavailable(var0, var1)) {
            var5 = "не получен";
        } else {
            CosmeticText.Rarity var7 = CosmeticText.rarity(var1);
            String var8 = DiamondMenus.baseName(var0);
            if (var7 == CosmeticText.Rarity.EXCLUDED && !var6) {
                var5 = "исключена из каталога";
            } else {
                if (var7 != CosmeticText.Rarity.EXCLUDED) {
                    var4 = exact(var8, var7, var2, var3);
                }

                if (var4 == null) {
                    var4 = byName(var8, var2, var3);
                }

                if (var4 == null) {
                    String var9 = strip(var8);
                    if (!var9.isEmpty() && !var9.equals(var8)) {
                        if (var7 != CosmeticText.Rarity.EXCLUDED) {
                            var4 = exact(var9, var7, var2, var3);
                        }

                        if (var4 == null) {
                            var4 = byName(var9, var2, var3);
                        }
                    }
                }

                if (var4 == null) {
                    var5 = "нет в каталоге (" + var7 + ")";
                }
            }
        }

        ProfileInfo.noteMatch(var4, var0, var1);
        if (var4 == null && var6 && dumps < 40) {
            dump(var0, var5, var1);
        }

        return var4;
    }

    private static void dump(String var0, String var1, List<String> var2) {
        try {
            dumps++;
            StringBuilder var3 = new StringBuilder("item: ").append(var0).append("  =>  НЕ РАСПОЗНАНА: ").append(var1).append('\n');

            for (String var5 : var2) {
                var3.append("    ").append(var5).append('\n');
            }

            Path var7 = Path.of("config", "diamond-finder");
            Files.createDirectories(var7);
            Files.writeString(var7.resolve("unmatched-dump.txt"), var3.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable var6) {
        }
    }

    public static void migrate(MenuScanner var0) {
        if (!migrated) {
            migrated = true;

            try {
                Path var1 = Path.of("config", "diamond-finder");
                Path var2 = var1.resolve("rescan-v2.done");
                if (Files.exists(var2)) {
                    return;
                }

                Files.createDirectories(var1);

                for (MenuScanner.Result var4 : new ArrayList<>(var0.results)) {
                    var4.time = 0L;
                }

                Field var10 = Class.forName("local.diamondfinder.ProfileDb").getDeclaredField("FILE");
                var10.setAccessible(true);
                Path var11 = (Path)var10.get(null);
                if (Files.exists(var11)) {
                    ArrayList var5 = new ArrayList();

                    for (String var7 : Files.readAllLines(var11)) {
                        String[] var8 = var7.split("\t", -1);
                        if (var8.length > 3 && var8[0].equals("v2")) {
                            var8[2] = "0";
                        }

                        var5.add(String.join("\t", var8));
                    }

                    Files.write(var11, var5);
                }

                Files.writeString(var2, "ok\n");
            } catch (Throwable var9) {
            }
        }
    }

    static String lower(String var0) {
        return var0 == null ? "" : var0.toLowerCase(Locale.ROOT);
    }
}
