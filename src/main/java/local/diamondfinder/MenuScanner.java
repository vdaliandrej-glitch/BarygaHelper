package local.diamondfinder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.slf4j.LoggerFactory;

public final class MenuScanner {
    private MenuScanner.Phase phase = MenuScanner.Phase.IDLE;
    private AbstractContainerMenu owned;
    private final MenuResponseGate response = new MenuResponseGate();
    private Object connection;
    private String title = "";
    private String stableSignature = "";
    private String previousPage = "";
    private long since;
    private long stableAt;
    private long nextCommand;
    private int generation;
    private int clickGeneration;
    private final Deque<String> queue = new ArrayDeque<>();
    private final Set<String> pageSignatures = new HashSet<>();
    private final Set<String> targets = new HashSet<>();
    private final Map<String, String> uniqueNames = new HashMap<>();
    public final List<MenuScanner.Result> results = new ArrayList<>();
    public String message = "Выберите косметику для поиска";
    public int total;
    public int checked;
    public int errors;
    private int failures;
    private int pages;
    private MenuScanner.Result current;
    private ScanCoverage.Category category;
    private final Catalog catalog;

    public MenuScanner(Catalog catalog) {
        this.catalog = catalog;
        ProfileDb.load(this.results, this.catalog.selected);
    }

    public boolean running() {
        return this.phase != MenuScanner.Phase.IDLE;
    }

    public void start() {
        Minecraft mc = Minecraft.getInstance();
        if (DiamondFinder.allowedServer() && mc.player != null && mc.getConnection() != null) {
            if (false) {
                this.message = "Сначала выберите предметы";
            } else if (mc.player.containerMenu != mc.player.inventoryMenu) {
                this.message = "Сначала закройте серверное меню";
            } else {
                this.stop("Подготовка");
                ProfileDb.beforeScan(this.results);
                this.queue.clear();
                this.targets.clear();
                this.targets.addAll(this.catalog.entries.keySet());
                this.uniqueNames.clear();
                Set<String> ambiguous = new HashSet<>();

                for (Catalog.Entry entry : this.catalog.entries.values()) {
                    String name = DiamondMenus.baseName(entry.name);
                    String old = this.uniqueNames.putIfAbsent(name, entry.key);
                    if (old != null && !old.equals(entry.key)) {
                        ambiguous.add(name);
                    }
                }

                ambiguous.forEach(this.uniqueNames::remove);
                mc.getConnection()
                    .getOnlinePlayers()
                    .stream()
                    .map(p -> p.getProfile().name())
                    .filter(n -> n.matches("[A-Za-z0-9_]{1,16}"))
                    .filter(n -> !n.equalsIgnoreCase(mc.player.getGameProfile().name()))
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .limit(1000L)
                    .forEach(this.queue::addLast);
                ScanView.skip(this.queue, this.results);
                this.total = this.queue.size();
                this.checked = this.errors = this.failures = 0;
                this.connection = mc.getConnection();
                this.phase = MenuScanner.Phase.QUEUED;
                this.nextCommand = now();
                this.message = "В очереди: " + this.total;
                this.message = ProfileDb.note(this.message);
            }
        } else {
            this.message = "Подключитесь к DiamondWorld";
        }
    }

    public void stop(String reason) {
        ScanAssist.onStop(this, reason);
        if (this.running()) {
            log("stop phase=" + this.phase + ": " + reason);
        }

        if (this.current != null && "Проверяется".equals(this.current.status)) {
            this.current.status = "Прервано · " + this.current.coverage.summary();
            this.current.time = now();
        }

        this.closeOwned();
        this.phase = MenuScanner.Phase.IDLE;
        this.queue.clear();
        this.current = null;
        this.message = reason;
    }

    public boolean capture(Screen screen) {
        if (this.running() && screen instanceof AbstractContainerScreen<?> s) {
            String t = s.getTitle().getString();

            boolean expected = switch (this.phase) {
                case PROFILE -> CosmeticText.profileTitle(t);
                case HUB -> DiamondMenus.hub(t) || CosmeticText.profileTitle(t);
                case CATEGORY, PAGE -> DiamondMenus.category(t) == this.category;
                case RETURN_HUB -> DiamondMenus.hub(t);
                default -> false;
            };
            if (!expected) {
                log("unexpected menu=" + CosmeticText.clean(t));
                this.stop("Пауза: открыто другое меню");
                return false;
            } else {
                this.owned = s.getMenu();
                this.title = t;
                this.stableSignature = "";
                this.stableAt = now();
                this.generation++;
                this.response.opened(this.owned.containerId);
                log("captured phase=" + this.phase + " id=" + this.owned.containerId + " title=" + CosmeticText.clean(t));
                return true;
            }
        } else {
            return false;
        }
    }

    public void serverContent(int containerId) {
        if (this.running() && this.owned != null && containerId == this.owned.containerId) {
            this.response.received(containerId);
            this.stableAt = now();
        }
    }

    public void serverMessage(String text) {
        if (this.current != null && (this.phase == MenuScanner.Phase.PROFILE || this.phase == MenuScanner.Phase.HUB)) {
            String t = CosmeticText.normalize(text);
            this.onRate(t);
            if ((t.contains("инвентар") || t.contains("профиль")) && (t.contains("скрыт") || t.contains("скрыл") || t.contains("закрыт"))) {
                this.current.coverage.privateInventory();
                this.finish(true, "Скрыт · только витрина");
            }
        }
    }

    public void tick() {
        ProfileDb.refresh(this.results, this.catalog.selected);
        if (this.running()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.getConnection() == this.connection && DiamondFinder.allowedServer() && mc.player.isAlive()) {
                Screen screen = mc.gui.screen();
                if (screen != null && !(screen instanceof FinderScreen)) {
                    this.stop("Поиск остановлен: открыто другое окно");
                } else if (this.phase == MenuScanner.Phase.QUEUED) {
                    if (this.queue.isEmpty()) {
                        this.stop("Готово: " + this.checked + "/" + this.total + ", ошибок: " + this.errors);
                    } else if (mc.player.containerMenu == mc.player.inventoryMenu && now() >= this.nextCommand) {
                        this.current = new MenuScanner.Result(this.queue.removeFirst());
                        this.results.add(this.current);
                        this.category = null;
                        this.transition(MenuScanner.Phase.PROFILE);
                        this.message = "Профиль: " + this.current.player;
                        log("profile " + this.current.player);
                        DiamondFinder.sendAutomaticCommand("profile " + this.current.player);
                    }
                } else if (now() - this.since > 4500L) {
                    if (this.phase == MenuScanner.Phase.PAGE
                        && this.owned != null
                        && mc.player.containerMenu == this.owned
                        && DiamondMenus.category(this.title) == this.category) {
                        this.endCategory(false);
                    } else {
                        this.finish(false, "Неполная проверка: нет ответа (" + this.phase + ")");
                    }
                } else if (this.owned != null && mc.player.containerMenu == this.owned) {
                    if (this.response.ready()) {
                        if (!this.owned.getCarried().isEmpty()) {
                            this.stop("Пауза: предмет на курсоре");
                        } else {
                            String signature = Catalog.signature(this.owned);
                            if (!signature.equals(this.stableSignature)) {
                                this.stableSignature = signature;
                                this.stableAt = now();
                            } else if (now() - this.stableAt >= 180L && now() - this.since >= 250L) {
                                List<DiamondMenus.Cell> cells = this.cells();
                                if (!cells.isEmpty()) {
                                    if (this.phase == MenuScanner.Phase.PROFILE) {
                                        boolean owner = cells.stream()
                                            .filter(cx -> cx.slot() <= 2)
                                            .anyMatch(
                                                cx -> {
                                                    String n = CosmeticText.normalize(cx.name());
                                                    return n.equals(this.current.player.toLowerCase(Locale.ROOT))
                                                        || n.endsWith(" " + this.current.player.toLowerCase(Locale.ROOT));
                                                }
                                            );
                                        if (!owner) {
                                            this.finish(false, "Профиль другого игрока / владелец не распознан");
                                        } else {
                                            ProfileInfo.capture(this.current, cells);

                                            for (DiamondMenus.Cell c : cells) {
                                                if (DiamondMenus.showcaseSlot(c.slot())) {
                                                    this.match(c, true);
                                                }
                                            }

                                            this.current.coverage.showcaseChecked();
                                            if (DiamondMenus.privateInventory(cells)) {
                                                this.current.coverage.privateInventory();
                                                this.finish(true, "Скрыт · только витрина");
                                            } else {
                                                int inventory = DiamondMenus.named(cells, "инвентарь игрока");
                                                if (inventory < 0) {
                                                    this.finish(false, "Инвентарь недоступен · только витрина");
                                                } else {
                                                    this.transition(MenuScanner.Phase.HUB);
                                                    this.click(inventory);
                                                }
                                            }
                                        }
                                    } else if (this.phase == MenuScanner.Phase.HUB && CosmeticText.profileTitle(this.title)) {
                                        if (DiamondMenus.privateInventory(cells)) {
                                            this.current.coverage.privateInventory();
                                            this.finish(true, "Скрыт · только витрина");
                                        }
                                    } else if (this.phase != MenuScanner.Phase.HUB && this.phase != MenuScanner.Phase.RETURN_HUB) {
                                        if (DiamondMenus.category(this.title) == this.category && DiamondMenus.categoryLayout(cells)) {
                                            if (this.phase == MenuScanner.Phase.PAGE && signature.equals(this.previousPage)) {
                                                if (this.generation > this.clickGeneration) {
                                                    this.endCategory(true);
                                                }
                                            } else if (!this.pageSignatures.add(signature)) {
                                                this.endCategory(false);
                                            } else {
                                                this.pages++;

                                                for (DiamondMenus.Cell c : cells) {
                                                    if (DiamondMenus.itemSlot(c.slot())) {
                                                        this.match(c, false);
                                                    }
                                                }

                                                for (DiamondMenus.Cell c : cells) {
                                                    if (c.slot() == 40 && c.lore().stream().anyMatch(s -> CosmeticText.normalize(s).equals("используется"))) {
                                                        this.match(c, true);
                                                    }
                                                }

                                                Slot next = nextSlot(this.owned);
                                                if (DiamondMenus.lastPage(cells) || next == null) {
                                                    this.endCategory(true);
                                                } else if (this.pages >= 64) {
                                                    this.endCategory(false);
                                                } else {
                                                    this.previousPage = signature;
                                                    this.transition(MenuScanner.Phase.PAGE);
                                                    this.click(next.index);
                                                }
                                            }
                                        }
                                    } else if (DiamondMenus.hub(this.title)) {
                                        this.category = this.current.coverage.next();
                                        if (this.category == null) {
                                            this.finish(this.current.coverage.full(), this.current.coverage.summary());
                                        } else {
                                            int slot = DiamondMenus.categorySlot(this.category, cells);
                                            if (slot < 0) {
                                                this.finish(false, "Раздел не найден: " + this.category.label);
                                            } else {
                                                this.pages = 0;
                                                this.pageSignatures.clear();
                                                this.previousPage = "";
                                                this.message = this.current.player + " · " + this.category.label;
                                                this.transition(MenuScanner.Phase.CATEGORY);
                                                this.click(slot);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                this.stop("Поиск остановлен: смена подключения или смерть");
            }
        }
    }

    private List<DiamondMenus.Cell> cells() {
        return Catalog.serverSlots(this.owned)
            .stream()
            .filter(s -> !s.getItem().isEmpty())
            .map(s -> new DiamondMenus.Cell(s.index, s.getItem().getHoverName().getString(), Catalog.lore(s.getItem())))
            .toList();
    }

    private void match(DiamondMenus.Cell c, boolean equipped) {
        String key = DiamondMenus.match(c.name(), c.lore(), this.uniqueNames, this.targets);
        if (key != null) {
            this.current.found.add(key);
            if (equipped) {
                this.current.equipped.add(key);
            }
        }
    }

    private void endCategory(boolean complete) {
        if (complete) {
            this.current.coverage.complete(this.category);
        } else {
            this.current.coverage.incomplete(this.category);
        }

        if (this.current.coverage.next() == null) {
            String status = this.current.coverage.full()
                ? (this.current.found.isEmpty() ? "Совпадений нет · 3/3" : "Найдено · 3/3")
                : "Неполная проверка · " + this.current.coverage.summary();
            this.finish(this.current.coverage.full(), status);
        } else {
            int back = DiamondMenus.named(this.cells(), "назад");
            if (back < 0) {
                this.finish(false, "Нет кнопки возврата · " + this.current.coverage.summary());
            } else {
                this.transition(MenuScanner.Phase.RETURN_HUB);
                this.click(back);
            }
        }
    }

    public static Slot nextSlot(AbstractContainerMenu menu) {
        return menu.slots
            .stream()
            .filter(s -> !s.getItem().isEmpty())
            .filter(s -> CosmeticText.next(s.getItem().getHoverName().getString()))
            .filter(
                s -> Catalog.lore(s.getItem()).stream().map(CosmeticText::normalize).noneMatch(t -> t.contains("последняя страниц") || t.contains("недоступн"))
            )
            .findFirst()
            .orElse(null);
    }

    private void transition(MenuScanner.Phase next) {
        this.phase = next;
        this.since = now();
        this.stableAt = this.since;
        this.stableSignature = "";
        this.clickGeneration = this.generation;
    }

    private void click(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (this.owned != null && mc.gameMode != null && mc.player != null && mc.player.containerMenu == this.owned) {
            log(this.phase + " click=" + slot + " menu=" + CosmeticText.clean(this.title));
            this.response.clicked();
            mc.gameMode.handleContainerInput(this.owned.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
        }
    }

    private void finish(boolean success, String status) {
        if (this.current != null) {
            this.current.status = status;
            this.current.time = now();
            ProfileDb.onFinish(this.current, success, this.catalog.selected, this.results);
            log(this.current.player + ": " + status + ", matches=" + this.current.found.size());
        }

        this.checked++;
        if (success) {
            this.failures = 0;
        } else {
            this.errors++;
            this.failures++;
        }

        this.closeOwned();
        this.current = null;
        if (this.failures >= 3) {
            this.stop("Остановлено после 3 неполных проверок. См. журнал.");
        } else {
            this.phase = MenuScanner.Phase.QUEUED;
            this.nextCommand = now() + 500L;
            this.message = "Проверено " + this.checked + "/" + this.total;
        }
    }

    private void closeOwned() {
        Minecraft mc = Minecraft.getInstance();
        if (this.owned != null && mc.player != null && mc.player.containerMenu == this.owned) {
            mc.player.connection.send(new ServerboundContainerClosePacket(this.owned.containerId));
            mc.player.containerMenu = mc.player.inventoryMenu;
        }

        this.owned = null;
        this.response.reset();
    }

    private static void log(String text) {
        LoggerFactory.getLogger("BarygaHelper").info("[cosmetic-scan] {}", text);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private void onRate(String var1) {
        if (ProfileInfo.isRateLimit(var1) && this.current != null && this.phase == MenuScanner.Phase.PROFILE && ProfileInfo.allowRetry(this.current.player)) {
            this.queue.addFirst(this.current.player);
            this.results.remove(this.current);
            this.current = null;
            this.closeOwned();
            this.phase = MenuScanner.Phase.QUEUED;
            this.nextCommand = now() + 1500L;
        }
    }

    private enum Phase {
        IDLE,
        QUEUED,
        PROFILE,
        HUB,
        CATEGORY,
        PAGE,
        RETURN_HUB;
    }

    public static final class Result {
        public final String player;
        public final Set<String> found = new LinkedHashSet<>();
        public final Set<String> equipped = new LinkedHashSet<>();
        public final ScanCoverage coverage = new ScanCoverage();
        public String status = "Проверяется";
        public long time;
        public String rank;
        public String regDate;
        public String level;
        public Set<String> owned;
        public Set<String> ownedEq;
        public boolean complete;
        public Map<String, String> effects;
        public int scans;

        Result(String player) {
            this.player = player;
        }
    }
}
