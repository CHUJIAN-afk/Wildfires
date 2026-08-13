# Third-party notices

This file records third-party work incorporated into, adapted by, or materially consulted by Wildfires. Acknowledgement does not imply endorsement by the original authors. Upstream copyrights remain with their respective owners.

## License model

Wildfires is a multi-license distribution. The root [`LICENSE`](LICENSE) reserves all rights in original Wildfires code, assets, and documentation. It does not relicense third-party material. Every bundled third-party item remains governed by the license and copyright notice recorded below and in [`third_party/`](third_party/README.md).

The built JAR carries `LICENSE`, this notice, and the complete versioned `third_party/` evidence directory. Presence of those files preserves notices but does not by itself resolve an incompatible or ambiguous license. Items explicitly marked unresolved remain release blockers.

| Material | Governing terms |
| --- | --- |
| Original Wildfires code, assets, and documentation | All Rights Reserved |
| Caelum-derived code/mechanisms | Caelum MIT notice, subject to deeper file-level sources |
| BSC5P-JSON-XYZ stellar catalog data | CC BY 4.0 |
| Mattenii-derived aurora shader | CC BY-NC-SA 3.0 |
| TFC Caelum-derived behavior and 31 PNG files | Unresolved BSD-2-Clause / `BSD 3` conflict; not cleared for public release |
| VS: Genesis-derived cubemap, mesh and shaders | Apache-2.0 |
| NTM: Space-derived orbit/capsule/navigation code, models and textures | LGPL-3.0-only |

## Caelum

- Project: [Caelum](https://github.com/z0phka/Caelum)
- Author: Nuparu00 / z0phka
- Referenced version: `1.20.1-2.0.0.0`
- Verified source revision: `59c03cc665cb91291b1b3d3a591304be702fbc05`
- Upstream license: MIT
- Use in Wildfires: sky and latitude design, stellar loading/rendering behavior, and related implementation guidance.
- Verbatim material: `src/main/resources/assets/wildfires/stars/stars.json`; its deeper catalog provenance and CC BY 4.0 terms are recorded below.
- Preserved upstream documents: [`third_party/caelum/1.20.1-2.0.0.0/`](third_party/caelum/1.20.1-2.0.0.0/PROVENANCE.md)

Copyright (c) 2023 Nuparu00. The complete MIT notice is preserved with the upstream snapshot.

## TFC Caelum

- Project: [TFC Caelum on Modrinth](https://modrinth.com/mod/tfc-caelum) / [CurseForge](https://www.curseforge.com/minecraft/mc-mods/tfc-caelum)
- Author: Verph
- Referenced release: `TFCCaelum-1.20.1-1.2.jar`, Modrinth version `wSasbQOH`
- Use in Wildfires: TFC calendar integration concepts, planets and satellites, eclipses, blood moons, rainbows, aurora behavior, configuration semantics, and sky resources.
- Verbatim material: 31 PNG files under `src/main/resources/assets/wildfires/textures/sky/`; see the preserved file manifest.
- Adapted material: portions of the aurora shader and celestial behavior. The shader has a separate CC BY-NC-SA 3.0 attribution below.
- Preserved upstream metadata and project description: [`third_party/tfc-caelum/1.2/`](third_party/tfc-caelum/1.2/PROVENANCE.md)

License status requires resolution before public redistribution. The release JAR declares `BSD 3`, while the matching Modrinth project/version currently declares `BSD-2-Clause`; the release contains neither a complete license text nor an original README. Wildfires preserves both authoritative statements without guessing which BSD variant controls. Obtain the original license text or written clarification from Verph and then update the provenance record.

## BSC5P-JSON-XYZ stellar catalog

- Project: [BSC5P-JSON-XYZ](https://github.com/frostoven/BSC5P-JSON-XYZ)
- Creator: aggregate1166877 / Frostoven
- Material: generated catalog data, indirectly obtained through Caelum and distributed as `src/main/resources/assets/wildfires/stars/stars.json`
- License for generated catalogs: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)
- Upstream acknowledgements: the catalog is based on BSC5P/HEASARC data and uses SIMBAD and VizieR data; their terms and acknowledgements remain applicable as described by the upstream project.
- Preserved license and provenance: [`third_party/bsc5p-json-xyz/`](third_party/bsc5p-json-xyz/PROVENANCE.md)

`BSC5P-JSON-XYZ Catalog Data (c) by aggregate1166877` is licensed under CC BY 4.0. Wildfires has renamed/packaged the Caelum table but has not altered its bytes; SHA-256 is `46f2b21d8205562d5918322fbf9217c51454d2fddf05c77223e9053584abc227`.

## Mattenii aurora shader

- Original work: [Aurora shader on Shadertoy](https://www.shadertoy.com/view/MsjfRG)
- Creator: Mattenii
- License: [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/)
- Path in Wildfires: `src/main/resources/assets/wildfires/shaders/core/aurora.fsh`
- Changes: adapted from the TFC Caelum split shader to Minecraft's Forge-managed GLSL 150 pipeline and the `wildfires` namespace; further rendering fixes are documented in the source history.

The attribution and license marker must remain in the shader source. Because this is a non-commercial, share-alike license and a file-level exception to the mod-level MIT declaration, release terms and distribution channels must be checked explicitly before publishing.

## VS: Genesis

- Project: [VS: Genesis](https://github.com/jamesgreen26/genesis)
- Referenced snapshot/version: `1.20.1-0.7.3`; supplied local tree had no Git metadata
- Copyright notice in upstream Apache license: Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236
- License: Apache License 2.0
- Use in Wildfires: adapted 3x2 square-planet face contract, static cube mesh, center-star directional surface shader, ray-box atmosphere shader and authoritative-transfer/client-transition separation; verbatim upstream Earth and Moon cubemaps.
- Wildfires partition: `src/main/java/first/wildfires/thirdparty/genesisadapt/` and `src/main/resources/assets/wildfires/shaders/core/genesis_planet_*`.
- Verbatim texture partition: `src/main/resources/assets/wildfires/textures/third_party/vs_genesis/overworld.png` and `moon.png`.
- Runtime dependencies intentionally not imported: Valkyrien Skies 2, Lodestone, Genesis and physical ship interfaces.
- Preserved source evidence and exact file mapping: [`third_party/vs-genesis/1.20.1-0.7.3-local-snapshot/`](third_party/vs-genesis/1.20.1-0.7.3-local-snapshot/PROVENANCE.md)

Each modified source/shader carries an Apache-2.0 notice and change description. The complete upstream Apache license is distributed with the evidence snapshot.

## NTM: Space

- Project: [NTM: Space](https://github.com/JameH2/Hbm-s-Nuclear-Tech-GIT)
- Referenced snapshot/version: `1.0.27_X5778`; supplied local tree had no Git metadata
- Copyright holders: NTM: Space contributors; the complete contributor and texture/model credit list is preserved in the upstream `gradle.properties` snapshot
- License: GNU Lesser General Public License version 3, stated in the upstream README and accompanied by GPLv3 `LICENSE` plus `LICENSE.LESSER`
- Adapted code/resources: orbit visuals and illumination; reusable-pod launch/landing/docking/passenger transfer and stable recovery; Forge OBJ renderers for the docking core and pod; and the station-ID tape's NTM navigation-drive contract, with persistent UUIDs replacing orbit-grid coordinates.
- Adapted models/bindings: upstream `docking_port.obj` and `rp_drop_pod.obj` with Forge material selectors, plus Forge MTL and model JSON bindings.
- Verbatim textures: `night.png`, `kerbol.png`, `sunspike.png`, `planet.png`, `docking_port.png`, `rp_drop_pod.png`, and the upstream `votv_f.png` bytes redistributed as `station_id_tape.png` under `src/main/resources/assets/wildfires/textures/third_party/ntm_space/`.
- Preserved source evidence, corresponding-source/relinking statement and exact hashes: [`third_party/ntm-space/1.0.27_X5778-local-snapshot/`](third_party/ntm-space/1.0.27_X5778-local-snapshot/PROVENANCE.md)

The root ARR license expressly excludes this LGPL partition. Its modified Java source, exact PNGs, build scripts, GPL/LGPL texts and attribution records are provided with the source distribution and evidence is included in the JAR. Wildfires does not claim authorship of the NTM textures; the inspected upstream snapshot did not provide per-PNG author or license exceptions, so the project-level LGPL claim and complete contributor credits are retained without narrowing them.

## Dependencies and APIs

Wildfires also depends on or integrates with Minecraft, Forge, TerraFirmaCraft, TerraFirmaEarth, Create and other projects. Dependency status alone does not mean their code or assets are redistributed under Wildfires' license. Any future copied or adapted material from those projects must be added here and to `third_party/` before it enters the repository.
