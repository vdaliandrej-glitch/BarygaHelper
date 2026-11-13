package local.diamondfinder;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Credit {
    private static final int FROM = 0x61D7A4;
    private static final int TO = 0x680BAB;

    private Credit() {
    }

    public static void draw(FinderScreen screen, GuiGraphicsExtractor g, String text, int x, int y, int width) {
        long now = System.currentTimeMillis();
        int length = text.length();

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c != ' ') {
                int charX = x + screen.textWidth(text.substring(0, i));
                double wave = Math.sin(now / 300.0 - i * 0.5);
                int bounce = wave > 0.85 ? -1 : 0;
                double blend = 0.5 + 0.5 * Math.sin(now / 700.0 - i * 0.28);
                screen.text(g, String.valueOf(c), charX, y + bounce, mix(FROM, TO, (float) blend));
            }
        }
    }

    private static int mix(int from, int to, float t) {
        int r = (int) ((from >> 16 & 0xFF) * (1.0F - t) + (to >> 16 & 0xFF) * t + 0.5F);
        int g = (int) ((from >> 8 & 0xFF) * (1.0F - t) + (to >> 8 & 0xFF) * t + 0.5F);
        int b = (int) ((from & 0xFF) * (1.0F - t) + (to & 0xFF) * t + 0.5F);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
