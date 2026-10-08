package com.cybersafetycs.wifishield;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Multi-network signal graph — one colored curve per nearby WiFi.
 * Every point comes from real scanner history (Net.history / historyWhen);
 * nothing is interpolated or fabricated. Tap a curve -> OnSeriesTap.
 */
public class MultiGraphView extends View {

    /** Distinct, readable-on-dark curve colors (assigned per BSSID). */
    static final int[] PALETTE = {
            0xFF3AD4FF,   // cyan
            0xFF35E07F,   // green
            0xFFFFB43A,   // amber
            0xFF4D92FF,   // blue
            0xFFB36BFF,   // purple
            0xFFFF6FD8,   // pink
            0xFF2EE6C5,   // teal
            0xFFFF8A3D,   // orange
            0xFFB8FF3A,   // lime
            0xFFFF5C8A,   // rose
    };

    public interface OnSeriesTap { void onSeriesTap(String bssid); }

    public static final class Series {
        public String bssid;
        public String label;
        public int color;
        public final List<double[]> pts = new ArrayList<>();   // {ts, dbm}
        public int lastDbm = -100;
    }

    public String windowKey = GraphView.WIN_LIVE;
    public String selectedBssid = null;
    public OnSeriesTap tapListener;

    private final List<Series> series = new ArrayList<>();
    // hit-testing data rebuilt on every draw: visible point -> owning bssid
    private final List<float[]> hitPts = new ArrayList<>();
    private final List<String> hitOwner = new ArrayList<>();

    private final Paint grid, linePaint, dotPaint;
    private final TextPaint textMute, textGreen, textAmber, textRed, labelPaint;
    private final float d;

    public MultiGraphView(Context context) {
        super(context);
        d = context.getResources().getDisplayMetrics().density;
        float sp = context.getResources().getDisplayMetrics().scaledDensity;

        grid = new Paint();
        grid.setColor(Theme.BORDER);
        grid.setStrokeWidth(Math.max(1f, d));

        linePaint = new Paint();
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2 * d);
        linePaint.setAntiAlias(true);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        dotPaint = new Paint();
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setAntiAlias(true);

        textMute = new TextPaint();
        textMute.setColor(Theme.TEXT_MUTE);
        textMute.setTextSize(8 * sp);

        textGreen = new TextPaint();
        textGreen.setColor(Theme.GREEN);
        textGreen.setTextSize(8 * sp);

        textAmber = new TextPaint();
        textAmber.setColor(Theme.AMBER);
        textAmber.setTextSize(8 * sp);

        textRed = new TextPaint();
        textRed.setColor(Theme.RED);
        textRed.setTextSize(8 * sp);

        labelPaint = new TextPaint();
        labelPaint.setColor(Theme.TEXT_MUTE);
        labelPaint.setTextSize(7 * sp);

        setBackgroundColor(Theme.PANEL);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    /**
     * Re-seed every curve from real Net history. `colorMap` assigns each BSSID
     * a stable palette index (created on first sight, kept across rebuilds).
     */
    public void sync(List<Net> nets, Map<String, Integer> colorMap) {
        series.clear();
        for (Net n : nets) {
            if (n.history.isEmpty()) continue;
            String key = n.bssid.toLowerCase();
            Integer ci = colorMap.get(key);
            if (ci == null) { ci = colorMap.size(); colorMap.put(key, ci); }
            Series s = new Series();
            s.bssid = n.bssid;
            s.label = n.displayName();
            s.color = PALETTE[ci % PALETTE.length];
            s.lastDbm = n.signalDbm;
            for (int i = 0; i < n.history.size(); i++) {
                s.pts.add(new double[]{n.sampleTimeSec(i), n.history.get(i)});
            }
            series.add(s);
        }
        invalidate();
    }

    public void setWindow(String key) { windowKey = key; invalidate(); }
    public void setSelected(String bssid) { selectedBssid = bssid; invalidate(); }

    private float px(float dp) { return dp * d; }

    private float mapX(double ts, double start, double span, float padL, float pw) {
        double frac = span > 0 ? (ts - start) / span : 0;
        return padL + (float) (pw * Math.max(0, Math.min(1, frac)));
    }

    private float mapY(int dbm, float padT, float ph) {
        float s = (dbm - GraphView.Y_MIN)
                / (float) (GraphView.Y_MAX - GraphView.Y_MIN);
        s = Math.max(0f, Math.min(1f, s));
        return padT + ph * (1 - s);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = Math.max(getWidth(), (int) px(60));
        float h = Math.max(getHeight(), (int) px(60));
        float padL = px(46), padR = px(14), padT = px(14), padB = px(26);
        float pw = w - padL - padR;
        float ph = h - padT - padB;

        double span = GraphView.WINDOWS.get(windowKey);
        double end = 0;
        for (Series s : series) {
            if (!s.pts.isEmpty()) end = Math.max(end, s.pts.get(s.pts.size() - 1)[0]);
        }
        double start = end - span;

        // grid + labels
        for (int g : new int[]{-40, -50, -60, -80}) {
            float y = mapY(g, padT, ph);
            canvas.drawLine(padL, y, padL + pw, y, grid);
            String lbl = String.valueOf(g);
            textMute.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(lbl, padL - px(8), y + px(3), textMute);
            Paint p = (g == -40) ? textRed : textMute;
            p.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(lbl + " dBm", padL + pw + px(6), y + px(3), p);
        }

        // quality band labels
        bandLabel(canvas, "STRONG", mapY(-30, padT, ph), textGreen, padL);
        bandLabel(canvas, "GOOD", mapY(-44, padT, ph), textGreen, padL);
        bandLabel(canvas, "FAIR", mapY(-56, padT, ph), textAmber, padL);
        bandLabel(canvas, "WEAK", mapY(-70, padT, ph), textAmber, padL);
        bandLabel(canvas, "VERY WEAK", mapY(-84, padT, ph), textRed, padL);

        // baseline
        float yBase = mapY(GraphView.Y_MIN, padT, ph);
        Paint base = new Paint(grid);
        base.setColor(Theme.BORDER_LIT);
        canvas.drawLine(padL, yBase, padL + pw, yBase, base);

        hitPts.clear();
        hitOwner.clear();
        boolean any = false;

        // one curve per network
        for (Series s : series) {
            List<float[]> pts = new ArrayList<>();
            for (double[] p : s.pts) {
                if (p[0] < start) continue;
                pts.add(new float[]{mapX(p[0], start, span, padL, pw),
                        mapY((int) p[1], padT, ph)});
            }
            if (pts.isEmpty()) continue;
            any = true;

            boolean isSel = selectedBssid != null
                    && selectedBssid.equalsIgnoreCase(s.bssid);
            int alpha = (selectedBssid == null || isSel) ? 255 : 130;
            linePaint.setColor(s.color);
            linePaint.setAlpha(alpha);
            linePaint.setStrokeWidth((isSel ? 3f : 2f) * d);
            dotPaint.setColor(s.color);
            dotPaint.setAlpha(alpha);

            if (pts.size() >= 2) {
                Path line = new Path();
                line.moveTo(pts.get(0)[0], pts.get(0)[1]);
                for (int i = 1; i < pts.size(); i++) {
                    float[] a = pts.get(i - 1), b = pts.get(i);
                    float mx = (a[0] + b[0]) / 2, my = (a[1] + b[1]) / 2;
                    line.quadTo(a[0], a[1], mx, my);
                    if (i == pts.size() - 1) line.lineTo(b[0], b[1]);
                }
                canvas.drawPath(line, linePaint);
            }
            // last real sample marker
            float[] last = pts.get(pts.size() - 1);
            canvas.drawCircle(last[0], last[1], px(3), dotPaint);

            for (float[] p : pts) {
                hitPts.add(p);
                hitOwner.add(s.bssid);
            }
        }

        if (!any) {
            textMute.setTextAlign(Paint.Align.CENTER);
            textMute.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
            canvas.drawText("Waiting for real scan data\u2026",
                    padL + pw / 2, padT + ph / 2, textMute);
            textMute.setTextSize(8 * getResources().getDisplayMetrics().scaledDensity);
            return;
        }

        // time axis (real timestamps only)
        SimpleDateFormat fmt = new SimpleDateFormat(
                span <= 60 ? "HH:mm:ss" : "HH:mm", Locale.US);
        double step = span / 4.0;
        labelPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 5; i++) {
            double ts = start + step * i;
            String lbl = i == 0 ? "now" : fmt.format(new Date((long) ts * 1000));
            float x = mapX(ts, start, span, padL, pw);
            canvas.drawText(lbl, x, padT + ph + px(14), labelPaint);
        }
    }

    private void bandLabel(Canvas c, String txt, float y, Paint p, float padL) {
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText(txt, padL, y, p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() == MotionEvent.ACTION_UP && tapListener != null) {
            float x = e.getX(), y = e.getY();
            String best = null;
            float bestDist = px(36);
            for (int i = 0; i < hitPts.size(); i++) {
                float dx = hitPts.get(i)[0] - x, dy = hitPts.get(i)[1] - y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < bestDist) { bestDist = dist; best = hitOwner.get(i); }
            }
            if (best != null) { tapListener.onSeriesTap(best); return true; }
        }
        return super.onTouchEvent(e);
    }
}
