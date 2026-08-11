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

## Dependencies and APIs

Wildfires also depends on or integrates with Minecraft, Forge, TerraFirmaCraft, TerraFirmaEarth, Create and other projects. Dependency status alone does not mean their code or assets are redistributed under Wildfires' license. Any future copied or adapted material from those projects must be added here and to `third_party/` before it enters the repository.
