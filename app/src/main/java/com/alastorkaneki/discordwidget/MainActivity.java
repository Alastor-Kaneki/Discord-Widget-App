package com.alastorkaneki.discordwidget;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public final class MainActivity extends AppCompatActivity implements DiscordSocialBridge.Listener {
    public static final String ACTION_CONVERSATIONS_CHANGED = "com.alastorkaneki.discordwidget.CONVERSATIONS_CHANGED";

    private ConversationAdapter adapter;
    private TextView status;
    private DiscordSocialBridge socialBridge;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshList();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.statusText);
        Button connectButton = findViewById(R.id.connectDiscordButton);
        Button notificationButton = findViewById(R.id.notificationAccessButton);
        Button refreshButton = findViewById(R.id.refreshButton);
        ListView list = findViewById(R.id.conversationList);

        adapter = new ConversationAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Conversation conversation = adapter.getConversation(position);
            Intent intent = new Intent(this, ReplyActivity.class);
            intent.putExtra(ReplyActivity.EXTRA_CONVERSATION_KEY, conversation.key);
            startActivity(intent);
        });

        socialBridge = new DiscordSocialBridge(this, this);
        connectButton.setEnabled(true);
        connectButton.setText(socialBridge.isAvailable()
                ? R.string.connect_discord
                : R.string.configure_discord_oauth);
        connectButton.setOnClickListener(view -> connectDiscord());
        notificationButton.setOnClickListener(view -> openNotificationAccess());
        refreshButton.setOnClickListener(view -> {
            refreshList();
            if (socialBridge.isAvailable()) {
                socialBridge.refreshDirectMessages();
            }
            ConversationWidgetProvider.updateAll(this);
        });

        status.setText(socialBridge.isAvailable()
                ? getString(R.string.social_ready_to_connect)
                : getString(R.string.oauth_unavailable_status, socialBridge.getUnavailableReason()));
        refreshList();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ACTION_CONVERSATIONS_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    @Override
    protected void onStop() {
        unregisterReceiver(receiver);
        super.onStop();
    }

    @Override
    public void onStatus(String value) {
        runOnUiThread(() -> {
            status.setText(value);
            if ("ready".equalsIgnoreCase(value)) {
                socialBridge.refreshDirectMessages();
            }
        });
    }

    @Override
    public void onConversation(String userId, String preview, long messageId) {
        Conversation conversation = new Conversation(
                "social:" + userId,
                getString(R.string.discord_user_id, userId),
                getString(R.string.oauth_dm),
                preview,
                System.currentTimeMillis(),
                Conversation.SOURCE_SOCIAL_DM,
                userId
        );
        ConversationStore.upsert(this, conversation);
        runOnUiThread(this::refreshList);
        ConversationWidgetProvider.updateAll(this);
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            status.setText(error);
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onMessageSent(String userId, long messageId) {
        runOnUiThread(() -> Toast.makeText(this, R.string.message_sent, Toast.LENGTH_SHORT).show());
        socialBridge.refreshDirectMessages();
    }

    private void connectDiscord() {
        if (socialBridge.isAvailable()) {
            status.setText(R.string.connecting_discord);
            socialBridge.connect();
            return;
        }
        showOAuthSetupDialog();
    }

    private void showOAuthSetupDialog() {
        String message = socialBridge.getUnavailableReason()
                + "\n\n"
                + getString(R.string.oauth_setup_requirements, socialBridge.getRedirectUri());
        new AlertDialog.Builder(this)
                .setTitle(R.string.oauth_setup_title)
                .setMessage(message)
                .setPositiveButton(R.string.open_developer_portal, (dialog, which) -> openDeveloperPortal())
                .setNeutralButton(R.string.enable_notification_access, (dialog, which) -> openNotificationAccess())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshList() {
        adapter.replace(ConversationStore.getAll(this));
    }

    private void openNotificationAccess() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void openDeveloperPortal() {
        try {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://discord.com/developers/applications")
            ));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.no_browser_available, Toast.LENGTH_LONG).show();
        }
    }
}