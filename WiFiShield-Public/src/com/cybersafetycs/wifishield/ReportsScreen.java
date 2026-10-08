package com.cybersafetycs.wifishield;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** RIPOTI / Reports screen — generate + list + open/share PDFs. */
class ReportsScreen implements Ui.Screen {

    private final MainActivity a;
    private LinearLayout listBox;

    ReportsScreen(MainActivity activity) { this.a = activity; }

    @Override
    public void build() {
        a.content.removeAllViews();
        LinearLayout root = Ui.v(a);
        root.setBackgroundColor(Theme.BG);
        a.content.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(a.header("Ripoti / Reports",
                "Generated security reports", null));

        ScrollView scroll = new ScrollView(a);
        scroll.setBackgroundColor(Theme.BG);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout body = Ui.v(a);
        int p = Ui.dp(a, 24);
        body.setPadding(p, 0, p, Ui.dp(a, 16));
        scroll.addView(body);

        // NOTE: no developer info here (Firebase status, project ids, file
        // paths, tokens) — this screen is for normal users only.

        if (a.selected != null) {
            TextView gen = Ui.redBtn(a, "Tengeneza Ripoti (current network)");
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            glp.setMargins(0, 0, 0, Ui.dp(a, 4));
            body.addView(gen, glp);
            gen.setOnClickListener(v -> a.generateReport());
        }

        // generated files list
        TextView lt = Ui.label(a, "GENERATED PDFs", 9, Theme.CYAN, true);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.setMargins(0, Ui.dp(a, 14), 0, Ui.dp(a, 4));
        body.addView(lt, llp);

        listBox = Ui.v(a);
        body.addView(listBox);
        fillList();
    }

    private void fillList() {
        listBox.removeAllViews();
        File[] files = LogStore.reports(a);
        if (files.length == 0) {
            TextView none = Ui.label(a,
                    "No reports yet \u2014 generate one from a network.",
                    10, Theme.TEXT_MUTE, false);
            none.setPadding(0, Ui.dp(a, 4), 0, 0);
            listBox.addView(none);
            return;
        }
        for (final File f : files) {
            LinearLayout row = Ui.card(a, Theme.PANEL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.setMargins(0, 0, 0, Ui.dp(a, 6));
            listBox.addView(row, rp);

            LinearLayout inner = Ui.h(a);
            inner.setGravity(Gravity.CENTER_VERTICAL);
            int pad = Ui.dp(a, 10);
            inner.setPadding(pad, pad, pad, pad);
            row.addView(inner);

            LinearLayout col = Ui.v(a);
            TextView name = Ui.label(a, f.getName(), 9, Theme.TEXT, true);
            TextView meta = Ui.label(a, (f.length() / 1024) + " KB", 8,
                    Theme.TEXT_MUTE, false);
            col.addView(name);
            col.addView(meta);
            inner.addView(col, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView open = Ui.button(a, "VIEW", Theme.RED, Theme.WHITE, true);
            inner.addView(open);
            open.setOnClickListener(v -> viewPdf(f));

            TextView share = Ui.button(a, "SHARE", Theme.PANEL_ALT, Theme.TEXT, false);
            LinearLayout.LayoutParams shp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            shp.setMargins(Ui.dp(a, 6), 0, 0, 0);
            share.setPadding(Ui.dp(a, 8), share.getPaddingTop(),
                    Ui.dp(a, 8), share.getPaddingBottom());
            inner.addView(share, shp);
            share.setOnClickListener(v -> sharePdf(f));

            TextView del = Ui.button(a, "DEL", Theme.PANEL_ALT, Theme.RED, false);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.setMargins(Ui.dp(a, 6), 0, 0, 0);
            del.setPadding(Ui.dp(a, 8), del.getPaddingTop(),
                    Ui.dp(a, 8), del.getPaddingBottom());
            inner.addView(del, dlp);
            del.setOnClickListener(v -> confirmDelete(a, f, this::fillList));
        }
    }

    /**
     * Delete one generated PDF after user confirmation.
     * Refreshes the caller's list (or the RIPOTI screen) on success.
     */
    static void confirmDelete(final MainActivity a, final File f, final Runnable onDeleted) {
        new AlertDialog.Builder(a)
                .setTitle("Delete report?")
                .setMessage(f.getName())
                .setPositiveButton("DELETE", (d, w) -> {
                    boolean ok = f.delete();
                    Toast.makeText(a, ok ? "Report deleted" : "Could not delete report",
                            Toast.LENGTH_SHORT).show();
                    if (ok) {
                        if (onDeleted != null) onDeleted.run();
                        else if (MainActivity.REP.equals(a.currentScreen)) {
                            Ui.Screen s = a.screens.get(MainActivity.REP);
                            if (s != null) s.build();
                        }
                    }
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    static Uri uriFor(File f) {
        String sub = "reports/" + f.getName();
        return Uri.parse("content://" + LocalFileProvider.AUTHORITY + "/" + sub);
    }

    static void viewPdf(MainActivity a, File f) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uriFor(f), "application/pdf");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(i, "Open PDF report"));
        } catch (Exception e) {
            Toast.makeText(a, "No PDF viewer installed", Toast.LENGTH_SHORT).show();
        }
    }

    void viewPdf(File f) { viewPdf(a, f); }

    void sharePdf(File f) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/pdf");
            i.putExtra(Intent.EXTRA_STREAM, uriFor(f));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(i, "Share PDF report"));
        } catch (Exception e) {
            Toast.makeText(a, "Could not share PDF", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSample() { }
}
