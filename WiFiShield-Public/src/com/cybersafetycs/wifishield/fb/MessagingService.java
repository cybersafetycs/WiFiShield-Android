package com.cybersafetycs.wifishield.fb;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import com.cybersafetycs.wifishield.FirebaseClient;
import com.cybersafetycs.wifishield.LogStore;
import com.cybersafetycs.wifishield.MainActivity;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/** FCM entry point: push messages → notification, new tokens → sync to RTDB. */
public class MessagingService extends FirebaseMessagingService {

    public static final String CHANNEL_ID = "wifishield_alerts";

    @Override
    public void onMessageReceived(RemoteMessage rm) {
        String title = null, body = null;
        if (rm.getNotification() != null) {
            title = rm.getNotification().getTitle();
            body = rm.getNotification().getBody();
        }
        Map<String, String> d = rm.getData();
        if (title == null) title = d.get("title");
        if (body == null) body = d.get("body");
        if (title == null || title.isEmpty()) title = "WiFiShield";
        if (body == null) body = "";
        show(title, body);
        LogStore.log(this, "FCM message received: " + short1(body));
    }

    @Override
    public void onNewToken(String token) {
        Fb.onNewToken(token);
        try {
            new FirebaseClient(this).syncFcmToken(token);
        } catch (Throwable t) {
            LogStore.log(this, "FCM token sync failed: " + t.getMessage());
        }
    }

    private void show(String title, String body) {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26
                    && nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID, "WiFiShield alerts",
                        NotificationManager.IMPORTANCE_DEFAULT));
            }
            Intent it = new Intent(this, MainActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, it,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(com.cybersafetycs.wifishield.R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), n);
        } catch (Throwable t) {
            LogStore.log(this, "notify failed: " + t.getMessage());
        }
    }

    private static String short1(String s) {
        if (s == null) return "";
        return s.length() <= 40 ? s : s.substring(0, 40) + "…";
    }
}
