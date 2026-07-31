# Discord Social SDK setup

Discord's normal OAuth2 REST scopes do not expose all user messages. This project uses Discord's Social SDK communication scope for the OAuth-backed DM path.

## Discord Developer Portal

1. Create a Discord application.
2. Complete the Getting Started flow under the Social SDK section.
3. Enable Public Client on the OAuth2 page for local PKCE token exchange.
4. Add the mobile redirect URI:

   `discord-APPLICATION_ID:/authorize/callback`

5. Download Discord Social SDK 1.5 or newer for Android from the application's Social SDK page.

## Project configuration

Set the application ID in the root `gradle.properties` file:

```properties
DISCORD_APPLICATION_ID=123456789012345678
```

Place the Android AAR at:

```text
app/discord_social_sdk/discord_partner_sdk.aar
```

The SDK directory is ignored by Git because Discord distributes the package through the Developer Portal. When `discord_partner_sdk.aar` is present, Gradle automatically enables Prefab, compiles the JNI bridge, packages the SDK, and registers the Discord authorization callback scheme.

The redirect URI in the Discord Developer Portal must exactly match the application ID used during the build:

```text
discord-123456789012345678:/authorize/callback
```

## Supported OAuth behavior

The app requests the Social SDK communication scopes and uses OAuth2 with PKCE. It can:

- list supported one-to-one DM conversation summaries
- fetch the latest supported DM content
- send a DM only after the user taps Send
- receive Social SDK message events while connected

Discord currently limits retrieved history to 200 messages and 72 hours. Both users must have used the same integration for history to be available.

## Server channels

Server channels are not exposed as unrestricted user-account message access. Discord's supported route is Linked Channels. A link requires a Social SDK lobby and the authorizing user must have Manage Channels, View Channel, and Send Messages. Linked Channels are separate from the notification-backed widget path and are not enabled in this build.
