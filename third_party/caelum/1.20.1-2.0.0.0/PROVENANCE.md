# Caelum provenance

## Identity

- Canonical source: https://github.com/z0phka/Caelum
- Project page: https://www.curseforge.com/minecraft/mc-mods/caelum
- Author/copyright holder: Nuparu00 / z0phka
- Version: `1.20.1-2.0.0.0`
- Verified revision: `59c03cc665cb91291b1b3d3a591304be702fbc05`
- Minecraft/loader: Minecraft 1.20.1 / Forge
- Verified on: 2026-08-11

The preserved upstream README and LICENSE are byte-identical to the files at the verified revision.

## Use in Wildfires

- Classification: adapted mechanisms plus one verbatim data file.
- Mechanisms: client sky, latitude mapping, star table loading/rendering and related celestial behavior.
- Verbatim destination: `src/main/resources/assets/wildfires/stars/stars.json`.
- Upstream path: `src/main/resources/assets/cealum/stars/stars.json`.
- The source and destination star tables are byte-identical with SHA-256 `46f2b21d8205562d5918322fbf9217c51454d2fddf05c77223e9053584abc227`.

Caelum's README identifies `frostoven/BSC5P-JSON-XYZ` as the stellar-data source. That generated catalog has separate CC BY 4.0 terms recorded under `third_party/bsc5p-json-xyz/`; the Caelum MIT license must not be used to erase the deeper data attribution.

## Rights and preserved material

- Project license: MIT.
- `upstream/README.md`: original file, SHA-256 `7c73b8ae969c736dbc01fdf39123c1aaca918b393bbbcad1ebbe229c3e466126`.
- `upstream/LICENSE`: original file, SHA-256 `d0ae4cfb3ccd36e52c3cdac4768f471f30cde91c37bbab59ec1f21e30d36952f`.
- `files.csv`: destination/source/hash/classification manifest.
