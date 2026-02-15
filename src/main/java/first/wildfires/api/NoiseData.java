package first.wildfires.api;

public enum NoiseData {
    OCEAN(-26, -12),
    OCEAN_REEF(-16, -8),
    DEEP_OCEAN(-30, -16),
    DEEP_OCEAN_TRENCH(-30, -16);

    private int depthMin;
    private int depthMax;

    NoiseData(int depthMin, int depthMax) {
        this.depthMin = depthMin;
        this.depthMax = depthMax;
    }

    public int getDepthMax() {
        return depthMax;
    }

    public void setDepthMax(int depthMax) {
        this.depthMax = depthMax;
    }

    public int getDepthMin() {
        return depthMin;
    }

    public void setDepthMin(int depthMin) {
        this.depthMin = depthMin;
    }
}
