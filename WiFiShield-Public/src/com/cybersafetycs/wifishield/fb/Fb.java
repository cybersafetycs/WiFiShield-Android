package com.cybersafetycs.wifishield.fb;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.cybersafetycs.wifishield.LogStore;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.HashMap;
import java.util.Map;

/**
 * Real Firebase feature wiring: Remote Config, Crashlytics, App Check, FCM.
 * Lives in fb/ so the SDK-less build.sh fallback (which never compiles this
 * package) still works; MainActivity reaches it only through FbKit reflection.
 */
public final class Fb {

    public static final String FCM_TOPIC = "wifishield";
    private static final long RC_REFRESH_MS = 10 * 60 * 1000L;
    private static final long AC_REFRESH_MS = 30 * 60 * 1000L;

    private static volatile String banner = "";
    private static volatile int intervalSec = 45;
    private static volatile String fcmToken = "";
    private static volatile String appCheckToken = "";
    private static volatile String rcStatus = "not fetched";
    private static volatile String appCheckStatus = "not initialized";
    private static volatile String crashStatus = "not initialized";
    private static volatile String fcmStatus = "not initialized";
    private static volatile boolean ready = false;

    private static final Handler ui = new Handler(Looper.getMainLooper());

    private Fb() {}

    public static boolean isReady() { return ready; }
    public static String banner() { return banner; }
    public static int intervalSec() { return intervalSec; }
    public static String fcmToken() { return fcmToken; }
    public static String appCheckToken() { return appCheckToken; }
    public static String rcStatus() { return rcStatus; }
    public static String appCheckStatus() { return appCheckStatus; }
    public static String crashStatus() { return crashStatus; }
    public static String fcmStatus() { return fcmStatus; }

    /** Called once from MainActivity.onCreate (main thread). */
    public static synchronized void init(Context ctx) {
        if (ready) return;
        ready = true;
        final Context app = ctx.getApplicationContext();

        // ---- Crashlytics ----
        try {
            FirebaseCrashlytics cl = FirebaseCrashlytics.getInstance();
            cl.setCustomKey("build_flavor", "gradle");
            cl.log("Fb.init");
            crashStatus = "enabled";
        } catch (Throwable t) {
            crashStatus = "error: " + t.getMessage();
        }

        // ---- App Check (Play Integrity; enforcement stays OFF in console
        //      because the REST sync client + sideloaded APK) ----
        try {
            FirebaseAppCheck ac = FirebaseAppCheck.getInstance();
            ac.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance());
            appCheckStatus = "provider installed (Play Integrity)";
            refreshAppCheck(ac);
        } catch (Throwable t) {
            appCheckStatus = "error: " + t.getMessage();
        }

        // ---- Remote Config ----
        try {
            FirebaseRemoteConfig rc = FirebaseRemoteConfig.getInstance();
            Map<String, Object> defs = new HashMap<>();
            defs.put("scan_interval_sec", 45L);
            defs.put("banner_text", "");
            rc.setDefaultsAsync(defs);
            FirebaseRemoteConfigSettings cs =
                    new FirebaseRemoteConfigSettings.Builder()
                            .setMinimumFetchIntervalInSeconds(300)
                            .build();
            rc.setConfigSettingsAsync(cs);
            rc.fetchAndActivate()
                    .addOnCompleteListener(task -> applyRemoteConfig(rc));
        } catch (Throwable t) {
            rcStatus = "error: " + t.getMessage();
        }

        // ---- FCM: subscribe to broadcast topic + fetch device token ----
        try {
            FirebaseMessaging fm = FirebaseMessaging.getInstance();
            fm.subscribeToTopic(FCM_TOPIC)
                    .addOnCompleteListener(t -> {
                        fcmStatus = t.isSuccessful()
                                ? "subscribed: " + FCM_TOPIC
                                : "topic failed: " + (t.getException() != null
                                        ? t.getException().getMessage() : "?");
                        LogStore.log(app, "FCM topic: " + fcmStatus);
                    });
            fm.getToken().addOnCompleteListener(t -> {
                if (t.isSuccessful() && t.getResult() != null) {
                    fcmToken = t.getResult();
                    fcmStatus = "token ok, topic: " + FCM_TOPIC;
                    LogStore.log(app, "FCM token: " + shortTok(fcmToken));
                } else {
                    fcmStatus = "token failed: " + (t.getException() != null
                            ? t.getException().getMessage() : "?");
                    LogStore.log(app, "FCM token failed");
                }
            });
        } catch (Throwable t) {
            fcmStatus = "error: " + t.getMessage();
        }

        scheduleRefresh(app);
    }

    /** Record a non-fatal error visible in console → Crashlytics. */
    public static void nonFatal(String where, String err) {
        try {
            if (!ready) return;
            FirebaseCrashlytics cl = FirebaseCrashlytics.getInstance();
            cl.log(where + ": " + err);
            cl.recordException(new RuntimeException(where + ": " + err));
        } catch (Throwable ignored) {}
    }

    /** Breadcrumb in Crashlytics logs (also searchable per crash). */
    public static void breadcrumb(String msg) {
        try {
            if (!ready) return;
            FirebaseCrashlytics.getInstance().log(msg);
        } catch (Throwable ignored) {}
    }

    /** Called from MessagingService when a fresh FCM token arrives. */
    static void onNewToken(String tok) {
        fcmToken = tok;
        fcmStatus = "token refreshed, topic: " + FCM_TOPIC;
        if (lastApp != null) LogStore.log(lastApp, "FCM token refreshed: " + shortTok(tok));
    }

    private static Context lastApp;

    private static void scheduleRefresh(final Context app) {
        lastApp = app;
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    FirebaseRemoteConfig rc = FirebaseRemoteConfig.getInstance();
                    rc.fetchAndActivate().addOnCompleteListener(t -> applyRemoteConfig(rc));
                } catch (Throwable ignored) {}
                try {
                    refreshAppCheck(FirebaseAppCheck.getInstance());
                } catch (Throwable ignored) {}
                ui.postDelayed(this, RC_REFRESH_MS);
            }
        }, RC_REFRESH_MS);
    }

    private static void applyRemoteConfig(FirebaseRemoteConfig rc) {
        try {
            long iv = rc.getLong("scan_interval_sec");
            if (iv >= 10 && iv <= 3600) intervalSec = (int) iv;
            banner = rc.getString("banner_text");
            rcStatus = "fetched, interval=" + intervalSec + "s"
                    + (banner.isEmpty() ? "" : ", banner on");
        } catch (Throwable t) {
            rcStatus = "activate error: " + t.getMessage();
        }
    }

    private static void refreshAppCheck(FirebaseAppCheck ac) {
        ac.getAppCheckToken(false).addOnCompleteListener(t -> {
            if (t.isSuccessful() && t.getResult() != null) {
                AppCheckToken tok = t.getResult();
                appCheckToken = tok.getToken();
                appCheckStatus = "token ok (ttl " + tok.getExpireTimeMillis() + " ms)";
            } else {
                String e = t.getException() != null
                        ? t.getException().getMessage() : "?";
                appCheckToken = "";
                appCheckStatus = "attestation failed (expected when sideloaded): "
                        + clip(e, 60);
            }
        });
    }

    static String shortTok(String t) {
        if (t == null || t.length() < 16) return String.valueOf(t);
        return t.substring(0, 12) + "…" + t.substring(t.length() - 6);
    }

    private static String clip(String s, int n) {
        if (s == null) return "?";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
