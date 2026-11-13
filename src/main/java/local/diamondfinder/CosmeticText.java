package local.diamondfinder;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class CosmeticText {
    private static final Pattern FORMAT = Pattern.compile("§[0-9a-fk-orx]", 2);

    public static String clean(String s) {
        return FORMAT.matcher(s).replaceAll("").replace(' ', ' ').replaceAll("[\\p{Co}\\u200B-\\u200D\\uFEFF]", "").trim();
    }

    public static String name(String s) {
        return clean(s).replaceFirst("\\s*[✗✘✖✕❌✓✔☑]+\\s*$", "").trim();
    }

    public static String normalize(String s) {
        return name(s).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static CosmeticText.Rarity rarity(List<String> lore) {
        for (String line : lore) {
            String s = clean(line).toLowerCase(Locale.ROOT);
            if (s.contains("редкость")) {
                if (s.contains("уникальн") || s.contains("diamond") || s.contains("даймонд")) {
                    return CosmeticText.Rarity.EXCLUDED;
                }

                if (s.contains("мифическ")) {
                    return CosmeticText.Rarity.MYTHIC;
                }

                if (s.contains("легендарн")) {
                    return CosmeticText.Rarity.LEGENDARY;
                }

                if (s.contains("эпическ")) {
                    return CosmeticText.Rarity.EPIC;
                }

                if (s.contains("редкая")) {
                    return CosmeticText.Rarity.RARE;
                }

                if (s.contains("обычн")) {
                    return CosmeticText.Rarity.COMMON;
                }
            }
        }

        return CosmeticText.Rarity.UNKNOWN;
    }

    public static String key(String name, CosmeticText.Rarity rarity) {
        return normalize(name) + "|" + rarity.name();
    }

    public static boolean unavailable(String name, List<String> lore) {
        return clean(name).matches(".*[✗✘✖✕❌]\\s*$")
            ? true
            : lore.stream()
                .map(CosmeticText::normalize)
                .anyMatch(s -> s.contains("не получен") || s.contains("не приобретен") || s.contains("не приобретён") || s.contains("нет в наличии"));
    }

    public static boolean catalogTitle(String title) {
        String t = normalize(title);
        return t.contains("поиск кастомизации") || t.contains("каталог косметики");
    }

    public static boolean profileTitle(String title) {
        return normalize(title).contains("профиль") || normalize(title).contains("profile");
    }

    public static boolean inventoryTitle(String title) {
        String t = normalize(title);
        return !catalogTitle(t) && (t.contains("инвентарь") || t.contains("косметик") || t.contains("кастомизац"));
    }

    public static boolean next(String name) {
        String t = normalize(name);
        return t.contains("следующ") || t.equals("вперед") || t.equals("вперёд") || t.equals("next") || t.equals("next page");
    }

    public enum Rarity {
        COMMON("Обычная", -5326648),
        RARE("Редкая", -11691777),
        EPIC("Эпическая", -4360449),
        LEGENDARY("Легендарная", -19154),
        MYTHIC("Мифическая", -14619195),
        EXCLUDED("Непередаваемая", -8947849),
        UNKNOWN("Неизвестная", -8947849);

        public final String label;
        public final int color;

        Rarity(String label, int color) {
            this.label = label;
            this.color = color;
        }

        public boolean tradable() {
            return this != EXCLUDED && this != UNKNOWN;
        }
    }
}
