# Discord Social SDK setup

Discord's normal OAuth2 REST scopes do not expose all user messages. This project uses Discord's Social SDK communication scope for the OAuth-backed DM path.

## Discord Developer Portal

1. Create a Discord developer team.
2. Create an application owned by that team.
3. Enable Discord Social SDK for the application.
4. Enable the communication scopes used by `Client::GetDefaultCommunicationScopes`.
5. Add the mobile redirect URI:

   `discord-APPLICATION_ID:/authorize/callback`

6. Set the application as a public client for local PKCE token exchange, or replace the token exchange with a confidential backend.
7. Download the latest standalone C++ Social SDK for Android.

## Project configuration

Set the public application ID in `gradle.properties`:

```properties
DISCORD_APPLICATION_ID=123456789012345678
```

Extract the SDK under:

```text
app/discord_social_sdk/
├── include/
│   └── discordpp.h
└── lib/
    ├── arm64-v8a/
    │   └── libdiscord_partner_sdk.so
    ├── armeabi-v7a/
    │   └── libdiscord_partner_sdk.so
    └── x86_64/
        └── libdiscord_partner_sdk.so
```

The SDK directory is ignored by Git because Discord distributes it through the Developer Portal. When `discordpp.h` is present, Gradle automatically enables the native bridge.

## Supported OAuth behavior

The app requests the Social SDK communication scopes and uses OAuth2 with PKCE. It can:

- list supported one-to-one DM conversation summaries
- fetch the latest supported DM content
- send a DM only after the user taps Send
- receive Social SDK message events while connected

Discord currently limits retrieved history to 200 messages and 72 hours. Both users must have used the same integration for history to be available.

## Server channels

Server channels are not exposed as unrestricted user-account message access. Discord's supported route is Linked Channels. A link requires a Social SDK lobby and the authorizing user must have Manage Channels, View Channel, and Send Messages. Linked Channels are separate from the notification-backed widget path and are not enabled in the first build.
