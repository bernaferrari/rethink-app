"""Capture Re-Rethink's store screens on a dedicated emulator (needs adb, Node with Playwright, and Pillow).

Install the capture build first. It shows the real UI over the Google apps that ship on the
emulator's Play image, with a seeded week of traffic; it has no VPN service and no INTERNET
permission, and never touches real data:

    ./gradlew :app:installFdroidStoreCapture
    python3 store-listing/capture-emulator.py --serial emulator-5572

The script removes any leftover stub apps (fixture-apps.json), sets the clock to 9:41 so the
seeded week of traffic ends "now", and drives the app by its English labels. Taps are recorded in en-US/raw/tap-map.json so other locales
replay them (--locale de-DE). Writes <locale>/raw/<profile>/<scene>.png plus a .nodes.json sidecar.
Use your own AVD and port; never a personal phone.
"""
import argparse
import datetime
import json
import os
import re
import subprocess
import sys
import time
from io import BytesIO
from pathlib import Path

from PIL import Image

parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
parser.add_argument('--serial', required=True, help='Dedicated emulator serial; never a physical phone')
parser.add_argument('--profile', choices=['phone', 'tablet7', 'tablet10', 'all'], default='all')
parser.add_argument('--locale', help='Play locale to capture in, e.g. de-DE (default: the studio default locale)')
parser.add_argument('--only', help='Comma-separated scene names to capture (default: all)')
args = parser.parse_args()
assert args.serial.startswith('emulator-'), 'Use a dedicated emulator, not a personal device.'

listing = Path(__file__).resolve().parent
sys.path.insert(0, str(listing / 'studio'))
from uitree import Navigator, set_app_locale, write_sidecar  # noqa: E402

studio = json.loads((listing / 'studio.json').read_text())
reference = studio.get('defaultLocale', 'en-US')
locale = args.locale or reference
only = set(args.only.split(',')) if args.only else None
navigator = Navigator(listing / reference / 'raw' / 'tap-map.json', recording=locale == reference)
raw = listing / locale / 'raw'
package = 'com.bernaferrari.bravedns.storecapture'
activity = package + '/com.bernaferrari.bravedns.ui.activity.LauncherAliasAppLock'


def adb(*parts):
    return subprocess.check_output(['adb', '-s', args.serial, *parts], stderr=subprocess.STDOUT)


def shell(*parts):
    return adb('shell', *parts).decode()


def broadcast(*extra):
    adb('shell', 'am', 'broadcast', '-a', 'com.android.systemui.demo', '--es', 'command', *extra)


last_xml = None


def dump():
    global last_xml
    for attempt in range(6):
        try:
            adb('shell', 'uiautomator', 'dump', '--compressed', '/sdcard/rethink-capture.xml')
            last_xml = shell('cat', '/sdcard/rethink-capture.xml')
            # A busy emulator can raise "isn't responding" over the app; keep waiting for it.
            wait = re.search(r'resource-id="android:id/aerr_wait"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', last_xml)
            if wait:
                x1, y1, x2, y2 = map(int, wait.groups())
                adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(3)
                continue
            if f'package="{package}"' in last_xml:
                return last_xml
        except subprocess.CalledProcessError:
            pass
        time.sleep(1.5)
    raise AssertionError('The Re-Rethink capture build is not in the foreground')


def find(label, text_only=False, above=None):
    """above: only nodes starting in the top fraction of the screen (filter chips over log rows)."""
    pattern = re.compile(label)
    for attempt in range(5):
        dump()
        try:
            return navigator.resolve(f'{profile}|{theme}|{pattern.pattern}', last_xml,
                                     lambda n: (above is None or n['b'][1] < height * above) and
                                     bool(pattern.fullmatch(n.get('text', '')) or
                                          (not text_only and pattern.fullmatch(n.get('desc', '')))))
        except AssertionError:
            if attempt == 4:
                raise
            time.sleep(1.2)


def tap(label, wait=2.2, text_only=False, above=None):
    x1, y1, x2, y2 = find(label, text_only, above)['b']
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(wait)


def back(wait=1.8):
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(wait)


def drag(distance):
    """A slow drag scrolls exactly `distance` pixels (no fling)."""
    x = width // 2
    start = int(height * .8)
    adb('shell', 'input', 'swipe', str(x), str(start), str(x), str(start - distance), '1400')
    time.sleep(1.2)


def scroll_to(label, top):
    """Scroll until the element with text `label` sits near `top` (a fraction of the screen height)."""
    for _ in range(12):
        dump()
        node = next((n for n in re.finditer(r'<node [^>]*>', last_xml)
                     if f'text="{label}"' in n.group(0)), None)
        if node:
            y = int(re.search(r'bounds="\[\d+,(\d+)\]', node.group(0)).group(1))
            if y <= height * top + 40:
                drag(int(y - height * top)) if y > height * top + 8 else None
                return
            drag(min(int(y - height * top), int(height * .55)))
        else:
            drag(int(height * .5))
    raise AssertionError(f'Could not scroll to {label}')


def animations(on):
    for key in scales:
        adb('shell', 'settings', 'put', 'global', key, '1' if on else '0')


def capture(name, animated=False):
    """animated: read the layout with animations off, then let the real motion run and grab a frame."""
    if only and name not in only:
        return
    time.sleep(1.5)
    xml = dump()
    if animated:
        animations(True)
        time.sleep(4.5)
    png = adb('exec-out', 'screencap', '-p')
    if animated:
        animations(False)
    png = png[png.index(b'\x89PNG\r\n\x1a\n'):]  # some emulators print a display warning first
    image = Image.open(BytesIO(png)).convert('RGB')
    assert image.size == (width, height), image.size
    path = raw / profile / f'{name}.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)
    write_sidecar(path, xml, width, height)
    print(path.relative_to(listing), flush=True)


def set_clock():
    """9:41 today, matching the demo status bar; the app seeds its week of traffic up to now."""
    nine41 = datetime.datetime.now().replace(hour=9, minute=41, second=0, microsecond=0)
    adb('shell', 'cmd', 'alarm', 'set-time', str(int(nine41.timestamp() * 1000)))


def launch():
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'pm', 'clear', package)
    set_app_locale(adb, package, None if locale == 'en-US' and reference == 'en-US' else locale)
    set_clock()
    adb('shell', 'am', 'start', '-W', '-n', activity)
    # The first start lists the fixture apps and seeds their rules and traffic.
    for _ in range(60):
        time.sleep(2)
        if 'storecapture-seeded' in shell('run-as', package, 'ls', 'files'):
            break
    else:
        raise AssertionError('The capture build did not seed its fixtures')
    # Start again on the seeded data, so every screen reads it from the beginning.
    adb('shell', 'am', 'force-stop', package)
    for _ in range(3):
        adb('shell', 'am', 'start', '-W', '-n', activity)
        time.sleep(5)
        if package in shell('dumpsys', 'activity', 'activities').split('topResumedActivity=', 1)[-1][:200]:
            return
    raise AssertionError('The capture build did not come to the foreground')


def status_bar():
    broadcast('exit')
    for extra in [('enter',), ('clock', '--es', 'hhmm', '0941'),
                  ('battery', '--es', 'level', '100', '--es', 'plugged', 'false'),
                  ('notifications', '--es', 'visible', 'false'),
                  ('network', '--es', 'wifi', 'show', '--es', 'level', '4', '--es', 'fully', 'true',
                   '--es', 'mobile', 'hide', '--es', 'sims', '0', '--es', 'airplane', 'hide')]:
        broadcast(*extra)


def night(on):
    adb('shell', 'cmd', 'uimode', 'night', 'yes' if on else 'no')
    time.sleep(2)


def settings(screen, wait=2.5):
    """Open a Settings (Configure) entry from the Settings tab."""
    tap('Settings', text_only=True)
    tap(screen, wait)


# The screens show the real Google apps of the Play image; stub apps from earlier runs would mix in.
for stub in json.loads((listing / 'fixture-apps.json').read_text()).get('apps', []):
    stub_package = stub.get('package') or stub.get('packageName')
    if stub_package and stub_package in shell('pm', 'list', 'packages', stub_package):
        adb('uninstall', stub_package)

profiles = {name: (p['captureWidth'], p['captureHeight'], p['captureDensity'])
            for name, p in json.loads((listing / 'profiles.json').read_text()).items() if 'captureWidth' in p}
original = shell('wm', 'size') + shell('wm', 'density')
assert 'Override' not in original, 'Reset wm size/density first (an interrupted run leaves overrides).'
heads_up = shell('settings', 'get', 'global', 'heads_up_notifications_enabled').strip()
auto_time = shell('settings', 'get', 'global', 'auto_time').strip()
scales = {key: shell('settings', 'get', 'global', key).strip()
          for key in ('animator_duration_scale', 'transition_animation_scale', 'window_animation_scale')}
profile = theme = None
width = height = 0
try:
    adb('shell', 'cmd', 'overlay', 'enable-exclusive', '--category', 'com.android.internal.systemui.navbar.gestural')
    adb('shell', 'settings', 'put', 'global', 'sysui_demo_allowed', '1')
    adb('shell', 'settings', 'put', 'global', 'heads_up_notifications_enabled', '0')
    adb('shell', 'settings', 'put', 'global', 'auto_time', '0')
    # The Home beacon animates forever; without animations uiautomator can read the screen.
    for key in scales:
        adb('shell', 'settings', 'put', 'global', key, '0')
    adb('shell', 'svc', 'wifi', 'disable')
    adb('shell', 'svc', 'data', 'disable')
    for profile, (width, height, density) in profiles.items():
        if args.profile not in ('all', profile):
            continue
        adb('shell', 'wm', 'size', f'{width}x{height}')
        adb('shell', 'wm', 'density', str(density))
        time.sleep(2)
        status_bar()
        theme = 'light'
        night(False)
        launch()
        capture('01-home', animated=True)

        tap('Stats', text_only=True)
        tap('24h')
        capture('05-stats')
        scroll_to('Most blocked apps', .12)
        capture('06-blocked-apps')

        settings('Logs')
        capture('02-logs')
        tap('DNS', above=.2)
        tap('Blocked', above=.25)
        capture('04-dns-blocked')
        back()

        settings('Apps')
        capture('03-apps')
        tap('Clock', 7)
        capture('07-app-detail')
        back()
        back()

        tap('Settings', text_only=True)
        capture('08-settings')
        tap('DNS', 2.5)
        capture('09-dns')
        back()

        theme = 'dark'
        night(True)
        launch()
        capture('11-home-dark', animated=True)
        settings('Apps')
        capture('12-apps-dark')
        back()
        capture('13-settings-dark')
        night(False)
finally:
    navigator.save()
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'cmd', 'uimode', 'night', 'no')
    adb('shell', 'wm', 'size', 'reset')
    adb('shell', 'wm', 'density', 'reset')
    broadcast('exit')
    adb('shell', 'settings', 'put', 'global', 'heads_up_notifications_enabled', heads_up if heads_up in ('0', '1') else '1')
    adb('shell', 'cmd', 'alarm', 'set-time', str(int(time.time() * 1000)))  # back to the host's clock
    adb('shell', 'settings', 'put', 'global', 'auto_time', auto_time if auto_time in ('0', '1') else '1')
    for key, value in scales.items():
        adb('shell', 'settings', 'put', 'global', key, value if value not in ('', 'null') else '1')
    adb('shell', 'svc', 'wifi', 'enable')
