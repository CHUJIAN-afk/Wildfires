package first.wildfires.thermal;

import java.util.Arrays;
import java.util.BitSet;

/** Mutable 16x16x16 air-temperature storage owned by one server-level manager. */
final class ThermalSection {

    static final int SIZE = 16;
    static final int VOLUME = SIZE * SIZE * SIZE;

    final long sectionKey;
    float[] current = new float[VOLUME];
    float[] pending = new float[VOLUME];
    private float[] hiddenCurrent;
    private float[] hiddenPending;
    private int hiddenCount;
    private int pendingHiddenCount;
    final BitSet active = new BitSet(VOLUME);
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

    void set(int index, float temperature) {
        current[index] = temperature;
        clearHiddenTemperature(index);
        active.set(index);
        forceDue = true;
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

    void beginHiddenUpdate() {
        if (hiddenCurrent == null) {
            if (hiddenPending != null) {
                Arrays.fill(hiddenPending, 0.0F);
            }
            pendingHiddenCount = 0;
        } else {
            if (hiddenPending == null) {
                hiddenPending = new float[VOLUME];
            }
            System.arraycopy(hiddenCurrent, 0, hiddenPending, 0, VOLUME);
            pendingHiddenCount = hiddenCount;
        }
    }

    void setPendingHiddenTemperature(int index, float temperature) {
        if (hiddenPending == null) {
            if (Math.abs(temperature) < 0.0001F) {
                return;
            }
            hiddenPending = new float[VOLUME];
        }
        float previous = hiddenPending[index];
        float stored = Math.abs(temperature) < 0.0001F ? 0.0F : temperature;
        if (previous == 0.0F && stored != 0.0F) {
            pendingHiddenCount++;
        } else if (previous != 0.0F && stored == 0.0F) {
            pendingHiddenCount--;
        }
        hiddenPending[index] = stored;
    }

    void commitHiddenUpdate() {
        float[] oldCurrent = hiddenCurrent;
        hiddenCurrent = pendingHiddenCount == 0 ? null : hiddenPending;
        hiddenCount = pendingHiddenCount;
        hiddenPending = oldCurrent;
        pendingHiddenCount = 0;
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
        hiddenPending = null;
        hiddenCount = 0;
        pendingHiddenCount = 0;
    }

    boolean hasHiddenTemperatures() {
        return hiddenCount > 0;
    }

    void wake(int index) {
        active.set(index);
        stableCycles = 0;
        forceDue = true;
    }

    void activate(int index) {
        active.set(index);
        stableCycles = 0;
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
