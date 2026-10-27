<p align="center">
  <img src="store-listing/brand/icon.png" width="88" alt="Re-Rethink icon">
</p>

<h1 align="center">Re-Rethink</h1>

<p align="center"><strong>Every app, on your terms.</strong></p>

<p align="center">A no-root firewall, encrypted DNS and VPN for Android. Block apps on Wi-Fi or mobile data, stop trackers before they connect, and see every connection your phone makes.</p>

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.bernaferrari.bravedns"><img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="80" alt="Get it on Google Play"></a>
</p>

<p align="center">
  <img src="store-listing/en-US/phone/phone-01-home.jpg" height="540" alt="Every app, on your terms: Home showing protection on, with today's connections and DNS queries">
  &nbsp;&nbsp;
  <img src="store-listing/en-US/phone/phone-02-logs.jpg" height="540" alt="Every connection, in plain sight: the network log with blocked ad and tracking domains">
</p>

## Your network, app by app

<table>
  <tr>
    <td width="25%"><img src="store-listing/en-US/phone/phone-03-apps.jpg" alt="Cut an app off with one tap: Wi-Fi and mobile data switches per app"></td>
    <td width="25%"><img src="store-listing/en-US/phone/phone-04-detail.jpg" alt="Your keyboard stays offline: a keyboard app blocked on both networks"></td>
    <td width="25%"><img src="store-listing/en-US/phone/phone-05-trackers.jpg" alt="Trackers stop at the DNS: blocked ad and analytics queries"></td>
    <td width="25%"><img src="store-listing/en-US/phone/phone-06-stats.jpg" alt="Who called, who got through: top allowed and blocked apps and countries"></td>
  </tr>
</table>

- **A firewall for every app.** Block Wi-Fi, mobile data or both with one tap, isolate an app, or add IP and domain rules. Universal rules cover apps in the background, with the screen off, or freshly installed.
- **Encrypted DNS that blocks trackers.** Use Rethink DNS with on-device blocklists, or your own resolver over DNS-over-HTTPS, DNS-over-TLS, DNSCrypt or ODoH.
- **Logs you can read.** Every connection and DNS query shows the app, the server, the country and whether it got through, with statistics for the hour, day or week.

## On a bigger screen

Statistics, logs and rules spread out on tablets and foldables, with a navigation rail beside them.

<p align="center">
  <img src="store-listing/en-US/tablet10/tablet10-01-stats.jpg" width="80%" alt="Re-Rethink statistics on a landscape tablet: most allowed and most blocked apps side by side">
</p>

## Private by design

Re-Rethink filters on the device through Android's `VpnService`. Connections go straight to where they were headed, unless you route them through a WireGuard peer or proxy of your own; DNS goes to the resolver you pick, Rethink DNS by default. Logs stay on the phone and can be turned off, and there are no ads. Android allows one VPN at a time, so Re-Rethink takes the place of any other VPN app.

Re-Rethink is a fork of [Rethink DNS + Firewall + VPN](https://github.com/celzero/rethink-app) by Celzero, redesigned with Jetpack Compose and Material 3 Expressive, and stays under the [Apache License 2.0](LICENSE). The tunnel runs on Celzero's [firestack](https://github.com/celzero/firestack).

<sub>Building from source or contributing? See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).</sub>
