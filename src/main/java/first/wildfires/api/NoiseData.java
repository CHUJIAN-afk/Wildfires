package first.wildfires.api;

public enum NoiseData {
    OCEAN(-26, -12),//海洋
    OCEAN_REEF(-16, -8),//海洋珊瑚礁
    DEEP_OCEAN(-30, -16),//深海
    DEEP_OCEAN_TRENCH(-30, -16),//深海海沟
    HIGHLANDS(60, 91),//高地
    MOUNTAINS(10, 70);//山脉

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

    public void setDepth(int depthMax, int depthMin) {
        setDepthMax(depthMax);
        setDepthMin(depthMin);
    }

}
