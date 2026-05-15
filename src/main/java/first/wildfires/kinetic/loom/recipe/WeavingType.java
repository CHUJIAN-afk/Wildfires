package first.wildfires.kinetic.loom.recipe;

public enum WeavingType {
    KNITTED_CLOTH("knitted_cloth"),
    WOVEN_BLOCK("woven_block");

    private final String name;

    WeavingType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static WeavingType fromName(String name) {
        for (WeavingType type : values()) {
            if (type.name.equals(name)) {
                return type;
            }
        }
        return KNITTED_CLOTH;
    }
}
