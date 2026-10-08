package com.cybersafetycs.wifishield;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** CAPTURE screen — live graph + stats (port of `_build_capture`). */
class CaptureScreen implements Ui.Screen {

    private final MainActivity a;
    GraphView graph;
    final Map<String, TextView> stats = new HashMap<>();

    CaptureScreen(MainActivity activity) { this.a = activity; }

    @Override
    public void build() {
        a.content.removeAllViews();
        stats.clear();

        LinearLayout root = Ui.v(a);
        root.setBackgroundColor(Theme.BG);
        a.content.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(a.header("Capture",
                "Live signal capture for selected network", null));

        LinearLayout body = Ui.v(a);
        int p = Ui.dp(a, 24);
        body.setPadding(p, Ui.dp(a, 10), p, Ui.dp(a, 16));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        if (a.selected == null) {
            TextView none = Ui.label(a, "Select a network first (Mitandao).",
                    11, Theme.TEXT_MUTE, false);
            none.setGravity(Gravity.CENTER);
            body.addView(none);
            return;
        }
        Net n = a.selected;

        LinearLayout card = Ui.card(a, Theme.PANEL);
        body.addView(card, lpMatch());
        TextView cap = Ui.label(a, "CAPTURING: " + n.displayName(),
                11, Theme.CYAN, true);
        cap.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), 0, Ui.dp(a, 2));
        card.addView(cap);
        TextView sig = Ui.label(a, "Signature: capturing \"" + n.displayName()
                + "\" on ch" + n.channel + " (" + n.band() + ") \u2014 "
                + n.signalDbm + " dBm", 9, Theme.TEXT_MUTE, false);
        sig.setPadding(Ui.dp(a, 14), 0, 0, 0);
        card.addView(sig);

        graph = new GraphView(a);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 170));
        glp.setMargins(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 4));
        card.addView(graph, glp);

        // window buttons
        LinearLayout win = Ui.h(a);
        win.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 14), Ui.dp(a, 6));
        win.setGravity(Gravity.CENTER_VERTICAL);
        TextView wl = Ui.label(a, "SIGNAL HISTORY", 9, Theme.TEXT_DIM, true);
        win.addView(wl, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        for (final String k : new String[]{GraphView.WIN_LIVE, GraphView.WIN_30,
                GraphView.WIN_60, GraphView.WIN_300}) {
            TextView b = Ui.button(a, k, Theme.PANEL_ALT, Theme.TEXT_DIM, false);
            b.setPadding(Ui.dp(a, 7), Ui.dp(a, 3), Ui.dp(a, 7), Ui.dp(a, 3));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.setMargins(Ui.dp(a, 2), 0, 0, 0);
            win.addView(b, bl);
            b.setOnClickListener(v -> graph.setWindow(k));
        }
        card.addView(win);

        // stats
        LinearLayout statsRow = Ui.h(a);
        LinearLayout.LayoutParams splp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        splp.setMargins(0, Ui.dp(a, 10), 0, 0);
        body.addView(statsRow, splp);
        String[][] items = {
                {"Samples", String.valueOf(n.history.size())},
                {"Signal", n.signalDbm + " dBm"},
                {"Security", n.security == null ? "OPEN" : n.security},
        };
        for (int i = 0; i < items.length; i++) {
            LinearLayout cc = Ui.card(a, Theme.PANEL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            clp.setMargins(Ui.dp(a, 6), 0, Ui.dp(a, 6), 0);
            statsRow.addView(cc, clp);
            TextView v = Ui.label(a, items[i][1], 20, Theme.GREEN, true);
            v.setGravity(Gravity.CENTER);
            v.setPadding(0, Ui.dp(a, 10), 0, 0);
            cc.addView(v);
            TextView k = Ui.label(a, items[i][0], 9, Theme.TEXT_MUTE, false);
            k.setGravity(Gravity.CENTER);
            k.setPadding(0, 0, 0, Ui.dp(a, 10));
            cc.addView(k);
            stats.put(items[i][0], v);
        }

        feed();
    }

    private LinearLayout.LayoutParams lpMatch() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void feed() {
        if (graph == null || a.selected == null) return;
        graph.clearSamples();
        List<Integer> h = a.selected.history;
        for (int i = 0; i < h.size(); i++) {
            graph.append(a.selected.sampleTimeSec(i), h.get(i));
        }
    }

    @Override
    public void onSample() {
        Net sel = a.selected;
        if (sel == null) return;
        if (graph != null) {
            graph.append(System.currentTimeMillis() / 1000.0, sel.signalDbm);
        }
        TextView s = stats.get("Signal");
        if (s != null) s.setText(sel.signalDbm + " dBm");
        TextView n = stats.get("Samples");
        if (n != null) n.setText(String.valueOf(sel.history.size()));
    }
}


/** UCHAMBUZI / Analysis screen (port of `_build_analysis`). */
class AnalysisScreen implements Ui.Screen {

    private final MainActivity a;

    AnalysisScreen(MainActivity activity) { this.a = activity; }

    @Override
    public void build() {
        a.content.removeAllViews();
        LinearLayout root = Ui.v(a);
        root.setBackgroundColor(Theme.BG);
        a.content.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(a.header("Uchambuzi / Analysis",
                "Detailed security analysis", null));

        LinearLayout body = Ui.v(a);
        int p = Ui.dp(a, 24);
        body.setPadding(p, 0, p, Ui.dp(a, 16));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        if (a.selected == null) {
            TextView none = Ui.label(a, "Select a network first (Mitandao).",
                    11, Theme.TEXT_MUTE, false);
            none.setGravity(Gravity.CENTER);
            body.addView(none);
            return;
        }
        Net n = a.selected;
        Analyzer.Result an = Analyzer.analyze(n);

        LinearLayout card = Ui.card(a, Theme.PANEL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, Ui.dp(a, 6), 0, 0);
        body.addView(card, clp);

        TextView title = Ui.label(a, n.displayName() + "  \u2014  Score "
                + an.score + "/100", 14, Theme.named(an.scoreColor), true);
        title.setPadding(Ui.dp(a, 14), Ui.dp(a, 14), 0, Ui.dp(a, 2));
        card.addView(title);

        for (Analyzer.Finding f : an.findings) {
            LinearLayout row = Ui.h(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(a, 14), Ui.dp(a, 2), Ui.dp(a, 14), 0);
            TextView dot = Ui.label(a, "\u25cf", 12,
                    Theme.severityColor(f.severity), false);
            row.addView(dot);
            TextView msg = Ui.label(a, f.message, 10, Theme.TEXT, false);
            msg.setPadding(Ui.dp(a, 6), 0, 0, 0);
            row.addView(msg);
            card.addView(row);
        }

        View sep = new View(a);
        LinearLayout.LayoutParams seplp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 1));
        seplp.setMargins(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8));
        sep.setBackgroundColor(Theme.BORDER);
        card.addView(sep, seplp);

        TextView rl = Ui.label(a, "MAPENDEKEZO / RECOMMENDATIONS", 9,
                Theme.CYAN, true);
        rl.setPadding(Ui.dp(a, 14), 0, 0, 0);
        card.addView(rl);
        for (String rec : an.recommendations) {
            TextView t = Ui.label(a, "\u2022  " + rec, 9, Theme.TEXT, false);
            t.setPadding(Ui.dp(a, 20), Ui.dp(a, 1), Ui.dp(a, 14), Ui.dp(a, 1));
            card.addView(t);
        }

        // channel congestion summary (bonus from analyzer engine)
        Analyzer.ChannelResult ch = Analyzer.channelAnalysis(a.nets);
        LinearLayout chCard = Ui.card(a, Theme.PANEL);
        LinearLayout.LayoutParams chlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chlp.setMargins(0, Ui.dp(a, 8), 0, 0);
        body.addView(chCard, chlp);
        TextView chT = Ui.label(a, "CHANNEL CONGESTION", 9, Theme.CYAN, true);
        chT.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), 0, Ui.dp(a, 2));
        chCard.addView(chT);
        TextView ch24 = Ui.label(a, "2.4 GHz: " + ch.g24.count + " APs  \u00b7  "
                + ch.g24.congestion + " congestion  \u00b7  ch1/"
                + ch.g24.counters.getOrDefault(1, 0) + " ch6/"
                + ch.g24.counters.getOrDefault(6, 0) + " ch11/"
                + ch.g24.counters.getOrDefault(11, 0), 9,
                Theme.named(ch.g24.congestionColor), false);
        ch24.setPadding(Ui.dp(a, 14), 0, 0, 0);
        chCard.addView(ch24);
        TextView ch5 = Ui.label(a, "5 GHz: " + ch.g5.count + " APs  \u00b7  "
                + ch.g5.congestion + " congestion", 9,
                Theme.named(ch.g5.congestionColor), false);
        ch5.setPadding(Ui.dp(a, 14), 0, 0, Ui.dp(a, 12));
        chCard.addView(ch5);

        // actions
        LinearLayout acts = Ui.h(a);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.setMargins(0, Ui.dp(a, 10), 0, 0);
        body.addView(acts, alp);
        TextView re = Ui.darkBtn(a, "Tenkeza uchambuzi mpya (Re-analyze)");
        acts.addView(re);
        re.setOnClickListener(v -> build());
        TextView rep = Ui.redBtn(a, "Tengeneza Ripoti");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(Ui.dp(a, 10), 0, 0, 0);
        acts.addView(rep, rlp);
        rep.setOnClickListener(v -> a.generateReport());
    }

    @Override
    public void onSample() { /* analysis rebuilt on demand */ }
}


/** HISTORIA / History — real scanner samples (timestamp + dBm) only. */
class HistoryScreen implements Ui.Screen {

    private final MainActivity a;
    TextView tableText;

    HistoryScreen(MainActivity activity) { this.a = activity; }

    @Override
    public void build() {
        a.content.removeAllViews();
        LinearLayout root = Ui.v(a);
        root.setBackgroundColor(Theme.BG);
        a.content.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(a.header("Historia / History",
                "Real signal samples from the scanner", null));

        ScrollView scroll = new ScrollView(a);
        scroll.setBackgroundColor(Theme.BG);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout body = Ui.v(a);
        int p = Ui.dp(a, 24);
        body.setPadding(p, 0, p, Ui.dp(a, 16));
        scroll.addView(body);

        Net sel = a.selected;
        if (sel == null) {
            TextView none = Ui.label(a,
                    "Select a network first (Mitandao).",
                    10, Theme.TEXT_MUTE, false);
            body.addView(none);
            return;
        }

        // real sample graph
        LinearLayout hc = Ui.card(a, Theme.PANEL);
        body.addView(hc);
        TextView ht = Ui.label(a,
                "Signal history: " + sel.displayName(), 9, Theme.CYAN, true);
        ht.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), 0, Ui.dp(a, 2));
        hc.addView(ht);

        List<Integer> h = sel.history;
        if (h.size() > 1) {
            int min = h.get(0), max = h.get(0);
            for (int v : h) { min = Math.min(min, v); max = Math.max(max, v); }
            TextView info = Ui.label(a, h.size() + " real samples  \u00b7  last: "
                    + h.get(h.size() - 1) + " dBm  \u00b7  min " + min
                    + " \u00b7 max " + max, 10, Theme.TEXT, false);
            info.setPadding(Ui.dp(a, 12), 0, 0, 0);
            hc.addView(info);
            GraphView mini = new GraphView(a);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 140));
            mlp.setMargins(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
            hc.addView(mini, mlp);
            for (int i = 0; i < h.size(); i++) {
                mini.append(sel.sampleTimeSec(i), h.get(i));
            }
        } else {
            TextView none = Ui.label(a,
                    "No samples yet \u2014 watch the live graph on Mitandao.",
                    10, Theme.TEXT_MUTE, false);
            none.setPadding(Ui.dp(a, 12), Ui.dp(a, 12), 0, Ui.dp(a, 12));
            hc.addView(none);
        }

        // raw sample table: real time + real dBm values only
        LinearLayout tc = Ui.card(a, Theme.PANEL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 300));
        tlp.setMargins(0, Ui.dp(a, 10), 0, 0);
        body.addView(tc, tlp);
        TextView tt = Ui.label(a, "RAW SCANNER SAMPLES  (time \u00b7 dBm)", 9,
                Theme.CYAN, true);
        tt.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), 0, Ui.dp(a, 4));
        tc.addView(tt);
        ScrollView tscroll = new ScrollView(a);
        tscroll.setBackgroundColor(Theme.PANEL);
        LinearLayout.LayoutParams tslp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        tslp.setMargins(Ui.dp(a, 12), 0, Ui.dp(a, 4), Ui.dp(a, 10));
        tc.addView(tscroll, tslp);
        tableText = Ui.mono(a, "", 8, Theme.TEXT_MUTE, false);
        tableText.setPadding(Ui.dp(a, 4), 0, 0, 0);
        tscroll.addView(tableText);
        refreshTable();
    }

    private void refreshTable() {
        if (tableText == null || a.selected == null) return;
        Net sel = a.selected;
        java.text.SimpleDateFormat fmt =
                new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US);
        StringBuilder sb = new StringBuilder();
        List<Integer> h = sel.history;
        for (int i = h.size() - 1; i >= 0; i--) {
            String t = i < sel.historyWhen.size()
                    ? fmt.format(new java.util.Date(sel.historyWhen.get(i)))
                    : "??:??:??";
            sb.append(t).append("   ").append(h.get(i)).append(" dBm\n");
        }
        tableText.setText(sb.length() == 0
                ? "(no scanner samples yet)" : sb.toString());
    }

    @Override
    public void onSample() { refreshTable(); }
}
