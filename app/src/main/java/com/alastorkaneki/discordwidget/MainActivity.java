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
import android.view.View;
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
    private Button connectButton;
    private Button notificationButton;
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
        connectButton = findViewById(R.id.connectDiscordButton);
        notificationButton = findViewById(R.id.notificationAccessButton);
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

        socialBridge = DiscordSocialBridge.get(this);
        socialBridge.initialize(this);
        connectButton.setEnabled(true);
        connectButton.setOnClickListener(view -> connectDiscord());
        notificationButton.setOnClickListener(view -> openNotificationAccess());
        notificationButton.setVisibility(socialBridge.isAvailable() ? View.GONE : View.VISIBLE);
        refreshButton.setOnClickListener(view -> {
            refreshList();
            if (socialBridge.isConnected()) {
                socialBridge.refreshDirectMessages();
            }
            ConversationWidgetProvider.updateAll(this);
        });

        updateConnectionUi();
        refreshList();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        socialBridge.initialize(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        socialBridge.initialize(this);
        socialBridge.addListener(this);
        IntentFilter filter = new IntentFilter(ACTION_CONVERSATIONS_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        socialBridge.initialize(this);
        updateConnectionUi();
        if (socialBridge.isConnected()) {
            socialBridge.refreshDirectMessages();
        }
    }

    @Override
    protected void onStop() {
        socialBridge.removeListener(this);
        unregisterReceiver(receiver);
        super.onStop();
    }

    @Override
    public void onStatus(String value) {
        runOnUiThread(() -> {
            if ("ready".equalsIgnoreCase(value)) {
                status.setText(R.string.discord_connected_status);
                connectButton.setText(R.string.discord_connected);
                notificationButton.setVisibility(View.GONE);
                refreshList();
            } else {
                status.setText(value);
            }
        });
    }

    @Override
    public void onConversation(String userId, String preview, long messageId) {
        runOnUiThread(this::refreshList);
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
    }

    private void updateConnectionUi() {
        connectButton.setText(socialBridge.isConnected()
                ? R.string.discord_connected
                : R.string.connect_discord);
        notificationButton.setVisibility(socialBridge.isAvailable() ? View.GONE : View.VISIBLE);
        if (!socialBridge.isAvailable()) {
            status.setText(getString(
                    R.string.oauth_unavailable_status,
                    socialBridge.getUnavailableReason()
            ));
        } else if (socialBridge.isConnected()) {
            status.setText(R.string.discord_connected_status);
        } else if (socialBridge.hasStoredSession()) {
            status.setText(R.string.oauth_restoring_session);
        } else {
            status.setText(R.string.social_ready_to_connect);
        }
    }

    private void connectDiscord() {
        if (socialBridge.isAvailable()) {
            if (socialBridge.isConnected()) {
                socialBridge.refreshDirectMessages();
                return;
            }
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.oauth_setup_title)
                .setMessage(message)
                .setPositiveButton(
                        R.string.open_developer_portal,
                        (dialog, which) -> openDeveloperPortal()
                )
                .setNegativeButton(android.R.string.cancel, null);
        if (!socialBridge.isSdkBundled()) {
            builder.setNeutralButton(
                    R.string.optional_notification_fallback,
                    (dialog, which) -> openNotificationAccess()
            );
        }
        builder.show();
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
