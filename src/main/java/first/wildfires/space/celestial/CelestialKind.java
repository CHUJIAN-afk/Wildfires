package first.wildfires.space.celestial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Optional;

/** Stable data identifiers for the broad role of a celestial body. */
public enum CelestialKind {

    STAR("star"),
    PLANET("planet"),
    MOON("moon"),
    OTHER("other");

    public static final Codec<CelestialKind> CODEC = Codec.STRING.comapFlatMap(
            id -> fromId(id)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown celestial kind: " + id)),
            CelestialKind::id);

    private final String id;

    CelestialKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<CelestialKind> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(kind -> kind.id.equals(id)).findFirst();
    }
}
