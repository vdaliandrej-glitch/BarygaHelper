package local.diamondfinder;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DiamondMenus {
    public static boolean hub(String title) {
        return CosmeticText.normalize(title).equals("кастомизация");
    }

    public static ScanCoverage.Category category(String title) {
        String t = CosmeticText.normalize(title);
        if (t.equals("кастомизация / голова")) {
            return ScanCoverage.Category.HATS;
        } else if (t.equals("кастомизация / аксессуары")) {
            return ScanCoverage.Category.BACK;
        } else {
            return t.equals("кастомизация / компаньоны") ? ScanCoverage.Category.PETS : null;
        }
    }

    public static int categorySlot(ScanCoverage.Category c, List<DiamondMenus.Cell> cells) {
        String label = switch (c) {
            case HATS -> "голова";
            case BACK -> "аксессуары";
            case PETS -> "компаньоны";
        };
        return named(cells, label);
    }

    public static int named(List<DiamondMenus.Cell> cells, String name) {
        return cells.stream().filter(c -> CosmeticText.normalize(c.name).equals(name)).mapToInt(DiamondMenus.Cell::slot).findFirst().orElse(-1);
    }

    public static boolean itemSlot(int slot) {
        return slot >= 1 && slot <= 25 && slot % 9 >= 1 && slot % 9 <= 7;
    }

    public static boolean showcaseSlot(int slot) {
        return slot == 8 || slot == 17 || slot == 26;
    }

    public static boolean categoryLayout(List<DiamondMenus.Cell> cells) {
        return named(cells, "назад") == 36 && named(cells, "редкость") == 39 && named(cells, "кейс") == 41;
    }

    public static boolean lastPage(List<DiamondMenus.Cell> cells) {
        return categoryLayout(cells) && cells.stream().filter(c -> itemSlot(c.slot) && !c.name.isBlank()).count() < 21L;
    }

    public static boolean privateInventory(List<DiamondMenus.Cell> cells) {
        return cells.stream().anyMatch(c -> {
            String name = CosmeticText.normalize(c.name);
            if (c.slot != 44 && !name.contains("инвентар") && !name.contains("профиль")) {
                return false;
            }

            String text = name + " " + CosmeticText.normalize(String.join(" ", c.lore));
            return text.contains("скрыт") || text.contains("скрыл") || text.contains("закрыт") || text.contains("недоступен") || text.contains("запретил");
        });
    }

    public static String baseName(String name) {
        return CosmeticText.normalize(name).replaceFirst("\\s*❖\\s*$", "").trim();
    }

    public static String match(String name, List<String> lore, Map<String, String> byName, Set<String> keys) {
        return MatchFix.match(name, lore, byName, keys);
    }

    public record Cell(int slot, String name, List<String> lore) {
    }
}
