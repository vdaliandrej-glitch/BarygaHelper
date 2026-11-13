package local.diamondfinder;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Extra {
    private static final int RED = -49088;
    private static final int EFFECT = -2644225;
    private static final int MIN_COSMETICS = 10;
    private static final Set<String> BAD = Collections.synchronizedSet(new HashSet<>());
    private static final String[] MONTHS = new String[]{
        "января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"
    };
    private static final Pattern YMD = Pattern.compile("(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})");
    private static final Pattern DMY = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})");
    private static final Pattern RU = Pattern.compile("(\\d{1,2})\\s+([\\p{L}]+)\\s+(\\d{4})", 64);

    private Extra() {
    }

    static LocalDate parse(String var0) {
        if (var0 == null) {
            return null;
        }

        try {
            Matcher var1 = YMD.matcher(var0);
            if (var1.find()) {
                return LocalDate.of(Integer.parseInt(var1.group(1)), Integer.parseInt(var1.group(2)), Integer.parseInt(var1.group(3)));
            }

            var1 = RU.matcher(var0);

            while (var1.find()) {
                String var2 = var1.group(2).toLowerCase(Locale.ROOT);

                for (int var3 = 0; var3 < 12; var3++) {
                    if (MONTHS[var3].equals(var2)) {
                        return LocalDate.of(Integer.parseInt(var1.group(3)), var3 + 1, Integer.parseInt(var1.group(1)));
                    }
                }
            }

            var1 = DMY.matcher(var0);
            if (var1.find()) {
                int var7 = Integer.parseInt(var1.group(3));
                if (var7 < 100) {
                    var7 += 2000;
                }

                return LocalDate.of(var7, Integer.parseInt(var1.group(2)), Integer.parseInt(var1.group(1)));
            }
        } catch (RuntimeException var4) {
        }

        return null;
    }

    static boolean suspicious(MenuScanner.Result var0) {
        if (var0 != null && var0.regDate != null && var0.owned != null) {
            if (var0.owned.size() <= 10) {
                return false;
            }

            LocalDate var1 = parse(var0.regDate);
            return var1 == null ? false : var1.isAfter(LocalDate.now().minusYears(1L));
        } else {
            return false;
        }
    }

    static String effectText(MenuScanner.Result var0) {
        Map<String, String> var1 = var0.effects;
        if (var1 != null && !var1.isEmpty()) {
            LinkedHashSet<String> var2 = new LinkedHashSet<>();
            if (var0.found != null) {
                for (String var4 : var0.found) {
                    String var5 = (String)var1.get(var4);
                    if (var5 != null && !var5.isBlank()) {
                        var2.add(var5);
                    }
                }
            }

            if (var2.isEmpty()) {
                for (String var8 : var1.values()) {
                    if (var8 != null && !var8.isBlank()) {
                        var2.add(var8);
                    }
                }
            }

            if (var2.isEmpty()) {
                return null;
            }

            String var7 = (String)var2.iterator().next();
            int var9 = var2.size() - 1;
            return "эффект: " + var7 + (var9 > 0 ? " +" + var9 : "");
        } else {
            return null;
        }
    }

    public static void extra(MenuScanner.Result var0, List<String> var1, List<Integer> var2) {
        boolean var3 = suspicious(var0);
        if (var0.player != null) {
            if (var3) {
                BAD.add(var0.player);
            } else {
                BAD.remove(var0.player);
            }
        }

        if (var3 && var0.regDate != null) {
            for (int var4 = 0; var4 < var1.size() && var4 < var2.size(); var4++) {
                if (((String)var1.get(var4)).contains(var0.regDate)) {
                    var2.set(var4, -49088);
                }
            }
        }

        String var5 = effectText(var0);
        if (var5 != null) {
            var1.add(var5);
            var2.add(-2644225);
        }
    }

    static boolean isBad(String var0) {
        if (var0 == null || BAD.isEmpty()) {
            return false;
        }

        if (BAD.contains(var0)) {
            return true;
        }

        String var1 = null;
        if (var0.endsWith("…")) {
            var1 = var0.substring(0, var0.length() - 1);
        } else if (var0.endsWith("...")) {
            var1 = var0.substring(0, var0.length() - 3);
        }

        if (var1 != null && !var1.isEmpty()) {
            synchronized (BAD) {
                for (String var4 : BAD) {
                    if (var4.startsWith(var1)) {
                        return true;
                    }
                }

                return false;
            }
        } else {
            return false;
        }
    }

    public static void nameText(FinderScreen var0, GuiGraphicsExtractor var1, String var2, int var3, int var4, int var5) {
        var0.text(var1, var2, var3, var4, isBad(var2) ? -49088 : var5);
    }
}
