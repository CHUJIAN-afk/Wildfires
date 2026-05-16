package first.wildfires.kinetic.loom.recipe;

import net.minecraft.network.chat.Component;

public enum WeavingType {
    KNITTED_CLOTH("knitted_cloth", "针织布"),
    WOVEN_BLOCK("woven_block", "编织块");

    private final String name;
    private final String displayName;

    WeavingType(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
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
