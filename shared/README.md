# `:shared` — Kotlin Multiplatform core

`:app` depends on this module. Maximize **commonMain**; platform code only for I/O, Firestack, and drivers.

## Targets

| Module / target | Role |
|-----------------|------|
| **:shared** `commonMain` | Models, Ktor core, prefs API, Room entities/DAOs (`room-runtime` + `paging-common`), WG config model, CMP theme/shells |
| **:shared** `androidMain` | Firestack WG keys, DataStore prefs, CIO, DNS resolve |
| **:shared** `jvmMain` | CIO, in-memory prefs, `JvmDemo` |
| **:shared** `iosMain` | Optional; not a product goal |
| **web-build** `wasmJs` (always on) | Isolated included build — `ui/compose/**` from commonMain + Compose viewport; no AGP |

## Why `web-build` is separate

AGP **android KMP library** + `js()`/`wasmJs()` in **one** Gradle module fails configuration (`clean` task registered twice). `includeBuild("web-build")` is always in `settings.gradle.kts`; there is **no** opt-in flag. Goal: **most UI in commonMain** so wasmJs (and future hosts) show the same shells without VPN/DNS/Room.

## Room (simple KMP rule)

- Portable entities/DAOs/repos → `:shared` `commonMain`.
- `@Database` / migrations / android-coupled entities → `:app`.

## WireGuard

Common in `:shared`: `Config` / `Peer` / `WgInterface` / `Attribute` / `WgKeyHandle`.  
App: `ConfigIo` for streams; Firestack only via `toFirestackKey()` on Android.

## Demo commands

```bash
# Browser UI demo (wasmJs + commonMain Compose; always available)
./gradlew compileWebJs
./gradlew runWebDemo          # webpack/dev server when configured
# or: ./gradlew -p web-build compileKotlinWasmJs

# JVM stand-in
./gradlew :shared:runJvmDemo

# Product Android path
./gradlew :shared:compileKotlinJvm
./gradlew :shared:compileAndroidMain
./gradlew :app:compilePlayFullDebugKotlin
```

**UI strategy:** portable screens/components live under `shared/.../ui/compose/` (`RethinkDemoApp`, `WelcomeScreenShared`, `HomeDashboardShared`, theme, empty states). App keeps Android-only screens/resources; incrementally delegate shells via `SharedCmpBridge`. `wasmJsMain` only mounts `RethinkDemoApp` in the browser.

## Still only in `:app` (expected)

`AppDatabase` / migrations / `DatabaseModule` / `RefreshDatabase`, android-coupled entities (`AppInfo`, `CustomIp`, `DnsLog`, …), VPN/Go/Firestack, rich Compose screens.
