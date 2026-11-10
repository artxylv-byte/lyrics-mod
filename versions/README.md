# Other Minecraft versions (work in progress)

The main build in the repository root targets Minecraft **1.21.11**. These folders hold builds of the same code
for other Minecraft versions; each one adds a small version-specific layer from `src/compat/<compat>`.

| Folder | Minecraft | Java | Status |
| --- | --- | --- | --- |
| `1.21.9` | 1.21.9 / 1.21.10 | 21 | Working. Fabric API for 1.21.9 has no world render events, so a small mixin hooks the world pass (`src/compat/mc1_21_9`) |
| `26.3` | 26.3 | 25 | Not compiling yet: API differences still to be ported |

Build one with `./gradlew -p versions/<folder> build` (jar in `versions/<folder>/build/libs`).
`./gradlew -p versions/1.21.9 runClientGameTest` runs the screenshot test on that version, and
`./gradlew -p versions/1.21.9 prodRunClient -Pworld=<save folder>` starts the finished jar like a normal Fabric install.
