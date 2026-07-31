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

    public DiscordSocialBridge(Activity activity, Listener listener) {
        this.listener = listener;
        boolean loaded = false;
        if (BuildConfig.SOCIAL_SDK_PRESENT && !"0".equals(BuildConfig.DISCORD_APPLICATION_ID)) {
            try {
                System.loadLibrary("discord_widget_bridge");
                Class<?> init = Class.forName("com.discord.socialsdk.DiscordSocialSdkInit");
                init.getMethod("setEngineActivity", Activity.class).invoke(null, activity);
                loaded = nativeInitialize(Long.parseUnsignedLong(BuildConfig.DISCORD_APPLICATION_ID), this);
            } catch (Throwable error) {
                listener.onError(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            }
        }
        available = loaded;
    }

    public boolean isAvailable() {
        return available;
    }

    public void connect() {
        if (!available) {
            listener.onError("Discord Social SDK is not configured");
            return;
        }
        nativeConnect();
    }

    public void refreshDirectMessages() {
        if (!available) {
            listener.onError("Discord Social SDK is not configured");
            return;
        }
        nativeRefreshDirectMessages();
    }

    public void sendDirectMessage(String userId, String message) {
        if (!available) {
            listener.onError("Discord Social SDK is not configured");
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
