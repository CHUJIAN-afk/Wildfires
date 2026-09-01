# User-supplied nebula shader set

- Material: five Shadertoy-style GLSL text files and one 165x87 PNG reference supplied in `E:\wildfirecore\nebula`.
- Supplier and retrieval date: project user, 2026-08-24.
- Public project URL, package version, original README and standalone license: not supplied.
- Project-specific authorization: the user states that they hold authorization for the visual/shader material and expressly requested its integration into Wildfires. This authorization does not cover attack-damage mechanisms; none are used here.
- Classification: `adapted` for the volume noise, raymarch and tone-mapping code used by the Galaxy Hymn impact nebula. The external source files are read-only and are not copied into the runtime resource tree. The supplied background star field was inspected but is excluded from the runtime adaptation.
- Wildfires changes: the current implementation directly adapts the active Buffer A cloud equations from `1.txt`: `SinNoise`, `S1/Sf/eff_dis`, all `strengthw0/w1/wa/0/1` terms, dark-orange/blue/cyan layering, reddening `0.6`, saturation `0.3` and `0.03R` marching. It also directly adapts the active tone-mapping/color-grading sequence from `5.txt`. Camera rays intersect a radius-14 world-anchored proxy and are transformed onto the supplied `normalize(-1,1,0.3)` target direction; `iChannel3` temporal feedback is replaced with deterministic first-step jitter because the localized Forge pass has no Shadertoy history buffer. The five-layer `stars()` routine, its hash helper and the opaque black backdrop are physically absent from the runtime shader rather than rendered and filtered afterward. During raymarch, each `cloudcolor` step separately accumulates transported cloud RGB into `cloudEmission`; only this structured cloud-body value can create Minecraft opacity. The supplied `sourceColor.a` remains internal to cloud absorption and reddening. The colored body uses premultiplied-alpha transparency as the spell's final, non-depth-writing world composite; an empty ray is exactly transparent and the existing Minecraft framebuffer is the background. Thresholded bloom remains additive. The base and glow passes share the adapted GLSL program but use two Forge shader JSON configurations and two `ShaderInstance` objects: base explicitly applies `ONE/ONE_MINUS_SRC_ALPHA`, while glow explicitly applies `SRC_ALPHA/ONE`. This is a Minecraft composition fix required because `ShaderInstance.apply()` otherwise replaces Java's transient blend state with the default opaque mode; it does not alter the supplied cloud equations. The Minecraft presentation layer dispatches this proxy at Forge `AFTER_WEATHER` so vanilla clouds/precipitation are behind it, applies a 2.0 gain with a 0.84 cap only to visible cloud-derived world opacity, and adds a bounded 1.35 saturation boost after the supplied grading. These changes do not restore the excluded backdrop or route `sourceColor.a` into world opacity. Files `2/3/4.txt` define the supplied downsample and Gaussian chain, but `5.txt` leaves `color += GetBloom(uv) * 0.08` commented out; Wildfires does not claim that chain is active. The proxy begins at 1.2% radius at the impact-frame center point, expands in 18 ticks, then fades after 82 ticks. The spell's server mechanics, 200/20 damage, targets and collisions are independent.

## Deeper source indicators and release boundary

`1.txt` labels its `stars()` routine as coming from `https://www.shadertoy.com/view/fl2Bzd`. `5.txt` says its Buffer B/C/D and image passes come from sonicether's “Gargantua With HDR Bloom”. No license text or author identity for `fl2Bzd`, sonicether's work, or the remaining shader set was included. These indications are preserved rather than overwritten by the project-specific authorization statement.

Because those deeper-source licenses have not been verified, this adaptation is a public-release blocker unless written authorization is confirmed to cover them or the adapted routines are replaced with independently authored equivalents. Internal authorized project use is recorded; this file does not claim that unknown public redistribution rights were resolved.

## Supplied-file manifest

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `1.txt` | 17239 | `03644B24ECB0ED5BEC74AEB1E9ADF3E811EC3EE1805A3F01BBF658C9EAE778B9` |
| `2.txt` | 4052 | `D5EC5012AF562B015FFFAABFEA90E39E0D67603B2635E87271173D4A6B135177` |
| `3.txt` | 1212 | `36140A018230C96A95BE79318A5BAE0A9108DDDC75853E976A4CBE42F281B73B` |
| `4.txt` | 1210 | `629C4B235C56BD87975490E2B39940FA90FAA8A34BBEA6F1BBE1CBD8BC585D5B` |
| `5.txt` | 3387 | `9BAC9480871E672936335C5AA43F97F7E938708CAD555524D8236FCC1B7F0346` |
| `下载 (3).png` | 42603 | `1F3F57F38BF62948CEB8A5012BDC72217A3695966208964C9A69643CD69C4EC7` |

The exact external path and hashes are the evidence locator for this local authorized worktree. If the source set is later published or moved, preserve an immutable source snapshot and any accompanying license/author documents before release.
