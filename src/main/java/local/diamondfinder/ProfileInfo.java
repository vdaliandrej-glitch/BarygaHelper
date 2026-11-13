package local.diamondfinder;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

public final class ProfileInfo {
    private static final Pattern FMT = Pattern.compile("§.");
    private static final Pattern JUNK = Pattern.compile("[\\p{Co}\\u200B-\\u200D\\uFEFF]");
    private static final Pattern LEAD = Pattern.compile("^[\\s:：\\-–—>»]+");
    private static final String MONTHS = "января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря";
    private static final Pattern DATE = Pattern.compile(
        "(?iu)\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}(?:[ ,T]+\\d{1,2}:\\d{2}(?::\\d{2})?)?|\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}|\\d{1,2}\\s+(?:" + MONTHS + ")\\s+\\d{4}(?:\\s*г\\.?)?",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RANK = Pattern.compile("(?iu)(привилеги\\p{L}*|ранг|донат\\p{L}*|группа|rank)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REG = Pattern.compile("(?iu)(регистрац\\p{L}*|зарегистр\\p{L}*|registered|дата рег)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEVEL = Pattern.compile("(?iu)(уров\\p{L}*|лвл|lvl|level)\\D{0,4}?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EFFECT = Pattern.compile("(?iu)эффект\\p{L}*\\s*[:：\\-–—]?\\s*(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAB_LEVEL = Pattern.compile("\\[\\s*(\\d{1,4})\\s*\\]");
    private static final int RANK_COLOR = -1;
    private static final int REG_COLOR = 0xFF9AA5B8;
    private static final int GOOD_COLOR = 0xFF7EE7A1;
    private static final int BAD_COLOR = 0xFFFF6B6B;
    private static final int SEPARATOR_COLOR = 0xFF5C6475;
    private static final int MAX_RETRIES = 8;
    private static final Map<String, String> TAB = new HashMap<>();
    private static final Map<String, String> EFFECTS = new HashMap<>();
    private static final Map<String, Integer> RETRIES = new HashMap<>();
    private static String effectPlayer = "";
    private static long tabAt;
    private static boolean dumped;
    private static int itemDumps;
    private static int effectDumps;

    private ProfileInfo() {
    }

    private static String clean(String text) {
        if (text == null) {
            return "";
        }

        text = FMT.matcher(text).replaceAll("");
        text = JUNK.matcher(text).replaceAll("");
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String keepGlyphs(String text) {
        if (text == null) {
            return "";
        }

        text = FMT.matcher(text).replaceAll("");
        return text.replaceAll("[ \\t\\r\\n]+", " ").trim();
    }

    private static String esc(String text) {
        StringBuilder out = new StringBuilder();

        for (char c : (text == null ? "" : text).toCharArray()) {
            if (Character.getType(c) == Character.PRIVATE_USE) {
                out.append(String.format("\\u%04X", (int) c));
            } else {
                out.append(c);
            }
        }

        return out.toString();
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    public static void capture(MenuScanner.Result result, List<DiamondMenus.Cell> cells) {
        if (result == null || cells == null) {
            return;
        }

        clearRetries(result.player);

        try {
            if (!result.player.equals(effectPlayer)) {
                EFFECTS.clear();
                effectPlayer = result.player;
            }

            dump(result, cells);
            if (result.rank == null) {
                result.rank = rankFromName(result.player, cells);
            }

            List<List<String>> items = new ArrayList<>();

            for (DiamondMenus.Cell cell : cells) {
                List<String> lines = new ArrayList<>();
                lines.add(clean(cell.name()));
                if (cell.lore() != null) {
                    for (String line : cell.lore()) {
                        lines.add(clean(line));
                    }
                }

                items.add(lines);
            }

            if (result.regDate == null) {
                String date = findRegDate(items);
                if (date != null) {
                    result.regDate = cut(date, 22);
                }
            }

            if (result.level == null) {
                result.level = findLevel(items);
            }

            if (result.rank == null) {
                result.rank = findRank(items);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Привилегия обычно стоит перед ником в названии предмета: "[VIP] Nick". */
    private static String rankFromName(String player, List<DiamondMenus.Cell> cells) {
        String nick = player.toLowerCase(Locale.ROOT);

        for (DiamondMenus.Cell cell : cells) {
            String name = keepGlyphs(cell.name());
            int at = name.toLowerCase(Locale.ROOT).lastIndexOf(nick);
            if (at >= 0 && (at <= 0 || !Character.isLetterOrDigit(name.charAt(at - 1)))) {
                String prefix = name.substring(0, at).trim();
                if (prefix.startsWith("[") && prefix.endsWith("]") && prefix.length() > 2) {
                    prefix = prefix.substring(1, prefix.length() - 1).trim();
                }

                return prefix.isEmpty() ? null : cut(prefix, 24);
            }
        }

        return null;
    }

    private static String findRegDate(List<List<String>> items) {
        // 1. Строка со словом "регистрация" и дата в ней или ниже
        for (List<String> lines : items) {
            for (int i = 0; i < lines.size(); i++) {
                if (!REG.matcher(lines.get(i)).find()) {
                    continue;
                }

                String date = find(DATE, lines.get(i));

                for (int j = i + 1; date == null && j < lines.size(); j++) {
                    date = find(DATE, lines.get(j));
                }

                if (date == null && i > 0) {
                    date = find(DATE, lines.get(0));
                }

                if (date != null) {
                    return date;
                }
            }
        }

        // 2. Предмет, похожий на часы/дату
        for (List<String> lines : items) {
            String title = lines.get(0).toLowerCase(Locale.ROOT);
            if (title.contains("час") || title.contains("врем") || title.contains("clock") || title.contains("дата")) {
                for (String line : lines) {
                    String date = find(DATE, line);
                    if (date != null) {
                        return date;
                    }
                }
            }
        }

        // 3. Любая дата
        for (List<String> lines : items) {
            for (String line : lines) {
                String date = find(DATE, line);
                if (date != null) {
                    return date;
                }
            }
        }

        return null;
    }

    private static String findLevel(List<List<String>> items) {
        for (List<String> lines : items) {
            for (String line : lines) {
                if (line.toLowerCase(Locale.ROOT).contains("редкост")) {
                    continue;
                }

                Matcher m = LEVEL.matcher(line);
                if (m.find()) {
                    return m.group(2);
                }
            }
        }

        return null;
    }

    private static String findRank(List<List<String>> items) {
        for (List<String> lines : items) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.toLowerCase(Locale.ROOT).contains("редкост")) {
                    continue;
                }

                Matcher m = RANK.matcher(line);
                if (!m.find()) {
                    continue;
                }

                String value = LEAD.matcher(line.substring(m.end())).replaceFirst("").trim();
                if (value.isEmpty() && i + 1 < lines.size()) {
                    value = lines.get(i + 1);
                }

                if (!value.isEmpty()) {
                    return cut(value, 24);
                }
            }
        }

        return null;
    }

    private static String find(Pattern pattern, String text) {
        if (text == null) {
            return null;
        }

        Matcher m = pattern.matcher(text);
        return m.find() ? m.group().trim() : null;
    }

    private static void dump(MenuScanner.Result result, List<DiamondMenus.Cell> cells) {
        if (dumped) {
            return;
        }

        dumped = true;

        try {
            Path dir = Path.of("config", "diamond-finder");
            Files.createDirectories(dir);
            StringBuilder text = new StringBuilder("player: ").append(result.player).append('\n');
            text.append("tab-level: ").append(TAB.get(result.player)).append('\n');

            for (DiamondMenus.Cell cell : cells) {
                text.append("slot ").append(cell.slot()).append(": ").append(esc(cell.name())).append('\n');
                if (cell.lore() != null) {
                    for (String line : cell.lore()) {
                        text.append("    ").append(esc(line)).append('\n');
                    }
                }
            }

            Files.writeString(dir.resolve("profile-dump.txt"), text.toString());
        } catch (Throwable ignored) {
        }
    }

    public static void noteMatch(String key, String name, List<String> lore) {
        if (key == null) {
            return;
        }

        try {
            String effect = null;
            if (lore != null) {
                for (int i = 0; i < lore.size() && effect == null; i++) {
                    String line = clean(lore.get(i));
                    Matcher m = EFFECT.matcher(line);
                    if (m.find()) {
                        String value = LEAD.matcher(m.group(1)).replaceFirst("").trim();
                        if (value.isEmpty() && i + 1 < lore.size()) {
                            value = clean(lore.get(i + 1));
                        }

                        effect = value.isEmpty() ? cut(line, 28) : cut(value, 28);
                    }
                }
            }

            if (effect != null) {
                EFFECTS.put(key, effect);
            }

            if (itemDumps < 6 || effect != null && effectDumps < 4) {
                itemDumps++;
                if (effect != null) {
                    effectDumps++;
                }

                StringBuilder text = new StringBuilder("item: ")
                    .append(esc(name))
                    .append("  =>  key=")
                    .append(key)
                    .append("  effect=")
                    .append(effect)
                    .append('\n');
                if (lore != null) {
                    for (String line : lore) {
                        text.append("    ").append(esc(line)).append('\n');
                    }
                }

                Path dir = Path.of("config", "diamond-finder");
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("item-dump.txt"), text.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Throwable ignored) {
        }
    }

    public static Map<String, String> takeEffects(String player) {
        Map<String, String> effects = new HashMap<>();
        if (player != null && player.equals(effectPlayer)) {
            effects.putAll(EFFECTS);
        }

        EFFECTS.clear();
        return effects;
    }

    public static void draw(FinderScreen screen, GuiGraphicsExtractor g, MenuScanner.Result result, int x, int y, int width) {
        if (result == null) {
            return;
        }

        String level = tabLevel(result.player);
        if (level != null) {
            result.level = level;
        }

        List<String> parts = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (result.rank != null) {
            parts.add(result.rank);
            colors.add(RANK_COLOR);
        }

        if (result.regDate != null) {
            parts.add("рег. " + result.regDate);
            colors.add(REG_COLOR);
        }

        if (result.level != null) {
            parts.add("ур. " + result.level);
            colors.add(GOOD_COLOR);
        }

        Extra.extra(result, parts, colors);
        if (result.coverage != null) {
            if (result.coverage.hidden()) {
                parts.add("инв. скрыт");
                colors.add(BAD_COLOR);
            } else if (result.coverage.full()) {
                parts.add("инв. открыт");
                colors.add(GOOD_COLOR);
            }
        }

        if (parts.isEmpty()) {
            return;
        }

        int cursor = x + screen.textWidth(result.player) + 10;
        int right = x + width;
        String separator = "  ·  ";

        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            int partW = screen.textWidth(part);
            if (i > 0) {
                int sepW = screen.textWidth(separator);
                if (cursor + sepW + partW > right) {
                    return;
                }

                screen.text(g, separator, cursor, y, SEPARATOR_COLOR);
                cursor += sepW;
            } else if (cursor + partW > right) {
                return;
            }

            screen.text(g, part, cursor, y, colors.get(i));
            cursor += partW;
        }
    }

    private static String tabLevel(String player) {
        if (player == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        if (now - tabAt > 1000L) {
            tabAt = now;

            try {
                refreshTab();
            } catch (Throwable ignored) {
            }
        }

        return TAB.get(player);
    }

    private static void refreshTab() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc == null ? null : mc.getConnection();
        if (connection == null) {
            return;
        }

        Object scoreboard = null;

        try {
            Object level = mc.getClass().getField("level").get(mc);
            if (level != null) {
                scoreboard = call(level, "getScoreboard");
            }
        } catch (Throwable ignored) {
        }

        Map<String, String> levels = new HashMap<>();

        for (PlayerInfo info : connection.getOnlinePlayers()) {
            String nick = info.getProfile().name();
            StringBuilder text = new StringBuilder();

            try {
                if (call(info, "getTabListDisplayName") instanceof Component display) {
                    text.append(display.getString());
                }
            } catch (Throwable ignored) {
            }

            try {
                Object team = scoreboard == null ? null : call(scoreboard, "getPlayersTeam", nick);
                if (team != null) {
                    if (call(team, "getPlayerPrefix") instanceof Component prefix) {
                        text.append(' ').append(prefix.getString());
                    }

                    if (call(team, "getPlayerSuffix") instanceof Component suffix) {
                        text.append(' ').append(suffix.getString());
                    }
                }
            } catch (Throwable ignored) {
            }

            Matcher m = TAB_LEVEL.matcher(clean(text.toString()));
            if (m.find()) {
                levels.put(nick, m.group(1));
            }
        }

        TAB.clear();
        TAB.putAll(levels);
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                return method.invoke(target, args);
            }
        }

        return null;
    }

    public static boolean isRateLimit(String message) {
        return message != null && message.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").contains("подождитенемного");
    }

    public static boolean allowRetry(String player) {
        int attempts = RETRIES.merge(player, 1, Integer::sum);
        return attempts <= MAX_RETRIES;
    }

    static void clearRetries(String player) {
        RETRIES.remove(player);
    }
}
