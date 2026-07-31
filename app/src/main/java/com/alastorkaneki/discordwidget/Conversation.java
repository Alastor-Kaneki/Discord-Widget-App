package com.alastorkaneki.discordwidget;

import org.json.JSONException;
import org.json.JSONObject;

public final class Conversation {
    public static final String SOURCE_NOTIFICATION = "notification";
    public static final String SOURCE_SOCIAL_DM = "social_dm";

    public final String key;
    public final String title;
    public final String subtitle;
    public final String preview;
    public final long timestamp;
    public final String source;
    public final String remoteUserId;

    public Conversation(
            String key,
            String title,
            String subtitle,
            String preview,
            long timestamp,
            String source,
            String remoteUserId
    ) {
        this.key = key;
        this.title = title;
        this.subtitle = subtitle;
        this.preview = preview;
        this.timestamp = timestamp;
        this.source = source;
        this.remoteUserId = remoteUserId;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("key", key);
        object.put("title", title);
        object.put("subtitle", subtitle);
        object.put("preview", preview);
        object.put("timestamp", timestamp);
        object.put("source", source);
        object.put("remoteUserId", remoteUserId);
        return object;
    }

    public static Conversation fromJson(JSONObject object) {
        return new Conversation(
                object.optString("key"),
                object.optString("title"),
                object.optString("subtitle"),
                object.optString("preview"),
                object.optLong("timestamp"),
                object.optString("source", SOURCE_NOTIFICATION),
                object.optString("remoteUserId")
        );
    }
}
