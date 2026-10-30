# AGENTS.md — Keepout architecture map

A no-root Android VPN/DNS/firewall app (fork of RethinkDNS, modernized). Kotlin, Jetpack Compose, Material 3 Expressive, KMP `:shared` module (Android + wasmJs demo), Go (Firestack/Intra) tunnel core via `tun2socks/`.

## Build & verify (JDK 17)

```bash
./gradlew :app:compilePlayDebugKotlin        # fastest signal (compile one flavor)
./gradlew :app:testPlayDebugUnitTest         # unit tests
./gradlew :app:detekt :app:lintPlayDebug     # quality gates (both baseline-ratcheted)
./gradlew :shared:compileKotlinWasmJs :shared:jvmTest   # web/jvm targets (CI builds wasm)
./gradlew :app:compileFdroidDebugKotlin :app:compileWebsiteDebugKotlin  # other channels
```

Flavors: `play` / `fdroid` / `website` (single `releaseChannel` dimension; the former `releaseType` dimension and `src/full` were promoted into `src/main`). Billing lives once in `app/src/billing/` and is wired
only into play + website variants (fdroid must never see Play Billing). Firebase applies to
play/website channels only.

## Quality gates (enforced in CI, `.github/workflows/ci.yml`)

- **Detekt** (`config/detekt/detekt.yml` + `baseline.xml`): size rules ON — any *new* oversized
  file/method fails the build; existing debt sits in the baseline and only shrinks.
  `FunctionNaming` ignores `@Composable`; `UnusedPrivateMember` ignores `@Preview`.
- **Lint** (`warningsAsErrors=true` + `app/lint-baseline.xml`): remaining baseline entries are
  deliberate keeps (manifest hardening attrs, FileProvider root paths, etc.) — do not delete
  blindly; each has a reason.
- After intentional debt reduction: `./gradlew :app:detektBaseline` / delete `app/lint-baseline.xml`
  and run lint once to regenerate, then commit the smaller baseline.

## Module map

| Path | Role |
|---|---|
| `app/src/main/.../service/` | VPN service layer. `BraveVPNService` (Go bridge + orchestration) delegates to: `ConnectionFirewall` (per-connection verdicts — **29 tests**), `VpnTunRoutes`/`VpnTunAppPolicy` (builder), `VpnDnsTransportController`, `VpnPrefObservers`, `VpnNotificationManager`, `BubbleController`, `VpnServiceDiagnostics`. `VpnController` is the process-wide facade UI talks to. `ConnectionMonitor` owns network callbacks (dedicated HandlerThread). |
| `app/src/main/.../net/go/` | Go tunnel adapter: `GoVpnAdapter` (thin façade; WG/RPN/ping) + `GoDnsTransports`/`GoDnsTransportUrls` (DNS transports — **48 tests**). The Go bridge hot path (`flow`/`bind`/`protect`/`go2kt`) is frozen: signature- and order-stable. Before changing the native pin or callback contract, read `docs/FIRESTACK_PROTOCOL.md`. |
| `app/src/main/.../rpnproxy/` | RPN (ReThink Proxy Network): `SubscriptionStateMachineV2` (Uninitialized/Entitled/NotEntitled/Pending — **72 tests**; Play snapshot is single writer; 3-strike empty-snapshot guard) + `RpnProxyManager` facade over 8 collaborators (hop/SSID/CSV logic extracted — **114 tests**). |
| `app/src/billing/` | Play Billing (shared by play/website): `InAppBillingHandler` + processors/notifiers. Lock order: connectionMutex → SM stateLock → repo mutex (documented in SM KDoc). |
| `app/src/main/.../data/` | `AppConfig` is a **façade** (interface delegation) over `DnsSettings`/`DnsEndpointSettings`/`DnsConnectionSettings`/`FirewallSettings`/`ProxySettings`/`TunnelSettings`/`LogSettings`. Callers use `appConfig.*` unchanged. |
| `app/src/main/.../database/` + `shared/.../database/` | Room 3 KMP. Entities/DAOs/@Database live in `:shared` commonMain (schemas in `shared/schemas/`); Android drivers in androidMain. The app is **v0**: both DBs are at version 1, no migrations exist, and schema changes simply re-emit v1. Rolling-window stats cards use indexed raw-log queries for exact cutoffs. Materialized `daily_*` tables remain transactionally maintained on batch inserts and connection completion; explicit log deletion rebuilds them (the androidMain bridge keeps commonMain Koin-free). |
| `app/src/main/.../backup/` | `BackupAgent` captures live SQLite `VACUUM INTO` snapshots into private staging; `BackupRestoreFileGate` serializes source snapshots and restores. `RestoreAgent` validates current schema identities and installs with rollback. Preference archives use bounded typed data. Tests under `app/src/test/.../backup/`. |
| `app/src/main/.../ui/` | `HomeScreenActivity` (thin host) + `HomeIntentRouter`/`HomeBillingController`/`HomeBackupController` + saveable `HomeDialog` host. `ui/components/*Row.kt` are Compose rows, strings resolved via `stringResource`. |
| `shared/.../ui/compose/` | Shared Compose screens + design system: `SharedDimensions` (M3 shape scale), `RethinkMotion` (+ live reduced-motion), `RethinkStatusColors`, `RethinkEmptyState`/`RethinkLoadingState`, `RethinkWindowClass` (600/840dp adaptive). Navigation: `RethinkAppNavigation` (Nav3 official multi-backstack recipe — one saveable `rememberNavBackStack` + one `rememberDecoratedNavEntries` per root tab, `NavDisplay(entries=)` swaps lists so decorator state survives tab switches; `dropUnlessResumed` guards back). Route graph is the single `RethinkRoute` sealed hierarchy — never introduce a parallel route vocabulary; unknown keys fall back to a blank entry rather than crashing. |
| `tun2socks/`, `config/`, `scripts/` | Go core (submodule), detekt config, web-deploy verification. |

## Invariants — break these and you regress months of work

1. **Go bridge protocol**: `flow`/`onQuery`/`bind4`/`bind6`/`protect`/`preflow`/`inflow` call order and
   signatures are load-bearing against the Go library. Never `runBlocking`/`Thread.sleep` on this path.
   The `go2kt` runBlocking itself is **verified-required** (2026-08 analysis): Go consumes each
   callback's return value as the routing verdict (`Mark`/`PreMark`/`DNSOpts`) or requires the
   protect/bind side effect before using the fd — launching async fail-opens (`Empty Mark → Exit`)
   or hairpins. Only a firestack protocol change (`FlowAsync` + `Complete(cid, mark)`) can free the
   Go thread. Safe latency work: UID caching, verdict caching, skip the CoFactory hop on
   bind/protect only.
2. **Billing wire format**: kotlinx.serialization with `encodeDefaults`/`ignoreUnknownKeys`/`isLenient`
   (gson-compat). Field names are the API.
3. **Lock ordering** (billing): connectionMutex → stateLock → repository mutex.
4. **Firewall rule strings** (`RULE1C` etc.) are persisted in the log DB and matched by UI filters.
5. **Launcher identity**: the manifest launcher is the `LauncherAliasAppLock` activity-alias — existing
   home-screen shortcuts resolve through it.
6. **startForeground** must be synchronous in `onStartCommand`; ConnectionMonitor unregister/quit must
   complete before `vpnScope.cancel` in `onDestroy`.
7. **This app is v0**: both databases are at schema version 1 with no migrations and no
   upgrade archaeology — schema changes bump the version and ship as v1 again. Never add
   version-gated migration code or "remove post vX" residue; delete old paths outright.

## Conventions

- UI strings: `stringResource` in composable scope; hoist above lambdas; never `context.getString`
  in @Composable files (lint enforces).
- Touch targets ≥48dp (`touchTargetMin`); motion through `LocalRethinkMotion`; shapes/radii/spacing
  from `SharedDimensions`; status colors via `RethinkStatusColors`.
- Exceptions: catch the narrowest actionable type; log with throwable; never swallow
  `CancellationException` in suspend paths. JNI/Go boundary wraps may stay broad *with* logging.
- Tests: JUnit4 + mockk (+ Robolectric where Android APIs are touched). No `Thread.sleep` —
  inject scopes (`UnconfinedTestDispatcher`) or use `runTest`. New extraction work lands with tests.
- Commits: sentence-style subject, body bullets, single logical change.
- Issue tracker: `bd` (beads) is initialized — multi-session work goes there (`bd ready`).
- DI: Android entry points (Activity, BroadcastReceiver, Worker, TileService, VpnService)
  use `KoinComponent` + `by inject()` at the boundary. Hot-path domain managers
  (`FirewallManager`, `VpnController`, `RpnProxyManager`, …) stay Kotlin `object`s
  and may resolve graph singletons via `inject()` — they are never also `@Single`.
  Graph types (`@Single` classes) take constructor params; no unused `KoinComponent`.
  `Logger` stays a static object (used everywhere). Never dual-register an object.
