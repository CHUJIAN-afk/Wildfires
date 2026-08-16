package first.wildfires.client.celestial;

import com.mojang.logging.LogUtils;
import first.wildfires.celestial.CelestialConfig;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

/** Resolves the single owner of the overworld sky without changing celestial simulation. */
public final class CelestialSkyOwnership {

    public enum Ownership {
        WILDFIRES,
        SHADER_NATIVE,
        PHOTON_BRIDGE
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BooleanSupplier SHADER_PACK_ACTIVE = IrisShaderPackProbe.discover();
    private static final AtomicBoolean QUERY_FAILURE_LOGGED = new AtomicBoolean();
    private static volatile boolean hasLogged;
    private static volatile CelestialConfig.SkyMode lastMode;
    private static volatile boolean lastShaderPackActive;
    private static volatile boolean lastPhotonCompatible;
    private static volatile boolean lastPhotonBridgeAvailable;
    private static volatile Ownership lastOwnership;

    private CelestialSkyOwnership() {
    }

    public static Ownership current() {
        CelestialConfig.SkyMode mode = CelestialConfig.skyMode();
        boolean shaderPackActive = shaderPackActive(SHADER_PACK_ACTIVE, true);
        boolean photonCompatible = shaderPackActive && PhotonSkyBridge.recognizesActivePack();
        boolean photonBridgeAvailable = photonCompatible && PhotonSkyBridge.isImplemented();
        Ownership ownership = resolve(mode, shaderPackActive, photonCompatible, photonBridgeAvailable);
        logTransition(mode, shaderPackActive, photonCompatible, photonBridgeAvailable, ownership);
        return ownership;
    }

    public static boolean usesWildfiresOverworldVisuals() {
        return current() == Ownership.WILDFIRES;
    }

    static Ownership resolve(CelestialConfig.SkyMode mode, boolean shaderPackActive,
                             boolean photonCompatible, boolean photonBridgeAvailable) {
        Objects.requireNonNull(mode, "mode");
        return switch (mode) {
            case WILDFIRES -> Ownership.WILDFIRES;
            case SHADER_NATIVE -> Ownership.SHADER_NATIVE;
            case AUTO -> {
                if (!shaderPackActive) {
                    yield Ownership.WILDFIRES;
                }
                if (photonCompatible && photonBridgeAvailable) {
                    yield Ownership.PHOTON_BRIDGE;
                }
                yield Ownership.SHADER_NATIVE;
            }
        };
    }

    static Ownership resolve(CelestialConfig.SkyMode mode, BooleanSupplier shaderPackProbe,
                             boolean photonCompatible, boolean photonBridgeAvailable) {
        return resolve(mode, shaderPackActive(shaderPackProbe, false), photonCompatible, photonBridgeAvailable);
    }

    private static boolean shaderPackActive(BooleanSupplier probe, boolean logFailure) {
        try {
            return Objects.requireNonNull(probe, "probe").getAsBoolean();
        } catch (RuntimeException | LinkageError failure) {
            if (logFailure && QUERY_FAILURE_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn("Unable to query Iris/Oculus shader-pack activity; treating shaders as inactive", failure);
            }
            return false;
        }
    }

    private static void logTransition(CelestialConfig.SkyMode mode, boolean shaderPackActive,
                                      boolean photonCompatible, boolean photonBridgeAvailable,
                                      Ownership ownership) {
        if (hasLogged && mode == lastMode && shaderPackActive == lastShaderPackActive
                && photonCompatible == lastPhotonCompatible
                && photonBridgeAvailable == lastPhotonBridgeAvailable && ownership == lastOwnership) {
            return;
        }
        lastMode = mode;
        lastShaderPackActive = shaderPackActive;
        lastPhotonCompatible = photonCompatible;
        lastPhotonBridgeAvailable = photonBridgeAvailable;
        lastOwnership = ownership;
        hasLogged = true;
        LOGGER.info("Wildfires overworld sky owner: {} (mode={}, shaderPackActive={}, photonCompatible={}, photonBridgeAvailable={})",
                ownership, mode, shaderPackActive, photonCompatible, photonBridgeAvailable);
    }
}
