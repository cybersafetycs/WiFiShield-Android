package com.cybersafetycs.wifishield;

import android.content.Context;

import java.lang.reflect.Method;

/**
 * Reflection facade over fb.Fb — same pattern as SdkStatus: the SDK-less
 * build.sh fallback compiles this file (fb/ is excluded) and every call
 * becomes a harmless no-op when fb.Fb is absent from the APK.
 */
public final class FbKit {

    private static boolean ready;
    private static Method mInit, mNonFatal, mCrumb, mBanner, mInterval,
            mFcmTok, mAcTok, mRc, mAc, mCrash, mFcm, mReady;

    static {
        try {
            Class<?> c = Class.forName("com.cybersafetycs.wifishield.fb.Fb");
            mReady = c.getMethod("isReady");
            mInit = c.getMethod("init", Context.class);
            mNonFatal = c.getMethod("nonFatal", String.class, String.class);
            mCrumb = c.getMethod("breadcrumb", String.class);
            mBanner = c.getMethod("banner");
            mInterval = c.getMethod("intervalSec");
            mFcmTok = c.getMethod("fcmToken");
            mAcTok = c.getMethod("appCheckToken");
            mRc = c.getMethod("rcStatus");
            mAc = c.getMethod("appCheckStatus");
            mCrash = c.getMethod("crashStatus");
            mFcm = c.getMethod("fcmStatus");
            ready = true;
        } catch (Throwable ignored) {
            ready = false; // build.sh fallback APK → all calls no-op
        }
    }

    private FbKit() {}

    public static boolean available() { return ready; }

    public static void init(Context ctx) {
        if (!ready) return;
        try { mInit.invoke(null, ctx); } catch (Throwable ignored) {}
    }

    public static void nonFatal(String where, String err) {
        if (!ready) return;
        try { mNonFatal.invoke(null, where, err); } catch (Throwable ignored) {}
    }

    public static void breadcrumb(String msg) {
        if (!ready) return;
        try { mCrumb.invoke(null, msg); } catch (Throwable ignored) {}
    }

    /** Remote Config banner_text ("" when SDK-less / not fetched). */
    public static String banner() {
        if (!ready) return "";
        try { return String.valueOf(mBanner.invoke(null)); } catch (Throwable t) { return ""; }
    }

    /** Remote Config scan_interval_sec (45 when SDK-less / not fetched). */
    public static int scanIntervalSec() {
        if (!ready) return 45;
        try { return (Integer) mInterval.invoke(null); } catch (Throwable t) { return 45; }
    }

    /** FCM device token ("" when SDK-less / not fetched). */
    public static String fcmToken() {
        if (!ready) return "";
        try { return String.valueOf(mFcmTok.invoke(null)); } catch (Throwable t) { return ""; }
    }

    /** App Check token for the REST sync header ("" when unavailable). */
    public static String appCheckToken() {
        if (!ready) return "";
        try { return String.valueOf(mAcTok.invoke(null)); } catch (Throwable t) { return ""; }
    }

    public static String rcStatus() {
        if (!ready) return "SDK not in this build";
        try { return String.valueOf(mRc.invoke(null)); } catch (Throwable t) { return "?"; }
    }

    public static String appCheckStatus() {
        if (!ready) return "SDK not in this build";
        try { return String.valueOf(mAc.invoke(null)); } catch (Throwable t) { return "?"; }
    }

    public static String crashStatus() {
        if (!ready) return "SDK not in this build";
        try { return String.valueOf(mCrash.invoke(null)); } catch (Throwable t) { return "?"; }
    }

    public static String fcmStatus() {
        if (!ready) return "SDK not in this build";
        try { return String.valueOf(mFcm.invoke(null)); } catch (Throwable t) { return "?"; }
    }
}
