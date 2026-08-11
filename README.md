# Wildfires development setup

Wildfires is a Minecraft 1.20.1 / Forge 47.4.10 project. Production classes target Java 17; development `JavaExec` tasks, including `runClient`, use Java 21.

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

Gradle automatically provisions Java 17/21 toolchains, checks out TerraFirmaEarth from the pinned commit `94d23b0312b5b98fc834b29b62ff6369ca3def9b`, builds its independent 1.1.3 named dev JAR with `reobfJar` disabled, and verifies its identity, source marker, SHA-256 and named mappings before Wildfires compiles or starts.

Generated TFE source and runtime artifacts stay in ignored directories:

```text
.wildfires-cache/
run/mods/Wildfire_TerraFirmaEarth-1.20.1-forge-1.1.3.jar
run/mods/.wildfires-tfe-dev.properties
```

After one successful setup, the verified JAR and source checkout are reused without contacting GitHub. Force a pinned-source rebuild with:

```powershell
.\gradlew.bat prepareTfeDevJar -PtfeRefresh=true
```

Inspect the local dependency without changing it:

```powershell
.\gradlew.bat tfeDevJarStatus
```

Build the mod with:

```powershell
.\gradlew.bat build
```

Do not substitute a production/reobfuscated TFE JAR. TFE contains `@Overwrite` methods that ForgeGradle cannot safely map back for this userdev environment; the bootstrap intentionally produces and verifies the named dev artifact from the pinned source.

## Sharing changes

`run/`, `.wildfires-cache/`, IDE files and local engineering notes are intentionally not versioned. Commit and push all intended source/resource changes before asking another developer to pull; use `git status --short` to check that no required files remain untracked.

Every push to `master` and every pull request also runs `.github/workflows/clean-build.yml` on a clean Windows checkout. The gate exercises automatic TFE provisioning, all configured self-tests and the production build, so a change that only works with an author's hidden local files cannot pass unnoticed.

## Acknowledgements and third-party material

Wildfires' sky and celestial work builds on ideas and material from the following projects and creators:

- [Caelum](https://github.com/z0phka/Caelum) by Nuparu00 / z0phka, licensed under MIT.
- [TFC Caelum](https://modrinth.com/mod/tfc-caelum) by Verph, for its TFC calendar bridge, celestial events, visual design and bundled sky assets.
- [BSC5P-JSON-XYZ](https://github.com/frostoven/BSC5P-JSON-XYZ) by aggregate1166877 / Frostoven, whose generated catalog data is licensed under CC BY 4.0 and is the upstream source of the stellar catalog used through Caelum.
- [Aurora shader](https://www.shadertoy.com/view/MsjfRG) by Mattenii, licensed under CC BY-NC-SA 3.0 and adapted through TFC Caelum.

Thank you to these authors and to the authors of every project listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). That file is the authoritative attribution and license-status index; preserved upstream README, license and metadata snapshots live under [`third_party/`](third_party/README.md).

Wildfires' original code, original assets, and original documentation are [All Rights Reserved](LICENSE). The distributed mod is not a single-license artifact: bundled third-party material retains the separate terms documented in `THIRD_PARTY_NOTICES.md`. Public release remains blocked while any item there is marked unresolved.
