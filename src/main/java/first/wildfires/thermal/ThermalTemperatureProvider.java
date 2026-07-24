package first.wildfires.thermal;

/**
 * Implement on a heat-producing block entity to expose its current temperature.
 */
public interface ThermalTemperatureProvider {

    float getThermalTemperature();
}
