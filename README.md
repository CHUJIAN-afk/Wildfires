# Wildfires development setup

Wildfires is a Minecraft 1.20.1 / Forge 47.4.10 project. Production classes target Java 17; development `JavaExec` tasks, including `runClient`, use Java 21.

ForgeGradle's development client is explicitly limited to a 1 GiB initial and 8 GiB maximum heap. This keeps the large local development modpack within a predictable Windows commit budget; it does not impose a memory requirement or launcher setting on the distributed mod.

The plain `*SelfTest` verification JVMs are independently limited to a 256 MiB initial and 2 GiB maximum heap, so a delayed or parallel verification process cannot reserve a machine-sized heap alongside the client.

## First run on a new machine

Requirements:

- Git available on `PATH`.
- A Java 17 or newer runtime capable of starting the Gradle wrapper.
- Network access to GitHub, Gradle/Forge Maven repositories and CurseMaven for the first build.

`D:\jdk\21` is used when present, but it is not required. Local launchers also accept `WILDFIRES_JAVA_HOME`; Gradle's toolchain resolver supplies the Java 17 compiler and Java 21 game launcher independently of that machine-specific path.

Run:

```powershell
.\gradlew.bat runClient
```

TerraFirmaEarth 2.0.1 does not currently publish matching source or a named development JAR. Place the authorized release JAR at the exact ignored path below. Gradle verifies its version, binary contract and SHA-256 before generating a local-only ForgeGradle input and compiling or starting Wildfires.

The release and generated userdev input stay in ignored directories:

```text
.wildfires-cache/
.wildfires-cache/tfe-release/Wildfire_TerraFirmaEarth-1.20.1-forge-2.0.1.jar
.wildfires-cache/tfe-userdev-input/Wildfire_TerraFirmaEarth-1.20.1-forge-2.0.1-wildfires-userdev-1.jar
```

The required release SHA-256 is `029AD1A7368B687BCD435B4321257BA8DD9E9D3B8533FC248BD43CEB5CE5A308`. Generate or refresh the local userdev input with:

```powershell
.\gradlew.bat prepareTfeUserdevInput
```

Inspect the local dependency without changing it:

```powershell
.\gradlew.bat tfeDevJarStatus
```

Build the mod with:

```powershell
.\gradlew.bat build
```

Do not rename another TFE build to match this path. TFE 2.0.1's release bytecode has one ForgeGradle method-name collision in `NTELeopardSeal`; the bootstrap makes a deterministic local input copy that renames only the private implementation helper before deobfuscation. It never edits or redistributes the authorized release JAR, and neither TFE JAR is included in the Wildfires artifact.

## Sharing changes

`run/`, `.wildfires-cache/`, IDE files and local engineering notes are intentionally not versioned. Commit and push all intended source/resource changes before asking another developer to pull; use `git status --short` to check that no required files remain untracked.

Every push to `master` and every pull request also runs `.github/workflows/clean-build.yml` on Windows. Because TFE 2.0.1 cannot be fetched from a matching public source release, that job requires its private dependency cache to contain the same fixed-hash release; it never substitutes the old public 1.1.3 source. CI enforces the TFE identity/binary contract and complete build; the real Forge GameTest server remains a release gate until the separate `space_station_travel` registration regression in the current working tree is repaired.

## Acknowledgements and third-party material

Wildfires' sky and celestial work builds on ideas and material from the following projects and creators:

- [Caelum](https://github.com/z0phka/Caelum) by Nuparu00 / z0phka, licensed under MIT.
- [TFC Caelum](https://modrinth.com/mod/tfc-caelum) by Verph, for its TFC calendar bridge, celestial events, visual design and bundled sky assets.
- [BSC5P-JSON-XYZ](https://github.com/frostoven/BSC5P-JSON-XYZ) by aggregate1166877 / Frostoven, whose generated catalog data is licensed under CC BY 4.0 and is the upstream source of the stellar catalog used through Caelum.
- [Aurora shader](https://www.shadertoy.com/view/MsjfRG) by Mattenii, licensed under CC BY-NC-SA 3.0 and adapted through TFC Caelum.
- [VS: Genesis](https://github.com/jamesgreen26/genesis) by jamesgreen26 and contributors, licensed under Apache-2.0, for the square-planet cubemap, mesh, center-star lighting, atmosphere implementation, bound-body ascent presentation, authoritative-transfer/client-transition separation and verbatim Earth/Moon cubemap textures adapted without VS2/Lodestone.
- [NTM: Space](https://github.com/JameH2/Hbm-s-Nuclear-Tech-GIT) by the NTM: Space contributors, licensed under LGPL-3.0, for the orbit-transfer presentation, Y=200 underfoot-body reveal, Y=300～800 high-atmosphere fade, reusable return-capsule and docking-core models, bottom-port deployment, door/airbrake/leg/thruster animation, data-bound surface round trip, station-ID navigation-drive mechanism, station-local eclipse/sunlight/star-visibility contract, six-face night sky, flat Sun/corona, distant point LOD and redistributed textures.

Thank you to these authors and to the authors of every project listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). That file is the authoritative attribution and license-status index; preserved upstream README, license and metadata snapshots live under [`third_party/`](third_party/README.md).

Wildfires' original code, original assets, and original documentation are [All Rights Reserved](LICENSE). The distributed mod is not a single-license artifact: bundled third-party material retains the separate terms documented in `THIRD_PARTY_NOTICES.md`. Public release remains blocked while any item there is marked unresolved.

| Partition | Paths | Copyright holders | License/source |
| --- | --- | --- | --- |
| Wildfires original work | Files not separately listed below | FirstSight and Wildfires contributors | [ARR](LICENSE) |
| VS: Genesis adaptations and two planet textures | `first/wildfires/thirdparty/genesisadapt/`, `NtmAscentPlanetRenderer.java`, `ReturnCapsuleClientTransition.java`, `ReturnCapsuleReceivingScreen.java`, `ReturnCapsuleMinecraftMixin.java`, `genesis_planet_*` shaders, `textures/third_party/vs_genesis/` | VS: Genesis contributors | [Apache-2.0 evidence](third_party/vs-genesis/1.20.1-0.7.3-local-snapshot/PROVENANCE.md) |
| NTM: Space adaptations/resources | orbit/ascent visual and illumination classes; reusable-capsule entity/item/state/service/visuals/renderer; station-ID tape; NTM OBJ loader/core renderer; `NtmAscentFogRendererMixin`; `models/third_party/ntm_space/`; `textures/third_party/ntm_space/`; `textures/particle/third_party/ntm_space/` | NTM: Space contributors | [LGPL-3.0 evidence/source mapping](third_party/ntm-space/1.0.27_X5778-local-snapshot/PROVENANCE.md) |
