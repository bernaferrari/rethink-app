# Pre-rebase / upstream reconciliation

## Guardrails and inputs

This reconciliation deliberately does not use the local `main` branch.

| Role | Ref | Commit |
| --- | --- | --- |
| Protected pre-rebase work | `backup-before-rebase-20260623` | `efe485338e5836ea4270101f91fd59b5b273710e` |
| Verified upstream base | `upstream/main` | `553062454b8b29084e67942f0abfcd74c86caac7` |
| Common ancestor | — | `3c67ba60170af8b8873e35a5eef75197de523af9` |
| Reconciliation branch | `codex/reconcile-pre-rebase-upstream` | based directly on the verified upstream commit |

The original worktree remains checked out on `backup-before-rebase-20260623`. All integration work was done in the separate `rethink-app-reconcile` worktree.

## Comparison method

The two sides were treated as independent squashed diffs from their common ancestor, then compared path-by-path:

- Pre-rebase work: 141 commits and 836 changed paths.
- Upstream work: 844 commits and 682 changed paths.
- Direct overlap: 345 paths.

The pre-rebase diff was first applied as one structural reconciliation layer over the verified upstream commit. Overlapping behavior was then reviewed and refined manually. This prevents either branch's commit ordering from deciding the result accidentally.

## Decisions

| Area | Result |
| --- | --- |
| UI architecture | Preserve the pre-rebase Compose navigation and screens. Do not restore upstream View activities, fragments, adapters, bottom sheets, or their XML layouts. |
| Shared architecture | Preserve the KMP/shared-source migration and Kotlin Gradle build files. |
| Database | Carry upstream Room 3 schema-v30 behavior into the shared database, including ECH fields, country configuration, subscription state/history, DAO queries, and repositories. |
| VPN/network core | Carry upstream proxy, WireGuard, Firestack, event-source, and RPN behavior into the Compose/KMP structure. Firestack uses the verified OSSRH commit with compatibility aliases for the reconciled call sites. |
| Billing flavors | Preserve the real Play/website billing implementation and the F-Droid stub boundary. Restore backend networking, error contracts, purchase management, and per-flavor dependency wiring. |
| Notifications | Route billing conflicts, authorization failures, and unregistered-device alerts directly into `HomeScreenActivity` and the Compose RPN account flow. The deleted notification trampoline is not restored. |
| Tests | Retain tests for current behavior. Remove or replace tests whose subjects were discarded View-only screens or superseded internal implementations. |

## View/XML behavior translated to Compose

- Trusted/free DNS selection now has persistent `AUTO`, `GLOBAL`, and `FALLBACK` modes, service routing, and Compose controls.
- RPN country selection supports refresh, reset, enable/disable, and server details.
- RPN server details expose hop, catch-all, lockdown, and mobile-only routing controls through `RpnProxyManager`.
- The Compose RPN account screen includes cancellation/revocation confirmation, local purchase history, server order history, and contact-support actions.
- Checkout links to the account-management route.
- Billing 401, 409, and device-registration notification taps reconstruct the error and navigate to Compose purchase management.

This is intentionally behavioral parity rather than a line-for-line port of obsolete XML screens.

## Test reconciliation

The upstream Play handler test had accumulated assertions against private fields, removed methods, and the discarded View-era architecture. It was replaced with a focused flavor-boundary suite covering product-type resolution, server endpoint contracts, conflict recovery eligibility, and redacted unauthorized handling. Purchase lifecycle behavior remains covered by the state-machine, processor, repository, and common application suites.

Verified successfully:

- F-Droid full-debug Kotlin compilation and complete unit suite.
- Play full-debug Kotlin compilation and complete unit suite, including the focused Play billing tests.
- Website full-debug Kotlin compilation and complete unit suite.
- Resource linking and manifest processing for all three variants as dependencies of those tasks.
- No unresolved merge paths and no `git diff --check` errors.

Play and website verification excluded only their Google Services processing tasks because this worktree intentionally has no local `google-services.json` credential/config file.

## Known tooling limitation

The Android shared target compiles and is exercised by every verified app flavor. The optional standalone `:shared:jvmTest` target does not currently resolve Room 3.0.0-rc01's `room3-sqlite-wrapper`, because that artifact publishes an Android AAR rather than a standard JVM variant. This is isolated to the demo JVM target and is not an Android application compile or test failure.
