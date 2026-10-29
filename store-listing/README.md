# Keepout · Play listing

Built with [Screenshot Studio](studio/). Read `~/Documents/screenshot-studio/PLAYBOOK.md` first.

| Change | File |
| --- | --- |
| Cards, order, captures | `gallery.json` |
| Words per language | `<locale>/copy.json`, `<locale>/listing.json` |
| Look | `theme.css`, `theme.js` |
| Canvas and capture sizes | `profiles.json` |
| Seeded apps and traffic | `app/src/storeCapture/.../StoreFixtures.kt` |

```sh
./gradlew :app:installFdroidStoreCapture
export PLAYWRIGHT_MODULE=$HOME/.cache/weakspot-shots/node_modules/playwright   # any Playwright install
python3 store-listing/capture-emulator.py --serial emulator-5572   # needs Pillow; your own AVD and port
node store-listing/studio/server.cjs 8790        # editor
node store-listing/studio/export.cjs --locale en-US
```

The capture build (`app/src/storeCapture`) shows the real UI over the Google apps that ship on the emulator's
Play image (Chrome, Gmail, YouTube, Maps, Clock, ...), with rules and a week of traffic seeded against their real
UIDs and the hosts they really use. The VPN state is pinned to "protected"; the build has no VPN service and no
INTERNET permission. Nothing is downloaded or installed for the screenshots: the script only removes the stub
apps an older run may have left (`fixture-apps.json`). It sets the emulator clock to 9:41 so the seeded traffic
ends at the status-bar time, captures Home with its real animation running, and restores the clock, animations
and display size afterwards.
