# Security and privacy

Ringading is an **unverified alpha** that communicates with an unofficial Blink cloud interface.
It is not affiliated with or endorsed by Blink or Amazon.

- Never open a public issue containing a Blink password, a verification code, an OAuth redirect URL, an access or refresh token, an FCM registration token, your account identifiers or raw notification payloads.
- Tokens generated at runtime should be stored on the Android TV only; do not copy device app data into this repository.
- If you discover a security vulnerability, do not post exploit details or credentials in a public issue. Use GitHub's private vulnerability reporting mechanism if it is enabled for the repository.
- Avoid exposing Android Debug Bridge to the public internet. Disable wireless debugging after installing and testing the app.
- The Blink-related Firebase identifiers in `ChimeApp.java` are public *application identifiers* sourced from community research, not account credentials. Their presence does not mean Blink has authorised this app, and Firebase may reject the app's differently signed registration.

## Supported versions

There is no production or security-supported release yet. Version `0.1.1-alpha` is for experimental testing on user-owned Blink accounts and Android TV devices.