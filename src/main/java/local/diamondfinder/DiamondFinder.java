package local.diamondfinder;

import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.time.Clock;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.Game;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.Chat;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.Command;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.slf4j.LoggerFactory;

public final class DiamondFinder implements ClientModInitializer {
    public static Catalog catalog;
    public static MenuScanner scanner;
    private static LocalMessageLog messageLog;
    private static boolean automaticCommand;
    public static String importMessage = "Откройте каталог сервера — страницы сохраняются автоматически";
    private static AbstractContainerMenu visibleMenu;
    private static String signature = "";
    private static String importedSignature = "";
    private static long stableAt;
    private static long clickedAt;
    private static boolean autoPages;
    private static String clickedSignature = "";
    private static int pages;
    private static final Set<String> seen = new HashSet<>();

    public void onInitializeClient() {
        catalog = new Catalog();
        scanner = new MenuScanner(catalog);
        messageLog = new LocalMessageLog(
            FabricLoader.getInstance().getConfigDir().resolve("ShintaroHelper.cfg"),
            FabricLoader.getInstance().getGameDir().resolve("logs/ShintaroHelper/message"),
            Clock.systemDefaultZone(),
            text -> LoggerFactory.getLogger("BarygaHelper").warn(text)
        );
        ClientSendMessageEvents.CHAT.register((Chat)text -> messageLog.record(text, false));
        ClientSendMessageEvents.COMMAND.register((Command)text -> {
            if (!automaticCommand) {
                messageLog.record(text, true);
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)mc -> messageLog.close());
        ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)mc -> catalog.save());
        ClientReceiveMessageEvents.GAME.register((Game)(text, overlay) -> {
            if (!overlay) {
                scanner.serverMessage(text.getString());
            }
        });
        KeyMapping open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.diamond_finder.open", Type.KEYSYM, 73, Category.MISC));
        KeyMapping stop = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.diamond_finder.stop", Type.KEYSYM, 297, Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> {
            while (open.consumeClick()) {
                if (mc.player != null && mc.gui.screen() == null) {
                    mc.gui.setScreen(new FinderScreen());
                }
            }

            while (stop.consumeClick()) {
                stopAll();
            }

            catalog.tick();
            ScanAssist.tick();
            SelfImport.tick();
            importTick();
            scanner.tick();
        });
        ClientCommandRegistrationCallback.EVENT
            .register(
                (ClientCommandRegistrationCallback)(dispatcher, access) -> dispatcher.register(
                    (LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("df").executes(c -> {
                        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(new FinderScreen()));
                        return 1;
                    })).then(ClientCommands.literal("stop").executes(c -> {
                        stopAll();
                        return 1;
                    }))
                )
            );
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("diamond_finder", "progress"), (g, tick) -> {
            Minecraft mc = Minecraft.getInstance();
            if (scanner.running() && mc.gui.screen() == null) {
                int x = Math.max(4, g.guiWidth() - 188);
                int y = 7;
                g.fill(x, y, x + 180, y + 32, -653256670);
                g.fill(x, y, x + 2, y + 32, -14623512);
                g.text(mc.font, "BARYGA HELPER  " + scanner.checked + "/" + scanner.total, x + 8, y + 5, -1575428, false);
                g.text(mc.font, "I — результаты   F8 — стоп", x + 8, y + 18, -7953993, false);
            }
        });
    }

    public static boolean allowedServer() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (server == null) {
            return false;
        }

        String host = server.ip.toLowerCase(Locale.ROOT).split(":")[0];
        return host.equals("diamondworld.pro") || host.endsWith(".diamondworld.pro");
    }

    public static void sendAutomaticCommand(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            automaticCommand = true;

            try {
                connection.sendCommand(command);
            } finally {
                automaticCommand = false;
            }
        }
    }

    public static void stopAll() {
        autoPages = false;
        if (scanner != null) {
            scanner.stop("Остановлено");
        }

        importMessage = "Автоперелистывание остановлено";
    }

    public static boolean menuKey(int key, int modifiers) {
        if (catalog == null) {
            return false;
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen && allowedServer())) {
                return false;
            } else if (key == 68 && (modifiers & 2) != 0 && (modifiers & 1) != 0) {
                catalog.dump(screen.getTitle().getString(), screen.getMenu());
                notifyPlayer("Снимок меню сохранён: config/diamond-finder");
                return true;
            } else if (key == 79 && (modifiers & 2) != 0 && CosmeticText.catalogTitle(screen.getTitle().getString())) {
                autoPages = !autoPages;
                seen.clear();
                pages = 0;
                clickedSignature = "";
                clickedAt = 0L;
                importMessage = autoPages ? "Импорт страниц запущен" : "Автоперелистывание остановлено";
                notifyPlayer(importMessage);
                return true;
            } else if (key == 297) {
                stopAll();
                return true;
            } else {
                return false;
            }
        }
    }

    private static void notifyPlayer(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.gui.hud.setOverlayMessage(Component.literal("[BarygaHelper] " + text), false);
        }
    }

    private static void importTick() {
        Minecraft mc = Minecraft.getInstance();
        if (allowedServer() && mc.gui.screen() instanceof AbstractContainerScreen<?> screen && CosmeticText.catalogTitle(screen.getTitle().getString())) {
            AbstractContainerMenu menu = screen.getMenu();
            long now = System.currentTimeMillis();
            String nextSignature = Catalog.signature(menu, true);
            if (visibleMenu != menu || !signature.equals(nextSignature)) {
                visibleMenu = menu;
                signature = nextSignature;
                stableAt = now;
            } else if (now - stableAt >= 300L) {
                if (!signature.equals(importedSignature)) {
                    int added = catalog.importMenu(menu);
                    importedSignature = signature;
                    importMessage = "В каталоге: " + catalog.entries.size() + " · новых на странице: " + added;
                    if (added > 0) {
                        notifyPlayer(importMessage);
                    }
                }

                if (autoPages) {
                    if (!clickedSignature.isEmpty() && signature.equals(clickedSignature)) {
                        if (now - clickedAt > 4000L) {
                            autoPages = false;
                            importMessage = "Страница не изменилась. Импорт остановлен.";
                            notifyPlayer(importMessage);
                        }
                    } else if (seen.add(signature) && ++pages <= 64) {
                        Slot next = MenuScanner.nextSlot(menu);
                        if (next == null) {
                            autoPages = false;
                            importMessage = "Страница сохранена. Стрелка «Далее» не найдена — можно листать вручную.";
                            notifyPlayer(importMessage);
                        } else if (mc.gameMode != null && mc.player != null && mc.player.containerMenu == menu && menu.getCarried().isEmpty()) {
                            clickedSignature = signature;
                            clickedAt = now;
                            mc.gameMode.handleContainerInput(menu.containerId, next.index, 0, ContainerInput.PICKUP, mc.player);
                        } else {
                            autoPages = false;
                        }
                    } else {
                        autoPages = false;
                        importMessage = "Импорт остановлен: повтор страницы или лимит";
                        notifyPlayer(importMessage);
                    }
                }
            }
        } else {
            autoPages = false;
            visibleMenu = null;
            signature = "";
            importedSignature = "";
        }
    }
}
