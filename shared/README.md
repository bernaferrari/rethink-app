# `:shared` — Kotlin Multiplatform core

`:app` depends on this module. Maximize **commonMain**; platform code only for I/O, Firestack, and drivers.

## Targets

| Module / target | Role |
|-----------------|------|
| **:shared** `commonMain` | Models, Ktor core, prefs API, Room entities/DAOs (`room-runtime` + `paging-common`), WG config model, CMP theme/shells |
| **:shared** `androidMain` | Firestack WG keys, DataStore prefs, CIO, DNS resolve |
| **:shared** `jvmMain` | CIO, in-memory prefs, `JvmDemo` |
| **shared** `wasmJs` (always on) | The same `commonMain` UI and state graph as Android, hosted in a Compose viewport |

## Browser target

`wasmJs` lives directly in `:shared`, alongside the AGP-backed Android KMP target. The root
build applies Gradle's base lifecycle plugin before configuring its `clean` task; that resolves
the historical duplicate-task conflict and lets Android and the browser compile the exact same
`commonMain` graph. The goal is that nearly all visual UI stays target-neutral; platform source
sets provide only host integration such as the Android VPN, package discovery, and filesystem
backends.

## Room (KMP rule)

- Portable entities/DAOs/repos → `:shared` `commonMain`.
- `@Database` declarations and verified `Migration`s also live here (`AppDatabase`, `LogDatabase`, `ConsoleLogDatabase` + `*Migrations.kt`); schemas export to `shared/schemas/`. Android builders wire drivers in `androidMain`.

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
./gradlew :app:compilePlayDebugKotlin
```

**UI strategy:** one navigation host, one route graph, host-only data.

```
Android Activity  ──┐
                    ├── RethinkAppNavigation (shared) ── entryBuilder ── screens
Wasm ComposeViewport┘         │
                              ├── RethinkRoute keys + canonical stacks
                              └── RethinkNavShell (bar/rail, CMP-8543)
```

| Host | Mount | entryBuilder |
|------|-------|----------------|
| **Android** | `HomeScreenRoot` → `RethinkAppNavigation` | product screens + ViewModels |
| **wasm** | `RethinkAppContent` → `RethinkAppNavigation` | `rethinkPreviewEntries` fixtures |

Same `RethinkRoute` graph and chrome on both hosts; root tabs preserve their back stacks, details push. Preview data lives under `ui/compose/demo/` — not a second app.

## Still only in `:app` (expected)

VPN/Go/Firestack service layer, billing (`src/billing` shared by play/website channels), backup/restore agents, android-coupled Compose screens and dialogs.
