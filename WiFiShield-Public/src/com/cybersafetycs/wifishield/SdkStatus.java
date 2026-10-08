package com.cybersafetycs.wifishield;

import android.content.Context;
import android.os.Bundle;

/**
 * Firebase SDK status + Analytics events, accessed via reflection so the
 * SDK-less build.sh fallback still compiles while the Gradle build (with the
 * real Firebase SDK) gets live calls.
 */
final class SdkStatus {

    private SdkStatus() {}

    /** The real registered app id from the SDK, or null when not initialized. */
    static String appId() {
        try {
            Object app = Class.forName("com.google.firebase.FirebaseApp")
                    .getMethod("getInstance").invoke(null);
            Object opts = app.getClass().getMethod("getOptions").invoke(app);
            return String.valueOf(
                    opts.getClass().getMethod("getApplicationId").invoke(opts));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Fire an Analytics event (visible in console → Analytics → DebugView). */
    static void logEvent(Context ctx, String name, String key, Object value) {
        try {
            Class<?> fa = Class.forName(
                    "com.google.firebase.analytics.FirebaseAnalytics");
            Object inst = fa.getMethod("getInstance", Context.class)
                    .invoke(null, ctx.getApplicationContext());
            Bundle b = new Bundle();
            if (key != null) {
                if (value instanceof Integer) b.putInt(key, (Integer) value);
                else b.putString(key, String.valueOf(value));
            }
            fa.getMethod("logEvent", String.class, Bundle.class)
                    .invoke(inst, name, b);
        } catch (Throwable ignored) {
            // SDK-less build → no analytics
        }
    }
}
