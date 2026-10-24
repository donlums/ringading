# Ringading — Android TV Blink Doorbell Chime

**Developed by Lumsdon**

Ringading is an experimental Android TV app intended to sound a chime on a Chromecast with Google TV when a Blink Video Doorbell is pressed, without a phone or Home Assistant.

## Status

**Experimental alpha — NOT validated as a working doorbell chime.** The source includes a TV interface, local test sound, Blink OAuth investigation, Firebase push registration and an event classifier for `category=media` + `event_type=button_press`. The app has **not** been built into an APK or tested end-to-end against Blink on a Chromecast.

**Major limitation:** The community Linux receiver [Blink Live Events](https://github.com/CoreyHayward/ha-blink-live-events) works with a Blink Android app identity using an independent MCS client. Android's Firebase SDK in our separately signed app might be rejected by Blink/Firebase; successful event delivery **is not established**. Embedded Blink sign-in may also be restricted. If direct FCM registration fails, the next step is to port the tested MCS receiver to Android. This project does not use a phone.

## Features under development

- Designed for Chromecast with Google TV 4K and Android TV (remote-controlled UI)
- Custom Ding-Dong test chime and volume
- Blink-hosted OAuth / PKCE account flow (experimental)
- Locally protected session and push identifiers in Android Keystore
- Doorbell button presses filtered separately from motion events
- Notification and time of latest press
- Branding: **Ringading** / **Developed by Lumsdon**

## Building

Requires Windows/macOS/Linux with Android Studio, Android SDK 35 and Java 17+. Open the project in Android Studio, then run `gradlew.bat :app:assembleDebug` on Windows or `./gradlew :app:assembleDebug` on macOS/Linux. After an actual successful build, APK is at `app/build/outputs/apk/debug/app-debug.apk`.

No CI workflow is included: this repository does **not** consume GitHub Actions build minutes.

## Privacy / security

Use only your own Blink account and doorbell. **Never post access tokens, refresh tokens, OAuth callback URLs, FCM tokens, Blink passwords, MFA codes or raw event payloads.** See [SECURITY.md](SECURITY.md). Runtime credentials are never committed.

## Licensing

No licence has been chosen yet. A public GitHub repository alone does not grant reuse rights.

## Disclaimer

Unofficial independent project, not affiliated with or endorsed by Blink, Amazon, Google or Home Assistant.
