# Android client

The Android client is a native Jetpack Compose app under `app/`. It talks to the
backend exclusively over the REST API (Retrofit / Moshi / OkHttp) and caches
state locally (DataStore + Room).

## Prerequisites

- Android Studio (the project builds against the SDK bundled with Studio;
  on this machine `JAVA_HOME` = the Studio bundled JBR).
- An Android emulator (AVD) or a physical device.

## Building

```bash
# Debug APK (installs build tools automatically)
.\gradlew.bat assembleDebug

# Install to a running emulator/device
.\gradlew.bat installDebug
```

The signed debug APK lands at:
`app/build/outputs/apk/debug/MediaraAI-1.0.0-debug.apk`

## Connecting to a backend

The base URL is injected at build time via `BuildConfig.API_BASE_URL`
(`app/build.gradle.kts`).

| Scenario | `API_BASE_URL` |
| -------- | -------------- |
| Emulator → backend on your PC | `http://10.0.2.2:8000/` |
| Physical device → backend on your PC (adb reverse) | `http://127.0.0.1:8000/` |
| Physical device → LAN backend | `http://192.168.x.x:8000/` |
| Production | `https://api.mediara.ai/` (set before shipping) |

Override on the command line before building:

```bash
export API_BASE_URL="http://10.0.2.2:8000/"
.\gradlew.bat installDebug
```

### Cleartext policy

`res/xml/network_security_config.xml` permits cleartext HTTP **only** for the
emulator loopback hosts (`10.0.2.2`, `localhost`, `127.0.0.1`). Every other
host requires HTTPS — enforced by the OS, not just the app.

If the backend isn't on loopback (e.g. a LAN-addressable dev machine), either
use HTTPS or add the host to the network security config **and keep that change
out of release builds**.

## Source map

```
app/src/main/java/com/mediara/app/
  data/remote/          DTOs, MediaraApiService, ApiClient, SessionManager
  data/repository/      MediationRepository (single API facade)
  data/MediaraMappers.kt  wire → domain mapping
  ui/                   Compose screens (auth, dashboard, caucus, proposals, agreement, settings)
  ui/theme/             Material 3 theme, dynamic colour, typography
  ui/navigation/        NavTransitions, AppNavigation, Screen routes
```

## App flows

- **Auth**: register → email verification code → login → optional TOTP MFA →
  settings UI for profile, MFA, password, data export/erasure.
- **Mediation**: create → invite code → join → private caucuses → AI Analysis →
  grounded proposals → voting → ratified accord → PDF download → follow-ups.
- **Safety hold**: if a private message trips the detector, the mediation pauses
  and the Safety Protection Protocol screen is surfaced with crisis resources.

## Two-account workflow

Both accounts can run in the **same** emulator — sign out and back in, or use
two emulators. Email-verification codes are printed to the backend console
(dev, console email backend) and are also returned by the DEBUG-only
`/api/auth/dev-resend-code/` endpoint.

## Navigation & transitions

See `ui/navigation/NavTransitions.kt`: every screen transition uses canned
`composableWithMotion` routes — horizontal slide/fade forward, reverse for
back, crossfade for tab switches, and a rise-from-bottom motion for auth/overlay
flows. New screens must register in `Screen.kt` (including the right motion
group: `BottomBarRoutes`, `CrossfadeRoutes`, or `RiseRoutes`) — see
[MODIFYING.md](MODIFYING.md).

## Release builds

```bash
.\gradlew.bat assembleRelease
```

Set a real `API_BASE_URL` (HTTPS) via `org.gradle.jvmargs`/env and sign with
your keystore (`signingConfigs` in `app/build.gradle.kts`).