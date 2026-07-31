package com.alastorkaneki.discordwidget;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;

import androidx.browser.customtabs.CustomTabsIntent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

final class DiscordOAuthManager {
    private static final String PREFS = "discord_oauth_pending";
    private static final String KEY_STATE = "state";
    private static final String KEY_VERIFIER = "verifier";
    private static final String KEY_CREATED_AT = "created_at";
    private static final long MAX_AGE_MILLIS = 15L * 60L * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    static final class PendingAuthorization {
        final String verifier;
        final String redirectUri;

        PendingAuthorization(String verifier, String redirectUri) {
            this.verifier = verifier;
            this.redirectUri = redirectUri;
        }
    }

    private DiscordOAuthManager() {
    }

    static String getRedirectUri() {
        return "discord-" + BuildConfig.DISCORD_APPLICATION_ID + ":/authorize/callback";
    }

    static void start(Activity activity) {
        String verifier = randomValue(64);
        String challenge = sha256Base64Url(verifier);
        String state = randomValue(32);
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STATE, state)
                .putString(KEY_VERIFIER, verifier)
                .putLong(KEY_CREATED_AT, System.currentTimeMillis())
                .apply();

        Uri authorizationUri = Uri.parse("https://discord.com/oauth2/authorize")
                .buildUpon()
                .appendQueryParameter("client_id", BuildConfig.DISCORD_APPLICATION_ID)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", getRedirectUri())
                .appendQueryParameter("scope", "openid sdk.social_layer")
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .build();

        try {
            new CustomTabsIntent.Builder().build().launchUrl(activity, authorizationUri);
        } catch (ActivityNotFoundException firstError) {
            Intent browser = new Intent(Intent.ACTION_VIEW, authorizationUri);
            activity.startActivity(browser);
        }
    }

    static PendingAuthorization consume(Context context, String returnedState) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String storedState = preferences.getString(KEY_STATE, null);
        String verifier = preferences.getString(KEY_VERIFIER, null);
        long createdAt = preferences.getLong(KEY_CREATED_AT, 0L);
        preferences.edit().clear().apply();

        long age = System.currentTimeMillis() - createdAt;
        if (storedState == null
                || verifier == null
                || returnedState == null
                || age < 0L
                || age > MAX_AGE_MILLIS
                || !MessageDigest.isEqual(
                        storedState.getBytes(StandardCharsets.UTF_8),
                        returnedState.getBytes(StandardCharsets.UTF_8)
                )) {
            return null;
        }
        return new PendingAuthorization(verifier, getRedirectUri());
    }

    private static String randomValue(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        return Base64.encodeToString(
                bytes,
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
        );
    }

    private static String sha256Base64Url(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.encodeToString(
                    hash,
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
