package first.wildfires.thermal;

/** Runtime state exposed by a heat-producing block entity. */
public interface ThermalSourceStateProvider {

    boolean isThermalSourceActive();

    default float getThermalSurfaceTemperature(float configuredTemperature) {
        return configuredTemperature;
    }
}
