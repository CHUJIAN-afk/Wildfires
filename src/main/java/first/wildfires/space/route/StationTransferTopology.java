package first.wildfires.space.route;

import first.wildfires.space.celestial.CelestialDefinition;
import first.wildfires.space.celestial.CelestialKind;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Shared parent-system classification for station routes.  This is deliberately independent of
 * dimensions: a stable orbit around a body exists even when that body has no landable surface.
 */
public enum StationTransferTopology {

    PRIMARY_TO_SATELLITE,
    SATELLITE_TO_PRIMARY,
    SIBLING_SATELLITES,
    INTER_SYSTEM;

    public boolean isLocalSystemTransfer() {
        return this != INTER_SYSTEM;
    }

    public boolean isJumpEligible() {
        return this == INTER_SYSTEM;
    }

    public static StationTransferTopology classify(ResourceLocation fromId,
                                                    CelestialDefinition from,
                                                    ResourceLocation toId,
                                                    CelestialDefinition to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return classify(new Node(fromId, from.parent().orElse(null), from.kind()),
                new Node(toId, to.parent().orElse(null), to.kind()));
    }

    public static StationTransferTopology classify(Node from, Node to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.id().equals(to.id())) {
            throw new IllegalArgumentException("A transfer topology requires two different bodies");
        }
        if (to.isSatellite() && from.id().equals(to.parent())) {
            return PRIMARY_TO_SATELLITE;
        }
        if (from.isSatellite() && to.id().equals(from.parent())) {
            return SATELLITE_TO_PRIMARY;
        }
        if (from.isSatellite() && to.isSatellite() && from.parent().equals(to.parent())) {
            return SIBLING_SATELLITES;
        }
        return INTER_SYSTEM;
    }

    public record Node(ResourceLocation id, ResourceLocation parent, CelestialKind kind) {

        public Node {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
        }

        public boolean isSatellite() {
            return kind == CelestialKind.MOON && parent != null;
        }
    }
}
