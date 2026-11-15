# Sand RGB ESP

Client-side Fabric mod for Minecraft Java 1.21.11.

## Current features
- Scans nearby blocks for normal sand and red sand.
- Animated RGB outline around detected sand blocks.
- Press **K** to toggle the ESP.
- Search radius: 24 blocks around the camera.

## Build
Run `gradle build` with Java 21. The JAR is created in `build/libs/`.
GitHub Actions also builds the JAR on every push and uploads it as an artifact.

## Install
Copy the generated `Sand-RGB-ESP-1.21.11-1.0.0.jar` into the Minecraft `mods` folder with Fabric Loader and Fabric API for 1.21.11.
