package com.cybersafetycs.wifishield;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** App activity log + reports directory (mirrors desktop alog + reports dir). */
public final class LogStore {

    private LogStore() {}

    public static File logFile(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "logs");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "wifishield.log");
    }

    public static File reportsDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "reports");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    public static void log(Context ctx, String entry) {
        log(ctx, entry, "INFO");
    }

    public static void log(Context ctx, String entry, String level) {
        try {
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date());
            String line = "[" + ts + "] [" + level + "] " + entry + "\n";
            try (FileOutputStream fos = new FileOutputStream(logFile(ctx), true)) {
                fos.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    /** Last n log lines (newest at bottom, like the desktop viewer). */
    public static List<String> readLog(Context ctx, int maxLines) {
        List<String> out = new ArrayList<>();
        File f = logFile(ctx);
        if (!f.exists()) return out;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) out.add(line);
        } catch (Exception ignored) {}
        if (out.size() > maxLines) out = out.subList(out.size() - maxLines, out.size());
        return out;
    }

    public static void logScan(Context ctx, int count, String backend, String err) {
        if (err != null && !err.isEmpty()) {
            log(ctx, "Scan failed via " + backend + ": " + err, "ERROR");
        } else {
            log(ctx, "Scan complete: " + count + " networks via " + backend);
        }
    }

    /** Generated PDF reports, newest first. */
    public static File[] reports(Context ctx) {
        File[] fs = reportsDir(ctx).listFiles((d, name) ->
                name.toLowerCase(Locale.US).endsWith(".pdf"));
        if (fs == null) return new File[0];
        Arrays.sort(fs, Comparator.comparingLong(File::lastModified).reversed());
        return fs;
    }
}
