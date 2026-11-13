package local.diamondfinder;

public final class MenuResponseGate {
    private int containerId = -1;
    private boolean ready;

    public void opened(int id) {
        this.containerId = id;
        this.ready = false;
    }

    public void clicked() {
        this.ready = false;
    }

    public void received(int id) {
        if (id == this.containerId && id >= 0) {
            this.ready = true;
        }
    }

    public boolean ready() {
        return this.ready;
    }

    public void reset() {
        this.containerId = -1;
        this.ready = false;
    }
}
