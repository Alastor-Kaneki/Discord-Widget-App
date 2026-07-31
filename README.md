# Discord Widget App

An AMOLED Android companion for pinning Discord conversations to the home screen.

The project uses two integration paths:

- Discord Social SDK with OAuth2 and PKCE for supported direct-message reading and user-initiated sending.
- Android notification access as a local fallback for conversations the Social SDK cannot expose.

No Discord password, 2FA code, user token, cookie, or self-bot login is used.

## Variants

- `communityDebug`: builds without Discord's proprietary Social SDK and supports notification-backed widgets.
- `socialDebug`: adds OAuth-backed Discord Social SDK messaging after the SDK package and application ID are configured.

See [`docs/SOCIAL_SDK_SETUP.md`](docs/SOCIAL_SDK_SETUP.md) for setup.
