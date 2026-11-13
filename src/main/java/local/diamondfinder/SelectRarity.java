package local.diamondfinder;

public final class SelectRarity implements Runnable {
    private final CosmeticText.Rarity rarity;

    public SelectRarity(CosmeticText.Rarity var1) {
        this.rarity = var1;
    }

    @Override
    public void run() {
        Catalog var1 = DiamondFinder.catalog;
        if (var1 != null && this.rarity != null) {
            for (Catalog.Entry var3 : var1.entries.values()) {
                if (var3 != null && var3.key != null && var3.rarityValue() == this.rarity && !ProfileDb.isFound(var3.key)) {
                    var1.selected.add(var3.key);
                }
            }

            var1.dirty();
        }
    }
}
