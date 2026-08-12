package first.wildfires.space.station;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Stable member roles. The station owner is stored separately and implicitly has every permission. */
public enum StationPermission {
    MEMBER("member", 0),
    OPERATOR("operator", 1),
    MANAGER("manager", 2);

    private static final Map<String, StationPermission> BY_ID;

    static {
        Map<String, StationPermission> roles = new LinkedHashMap<>();
        for (StationPermission permission : values()) {
            if (roles.put(permission.id, permission) != null) {
                throw new ExceptionInInitializerError("Duplicate station permission id: " + permission.id);
            }
        }
        BY_ID = Map.copyOf(roles);
    }

    private final String id;
    private final int authority;

    StationPermission(String id, int authority) {
        this.id = id;
        this.authority = authority;
    }

    public String id() {
        return id;
    }

    public boolean mayOperate() {
        return authority >= OPERATOR.authority;
    }

    public boolean mayManage() {
        return authority >= MANAGER.authority;
    }

    public static Optional<StationPermission> fromId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }
}
