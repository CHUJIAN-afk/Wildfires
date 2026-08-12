# NTM: Space provenance

## Identity

- Canonical source: https://github.com/JameH2/Hbm-s-Nuclear-Tech-GIT
- Project pages: https://modrinth.com/mod/ntmspace and https://www.curseforge.com/minecraft/mc-mods/ntm-space
- Authors/copyright holders: NTM: Space contributors. The complete contributor/asset-credit list is preserved verbatim in `upstream/gradle.properties`.
- Version: `1.0.27_X5778`
- Minecraft/loader: Minecraft 1.7.10 / Forge
- Retrieved from the user-provided local source snapshot on: 2026-08-12
- Release/file ID: local build number `5778`; hosted release ID not available in the snapshot
- Git revision: unavailable; the supplied tree contains no `.git` metadata
- Deterministic tree-manifest SHA-256: `c7d4f3625f90dae5598874a37830a2f2d77f36da2d52ce51f8ffd11792d302a2` (12324 files; same manifest algorithm documented in the Genesis record)

## Use in Wildfires

- Classification: `adapted` rendering/transfer code and `verbatim` PNG resources.
- Adapted files: `OrbitVisualRules.java` ports station LEAVING/TRANSFER/ARRIVING visual semantics, NTM's cubic circular easing, shortest-turn `clerp`, route-angle formula, two-real-hour local satellite orbit, apparent-size calculation, Sun scale, `0.01..0.5` point/body LOD, and the common `sunBrightness=1-eclipseAmount` / star-visibility contract; its source flat-body phase approximation is replaced by exact rotated-cube silhouette coverage, while the project requirement keeps player `+Y` exactly perpendicular to the ecliptic. `NtmOrbitSkyRenderer.java` ports the six-face night atlas with its per-face transformed vertex/UV contract, eclipse/distance-dependent alpha, colored point pass, and photosphere/corona order. The old fixed-function untextured black star-mask is intentionally folded into a single standard-alpha photosphere pass because separate coplanar squares produce black rims and striped flicker on the Forge 1.20.1 shader path. `OrbitClientIllumination.java` resolves the same station-local contract from synchronized observation context for the 1.20.1 lightmap.
- Verbatim resources: `night.png`, `kerbol.png`, `sunspike.png`, and `planet.png` from `assets/hbm/textures/misc/space/`.
- Wildfires modifications: replaces NTM's solar-system registry with `CelestialState/CelestialBodyState`; converts the geocentric Wildfires ephemeris to heliocentric positions before applying NTM's route-angle formula; keeps Wildfires real radii instead of NTM's 3000 km render cap; monotonically compresses astronomical draw depth while preserving angular size so overlap uses correct near/far ordering; replaces fixed-function OpenGL/Tessellator calls with `RenderSystem`, `VertexBuffer` and Forge shaders; maps NTM station phases to synchronized Wildfires journey phases and rotates the complete sky presentation as one frame.
- Partition: the three adapted Java files and four PNGs listed in `files.csv` are LGPL-3.0-only. Wildfires orchestration and ephemeris code outside that list retains its separately stated license.
- Corresponding source/relinking: the complete adapted Java source, resource files and build scripts are provided in this repository. The root ARR notice excludes this LGPL partition and does not restrict modification or reverse engineering of it. Recipients can replace/modify the listed sources and rebuild the combined JAR.
- JAR license paths: `third_party/ntm-space/1.0.27_X5778-local-snapshot/upstream/LICENSE` and `LICENSE.LESSER`.

## Rights and notices

- Governing license claim: README section `License` states GNU Lesser General Public License version 3; full GPLv3 and LGPLv3 texts are supplied as `LICENSE` and `LICENSE.LESSER`.
- Asset-level exceptions: no per-file exception or author marker accompanies the four selected PNGs. The project-level LGPL claim is therefore retained without narrowing it, and the entire upstream contributor/texture/model credit list travels with the distribution.
- Required conditions: preserve GPL/LGPL texts and notices; make modified LGPL source available; allow modification/replacement and reverse engineering for debugging those modifications; do not relicense the listed files as Wildfires ARR.
- Upstream `NOTICE`, `COPYING`, `CREDITS` and `AUTHORS`: not included as separate files. Credits are in `gradle.properties`.
- Release status: cleared under the recorded LGPL-3.0-only partition and source-availability model; asset authorship is not claimed by Wildfires. This is an evidence record, not legal advice.

## Preserved upstream material

- `upstream/README.md`: SHA-256 `90b2d54fed933d0fda7d39f3ebd9bbb1c750c5376e11662d3eae7490127a2cd6`
- `upstream/LICENSE`: SHA-256 `605e9047a563c5c8396ffb18232aa4304ec56586aee537c45064c6fb425e44ad`
- `upstream/LICENSE.LESSER`: SHA-256 `e3a994d82e644b03a792a930f574002658412f62407f5fee083f2555c5f23118`
- `upstream/CONTRIBUTING.md`: SHA-256 `5be2eb6675b103d86d360610a5c83bfc9f9cc67d81a398e9a2de7319dc20929c`
- `upstream/gradle.properties`: SHA-256 `d2a7931bc3ec7d57265b48030911fad0830667e1fc3b28e65469b496d9bc3eb6`

## Verification

- [x] `files.csv` covers every adapted or verbatim destination.
- [x] Exact-copy resource hashes match the supplied NTM snapshot.
- [x] Root acknowledgement and `THIRD_PARTY_NOTICES.md` entry are required in this change.
- [x] The combined distribution retains complete corresponding source for the LGPL partition.
