package com.alastorkaneki.discordwidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;

public final class WidgetConfigActivity extends Activity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ImmersiveMode.apply(this);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_widget_config);

        appWidgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
        );
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        ConversationAdapter adapter = new ConversationAdapter(this);
        adapter.replace(ConversationStore.getAll(this));
        ListView list = findViewById(R.id.widgetConversationList);
        TextView empty = findViewById(R.id.widgetEmptyText);
        list.setEmptyView(empty);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Conversation conversation = adapter.getConversation(position);
            ConversationStore.bindWidget(this, appWidgetId, conversation.key);
            ConversationWidgetProvider.update(this, AppWidgetManager.getInstance(this), appWidgetId);
            Intent result = new Intent();
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, result);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ImmersiveMode.apply(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            ImmersiveMode.apply(this);
        }
    }
}
