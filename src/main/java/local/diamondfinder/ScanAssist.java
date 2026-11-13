package local.diamondfinder;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

public final class ScanAssist {
    private static final long RESUME_DELAY_MS = 1500L;
    private static final long FEED_INTERVAL_MS = 2000L;
    private static ScanAssist.Pending pending;
    private static boolean ready;
    private static boolean broken;
    private static int errors;
    private static long lastFeed;
    private static Field fQueue;
    private static Field fCurrent;
    private static Field fConn;
    private static Field fPhase;
    private static Field fNext;
    private static Field fFail;
    private static Object phaseQueued;

    private ScanAssist() {
    }

    private static void init() throws Exception {
        if (ready) {
            return;
        }

        Class<MenuScanner> type = MenuScanner.class;
        fQueue = type.getDeclaredField("queue");
        fCurrent = type.getDeclaredField("current");
        fConn = type.getDeclaredField("connection");
        fPhase = type.getDeclaredField("phase");
        fNext = type.getDeclaredField("nextCommand");
        fFail = type.getDeclaredField("failures");

        for (Field field : new Field[]{fQueue, fCurrent, fConn, fPhase, fNext, fFail}) {
            field.setAccessible(true);
        }

        phaseQueued = MenuScanner.Phase.QUEUED;
        ready = true;
    }

    @SuppressWarnings("unchecked")
    private static Deque<String> queue(MenuScanner scanner) throws IllegalAccessException {
        return (Deque<String>) fQueue.get(scanner);
    }

    private static boolean autoReason(String reason) {
        return reason != null
            && (
                reason.startsWith("Пауза: открыто другое меню")
                    || reason.startsWith("Пауза: предмет на курсоре")
                    || reason.startsWith("Поиск остановлен: открыто другое окно")
            );
    }

    public static void onStop(MenuScanner scanner, String reason) {
        try {
            if (!autoReason(reason)) {
                pending = null;
                return;
            }

            if (!scanner.running()) {
                return;
            }

            init();
            ScanAssist.Pending next = new ScanAssist.Pending();
            next.queue = new ArrayList<>(queue(scanner));
            if (fCurrent.get(scanner) instanceof MenuScanner.Result current && !current.complete) {
                next.queue.add(0, current.player);
                scanner.results.remove(current);
            }

            next.connection = fConn.get(scanner);
            next.since = System.currentTimeMillis();
            next.reason = reason;
            pending = next;
        } catch (Throwable e) {
            pending = null;
        }
    }

    public static void tick() {
        if (broken) {
            return;
        }

        try {
            run();
        } catch (Throwable e) {
            if (++errors >= 5) {
                broken = true;
            }
        }
    }

    private static void run() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        MenuScanner scanner = DiamondFinder.scanner;
        if (mc == null || mc.player == null || scanner == null || !DiamondFinder.allowedServer()) {
            return;
        }

        MatchFix.migrate(scanner);
        long now = System.currentTimeMillis();
        if (scanner.running()) {
            feed(mc, scanner, now, null);
            return;
        }

        ScanAssist.Pending resume = pending;
        if (resume == null) {
            return;
        }

        if (!resume.noted) {
            resume.noted = true;
            scanner.message = resume.reason + " · продолжу автоматически, осталось " + resume.queue.size();
        }

        if (mc.getConnection() != resume.connection) {
            pending = null;
            return;
        }

        Screen screen = mc.gui.screen();
        boolean busy = !mc.player.isAlive()
            || screen != null && !(screen instanceof FinderScreen)
            || mc.player.containerMenu != mc.player.inventoryMenu;
        if (busy) {
            resume.since = now;
            return;
        }

        feed(mc, scanner, now, resume);
        if (now - resume.since < RESUME_DELAY_MS) {
            return;
        }

        if (resume.queue.isEmpty()) {
            pending = null;
            return;
        }

        init();
        Deque<String> queue = queue(scanner);
        queue.clear();
        queue.addAll(resume.queue);
        fPhase.set(scanner, phaseQueued);
        fConn.set(scanner, mc.getConnection());
        fNext.setLong(scanner, 0L);
        fFail.setInt(scanner, 0);
        scanner.message = "Продолжаю скан: осталось " + queue.size();
        pending = null;
    }

    private static void feed(Minecraft mc, MenuScanner scanner, long now, ScanAssist.Pending resume) throws Exception {
        if (now - lastFeed < FEED_INTERVAL_MS) {
            return;
        }

        lastFeed = now;
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            return;
        }

        String self = mc.player.getGameProfile().name();
        List<String> fresh = new ArrayList<>();

        for (PlayerInfo info : connection.getOnlinePlayers()) {
            String nick = info.getProfile().name();
            if (nick != null
                && nick.matches("[A-Za-z0-9_]{1,16}")
                && !nick.equalsIgnoreCase(self)
                && !ScanView.known(nick)
                && !fresh.contains(nick)) {
                fresh.add(nick);
            }
        }

        if (fresh.isEmpty()) {
            return;
        }

        for (String nick : fresh) {
            ScanView.add(nick);
        }

        Deque<String> toScan = new ArrayDeque<>(fresh);
        ProfileDb.skip(toScan, scanner.results);
        if (!toScan.isEmpty()) {
            init();
            if (resume != null) {
                resume.queue.addAll(toScan);
            } else {
                queue(scanner).addAll(toScan);
            }

            scanner.total = scanner.total + toScan.size();
        }
    }

    private static final class Pending {
        List<String> queue;
        Object connection;
        long since;
        String reason;
        boolean noted;
    }
}
