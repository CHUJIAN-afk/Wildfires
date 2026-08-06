package first.wildfires.thermal;

import java.util.BitSet;

/** Mutable 16x16x16 air-temperature storage owned by one server-level manager. */
final class ThermalSection {

    static final int SIZE = 16;
    static final int VOLUME = SIZE * SIZE * SIZE;

    final long sectionKey;
    float[] current = new float[VOLUME];
    private float[] hiddenCurrent;
    private int hiddenCount;
    final BitSet active = new BitSet(VOLUME);
    final BitSet airCacheKnown = new BitSet(VOLUME);
    final BitSet airCells = new BitSet(VOLUME);
    final BitSet boundaryCacheKnown = new BitSet(VOLUME);
    long lastSimulationTick;
    long lastNearTick = Long.MIN_VALUE;
    int stableCycles;
    boolean forceDue = true;
    boolean hasSource;
    boolean nearPlayer;

    ThermalSection(long sectionKey, long currentTick) {
        this.sectionKey = sectionKey;
        this.lastSimulationTick = currentTick;
    }

    float get(int index) {
        return current[index];
    }

    float getHiddenTemperature(int index) {
        return hiddenCurrent == null ? 0.0F : hiddenCurrent[index];
    }

    float getSolverTemperature(int index) {
        float visible = current[index];
        return visible != 0.0F ? visible : getHiddenTemperature(index);
    }

    void setHiddenTemperature(int index, float temperature) {
        current[index] = 0.0F;
        if (Math.abs(temperature) < 0.0001F) {
            clearHiddenTemperature(index);
            return;
        }
        if (hiddenCurrent == null) {
            hiddenCurrent = new float[VOLUME];
        }
        if (hiddenCurrent[index] == 0.0F) {
            hiddenCount++;
        }
        hiddenCurrent[index] = temperature;
    }

    void clearHiddenTemperature(int index) {
        if (hiddenCurrent == null || hiddenCurrent[index] == 0.0F) {
            return;
        }
        hiddenCurrent[index] = 0.0F;
        hiddenCount--;
        if (hiddenCount == 0) {
            hiddenCurrent = null;
        }
    }

    void clearHiddenTemperatures() {
        hiddenCurrent = null;
        hiddenCount = 0;
    }

    boolean hasHiddenTemperatures() {
        return hiddenCount > 0;
    }

    float[] hiddenSnapshot() {
        return hiddenCurrent == null ? new float[VOLUME] : hiddenCurrent.clone();
    }

    void replaceSimulationState(float[] visible, float[] hidden, int newHiddenCount,
                                BitSet nextActive, int nextStableCycles, boolean nextForceDue) {
        if (visible.length != VOLUME || hidden != null && hidden.length != VOLUME) {
            throw new IllegalArgumentException("Invalid thermal Section buffer length");
        }
        current = visible;
        hiddenCurrent = newHiddenCount == 0 ? null : hidden;
        hiddenCount = newHiddenCount;
        replaceActive(nextActive);
        stableCycles = nextStableCycles;
        forceDue = nextForceDue;
    }

    void invalidateAirCache(int index) {
        airCacheKnown.clear(index);
        airCells.clear(index);
    }

    void clearAirCache() {
        airCacheKnown.clear();
        airCells.clear();
    }

    void wake(int index) {
        active.set(index);
        stableCycles = 0;
        forceDue = true;
    }

    void replaceActive(BitSet nextActive) {
        active.clear();
        active.or(nextActive);
    }

    boolean hasSavedTemperature(float threshold) {
        for (float temperature : current) {
            if (Math.abs(temperature) >= threshold) {
                return true;
            }
        }
        return false;
    }
}
