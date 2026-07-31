package com.alastorkaneki.discordwidget;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DiscordNotificationListener extends NotificationListenerService {
    private static final Map<String, StatusBarNotification> ACTIVE = new ConcurrentHashMap<>();

    @Override
    public void onListenerConnected() {
        StatusBarNotification[] notifications = getActiveNotifications();
        if (notifications == null) {
            return;
        }
        for (StatusBarNotification notification : notifications) {
            onNotificationPosted(notification);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!isDiscordPackage(sbn.getPackageName())) {
            return;
        }
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        String title = text(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        if (title.isEmpty()) {
            title = text(extras.getCharSequence(Notification.EXTRA_TITLE));
        }
        String preview = text(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (preview.isEmpty()) {
            preview = text(extras.getCharSequence(Notification.EXTRA_TEXT));
        }
        String subtitle = text(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        String shortcutId = notification.getShortcutId();
        String key = shortcutId == null || shortcutId.trim().isEmpty()
                ? sbn.getPackageName() + ":" + title + ":" + subtitle
                : sbn.getPackageName() + ":" + shortcutId;
        Conversation conversation = new Conversation(
                key,
                title.isEmpty() ? getString(R.string.discord_conversation) : title,
                subtitle,
                preview,
                sbn.getPostTime(),
                Conversation.SOURCE_NOTIFICATION,
                ""
        );
        ACTIVE.put(key, sbn);
        ConversationStore.upsert(this, conversation);
        ConversationWidgetProvider.updateAll(this);
        sendBroadcast(new Intent(MainActivity.ACTION_CONVERSATIONS_CHANGED).setPackage(getPackageName()));
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        ACTIVE.values().removeIf(item -> item.getKey().equals(sbn.getKey()));
    }

    public static boolean open(Context context, String conversationKey) {
        StatusBarNotification sbn = ACTIVE.get(conversationKey);
        if (sbn != null) {
            PendingIntent contentIntent = sbn.getNotification().contentIntent;
            if (contentIntent != null) {
                try {
                    contentIntent.send();
                    return true;
                } catch (PendingIntent.CanceledException ignored) {
                }
            }
        }
        return launchDiscord(context);
    }

    public static boolean reply(Context context, String conversationKey, String message) {
        StatusBarNotification sbn = ACTIVE.get(conversationKey);
        if (sbn == null) {
            return false;
        }
        Notification.Action[] actions = sbn.getNotification().actions;
        if (actions == null) {
            return false;
        }
        for (Notification.Action action : actions) {
            android.app.RemoteInput[] inputs = action.getRemoteInputs();
            if (inputs == null || inputs.length == 0) {
                continue;
            }
            Intent fillInIntent = new Intent();
            Bundle results = new Bundle();
            for (android.app.RemoteInput input : inputs) {
                results.putCharSequence(input.getResultKey(), message);
            }
            android.app.RemoteInput.addResultsToIntent(inputs, fillInIntent, results);
            try {
                action.actionIntent.send(context, 0, fillInIntent);
                return true;
            } catch (PendingIntent.CanceledException ignored) {
            }
        }
        return false;
    }

    private static boolean isDiscordPackage(String packageName) {
        return "com.discord".equals(packageName)
                || "com.discord.beta".equals(packageName)
                || "com.discord.canary".equals(packageName);
    }

    private static boolean launchDiscord(Context context) {
        String[] packages = {"com.discord", "com.discord.beta", "com.discord.canary"};
        for (String packageName : packages) {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            }
        }
        return false;
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
