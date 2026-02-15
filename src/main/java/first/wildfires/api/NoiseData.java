package first.wildfires.api;

public enum NoiseData {
    OCEAN(-26, -12),//海洋
    OCEAN_REEF(-16, -8),//海洋珊瑚礁
    DEEP_OCEAN(-30, -16),//深海
    DEEP_OCEAN_TRENCH(-30, -16),//深海海沟
    HIGHLANDS(60, 91),//高地
    MOUNTAINS(10, 70);//山脉

    private int heightMin;
    private int heightMax;

    NoiseData(int depthMin, int depthMax) {
        this.heightMin = depthMin;
        this.heightMax = depthMax;
    }

    public int getHeightMax() {
        return heightMax;
    }

    public void setHeightMax(int heightMax) {
        this.heightMax = heightMax;
    }

    public int getHeightMin() {
        return heightMin;
    }

    public void setHeightMin(int heightMin) {
        this.heightMin = heightMin;
    }

    public void setHeight(int heightMin, int heightMax) {
        setHeightMin(heightMin);
        setHeightMax(heightMax);
    }

}
