# Ringading — Android TV Blink Doorbell Chime

**Developed by Lumsdon**

Standalone Android TV experimental chime for Blink Video Doorbell owners. No phone companion is required.

## Latest: v0.2.0 beta

- **Louder, fuller two-note ding-dong** routed through your TV's media volume.
- Adjustable **4, 8 (default), or 12-second** ring with repeated pairs.
- Chime volume from **0–100% (default 100%)** of the TV's media output, with soft limiting to prevent digital clipping.
- Optional **“Someone is at your door”** on-screen banner over other apps for approximately 9 seconds. Requires Android's **Display over other apps** special permission, if your TV supports it.
- Normal high-priority doorbell notification remains a fallback.
- Blink cloud connection unchanged; already working users can compare behaviours.

**Important installation note:** The free GitHub runner changes Android debug signing keys on each job. Instead of forcing you to uninstall the original working build, v0.2.0-beta debug APK uses Android package `uk.co.lumsdon.ringading.beta`, allowing it to install **alongside** the original Ringading. It has separate data and needs its own Blink login/association to ring on real events. Disable pop-ups or sign out of one app to avoid duplicate chimes when both receive an event. A stable production release-signing key has not yet been set up.

## Build on Windows

Open this Gradle project in Android Studio (JDK 17, SDK 35). Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. The repository also builds APK artifacts using free runners for public repositories on each source change.

## Blink connection limitations

This app uses an unofficial Blink cloud/Firebase approach based on [CoreyHayward's Blink Live Events](https://github.com/CoreyHayward/ha-blink-live-events). Receiving real presses depends on Blink accepting the independent Android app identity and notifying it. It may stop working after vendor-side changes. Motion events do not trigger the chime.

## Privacy

Never share Blink passwords, MFA codes, OAuth callback URLs, session tokens, or notification payloads. See [SECURITY.md](SECURITY.md).

## Disclaimer

Independent community project not affiliated with Blink, Amazon, Google, or Home Assistant. Public repository; no redistribution licence selected yet.
