# `:shared` — Kotlin Multiplatform core

`:app` depends on this module. Maximize **commonMain**; platform code only for I/O, Firestack, and drivers.

## Targets

| Module / target | Role |
|-----------------|------|
| **:shared** `commonMain` | Models, Ktor core, prefs API, Room entities/DAOs (`room-runtime` + `paging-common`), WG config model, CMP theme/shells |
| **:shared** `androidMain` | Firestack WG keys, DataStore prefs, CIO, DNS resolve |
| **:shared** `jvmMain` | CIO, in-memory prefs, `JvmDemo` |
| **:shared** `iosMain` | Optional; not a product goal |
| **shared** `wasmJs` (always on) | The same `commonMain` UI and state graph as Android, hosted in a Compose viewport |

## Browser target

`wasmJs` lives directly in `:shared`, alongside the AGP-backed Android KMP target. The root
build applies Gradle's base lifecycle plugin before configuring its `clean` task; that resolves
the historical duplicate-task conflict and lets Android and the browser compile the exact same
`commonMain` graph. The goal is that nearly all visual UI stays target-neutral; platform source
sets provide only host integration such as the Android VPN, package discovery, and filesystem
backends.

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
# or: ./gradlew :shared:compileKotlinWasmJs

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
