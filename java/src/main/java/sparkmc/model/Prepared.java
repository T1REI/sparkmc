package sparkmc.model;

public final class Prepared {
    private final LaunchTarget target;
    private final Integer requiredJava;

    public Prepared(LaunchTarget target, Integer requiredJava) {
        this.target = target;
        this.requiredJava = requiredJava;
    }

    public LaunchTarget target() {
        return target;
    }

    public Integer requiredJava() {
        return requiredJava;
    }
}
