# F-Droid submission notes

This repository is prepared so F-Droid can build the Android app from source
instead of publishing the GitHub Release APK directly.

## Current package

- Application ID: `kr.forestmate.app`
- Android source directory: `packaging/android`
- Current version: `1.0.3`
- Current version code: `4`
- Release tag: `android-v1.0.3`
- Expected unsigned APK: `packaging/android/app/build/outputs/apk/release/app-release-unsigned.apk`

## Inclusion fit

- License is declared as `Apache-2.0` in `LICENSE` and `.fdroid.yml`.
- Android release builds produce an unsigned APK that F-Droid can sign.
- The build does not require the private Bubblewrap keystore or GitHub Release
  APK.
- Android dependencies are Gradle/Maven dependencies, not checked-in binary
  libraries.
- The build uses `google()` and `mavenCentral()` repositories.
- No Firebase, Crashlytics, Google Play Services, AdMob, or tracking SDKs are
  included in the Android source.
- The app should be marked `NonFreeNet` because the default TWA opens the
  hosted ForestMate web/API service at `forestmate.onrender.com`. The web app
  and server are source-available in this repository and can be self-hosted.

## Local checks

From the repository root:

```bash
ruby -e "require 'yaml'; YAML.load_file('.fdroid.yml')"
rg -n "firebase|crashlytics|play-services|com\\.google\\.android\\.gms|com\\.google\\.firebase|admob|analytics|tracker|facebook|sentry" app server packaging/android --glob '!**/build/**' --glob '!**/.gradle/**'
```

From `packaging/android`:

```bash
./gradlew --no-daemon assembleRelease
```

The APK should appear at:

```text
packaging/android/app/build/outputs/apk/release/app-release-unsigned.apk
```

If `fdroidserver` is installed, also run:

```bash
fdroid readmeta
fdroid lint
fdroid build kr.forestmate.app
```

## F-Droid data submission

For official F-Droid inclusion, add metadata for `kr.forestmate.app` to the
F-Droid `fdroiddata` repository. The source-side `.fdroid.yml` in this repo is
the canonical starting point for that metadata.

F-Droid will clone this repository, check out the release tag, scan the source,
run the Gradle release build in `packaging/android`, and sign the resulting
unsigned APK with the F-Droid signing key.

## Release process

For the next Android release:

1. Update `versionName` and `versionCode` in `packaging/android/app/build.gradle`.
2. Update `appVersionName`, `appVersionCode`, and `appVersion` in
   `packaging/android/twa-manifest.json`.
3. Add a matching Fastlane changelog under
   `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`.
4. Create a tag named `android-vX.Y.Z` after the F-Droid source commit lands on
   the public repository.
5. Update `.fdroid.yml` `CurrentVersion`, `CurrentVersionCode`, and build block.
