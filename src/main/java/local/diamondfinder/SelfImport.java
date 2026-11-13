package local.diamondfinder;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class SelfImport {
    private static final long STABLE_MS = 300L;
    private static final long CLICK_TIMEOUT_MS = 4000L;
    private static final long SESSION_GAP_MS = 4000L;
    private static final int MAX_PAGES = 64;
    private static boolean loaded;
    private static boolean allDone;
    private static boolean failed;
    private static int errors;
    private static final Set<String> done = new HashSet<>();
    private static final Set<String> skipped = new HashSet<>();
    private static Boolean ownProfile;
    private static String profSig = "";
    private static long lastContainerAt;
    private static AbstractContainerMenu visible;
    private static String sig = "";
    private static long stableAt;
    private static String sessCat;
    private static String sessLabel;
    private static final Set<String> seenSigs = new HashSet<>();
    private static int pages;
    private static int sessFound;
    private static int sessNew;
    private static String clickedSig = "";
    private static long clickedAt;
    private static int catalogSize = -1;
    private static Map<String, String> unique = new HashMap<>();
    private static Set<String> targets = new HashSet<>();

    private SelfImport() {
    }

    public static void tick() {
        if (!failed) {
            try {
                run();
            } catch (Throwable var1) {
                if (++errors >= 5) {
                    failed = true;
                }
            }
        }
    }

    private static void run() {
        ensureLoaded();
        if (!allDone) {
            Minecraft var0 = Minecraft.getInstance();
            if (var0 != null && var0.player != null && var0.gui != null && DiamondFinder.allowedServer()) {
                MenuScanner var1 = DiamondFinder.scanner;
                if (var1 != null && var1.running()) {
                    resetSession();
                } else {
                    long var2 = System.currentTimeMillis();
                    Screen var4 = var0.gui.screen();
                    if (!(var4 instanceof AbstractContainerScreen)) {
                        visible = null;
                        if (var2 - lastContainerAt > 4000L) {
                            ownProfile = null;
                            profSig = "";
                            resetSession();
                        }
                    } else {
                        lastContainerAt = var2;
                        AbstractContainerScreen var5 = (AbstractContainerScreen)var4;
                        String var6 = var5.getTitle().getString();
                        boolean var7 = CosmeticText.profileTitle(var6);
                        ScanCoverage.Category var8 = DiamondMenus.category(var6);
                        if (var7 || var8 != null) {
                            AbstractContainerMenu var9 = var5.getMenu();
                            String var10 = Catalog.signature(var9);
                            if (visible == var9 && sig.equals(var10)) {
                                if (var2 - stableAt >= 300L) {
                                    List var11 = cells(var9);
                                    if (var7) {
                                        if (!var10.equals(profSig)) {
                                            profSig = var10;
                                            ownProfile = isOwnProfile(var11, var0);
                                        }
                                    } else if (DiamondMenus.categoryLayout(var11)) {
                                        if (!Boolean.FALSE.equals(ownProfile)) {
                                            String var12 = var8.name();
                                            if (!done.contains(var12) && !skipped.contains(var12)) {
                                                if (!var12.equals(sessCat)) {
                                                    resetSession();
                                                    sessCat = var12;
                                                    sessLabel = label(var8);
                                                }

                                                if (seenSigs.add(var10)) {
                                                    if (++pages > 64) {
                                                        abort("слишком много страниц");
                                                        return;
                                                    }

                                                    processPage(var11);
                                                } else if (!var10.equals(clickedSig)) {
                                                    abort("страницы зациклились");
                                                    return;
                                                }

                                                if (!clickedSig.isEmpty() && var10.equals(clickedSig)) {
                                                    if (var2 - clickedAt > 4000L) {
                                                        abort("страница не листается");
                                                    }
                                                } else {
                                                    Slot var13 = MenuScanner.nextSlot(var9);
                                                    if (DiamondMenus.lastPage(var11) || var13 == null) {
                                                        finish();
                                                    } else if (var0.gameMode != null && var0.player.containerMenu == var9 && var9.getCarried().isEmpty()) {
                                                        clickedSig = var10;
                                                        clickedAt = var2;
                                                        var0.gameMode
                                                            .handleContainerInput(var9.containerId, var13.index, 0, ContainerInput.PICKUP, var0.player);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                visible = var9;
                                sig = var10;
                                stableAt = var2;
                            }
                        }
                    }
                }
            }
        }
    }

    private static void processPage(List<DiamondMenus.Cell> var0) {
        refreshCatalog();

        for (DiamondMenus.Cell var2 : var0) {
            boolean var3 = DiamondMenus.itemSlot(var2.slot());
            boolean var4 = var2.slot() == 40 && var2.lore().stream().anyMatch(var0x -> CosmeticText.normalize(var0x).equals("используется"));
            if (var3 || var4) {
                String var5 = DiamondMenus.match(var2.name(), var2.lore(), unique, targets);
                if (var5 != null) {
                    sessFound++;
                    if (!ProfileDb.isFound(var5)) {
                        ProfileDb.markFound(var5);
                        sessNew++;
                    }
                }
            }
        }
    }

    private static void finish() {
        done.add(sessCat);
        save();
        if (done.size() >= ScanCoverage.Category.values().length) {
            allDone = true;
        }

        notifyPlayer("Мой инвентарь · " + sessLabel + ": найдено " + sessFound + ", в «Найдено» добавлено " + sessNew);
        resetSession();
    }

    private static void abort(String var0) {
        skipped.add(sessCat);
        notifyPlayer("Импорт инвентаря (" + sessLabel + ") прерван: " + var0);
        resetSession();
    }

    private static void resetSession() {
        sessCat = null;
        sessLabel = null;
        seenSigs.clear();
        pages = 0;
        sessFound = 0;
        sessNew = 0;
        clickedSig = "";
        clickedAt = 0L;
    }

    private static String label(ScanCoverage.Category var0) {
        switch (var0.name()) {
            case "HATS":
                return "голова";
            case "BACK":
                return "аксессуары";
            case "PETS":
                return "компаньоны";
            default:
                return var0.name();
        }
    }

    private static boolean isOwnProfile(List<DiamondMenus.Cell> var0, Minecraft var1) {
        String var2 = var1.player.getGameProfile().name().toLowerCase(Locale.ROOT);

        for (DiamondMenus.Cell var4 : var0) {
            if (var4.slot() <= 2) {
                String var5 = CosmeticText.normalize(var4.name());
                if (var5.equals(var2) || var5.endsWith(" " + var2)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static List<DiamondMenus.Cell> cells(AbstractContainerMenu var0) {
        ArrayList<DiamondMenus.Cell> var1 = new ArrayList<>();

        for (Slot var3 : Catalog.serverSlots(var0)) {
            ItemStack var4 = var3.getItem();
            if (!var4.isEmpty()) {
                var1.add(new DiamondMenus.Cell(var3.index, var4.getHoverName().getString(), Catalog.lore(var4)));
            }
        }

        return var1;
    }

    private static void refreshCatalog() {
        Catalog var0 = DiamondFinder.catalog;
        if (var0 != null && var0.entries.size() != catalogSize) {
            catalogSize = var0.entries.size();
            HashMap<String, String> var1 = new HashMap<>();
            HashSet<String> var2 = new HashSet<>();

            for (Catalog.Entry var4 : var0.entries.values()) {
                String var5 = DiamondMenus.baseName(var4.name);
                String var6 = var1.putIfAbsent(var5, var4.key);
                if (var6 != null && !var6.equals(var4.key)) {
                    var2.add(var5);
                }
            }

            for (String var8 : var2) {
                var1.remove(var8);
            }

            unique = var1;
            targets = new HashSet<>(var0.entries.keySet());
        }
    }

    private static Path file() {
        return DiamondFinder.catalog.dir.resolve("self-import.txt");
    }

    private static void ensureLoaded() {
        if (!loaded && DiamondFinder.catalog != null) {
            loaded = true;

            try {
                Path var0 = file();
                if (Files.exists(var0)) {
                    for (String var2 : Files.readAllLines(var0)) {
                        if (!var2.isBlank()) {
                            done.add(var2.trim());
                        }
                    }
                }
            } catch (Throwable var3) {
            }

            if (done.size() >= ScanCoverage.Category.values().length) {
                allDone = true;
            }
        }
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), String.join("\n", done) + "\n");
        } catch (Throwable var1) {
        }
    }

    private static void notifyPlayer(String var0) {
        try {
            Method var1 = DiamondFinder.class.getDeclaredMethod("notifyPlayer", String.class);
            var1.setAccessible(true);
            var1.invoke(null, var0);
        } catch (Throwable var2) {
        }
    }

    public static void recheck() {
        done.clear();
        skipped.clear();
        allDone = false;
        failed = false;
        errors = 0;
        resetSession();
        save();
        notifyPlayer("Перепроверка инвентаря: откройте косметику (голова / аксессуары / компаньоны), страницы пролистаются сами");
    }
}
