# Ringading — Blink doorbell chime for Android TV

**Developed by Pix91**

Ringading makes your compatible Android TV play a configurable chime when your Blink doorbell is pressed, without a phone companion or Home Assistant.

## Automatic Blink session renewal

Ringading checks its authorised Blink session when the app starts and schedules a network-connected check roughly every 12 hours using Android WorkManager, including after TV restarts. Access tokens are refreshed as needed; new rotating refresh tokens are stored encrypted on the TV. The Blink connection screen shows the last successful check and a manual **Check Blink connection now** button.

**Blink controls authentication**. A 30-day trusted-device MFA period cannot be extended or bypassed by Ringading; Blink can request reauthentication at any time. Android may delay periodic jobs while the TV is asleep or offline. If Blink rejects the refresh token, Ringading keeps the existing local settings and displays a sign-in warning rather than pretending the connection is healthy.

## Features

- TV-friendly navigation, chime volume, 4/8/12-second doorbell sounds and a visitor alert
- Optional “Someone is at your door” banner
- Blink sign-in with locally encrypted credentials
- **Watch live camera**: experimental on-device doorbell discovery and a direct Blink IMMIS transport bridge to a TV video player
- A 35-second limit on test video sessions; no cloud recording or external video proxy

## Live camera testing

The live-video feature has compiled and passed automated Android tests, **but actual Blink camera playback has not yet been verified**. The current prototype supports Blink's `immis://` transport, not `rtsps://`. Live View may be unavailable in Event Response mode or when the camera is asleep or busy.

## Installation and signing

The Pix91 Android application ID is `uk.co.pix91.ringading`. This is **different** from earlier test editions and the original app. Installing it will not erase those separate installations, but you must sign in to Blink again to use this package.

The automatic GitHub Actions debug APK uses a temporary signing certificate. It is **not** a permanent in-place update channel; every main-app release intended to update users must instead use the existing permanent release-signing workflow, with its private secret configured. Do not uninstall a working copy of Ringading until you are ready to migrate.

To build the Android app with JDK 17 and SDK 35:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Build artifacts are available in [GitHub Actions](https://github.com/donlums/ringading/actions). On-screen test alert controls do not send real doorbell events.

## Security and disclaimer

Never share Blink passwords, MFA codes, camera URLs, OAuth callbacks, session tokens or notification payloads.

This is an independent community project and is not affiliated with Blink, Amazon or Google. Its cloud integration is unofficial and could change without notice. Ringading should not be your only doorbell alert until tested on your own device. Protocol research references [BlinkPy](https://github.com/fronzbot/blinkpy) and [Blink Live View Proxy](https://github.com/Teethree89/ha-blink-live-view-proxy); respect their MIT notices when reusing code.
