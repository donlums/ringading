# Ringading · Blink doorbell chime for Android TV

**Developed by Pix91**

Ringading adds a chime and visitor notification to a compatible Android TV, without a phone companion.

## v0.4 Live View Preview (experimental)

- All v0.3 features: remote-first dashboard, 4/8/12-second chime, volume settings, visitor pop-up
- **Watch live camera** button on Home
- Discover doorbells using your Blink account session (no second login within this install)
- Request Blink's live-view session and decode proprietary IMMIS framing on the TV
- Serve its MPEG-TS media to the TV player over a **loopback-only** local connection
- Full-screen picture and audio using Android Media3 ExoPlayer
- 35-second test-session cap, with close/Back stopping playback
- No phone, Home Assistant, extra hardware, remote proxy or recording

### Limitations

**Live playback is unverified.** The code compiles, but it must be tested against your actual Blink doorbell. Some cameras use alternative `rtsps://` transport, which is not supported by this preview. Other models may use an SSL setup or stream handshake that this prototype cannot yet decode.

**Original Blink Video Doorbell (1st Gen) Event Response mode:** Blink generally only permits Live View through recent event notifications within 60 seconds if it is not paired with a Sync Module or compatible wiring. Manual viewing may therefore fail. See [Blink Event Response support](https://support.blinkforhome.com/en_US/event-response).

For safety, v0.4 has a separate package `uk.co.lumsdon.ringading.livepreview`. It **cannot and will not replace the working v0.3 or original v0.1 app**, so connecting Blink again is necessary for this preview. The existing installed chime remains untouched.

## Build

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Public GitHub Actions provides build artifacts on changes to main. Keep account credentials, callback URLs and live-view session URLs private, including in bug reports. The loopback HTTP exception in the app's network security configuration applies only to 127.0.0.1.

## Protocol references

Protocol research draws on [BlinkPy](https://github.com/fronzbot/blinkpy) and [Blink Live View Proxy](https://github.com/Teethree89/ha-blink-live-view-proxy). Both are MIT-licensed community projects. Ringading is unofficial and not affiliated with Blink, Amazon or Google. It should not be your only doorbell alert.
