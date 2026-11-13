package local.diamondfinder;

import java.util.EnumSet;
import java.util.Set;

public final class ScanCoverage {
    private final EnumSet<ScanCoverage.Category> completed = EnumSet.noneOf(ScanCoverage.Category.class);
    private final EnumSet<ScanCoverage.Category> attempted = EnumSet.noneOf(ScanCoverage.Category.class);
    private boolean showcase;
    private boolean hidden;

    public void showcaseChecked() {
        this.showcase = true;
    }

    public void privateInventory() {
        this.hidden = true;
    }

    public void complete(ScanCoverage.Category category) {
        this.completed.add(category);
        this.attempted.add(category);
    }

    public void incomplete(ScanCoverage.Category category) {
        this.attempted.add(category);
    }

    public ScanCoverage.Category next() {
        for (ScanCoverage.Category category : ScanCoverage.Category.values()) {
            if (!this.attempted.contains(category)) {
                return category;
            }
        }

        return null;
    }

    public boolean full() {
        return this.completed.size() == ScanCoverage.Category.values().length && !this.hidden;
    }

    public boolean hidden() {
        return this.hidden;
    }

    public Set<ScanCoverage.Category> completed() {
        return Set.copyOf(this.completed);
    }

    public String summary() {
        if (this.hidden) {
            return "Скрыт · только витрина";
        } else if (this.full()) {
            return "Проверены все 3 раздела";
        } else if (!this.completed.isEmpty()) {
            return "Проверено разделов: " + this.completed.size() + "/3";
        } else {
            return this.showcase ? "Только витрина" : "Не проверен";
        }
    }

    public enum Category {
        HATS("Головные уборы"),
        BACK("Аксессуары на спину"),
        PETS("Питомцы");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }
}
