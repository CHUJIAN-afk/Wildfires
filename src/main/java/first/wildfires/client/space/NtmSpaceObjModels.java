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
    private static CompositeRenderable capsule;
    private static CompositeRenderable stationCore;

    private NtmSpaceObjModels() {
    }

    static synchronized CompositeRenderable capsule() {
        if (capsule == null) {
            capsule = ObjLoader.INSTANCE.loadModel(CAPSULE_SETTINGS).bakeRenderable(
                    StandaloneGeometryBakingContext.create(
                            Wildfires.rl("ntm_reusable_return_capsule")));
        }
        return capsule;
    }

    static synchronized CompositeRenderable stationCore() {
        if (stationCore == null) {
            stationCore = ObjLoader.INSTANCE.loadModel(CORE_SETTINGS).bakeRenderable(
                    StandaloneGeometryBakingContext.create(Wildfires.rl("ntm_station_core")));
        }
        return stationCore;
    }

    /** OBJ and MTL data belong to the active resource-manager generation. */
    static synchronized void reset() {
        capsule = null;
        stationCore = null;
    }
}
