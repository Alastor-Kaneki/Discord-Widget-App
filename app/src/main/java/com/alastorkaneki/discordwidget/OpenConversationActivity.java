package com.alastorkaneki.discordwidget;

import android.app.Activity;
import android.os.Bundle;

public final class OpenConversationActivity extends Activity {
    public static final String EXTRA_CONVERSATION_KEY = "conversation_key";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String key = getIntent().getStringExtra(EXTRA_CONVERSATION_KEY);
        if (key != null) {
            DiscordNotificationListener.open(this, key);
        }
        finish();
    }
}
