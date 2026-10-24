# Ringading · Android TV doorbell chime

**Developed by Lumsdon**

An Android TV doorbell chime for a Blink Video Doorbell, without a phone companion or Home Assistant. This is an unofficial independent project.

## v0.3: redesigned TV dashboard

- Remote-friendly four-tab navigation: **Home**, **Chime & alerts**, **Blink connection**, and **About**.
- High-contrast dark navy and amber TV layout with larger focus states and safe-screen margins.
- Home shows the latest ring and a one-click chime / pop-up preview.
- Chime supports **4, 8, or 12 seconds** and volume from **0–100%** of the TV media volume.
- Optional nine-second **“Someone is at your door”** pop-up; requires user-granted Display over other apps permission.
- Actual button presses trigger alerts; motion-only events are ignored.
- Blink sign-in and encrypted local session storage remain unchanged.

## Preserve your working Blink connection

**Important:** The first Ringading v0.1 APK was signed with a temporary debug key generated on a GitHub hosted runner. That private signing key was not retained. **No new APK can replace that exact installation without losing its existing app data**, because Android requires an identical signing certificate to perform an update. Do not uninstall the working app until you are ready.

The automatic GitHub **preview** build uses package `uk.co.lumsdon.ringading.preview`, so you can safely install it **beside** the existing Ringading app and test the redesign. The preview has separate settings and Blink tokens and must be paired separately to receive live events.

The **permanent main-app** signing key is now generated and encrypted as `signing/ringading-release.p12.gpg.b64` with GPG AES-256. Its private unlocking password is **not** in GitHub. The signed release workflow is a manual GitHub Actions workflow which requires a private repository secret named `RINGADING_RELEASE_PASSWORD`.

### One-time setup for permanent update signing

1. Obtain the separately supplied private recovery file `RINGADING-SIGNING-RECOVERY-KEEP-PRIVATE.txt` and keep it in a password manager. **Never upload it to your public repo.**
2. In the `donlums/ringading` repository open **Settings → Secrets and variables → Actions → New repository secret**.
3. Name the secret `RINGADING_RELEASE_PASSWORD`, and paste the single secret value from that private recovery file.
4. Open **Actions → Ringading signed main app (permanent update key) → Run workflow**.
5. After the release workflow succeeds, its artifact is a permanently signed main-app APK with package `uk.co.lumsdon.ringading`.

The **first** installation of the permanently signed main APK will still require uninstalling the old v0.1 APK (after you are ready), losing its old secure Blink login. Sign into Blink once again after installing the new main app. **Future main-app APKs signed with this same permanent key and a higher `versionCode` can update in place**, preserving Android app data and Blink connection. Keep the signing password and archive backups safe permanently; do not regenerate them for each update.

Do not upload keystores, signing passwords or Blink access tokens in public issues or code.

## Build and test

From Android Studio on Windows 11 with Android SDK 35 and JDK 17:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. GitHub also produces a downloadable preview APK via **Ringading APK (free public runner)** on each push to `main`. It uses the public repository GitHub-hosted runner.

To build a **main app release**, use the separately configured manually triggered release-signing workflow after entering your repository secret, not `assembleDebug`.

## Disclaimers

Blink Cloud / Firebase communication is an unofficial, reverse-engineered interface and may change. It is already reported working on one installed Ringading device but should not be relied on as the only doorbell alert. TV pop-up support varies by device and streaming application.

Reference: [Blink Live Events by CoreyHayward](https://github.com/CoreyHayward/ha-blink-live-events). Ringading is not affiliated with Blink, Amazon, Google, or Home Assistant. A public repository does not in itself grant a software licence.
