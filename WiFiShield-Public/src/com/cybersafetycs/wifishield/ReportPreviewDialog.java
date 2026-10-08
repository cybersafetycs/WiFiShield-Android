package com.cybersafetycs.wifishield;

import android.app.Dialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * In-app coloured preview of a generated PDF (port of the desktop
 * `_show_report_preview` Toplevel) with Open / Share / Close actions.
 */
class ReportPreviewDialog extends Dialog {

    private final MainActivity a;
    private final File pdf;

    ReportPreviewDialog(MainActivity activity, File pdf) {
        super(activity);
        this.a = activity;
        this.pdf = pdf;
        build();
    }

    private void build() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(scrollView());
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        getWindow().setBackgroundDrawableResource(android.R.color.black);
    }

    private LinearLayout scrollView() {
        Net n = a.selected;
        Analyzer.Result an = Analyzer.analyze(n);

        LinearLayout root = Ui.v(a);
        root.setBackgroundColor(Theme.BG);

        // ---- red brand header ----
        LinearLayout hdr = Ui.v(a);
        hdr.setBackgroundColor(Theme.RED);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int p = Ui.dp(a, 16);
        hdr.setPadding(p, p, p, p);
        TextView tag = Ui.label(a, "CYBERSAFETYCS", 10, Color.WHITE, true);
        hdr.addView(tag);
        TextView sub = Ui.label(a, "WiFiShield  \u00b7  WiFi Security Report",
                15, Color.WHITE, true);
        hdr.addView(sub);
        root.addView(hdr, hlp);

        // ---- scrollable body ----
        ScrollView scroll = new ScrollView(a);
        scroll.setBackgroundColor(Theme.BG);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout body = Ui.v(a);
        int bp = Ui.dp(a, 14);
        body.setPadding(bp, 0, bp, Ui.dp(a, 14));
        scroll.addView(body);

        TextView title = Ui.label(a, n.displayName(), 18, Theme.TEXT, true);
        title.setPadding(0, Ui.dp(a, 8), 0, 0);
        body.addView(title);
        TextView bssid = Ui.label(a, "BSSID " + (n.bssid.isEmpty() ? "N/A" : n.bssid)
                + "   \u00b7   " + (n.isConnected ? "CONNECTED" : "SCANNED NETWORK"),
                9, Theme.TEXT_DIM, false);
        body.addView(bssid);

        // score card
        LinearLayout sc = Ui.card(a, Theme.PANEL);
        LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sclp.setMargins(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        body.addView(sc, sclp);
        LinearLayout top = Ui.h(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        int tp = Ui.dp(a, 16);
        top.setPadding(tp, Ui.dp(a, 14), tp, Ui.dp(a, 4));
        sc.addView(top);
        TextView score = Ui.label(a, String.valueOf(an.score), 40,
                Theme.named(an.scoreColor), true);
        top.addView(score);
        LinearLayout mid = Ui.v(a);
        mid.setPadding(Ui.dp(a, 16), 0, 0, 0);
        TextView enc = Ui.label(a, an.encryptionLabel, 11, Theme.TEXT, true);
        mid.addView(enc);
        mid.addView(Ui.scoreBar(a, an.score, Theme.named(an.scoreColor)),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 14)));
        TextView sig = Ui.label(a, "Signal " + n.signalDbm + " dBm ("
                + an.signalLabel + ")   \u00b7   " + n.band() + "   \u00b7   CH "
                + n.channel, 9, Theme.TEXT_DIM, false);
        sig.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        mid.addView(sig);
        top.addView(mid, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView over = Ui.label(a, "/ 100", 11, Theme.TEXT_MUTE, false);
        top.addView(over);

        // info grid (2 columns)
        LinearLayout gc = Ui.card(a, Theme.PANEL);
        LinearLayout.LayoutParams gclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gclp.setMargins(0, 0, 0, Ui.dp(a, 6));
        body.addView(gc, gclp);
        String[][] cells = {
                {"SSID", n.displayName()},
                {"BSSID", n.bssid.isEmpty() ? "N/A" : n.bssid},
                {"SECURITY", an.securityRaw.isEmpty() ? "OPEN" : an.securityRaw},
                {"SIGNAL", n.signalDbm + " dBm"},
                {"CHANNEL", String.valueOf(n.channel > 0 ? n.channel : "?")},
                {"FREQUENCY", (n.frequency > 0 ? n.frequency : "?") + " MHz"},
                {"BAND", n.band()},
                {"WI-FI", n.wifiCell()},
        };
        LinearLayout grid = Ui.v(a);
        int gp = Ui.dp(a, 12);
        grid.setPadding(gp, gp, gp, gp);
        gc.addView(grid);
        for (int i = 0; i < cells.length; i += 2) {
            LinearLayout row = Ui.h(a);
            for (int j = i; j < i + 2 && j < cells.length; j++) {
                LinearLayout cell = Ui.v(a);
                cell.setBackgroundColor(Theme.PANEL_ALT);
                int cp = Ui.dp(a, 8);
                cell.setPadding(cp, Ui.dp(a, 6), cp, Ui.dp(a, 6));
                TextView k = Ui.label(a, cells[j][0], 8, Theme.TEXT_DIM, true);
                TextView v = Ui.label(a, cells[j][1], 10, Theme.TEXT, true);
                cell.addView(k);
                cell.addView(v);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                clp.setMargins(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
                row.addView(cell, clp);
            }
            grid.addView(row);
        }

        // findings
        LinearLayout fc = Ui.card(a, Theme.PANEL);
        body.addView(fc, gclpCopy());
        fc.addView(Ui.section(a, "MATATIZO YALIYOGUNDULIWA / FINDINGS"));
        for (Analyzer.Finding f : an.findings) {
            LinearLayout row = Ui.h(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(a, 14), Ui.dp(a, 3), Ui.dp(a, 14), Ui.dp(a, 3));
            int col = Theme.severityColor(f.severity);
            TextView chip = Ui.label(a, sevName(f.severity), 8,
                    (f.severity.equals("good") || f.severity.equals("info"))
                            ? Theme.BG : Color.WHITE, true);
            chip.setBackgroundColor(col);
            chip.setPadding(Ui.dp(a, 7), 1, Ui.dp(a, 7), 1);
            row.addView(chip);
            TextView msg = Ui.label(a, f.message, 9, Theme.TEXT, false);
            msg.setPadding(Ui.dp(a, 8), 0, 0, 0);
            row.addView(msg);
            fc.addView(row);
        }

        // recommendations
        LinearLayout rc = Ui.card(a, Theme.PANEL);
        LinearLayout.LayoutParams rclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rclp.setMargins(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        body.addView(rc, rclp);
        rc.addView(Ui.section(a, "MAPENDEKEZO / RECOMMENDATIONS"));
        int i = 1;
        for (String r : an.recommendations) {
            TextView t = Ui.label(a, (i++) + ".  " + r, 9, Theme.TEXT, false);
            t.setPadding(Ui.dp(a, 14), Ui.dp(a, 2), Ui.dp(a, 14), Ui.dp(a, 2));
            rc.addView(t);
        }

        TextView foot = Ui.label(a,
                "CyberSafetyCS WiFiShield \u2014 automated scan report. "
                + "Signal & security data reflects the probe at scan time.",
                8, Theme.TEXT_MUTE, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 10));
        body.addView(foot);

        // ---- bottom action bar ----
        LinearLayout bar = Ui.h(a);
        bar.setBackgroundColor(Theme.PANEL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int bpad = Ui.dp(a, 12);
        bar.setPadding(bpad, bpad, bpad, bpad);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView name = Ui.mono(a, pdf.getName(), 8, Theme.TEXT_MUTE, false);
        bar.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView open = Ui.redBtn(a, "OPEN PDF");
        open.setOnClickListener(v -> ReportsScreen.viewPdf(a, pdf));
        bar.addView(open);
        TextView share = Ui.darkBtn(a, "SHARE");
        LinearLayout.LayoutParams shp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shp.setMargins(Ui.dp(a, 6), 0, Ui.dp(a, 6), 0);
        bar.addView(share, shp);
        share.setOnClickListener(v -> {
            try {
                android.content.Intent it = new android.content.Intent(
                        android.content.Intent.ACTION_SEND);
                it.setType("application/pdf");
                it.putExtra(android.content.Intent.EXTRA_STREAM,
                        ReportsScreen.uriFor(pdf));
                it.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                a.startActivity(android.content.Intent.createChooser(it, "Share PDF"));
            } catch (Exception ignored) {}
        });
        TextView del = Ui.button(a, "DELETE", Theme.PANEL_ALT, Theme.RED, false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.setMargins(0, 0, Ui.dp(a, 6), 0);
        del.setPadding(Ui.dp(a, 8), del.getPaddingTop(),
                Ui.dp(a, 8), del.getPaddingBottom());
        bar.addView(del, dlp);
        del.setOnClickListener(v -> ReportsScreen.confirmDelete(a, pdf, () -> {
            dismiss();
            if (MainActivity.REP.equals(a.currentScreen)) {
                Ui.Screen s = a.screens.get(MainActivity.REP);
                if (s != null) s.build();
            }
        }));

        TextView close = Ui.darkBtn(a, "CLOSE");
        bar.addView(close);
        close.setOnClickListener(v -> dismiss());

        // generation time
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date(pdf.lastModified()));
        TextView gen = Ui.label(a, "Generated " + when, 8, Theme.TEXT_MUTE, false);
        gen.setPadding(0, Ui.dp(a, 4), 0, 0);
        body.addView(gen);

        return root;
    }

    private LinearLayout.LayoutParams gclpCopy() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(a, 6));
        return lp;
    }

    private static String sevName(String sev) {
        switch (sev) {
            case "good": return "GOOD";
            case "warn": return "WARNING";
            case "danger": return "RISK";
            default: return "INFO";
        }
    }
}
