package com.cybersafetycs.wifishield;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GRAPHI / Signal Graphs screen — every nearby network's real signal curve
 * on one graph (distinct color per WiFi), plus a legend of network names.
 * Tap a curve or a name -> full-screen graph + details overlay (MainActivity).
 */
class GraphsScreen implements Ui.Screen {

    private final MainActivity a;
    MultiGraphView graph;
    final Map<String, TextView> legendDbm = new LinkedHashMap<>();
    final Map<String, LinearLayout> legendRows = new LinkedHashMap<>();
    /** stable bssid -> palette index (survives screen rebuilds). */
    final Map<String, Integer> colors = new LinkedHashMap<>();

    GraphsScreen(MainActivity activity) { this.a = activity; }

    @Override
    public void build() {
        a.content.removeAllViews();
        legendDbm.clear();
        legendRows.clear();

        LinearLayout root = Ui.v(a);
        root.setBackgroundColor(Theme.BG);
        a.content.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView scanBtn = Ui.redBtn(a, "\u21bb SCAN");
        scanBtn.setOnClickListener(v -> a.startScan());
        root.addView(a.header("Graphi / Signal Graphs",
                "All nearby networks \u2014 real signal curves", scanBtn));

        LinearLayout body = Ui.v(a);
        int p = Ui.dp(a, 20);
        body.setPadding(p, Ui.dp(a, 8), p, Ui.dp(a, 12));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // ---- multi-network graph card ------------------------------------
        LinearLayout card = Ui.card(a, Theme.PANEL);
        body.addView(card, matchWrap());

        graph = new MultiGraphView(a);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 230));
        glp.setMargins(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 4));
        card.addView(graph, glp);

        // window buttons (same windows as every other graph)
        LinearLayout win = Ui.h(a);
        win.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 14), Ui.dp(a, 8));
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
            b.setOnClickListener(v -> {
                if (graph != null) graph.setWindow(k);
                if (a.graphDetailGraph != null) a.graphDetailGraph.setWindow(k);
            });
        }
        card.addView(win);

        TextView hint = Ui.label(a,
                "Tap a curve or a network name to open its full-screen graph.",
                8, Theme.TEXT_MUTE, false);
        LinearLayout.LayoutParams hlp = matchWrap();
        hlp.setMargins(0, Ui.dp(a, 6), 0, Ui.dp(a, 4));
        body.addView(hint, hlp);

        // ---- legend: one row per network (tap -> full screen) ------------
        ScrollView scroll = new ScrollView(a);
        scroll.setBackgroundColor(Theme.BG);
        body.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout legend = Ui.v(a);
        scroll.addView(legend);

        if (!a.hasAnyScanPermission() || a.scanBlockedByLocation()) {
            TextView blocked = Ui.label(a,
                    a.scanBlockedByLocation()
                            ? "WiFi scans blocked \u2014 Location access required."
                            : "Location permission required for WiFi scanning.",
                    10, Theme.AMBER, true);
            blocked.setPadding(0, Ui.dp(a, 10), 0, 0);
            legend.addView(blocked);
        } else if (a.nets.isEmpty()) {
            TextView none = Ui.label(a,
                    a.scanError != null
                            ? ("Scan failed \u2014 " + a.scanError)
                            : "No networks yet \u2014 tap  \u21bb SCAN",
                    10, a.scanError != null ? Theme.RED : Theme.TEXT_MUTE, false);
            none.setPadding(0, Ui.dp(a, 10), 0, 0);
            legend.addView(none);
        } else {
            TextView sec = Ui.section(a, "ALL NETWORKS \u2014 TAP FOR FULL SCREEN");
            sec.setPadding(0, 0, 0, Ui.dp(a, 4));
            legend.addView(sec);
            for (Net n : a.nets) legend.addView(buildLegendRow(n));
        }

        syncGraph();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private View buildLegendRow(Net n) {
        String key = n.bssid.toLowerCase();
        boolean sel = a.selected != null && a.selected.bssid.equals(n.bssid);

        Integer ci = colors.get(key);
        if (ci == null) { ci = colors.size(); colors.put(key, ci); }
        int color = MultiGraphView.PALETTE[ci % MultiGraphView.PALETTE.length];

        LinearLayout row = Ui.h(a);
        row.setBackgroundColor(sel ? Theme.RED_BG : Theme.PANEL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(a, 8);
        row.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, 0, 0, Ui.dp(a, 4));
        row.setLayoutParams(lp);

        TextView chip = Ui.label(a, "\u25cf", 14, color, false);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, Ui.dp(a, 8), 0);
        row.addView(chip, cp);

        LinearLayout mc = Ui.v(a);
        TextView name = Ui.label(a, n.displayName(), 10, Theme.TEXT, true);
        mc.addView(name);
        String sec = n.security == null ? "OPEN" : n.security;
        String sub = sec + "  \u00b7  CH " + n.channel + "  \u00b7  " + n.band();
        if (n.isConnected) sub += "  \u00b7  CONNECTED";
        mc.addView(Ui.label(a, sub, 8, Theme.TEXT_MUTE, false));
        row.addView(mc, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout rc = Ui.v(a);
        rc.setGravity(Gravity.END);
        TextView dbm = Ui.mono(a, n.signalDbm + " dBm", 10,
                Ui.dbmColor(n.signalDbm), true);
        dbm.setGravity(Gravity.END);
        rc.addView(dbm);
        TextView arrow = Ui.label(a, "\u25b6", 8, Theme.TEXT_MUTE, false);
        arrow.setGravity(Gravity.END);
        rc.addView(arrow);
        row.addView(rc);

        legendDbm.put(key, dbm);
        legendRows.put(key, row);
        row.setOnClickListener(v -> openDetail(n));
        return row;
    }

    private void openDetail(Net n) {
        a.selected = n;
        for (Map.Entry<String, LinearLayout> e : legendRows.entrySet()) {
            Net rn = a.netByBssid(e.getKey());
            boolean sel = rn != null && rn.bssid.equals(n.bssid);
            e.getValue().setBackgroundColor(sel ? Theme.RED_BG : Theme.PANEL);
        }
        a.openGraphDetail(n);
    }

    private void syncGraph() {
        if (graph == null) return;
        graph.sync(a.nets, colors);
        graph.setSelected(a.selected == null ? null : a.selected.bssid);
        graph.tapListener = bssid -> {
            Net n = a.netByBssid(bssid);
            if (n != null) openDetail(n);
        };
    }

    @Override
    public void onSample() {
        syncGraph();
        for (Net n : a.nets) {
            TextView t = legendDbm.get(n.bssid.toLowerCase());
            if (t == null) continue;
            t.setText(n.signalDbm + " dBm");
            t.setTextColor(Ui.dbmColor(n.signalDbm));
        }
    }
}
