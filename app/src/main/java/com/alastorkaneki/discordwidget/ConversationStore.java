package com.alastorkaneki.discordwidget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConversationStore {
    private static final String PREFS = "conversation_store";
    private static final String KEY_CONVERSATIONS = "conversations";
    private static final String WIDGET_PREFIX = "widget_";
    private static final int MAX_CONVERSATIONS = 100;

    private ConversationStore() {
    }

    public static synchronized void upsert(Context context, Conversation conversation) {
        Map<String, Conversation> map = new LinkedHashMap<>();
        for (Conversation item : getAll(context)) {
            map.put(item.key, item);
        }
        map.put(conversation.key, conversation);
        List<Conversation> sorted = new ArrayList<>(map.values());
        sorted.sort(Comparator.comparingLong((Conversation item) -> item.timestamp).reversed());
        if (sorted.size() > MAX_CONVERSATIONS) {
            sorted = new ArrayList<>(sorted.subList(0, MAX_CONVERSATIONS));
        }
        saveAll(context, sorted);
    }

    public static synchronized List<Conversation> getAll(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_CONVERSATIONS, "[]");
        List<Conversation> conversations = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                conversations.add(Conversation.fromJson(array.getJSONObject(index)));
            }
        } catch (JSONException ignored) {
        }
        conversations.sort(Comparator.comparingLong((Conversation item) -> item.timestamp).reversed());
        return conversations;
    }

    public static synchronized Conversation get(Context context, String key) {
        for (Conversation conversation : getAll(context)) {
            if (conversation.key.equals(key)) {
                return conversation;
            }
        }
        return null;
    }

    public static void bindWidget(Context context, int appWidgetId, String conversationKey) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(WIDGET_PREFIX + appWidgetId, conversationKey)
                .apply();
    }

    public static String getWidgetConversationKey(Context context, int appWidgetId) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(WIDGET_PREFIX + appWidgetId, "");
    }

    public static void removeWidget(Context context, int appWidgetId) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(WIDGET_PREFIX + appWidgetId)
                .apply();
    }

    private static void saveAll(Context context, List<Conversation> conversations) {
        JSONArray array = new JSONArray();
        for (Conversation conversation : conversations) {
            try {
                array.put(conversation.toJson());
            } catch (JSONException ignored) {
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CONVERSATIONS, array.toString())
                .apply();
    }
}
