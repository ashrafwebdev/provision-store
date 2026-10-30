# Provision Store

Android app for running a provision (grocery) store: billing, stock, and khata (credit). Built for personal use.

## Install

1. Open the [Releases](https://github.com/ashrafwebdev/provision-store/releases) page on your phone.
2. Download the newest `ProvisionStore-v<n>.apk`.
3. Open it and allow installs from this source when asked.

## Updates

The app checks GitHub for a newer release each time it opens. When one is found it offers to download and install it. Updates install over the existing app and keep your data, because every release is signed with the same key.

## How releases are built

Every push to `main` runs [`.github/workflows/release.yml`](.github/workflows/release.yml). It builds a signed release APK and publishes it as a GitHub release tagged `v<run number>`. The version code is the run number, so each release is newer than the last.

Required repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
The keystore itself is never committed. Keep a backup copy somewhere safe; losing it means installed copies can no longer be updated.
