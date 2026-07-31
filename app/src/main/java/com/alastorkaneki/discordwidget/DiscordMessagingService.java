package com.alastorkaneki.discordwidget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public final class DiscordMessagingService extends Service {
    private static final String CHANNEL_ID = "discord_messaging_connection";
    private static final int NOTIFICATION_ID = 3012;
    private static final long REFRESH_INTERVAL_MILLIS = 300_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private DiscordSocialBridge bridge;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (bridge != null && bridge.isConnected()) {
                bridge.refreshDirectMessages();
                handler.postDelayed(this, REFRESH_INTERVAL_MILLIS);
            } else {
                stopSelf();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        bridge = DiscordSocialBridge.get(this);
        createChannel();
        Notification notification = createNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(refresh);
        handler.post(refresh);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(refresh);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.messaging_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.messaging_channel_description));
        manager.createNotificationChannel(channel);
    }

    private Notification createNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(getString(R.string.messaging_service_title))
                .setContentText(getString(R.string.messaging_service_text))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }
}
