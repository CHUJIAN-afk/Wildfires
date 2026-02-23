package first.wildfires.api;

public enum NoiseData {
    OCEAN(-30, -16),//海洋
    OCEAN_REEF(-20, -10),//海洋珊瑚礁
    DEEP_OCEAN(-60, -46),//深海
    DEEP_OCEAN_TRENCH(-100, -70),//深海海沟
    MOUNTAINS(30, 120);//山脉

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
