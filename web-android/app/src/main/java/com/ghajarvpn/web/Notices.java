package com.ghajarvpn.web;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Posts Ghajar notices on the notification shade, one entry per notice. */
final class Notices {
    static final String GENERAL = "ghajar_general";
    static final String IMPORTANT = "ghajar_important";

    private Notices() {}

    static void channels(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel general = new NotificationChannel(GENERAL, "اعلان‌های فروشگاه", NotificationManager.IMPORTANCE_DEFAULT);
        general.setDescription("پیام‌ها، اطلاعیه‌ها و کدهای تخفیف");
        NotificationChannel important = new NotificationChannel(IMPORTANT, "هشدار سرویس", NotificationManager.IMPORTANCE_HIGH);
        important.setDescription("هشدار حجم و زمان سرویس و وضعیت فروشگاه");
        nm.createNotificationChannel(general);
        nm.createNotificationChannel(important);
    }

    static void post(Context ctx, String id, String title, String body, String route, boolean important) {
        if (Build.VERSION.SDK_INT >= 33
                && ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        channels(ctx);
        String tag = id == null || id.isEmpty() ? "ghajar" : id;
        Intent open = new Intent(ctx, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_ROUTE, route == null ? "" : route);
        PendingIntent tap = PendingIntent.getActivity(ctx, tag.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(ctx, important ? IMPORTANT : GENERAL)
                .setSmallIcon(R.drawable.ic_stat_ghajar)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(tap)
                .setColor(0xFF00A86B)
                .build();
        nm.notify(tag, 1, n);
    }
}
