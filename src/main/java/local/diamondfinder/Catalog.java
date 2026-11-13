package local.diamondfinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.LoggerFactory;

public final class Catalog {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public final Path dir = FabricLoader.getInstance().getConfigDir().resolve("diamond-finder");
    public final Map<String, Catalog.Entry> entries = new LinkedHashMap<>();
    public final Set<String> selected = new LinkedHashSet<>();
    private long dirtyAt;
    private boolean iconsDecoded;
    public String error = "";

    public Catalog() {
        try {
            Path file = this.dir.resolve("catalog.json");
            if (Files.exists(file)) {
                Catalog.Saved saved = (Catalog.Saved)this.gson.fromJson(Files.readString(file), Catalog.Saved.class);
                if (saved != null && saved.entries != null) {
                    for (Catalog.Entry e : saved.entries) {
                        if (e != null && e.name != null && e.rarityValue().tradable()) {
                            e.key = CosmeticText.key(e.name, e.rarityValue());
                            e.stack = ItemStack.EMPTY;
                            this.entries.put(e.key, e);
                        }
                    }
                }

                if (saved != null && saved.selected != null) {
                    this.selected.addAll(saved.selected);
                }

                this.selected.retainAll(this.entries.keySet());
            }
        } catch (Exception e) {
            this.fail("Не удалось прочитать каталог", e);
        }
    }

    private void fail(String text, Exception e) {
        this.error = text;
        LoggerFactory.getLogger("diamond-finder").warn(text, e);
    }

    public static List<String> lore(ItemStack stack) {
        ItemLore lore = (ItemLore)stack.get(DataComponents.LORE);
        return lore == null ? List.of() : lore.lines().stream().map(c -> CosmeticText.clean(c.getString())).toList();
    }

    public static List<Slot> serverSlots(AbstractContainerMenu menu) {
        return menu.slots.stream().filter(s -> !(s.container instanceof Inventory)).toList();
    }

    public static String signature(AbstractContainerMenu menu) {
        return signature(menu, false);
    }

    public static String signature(AbstractContainerMenu menu, boolean allSlots) {
        StringBuilder s = new StringBuilder();

        for (Slot slot : allSlots ? menu.slots : serverSlots(menu)) {
            ItemStack item = slot.getItem();
            s.append(slot.index).append(':').append(ItemStack.hashItemAndComponents(item)).append(';');
        }

        return s.toString();
    }

    public int importMenu(AbstractContainerMenu menu) {
        int added = 0;

        for (Slot slot : menu.slots) {
            ItemStack item = slot.getItem();
            if (!item.isEmpty()) {
                List<String> lore = lore(item);
                CosmeticText.Rarity rarity = CosmeticText.rarity(lore);
                if (rarity.tradable()) {
                    String name = CosmeticText.name(item.getHoverName().getString());
                    if (!name.isBlank()) {
                        String key = CosmeticText.key(name, rarity);
                        Catalog.Entry existing = this.entries.get(key);
                        if (existing == null || existing.stack == null || !ItemStack.isSameItemSameComponents(existing.stack, item)) {
                            Catalog.Entry entry = new Catalog.Entry();
                            entry.key = key;
                            entry.name = name;
                            entry.rarity = rarity.name();
                            entry.lore = lore;
                            entry.stack = item.copyWithCount(1);
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.level != null) {
                                entry.icon = (JsonElement)ItemStack.CODEC
                                    .encodeStart(mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), entry.stack)
                                    .result()
                                    .orElse(null);
                            }

                            this.entries.put(key, entry);
                            if (existing == null) {
                                added++;
                            }

                            this.dirty();
                        }
                    }
                }
            }
        }

        return added;
    }

    public void dirty() {
        this.dirtyAt = System.currentTimeMillis();
    }

    public void toggle(String key) {
        if (!this.selected.remove(key)) {
            this.selected.add(key);
        }

        this.dirty();
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            this.iconsDecoded = false;
            if (this.dirtyAt > 0L) {
                this.save();
            }
        } else {
            if (!this.iconsDecoded) {
                RegistryOps<JsonElement> ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);

                for (Catalog.Entry e : this.entries.values()) {
                    if (e.icon != null) {
                        e.stack = ItemStack.CODEC.parse(ops, e.icon).result().orElse(ItemStack.EMPTY);
                    }
                }

                this.iconsDecoded = true;
            }

            if (this.dirtyAt > 0L && System.currentTimeMillis() - this.dirtyAt > 800L) {
                this.save();
            }
        }
    }

    public void save() {
        try {
            Files.createDirectories(this.dir);
            Catalog.Saved saved = new Catalog.Saved();
            saved.entries.addAll(this.entries.values());
            saved.selected.addAll(this.selected);
            Path temp = this.dir.resolve("catalog.json.tmp");
            Path destination = this.dir.resolve("catalog.json");
            Files.writeString(temp, this.gson.toJson(saved), StandardCharsets.UTF_8);

            try {
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            this.dirtyAt = 0L;
            this.error = "";
        } catch (Exception e) {
            this.dirtyAt = System.currentTimeMillis() + 5000L;
            this.fail("Ошибка сохранения каталога", e);
        }
    }

    public void dump(String title, AbstractContainerMenu menu) {
        try {
            Files.createDirectories(this.dir);
            JsonObject root = new JsonObject();
            root.addProperty("title", title);
            JsonArray slots = new JsonArray();

            for (Slot slot : menu.slots) {
                if (!slot.getItem().isEmpty()) {
                    JsonObject s = new JsonObject();
                    s.addProperty("slot", slot.index);
                    s.addProperty("playerInventory", slot.container instanceof Inventory);
                    s.addProperty("name", slot.getItem().getHoverName().getString());
                    s.add("lore", this.gson.toJsonTree(lore(slot.getItem())));
                    slots.add(s);
                }
            }

            root.add("slots", slots);
            Files.writeString(this.dir.resolve("menu-" + System.currentTimeMillis() + ".json"), this.gson.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            this.fail("Ошибка записи меню", e);
        }
    }

    public static final class Entry {
        public String key;
        public String name;
        public String rarity;
        public List<String> lore = new ArrayList<>();
        public JsonElement icon;
        public transient ItemStack stack = ItemStack.EMPTY;

        public CosmeticText.Rarity rarityValue() {
            try {
                return CosmeticText.Rarity.valueOf(this.rarity);
            } catch (Exception e) {
                return CosmeticText.Rarity.UNKNOWN;
            }
        }
    }

    private static final class Saved {
        int schema = 1;
        List<Catalog.Entry> entries = new ArrayList<>();
        Set<String> selected = new LinkedHashSet<>();
    }
}
