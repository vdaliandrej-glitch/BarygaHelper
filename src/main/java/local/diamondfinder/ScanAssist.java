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
        if (!ready) {
            Class<MenuScanner> var0 = MenuScanner.class;
            fQueue = var0.getDeclaredField("queue");
            fCurrent = var0.getDeclaredField("current");
            fConn = var0.getDeclaredField("connection");
            fPhase = var0.getDeclaredField("phase");
            fNext = var0.getDeclaredField("nextCommand");
            fFail = var0.getDeclaredField("failures");

            for (Field var4 : new Field[]{fQueue, fCurrent, fConn, fPhase, fNext, fFail}) {
                var4.setAccessible(true);
            }

            phaseQueued = Enum.valueOf((Class)Class.forName("local.diamondfinder.MenuScanner$Phase"), "QUEUED");
            ready = true;
        }
    }

    private static boolean autoReason(String var0) {
        return var0 != null
            && (
                var0.startsWith("Пауза: открыто другое меню")
                    || var0.startsWith("Пауза: предмет на курсоре")
                    || var0.startsWith("Поиск остановлен: открыто другое окно")
            );
    }

    public static void onStop(MenuScanner var0, String var1) {
        try {
            if (!autoReason(var1)) {
                pending = null;
                return;
            }

            if (!var0.running()) {
                return;
            }

            init();
            ScanAssist.Pending var2 = new ScanAssist.Pending();
            var2.queue = new ArrayList<>((Deque)fQueue.get(var0));
            if (fCurrent.get(var0) instanceof MenuScanner.Result var4 && !var4.complete) {
                var2.queue.add(0, var4.player);
                var0.results.remove(var4);
            }

            var2.connection = fConn.get(var0);
            var2.since = System.currentTimeMillis();
            var2.reason = var1;
            pending = var2;
        } catch (Throwable var5) {
            pending = null;
        }
    }

    public static void tick() {
        if (!broken) {
            try {
                run();
            } catch (Throwable var1) {
                if (++errors >= 5) {
                    broken = true;
                }
            }
        }
    }

    private static void run() throws Exception {
        Minecraft var0 = Minecraft.getInstance();
        MenuScanner var1 = DiamondFinder.scanner;
        if (var0 != null && var0.player != null && var1 != null && DiamondFinder.allowedServer()) {
            MatchFix.migrate(var1);
            long var2 = System.currentTimeMillis();
            if (var1.running()) {
                feed(var0, var1, var2, null);
            } else {
                ScanAssist.Pending var4 = pending;
                if (var4 != null) {
                    if (!var4.noted) {
                        var4.noted = true;
                        var1.message = var4.reason + " · продолжу автоматически, осталось " + var4.queue.size();
                    }

                    if (var0.getConnection() != var4.connection) {
                        pending = null;
                    } else if (!var0.player.isAlive()) {
                        var4.since = var2;
                    } else {
                        Screen var5 = var0.gui.screen();
                        if (var5 != null && !(var5 instanceof FinderScreen)) {
                            var4.since = var2;
                        } else if (var0.player.containerMenu != var0.player.inventoryMenu) {
                            var4.since = var2;
                        } else {
                            feed(var0, var1, var2, var4);
                            if (var2 - var4.since >= 1500L) {
                                if (var4.queue.isEmpty()) {
                                    pending = null;
                                } else {
                                    init();
                                    Deque var6 = (Deque)fQueue.get(var1);
                                    var6.clear();
                                    var6.addAll(var4.queue);
                                    fPhase.set(var1, phaseQueued);
                                    fConn.set(var1, var0.getConnection());
                                    fNext.setLong(var1, 0L);
                                    fFail.setInt(var1, 0);
                                    var1.message = "Продолжаю скан: осталось " + var6.size();
                                    pending = null;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void feed(Minecraft var0, MenuScanner var1, long var2, ScanAssist.Pending var4) throws Exception {
        if (var2 - lastFeed >= 2000L) {
            lastFeed = var2;
            ClientPacketListener var5 = var0.getConnection();
            if (var5 != null) {
                String var6 = var0.player.getGameProfile().name();
                ArrayList<String> var7 = new ArrayList<>();

                for (PlayerInfo var9 : var5.getOnlinePlayers()) {
                    String var10 = var9.getProfile().name();
                    if (var10 != null
                        && var10.matches("[A-Za-z0-9_]{1,16}")
                        && !var10.equalsIgnoreCase(var6)
                        && !ScanView.known(var10)
                        && !var7.contains(var10)) {
                        var7.add(var10);
                    }
                }

                if (!var7.isEmpty()) {
                    for (String var13 : var7) {
                        ScanView.add(var13);
                    }

                    ArrayDeque var12 = new ArrayDeque(var7);
                    ProfileDb.skip(var12, var1.results);
                    if (!var12.isEmpty()) {
                        init();
                        if (var4 != null) {
                            var4.queue.addAll(var12);
                        } else {
                            ((Deque)fQueue.get(var1)).addAll(var12);
                        }

                        var1.total = var1.total + var12.size();
                    }
                }
            }
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
