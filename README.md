# Sand RGB ESP

Client-side Fabric mod for **Minecraft Java 1.21.11**.

## Implemented
- Detects normal sand and red sand within 16 blocks of the player.
- Draws an animated RGB outline around detected blocks.
- Press **K** to toggle the ESP.
- Scans the nearby area every 10 client ticks to reduce per-frame work.

## Build
Requires Java 21 and Gradle 9.2.1.
Run `gradle build`. The remapped mod JAR is produced in `build/libs/`.

GitHub Actions builds the project on every push and uploads the JAR as an artifact when the build succeeds.

## Install
Put the generated JAR in the Minecraft `mods` folder with Fabric Loader and Fabric API for 1.21.11.

## Not implemented yet
Directional arrows, distance labels, through-wall rendering, and a separate resource-pack texture are not included in this build yet.
