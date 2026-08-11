# TFC Caelum provenance

## Identity

- Canonical project pages: https://modrinth.com/mod/tfc-caelum and https://www.curseforge.com/minecraft/mc-mods/tfc-caelum
- Author: Verph
- Release: `TFCCaelum-1.20.1-1.2.jar`
- Modrinth project/version: `WYvV2Kci` / `wSasbQOH`
- Minecraft/loader: Minecraft 1.20.1 / Forge
- Published on Modrinth: 2024-08-28
- Verified on: 2026-08-11
- Published archive SHA-1: `114e10e497503f5ff4cb13850a8dec9bb9f4d625`
- Published archive SHA-512: `f1c7153fc07da59677183f9967ca16e189bcf38d961b03931f100b141e9a10ef684dd1d71a6cf8d27cd3d8ceef7670db25d3b6c398f835387ca8ee8936ea0303`

The inspected release is an extracted compiled JAR, not a source checkout. It contains no original README, LICENSE, COPYING, NOTICE, CREDITS or AUTHORS file and declares no source repository. `upstream/PROJECT_DESCRIPTION.md` preserves the Modrinth project body rather than pretending it was a release README. `upstream/mods.toml` is copied from the release JAR.

- `upstream/PROJECT_DESCRIPTION.md` SHA-256: `1e1eb2900f6b1f08e918627e1eaa42add6b031b0befff6ea178949f5d8bb4dfc`.
- `upstream/mods.toml` SHA-256: `23ca1f9589c3f2b1a8fcba128804c31f843e9d5cb3dab0d24655ce13f93aa167`; byte-identical to the inspected release file.

## Use in Wildfires

- Classification: adapted behavior plus 31 verbatim PNG resources.
- Adapted scope: TFC calendar integration, planets/satellites, eclipses, supermoon and blood moon behavior, rainbows, auroras, configuration semantics and rendering behavior.
- Verbatim scope: the PNG destinations listed in `files.csv`; all 31 were byte-compared with the extracted release on 2026-08-11.
- Shader exception: `src/main/resources/assets/wildfires/shaders/core/aurora.fsh` is adapted from the release's shader and retains the original Mattenii / Shadertoy attribution and CC BY-NC-SA 3.0 marker.

## Unresolved license conflict

- The release JAR's `META-INF/mods.toml` says `license = "BSD 3"`.
- The matching Modrinth project/version says `BSD-2-Clause`.
- Neither source supplies the complete license text, and no source repository is linked.

Do not silently normalize either declaration to a chosen SPDX identifier. Before public redistribution, obtain the license text or written clarification from Verph, preserve it under `upstream/`, and update `THIRD_PARTY_NOTICES.md`. Credit alone does not resolve the conflict.
