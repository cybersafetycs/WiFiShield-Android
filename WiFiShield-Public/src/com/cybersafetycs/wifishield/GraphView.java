package com.cybersafetycs.wifishield;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextPaint;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Live signal graph — Java port of the desktop graph.py widget. */
public class GraphView extends View {

    public static final String WIN_LIVE = "LIVE";
    public static final String WIN_30 = "30 sec";
    public static final String WIN_60 = "1 min";
    public static final String WIN_300 = "5 min";

    static final Map<String, Integer> WINDOWS = new LinkedHashMap<>();
    static {
        WINDOWS.put(WIN_LIVE, 8 * 60);
        WINDOWS.put(WIN_30, 30);
        WINDOWS.put(WIN_60, 60);
        WINDOWS.put(WIN_300, 300);
    }

    static final int Y_MIN = -95, Y_MAX = -25;
    static final int[] GRID = {-40, -60, -80};

    public String windowKey = WIN_LIVE;
    public final List<double[]> samples = new ArrayList<>();   // {ts, dbm}

    private final Paint grid, textMute, textRed, textGreen, textAmber,
            fillPaint, linePaint, dotPaint, labelPaint;
    private final float d;

    public GraphView(Context context) {
        super(context);
        d = context.getResources().getDisplayMetrics().density;

        grid = new Paint();
        grid.setColor(Theme.BORDER);
        grid.setStrokeWidth(Math.max(1f, d));

        textMute = new TextPaint();
        textMute.setColor(Theme.TEXT_MUTE);
        textMute.setTextSize(8 * d * context.getResources().getDisplayMetrics().scaledDensity
                / context.getResources().getDisplayMetrics().density * d);
        // simpler: sp -> px
        textMute.setTextSize(8 * context.getResources().getDisplayMetrics().scaledDensity);

        textRed = new TextPaint();
        textRed.setColor(Theme.RED);
        textRed.setTextSize(8 * context.getResources().getDisplayMetrics().scaledDensity);

        textGreen = new TextPaint();
        textGreen.setColor(Theme.GREEN);
        textGreen.setTextSize(8 * context.getResources().getDisplayMetrics().scaledDensity);

        textAmber = new TextPaint();
        textAmber.setColor(Theme.AMBER);
        textAmber.setTextSize(8 * context.getResources().getDisplayMetrics().scaledDensity);

        labelPaint = new TextPaint();
        labelPaint.setColor(Theme.TEXT_MUTE);
        labelPaint.setTextSize(7 * context.getResources().getDisplayMetrics().scaledDensity);

        fillPaint = new Paint();
        fillPaint.setColor(Theme.CYAN_BG);
        fillPaint.setStyle(Paint.Style.FILL);

        linePaint = new Paint();
        linePaint.setColor(Theme.CYAN);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2 * d);
        linePaint.setAntiAlias(true);

        dotPaint = new Paint();
        dotPaint.setColor(Theme.CYAN);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setAntiAlias(true);

        setBackgroundColor(Theme.PANEL);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void append(double timestamp, int dbm) {
        samples.add(new double[]{timestamp, dbm});
        int max = 0;
        for (int v : WINDOWS.values()) max = Math.max(max, v);
        if (samples.size() > max + 20) {
            samples.subList(0, samples.size() - (max + 20)).clear();
        }
        invalidate();
    }

    public void setWindow(String key) {
        windowKey = key;
        invalidate();
    }

    public void clearSamples() {
        samples.clear();
        invalidate();
    }

    private float px(float dp) { return dp * d; }

    private float mapX(double ts, double start, double span, float padL, float pw) {
        double frac = span > 0 ? (ts - start) / span : 0;
        return padL + (float) (pw * frac);
    }

    private float mapY(int dbm, float padT, float ph) {
        float s = (dbm - Y_MIN) / (float) (Y_MAX - Y_MIN);
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

        double span = WINDOWS.get(windowKey);
        double end = samples.isEmpty() ? 0 : samples.get(samples.size() - 1)[0];
        double start = end - span;

        // grid + labels
        int[] gridAll = {-40, -60, -80, -50};
        for (int g : gridAll) {
            float y = mapY(g, padT, ph);
            canvas.drawLine(padL, y, padL + pw, y, grid);
            String lbl = String.valueOf(g);
            textMute.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(lbl, padL - px(8), y + px(3), textMute);
            Paint p = (g == -40) ? textRed : textMute;
            p.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(lbl + " dBm", padL + pw + px(6), y + px(3), p);
        }

        // band labels
        bandLabel(canvas, "STRONG", mapY(-30, padT, ph), textGreen, padL);
        bandLabel(canvas, "GOOD", mapY(-44, padT, ph), textGreen, padL);
        bandLabel(canvas, "FAIR", mapY(-56, padT, ph), textAmber, padL);
        bandLabel(canvas, "WEAK", mapY(-70, padT, ph), textAmber, padL);
        bandLabel(canvas, "VERY WEAK", mapY(-84, padT, ph), textRed, padL);

        // baseline
        float yBase = mapY(Y_MIN, padT, ph);
        Paint base = new Paint(grid);
        base.setColor(Theme.BORDER_LIT);
        canvas.drawLine(padL, yBase, padL + pw, yBase, base);

        // time axis
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

        if (samples.isEmpty()) {
            textMute.setTextAlign(Paint.Align.CENTER);
            textMute.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
            canvas.drawText("Collecting signal data\u2026", padL + pw / 2,
                    padT + ph / 2, textMute);
            textMute.setTextSize(8 * getResources().getDisplayMetrics().scaledDensity);
            return;
        }

        // visible points
        List<float[]> pts = new ArrayList<>();
        for (double[] s : samples) {
            if (s[0] < start) continue;
            pts.add(new float[]{mapX(s[0], start, span, padL, pw),
                    mapY((int) s[1], padT, ph)});
        }
        pts.add(new float[]{mapX(end, start, span, padL, pw),
                mapY((int) samples.get(samples.size() - 1)[1], padT, ph)});
        if (pts.size() < 2) return;

        // area fill
        Path poly = new Path();
        poly.moveTo(pts.get(0)[0], pts.get(pts.size() - 1)[1]);
        // build: first point -> line through points -> down to baseline
        Path area = new Path();
        area.moveTo(pts.get(0)[0], pts.get(0)[1]);
        for (int i = 1; i < pts.size(); i++) area.lineTo(pts.get(i)[0], pts.get(i)[1]);
        float xEnd = mapX(end, start, span, padL, pw);
        area.lineTo(xEnd, yBase);
        area.lineTo(pts.get(0)[0], yBase);
        area.close();
        canvas.drawPath(area, fillPaint);

        // trend line (smoothed with quads)
        Path line = new Path();
        line.moveTo(pts.get(0)[0], pts.get(0)[1]);
        for (int i = 1; i < pts.size(); i++) {
            float[] a = pts.get(i - 1), b = pts.get(i);
            float mx = (a[0] + b[0]) / 2, my = (a[1] + b[1]) / 2;
            line.quadTo(a[0], a[1], mx, my);
            if (i == pts.size() - 1) line.lineTo(b[0], b[1]);
        }
        canvas.drawPath(line, linePaint);

        // last point marker
        float[] last = pts.get(pts.size() - 1);
        canvas.drawCircle(last[0], last[1], px(3), dotPaint);

        // current value chip
        int val = (int) samples.get(samples.size() - 1)[1];
        Paint chip = new TextPaint();
        chip.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
        chip.setFakeBoldText(true);
        chip.setTextAlign(Paint.Align.RIGHT);
        chip.setColor(val <= -70 ? Theme.RED : val <= -60 ? Theme.AMBER : Theme.GREEN);
        canvas.drawText(val + " dBm", padL + pw - px(8), padT + px(14), chip);
    }

    private void bandLabel(Canvas c, String txt, float y, Paint p, float padL) {
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText(txt, padL, y, p);
    }
}

/** 8-bar signal strength indicator (port of main.py `_bars`). */
class BarsView extends View {
    private int dbm = -100;
    private final float d;

    public BarsView(Context context) {
        super(context);
        d = context.getResources().getDisplayMetrics().density;
    }

    public void setDbm(int v) {
        dbm = v;
        invalidate();
    }

    static int fillBars(int dbm, int total) {
        float s = Math.max(0f, Math.min(1f, (dbm + 100) / 70f));
        int r = Math.round(s * total);
        return r == 0 ? 1 : r;
    }

    static int dbmColor(int dbm) {
        if (dbm >= -55) return Theme.GREEN;
        if (dbm >= -75) return Theme.AMBER;
        return Theme.RED;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = 8;
        int filled = fillBars(dbm, n);
        Paint p = new Paint();
        float x = 0;
        for (int i = 0; i < n; i++) {
            float bw = 4 * d, bh = (4 + (i + 1) * 2) * d;
            p.setColor(i >= filled ? Theme.BORDER : dbmColor(dbm));
            canvas.drawRect(x, getHeight() - bh, x + bw, getHeight(), p);
            x += bw + 2 * d;
        }
        setMeasuredDimension((int) x, (int) (18 * d));
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), (int) (18 * d));
    }
}
