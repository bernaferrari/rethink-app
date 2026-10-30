# Release verification and manual signing

The security workflow builds a minified F-Droid release without signing secrets. It checks the
application ID, encoded version codes, launcher alias, VPN service permission, native ABI contents,
and 16 KB native-library alignment. CodeQL runs against the de-Googled Android build. Dependency
review rejects high/critical advisories; its resolved Gradle graph is submitted to GitHub only from
trusted pushes to `main`, with write permission confined to that job. No APK is sent to an external
scanning service.

Local unsigned verification requires JDK 17 and an Android SDK with Build Tools. Export
`ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to that SDK directory for the Python artifact tools.

```sh
./gradlew :app:assembleFdroidRelease -PVERSION_CODE=1 -PkeepoutArtifactsOnly=true
python3 scripts/verify-release-apks.py app/build/outputs/apk/fdroid/release --version-code 1
python3 -m unittest discover -s scripts/tests -v
```

Every release produces four ABI APKs (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) and a universal
APK. ABI version codes retain the established `multiplier * 10000000 + base` encoding; universal
uses the arm64 multiplier. The base must be a positive integer below 10000000. Keep the published
base monotonically increasing; the scripts do not query a store or publish anything.

The **Manual signed release artifacts** workflow is dispatch-only and accepts only the `main` ref.
Choose a channel, a positive base version code, and the expected signing certificate SHA-256 digest.
Provide these repository secrets at runtime:

- `RELEASE_KEYSTORE_BASE64`: base64 encoding of the release keystore.
- `RELEASE_KEY_ALIAS`: alias in that keystore.
- `RELEASE_STORE_PASSWORD` and `RELEASE_KEY_PASSWORD`: keystore and private-key passwords.
- `GOOGLE_SERVICES_RELEASE_JSON`: required only for Play/website channels.

Signing material is written to a private temporary directory and removed in `finally`. Passwords
are passed by environment-variable name, never in command arguments or log output. Gradle's build
environment excludes signing secrets. The supplied certificate is checked before building and again
on every signed APK. Signed artifacts and R8 mappings are uploaded as workflow artifacts for review;
the workflow never publishes a GitHub release, deploys a site, or uploads to an app store. The
`keepoutArtifactsOnly` Gradle property disables Crashlytics upload tasks for artifact-only builds.

For local signing, supply the same environment variables plus `RELEASE_CHANNEL`, `VERSION_CODE`, and
`EXPECTED_CERT_SHA256`, then run `python3 scripts/build-signed-release.py`. Use a clean generated
`app/build/outputs/signed-release/<channel>` directory; the script rejects stale APKs rather than
mixing releases. Never commit a keystore, its base64 encoding, or passwords.

# Translation maintenance

`python3 scripts/refresh-upstream-translations.py` previews changes against a locally fetched
`upstream/main`; add `--apply` to write them. Only resource keys with unchanged English contracts are
eligible. Non-translatable entries, wrong-brand text, unknown references, changed link targets and
incompatible format arguments are excluded. A second run should report no changed files. Translation
language quality still needs native-speaker review; matching the source contract does not certify
linguistic quality.
