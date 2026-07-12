package first.wildfires.api.tfc;

public class TemperatureFilter {
    final String operator;
    final float value;

    public TemperatureFilter(String operator, float value) {
        this.operator = operator;
        this.value = value;
    }

    public boolean matches(float temp) {
        return switch (operator) {
            case ">=" -> temp >= value;
            case ">" -> temp > value;
            case "<=" -> temp <= value;
            case "<" -> temp < value;
            case "==", "=" -> Math.abs(temp - value) < 0.001f;
            default -> false;
        };
    }
}
