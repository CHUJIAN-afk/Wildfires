package first.wildfires.space.station;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Stable virtual station-side endpoint used before physical docking-port content exists. */
public record StationDockRecord(ResourceLocation id, BlockPos position) {

    public StationDockRecord {
        Objects.requireNonNull(id, "id");
        position = Objects.requireNonNull(position, "position").immutable();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id.toString());
        tag.putInt("x", position.getX());
        tag.putInt("y", position.getY());
        tag.putInt("z", position.getZ());
        return tag;
    }

    public static StationDockRecord load(CompoundTag tag) {
        if (!tag.contains("id", Tag.TAG_STRING)
                || !tag.contains("x", Tag.TAG_INT)
                || !tag.contains("y", Tag.TAG_INT)
                || !tag.contains("z", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Station dock is missing a required field");
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
        if (id == null) {
            throw new IllegalArgumentException("Invalid station dock id: " + tag.getString("id"));
        }
        return new StationDockRecord(id,
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")));
    }
}
