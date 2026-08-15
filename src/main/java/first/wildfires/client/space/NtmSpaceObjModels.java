/*
 * Loads NTM: Space geometry adapted only with Forge material bindings.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package first.wildfires.client.space;

import first.wildfires.Wildfires;
import net.minecraftforge.client.model.geometry.StandaloneGeometryBakingContext;
import net.minecraftforge.client.model.obj.ObjLoader;
import net.minecraftforge.client.model.obj.ObjModel;
import net.minecraftforge.client.model.renderable.CompositeRenderable;

/** Shared immutable render meshes for the NTM station core and reusable return capsule. */
final class NtmSpaceObjModels {

    private static final ObjModel.ModelSettings CAPSULE_SETTINGS = new ObjModel.ModelSettings(
            Wildfires.rl("models/third_party/ntm_space/rp_drop_pod.obj"), false, true, true,
            true, "wildfires:models/third_party/ntm_space/rp_drop_pod.mtl");
    private static final ObjModel.ModelSettings CORE_SETTINGS = new ObjModel.ModelSettings(
            Wildfires.rl("models/third_party/ntm_space/docking_port.obj"), false, true, true,
            true, "wildfires:models/third_party/ntm_space/docking_port.mtl");
    private static volatile CompositeRenderable capsule;
    private static volatile CompositeRenderable stationCore;

    private NtmSpaceObjModels() {
    }

    static CompositeRenderable capsule() {
        CompositeRenderable current = capsule;
        if (current == null) {
            synchronized (NtmSpaceObjModels.class) {
                current = capsule;
                if (current == null) {
                    current = ObjLoader.INSTANCE.loadModel(CAPSULE_SETTINGS).bakeRenderable(
                            StandaloneGeometryBakingContext.create(
                                    Wildfires.rl("ntm_reusable_return_capsule")));
                    capsule = current;
                }
            }
        }
        return current;
    }

    static CompositeRenderable stationCore() {
        CompositeRenderable current = stationCore;
        if (current == null) {
            synchronized (NtmSpaceObjModels.class) {
                current = stationCore;
                if (current == null) {
                    current = ObjLoader.INSTANCE.loadModel(CORE_SETTINGS).bakeRenderable(
                            StandaloneGeometryBakingContext.create(Wildfires.rl("ntm_station_core")));
                    stationCore = current;
                }
            }
        }
        return current;
    }

    /** OBJ and MTL data belong to the active resource-manager generation. */
    static synchronized void reset() {
        capsule = null;
        stationCore = null;
        NtmObjFastRenderer.clear();
    }
}
