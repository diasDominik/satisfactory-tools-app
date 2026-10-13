# Satisfactory Tools (Android / iOS)

A Kotlin Multiplatform + Compose Multiplatform port of [Satisfactory Tools](https://www.satisfactorytools.com/)
([greeny/SatisfactoryTools](https://github.com/greeny/SatisfactoryTools)) for Android and iOS (plus a desktop build
for development). Supports Satisfactory 1.0 and the 1.0 FICSMAS data.

## Features

- **Production planner**: multiple production lines, each with
  - products as items per minute or "maximize" (with ratios),
  - raw resource toggles and limits (defaults to the map limits),
  - inputs you already have,
  - alternate recipes, basic recipes and buildings you can enable or disable,
  - results: overview (inputs, raw resources, products and byproducts, buildings, power, alternates needed),
    an interactive production graph, per-item production and consumption, building counts and build costs,
    and power per building with clock speeds of the last machine.
  - clone, rename, change icon, reset, remove; select several lines to export or remove them.
  - **.sft export and import**, compatible with the website's export files.
- **Codex**: items (with filters for radioactive, energy value, stack size and form), buildings and schematics,
  with recipes, usages, building costs, overclock calculators for manufacturers, extractors and generators,
  and a dependency graph for schematics.
- **Game version switch** between 1.0 and 1.0 + FICSMAS.

The planner runs fully on the device: upstream uses a server-side solver, which this app replaces with a linear
program solved in shared Kotlin (`shared/src/commonMain/kotlin/.../planner`). It minimises weighted raw resource
use with the same weights the website uses.

Item and building icons are generated placeholders. The upstream icons are Coffee Stain Studios' property and
the upstream license forbids using them in derivative works.

## Project layout

- `shared/`: all app code (data, planner, Compose UI) for Android, iOS and JVM.
- `androidApp/`: Android application entry point.
- `iosApp/`: Xcode project for the iOS app.
- `desktopApp/`: desktop entry point, handy for quick UI iteration.

## Building

- Android: `./gradlew :androidApp:assembleDebug` (or run `androidApp` from Android Studio).
- iOS: open `iosApp/iosApp.xcodeproj` in Xcode and run, or use the KMP plugin in Android Studio / IntelliJ.
- Desktop: `./gradlew :desktopApp:run`.
- Tests: `./gradlew :shared:jvmTest`.

## Updating game data

Copy `data/data1.0.json` (and `data1.0-ficsmas.json`) from upstream into
`shared/src/commonMain/composeResources/files/`.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for licenses of reused code and data.
