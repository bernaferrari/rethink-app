# Re-Rethink · Play listing

Built with [Screenshot Studio](studio/). Read `~/Documents/screenshot-studio/PLAYBOOK.md` first.

| Change | File |
| --- | --- |
| Cards, order, captures | `gallery.json` |
| Words per language | `<locale>/copy.json`, `<locale>/listing.json` |
| Look | `theme.css`, `theme.js` |
| Canvas and capture sizes | `profiles.json` |
| Fictional apps (names, icons) | `fixture-apps.json` |

```sh
./gradlew :app:installFdroidStoreCapture
export PLAYWRIGHT_MODULE=$HOME/.cache/weakspot-shots/node_modules/playwright   # any Playwright install
python3 store-listing/capture-emulator.py --serial emulator-5572   # needs Pillow; your own AVD and port
node store-listing/studio/server.cjs 8790        # editor
node store-listing/studio/export.cjs --locale en-US
```

The capture build (`app/src/storeCapture`) shows the real UI over fictional apps, rules and a week of traffic,
with the VPN state pinned to "protected". It has no VPN service and no INTERNET permission. The apps themselves
are code-free APKs that `studio/fixture-apps.cjs` builds from `fixture-apps.json` (git-ignored in
`.fixture-apps/`) and the capture script installs. The script sets the emulator clock to 9:41 so the seeded
traffic ends at the status-bar time, and restores the clock, animations and display size afterwards.
