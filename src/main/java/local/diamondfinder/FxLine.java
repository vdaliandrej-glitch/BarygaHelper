package local.diamondfinder;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class FxLine {
    private static final int COLOR = 0xFF22D3EE;

    private FxLine() {
    }

    public static void draw(FinderScreen screen, GuiGraphicsExtractor g, MenuScanner.Result result, String key, int x, int y, int width) {
        if (result != null && result.effects != null && key != null) {
            Object effect = result.effects.get(key);
            if (effect != null) {
                String text = effect.toString().trim();
                if (!text.isEmpty()) {
                    screen.text(g, screen.shorten("Эффект: " + text, width - 47), x + 36, y + 28, COLOR);
                }
            }
        }
    }
}
