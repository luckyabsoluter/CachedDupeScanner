# Ship a Signed Release APK with GitHub Actions

This document explains how to configure GitHub Actions to build a **signed Release APK** and attach it to a GitHub Release whenever a `v*` tag is pushed.

- Workflow: `.github/workflows/android-apk-release.yml`
- Required secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`

## 1) Create a keystore

Create it once locally. **Never commit** the generated `.jks` file to Git.

```bash
keytool -genkeypair \
  -v \
  -keystore release-keystore.jks \
  -alias release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

- The `-alias` value (e.g., `release`) must match the GitHub Secret `ANDROID_KEY_ALIAS`.
- Store the keystore password and key password as `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_PASSWORD`, respectively.

## 2) Encode the keystore as Base64

GitHub Secrets cannot store files directly, so save it as a Base64 string.

### macOS / Linux

```bash
base64 -w 0 release-keystore.jks > release-keystore.jks.b64
```

Copy the contents of `release-keystore.jks.b64` into `ANDROID_KEYSTORE_BASE64`.

### Windows (PowerShell)

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release-keystore.jks")) | Set-Clipboard
```

Paste the string copied to the clipboard into `ANDROID_KEYSTORE_BASE64`.

## 3) Add GitHub Secrets

In the GitHub repo, go to `Settings` → `Secrets and variables` → `Actions` → `New repository secret` and add the following:

- `ANDROID_KEYSTORE_BASE64`: Base64 string of the keystore file
- `ANDROID_KEYSTORE_PASSWORD`: keystore password
- `ANDROID_KEY_ALIAS`: key alias (e.g., `release`)
- `ANDROID_KEY_PASSWORD`: key password

## 4) Trigger a release via tag

The workflow runs only when the tag matches the `v*` pattern.

```bash
git tag v1.0.0
git push origin v1.0.0
```

When the run completes, `app-v1.0.0.apk` will be uploaded to GitHub Releases.

## 5) Notes

- The workflow injects signing info via the `android.injected.signing.*` Gradle properties.
- To ensure uploads even if the APK filename differs by environment or AGP version, the workflow finds the first `*.apk` in the `release` folder and renames it to the tag name.
- If your goal is Play Store distribution, consider switching from APK to AAB (`:app:bundleRelease`).
