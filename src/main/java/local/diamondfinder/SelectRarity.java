package local.diamondfinder;

public final class SelectRarity implements Runnable {
    private final CosmeticText.Rarity rarity;

    public SelectRarity(CosmeticText.Rarity rarity) {
        this.rarity = rarity;
    }

    @Override
    public void run() {
        Catalog catalog = DiamondFinder.catalog;
        if (catalog != null && this.rarity != null) {
            for (Catalog.Entry entry : catalog.entries.values()) {
                if (entry != null && entry.key != null && entry.rarityValue() == this.rarity && !ProfileDb.isFound(entry.key)) {
                    catalog.selected.add(entry.key);
                }
            }

            catalog.dirty();
        }
    }
}
