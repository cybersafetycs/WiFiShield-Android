package com.cybersafetycs.wifishield;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Map;

/** Shared UI helpers + screen contract. */
final class Ui {

    private Ui() {}

    interface Screen {
        void build();          // (re)build into the content container
        void onSample();       // a fresh real measurement arrived (main thread)
    }

    static float dens(Context c) {
        return c.getResources().getDisplayMetrics().density;
    }

    static int dp(Context c, float v) {
        return Math.round(v * dens(c));
    }

    static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                c.getResources().getDisplayMetrics());
    }

    static TextView label(Context c, String text, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    static TextView mono(Context c, String text, float sizeSp, int color, boolean bold) {
        TextView t = label(c, text, sizeSp, color, bold);
        t.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    static LinearLayout v(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout h(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    static void margins(View v, int l, int t, int r, int b) {
        ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        if (p == null) return;
        p.leftMargin = l; p.topMargin = t; p.rightMargin = r; p.bottomMargin = b;
        v.setLayoutParams(p);
    }

    /** Dark card with 1dp border (desktop `_card`). */
    static LinearLayout card(Context c, int bg) {
        LinearLayout l = v(c);
        l.setBackgroundColor(bg);
        android.graphics.drawable.GradientDrawable bd =
                new android.graphics.drawable.GradientDrawable();
        bd.setColor(bg);
        bd.setStroke(dp(c, 1), Theme.BORDER);
        bd.setCornerRadius(dp(c, 8));
        l.setBackground(bd);
        return l;
    }

    static TextView button(Context c, String text, int bg, int fg, boolean bold) {
        TextView b = label(c, text, 9, fg, bold);
        b.setBackgroundColor(bg);
        b.setGravity(Gravity.CENTER);
        int pad = dp(c, 10);
        b.setPadding(pad, dp(c, 7), pad, dp(c, 7));
        return b;
    }

    static TextView redBtn(Context c, String text) {
        return button(c, text, Theme.RED, Theme.WHITE, true);
    }

    static TextView darkBtn(Context c, String text) {
        return button(c, text, Theme.PANEL_ALT, Theme.TEXT, false);
    }

    /** Horizontal score bar: `score` out of 100 filled in `color`.
     *  Callers must pass their own LayoutParams (works in any parent). */
    static LinearLayout scoreBar(Context c, int score, int color) {
        LinearLayout outer = h(c);
        outer.setBackgroundColor(Theme.BG);
        int s = Math.max(0, Math.min(100, score));
        View fill = new View(c);
        fill.setBackgroundColor(color);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT);
        fl.weight = s;
        outer.addView(fill, fl);
        View rest = new View(c);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT);
        rl.weight = 100 - s;
        outer.addView(rest, rl);
        return outer;
    }

    /** Section header inside a card (cyan, uppercase). */
    static TextView section(Context c, String title) {
        TextView t = label(c, title, 9, Theme.CYAN, true);
        int pad = dp(c, 12);
        t.setPadding(pad, dp(c, 8), pad, dp(c, 2));
        return t;
    }

    static void setVisible(View v, boolean show) {
        v.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    static String fmtDbm(int dbm) { return dbm + " dBm"; }

    static int dbmColor(int dbm) {
        if (dbm >= -55) return Theme.GREEN;
        if (dbm >= -75) return Theme.AMBER;
        return Theme.RED;
    }

    static void addAll(LinearLayout parent, Map<String, TextView> map) {
        for (TextView t : map.values()) parent.addView(t);
    }
}
