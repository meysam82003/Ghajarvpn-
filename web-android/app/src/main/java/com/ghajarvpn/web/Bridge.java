package com.ghajarvpn.web;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** What the page sees as window.ghajarNative. */
final class Bridge {
    private final MainActivity activity;

    Bridge(MainActivity activity) {
        this.activity = activity;
    }

    /** A notice on the notification shade, as the web app posts it while open. */
    @JavascriptInterface
    public void notify(String id, String title, String body, String route, boolean important) {
        Notices.post(activity, id, title, body, route, important);
    }

    /** The account token, kept so notices can be checked while the app is closed. */
    @JavascriptInterface
    public void setToken(String token) {
        activity.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE).edit()
                .putString(Config.KEY_TOKEN, token == null ? "" : token.trim()).apply();
        if (token != null && !token.trim().isEmpty()) NoticeJob.schedule(activity);
    }

    /** A config file (.conf / .ovpn) into Downloads. */
    @JavascriptInterface
    public void saveFile(String name, String mime, String text) throws Exception {
        String safe = name == null ? "ghajar.txt" : name.replaceAll("[^\\w.\\-]", "_");
        byte[] data = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, safe);
            v.put(MediaStore.Downloads.MIME_TYPE, mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IllegalStateException("no download slot");
            try (OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("no stream");
                out.write(data);
            }
        } else {
            File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            try (FileOutputStream out = new FileOutputStream(new File(dir, safe))) {
                out.write(data);
            }
        }
    }

    @JavascriptInterface
    public void share(String title, String text) {
        activity.runOnUiThread(() -> {
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, title);
            activity.startActivity(Intent.createChooser(send, title));
        });
    }

    @JavascriptInterface
    public void retry() {
        activity.reload();
    }
}
