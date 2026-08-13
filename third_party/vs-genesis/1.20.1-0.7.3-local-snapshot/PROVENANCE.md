# VS: Genesis provenance

## Identity

- Canonical source: https://github.com/jamesgreen26/genesis
- Project pages: https://modrinth.com/mod/vs-genesis and https://www.curseforge.com/minecraft/mc-mods/vs-genesis
- Authors/copyright holders: jamesgreen26, JSJBDEV, Verquinox, jcm236; the inspected `mods.toml` additionally lists G_Mungus, Quoissant, Cosmos, Frisk256, Acrogenous, BrickyBoy, Jcm, metafive, GuyApooye and ThePlasticPotato.
- Version: `1.20.1-0.7.3`
- Minecraft/loader: Minecraft 1.20.1 / Forge 47.4.0
- Retrieved from the user-provided local source snapshot on: 2026-08-12
- Release/file ID: not available in the local snapshot
- Git revision: unavailable; the supplied tree contains no `.git` metadata
- Deterministic tree-manifest SHA-256: `25bf844becc888ee8ccdbbd46aa78e886c1ba3f633c900e3b4c977fa772d67b8` (1121 files; relative paths sorted, each line is `<lowercase-file-sha256>  <forward-slash-path>`, UTF-8/LF with a final LF)

## Use in Wildfires

- Classification: `adapted` code and shaders plus two `verbatim` planet cubemap textures.
- Purpose: Genesis 3x2 square-planet face order, static cube geometry, center-star directional surface lighting, ray-box atmosphere rendering, the upstream Earth/Moon cubemaps used to preserve the intended surface art and seam contract, and its separation of authoritative level transfer from a bounded client transition screen.
- Partition root: `src/main/java/first/wildfires/thirdparty/genesisadapt/`, `src/main/java/first/wildfires/client/space/ReturnCapsuleTransitionOverlay.java` plus `src/main/resources/assets/wildfires/shaders/core/genesis_planet_*`.
- ARR dependency direction: the Wildfires orbit renderer calls this Apache-2.0 partition; the partition has no VS2, Lodestone, Genesis runtime or physical-ship dependency.
- Modifications: ported to Forge `RegisterShadersEvent` and static `VertexBuffer`; removed VS2/VantagePoint/Lodestone and the upstream ship-shadow dependency; made planet alpha compatible with NTM distance LOD and independent Wildfires orbital-cloud tint/alpha; removed forced `gl_FragDepth=1.0`; restored the exact upstream per-face winding/UV orientation and cube-corner lighting gradient; and adapted the cube atmosphere to Forge GLSL 150 with encoded local entry coordinates, ray-box/diagonal-plane limb evaluation, Genesis path-normalized entry/exit illumination, center-star lighting, and datapack-controlled day/sunset/night palettes, regional brightness, terminator transitions, limb response, opacity and exposure. Wildfires additionally extends both adapted shaders with a bounded four-caster, fixed 3x3 finite-square-star visibility pass: rotating satellite OBBs are intersected from the actual three-dimensional cube surface/shell position so geometric umbra and penumbra cross face edges and corners without UV seams; atmosphere eclipses attenuate direct scattering while retaining ambient/night scattering. Orbit-wide sky colour remains exclusively NTM black; the Genesis atmosphere is only a local cube shell. Cubic cloud-shell geometry remains rigidly aligned to the planet; future cloud motion changes material sampling rather than rotating a second cube through the planet. `ReturnCapsuleTransitionOverlay` independently ports the `TransitionScreen/TransitionFrame/TransitionState` separation into a state-driven black-vacuum fade; it retains no Genesis registry, ship, OBB, scale or VS2 type.
- Verbatim resources: `textures/planets/minecraft/overworld.png` and `textures/planets/genesis/moon.png`, redistributed byte-for-byte under the recorded Apache-2.0 project terms; their exact hashes and destinations are in `files.csv`.
- Full source is shipped in this repository. The JAR includes this record and `upstream/LICENSE`.

## Rights and notices

- Governing license: Apache License 2.0, stated by the root `LICENSE` and `mods.toml`.
- File/asset exceptions found: none in the inspected tree for the adapted source/shaders or the two recorded planet textures. No other Genesis art is copied.
- Required conditions: include Apache-2.0 text, retain applicable attribution, and mark modified files.
- Upstream `NOTICE`, `COPYING`, `CREDITS` and `AUTHORS`: not included in the inspected snapshot.
- Release status: cleared for the recorded adaptation subject to the preserved Apache-2.0 notices; this is an evidence record, not legal advice.

## Preserved upstream material

- `upstream/README.md`: SHA-256 `33bac9334d4000e54fd5a2e47d48fcec89e671d81250a5f06189ce6d216eded0`
- `upstream/LICENSE`: SHA-256 `6c8b55b771e0add46762085b36c6bce3ca2ca74c623daa3cde3561025d7e7a4d`
- `upstream/gradle.properties`: SHA-256 `524c61f83838d6ec1ee12c12d461077f4f566be02ddcdb8e4c1a8e576d2bcd21`
- `upstream/mods.toml`: SHA-256 `60c9d054da581e338dee75d6ce859695b28e09eaf1ffb2106d01b929fad6944b`

## Verification

- [x] `files.csv` covers every adapted destination.
- [x] Root acknowledgement and `THIRD_PARTY_NOTICES.md` entry are required in this change.
- [x] Upstream evidence bytes and hashes were checked against the supplied snapshot.
- [x] No VS2/Lodestone runtime content is redistributed through this partition.
