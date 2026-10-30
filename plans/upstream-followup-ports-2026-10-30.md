# Remaining upstream ports — scope and verification

The user approved the entire follow-up list on 30 October 2026, including optional
features. Implementation starts from `3c810ab9c`, which includes the user's committed
Keepout icon and log-verdict changes. Uncommitted main-worktree UI/store/design files
are user work and are outside this task.

Upstream remains `07fe5373f`. These are behavioral adaptations to the existing
Compose/KMP/Room 3 architecture, not an upstream history merge.

## Approved behavior

| Area | Required result |
|---|---|
| Missing native proxies | Recreate an enabled WG/RPN proxy that is actually absent; refresh an existing unresponsive proxy. Respect disable/pause, adapter generation and concurrent recovery ownership. |
| Relayed RPN automation | Use AUTO's effective network policy for relayed country proxies while acting on the actual country proxy. Missing AUTO state must not silently enable unrestricted routing. |
| Empty SSID policies | An empty policy matches known Wi-Fi even if its name is unavailable. A named policy does not match an unknown name; mobile selection remains distinct. |
| Smart DNS profiles | Persist and expose None, Security, Privacy and Family, and apply selection to the native resolver at connection and update time. Preserve current default behavior. |
| Custom DoH bootstrap IPs | Complete upstream's unfinished editor/storage idea: validate bounded literal IP lists, persist them, and supply them to transport creation without replacing the HTTPS hostname/SNI. Empty input retains existing bootstrap behavior. |
| RPN state storage | Use a canonical internal directory for all entitlement/state paths, including native registration. Fresh creation only; no legacy relocation code. |
| Encrypted replacement | A failed pre-commit replacement preserves the previous file. Close streams safely, preserve filename authentication, serialize conflicting writes, and never reset shared keys to recover one file. |
| Website billing coverage | Run the existing billing regression suites for both Play and Website while keeping F-Droid free of billing classes. |
| Retained WAL policy | Configure a 32 MiB retained journal limit on app/log database connections using the production driver semantics. This is not a hard cap on an active WAL. |
| Console severity | Apply text and minimum severity to the displayed query, including already retained rows. |
| Console pause/search | Freeze a bounded snapshot of at most 5,000 entries; search and severity remain usable while paused, and resume returns to live data. Backend logging continues. |
| DNS log rule actions | Offer explicitly scoped app/global domain rules with validation, shared-UID clarity, owned asynchronous saves, busy/error states and immediate rule-manager application. |
| Close connections | Add a confirmed Statistics action for closing all active connections; disable and recheck when the VPN is stopped, without starting it. |

## Invariants and boundaries

Both persistent databases remain fresh schema v1. Re-emit the current schema when
needed; add no migrations or old-version paths. Preserve synchronous native callback
returns, the pinned Firestack protocol, foreground-service ordering, monitor teardown,
billing wire fields/lock order, launcher identity, and the single shared route hierarchy.

Use current Keepout ImageVectors and existing M3 components. Preserve resource-based
UI strings, minimum touch targets, reduced-motion behavior and cancellation propagation.
Do not grow Detekt/lint baselines, replace working user UI wholesale, publish artifacts,
use real signing keys, or exercise a paid account without one being provided.

## Verification requirements

Focused tests must cover actual recovery/automation decisions; native DNS arguments;
fresh seeded database creation; encrypted round-trips and failure preservation; driver
connection behavior; both billing test channels; console snapshots/query state; and
rule/connection-action persistence, scope and lifetime guards.

Integration gates include all three channel compiles/unit suites/lints, app/shared
Detekt, shared JVM and Wasm compilation, and reviewed screenshot verification. Run
production-driver and encrypted-file instrumentation on an isolated test emulator,
then fresh native DNS/HTTP and relevant UI smoke checks. Never modify the user's
existing emulator. Validate minified releases and APK packaging after production
changes. Final Standards and Spec reviews are separate; capture findings and actual
results below before landing. Work progress and dependencies live in Beads epic
`rethink-app-y8p`, not in this document.

## Results

All thirteen ports are integrated at application-source commit `dd984d2eb`.
Subsequent user UI commits through `334e0f42f` were preserved in the integration
branch. The final source review compares `334e0f42f...dd984d2eb`, excluding those
user-authored changes. Upstream was fetched again and remains `07fe5373f`.

### Focused evidence

- Network: 97 application tests and 57 shared JVM tests passed, without failures,
  errors or skips. Play/Wasm compilation and both Detekt tasks passed.
- Product: 35 application tests and 52 shared JVM tests passed; Play/Wasm
  compilation and both Detekt tasks passed.
- Isolated Android emulator: four real Keystore/encrypted-file and production
  Room-driver tests passed before final integration. The combined build will
  be installed fresh and checked again; no existing user emulator is used.
- Root independently verified that reversing only the cache identifiers restores
  all 119 generated vector files exactly, and that all 33 firewall rule-to-glyph
  mappings and the null/unknown DNS fallback are unchanged. The icon generator
  check passed. Historical Mac/Linux comparator regression pairs are frozen
  separately from current visual goldens; comparison tolerances are unchanged.

The source review found a partial WG recovery case: native proxy restoration
could succeed while DNS-alias registration failed permanently. Recovery now
retries missing aliases and gates rollback/pause on whether the current routing
configuration actually requires that alias. Optional aliases preserve data-only
routing. Recovery also defers an absent origin until its configured hop is
available, rather than silently installing a direct path.

### Integration evidence

The complete integrated unit matrix at `dd984d2eb` passed with zero failures,
errors or skips:

| Suite | Tests |
|---|---:|
| Play | 803 |
| F-Droid | 750 |
| Website | 803 |
| Shared JVM | 61 |
| Total executions | 2,417 |

All 53 billing regressions ran in both Play and Website; none ran in F-Droid.
All three debug channels and shared Wasm compiled. The shared JVM run includes
three real Bundled SQLite retained-journal tests. Full XML results were archived
before subsequent focused/screenshot tasks could replace their output.

The combined gate exposed two quality issues: the crypto facade had thirteen
functions against an eleven-function limit, and the paused-snapshot description
needed a plural resource. Commits `c2b1b143b` and `74f4365e4` address these without
baseline growth. Root proved the three relocated private helper signatures and
bodies are unchanged after dedenting, with identical public/internal declarations.
The new plural preserves the exact rendered 5,000-entry wording. UI acceptance
also found the App logs route lacked a production entry; `7d9abd673` exposes it
through the shared Logs overflow using the existing route and preserves other
actions. Both review axes checked all three follow-ups with zero remaining findings.

The newest user DNS-row design at `17722ad9e` is preserved in integration
`248644b9b`. The user subsequently requested an immediate push. The newest main commit
`c258bbb1a` (log presentation and download icon) was also preserved before landing.
The final focused gate passed updated compiles, encryption tests, Detekt, Play
lint and F-Droid lint; Website lint was still running at the push request.
These checks started before the newest main presentation merge.

Screenshot verification/intentional refresh, a fresh installation of the combined
APK and its full Android UI/native smoke suite, and new minified release/15-APK
validation have **not** been completed for this batch. The four isolated Android
crypto/Room tests reported above preceded final integration. Paid RPN traffic and
physical Wi-Fi/WireGuard handovers remain unverified. No release was published or
real signing key used. This push is not a claim that those remaining checks passed.

## Standards

The independent final Standards review found no high-confidence actionable
findings in `334e0f42f...dd984d2eb`. It inspected storage replacement and RPN path
associations, driver ownership, product actions, test infrastructure and the final
network delta. Cancellation propagates; rollback preserves primary failures and
isolates cleanup-reporting failures; required WG DNS restoration gates resume;
optional DNS failures preserve data routing; obsolete Smart DNS status cannot
supersede a newer selection. Repository-endorsed façades and object boundaries
were respected. Mechanical icon equivalence relied on root's independent proof.
This was source inspection; runtime evidence is recorded separately.

## Spec

The independent final Spec review found no remaining high-confidence findings
against the approved thirteen behaviors at `dd984d2eb`. The confirmed WG DNS
partial-recovery issue is addressed, including the selected one-WG versus advanced
split-DNS distinction. Relayed RPN automation inherits AUTO policy while operating
on the country proxy; missing AUTO policy fails closed. Integrated RPN paths are
canonical and internal, without relocation or migrations. Storage, console,
rule/connection actions and billing-test sharing match the approved behaviors.
This was source inspection; paid native RPN behavior remains unverified.

Source-review totals: Standards 0 outstanding findings; Spec 0 outstanding
findings (the confirmed WG DNS issue was fixed before final review).
