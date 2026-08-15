package first.wildfires.celestial;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/** World-persistent creation-time ephemeris stored only in overworld DataStorage. */
public final class CelestialEphemerisSavedData extends SavedData {

    public static final int DATA_VERSION = 1;
    public static final String FILE_ID = "wildfires_celestial_ephemeris";

    private CelestialOrbitalPhases phases;
    private volatile SettingsCache settingsCache;

    public CelestialEphemerisSavedData() {
    }

    private CelestialEphemerisSavedData(CelestialOrbitalPhases phases) {
        this.phases = Objects.requireNonNull(phases, "phases");
    }

    public static CelestialEphemerisSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        CelestialEphemerisSavedData data = server.overworld().getDataStorage().computeIfAbsent(
                CelestialEphemerisSavedData::load, CelestialEphemerisSavedData::new, FILE_ID);
        if (data.phases == null) {
            UUID entropy = UUID.randomUUID();
            long seed = entropy.getMostSignificantBits() ^ entropy.getLeastSignificantBits()
                    ^ server.overworld().getSeed();
            data.phases = CelestialOrbitalPhases.random(new Random(seed),
                    CelestialConfig.serverSettings().planetSettings());
            data.settingsCache = null;
            data.setDirty();
        }
        return data;
    }

    public CelestialOrbitalPhases phases() {
        if (phases == null) {
            throw new IllegalStateException("Creation-time ephemeris has not been initialized");
        }
        return phases;
    }

    /** Reuses the immutable server-settings/creation-ephemeris combination on the hot query path. */
    CelestialRuntimeSettings settings(CelestialRuntimeSettings base) {
        Objects.requireNonNull(base, "base");
        CelestialOrbitalPhases currentPhases = phases();
        SettingsCache current = settingsCache;
        if (current != null && current.base() == base && current.phases() == currentPhases) {
            return current.settings();
        }
        synchronized (this) {
            current = settingsCache;
            if (current != null && current.base() == base && current.phases() == currentPhases) {
                return current.settings();
            }
            CelestialRuntimeSettings combined = base.withOrbitalPhases(currentPhases);
            settingsCache = new SettingsCache(base, currentPhases, combined);
            return combined;
        }
    }

    public static CelestialEphemerisSavedData load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (!tag.contains("data_version", Tag.TAG_INT)
                || tag.getInt("data_version") != DATA_VERSION
                || !(tag.get("phases") instanceof ListTag entries)) {
            throw new IllegalArgumentException("Invalid celestial ephemeris SavedData root");
        }
        Map<ResourceLocation, Double> phases = new LinkedHashMap<>();
        for (Tag raw : entries) {
            if (!(raw instanceof CompoundTag entry)
                    || !entry.contains("body", Tag.TAG_STRING)
                    || !entry.contains("turns", Tag.TAG_DOUBLE)) {
                throw new IllegalArgumentException("Invalid celestial ephemeris phase entry");
            }
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("body"));
            double turns = entry.getDouble("turns");
            if (id == null || phases.put(id, turns) != null) {
                throw new IllegalArgumentException("Invalid or duplicate celestial ephemeris body");
            }
        }
        return new CelestialEphemerisSavedData(new CelestialOrbitalPhases(phases));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("data_version", DATA_VERSION);
        ListTag entries = new ListTag();
        for (ResourceLocation id : CelestialOrbitalPhases.orderedIds()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("body", id.toString());
            entry.putDouble("turns", phases().turns(id));
            entries.add(entry);
        }
        tag.put("phases", entries);
        return tag;
    }

    /** One volatile holder prevents readers from observing keys and values from different refreshes. */
    private record SettingsCache(CelestialRuntimeSettings base, CelestialOrbitalPhases phases,
                                 CelestialRuntimeSettings settings) {
    }
}
