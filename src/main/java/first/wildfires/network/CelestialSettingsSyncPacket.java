package first.wildfires.network;

import first.wildfires.celestial.CelestialBodyParameters;
import first.wildfires.celestial.CelestialPlanetSettings;
import first.wildfires.celestial.CelestialRuntimeSettings;
import first.wildfires.celestial.CelestialSettingsCache;
import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.register.NetworkPacketRegister;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Synchronizes celestial gameplay parameters from the authoritative server configuration. */
public record CelestialSettingsSyncPacket(CelestialRuntimeSettings settings) implements ICustomPacketPayload {

    public CelestialSettingsSyncPacket(FriendlyByteBuf buffer) {
        this(decodeSettings(buffer));
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeDouble(settings.synodicDays());
        buffer.writeDouble(settings.anomalisticDays());
        buffer.writeDouble(settings.nodalYears());
        buffer.writeDouble(settings.lunarInclinationRadians());
        buffer.writeBoolean(settings.bloodMoonSurfaceMonsters());
        buffer.writeDouble(settings.bloodMoonSpawnMultiplier());
        buffer.writeDouble(settings.sunScale());
        buffer.writeDouble(settings.moonScale());
        buffer.writeEnum(settings.lunarPeriodPreset());
        CelestialPlanetSettings planetSettings = settings.planetSettings();
        buffer.writeDouble(planetSettings.earthDiameterKm());
        buffer.writeDouble(planetSettings.earthOrbitalDays());
        buffer.writeDouble(planetSettings.earthSemiMajorMillionKm());
        List<CelestialBodyParameters> planets = planetSettings.configurableBodies();
        buffer.writeVarInt(planets.size());
        for (CelestialBodyParameters planet : planets) {
            buffer.writeDouble(planet.diameterKm());
            buffer.writeDouble(planet.orbitalDays());
            buffer.writeDouble(planet.semiMajorMillionKm());
            buffer.writeDouble(planet.synodicDays());
            buffer.writeDouble(planet.inclinationRadians());
        }
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> CelestialSettingsCache.accept(settings));
    }

    public void sendTo(ServerPlayer player) {
        NetworkPacketRegister.Instance.send(PacketDistributor.PLAYER.with(() -> player), this);
    }

    private static CelestialRuntimeSettings decodeSettings(FriendlyByteBuf buffer) {
        double synodicDays = buffer.readDouble();
        double anomalisticDays = buffer.readDouble();
        double nodalYears = buffer.readDouble();
        double lunarInclination = buffer.readDouble();
        boolean bloodMoonSurfaceMonsters = buffer.readBoolean();
        double bloodMoonSpawnMultiplier = buffer.readDouble();
        double sunScale = buffer.readDouble();
        double moonScale = buffer.readDouble();
        CelestialRuntimeSettings.LunarPeriodPreset preset =
                buffer.readEnum(CelestialRuntimeSettings.LunarPeriodPreset.class);
        double earthDiameterKm = buffer.readDouble();
        double earthOrbitalDays = buffer.readDouble();
        double earthSemiMajorMillionKm = buffer.readDouble();
        int count = buffer.readVarInt();
        if (count != CelestialPlanetSettings.CONFIGURABLE_BODY_COUNT) {
            throw new IllegalArgumentException("Expected " + CelestialPlanetSettings.CONFIGURABLE_BODY_COUNT
                    + " synchronized planet definitions, got " + count);
        }
        List<CelestialBodyParameters> planets = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            planets.add(new CelestialBodyParameters(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble()));
        }
        return new CelestialRuntimeSettings(synodicDays, anomalisticDays, nodalYears, lunarInclination,
                bloodMoonSurfaceMonsters, bloodMoonSpawnMultiplier, sunScale, moonScale, preset,
                new CelestialPlanetSettings(planets, earthDiameterKm, earthOrbitalDays,
                        earthSemiMajorMillionKm));
    }
}
