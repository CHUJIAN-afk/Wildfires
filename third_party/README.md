# Third-party material management

This directory is the durable source-of-truth archive for external material used by Wildfires. `THIRD_PARTY_NOTICES.md` is the concise distribution-facing index; this directory stores the evidence behind it.

Wildfires' original content is All Rights Reserved. That status does not apply to anything registered here. The production JAR includes this complete directory together with the root `LICENSE` and `THIRD_PARTY_NOTICES.md`, so the applicable third-party terms travel with the files they govern.

## Required workflow

Before copying or adapting code, data, textures, models, sounds, shaders, documentation or configuration from another project:

1. Classify the use as `dependency-only`, `inspiration`, `adapted`, or `verbatim`.
2. Create `third_party/<project>/<version-or-commit>/PROVENANCE.md` from `TEMPLATE.md`.
3. Put the upstream `README`, `LICENSE`, `COPYING`, `NOTICE`, `CREDITS` and `AUTHORS` files in that version's `upstream/` directory, unchanged. Preserve their original filenames. If a release does not contain one, record `not included in the inspected release`; do not invent an upstream README or license.
4. Record the canonical URL, author, mod/game version, release ID or commit, retrieval date and SHA-256 values. Project pages saved as Markdown must be named `PROJECT_DESCRIPTION.md`, not `README.md`.
5. For every `adapted` or `verbatim` item, record source and destination paths in `files.csv`. Keep copyright/license headers in the destination file. Directory globs may supplement but never replace the manifest for exact copies.
6. Check asset-level and file-level licenses separately from the mod's general code license. Data sets, shaders, fonts, music and commissioned art commonly have different terms.
7. Update the root `README.md` acknowledgement when the project materially influences Wildfires, and always update `THIRD_PARTY_NOTICES.md` when material is distributed or adapted.
8. Resolve unclear, missing, non-commercial, no-derivatives, copyleft or mutually conflicting terms before release. A credit line is not a substitute for permission.

Snapshots under `upstream/` are immutable evidence. When upstream changes, add a new version/commit directory rather than editing an old snapshot.

## Current register

| Project/material | Use | Evidence | Status |
| --- | --- | --- | --- |
| Caelum `1.20.1-2.0.0.0` | adapted + verbatim star table | README, MIT license, revision and hashes | Recorded |
| TFC Caelum `1.2` | adapted + 31 verbatim PNGs | JAR metadata, project description, release hashes, file manifest | License conflict must be resolved before release |
| BSC5P-JSON-XYZ catalog | verbatim data through Caelum | upstream license, catalog hash | CC BY 4.0 attribution recorded |
| Mattenii aurora shader | adapted shader | source URL and in-file notice | CC BY-NC-SA 3.0 release compatibility must be reviewed |
| VS: Genesis `1.20.1-0.7.3` | adapted cubemap/mesh/shaders/ascent body + 2 verbatim planet PNGs | local source-tree digest, README, Apache-2.0 license, metadata and file manifest | Recorded |
| NTM: Space `1.0.27_X5778` | adapted orbit/capsule/navigation/ascent code and models + 8 verbatim PNGs | local source-tree digest, README, GPL/LGPL texts, contributor credits, corresponding-source statement and file hashes | LGPL-3.0-only partition recorded |
| Waterfall `0.10.3` / commit `6be4f897` | adapted radiant-drive and Daedalus v1/v2 shaders/configs + derived meshes and textures | README, CC BY-NC-SA 4.0 statement, source/config snapshots and file manifest | Non-commercial share-alike partition recorded |

This register is about incorporated or adapted material. Ordinary dependencies may be documented in build files without snapshotting their entire source repositories.
