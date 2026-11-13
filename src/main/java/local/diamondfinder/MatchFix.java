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
    private static final int MAX_DUMPS = 40;
    private static int dumps;
    private static boolean migrated;

    private MatchFix() {
    }

    private static boolean mentionsEffect(List<String> lore) {
        if (lore == null) {
            return false;
        }

        for (String line : lore) {
            if (CosmeticText.normalize(line).contains("эффект")) {
                return true;
            }
        }

        return false;
    }

    private static boolean unavailable(String name, List<String> lore) {
        if (CosmeticText.clean(name).matches(".*[✗✘✖✕❌]\\s*$")) {
            return true;
        }

        if (lore == null) {
            return false;
        }

        for (String line : lore) {
            String text = CosmeticText.normalize(line);
            if (!text.contains("эффект")
                && (text.contains("не получен") || text.contains("не приобретен") || text.contains("не приобретён") || text.contains("нет в наличии"))) {
                return true;
            }
        }

        return false;
    }

    private static String strip(String name) {
        return name.replaceFirst("\\s*\\([^)]*\\)\\s*$", "").replaceFirst("[^\\p{L}\\p{N}]+$", "").trim();
    }

    private static String exact(String name, CosmeticText.Rarity rarity, Map<String, String> byName, Set<String> keys) {
        if (rarity.tradable()) {
            String key = name + "|" + rarity.name();
            return keys.contains(key) ? key : null;
        }

        String key = byName.get(name);
        return key != null && keys.contains(key) ? key : null;
    }

    private static String byName(String name, Map<String, String> byName, Set<String> keys) {
        String direct = byName.get(name);
        if (direct != null && keys.contains(direct)) {
            return direct;
        }

        String match = null;
        int count = 0;
        String prefix = name + "|";

        for (String key : keys) {
            if (key.startsWith(prefix)) {
                match = key;
                if (++count > 1) {
                    return null;
                }
            }
        }

        return count == 1 ? match : null;
    }

    public static String match(String name, List<String> lore, Map<String, String> byName, Set<String> keys) {
        String key = null;
        String reason = null;
        boolean hasEffect = mentionsEffect(lore);
        if (unavailable(name, lore)) {
            reason = "не получен";
        } else {
            CosmeticText.Rarity rarity = CosmeticText.rarity(lore);
            String base = DiamondMenus.baseName(name);
            if (rarity == CosmeticText.Rarity.EXCLUDED && !hasEffect) {
                reason = "исключена из каталога";
            } else {
                if (rarity != CosmeticText.Rarity.EXCLUDED) {
                    key = exact(base, rarity, byName, keys);
                }

                if (key == null) {
                    key = byName(base, byName, keys);
                }

                if (key == null) {
                    String stripped = strip(base);
                    if (!stripped.isEmpty() && !stripped.equals(base)) {
                        if (rarity != CosmeticText.Rarity.EXCLUDED) {
                            key = exact(stripped, rarity, byName, keys);
                        }

                        if (key == null) {
                            key = byName(stripped, byName, keys);
                        }
                    }
                }

                if (key == null) {
                    reason = "нет в каталоге (" + rarity + ")";
                }
            }
        }

        ProfileInfo.noteMatch(key, name, lore);
        if (key == null && hasEffect && dumps < MAX_DUMPS) {
            dump(name, reason, lore);
        }

        return key;
    }

    private static void dump(String name, String reason, List<String> lore) {
        try {
            dumps++;
            StringBuilder text = new StringBuilder("item: ").append(name).append("  =>  НЕ РАСПОЗНАНА: ").append(reason).append('\n');

            for (String line : lore) {
                text.append("    ").append(line).append('\n');
            }

            Path dir = Path.of("config", "diamond-finder");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("unmatched-dump.txt"), text.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
        }
    }

    public static void migrate(MenuScanner scanner) {
        if (migrated) {
            return;
        }

        migrated = true;

        try {
            Path dir = Path.of("config", "diamond-finder");
            Path marker = dir.resolve("rescan-v2.done");
            if (Files.exists(marker)) {
                return;
            }

            Files.createDirectories(dir);

            for (MenuScanner.Result result : new ArrayList<>(scanner.results)) {
                result.time = 0L;
            }

            Field fileField = ProfileDb.class.getDeclaredField("FILE");
            fileField.setAccessible(true);
            Path db = (Path) fileField.get(null);
            if (Files.exists(db)) {
                List<String> lines = new ArrayList<>();

                for (String line : Files.readAllLines(db)) {
                    String[] parts = line.split("\t", -1);
                    if (parts.length > 3 && parts[0].equals("v2")) {
                        parts[2] = "0";
                    }

                    lines.add(String.join("\t", parts));
                }

                Files.write(db, lines);
            }

            Files.writeString(marker, "ok\n");
        } catch (Throwable ignored) {
        }
    }

    static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
