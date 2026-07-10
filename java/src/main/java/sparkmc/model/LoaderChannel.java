package sparkmc.model;

public enum LoaderChannel {
    Recommended,
    Latest;

    public String label() {
        return name();
    }

    public String key() {
        return name().toLowerCase();
    }

    public static LoaderChannel[] all() {
        return values();
    }
}
