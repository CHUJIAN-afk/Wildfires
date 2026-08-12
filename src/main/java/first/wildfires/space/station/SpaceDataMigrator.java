package first.wildfires.space.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Explicit stepwise migration for the global space SavedData root. */
final class SpaceDataMigrator {

    private SpaceDataMigrator() {
    }

    static CompoundTag migrate(CompoundTag source, int sourceVersion) {
        if (sourceVersion < 0 || sourceVersion > SpaceSavedData.DATA_VERSION) {
            throw new IllegalArgumentException("Unsupported space data version for migration: " + sourceVersion);
        }
        CompoundTag migrated = source.copy();
        int version = sourceVersion;
        while (version < SpaceSavedData.DATA_VERSION) {
            migrated = switch (version) {
                case 0 -> migrateZeroToOne(migrated);
                default -> throw new IllegalArgumentException("Missing space data migrator from version "
                        + version);
            };
            version++;
        }
        migrated.putInt("data_version", SpaceSavedData.DATA_VERSION);
        return migrated;
    }

    private static CompoundTag migrateZeroToOne(CompoundTag source) {
        CompoundTag migrated = source.copy();
        requireCompatibleLegacyType(migrated, "stations", Tag.TAG_LIST);
        requireCompatibleLegacyType(migrated, "retired_regions", Tag.TAG_LIST);
        requireCompatibleLegacyType(migrated, "audit", Tag.TAG_LIST);
        requireCompatibleLegacyType(migrated, "next_region_ordinal", Tag.TAG_LONG);
        requireCompatibleLegacyType(migrated, "next_audit_sequence", Tag.TAG_LONG);
        if (!migrated.contains("stations", Tag.TAG_LIST)) {
            migrated.put("stations", new ListTag());
        }
        if (!migrated.contains("retired_regions", Tag.TAG_LIST)) {
            migrated.put("retired_regions", new ListTag());
        }
        if (!migrated.contains("audit", Tag.TAG_LIST)) {
            migrated.put("audit", new ListTag());
        }
        if (!migrated.contains("next_region_ordinal", Tag.TAG_LONG)) {
            migrated.putLong("next_region_ordinal", 1L);
        }
        if (!migrated.contains("next_audit_sequence", Tag.TAG_LONG)) {
            migrated.putLong("next_audit_sequence", 0L);
        }
        migrated.putInt("data_version", 1);
        return migrated;
    }

    private static void requireCompatibleLegacyType(CompoundTag tag, String key, int type) {
        if (tag.contains(key) && !tag.contains(key, type)) {
            throw new IllegalArgumentException("Legacy space data field has the wrong NBT type: " + key);
        }
    }
}
