# NexAlloy patch bundle template

A standalone project that builds a [NexAlloy Next](https://github.com/2eno/NexAlloy-Next) patch bundle (`.nxb`).

1. Copy this directory into a new repository.
2. Change `namespace`/`applicationId` (the bundle id), `nexalloyBundle` and `res/values/strings.xml` in
   [bundle/](bundle).
3. Write patches, see the [development guide](https://github.com/2eno/NexAlloy-Next/blob/main/DEVELOPMENT.md#writing-a-patch-bundle).
4. `./gradlew :bundle:assembleBundle` builds `bundle/build/outputs/bundle/bundle-<version>.nxb`. Install it in NexAlloy
   with *Import bundle from file*.
5. Push a tag: the [release workflow](.github/workflows/release.yml) attaches the bundle to a GitHub release. Users add
   your repository (`owner/repo`) as a patch source in NexAlloy.

`buildSrc/` holds a copy of the `nexalloy.bundle` plugin from NexAlloy's `build-logic/`, and `gradle.properties` the
patcher version to compile against. Update both when targeting a newer NexAlloy.
