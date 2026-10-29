# Developing Keepout

Related: [README](../README.md) · [AGENTS.md](../AGENTS.md) (architecture map and invariants) · [Store listing](../store-listing/README.md)

## Build and run

Prerequisites: JDK 17 and the Android SDK. Clone the repository, open it in the latest Android Studio, let
Gradle sync, and run the `app` configuration on a device or emulator. From the command line:

```bash
git clone https://github.com/bernaferrari/rethink-app.git
./gradlew :app:installFdroidDebug          # de-Googled build; no Firebase, no Play Billing
./gradlew :app:installPlayDebug            # the Play channel
```

Flavors are release channels: `play`, `fdroid` and `website`. Billing lives once in `app/src/billing/` and is
wired only into `play` and `website`; Firebase applies to those two channels only. Release builds need a
`VERSION_CODE` (environment or `-PVERSION_CODE=...`) and a local `keystore.properties`, which is never committed.

## Checks

```bash
./gradlew :app:compilePlayDebugKotlin                     # fastest signal
./gradlew :app:testPlayDebugUnitTest                      # unit tests
./gradlew :app:detekt :app:lintPlayDebug                  # quality gates (baseline-ratcheted)
./gradlew :shared:compileKotlinWasmJs :shared:jvmTest     # web and JVM targets
./gradlew :app:compileFdroidDebugKotlin :app:compileWebsiteDebugKotlin
```

CI runs these in [`.github/workflows/ci.yml`](../.github/workflows/ci.yml). The browser demo builds with
`./scripts/verify-web-deploy.sh` (Kotlin/Wasm, UI only; it does not filter traffic).

## Store screenshots

The `storeCapture` build type (`.storecapture`, code in `app/src/storeCapture/`) shows the real UI over fictional
apps and a seeded week of traffic. It has no VPN service and no INTERNET permission. The fictional apps are
code-free APKs described in `store-listing/fixture-apps.json`. See [store-listing/README.md](../store-listing/README.md).

## Technology

- [Kotlin](https://kotlinlang.org/), [Jetpack Compose](https://developer.android.com/jetpack/compose) and
  [Material 3 Expressive](https://m3.material.io/); shared screens live in the Kotlin Multiplatform `:shared` module.
- Coroutines and Flow; Room and DataStore for rules, settings and logs.
- The tunnel is Celzero's Go [firestack](https://github.com/celzero/firestack) (`tun2socks/`).

## Behavior notes

- Some device makers apply stricter VPN and background policies, so behavior can vary.
- Background firewall rules rely on notification, battery-optimization and accessibility permissions.
- Keepout is local-first: rules and logs stay on the device, and only DNS goes to the chosen resolver
  (Rethink DNS by default) unless you add a proxy or WireGuard peer.
