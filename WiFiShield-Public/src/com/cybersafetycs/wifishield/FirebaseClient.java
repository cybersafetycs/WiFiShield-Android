package com.cybersafetycs.wifishield;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Firebase connection driven by assets/google-services.json (the real config
 * from the user's Firebase project "wifishield-1feea"). No SDK/Gradle — plain
 * HTTPS REST so the hand-rolled build keeps working:
 *
 *   1. anonymous sign-in via identitytoolkit (needs Auth → Anonymous enabled)
 *   2. PUT scan snapshots to  /scans/<ts>.json
 *   3. PUT report metadata to /reports/<ts>.json
 *
 * Throttled (30 s) and skips payloads identical to the last upload.
 */
public class FirebaseClient {

    private final Context ctx;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();

    private String projectId = "";
    private String apiKey = "";
    private String idToken;
    private long lastQueueMs = 0;
    private String lastBody = "";
    private volatile String lastStatus = "not initialized";
    private volatile long lastSyncAt = 0;

    public FirebaseClient(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        loadConfig();
    }

    // ---- config from google-services.json ---------------------------------
    private void loadConfig() {
        try (InputStream in = ctx.getAssets().open("google-services.json")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            JSONObject root = new JSONObject(
                    new String(bos.toByteArray(), StandardCharsets.UTF_8));
            projectId = root.getJSONObject("project_info").getString("project_id");
            apiKey = root.getJSONArray("client").getJSONObject(0)
                    .getJSONArray("api_key").getJSONObject(0).getString("current_key");
            lastStatus = "config loaded (" + projectId + ")";
        } catch (Exception e) {
            lastStatus = "google-services.json error: " + e.getMessage();
        }
    }

    public boolean isReady() {
        return !projectId.isEmpty() && !apiKey.isEmpty();
    }

    public String status() { return lastStatus; }

    public long lastSyncAt() { return lastSyncAt; }

    // ---- anonymous auth (REST) ---------------------------------------------
    private synchronized String token() throws Exception {
        if (idToken != null && !idToken.isEmpty()) return idToken;
        HttpURLConnection c = open(
                "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + apiKey);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.getOutputStream().write(
                "{\"returnSecureToken\":true}".getBytes(StandardCharsets.UTF_8));
        String resp = read(c);
        if (c.getResponseCode() >= 400) {
            lastStatus = "auth error " + c.getResponseCode()
                    + " (enable Auth → Anonymous in Firebase console)";
            throw new RuntimeException(lastStatus);
        }
        idToken = new JSONObject(resp).getString("idToken");
        lastStatus = "firebase ok (" + projectId + ")";
        return idToken;
    }

    // ---- RTDB REST PUT -------------------------------------------------------
    private boolean put(String path, String body) throws Exception {
        String t = token();
        int code = putOnce(path, body, t);
        if (code == 401 || code == 403) {          // token expired → refresh once
            synchronized (this) { idToken = null; }
            t = token();
            code = putOnce(path, body, t);
        }
        if (code >= 200 && code < 300) {
            lastSyncAt = System.currentTimeMillis();
            lastStatus = "synced " + path.substring(0, path.indexOf('.'))
                    + " (" + projectId + ")";
            return true;
        }
        lastStatus = "RTDB HTTP " + code + " — create Realtime Database in console"
                + " (or check rules)";
        return false;
    }

    private int putOnce(String path, String body, String t) throws Exception {
        HttpURLConnection c = open(dbBase() + "/" + path + "?auth=" + t);
        c.setRequestMethod("PUT");
        c.setDoOutput(true);
        String ac = FbKit.appCheckToken();          // empty until App Check works
        if (!ac.isEmpty()) c.setRequestProperty("X-Firebase-AppCheck", ac);
        c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        read(c);                                    // drain
        return c.getResponseCode();
    }

    private String dbBase() {
        return "https://" + projectId + "-default-rtdb.firebaseio.com";
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        return c;
    }

    private static String read(HttpURLConnection c) throws Exception {
        InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
        if (in == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    // ---- public sync entry points ------------------------------------------
    /** Upload a full scan snapshot (throttled 30 s, skipped when unchanged). */
    public void syncScans(final List<Net> nets) {
        if (!isReady()) return;
        final long now = System.currentTimeMillis();
        if (now - lastQueueMs < 30_000L) return;
        lastQueueMs = now;
        final String body;
        try {
            body = scanJson(nets, now);
        } catch (Exception e) {
            lastStatus = "json error: " + e.getMessage();
            return;
        }
        if (body.equals(lastBody)) {
            lastStatus = "up to date (" + projectId + ")";
            return;
        }
        ex.execute(() -> {
            try {
                if (put("scans/" + now + ".json", body)) lastBody = body;
            } catch (Exception e) {
                lastStatus = "sync failed: " + e.getMessage();
            }
        });
    }

    /** Upload metadata of a generated PDF report. */
    public void syncReport(final java.io.File pdf, final Net net, final int score) {
        if (!isReady()) return;
        ex.execute(() -> {
            try {
                JSONObject o = new JSONObject();
                o.put("ts", System.currentTimeMillis());
                o.put("file", pdf.getName());
                o.put("size", pdf.length());
                o.put("ssid", net.displayName());
                o.put("bssid", net.bssid);
                o.put("score", score);
                o.put("security", net.security);
                o.put("signal", net.signalDbm);
                o.put("device", Build.MODEL != null ? Build.MODEL : "");
                o.put("pkg", "com.cybersafetycs.wifishield");
                put("reports/" + o.getLong("ts") + ".json", o.toString());
            } catch (Exception e) {
                lastStatus = "report sync failed: " + e.getMessage();
            }
        });
    }

    /** Store the FCM device token at /fcm/latest.json (console lookup aid). */
    public void syncFcmToken(final String token) {
        if (!isReady() || token == null || token.isEmpty()) return;
        ex.execute(() -> {
            try {
                JSONObject o = new JSONObject();
                o.put("token", token);
                o.put("ts", System.currentTimeMillis());
                o.put("device", Build.MODEL != null ? Build.MODEL : "");
                o.put("pkg", "com.cybersafetycs.wifishield");
                put("fcm/latest.json", o.toString());
            } catch (Exception e) {
                lastStatus = "fcm token sync failed: " + e.getMessage();
            }
        });
    }

    private String scanJson(List<Net> nets, long now) throws Exception {
        JSONObject root = new JSONObject();
        root.put("ts", now);
        root.put("pkg", "com.cybersafetycs.wifishield");
        root.put("device", Build.MODEL != null ? Build.MODEL : "");
        root.put("count", nets.size());
        JSONArray arr = new JSONArray();
        for (Net n : nets) {
            JSONObject o = new JSONObject();
            o.put("ssid", n.displayName());
            o.put("bssid", n.bssid);
            o.put("dbm", n.signalDbm);
            o.put("freq", n.frequency);
            o.put("ch", n.channel);
            o.put("band", n.band());
            o.put("sec", n.security);
            if (!n.standard.isEmpty()) o.put("std", n.standard);
            if (!n.width.isEmpty()) o.put("width", n.width);
            if (n.isConnected) o.put("connected", true);
            arr.put(o);
        }
        root.put("networks", arr);
        return root.toString();
    }
}
