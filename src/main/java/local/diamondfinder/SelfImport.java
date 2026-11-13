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
    private static final int SELECTED_SLOT = 40;
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
        if (failed) {
            return;
        }

        try {
            run();
        } catch (Throwable e) {
            if (++errors >= 5) {
                failed = true;
            }
        }
    }

    private static void run() {
        ensureLoaded();
        if (allDone) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gui == null || !DiamondFinder.allowedServer()) {
            return;
        }

        MenuScanner scanner = DiamondFinder.scanner;
        if (scanner != null && scanner.running()) {
            resetSession();
            return;
        }

        long now = System.currentTimeMillis();
        Screen screen = mc.gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            visible = null;
            if (now - lastContainerAt > SESSION_GAP_MS) {
                ownProfile = null;
                profSig = "";
                resetSession();
            }
            return;
        }

        lastContainerAt = now;
        String title = container.getTitle().getString();
        boolean profile = CosmeticText.profileTitle(title);
        ScanCoverage.Category category = DiamondMenus.category(title);
        if (!profile && category == null) {
            return;
        }

        AbstractContainerMenu menu = container.getMenu();
        String signature = Catalog.signature(menu);
        if (visible != menu || !sig.equals(signature)) {
            visible = menu;
            sig = signature;
            stableAt = now;
            return;
        }

        if (now - stableAt < STABLE_MS) {
            return;
        }

        List<DiamondMenus.Cell> cells = cells(menu);
        if (profile) {
            if (!signature.equals(profSig)) {
                profSig = signature;
                ownProfile = isOwnProfile(cells, mc);
            }
            return;
        }

        if (!DiamondMenus.categoryLayout(cells) || Boolean.FALSE.equals(ownProfile)) {
            return;
        }

        String categoryName = category.name();
        if (done.contains(categoryName) || skipped.contains(categoryName)) {
            return;
        }

        if (!categoryName.equals(sessCat)) {
            resetSession();
            sessCat = categoryName;
            sessLabel = label(category);
        }

        if (seenSigs.add(signature)) {
            if (++pages > MAX_PAGES) {
                abort("слишком много страниц");
                return;
            }

            processPage(cells);
        } else if (!signature.equals(clickedSig)) {
            abort("страницы зациклились");
            return;
        }

        if (!clickedSig.isEmpty() && signature.equals(clickedSig)) {
            if (now - clickedAt > CLICK_TIMEOUT_MS) {
                abort("страница не листается");
            }
            return;
        }

        Slot next = MenuScanner.nextSlot(menu);
        if (DiamondMenus.lastPage(cells) || next == null) {
            finish();
        } else if (mc.gameMode != null && mc.player.containerMenu == menu && menu.getCarried().isEmpty()) {
            clickedSig = signature;
            clickedAt = now;
            mc.gameMode.handleContainerInput(menu.containerId, next.index, 0, ContainerInput.PICKUP, mc.player);
        }
    }

    private static void processPage(List<DiamondMenus.Cell> cells) {
        refreshCatalog();

        for (DiamondMenus.Cell cell : cells) {
            boolean item = DiamondMenus.itemSlot(cell.slot());
            boolean equipped = cell.slot() == SELECTED_SLOT
                && cell.lore().stream().anyMatch(line -> CosmeticText.normalize(line).equals("используется"));
            if (item || equipped) {
                String key = DiamondMenus.match(cell.name(), cell.lore(), unique, targets);
                if (key != null) {
                    sessFound++;
                    if (!ProfileDb.isFound(key)) {
                        ProfileDb.markFound(key);
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

    private static void abort(String reason) {
        skipped.add(sessCat);
        notifyPlayer("Импорт инвентаря (" + sessLabel + ") прерван: " + reason);
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

    private static String label(ScanCoverage.Category category) {
        return switch (category.name()) {
            case "HATS" -> "голова";
            case "BACK" -> "аксессуары";
            case "PETS" -> "компаньоны";
            default -> category.name();
        };
    }

    private static boolean isOwnProfile(List<DiamondMenus.Cell> cells, Minecraft mc) {
        String self = mc.player.getGameProfile().name().toLowerCase(Locale.ROOT);

        for (DiamondMenus.Cell cell : cells) {
            if (cell.slot() <= 2) {
                String name = CosmeticText.normalize(cell.name());
                if (name.equals(self) || name.endsWith(" " + self)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static List<DiamondMenus.Cell> cells(AbstractContainerMenu menu) {
        List<DiamondMenus.Cell> cells = new ArrayList<>();

        for (Slot slot : Catalog.serverSlots(menu)) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                cells.add(new DiamondMenus.Cell(slot.index, stack.getHoverName().getString(), Catalog.lore(stack)));
            }
        }

        return cells;
    }

    private static void refreshCatalog() {
        Catalog catalog = DiamondFinder.catalog;
        if (catalog == null || catalog.entries.size() == catalogSize) {
            return;
        }

        catalogSize = catalog.entries.size();
        Map<String, String> byName = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();

        for (Catalog.Entry entry : catalog.entries.values()) {
            String base = DiamondMenus.baseName(entry.name);
            String previous = byName.putIfAbsent(base, entry.key);
            if (previous != null && !previous.equals(entry.key)) {
                ambiguous.add(base);
            }
        }

        for (String base : ambiguous) {
            byName.remove(base);
        }

        unique = byName;
        targets = new HashSet<>(catalog.entries.keySet());
    }

    private static Path file() {
        return DiamondFinder.catalog.dir.resolve("self-import.txt");
    }

    private static void ensureLoaded() {
        if (loaded || DiamondFinder.catalog == null) {
            return;
        }

        loaded = true;

        try {
            Path path = file();
            if (Files.exists(path)) {
                for (String line : Files.readAllLines(path)) {
                    if (!line.isBlank()) {
                        done.add(line.trim());
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        if (done.size() >= ScanCoverage.Category.values().length) {
            allDone = true;
        }
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), String.join("\n", done) + "\n");
        } catch (Throwable ignored) {
        }
    }

    private static void notifyPlayer(String message) {
        try {
            Method method = DiamondFinder.class.getDeclaredMethod("notifyPlayer", String.class);
            method.setAccessible(true);
            method.invoke(null, message);
        } catch (Throwable ignored) {
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
