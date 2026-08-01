package com.alastorkaneki.discordwidget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class DiscordSocialBridge {
    public interface Listener {
        void onStatus(String status);
        void onConversation(String userId, String preview, long messageId);
        void onMessageHistory(String userId, String messagesJson);
        void onError(String error);
        void onMessageSent(String userId, long messageId);
    }

    private static volatile DiscordSocialBridge instance;

    private final Context appContext;
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final boolean applicationIdConfigured;
    private final boolean sdkBundled;

    private volatile boolean initialized;
    private volatile boolean available;
    private volatile boolean restoreRequested;
    private volatile String unavailableReason;

    private DiscordSocialBridge(Context context) {
        appContext = context.getApplicationContext();
        applicationIdConfigured = BuildConfig.DISCORD_APPLICATION_ID != null
                && !BuildConfig.DISCORD_APPLICATION_ID.trim().isEmpty()
                && !"0".equals(BuildConfig.DISCORD_APPLICATION_ID);
        sdkBundled = BuildConfig.SOCIAL_SDK_PRESENT;
        if (!applicationIdConfigured) {
            unavailableReason = appContext.getString(R.string.oauth_missing_application_id);
        } else if (!sdkBundled) {
            unavailableReason = appContext.getString(R.string.oauth_missing_social_sdk);
        } else {
            unavailableReason = appContext.getString(R.string.oauth_native_initialization_failed);
        }
    }

    public static DiscordSocialBridge get(Context context) {
        DiscordSocialBridge current = instance;
        if (current != null) {
            return current;
        }
        synchronized (DiscordSocialBridge.class) {
            if (instance == null) {
                instance = new DiscordSocialBridge(context);
            }
            return instance;
        }
    }

    public synchronized void initialize(Activity activity) {
        if (!applicationIdConfigured || !sdkBundled) {
            return;
        }
        try {
            Class<?> init = Class.forName("com.discord.socialsdk.DiscordSocialSdkInit");
            init.getMethod("setEngineActivity", Activity.class).invoke(null, activity);
            if (!initialized) {
                System.loadLibrary("discord_widget_bridge");
                initialized = nativeInitialize(
                        Long.parseUnsignedLong(BuildConfig.DISCORD_APPLICATION_ID),
                        this
                );
                available = initialized;
                if (!initialized) {
                    unavailableReason = appContext.getString(R.string.oauth_native_initialization_failed);
                    return;
                }
            }
            if (!restoreRequested) {
                restoreRequested = true;
                TokenStore.Session session = TokenStore.load(appContext);
                if (session != null) {
                    nativeRestoreSession(
                            session.accessToken,
                            session.refreshToken,
                            session.expiresAtMillis
                    );
                }
            }
        } catch (Throwable error) {
            String detail = error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getMessage();
            unavailableReason = appContext.getString(R.string.oauth_initialization_error, detail);
            available = false;
        }
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isConnected() {
        return available && nativeIsReady();
    }

    public boolean hasStoredSession() {
        return TokenStore.hasSession(appContext);
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
                ? DiscordOAuthManager.getRedirectUri()
                : "discord-APPLICATION_ID:/authorize/callback";
    }

    public void connect(Activity activity) {
        if (!available) {
            dispatchError(unavailableReason);
            return;
        }
        try {
            DiscordOAuthManager.start(activity);
        } catch (Throwable error) {
            dispatchError(appContext.getString(R.string.oauth_no_browser));
        }
    }

    public void exchangeAuthorizationCode(
            String code,
            String verifier,
            String redirectUri
    ) {
        if (!available) {
            dispatchError(unavailableReason);
            return;
        }
        nativeExchangeAuthorizationCode(code, verifier, redirectUri);
    }

    public void refreshDirectMessages() {
        if (!available) {
            return;
        }
        if (!nativeIsReady()) {
            dispatchError(appContext.getString(R.string.oauth_reconnecting));
            return;
        }
        nativeRefreshDirectMessages();
    }

    public void fetchDirectMessageHistory(String userId, int limit) {
        if (!available) {
            dispatchError(unavailableReason);
            return;
        }
        if (!nativeIsReady()) {
            dispatchError(appContext.getString(R.string.oauth_reconnecting));
            return;
        }
        if (userId == null || userId.isEmpty()) {
            dispatchError(appContext.getString(R.string.invalid_discord_recipient));
            return;
        }
        nativeFetchDirectMessageHistory(userId, Math.max(1, Math.min(200, limit)));
    }

    public void sendDirectMessage(String userId, String message) {
        if (!available) {
            dispatchError(unavailableReason);
            return;
        }
        if (!nativeIsReady()) {
            dispatchError(appContext.getString(R.string.oauth_reconnecting));
            return;
        }
        nativeSendDirectMessage(userId, message);
    }

    private void dispatchStatus(String status) {
        if ("ready".equalsIgnoreCase(status)) {
            startMessagingService();
            nativeRefreshDirectMessages();
        }
        for (Listener listener : listeners) {
            listener.onStatus(status);
        }
    }

    private void dispatchConversation(String userId, String preview, long messageId) {
        Conversation conversation = new Conversation(
                "social:" + userId,
                appContext.getString(R.string.discord_user_id, userId),
                appContext.getString(R.string.oauth_dm),
                preview,
                System.currentTimeMillis(),
                Conversation.SOURCE_SOCIAL_DM,
                userId
        );
        ConversationStore.upsert(appContext, conversation);
        Intent changed = new Intent(MainActivity.ACTION_CONVERSATIONS_CHANGED);
        changed.setPackage(appContext.getPackageName());
        appContext.sendBroadcast(changed);
        ConversationWidgetProvider.updateAll(appContext);
        for (Listener listener : listeners) {
            listener.onConversation(userId, preview, messageId);
        }
    }

    private void dispatchMessageHistory(String userId, String messagesJson) {
        for (Listener listener : listeners) {
            listener.onMessageHistory(userId, messagesJson);
        }
    }

    private void dispatchError(String error) {
        for (Listener listener : listeners) {
            listener.onError(error);
        }
    }

    private void dispatchMessageSent(String userId, long messageId) {
        for (Listener listener : listeners) {
            listener.onMessageSent(userId, messageId);
        }
        if (available && nativeIsReady()) {
            nativeRefreshDirectMessages();
        }
    }

    private void dispatchTokens(String accessToken, String refreshToken, long expiresAtMillis) {
        TokenStore.save(appContext, accessToken, refreshToken, expiresAtMillis);
    }

    private void startMessagingService() {
        try {
            appContext.startForegroundService(new Intent(appContext, DiscordMessagingService.class));
        } catch (RuntimeException ignored) {
        }
    }

    private static native boolean nativeInitialize(long applicationId, DiscordSocialBridge bridge);
    private static native void nativeExchangeAuthorizationCode(
            String code,
            String verifier,
            String redirectUri
    );
    private static native void nativeRestoreSession(
            String accessToken,
            String refreshToken,
            long expiresAtMillis
    );
    private static native boolean nativeIsReady();
    private static native void nativeRefreshDirectMessages();
    private static native void nativeFetchDirectMessageHistory(String userId, int limit);
    private static native void nativeSendDirectMessage(String userId, String message);
}
