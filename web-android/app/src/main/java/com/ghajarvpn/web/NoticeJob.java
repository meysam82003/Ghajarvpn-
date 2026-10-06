package com.ghajarvpn.web;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Checks the notice feed while the app is closed (every 15 minutes, the
 * shortest Android allows), the same feed and the same "shown" report the
 * web app and the Ghajar Android app use: volume and time warnings, shop
 * announcements and discount codes, payment results and messages.
 */
public class NoticeJob extends JobService {
    private static final int JOB_ID = 4101;

    static void schedule(Context ctx) {
        JobScheduler js = ctx.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, NoticeJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build();
        js.schedule(job);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                check(getApplicationContext());
            } catch (Exception ignored) {
                // Offline or signed out: the next run tries again.
            }
            jobFinished(params, false);
        }, "ghajar-notices").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    static void check(Context ctx) throws Exception {
        String token = ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE).getString(Config.KEY_TOKEN, "");
        if (token == null || token.isEmpty()) return;
        JSONObject feed = new JSONObject(request(Config.API_BASE + "notices.php?action=feed&client=app", token, null));
        if (!feed.optBoolean("status", false)) return;
        JSONArray rows = feed.optJSONArray("notices");
        if (rows == null) return;
        JSONArray shown = new JSONArray();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !row.optBoolean("should_float", true)) continue;
            String body = row.optString("body", "").trim();
            long id = row.optLong("id", 0);
            if (body.isEmpty() || id == 0) continue;
            String kind = row.optString("kind", "");
            String title = row.optString("title", "").trim();
            if (title.isEmpty()) {
                title = kind.equals("service_time") ? "مهلت سرویس رو به پایان است"
                        : kind.equals("service_volume") ? "حجم سرویس رو به پایان است"
                        : kind.equals("shop_status") ? "وضعیت فروشگاه" : "اعلان قاجار وی پی ان";
            }
            String action = row.optString("action", "none");
            String ref = row.optString("action_ref", "");
            String route = "#/notices";
            if (action.equals("market_shop")) {
                String[] parts = ref.split("\\|", 2);
                if (parts[0].matches("\\d+")) {
                    route = "#/shop?shop=" + parts[0] + (parts.length > 1 && !parts[1].isEmpty() ? "&code=" + parts[1].replaceAll("[^\\w-]", "") : "");
                }
            } else if (action.equals("renew") && !ref.isEmpty()) {
                route = "#/shop?renew=" + java.net.URLEncoder.encode(ref, "UTF-8");
            }
            boolean important = kind.equals("shop_status") || kind.equals("service_time") || kind.equals("service_volume");
            Notices.post(ctx, "notice:" + id, title, body, route, important);
            shown.put(id);
        }
        if (shown.length() > 0) {
            request(Config.API_BASE + "notices.php?action=shown&client=app", token, new JSONObject().put("ids", shown).toString());
        }
    }

    private static String request(String url, String token, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("X-Ghajar-Client", "app");
        if (body != null) {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        try (InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream()) {
            if (in == null) return "{}";
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            return buf.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
