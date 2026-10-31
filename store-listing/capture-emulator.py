"""Capture Keepout's store screens on the capture emulator (needs adb and Pillow).

`node store-listing/studio/recapture.cjs` builds and installs the capture build, runs this tour for every
device size and exports. The capture build shows the real UI over the Google apps that ship on the
emulator's Play image, with a seeded week of traffic; it has no VPN service and no INTERNET permission,
and never touches real data. Run alone: python3 store-listing/capture-emulator.py [--profile phone] [--locale de-DE].

The tour removes any leftover stub apps (fixture-apps.json), sets the clock to 9:41 so the seeded week of
traffic ends "now", and drives the app by its English labels. Taps are recorded in en-US/raw/tap-map.json
so other locales replay them. Writes <locale>/raw/<profile>/<scene>.png plus a .nodes.json sidecar.
The emulator plumbing is the studio's capture.py.
"""
import json
import sys
import time
from pathlib import Path

listing = Path(__file__).resolve().parent
sys.path.insert(0, str(listing / 'studio'))
from capture import Device, parser  # noqa: E402

args = parser(__doc__).parse_args()
# The Home beacon animates forever; with animations off uiautomator can read the screen.
d = Device(args, 'com.bernaferrari.bravedns.storecapture', 'com.bernaferrari.bravedns.ui.activity.LauncherAliasAppLock',
           animations=False)


def tap(label, wait=2.2, text_only=False, above=None):
    """above: only nodes starting in the top fraction of the screen (filter chips over log rows)."""
    d.tap(label, wait, text_only=text_only, attempts=5,
          within=None if above is None else lambda b: b[1] < d.height * above)


def launch():
    d.set_clock('09:41')  # matches the demo status bar; the app seeds its week of traffic up to now
    d.launch(clear=True, wait=0)
    # The first start lists the apps and seeds their rules and traffic.
    for _ in range(60):
        time.sleep(2)
        if 'storecapture-seeded' in d.app_files():
            break
    else:
        raise AssertionError('The capture build did not seed its fixtures')
    # Start again on the seeded data, so every screen reads it from the beginning.
    for _ in range(3):
        d.launch(wait=5)
        if d.package in d.top_activity():
            return
    raise AssertionError('The capture build did not come to the foreground')


def shot(name, animated=False):
    d.shot(name, settle=1.5, animated=animated)


def settings(screen, wait=2.5):
    """Open a Settings (Configure) entry from the Settings tab."""
    tab('Settings')
    tap(screen, wait)


def tab(label, wait=2.5):
    """A bottom-bar (or rail) tab: Home, Apps, Activity, Settings. Home's own "Activity" card also
    lands on the Activity tab, so whichever node matches first, the tour ends up in the same place."""
    tap(label, wait, text_only=True)


with d:
    # The screens show the real Google apps of the Play image; stub apps from earlier runs would mix in.
    for stub in json.loads((listing / 'fixture-apps.json').read_text()).get('apps', []):
        stub_package = stub.get('package') or stub.get('packageName')
        if stub_package and stub_package in d.shell('pm', 'list', 'packages', stub_package):
            d.adb('uninstall', stub_package)

    for profile in d.profiles():
        d.night(False)
        launch()
        shot('01-home', animated=True)

        # Stats is the Insights view of the Activity tab (Log · Insights switch in its top bar).
        tab('Activity')
        tap('Insights')
        tap('24h')
        shot('05-stats')
        d.scroll_to('Most blocked apps', .12)
        shot('06-blocked-apps')

        # The log is the Activity tab's other view. It opens on All: connections and DNS lookups as
        # one timeline. Type lives in the Filters sheet (All · Connections · DNS lookups); the
        # verdict group (All · Allowed · Blocked) shares the one control row with the Filters button.
        for _ in range(3):  # back to the top: the collapsed bar swaps the switch for the 1h/24h/7d tabs
            d.scroll(.6, up=True)
        tap('Log')
        shot('02-logs')
        tap('Filters', above=.3)
        tap('DNS lookups')
        tap('Close sheet', 1.8)  # tap the scrim: the "DNS lookups" chip now shows under the controls
        tap('Blocked', above=.35)
        shot('04-dns-blocked')

        tab('Apps')
        shot('03-apps')
        d.wait_for('Clock')
        tap('Clock', 7)
        shot('07-app-detail')
        d.back(1.8)

        tab('Settings')
        shot('08-settings')
        tap('DNS', 2.5)
        shot('09-dns')
        d.back(1.8)

        d.night(True)
        launch()
        shot('11-home-dark', animated=True)
        tab('Apps')
        shot('12-apps-dark')
        tab('Settings')
        shot('13-settings-dark')
