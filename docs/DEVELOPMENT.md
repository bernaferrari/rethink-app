# Developing Keepout

An Android VPN, DNS and firewall app in Kotlin and Jetpack Compose, forked from Celzero's Rethink DNS + Firewall.
`:app` is the Android app; `:shared` is a Kotlin Multiplatform module with the shared screens and database, which
also builds a browser demo.

[README](../README.md) · [AGENTS.md](../AGENTS.md) (architecture map and invariants) · [Store listing](../store-listing/README.md)

## Quick start

```bash
git clone https://github.com/bernaferrari/rethink-app.git
cd rethink-app
./gradlew :app:installFdroidDebug
```

## What you need

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 17 | The build's toolchain; Gradle's foojay resolver downloads it if missing. CI's web job uses 21. |
| Android SDK | API 37 | `compileSdk` 37 (minor 2), `targetSdk` 37, `minSdk` 24. |
| Gradle | 9.8.1 | Use `./gradlew`. |
| Node | any recent | Only for the store listing tools. |

No Go toolchain is needed: the tunnel ships as Celzero's prebuilt
[firestack](https://github.com/celzero/firestack) AAR from JitPack. `tun2socks/` is a git submodule with the Go source.

## Run it

| Target | Command |
| --- | --- |
| Android, F-Droid channel (no Firebase, no billing) | `./gradlew :app:installFdroidDebug` |
| Android, Play channel | `./gradlew :app:installPlayDebug` |
| Android, website channel | `./gradlew :app:installWebsiteDebug` |
| Web demo (Kotlin/Wasm, UI only, filters nothing) | `./gradlew :shared:wasmJsBrowserDevelopmentRun` |
| JVM demo (models and Room types only) | `./gradlew :shared:runJvmDemo` |

Flavors are release channels. Billing lives once in `app/src/billing/` and is wired only into `play` and `website`;
Firebase also applies to those two only. `fdroid` must never see Play Billing classes.

## Where things live

| Path | What's there |
| --- | --- |
| `app/src/main/` | The app: VPN service, Go tunnel adapter, firewall, settings, backup, Android UI |
| `app/src/billing/` | Play Billing, shared by the `play` and `website` channels |
| `app/src/play/`, `fdroid/`, `website/` | Per-channel wiring |
| `app/src/storeCapture/` | Seeded data for store screenshots |
| `shared/` | KMP module: shared Compose screens, design system, Room entities, DAOs and schemas |
| `tun2socks/` | Go tunnel source (submodule, reference only) |
| `config/detekt/` | Detekt rules and baselines |
| `scripts/` | Web bundle build and verification |

## Before you push

```bash
./gradlew :app:compilePlayDebugKotlin :app:testPlayDebugUnitTest :app:detekt
./gradlew :app:verifyRoborazziPlayDebug --tests "*Screenshot*"
./gradlew :app:lintPlayDebug :app:lintFdroidDebug :app:lintWebsiteDebug
./gradlew :app:compileFdroidDebugKotlin :app:compileWebsiteDebugKotlin
./scripts/verify-web-deploy.sh && ./gradlew :shared:jvmTest
```

[CI](../.github/workflows/ci.yml) runs all of these on every push and pull request: compile, unit tests and detekt
for each channel, Roborazzi screenshot goldens on PlayDebug, lint for each channel, and the web bundle plus shared
JVM tests. Detekt (`config/detekt/`) and lint (`app/lint-baseline.xml`, warnings as errors) are baseline-ratcheted:
new debt fails, and after you pay some off, regenerate the baseline with `./gradlew :app:detektBaseline` or by
deleting `app/lint-baseline.xml` and running lint once. CI runs no instrumented tests.

## Store screenshots and icon

Screenshot Studio lives in `store-listing/studio/`. It is synced from `~/Documents/screenshot-studio`, so never edit
it here. Start with [store-listing/README.md](../store-listing/README.md).

- The capture build is `./gradlew :app:assembleFdroidStoreCapture` (`com.bernaferrari.bravedns.storecapture`). It
  shows the real UI with seeded rules and a week of made-up traffic against the Google apps on the emulator's Play
  image. It has no VPN service and no INTERNET permission, installs beside the real app and is never released.
  The scene tour is `store-listing/capture-emulator.py`. `fixture-apps.json` only lists retired stub apps,
  which the tour uninstalls if it finds them.
- `node store-listing/studio/recapture.cjs` builds, installs and captures on the dedicated capture emulator
  (`CAPTURE_SERIAL`, default `emulator-5578`, never a personal phone), then exports. Narrow it with a locale
  (`en-US`), `--profile phone`, `--no-build` or `--no-capture`.
- Export only: `PLAYWRIGHT_MODULE=… node store-listing/studio/export.cjs --locale en-US`. Live editor:
  `node store-listing/studio/server.cjs`.
- The icon is drawn as layers in `store-listing/brand/*.svg`, with targets in `brand/icon.json` (an `alpha/`
  variant included). Regenerate every target with `PLAYWRIGHT_MODULE=… node store-listing/studio/icon.cjs`.

## Good to know

- Release builds need `VERSION_CODE` (environment or `-PVERSION_CODE=...`); they fail rather than ship
  versionCode 0. Signing reads a local `keystore.properties`. It is not in `.gitignore`, so take care never to
  commit it or a keystore file.
- External VPN controls (`receiver/VPNControlReceiver.kt`): start/stop broadcasts work only on Android 14+ from an
  OS-verified sender in the configured package allowlist. Package names in intent extras are never trusted. On older
  Android the receiver fails closed, and the app and Quick Settings tile still work.
- Keepout is local-first: rules and logs stay on the device, and only DNS leaves it, to the chosen resolver (Rethink
  DNS by default) unless the user adds a proxy or WireGuard peer.
- Some device makers enforce stricter VPN and background policies. Background firewall rules rely on notification,
  battery-optimization and accessibility permissions, so check behavior on more than one OEM.
- Both Room databases are at version 1 with no migrations; schema changes re-emit v1 (see AGENTS.md).
