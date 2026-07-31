package com.alastorkaneki.discordwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.text.DateFormat;
import java.util.Date;

public final class ConversationWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            update(context, manager, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            ConversationStore.removeWidget(context, appWidgetId);
        }
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ConversationWidgetProvider.class));
        for (int id : ids) {
            update(context, manager, id);
        }
    }

    public static void update(Context context, AppWidgetManager manager, int appWidgetId) {
        String key = ConversationStore.getWidgetConversationKey(context, appWidgetId);
        Conversation conversation = ConversationStore.get(context, key);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_conversation);

        if (conversation == null) {
            views.setTextViewText(R.id.widgetTitle, context.getString(R.string.widget_not_configured));
            views.setTextViewText(R.id.widgetPreview, context.getString(R.string.widget_configure_hint));
            views.setTextViewText(R.id.widgetTime, "");
        } else {
            views.setTextViewText(R.id.widgetTitle, conversation.title);
            views.setTextViewText(R.id.widgetPreview, conversation.preview);
            views.setTextViewText(
                    R.id.widgetTime,
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(conversation.timestamp))
            );

            Intent openIntent = new Intent(context, OpenConversationActivity.class);
            openIntent.putExtra(OpenConversationActivity.EXTRA_CONVERSATION_KEY, conversation.key);
            PendingIntent openPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.widgetRoot, openPendingIntent);

            Intent replyIntent = new Intent(context, ReplyActivity.class);
            replyIntent.putExtra(ReplyActivity.EXTRA_CONVERSATION_KEY, conversation.key);
            PendingIntent replyPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId + 100000,
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.widgetReply, replyPendingIntent);
        }

        manager.updateAppWidget(appWidgetId, views);
    }
}
