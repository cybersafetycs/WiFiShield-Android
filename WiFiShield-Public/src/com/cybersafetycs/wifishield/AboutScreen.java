package com.cybersafetycs.wifishield;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** MAWASILIANO screen — APK identity, contact, open-source links. */
class AboutScreen implements Ui.Screen {

    private final MainActivity a;

    AboutScreen(MainActivity activity) { this.a = activity; }

    @Override
    public void build() {
        a.content.removeAllViews();
        LinearLayout root = Ui.v(a);
        root.setBackgroundColor(Theme.BG);
        a.content.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(a.header("Mawasiliano",
                "About \u00b7 Contact \u00b7 Contribute", null));

        ScrollView scroll = new ScrollView(a);
        scroll.setBackgroundColor(Theme.BG);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout body = Ui.v(a);
        int p = Ui.dp(a, 24);
        body.setPadding(p, Ui.dp(a, 10), p, Ui.dp(a, 16));
        scroll.addView(body);

        // ---- APK identity (name + project title only — no version codes,
        //      package names or other developer metadata) ----
        LinearLayout id = Ui.card(a, Theme.PANEL);
        body.addView(id, lpMatch());
        TextView name = Ui.label(a, "WiFiShield", 16, Theme.RED, true);
        name.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), 0, Ui.dp(a, 2));
        id.addView(name);
        TextView dh = Ui.label(a, "DHUMUNI WIFI SECURITY RESEARCH",
                10, Theme.CYAN, true);
        dh.setPadding(Ui.dp(a, 14), 0, 0, Ui.dp(a, 12));
        id.addView(dh);

        // ---- MAWASILIANO (contact) ----
        LinearLayout mc = Ui.card(a, Theme.PANEL);
        body.addView(mc, lpMatch());
        TextView mt = Ui.label(a, "MAWASILIANO", 9, Theme.CYAN, true);
        mt.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), 0, Ui.dp(a, 2));
        mc.addView(mt);
        TextView mail = link("ngokojuma@protonmail.com");
        mail.setPadding(Ui.dp(a, 14), 0, 0, Ui.dp(a, 12));
        mail.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("mailto:ngokojuma@protonmail.com"));
            open(i, "No email app installed");
        });
        mc.addView(mail);

        // ---- development / open source ----
        LinearLayout dc = Ui.card(a, Theme.PANEL);
        body.addView(dc, lpMatch());
        TextView dt = Ui.label(a, "DEVELOPED BY CYBERSAFETYCS", 9,
                Theme.CYAN, true);
        dt.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), 0, Ui.dp(a, 2));
        dc.addView(dt);
        TextView os = Ui.label(a, "Open source \u2014 anyone can join and "
                + "contribute.", 9, Theme.TEXT, false);
        os.setPadding(Ui.dp(a, 14), 0, 0, 0);
        dc.addView(os);

        TextView gh = link("github.com/cybersafetycs");
        gh.setPadding(Ui.dp(a, 14), Ui.dp(a, 6), 0, 0);
        gh.setOnClickListener(v -> openUrl("https://github.com/cybersafetycs"));
        dc.addView(gh);

        TextView yt = link("youtube.com/@cybersafetycs");
        yt.setPadding(Ui.dp(a, 14), 0, 0, Ui.dp(a, 12));
        yt.setOnClickListener(v -> openUrl(
                "https://www.youtube.com/@cybersafetycs"));
        dc.addView(yt);
    }

    private TextView link(String text) {
        TextView t = Ui.label(a, text, 10, Theme.GREEN, false);
        t.setPaintFlags(t.getPaintFlags()
                | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void openUrl(String url) {
        open(new Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                "No browser installed");
    }

    private void open(Intent i, String err) {
        try {
            a.startActivity(i);
        } catch (Exception e) {
            android.widget.Toast.makeText(a, err,
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private static LinearLayout.LayoutParams lpMatch() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onSample() { }
}
