package com.ktai.yawmi;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String ACTION_FIRE = "com.ktai.yawmi.FIRE";
    public static final String ACTION_DONE = "com.ktai.yawmi.DONE";
    public static final String ACTION_DISMISS = "com.ktai.yawmi.DISMISS";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        String id = intent == null ? null : intent.getStringExtra("id");
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        if (ACTION_DONE.equals(action)) {
            if (id != null) {
                markDone(ctx, id);
                nm.cancel(id.hashCode());
            }
            return;
        }
        if (ACTION_DISMISS.equals(action)) {
            if (id != null) nm.cancel(id.hashCode());
            return;
        }

        // FIRE — إشعار النظام بصوت التنبيه حتى والتطبيق مقفل
        if (id == null) return;
        String text = intent.getStringExtra("text");

        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra("task_id", id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentPi = PendingIntent.getActivity(ctx, id.hashCode(), open, flags);

        Intent doneI = new Intent(ctx, ReminderReceiver.class);
        doneI.setAction(ACTION_DONE);
        doneI.putExtra("id", id);
        PendingIntent donePi = PendingIntent.getBroadcast(ctx, ("done:" + id).hashCode(), doneI, flags);

        Intent disI = new Intent(ctx, ReminderReceiver.class);
        disI.setAction(ACTION_DISMISS);
        disI.putExtra("id", id);
        PendingIntent disPi = PendingIntent.getBroadcast(ctx, ("dis:" + id).hashCode(), disI, flags);

        Notification.Builder b;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(ctx, MainActivity.CHANNEL_ID);
        } else {
            b = new Notification.Builder(ctx).setPriority(Notification.PRIORITY_MAX);
        }
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("تذكير من يومي ⏰")
                .setContentText(text == null ? "لديك مهمة مستحقة" : text)
                .setStyle(new Notification.BigTextStyle().bigText(text == null ? "لديك مهمة مستحقة" : text))
                .setAutoCancel(true)
                .setContentIntent(contentPi)
                .setCategory(Notification.CATEGORY_ALARM)
                .addAction(new Notification.Action.Builder(0, "تم ✓", donePi).build())
                .addAction(new Notification.Action.Builder(0, "لاحقًا", disPi).build());
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            b.setFullScreenIntent(contentPi, true);
        }
        nm.notify(id.hashCode(), b.build());
    }

    private void markDone(Context ctx, String id) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences("yawmi_native", Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString("done_ids", "[]"));
            arr.put(id);
            sp.edit().putString("done_ids", arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}
