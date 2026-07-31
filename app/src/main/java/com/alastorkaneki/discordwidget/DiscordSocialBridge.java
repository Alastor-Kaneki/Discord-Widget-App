package com.alastorkaneki.discordwidget;

import android.app.Activity;

public final class DiscordSocialBridge {
    public interface Listener {
        void onStatus(String status);
        void onConversation(String userId, String preview, long messageId);
        void onError(String error);
        void onMessageSent(String userId, long messageId);
    }

    private final Listener listener;
    private final boolean available;
    private final boolean applicationIdConfigured;
    private final boolean sdkBundled;
    private final String unavailableReason;

    public DiscordSocialBridge(Activity activity, Listener listener) {
        this.listener = listener;
        applicationIdConfigured = BuildConfig.DISCORD_APPLICATION_ID != null
                && !BuildConfig.DISCORD_APPLICATION_ID.trim().isEmpty()
                && !"0".equals(BuildConfig.DISCORD_APPLICATION_ID);
        sdkBundled = BuildConfig.SOCIAL_SDK_PRESENT;

        boolean loaded = false;
        String reason = "";
        if (!applicationIdConfigured) {
            reason = activity.getString(R.string.oauth_missing_application_id);
        } else if (!sdkBundled) {
            reason = activity.getString(R.string.oauth_missing_social_sdk);
        } else {
            try {
                System.loadLibrary("discord_widget_bridge");
                Class<?> init = Class.forName("com.discord.socialsdk.DiscordSocialSdkInit");
                init.getMethod("setEngineActivity", Activity.class).invoke(null, activity);
                loaded = nativeInitialize(Long.parseUnsignedLong(BuildConfig.DISCORD_APPLICATION_ID), this);
                if (!loaded) {
                    reason = activity.getString(R.string.oauth_native_initialization_failed);
                }
            } catch (Throwable error) {
                String detail = error.getMessage() == null
                        ? error.getClass().getSimpleName()
                        : error.getMessage();
                reason = activity.getString(R.string.oauth_initialization_error, detail);
            }
        }
        available = loaded;
        unavailableReason = available ? "" : reason;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isApplicationIdConfigured() {
        return applicationIdConfigured;
    }

    public boolean isSdkBundled() {
        return sdkBundled;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }

    public String getRedirectUri() {
        return applicationIdConfigured
                ? "discord-" + BuildConfig.DISCORD_APPLICATION_ID + ":/authorize/callback"
                : "discord-APPLICATION_ID:/authorize/callback";
    }

    public void connect() {
        if (!available) {
            listener.onError(unavailableReason);
            return;
        }
        nativeConnect();
    }

    public void refreshDirectMessages() {
        if (!available) {
            listener.onError(unavailableReason);
            return;
        }
        nativeRefreshDirectMessages();
    }

    public void sendDirectMessage(String userId, String message) {
        if (!available) {
            listener.onError(unavailableReason);
            return;
        }
        nativeSendDirectMessage(userId, message);
    }

    private void dispatchStatus(String status) {
        listener.onStatus(status);
    }

    private void dispatchConversation(String userId, String preview, long messageId) {
        listener.onConversation(userId, preview, messageId);
    }

    private void dispatchError(String error) {
        listener.onError(error);
    }

    private void dispatchMessageSent(String userId, long messageId) {
        listener.onMessageSent(userId, messageId);
    }

    private static native boolean nativeInitialize(long applicationId, DiscordSocialBridge bridge);
    private static native void nativeConnect();
    private static native void nativeRefreshDirectMessages();
    private static native void nativeSendDirectMessage(String userId, String message);
}