package com.ktai.yawmi;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;
    static final String CHANNEL_ID = "yawmi_reminders";
    private static final int REQ_NOTIF = 11;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createChannel();
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        web.setBackgroundColor(Color.parseColor("#0A0A0A"));
        web.addJavascriptInterface(new Bridge(), "YawmiNative");
        web.setWebViewClient(new WebViewClient());
        setContentView(web);
        String url = "file:///android_asset/www/index.html";
        String tid = getIntent() == null ? null : getIntent().getStringExtra("task_id");
        if (tid != null) url += "#task=" + Uri.encode(tid);
        web.loadUrl(url);
        requestNotifPermissionIfNeeded();
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "تذكيرات المهام", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("تذكيرات مهامك اليومية — صوت واهتزاز حتى والتطبيق مقفل");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 300, 120, 300});
        ch.setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        nm.createNotificationChannel(ch);
    }

    private void requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String tid = intent == null ? null : intent.getStringExtra("task_id");
        if (tid != null && web != null) {
            web.evaluateJavascript("window.__yawmiOpen&&window.__yawmiOpen(" + JSONObject.quote(tid) + ")", null);
        }
    }

    private class Bridge {

        @JavascriptInterface
        public void schedule(final String id, final String text, final long timeMillis) {
            try {
                AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                PendingIntent pi = reminderPendingIntent(id);
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            try {
                                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:" + getPackageName())));
                            } catch (Exception ignored) {}
                        }
                    });
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pi);
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pi);
                }
                addOrUpdatePref(id, text, timeMillis);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void cancel(String id) {
            try {
                AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                am.cancel(reminderPendingIntent(id));
                removePref(id);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public String getDoneIds() {
            try {
                SharedPreferences sp = getSharedPreferences("yawmi_native", MODE_PRIVATE);
                return sp.getString("done_ids", "[]");
            } catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface
        public void clearDoneIds() {
            try {
                SharedPreferences sp = getSharedPreferences("yawmi_native", MODE_PRIVATE);
                sp.edit().remove("done_ids").apply();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public boolean hasNotifPermission() {
            if (Build.VERSION.SDK_INT >= 33) {
                return checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
            }
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            return nm.areNotificationsEnabled();
        }

        @JavascriptInterface
        public void requestNotifPermission() {
            runOnUiThread(new Runnable() {
                @Override public void run() { requestNotifPermissionIfNeeded(); }
            });
        }
    }

    private PendingIntent reminderPendingIntent(String id) {
        Intent i = new Intent(this, ReminderReceiver.class);
        i.setAction(ReminderReceiver.ACTION_FIRE);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(this, id.hashCode(), i, flags);
    }

    private void addOrUpdatePref(String id, String text, long time) {
        try {
            SharedPreferences sp = getSharedPreferences("yawmi_native", MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString("reminders", "[]"));
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!id.equals(o.optString("id"))) out.put(o);
            }
            JSONObject n = new JSONObject();
            n.put("id", id);
            n.put("text", text);
            n.put("time", time);
            out.put(n);
            sp.edit().putString("reminders", out.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void removePref(String id) {
        try {
            SharedPreferences sp = getSharedPreferences("yawmi_native", MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString("reminders", "[]"));
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!id.equals(o.optString("id"))) out.put(o);
            }
            sp.edit().putString("reminders", out.toString()).apply();
        } catch (Exception ignored) {}
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
