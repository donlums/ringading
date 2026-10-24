# Ringading · Android TV doorbell chime

**Developed by Lumsdon**

An Android TV doorbell chime for a Blink Video Doorbell, without a phone companion or Home Assistant. This is an unofficial independent project.

## Features

- Remote-friendly TV dashboard with **Home**, **Chime & alerts**, **Blink connection**, and **About**.
- High-contrast dark navy and amber design, larger focus states and TV-safe margins.
- Quick access to a test chime and door-alert preview.
- Louder ding-dong chime with adjustable **4, 8 or 12-second** duration and volume.
- Optional **“Someone is at your door”** pop-up; requires permission to display over other apps.
- Only doorbell button presses trigger alerts; motion events are ignored.
- Blink sign-in and encrypted local session storage.

## Installing Ringading

Download a test APK from the [GitHub Actions builds](https://github.com/donlums/ringading/actions/workflows/build-apk.yml).

**The current preview installs separately from the original Ringading app**, allowing you to test the new interface without disturbing your existing Blink connection. The preview has its own settings and requires a separate Blink sign-in to receive live events.

Android requires matching app signatures for in-place updates. Keep the existing app installed until a compatible update or migration is available.

## Build from source

Open the project with Android Studio using JDK 17 and Android SDK 35.

On Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

The resulting APK is in `app/build/outputs/apk/debug/`.

## Privacy and disclaimer

Never share Blink passwords, verification codes, OAuth callback URLs, session tokens or notification payloads.

Blink cloud notifications use an unofficial integration and may change. Ringading should not be your only doorbell alert until thoroughly tested on your device. TV overlay support varies by device and streaming app.

Reference: [Blink Live Events by CoreyHayward](https://github.com/CoreyHayward/ha-blink-live-events). Ringading is not affiliated with Blink, Amazon, Google or Home Assistant.
