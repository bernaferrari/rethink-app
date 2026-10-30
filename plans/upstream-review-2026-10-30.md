# Upstream comparison — 30 October 2026

Recommendation: selectively port behavioral fixes. Do not merge or replace Keepout's architecture with upstream files.

## Comparison and coverage

- Upstream: celzero/rethink-app `07fe5373f` (25 October 2026), fetched during this review.
- Fork at start: `3880974d7`; concurrent work advanced HEAD to `c73f30763` during review. Current working files were consulted; no existing edits were altered.
- Common base: `553062454b8b29084e67942f0abfcd74c86caac7`.
- At start: 126 fork-only and 1,115 upstream-only commits, including upstream merge commits. There are 1,005 non-merge upstream-only commits.
- Upstream changes since base: 1,038 paths: 566 application-code paths, 93 test paths, 325 resource paths, 23 store-metadata paths, 31 build/docs/other paths. Endpoint comparison is much larger (2,568 paths), largely because Keepout changed packages, source layouts, UI, and database architecture.
- Upstream contains 673 non-test Kotlin source files: 326 have a filename counterpart locally, 302 were removed/replaced here, and 45 belong to TV. Same filename is only an inventory aid, not semantic equivalence.
- Full commit, upstream-since-base changed-path, and upstream-to-HEAD endpoint changed-path inventories accompany this report. The endpoint inventory excludes uncommitted user edits; relevant working files were read separately. History was screened across networking, firewall, DNS, WireGuard, RPN, billing, storage, backup, notifications, permissions, UI, product features, translations, build and CI. Significant behavioral differences were checked against final upstream and local code, rather than trusting commit titles.
- This is a broad source/history comparison, **not a line-by-line certification of every difference**. Native Firestack source history, every translation, every TV screen/artwork/XML change, and live billing/device behavior were not exhaustively reviewed. No builds or device tests were run for this read-only review.
- Only this report and inventories were created. No source changes, merge, cherry-pick, commit, push, dependency update, or remote branch mutation occurred.

Commit links below refer to https://github.com/celzero/rethink-app/commit/<hash>. Local paths are relative to the repository root. Effort includes focused regression tests: S = hours, M = approximately a day, L = multiple days.

## Recommended fixes, in priority order

| # | Port | Local evidence and reason | Upstream commits | Effort / risk / confidence |
|---|---|---|---|---|
| 1 | Restrict One-WG fallback to split tunnels | `app/src/main/kotlin/com/bernaferrari/bravedns/service/WgHopSelector.kt:143` unconditionally appends the default route. Upstream WireguardManager:609 only appends it when split-proxy status permits it. Full-tunnel failure must not silently permit direct fallback. | [2a0c9e04e](https://github.com/celzero/rethink-app/commit/2a0c9e04e) | M / high routing risk / high missing-code confidence; verify native behavior |
| 2 | Fix identity lock reentry and bind device identity to account | `app/src/billing/kotlin/com/bernaferrari/bravedns/iab/BillingBackendClient.kt:114` locks identityMutex, then missing-DID branch:139 calls refreshIdentity, which locks it again:264. Also failed account reconciliation falls through to the previous stored DID:134. Upstream uses explicit locked helpers and CID-bound didForCid. | [7ab6facc9](https://github.com/celzero/rethink-app/commit/7ab6facc9) | M / medium / high |
| 3 | Reject malformed 2xx billing responses | `app/src/billing/kotlin/com/bernaferrari/bravedns/iab/RpnPurchaseAckServerResponse.kt:57` converts invalid or absent JSON to an empty object:74 and returns Ok:68. Upstream requires an actual JSON object. Preserve kotlinx.serialization and wire-field names. | [cd88e02f2](https://github.com/celzero/rethink-app/commit/cd88e02f2) | S / low / high |
| 4 | Validate IP rules before the CIDR-only native trie | `app/src/main/kotlin/com/bernaferrari/bravedns/service/IpRulesManager.kt:178` handles only wildcard conversion; other ranges pass through:180. Failed normalization becomes an empty address:570, which means a port-only rule:574. Upstream converts representable ranges, avoids sending unsupported ranges to JNI, and preserves original text. Adapt UI to make unenforceable rules explicit rather than silently accepting ineffective protection. | [1295825e3](https://github.com/celzero/rethink-app/commit/1295825e3), [39d659af5](https://github.com/celzero/rethink-app/commit/39d659af5) | M / medium / high |
| 5 | Correct IP-rule cache keys | `app/src/main/kotlin/com/bernaferrari/bravedns/service/IpRulesManager.kt:252` concatenates normalized address and port before calling joinIpNetPort. Upstream passes address and port separately, preserving their boundary and avoiding needless normalization before cache hits. | [91473f5b7](https://github.com/celzero/rethink-app/commit/91473f5b7) | S / low / high |
| 6 | Require unlock for sensitive notification actions | `app/src/main/kotlin/com/bernaferrari/bravedns/database/RefreshDatabase.kt:865` constructs allow/deny actions directly; similar clear/disable actions exist in RefreshDatabase:270 and BubbleHelper:268/:386. Upstream marks sensitive actions authenticationRequired on Android 12+. | [62eac970e](https://github.com/celzero/rethink-app/commit/62eac970e), [a6f7c6619](https://github.com/celzero/rethink-app/commit/a6f7c6619) | S / low / high |
| 7 | Separate notification PendingIntent identities | `app/src/main/kotlin/com/bernaferrari/bravedns/util/Utilities.kt:606` and :609 always use request code 0. Billing alerts target the same activity and differ only in extras; UPDATE_CURRENT can replace another alert's payload. Add caller-selected stable identities. | [d240ca693](https://github.com/celzero/rethink-app/commit/d240ca693) | S / low / high |
| 8 | Keep notification broadcast work alive | `app/src/main/kotlin/com/bernaferrari/bravedns/receiver/NotificationActionReceiver.kt:168` launches an unowned IO scope after onReceive. Adapt upstream goAsync/finally-finish with application scope so firewall/database work has the receiver's execution lifetime. | [90429935f](https://github.com/celzero/rethink-app/commit/90429935f) | S / low / high |
| 9 | Correct SSID automation permission and network selection | `app/src/main/kotlin/com/bernaferrari/bravedns/util/SsidPermissionManager.kt:63` checks foreground access only; `ui/compose/wireguard/WgConfigDetailScreen.kt:305` uses it to enable background automation. Separately `net/go/GoVpnAdapter.kt:874` and :952 allow SSID resume while cellular is active. Port separate foreground/background permission flow and selected-underlay checks. | [95797ed41](https://github.com/celzero/rethink-app/commit/95797ed41), [843f4d833](https://github.com/celzero/rethink-app/commit/843f4d833), [cb05b4620](https://github.com/celzero/rethink-app/commit/cb05b4620), [c8a119de6](https://github.com/celzero/rethink-app/commit/c8a119de6) | M / medium / high difference confidence |
| 10 | Refresh stale RPN registration with verified entitlement bytes | `app/src/main/kotlin/com/bernaferrari/bravedns/rpnproxy/RpnProxyManager.kt:898` prefers saved state, and :979 skips registered tunnels without entitlement freshness checks. Upstream separates saved state from current server entitlement and validates refreshed registrations. Port the final coherent chain, including 07fe5373f's correction, after identity tests. | [636bbf97a](https://github.com/celzero/rethink-app/commit/636bbf97a), [68a65f3a7](https://github.com/celzero/rethink-app/commit/68a65f3a7), [54df2cfe4](https://github.com/celzero/rethink-app/commit/54df2cfe4), [07fe5373f](https://github.com/celzero/rethink-app/commit/07fe5373f) | L / high native/server coupling / high |
| 11 | Scope purchase changes to the relevant token | `app/src/main/kotlin/com/bernaferrari/bravedns/rpnproxy/SubscriptionStateMachineV2.kt:391` exposes parameterless cancellation/expiry/revocation. Upstream processForToken avoids delayed responses changing a newer current purchase. Preserve Keepout's simpler state machine and lock ordering. | [7ab6facc9](https://github.com/celzero/rethink-app/commit/7ab6facc9) | M–L / medium-high / high difference, medium incident confidence |
| 12 | Serialize one WG endpoint and delegate DNS resolution to Go | `shared/src/commonMain/kotlin/com/bernaferrari/bravedns/wireguard/Peer.kt:86` resolves synchronously and :87 can emit a second endpoint. Upstream emits the original endpoint once; avoid platform DNS work during serialization. Update Android/JVM/Wasm callers together. | [46496a1d5](https://github.com/celzero/rethink-app/commit/46496a1d5) | M / medium / high |
| 13 | Supply userspace-only fallback DNS for WG without DNS | `shared/src/commonMain/kotlin/com/bernaferrari/bravedns/wireguard/WgInterface.kt:138` emits empty dns. Upstream supplies 1.1.1.1 only in userspace serialization. Decide resolver policy explicitly; preserve configured DNS and exported configuration. | [07a92d175](https://github.com/celzero/rethink-app/commit/07a92d175), [64b59c9eb](https://github.com/celzero/rethink-app/commit/64b59c9eb) | S / low-medium policy risk / high |
| 14 | Distinguish partial network block from fully blocked UID | `app/src/main/kotlin/com/bernaferrari/bravedns/service/FirewallManager.kt:333` treats any non-ALLOW status as fully firewalled. Upstream tests BOTH; audit new-app waiting behavior at BraveVPNService:714 before adapting. | [86150d371](https://github.com/celzero/rethink-app/commit/86150d371) | S / low-medium / high |
| 15 | Normalize preflow UID consistently with flow | `app/src/main/kotlin/com/bernaferrari/bravedns/service/BraveVPNService.kt:2478` returns raw UID; normal flow normalizes appId. Upstream normalizes preflow too, for secondary-user/cloned-app consistency. Keep callbacks synchronous and order-stable. | [023508457](https://github.com/celzero/rethink-app/commit/023508457) | S / medium bridge risk / high |
| 16 | Block third-party overlays over sensitive activity windows | No setHideOverlayWindows or HIDE_OVERLAY_WINDOWS locally. Port API31+ window guard and permission to Compose activity boundaries; log actionable SecurityException and consider overlay interoperability. | [62eac970e](https://github.com/celzero/rethink-app/commit/62eac970e), [aa3b4d838](https://github.com/celzero/rethink-app/commit/aa3b4d838) | S / low-medium / high |
| 17 | Correct version-code handling for every APK output | `app/build.gradle.kts:485` selects singleOrNull among ABI outputs; normal multi-ABI output has several, so none are adjusted, and universal output is not handled. Upstream iterates outputs and gives universal an explicit policy. Adapt arithmetic to Keepout's own release history; do not import Rethink's version numbers. | [55c6bdc42](https://github.com/celzero/rethink-app/commit/55c6bdc42) plus final upstream androidComponents | S / medium distribution risk / high code difference |

## Valuable but larger or policy-dependent work

- **Device-bound restore state** — upstream [0b5f6e3a5](https://github.com/celzero/rethink-app/commit/0b5f6e3a5), [232e32bd2](https://github.com/celzero/rethink-app/commit/232e32bd2) resets imported subscription/history and excludes crypto keyset preferences. Local RestoreAgent:117–122 installs imported state wholesale. Adapt reset/reconciliation separately for Play/website; keep atomic rollback and schema identity checks. M, medium risk; difference confirmed, exact per-channel policy needs design.
- **Post-answer DNS firewall evaluation** — local BraveVPNService:2256 returns empty DNSOpts. Upstream TunDnsManager:935 evaluates answer IPs through firewall decisions and handles unsafe early verdicts when port rules exist. Reference [040955374](https://github.com/celzero/rethink-app/commit/040955374), [3ba113848](https://github.com/celzero/rethink-app/commit/3ba113848) and later fixes. L, high risk; feature/behavior port, not a one-line callback replacement.
- **Actual proxy-family reachability** — local GoVpnAdapter:1860 trusts router IP-family flags. Upstream [b5a792035](https://github.com/celzero/rethink-app/commit/b5a792035), [b89a62dff](https://github.com/celzero/rethink-app/commit/b89a62dff), [2b54e98da](https://github.com/celzero/rethink-app/commit/2b54e98da) probes each family and bounds blocking JNI work. Review aggregation, fail-open decisions, cancellation and detached work rather than copying exact implementation. L, high risk.
- **Lockdown TUN rebuild ordering** — local BraveVPNService:1712 unlinks inside adapter update after a new fd exists; upstream [23fabbb77](https://github.com/celzero/rethink-app/commit/23fabbb77) awaits unlink before establishing replacement. M, high native lifecycle risk; device testing required.
- **WG duplicate configuration guard** — upstream [d96e33dc6](https://github.com/celzero/rethink-app/commit/d96e33dc6) checks shared private key plus overlapping peer keys before enabling duplicate imports. Local WireguardManager:157 enables without it. M, medium risk; do not universally prohibit all key reuse.
- **Captive-portal DNS mode** — [935b6f902](https://github.com/celzero/rethink-app/commit/935b6f902), [bee885cfe](https://github.com/celzero/rethink-app/commit/bee885cfe). Temporarily use system DNS on the active captive network; restore selected DNS afterward. Local NetworkProbeController:149 recognizes captive state without this routing policy. Useful opt-in hotel/airport behavior; M–L, medium-high privacy/filtering risk.
- **Better crash-stack reconstruction** — local EnhancedBugReport:313 reports synthetic exceptions from the reporting callsite. Upstream [0477aa62a](https://github.com/celzero/rethink-app/commit/0477aa62a), [c93bd8329](https://github.com/celzero/rethink-app/commit/c93bd8329), [9062e7532](https://github.com/celzero/rethink-app/commit/9062e7532) parses original Go/JVM stacks and preserves fallback when native crash-output setup fails. M, medium API coupling; retain reporting consent.
- **Non-disruptive database backup** — [232e32bd2](https://github.com/celzero/rethink-app/commit/232e32bd2) uses VACUUM INTO; local BackupAgent:87 stops protection and checkpoints/copies. M, medium risk: need a consistent Room3 snapshot policy across both DBs; do not copy live checkpoint/copy fallback without writer coordination.

## Product additions worth considering separately

These are options, not defects or requirements to reach upstream parity.

| Feature | Value and adaptation | Reference | Effort / risk |
|---|---|---|---|
| Configurable log retention | Strong privacy/storage value. Upstream offers 1h through 7d; local PurgeConnectionLogs:36 fixes seven days. Preserve transactional aggregate maintenance. | 4017493cd, 22e60a6d8, f82b8fdcf and follow-ups | M / medium |
| Per-app notes | Remember why an app was trusted/excluded. Current shared AppInfo has no notes. Port save-failure handling and draft persistence; re-emit v1 schema without migrations. | 9d1a765d6, 93409699a, 3ddc49f63 | M / medium |
| Package/UID sorting | Useful technical inspection. Current alphabetical sections and fast-scroller must change coherently. | df8c374ac, 67002baa1, 85c05eb6c | M / medium |
| PCAP file management and bounded capture | Share/delete captures and auto-stop around three hours. Core plumbing exists locally but there is no normal UI setter path. Preserve stop/drain guarantees. | 66730ecbd, 5b4af9134, d54f618a5 | M–L / medium |
| Rule-file import with preview/limits | Good parser/tests reference, including 10k-entry and 4096-character-line bounds. Upstream marks this debug-only; not a production parity defect. | 5581fd69c, 70bb0ce98, e65626905 | M / medium |
| Translation refresh | Upstream changed 55 localized strings files and has ten extra locale directories. Copy only keys whose English meaning still matches Keepout, not obsolete Views strings or product wording. Locale config generation already exists locally. Language correctness not reviewed. | Weblate history in commit inventory | M / low-medium |
| Android TV | Upstream has 45 Kotlin files plus launcher, D-pad focus and separate navigation. Optional separate product effort; reuse ideas rather than merging flavor/UI wholesale. | ea2cd8df7, 4980f755e, 59d2c9ce1, 7c4f29c98 | L / medium-high |

Highest-value product options: log retention, per-app notes, and capture management. Each has an existing upstream implementation to use as behavioral reference without restoring the old UI.

## Already covered, superseded, or rejected

- Temporary allowances already expire and close established connections through serialized TemporaryAppAllowances. Do not re-port 7896efec1/ada446146 blindly.
- Keep local synchronous startForeground, start gate, owned teardown, pending-log drain, transactional stats, and monotonic PauseCountdown. Upstream service/lifecycle changes are not wholesale replacements.
- Keep staged restore rollback, Room identity validation, awaited preference replacement and restart marker. Upstream does not supersede those safeguards.
- allowBypass is already connected through VpnTunAppPolicy and VpnPrefObservers. Upstream's Play-default enablement is a product choice, not a required fix.
- Current DNS details already include multiple response IPs. Upstream c5d458122 is covered behaviorally.
- Compose edge-to-edge/layout/navigation/icon work supersedes most XML insets, RecyclerView lifecycle and Glide changes. Keep Material 3 Expressive/KMP modules and the single route hierarchy.
- Keep kotlinx.serialization/Ktor/Coil. Upstream's Gson/Retrofit/OkHttp/Guava-specific keep rules, including 9e122556b's OkHttp IDN workaround, should not be copied for libraries not directly used here; audit a resolved release graph before adding transitive-library rules.
- Gradle Kotlin DSL, version catalogue, SDK37, built-in Kotlin support and modern dependencies are already present; most upstream build migration work is redundant. Repository centralization is optional cleanup, not a missing runtime fix.
- Upstream CodeQL/APK security scanning and signed-release pipelines are useful references if desired. Adapt to Play/Fdroid/Website variants and least-privilege permissions; do not copy upstream secrets names or obsolete Full/TV task names.
- Do not copy **51f12c1e2 external broadcast sender-extra fallback**. It weakens Keepout's intentional OS-identity requirement.
- Do not copy Room migration history: this app is v0, schemas are version1, no upgrade archaeology.
- Do not mechanically copy df702a42d crash-file pruning: final upstream retains an unconditional empty-file delete after its active-file-aware delete, defeating its own guard.
- Do not copy interim forced RPN IPv6-off behavior: later upstream probing supersedes it.
- Do not silently ignore unknown WG settings merely because 17f1d0221 does; define supported/ignored policy and communicate unsupported settings.
- Do not import debug SOCKS/HTTP servers, simulated-connection screens, frost themes, dolphin animations, artwork or Rethink branding as required fixes.
- Recent-RPN NOT LIKE query correction 6ac2ec9cc applies to queries absent here; remember it if implementing recent-RPN exclusion UI, rather than adding unused queries.
- Seed database v31 cannot replace Keepout's Room3 v1 schema. Refresh blocklist metadata by stable IDs and current schema rather than copying upstream database assets.

## Native dependency and execution order

Local Firestack is pinned to `310d7bc603`; upstream uses `d3d530ea3e`. This requires a **separate native compatibility review**, not just editing gradle.properties. Upstream API history includes added and removed DNS-transport arguments. Final matching call shapes do not prove callback, routing or registration compatibility. Native source changes were not fully inventoried in this review.

Suggested sequence:

1. Characterize and fix malformed billing response handling, identity reentry, IP-rule parsing/cache keys, and notification behavior with narrow tests.
2. Port One-WG split/fallback behavior with routing tests and physical-device validation. Keep Go callback signatures/order stable.
3. Port SSID permission/selected-network behavior and WG serialization improvements with shared JVM and Android tests.
4. Add token-scoped billing mutation tests, then integrate refreshed RPN entitlement lifecycle after checking required native APIs.
5. Review native upgrade, TUN rebuild ordering, DNS post-answer policy and proxy reachability as distinct high-risk tasks.
6. Choose optional product additions independently.

Verification for subsequent implementation: `./gradlew :app:compilePlayDebugKotlin :app:testPlayDebugUnitTest :app:detekt :app:lintPlayDebug`; then `:app:compileFdroidDebugKotlin :app:compileWebsiteDebugKotlin :app:lintFdroidDebug :app:lintWebsiteDebug :shared:compileKotlinWasmJs :shared:jvmTest`. Native routing/lifecycle changes also require Android device tests, not just Kotlin compilation.

This report is a selection guide, not approval to implement or a complete implementation plan for every item.

## Implementation and verification appendix — 2026-10-30

The comparison above is historical. The subsequently approved ports and options were adapted in isolated worktrees to Keepout's current architecture, rather than mechanically merging upstream.

Delivered scope includes the paired Firestack upgrade and network/firewall policies (One-WG fallback, IP/CIDR/port rules and cache invalidation, SSID handling, DNS answer policy, reachability, TUN ordering, WG import policy and captive DNS); billing identity/token/entitlement lifecycle; notification and service lifetime fixes; original crash-stack reporting; and consistent backups with atomic restore, device-state reset, bounded primitive preference encoding and coordinated file ownership. Approved options include retention, notes, technical sorting, bounded PCAP management, rule import, shared Android TV navigation, compatible translations/metadata, security workflows and manual signed-artifact generation.

Both databases remain fresh **v1**, with no migrations or upgrade archaeology. Public DNS defaults are seeded on `onCreate`; old database assets and opaque WG/TCP identities were not imported.

### Final local gates

| Gate | Result |
|---|---|
| Play unit tests | 712 passed; zero failures/errors/skips |
| Fdroid unit tests | 659 passed; zero failures/errors/skips |
| Website unit tests | 659 passed; zero failures/errors/skips |
| Shared JVM tests | 51 passed; zero failures/errors/skips |
| All three debug compiles/lints; app/shared Detekt; Wasm compile | Passed |
| Reviewed screenshots | All 17 verified; intentional current-main Expressive goldens refreshed after visual review, without threshold changes |
| Debt baselines | App 416 → 416; shared 289 → 281 |

Full-suite XML was archived before screenshot verification overwrote Play's test output. Final application-source validation used `df7e77e78`; subsequent CI configuration and this appendix do not change application source.

All three release variants passed real R8/minification with positive base version code 1 and configuration caching enabled. All **15 APKs** (four ABI splits plus universal per channel) passed identity/version/native-library and 16KB ELF/ZIP checks. The actual signing wrapper signed and verified five representative Fdroid APKs with a temporary dummy key, then removed its key material. No real signing key or publication was used. Workflow syntax checks and all 17 Python tests passed. A clean-worktree synthetic **debug-only** Google configuration made Play/Website debug plugin tasks pass; release tasks still rejected missing release configuration. No generated JSON or real credentials were committed; remote GitHub CI remains a separate check.

### Device evidence

The Android emulator passed **12 UI tests plus one production-default Bundled Room test**. Runtime checks demonstrated native DNS setup and controlled HTTP 200; per-port blocking of 19862 while 19863 remained allowed, with HTTP restored after deletion; and a PCAP containing one global header and 42 valid records through EOF, with no growth after Stop and appropriate share/delete ownership. Minified fresh startup/consent and cold saved-intent presentation also passed.

An actual 44,629-byte KPRF backup contained two SQLite images passing `integrity_check` at `user_version=1`. After correcting Room maintenance to use a writer outside transactions, the same archive restored through the UI with an active VPN. The exact final dummy-signed R8 APK also restored it successfully: worker success, launcher restart, 40 allowed/8 blocked apps recovered, then explicit Start returned Protected with controlled HTTP 200. This exercised the production driver through minification, beyond mocked storage tests.

### Standards review

Targeted source reviews, including the final maintenance and CI deltas, reported **zero remaining actionable findings**. These were scoped reviews, not exhaustive line-by-line certification or a complete audit of native implementation internals.

### Spec review

Targeted review against the approved ports/options reported **zero remaining actionable findings**. Current Compose/KMP structure, fresh-schema policy, billing wire format, guarded state transitions and synchronous native callback requirements were preserved; the evidence above covers the exercised scenarios.

Live paid RPN, physical WireGuard/captive-portal/TV behavior, and linguistic correctness of every locale remain **unverified**. Emulator D-pad coverage and resource-contract checks do not certify those physical or language scenarios.
