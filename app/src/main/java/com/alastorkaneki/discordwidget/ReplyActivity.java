package com.alastorkaneki.discordwidget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public final class ReplyActivity extends Activity implements DiscordSocialBridge.Listener {
    public static final String EXTRA_CONVERSATION_KEY = "conversation_key";

    private Conversation conversation;
    private DiscordSocialBridge socialBridge;
    private EditText input;
    private Button send;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        Button open = findViewById(R.id.replyOpenDiscord);

        title.setText(conversation.title);
        socialBridge = DiscordSocialBridge.get(this);
        socialBridge.initialize(this);

        send.setOnClickListener(view -> sendMessage());
        open.setOnClickListener(view -> openDiscord());
        if (Conversation.SOURCE_SOCIAL_DM.equals(conversation.source)) {
            send.setEnabled(socialBridge.isConnected());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        socialBridge.initialize(this);
        socialBridge.addListener(this);
        if (Conversation.SOURCE_SOCIAL_DM.equals(conversation.source)) {
            send.setEnabled(socialBridge.isConnected());
        }
    }

    @Override
    protected void onStop() {
        socialBridge.removeListener(this);
        super.onStop();
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
            runOnUiThread(() -> send.setEnabled(true));
        }
    }

    @Override
    public void onConversation(String userId, String preview, long messageId) {
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            send.setEnabled(true);
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onMessageSent(String userId, long messageId) {
        runOnUiThread(() -> {
            Toast.makeText(this, R.string.message_sent, Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
