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
    private static final int RED = 0xFFFF4040;
    private static final int EFFECT = 0xFFD7A6FF;
    private static final int MIN_COSMETICS = 10;
    private static final Set<String> BAD = Collections.synchronizedSet(new HashSet<>());
    private static final String[] MONTHS = new String[]{
        "января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"
    };
    private static final Pattern YMD = Pattern.compile("(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})");
    private static final Pattern DMY = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})");
    private static final Pattern RU = Pattern.compile("(\\d{1,2})\\s+([\\p{L}]+)\\s+(\\d{4})", Pattern.UNICODE_CASE);

    private Extra() {
    }

    static LocalDate parse(String text) {
        if (text == null) {
            return null;
        }

        try {
            Matcher m = YMD.matcher(text);
            if (m.find()) {
                return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            }

            m = RU.matcher(text);

            while (m.find()) {
                String month = m.group(2).toLowerCase(Locale.ROOT);

                for (int i = 0; i < 12; i++) {
                    if (MONTHS[i].equals(month)) {
                        return LocalDate.of(Integer.parseInt(m.group(3)), i + 1, Integer.parseInt(m.group(1)));
                    }
                }
            }

            m = DMY.matcher(text);
            if (m.find()) {
                int year = Integer.parseInt(m.group(3));
                if (year < 100) {
                    year += 2000;
                }

                return LocalDate.of(year, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
            }
        } catch (RuntimeException ignored) {
        }

        return null;
    }

    static boolean suspicious(MenuScanner.Result result) {
        if (result == null || result.regDate == null || result.owned == null) {
            return false;
        }

        if (result.owned.size() <= MIN_COSMETICS) {
            return false;
        }

        LocalDate registered = parse(result.regDate);
        return registered != null && registered.isAfter(LocalDate.now().minusYears(1L));
    }

    static String effectText(MenuScanner.Result result) {
        Map<String, String> effects = result.effects;
        if (effects == null || effects.isEmpty()) {
            return null;
        }

        Set<String> texts = new LinkedHashSet<>();
        if (result.found != null) {
            for (String key : result.found) {
                String effect = effects.get(key);
                if (effect != null && !effect.isBlank()) {
                    texts.add(effect);
                }
            }
        }

        if (texts.isEmpty()) {
            for (String effect : effects.values()) {
                if (effect != null && !effect.isBlank()) {
                    texts.add(effect);
                }
            }
        }

        if (texts.isEmpty()) {
            return null;
        }

        String first = texts.iterator().next();
        int more = texts.size() - 1;
        return "эффект: " + first + (more > 0 ? " +" + more : "");
    }

    public static void extra(MenuScanner.Result result, List<String> lines, List<Integer> colors) {
        boolean bad = suspicious(result);
        if (result.player != null) {
            if (bad) {
                BAD.add(result.player);
            } else {
                BAD.remove(result.player);
            }
        }

        if (bad && result.regDate != null) {
            for (int i = 0; i < lines.size() && i < colors.size(); i++) {
                if (lines.get(i).contains(result.regDate)) {
                    colors.set(i, RED);
                }
            }
        }

        String effect = effectText(result);
        if (effect != null) {
            lines.add(effect);
            colors.add(EFFECT);
        }
    }

    static boolean isBad(String nick) {
        if (nick == null || BAD.isEmpty()) {
            return false;
        }

        if (BAD.contains(nick)) {
            return true;
        }

        String prefix = null;
        if (nick.endsWith("…")) {
            prefix = nick.substring(0, nick.length() - 1);
        } else if (nick.endsWith("...")) {
            prefix = nick.substring(0, nick.length() - 3);
        }

        if (prefix == null || prefix.isEmpty()) {
            return false;
        }

        synchronized (BAD) {
            for (String bad : BAD) {
                if (bad.startsWith(prefix)) {
                    return true;
                }
            }

            return false;
        }
    }

    public static void nameText(FinderScreen screen, GuiGraphicsExtractor g, String nick, int x, int y, int color) {
        screen.text(g, nick, x, y, isBad(nick) ? RED : color);
    }
}
