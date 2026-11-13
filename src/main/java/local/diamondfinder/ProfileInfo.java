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
        "(?iu)\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}(?:[ ,T]+\\d{1,2}:\\d{2}(?::\\d{2})?)?|\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}|\\d{1,2}\\s+(?:января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)\\s+\\d{4}(?:\\s*г\\.?)?",
        2
    );
    private static final Pattern RANK = Pattern.compile("(?iu)(привилеги\\p{L}*|ранг|донат\\p{L}*|группа|rank)", 2);
    private static final Pattern REG = Pattern.compile("(?iu)(регистрац\\p{L}*|зарегистр\\p{L}*|registered|дата рег)", 2);
    private static final Pattern LEVEL = Pattern.compile("(?iu)(уров\\p{L}*|лвл|lvl|level)\\D{0,4}?(\\d+)", 2);
    private static final Pattern EFFECT = Pattern.compile("(?iu)эффект\\p{L}*\\s*[:：\\-–—]?\\s*(.*)", 2);
    private static final Pattern TAB_LEVEL = Pattern.compile("\\[\\s*(\\d{1,4})\\s*\\]");
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

    private static String clean(String var0) {
        if (var0 == null) {
            return "";
        }

        var0 = FMT.matcher(var0).replaceAll("");
        var0 = JUNK.matcher(var0).replaceAll("");
        return var0.replaceAll("\\s+", " ").trim();
    }

    private static String keepGlyphs(String var0) {
        if (var0 == null) {
            return "";
        }

        var0 = FMT.matcher(var0).replaceAll("");
        return var0.replaceAll("[ \\t\\r\\n]+", " ").trim();
    }

    private static String esc(String var0) {
        StringBuilder var1 = new StringBuilder();

        for (char var5 : (var0 == null ? "" : var0).toCharArray()) {
            if (Character.getType(var5) == 18) {
                var1.append(String.format("\\u%04X", Integer.valueOf(var5)));
            } else if (var5 == 167) {
                var1.append("§");
            } else {
                var1.append(var5);
            }
        }

        return var1.toString();
    }

    private static String cut(String var0, int var1) {
        return var0.length() <= var1 ? var0 : var0.substring(0, var1 - 1) + "…";
    }

    public static void capture(MenuScanner.Result var0, List<DiamondMenus.Cell> var1) {
        if (var0 != null && var1 != null) {
            clearRetries(var0.player);

            try {
                if (!var0.player.equals(effectPlayer)) {
                    EFFECTS.clear();
                    effectPlayer = var0.player;
                }

                dump(var0, var1);
                if (var0.rank == null) {
                    String var2 = var0.player.toLowerCase(Locale.ROOT);

                    for (DiamondMenus.Cell var4 : var1) {
                        String var5 = keepGlyphs(var4.name());
                        int var6 = var5.toLowerCase(Locale.ROOT).lastIndexOf(var2);
                        if (var6 >= 0 && (var6 <= 0 || !Character.isLetterOrDigit(var5.charAt(var6 - 1)))) {
                            String var7 = var5.substring(0, var6).trim();
                            if (var7.startsWith("[") && var7.endsWith("]") && var7.length() > 2) {
                                var7 = var7.substring(1, var7.length() - 1).trim();
                            }

                            if (!var7.isEmpty()) {
                                var0.rank = cut(var7, 24);
                            }
                            break;
                        }
                    }
                }

                ArrayList<List<String>> var10 = new ArrayList<>();

                for (DiamondMenus.Cell var15 : var1) {
                    ArrayList<String> var21 = new ArrayList<>();
                    var21.add(clean(var15.name()));
                    if (var15.lore() != null) {
                        for (String var33 : var15.lore()) {
                            var21.add(clean(var33));
                        }
                    }

                    var10.add(var21);
                }

                if (var0.regDate == null) {
                    String var12 = null;

                    label210:
                    for (List<String> var22 : var10) {
                        for (int var28 = 0; var28 < var22.size(); var28++) {
                            if (REG.matcher((CharSequence)var22.get(var28)).find()) {
                                var12 = find(DATE, (String)var22.get(var28));

                                for (int var34 = var28 + 1; var12 == null && var34 < var22.size(); var34++) {
                                    var12 = find(DATE, (String)var22.get(var34));
                                }

                                if (var12 == null && var28 > 0) {
                                    var12 = find(DATE, (String)var22.get(0));
                                }

                                if (var12 != null) {
                                    break label210;
                                }
                            }
                        }
                    }

                    if (var12 == null) {
                        for (List<String> var23 : var10) {
                            String var29 = ((String)var23.get(0)).toLowerCase(Locale.ROOT);
                            if (var29.contains("час") || var29.contains("врем") || var29.contains("clock") || var29.contains("дата")) {
                                for (String var8 : var23) {
                                    var12 = find(DATE, var8);
                                    if (var12 != null) {
                                        break;
                                    }
                                }
                            }

                            if (var12 != null) {
                                break;
                            }
                        }
                    }

                    if (var12 == null) {
                        for (List<String> var24 : var10) {
                            for (String var36 : var24) {
                                var12 = find(DATE, var36);
                                if (var12 != null) {
                                    break;
                                }
                            }

                            if (var12 != null) {
                                break;
                            }
                        }
                    }

                    if (var12 != null) {
                        var0.regDate = cut(var12, 22);
                    }
                }

                if (var0.level == null) {
                    for (List<String> var19 : var10) {
                        for (String var31 : var19) {
                            Matcher var37;
                            if (var0.level == null && !var31.toLowerCase(Locale.ROOT).contains("редкост") && (var37 = LEVEL.matcher(var31)).find()) {
                                var0.level = var37.group(2);
                            }
                        }
                    }
                }

                if (var0.rank == null) {
                    for (List<String> var20 : var10) {
                        for (int var26 = 0; var26 < var20.size() && var0.rank == null; var26++) {
                            String var32 = (String)var20.get(var26);
                            if (RANK.matcher(var32).find() && !var32.toLowerCase(Locale.ROOT).contains("редкост")) {
                                Matcher var38 = RANK.matcher(var32);
                                var38.find();
                                String var39 = LEAD.matcher(var32.substring(var38.end())).replaceFirst("").trim();
                                if (var39.isEmpty() && var26 + 1 < var20.size()) {
                                    var39 = (String)var20.get(var26 + 1);
                                }

                                if (!var39.isEmpty()) {
                                    var0.rank = cut(var39, 24);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable var9) {
            }
        }
    }

    private static String find(Pattern var0, String var1) {
        if (var1 == null) {
            return null;
        }

        Matcher var2 = var0.matcher(var1);
        return var2.find() ? var2.group().trim() : null;
    }

    private static void dump(MenuScanner.Result var0, List<DiamondMenus.Cell> var1) {
        if (!dumped) {
            dumped = true;

            try {
                Path var2 = Path.of("config", "diamond-finder");
                Files.createDirectories(var2);
                StringBuilder var3 = new StringBuilder("player: ").append(var0.player).append('\n');
                String var4 = TAB.get(var0.player);
                var3.append("tab-level: ").append(var4).append('\n');

                for (DiamondMenus.Cell var6 : var1) {
                    var3.append("slot ").append(var6.slot()).append(": ").append(esc(var6.name())).append('\n');
                    if (var6.lore() != null) {
                        for (String var8 : var6.lore()) {
                            var3.append("    ").append(esc(var8)).append('\n');
                        }
                    }
                }

                Files.writeString(var2.resolve("profile-dump.txt"), var3.toString());
            } catch (Throwable var9) {
            }
        }
    }

    public static void noteMatch(String var0, String var1, List<String> var2) {
        if (var0 != null) {
            try {
                String var3 = null;
                if (var2 != null) {
                    for (int var4 = 0; var4 < var2.size() && var3 == null; var4++) {
                        String var5 = clean((String)var2.get(var4));
                        Matcher var6 = EFFECT.matcher(var5);
                        if (var6.find()) {
                            String var7 = LEAD.matcher(var6.group(1)).replaceFirst("").trim();
                            if (var7.isEmpty() && var4 + 1 < var2.size()) {
                                var7 = clean((String)var2.get(var4 + 1));
                            }

                            var3 = var7.isEmpty() ? cut(var5, 28) : cut(var7, 28);
                        }
                    }
                }

                if (var3 != null) {
                    EFFECTS.put(var0, var3);
                }

                if (itemDumps < 6 || var3 != null && effectDumps < 4) {
                    itemDumps++;
                    if (var3 != null) {
                        effectDumps++;
                    }

                    StringBuilder var9 = new StringBuilder("item: ")
                        .append(esc(var1))
                        .append("  =>  key=")
                        .append(var0)
                        .append("  effect=")
                        .append(var3)
                        .append('\n');
                    if (var2 != null) {
                        for (String var12 : var2) {
                            var9.append("    ").append(esc(var12)).append('\n');
                        }
                    }

                    Path var11 = Path.of("config", "diamond-finder");
                    Files.createDirectories(var11);
                    Files.writeString(var11.resolve("item-dump.txt"), var9.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
            } catch (Throwable var8) {
            }
        }
    }

    public static Map<String, String> takeEffects(String var0) {
        HashMap var1 = new HashMap();
        if (var0 != null && var0.equals(effectPlayer)) {
            var1.putAll(EFFECTS);
        }

        EFFECTS.clear();
        return var1;
    }

    public static void draw(FinderScreen var0, GuiGraphicsExtractor var1, MenuScanner.Result var2, int var3, int var4, int var5) {
        if (var2 != null) {
            String var6 = tabLevel(var2.player);
            if (var6 != null) {
                var2.level = var6;
            }

            ArrayList var7 = new ArrayList();
            ArrayList var8 = new ArrayList();
            if (var2.rank != null) {
                var7.add(var2.rank);
                var8.add(-1);
            }

            if (var2.regDate != null) {
                var7.add("рег. " + var2.regDate);
                var8.add(-6642248);
            }

            if (var2.level != null) {
                var7.add("ур. " + var2.level);
                var8.add(-8460383);
            }

            Extra.extra(var2, var7, var8);
            if (var2.coverage != null) {
                if (var2.coverage.hidden()) {
                    var7.add("инв. скрыт");
                    var8.add(-38037);
                } else if (var2.coverage.full()) {
                    var7.add("инв. открыт");
                    var8.add(-8460383);
                }
            }

            if (!var7.isEmpty()) {
                int var9 = var3 + var0.textWidth(var2.player) + 10;
                int var10 = var3 + var5;
                String var11 = "  ·  ";

                for (int var12 = 0; var12 < var7.size(); var12++) {
                    String var13 = (String)var7.get(var12);
                    int var14 = var0.textWidth(var13);
                    if (var12 > 0) {
                        int var15 = var0.textWidth(var11);
                        if (var9 + var15 + var14 > var10) {
                            return;
                        }

                        var0.text(var1, var11, var9, var4, -10722187);
                        var9 += var15;
                    } else if (var9 + var14 > var10) {
                        return;
                    }

                    var0.text(var1, var13, var9, var4, (Integer)var8.get(var12));
                    var9 += var14;
                }
            }
        }
    }

    private static String tabLevel(String var0) {
        if (var0 == null) {
            return null;
        }

        long var1 = System.currentTimeMillis();
        if (var1 - tabAt > 1000L) {
            tabAt = var1;

            try {
                refreshTab();
            } catch (Throwable var4) {
            }
        }

        return TAB.get(var0);
    }

    private static void refreshTab() {
        Minecraft var0 = Minecraft.getInstance();
        ClientPacketListener var1 = var0 == null ? null : var0.getConnection();
        if (var1 != null) {
            Object var2 = null;

            try {
                Object var3 = var0.getClass().getField("level").get(var0);
                if (var3 != null) {
                    var2 = call(var3, "getScoreboard");
                }
            } catch (Throwable var14) {
            }

            HashMap var15 = new HashMap();

            for (PlayerInfo var5 : var1.getOnlinePlayers()) {
                String var6 = var5.getProfile().name();
                StringBuilder var7 = new StringBuilder();

                try {
                    if (call(var5, "getTabListDisplayName") instanceof Component var9) {
                        var7.append(var9.getString());
                    }
                } catch (Throwable var13) {
                }

                try {
                    Object var16 = var2 == null ? null : call(var2, "getPlayersTeam", var6);
                    if (var16 != null) {
                        Object var18 = call(var16, "getPlayerPrefix");
                        Object var10 = call(var16, "getPlayerSuffix");
                        if (var18 instanceof Component var11) {
                            var7.append(' ').append(var11.getString());
                        }

                        if (var10 instanceof Component var19) {
                            var7.append(' ').append(var19.getString());
                        }
                    }
                } catch (Throwable var12) {
                }

                Matcher var17 = TAB_LEVEL.matcher(clean(var7.toString()));
                if (var17.find()) {
                    var15.put(var6, var17.group(1));
                }
            }

            TAB.clear();
            TAB.putAll(var15);
        }
    }

    private static Object call(Object var0, String var1, Object... var2) throws Exception {
        for (Method var6 : var0.getClass().getMethods()) {
            if (var6.getName().equals(var1) && var6.getParameterCount() == var2.length) {
                return var6.invoke(var0, var2);
            }
        }

        return null;
    }

    public static boolean isRateLimit(String var0) {
        return var0 == null ? false : var0.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").contains("подождитенемного");
    }

    public static boolean allowRetry(String var0) {
        int var1 = RETRIES.merge(var0, 1, Integer::sum);
        return var1 <= 8;
    }

    static void clearRetries(String var0) {
        RETRIES.remove(var0);
    }
}
