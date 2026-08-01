package com.alastorkaneki.discordwidget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

public final class ReplyActivity extends Activity implements DiscordSocialBridge.Listener {
    public static final String EXTRA_CONVERSATION_KEY = "conversation_key";

    private Conversation conversation;
    private DiscordSocialBridge socialBridge;
    private EditText input;
    private Button send;
    private TextView history;
    private TextView historyStatus;
    private ProgressBar historyProgress;
    private ScrollView historyScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ImmersiveMode.apply(this);
        setContentView(R.layout.activity_reply);

        String key = getIntent().getStringExtra(EXTRA_CONVERSATION_KEY);
        conversation = ConversationStore.get(this, key == null ? "" : key);
        if (conversation == null) {
            finish();
            return;
        }

        TextView title = findViewById(R.id.replyTitle);
        input = findViewById(R.id.replyInput);
        send = findViewById(R.id.replySend);
        history = findViewById(R.id.replyHistory);
        historyStatus = findViewById(R.id.replyHistoryStatus);
        historyProgress = findViewById(R.id.replyHistoryProgress);
        historyScroll = findViewById(R.id.replyHistoryScroll);
        Button refresh = findViewById(R.id.replyRefreshHistory);
        Button open = findViewById(R.id.replyOpenDiscord);

        title.setText(conversation.title);
        socialBridge = DiscordSocialBridge.get(this);
        socialBridge.initialize(this);

        send.setOnClickListener(view -> sendMessage());
        refresh.setOnClickListener(view -> loadHistory());
        open.setOnClickListener(view -> openDiscord());
        if (Conversation.SOURCE_SOCIAL_DM.equals(conversation.source)) {
            send.setEnabled(socialBridge.isConnected());
        } else {
            historyStatus.setText(R.string.notification_history_unavailable);
            historyProgress.setVisibility(View.GONE);
            refresh.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        socialBridge.initialize(this);
        socialBridge.addListener(this);
        if (Conversation.SOURCE_SOCIAL_DM.equals(conversation.source)) {
            send.setEnabled(socialBridge.isConnected());
            if (socialBridge.isConnected()) {
                loadHistory();
            }
        }
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

    @Override
    protected void onStop() {
        socialBridge.removeListener(this);
        super.onStop();
    }

    private void loadHistory() {
        if (!Conversation.SOURCE_SOCIAL_DM.equals(conversation.source)) {
            return;
        }
        if (!socialBridge.isConnected()) {
            historyProgress.setVisibility(View.GONE);
            historyStatus.setText(R.string.oauth_reconnecting);
            return;
        }
        historyProgress.setVisibility(View.VISIBLE);
        historyStatus.setText(R.string.loading_message_history);
        socialBridge.fetchDirectMessageHistory(conversation.remoteUserId, 200);
    }

    private void sendMessage() {
        String message = input.getText().toString().trim();
        if (message.isEmpty()) {
            return;
        }
        send.setEnabled(false);
        if (Conversation.SOURCE_SOCIAL_DM.equals(conversation.source)) {
            if (!socialBridge.isAvailable()) {
                send.setEnabled(true);
                Toast.makeText(this, socialBridge.getUnavailableReason(), Toast.LENGTH_LONG).show();
                return;
            }
            if (!socialBridge.isConnected()) {
                send.setEnabled(true);
                Toast.makeText(this, R.string.oauth_reconnecting, Toast.LENGTH_LONG).show();
                return;
            }
            if (conversation.remoteUserId.isEmpty()) {
                send.setEnabled(true);
                Toast.makeText(this, R.string.invalid_discord_recipient, Toast.LENGTH_LONG).show();
                return;
            }
            socialBridge.sendDirectMessage(conversation.remoteUserId, message);
            return;
        }
        boolean sent = DiscordNotificationListener.reply(this, conversation.key, message);
        if (sent) {
            Toast.makeText(this, R.string.message_sent, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            send.setEnabled(true);
            Toast.makeText(this, R.string.reply_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void renderHistory(String messagesJson) {
        try {
            JSONArray messages = new JSONArray(messagesJson);
            if (messages.length() == 0) {
                history.setText("");
                historyStatus.setText(R.string.no_message_history);
                historyProgress.setVisibility(View.GONE);
                return;
            }
            StringBuilder text = new StringBuilder();
            DateFormat dateFormat = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT,
                    DateFormat.SHORT
            );
            for (int index = 0; index < messages.length(); index++) {
                JSONObject message = messages.getJSONObject(index);
                boolean outgoing = message.optBoolean("outgoing");
                String author = outgoing
                        ? getString(R.string.you)
                        : message.optString("authorName", conversation.title);
                if (author.isEmpty()) {
                    author = conversation.title;
                }
                String content = message.optString("content");
                if (content.isEmpty()) {
                    content = getString(R.string.non_text_message);
                }
                long timestamp = message.optLong("timestamp");
                text.append(author);
                if (timestamp > 0L) {
                    text.append(" • ").append(dateFormat.format(new Date(timestamp)));
                }
                text.append('\n').append(content);
                if (index + 1 < messages.length()) {
                    text.append("\n\n");
                }
            }
            history.setText(text.toString());
            historyStatus.setText(getResources().getQuantityString(
                    R.plurals.message_count,
                    messages.length(),
                    messages.length()
            ));
            historyProgress.setVisibility(View.GONE);
            historyScroll.post(() -> historyScroll.fullScroll(View.FOCUS_DOWN));
        } catch (JSONException error) {
            historyProgress.setVisibility(View.GONE);
            historyStatus.setText(R.string.message_history_parse_failed);
        }
    }

    private void openDiscord() {
        Intent intent = getPackageManager().getLaunchIntentForPackage("com.discord");
        if (intent == null) {
            intent = getPackageManager().getLaunchIntentForPackage("com.discord.beta");
        }
        if (intent == null) {
            intent = getPackageManager().getLaunchIntentForPackage("com.discord.canary");
        }
        if (intent == null) {
            Toast.makeText(this, R.string.discord_not_installed, Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(intent);
    }

    @Override
    public void onStatus(String status) {
        if ("ready".equalsIgnoreCase(status)) {
            runOnUiThread(() -> {
                send.setEnabled(true);
                loadHistory();
            });
        }
    }

    @Override
    public void onConversation(String userId, String preview, long messageId) {
        if (conversation.remoteUserId.equals(userId)) {
            runOnUiThread(this::loadHistory);
        }
    }

    @Override
    public void onMessageHistory(String userId, String messagesJson) {
        if (conversation.remoteUserId.equals(userId)) {
            runOnUiThread(() -> renderHistory(messagesJson));
        }
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            send.setEnabled(true);
            historyProgress.setVisibility(View.GONE);
            historyStatus.setText(error);
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onMessageSent(String userId, long messageId) {
        if (!conversation.remoteUserId.equals(userId)) {
            return;
        }
        runOnUiThread(() -> {
            input.setText("");
            send.setEnabled(true);
            Toast.makeText(this, R.string.message_sent, Toast.LENGTH_SHORT).show();
            loadHistory();
        });
    }
}
