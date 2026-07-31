# Discord Widget App

An AMOLED Android companion for pinning Discord conversations to the home screen.

The app uses two integration paths:

- Discord Social SDK with OAuth2 and PKCE for supported direct-message reading and user-initiated sending.
- Android notification access as a local fallback for conversations the Social SDK cannot expose.

No Discord password, 2FA code, user token, cookie, or self-bot login is used.

## Current features

- True-black AMOLED interface
- Discord notification discovery
- Per-conversation home-screen widgets
- Tap a widget to open the related Discord notification
- Reply through Discord's notification action when available
- Optional OAuth-backed Social SDK integration for DMs
- Local-only conversation cache
- GitHub Actions debug APK build

## Build

```bash
gradle assembleDebug
```

The default build works without Discord's proprietary SDK and uses notification access. OAuth-backed DM reading and sending are enabled when the Social SDK package and application ID are configured.

See [`docs/SOCIAL_SDK_SETUP.md`](docs/SOCIAL_SDK_SETUP.md).
