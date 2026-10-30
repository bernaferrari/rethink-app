# Firestack protocol pairing

Keepout pairs `com.celzero:firestack:d3d530ea3e` with the Kotlin bridge in this tree.
The previous artifact was `310d7bc603`. Change the artifact and bridge together.
Do not independently copy callback signatures from another Firestack version.

The Java APIs were compared directly with `javap` against the cached AAR classes,
and checked against the corresponding Firestack Go source revisions.

| API | 310d7bc603 | d3d530ea3e |
| --- | --- | --- |
| `Bridge.flow` | Eight parameters | Adds final `boolean isAlg`; returns `Mark` synchronously |
| DNS prequery | Absent | `onPrequery(origin, uid, fqdn, qtype): DomainOpts` |
| `Router.contains` | Address | Address and UID |
| `Rpn.registerWin` | Bytes, DID, options | Verified entitlement bytes, saved state bytes, DID, options |
| `RpnProxy.entitlement` | Existing entitlement access | Used to verify active registration freshness |
| `DNSOpts` | Separate `PIDCSV` | Proxy candidates belong to each transport in `TIDCSV`/`TIDSECCSV`; `UID` is explicit |
| `Settings.setPlusStrategy` | One combined strategy | Separate order and filtering strategy |
| No-translation constant | `PtModeNo46 = 2` | `PtModeNone = 2`, same meaning |

The callback return contract is unchanged: `flow`, `preflow`, `inflow`, `onQuery`,
and `onUpstreamAnswer` return routing decisions synchronously. `bind4`, `bind6`,
and `protect` complete their fd side effects before Go proceeds. Keep `go2kt`
synchronous; converting it to launch-and-return can silently permit direct routing.
`isAlg` now makes multi-address answer evaluation explicit instead of treating a
comma-separated answer set as a single address. Routing still receives one address.

DNS transport routing is documented in Firestack
`intra/backend/dnsx_listener.go` and parsed by
`intra/dnsx/transport.go:parseAllTidOpts` / `parseTidOpt`:

```
<transport>,<transport:proxy>,<transport:proxy1:proxy2>
```

A transport with no proxy suffix is valid. Empty secondary transports stay empty.
A user-proxy DNS transport must not recursively use another user proxy. The local
`DnsTransportRouting` adapter owns this encoding and has regression tests.

`onPrequery` currently returns empty DomainOpts, retaining the native default
ranking policy. It must not introduce a second asynchronous firewall verdict path.

The native range also includes bootstrap DNS deadlock repair, serialized proxy
notifications, DNSCrypt missing-transport handling, NAT64/ALG fixes, and WireGuard
re-resolution and handshake changes. The full native range changes 125 paths;
this port does not claim every native path has been independently audited.

Reachability is measured per active proxy and family. Overlay capabilities are the
union of reachable families; one proxy without IPv6 does not disable IPv6 for all
others. Failure does not introduce a direct fallback for a full-tunnel WireGuard
session. `NativeProbeRunner` owns a bounded worker pool because Kotlin coroutine
cancellation cannot interrupt JNI. Waits are bounded, repeated active probes are
coalesced, adapter close rejects late results, and generation changes discard
results from replaced proxy configurations. Native work may finish after a wait
has expired; it stays bounded and owned, rather than creating detached IO jobs.

Validation includes Kotlin compilation against the pinned artifact, reflection
contract tests, routing/DNS policy tests, and shared JVM serialization tests.
These checks do not prove native device behavior. VPN start/stop/rebuild, full
and split WireGuard routes, network switching, DNS transport selection, RPN
registration, and capture drain require device smoke tests before release.
