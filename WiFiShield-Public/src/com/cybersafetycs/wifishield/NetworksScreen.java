package com.cybersafetycs.wifishield;

import android.content.res.Configuration;
import android.graphics.Typeface;
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
 * MITANDAO / Networks screen — list + scrollable detail pane
 * (score, signal, live graph, details, SECURITY FINDINGS, actions).
 * Portrait: tap a row -> detail opens with a back button.
 * Landscape: list left, detail right (desktop two-pane layout).
 */
class NetworksScreen implements Ui.Screen {

    private final MainActivity a;
    private LinearLayout listRows;
    private FrameLayout detailHost;
    private ScrollView listScroll, detailScroll;
    private boolean detailOpen = false;

    // live detail refs
    TextView liveDbm, liveQual;
    GraphView detailGraph;

    // list row refs: bssid -> widgets
    static final class RowRef {
        LinearLayout row;
        TextView dot, name, detail, dbm;
        BarsView bars;
    }
    final Map<String, RowRef> rows = new LinkedHashMap<>();

    NetworksScreen(MainActivity activity) { this.a = activity; }

    boolean isDetailOpen() { return detailOpen; }

    // =====================================================================
    @Override
    public void build() {
        a.content.removeAllViews();
        rows.clear();

        LinearLayout root = Ui.v(a);
        root.setBackgroundColor(Theme.BG);
        a.content.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView scanBtn = Ui.redBtn(a, "\u21bb SCAN");
        scanBtn.setOnClickListener(v -> a.startScan());
        root.addView(a.header("Mitandao / Networks",
                "Real WiFi scan results around you", scanBtn));

        View body;
        if (a.isLandscape()) {
            LinearLayout two = Ui.h(a);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, 0, 1);
            buildListPane();
            two.addView(listScroll, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1.15f));
            buildDetailPane();
            two.addView(detailScroll, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            body = two;
            root.addView(two, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        } else {
            FrameLayout stack = new FrameLayout(a);
            buildListPane();
            stack.addView(listScroll, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            buildDetailPane();
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            detailScroll.setLayoutParams(dlp);
            stack.addView(detailScroll);
            root.addView(stack, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            applyVisibility();
        }

        fillList();
        buildDetail();
    }

    private void buildListPane() {
        listScroll = new ScrollView(a);
        listScroll.setBackgroundColor(Theme.BG);
        LinearLayout outer = Ui.v(a);
        outer.setPadding(Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 12));
        listRows = Ui.v(a);
        outer.addView(listRows);
        listScroll.addView(outer);
    }

    private void buildDetailPane() {
        detailScroll = new ScrollView(a);
        detailScroll.setBackgroundColor(Theme.BG);
        detailScroll.setVerticalScrollBarEnabled(true);
    }

    private void applyVisibility() {
        if (a.isLandscape()) return;
        boolean showDetail = detailOpen && a.selected != null;
        listScroll.setVisibility(showDetail ? View.GONE : View.VISIBLE);
        detailScroll.setVisibility(showDetail ? View.VISIBLE : View.GONE);
    }

    // =====================================================================
    //  location gate (mirrors the Windows 11 Location panel)
    // =====================================================================
    private boolean maybeLocationGate(LinearLayout parent) {
        boolean blocked = !a.hasAnyScanPermission() || a.scanBlockedByLocation();
        if (!blocked && !a.nets.isEmpty()) return false;
        if (!blocked) return false;    // just empty — show empty text instead

        LinearLayout gate = Ui.card(a, Theme.PANEL);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.setMargins(Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 6), 0);
        parent.addView(gate, gp);

        TextView warn = Ui.label(a,
                "WiFi scans are blocked \u2014 Location access required", 10,
                Theme.AMBER, true);
        warn.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 4));
        gate.addView(warn);

        String txt = "Android hides WiFi scan results until Location is available"
                + " (same as Windows 11).\n\n"
                + "Enable:\n"
                + "   \u2022 Location permission for this app\n"
                + "   \u2022 Location / GPS switch ON\n\n"
                + "Then tap  \u21bb SCAN  again.";
        TextView body = Ui.label(a, txt, 9, Theme.TEXT_DIM, false);
        body.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), Ui.dp(a, 4));
        body.setLineSpacing(0, 1.15f);
        gate.addView(body);

        if (!a.hasAnyScanPermission()) {
            TextView grant = Ui.redBtn(a, "\u2699 GRANT LOCATION PERMISSION");
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.setMargins(Ui.dp(a, 12), Ui.dp(a, 4), Ui.dp(a, 12), Ui.dp(a, 4));
            gate.addView(grant, bp);
            grant.setOnClickListener(v -> a.ensurePermissions());
        }
        TextView open = Ui.button(a, "\u2699 OPEN LOCATION SETTINGS",
                Theme.RED, Theme.WHITE, true);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        op.setMargins(Ui.dp(a, 12), Ui.dp(a, 2), Ui.dp(a, 12), Ui.dp(a, 12));
        gate.addView(open, op);
        open.setOnClickListener(v -> a.openLocationSettings());
        return true;
    }

    // =====================================================================
    //  network list
    // =====================================================================
    private void fillList() {
        listRows.removeAllViews();
        rows.clear();

        if (maybeLocationGate(listRows)) return;

        if (a.nets.isEmpty()) {
            TextView none = Ui.label(a,
                    a.scanError != null
                            ? ("Scan failed \u2014 " + a.scanError)
                            : "No networks yet \u2014 tap  \u21bb SCAN",
                    10, a.scanError != null ? Theme.RED : Theme.TEXT_MUTE, false);
            none.setPadding(Ui.dp(a, 8), Ui.dp(a, 10), Ui.dp(a, 8), 0);
            listRows.addView(none);
            return;
        }

        for (Net n : a.nets) listRows.addView(buildRow(n));
    }

    private View buildRow(Net n) {
        boolean sel = a.selected != null && a.selected.bssid.equals(n.bssid);
        LinearLayout row = Ui.h(a);
        row.setBackgroundColor(sel ? Theme.RED_BG : Theme.PANEL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(a, 8);
        row.setPadding(p, p - Ui.dp(a, 2), p, p - Ui.dp(a, 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(a, 4));
        row.setLayoutParams(lp);

        String sec = n.security == null ? "OPEN" : n.security;
        boolean open = sec.toLowerCase().contains("open") || sec.equals("WEP");
        boolean wpa = sec.contains("WPA") && !sec.contains("WPA3");
        int dotCol = open ? Theme.RED : wpa ? Theme.AMBER : Theme.GREEN;

        TextView dot = Ui.label(a, "\u25cf", 11, dotCol, false);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dp.setMargins(0, 0, Ui.dp(a, 8), 0);
        row.addView(dot, dp);

        LinearLayout mc = Ui.v(a);
        TextView name = Ui.label(a, n.displayName(), 10, Theme.TEXT, true);
        mc.addView(name);
        String detailTxt = sec + "  \u00b7  CH " + n.channel + "  \u00b7  " + n.band();
        if (n.isConnected) detailTxt += "  \u00b7  CONNECTED";
        TextView detail = Ui.label(a, detailTxt, 8, Theme.TEXT_MUTE, false);
        mc.addView(detail);
        row.addView(mc, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout rc = Ui.v(a);
        rc.setGravity(Gravity.END);
        TextView dbm = Ui.mono(a, n.signalDbm + " dBm", 9,
                Ui.dbmColor(n.signalDbm), true);
        dbm.setGravity(Gravity.END);
        rc.addView(dbm);
        BarsView bars = new BarsView(a);
        bars.setDbm(n.signalDbm);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(a, 16));
        bp.gravity = Gravity.END;
        rc.addView(bars, bp);
        row.addView(rc);

        RowRef ref = new RowRef();
        ref.row = row; ref.dot = dot; ref.name = name;
        ref.detail = detail; ref.dbm = dbm; ref.bars = bars;
        rows.put(n.bssid.toLowerCase(), ref);

        View.OnClickListener click = v -> selectNet(n);
        row.setOnClickListener(click);
        return row;
    }

    void selectNet(Net n) {
        boolean same = a.selected != null && a.selected.bssid.equals(n.bssid);
        a.selected = n;
        if (!same) detailOpen = true;
        // refresh selection backgrounds
        for (Map.Entry<String, RowRef> e : rows.entrySet()) {
            Net rn = a.netByBssid(e.getKey());
            boolean sel = rn != null && rn.bssid.equals(n.bssid);
            e.getValue().row.setBackgroundColor(sel ? Theme.RED_BG : Theme.PANEL);
        }
        buildDetail();
        applyVisibility();
    }

    // =====================================================================
    //  detail pane (desktop `_build_net_detail` — scrolls, never cut off)
    // =====================================================================
    private void buildDetail() {
        detailScroll.removeAllViews();
        detailScroll.scrollTo(0, 0);
        if (a.selected == null) {
            TextView ph = Ui.label(a, "Select a network from the list.",
                    11, Theme.TEXT_MUTE, false);
            ph.setGravity(Gravity.CENTER);
            ph.setPadding(0, Ui.dp(a, 40), 0, 0);
            detailScroll.addView(ph);
            return;
        }
        Net n = a.selected;
        Analyzer.Result an = Analyzer.analyze(n);

        LinearLayout body = Ui.v(a);
        int hp = Ui.dp(a, 14);
        body.setPadding(hp, 0, hp, Ui.dp(a, 14));
        detailScroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // back button (portrait stack mode)
        if (!a.isLandscape()) {
            TextView back = Ui.darkBtn(a, "\u2190 BACK TO LIST");
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.setMargins(0, Ui.dp(a, 10), 0, 0);
            body.addView(back, blp);
            back.setOnClickListener(v -> {
                detailOpen = false;
                applyVisibility();
            });
        }

        // ---- header card: score -------------------------------------------
        LinearLayout header = Ui.card(a, n.isConnected ? Theme.RED_BG : Theme.PANEL);
        body.addView(header, cardLp());
        LinearLayout hf = Ui.h(a);
        hf.setGravity(Gravity.CENTER_VERTICAL);
        int hp2 = Ui.dp(a, 12);
        hf.setPadding(hp2, Ui.dp(a, 10), hp2, Ui.dp(a, 10));
        header.addView(hf);

        TextView dot = Ui.label(a, "\u25cf", 14, Theme.RED, false);
        hf.addView(dot);
        LinearLayout info = Ui.v(a);
        info.setPadding(Ui.dp(a, 8), 0, 0, 0);
        TextView ssid = Ui.label(a, n.displayName(), 14, Theme.TEXT, true);
        info.addView(ssid);
        TextView enc = Ui.label(a, an.encryptionLabel + "      BSSID "
                + (n.bssid.isEmpty() ? "N/A" : n.bssid), 9,
                Theme.named(an.encryptionColor), false);
        info.addView(enc);
        hf.addView(info, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout sp = Ui.v(a);
        sp.setGravity(Gravity.END);
        TextView score = Ui.label(a, String.valueOf(an.score), 28,
                Theme.named(an.scoreColor), true);
        score.setGravity(Gravity.END);
        sp.addView(score);
        TextView over = Ui.label(a, "/ 100", 9, Theme.TEXT_MUTE, false);
        over.setGravity(Gravity.END);
        sp.addView(over);
        hf.addView(sp);

        LinearLayout bar = Ui.scoreBar(a, an.score, Theme.named(an.scoreColor));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 6));
        blp.setMargins(Ui.dp(a, 14), 0, Ui.dp(a, 14), Ui.dp(a, 12));
        header.addView(bar, blp);

        // ---- signal block -------------------------------------------------
        body.addView(block("NGUVU YA SIGNAL / SIGNAL STRENGTH", this::buildSignalBlock));

        // ---- live graph ---------------------------------------------------
        detailGraph = new GraphView(a);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 190));
        glp.setMargins(Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 14), 0);
        body.addView(detailGraph, glp);

        LinearLayout win = Ui.h(a);
        win.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 14), 0);
        win.setGravity(Gravity.CENTER_VERTICAL);
        TextView wl = Ui.label(a, "SIGNAL HISTORY", 9, Theme.TEXT_DIM, true);
        win.addView(wl, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        for (final String k : new String[]{GraphView.WIN_LIVE, GraphView.WIN_30,
                GraphView.WIN_60, GraphView.WIN_300}) {
            TextView b = Ui.button(a, k, Theme.PANEL_ALT, Theme.TEXT_DIM, false);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.setMargins(Ui.dp(a, 2), 0, 0, 0);
            b.setPadding(Ui.dp(a, 7), Ui.dp(a, 3), Ui.dp(a, 7), Ui.dp(a, 3));
            win.addView(b, bl);
            b.setOnClickListener(v -> {
                if (detailGraph != null) detailGraph.setWindow(k);
            });
        }
        body.addView(win);

        // ---- network details ----------------------------------------------
        body.addView(block("MAELEZO YA MTANDAO / NETWORK DETAILS", this::buildDetails));

        // ---- security findings --------------------------------------------
        body.addView(block("UCHAMBUZI WA USALAMA / SECURITY FINDINGS",
                this::buildFindings));

        // ---- action buttons -----------------------------------------------
        LinearLayout acts = Ui.h(a);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.setMargins(Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 14), Ui.dp(a, 8));
        body.addView(acts, alp);
        TextView run = Ui.redBtn(a, "RUN ANALYSIS");
        run.setPadding(Ui.dp(a, 18), run.getPaddingTop(),
                Ui.dp(a, 18), run.getPaddingBottom());
        acts.addView(run);
        run.setOnClickListener(v -> a.showScreen(MainActivity.ANA));
        TextView rep = Ui.darkBtn(a, "TENGENEZA RIPOTI / GENERATE REPORT");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(Ui.dp(a, 10), 0, 0, 0);
        acts.addView(rep, rlp);
        rep.setOnClickListener(v -> a.generateReport());

        feedGraph();
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(a, 6), 0, Ui.dp(a, 2));
        return lp;
    }

    /** Card with cyan section title + content builder (desktop `_detail_block`). */
    private LinearLayout block(String title, ContentBuilder builder) {
        LinearLayout card = Ui.card(a, Theme.PANEL);
        card.addView(Ui.section(a, title));
        LinearLayout inner = Ui.v(a);
        int p = Ui.dp(a, 12);
        inner.setPadding(p, Ui.dp(a, 4), p, Ui.dp(a, 12));
        card.addView(inner);
        builder.build(inner);
        card.setLayoutParams(cardLp());
        return card;
    }

    interface ContentBuilder { void build(LinearLayout parent); }

    private void buildSignalBlock(LinearLayout f) {
        LinearLayout row = Ui.h(f.getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        Analyzer.Result an = Analyzer.analyze(a.selected);
        int col = Theme.named(an.signalColor);
        liveDbm = Ui.mono(f.getContext(), a.selected.signalDbm + " dBm   ",
                18, col, true);
        row.addView(liveDbm);
        liveQual = Ui.label(f.getContext(), an.signalLabel, 11, col, true);
        liveQual.setPadding(Ui.dp(f.getContext(), 6), 0, 0, 0);
        row.addView(liveQual);
        TextView scale = Ui.label(f.getContext(),
                "  Weak  Fair  Good  Excellent", 8, Theme.TEXT_MUTE, false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.END;
        row.addView(scale, slp);
        f.addView(row);
    }

    private void buildDetails(LinearLayout f) {
        Net n = a.selected;
        Analyzer.Result an = Analyzer.analyze(n);
        grid3(f, new String[][]{
                {"Channel", String.valueOf(n.channel)},
                {"Frequency", n.frequency + " MHz"},
                {"Band", n.band()},
                {"Signal", n.signalDbm + " dBm"},
                {"Security", an.securityRaw.isEmpty() ? "OPEN" : an.securityRaw},
                {"BSSID", n.bssid.isEmpty() ? "N/A" : n.bssid},
                {"Wi-Fi", n.wifiCell()},
                {"PMF", n.pmf != null ? n.pmf : "Not advertised"},
                {"WPS", n.wps != null ? n.wps : "Not advertised"},
        });
    }

    /** 3-column key/value grid. */
    private void grid3(LinearLayout f, String[][] pairs) {
        for (int i = 0; i < pairs.length; i += 3) {
            LinearLayout row = Ui.h(f.getContext());
            for (int j = i; j < i + 3 && j < pairs.length; j++) {
                LinearLayout cell = Ui.v(f.getContext());
                TextView k = Ui.label(f.getContext(), pairs[j][0].toUpperCase(),
                        7, Theme.TEXT_MUTE, true);
                TextView v = Ui.label(f.getContext(), pairs[j][1], 9,
                        Theme.TEXT, false);
                cell.addView(k);
                cell.addView(v);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                clp.setMargins(0, 0, Ui.dp(f.getContext(), 8), Ui.dp(f.getContext(), 6));
                cell.setLayoutParams(clp);
                row.addView(cell);
            }
            f.addView(row);
        }
    }

    private void buildFindings(LinearLayout f) {
        Net n = a.selected;
        Analyzer.Result an = Analyzer.analyze(n);
        for (Analyzer.Finding fd : an.findings) {
            LinearLayout card = Ui.v(f.getContext());
            card.setBackgroundColor(Theme.PANEL_ALT);
            android.graphics.drawable.GradientDrawable bd =
                    new android.graphics.drawable.GradientDrawable();
            bd.setColor(Theme.PANEL_ALT);
            bd.setStroke(Ui.dp(f.getContext(), 1), Theme.BORDER);
            bd.setCornerRadius(Ui.dp(f.getContext(), 6));
            card.setBackground(bd);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMargins(0, 0, 0, Ui.dp(f.getContext(), 6));
            f.addView(card, clp);

            LinearLayout inner = Ui.h(f.getContext());
            int p = Ui.dp(f.getContext(), 8);
            inner.setPadding(p, p, p, p);
            card.addView(inner);

            TextView dot = Ui.label(f.getContext(), "\u25cf", 12,
                    Theme.severityColor(fd.severity), false);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.setMargins(0, 0, Ui.dp(f.getContext(), 6), 0);
            inner.addView(dot, dlp);

            LinearLayout tx = Ui.v(f.getContext());
            TextView msg = Ui.label(f.getContext(), fd.message, 9,
                    Theme.TEXT, false);
            TextView det = Ui.label(f.getContext(), fd.detail, 8,
                    Theme.TEXT_MUTE, false);
            tx.addView(msg);
            if (!fd.detail.isEmpty()) tx.addView(det);
            inner.addView(tx, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
    }

    /** Seed the graph from the selected network's rolling history. */
    private void feedGraph() {
        if (detailGraph == null || a.selected == null) return;
        detailGraph.clearSamples();
        java.util.List<Integer> h = a.selected.history;
        for (int i = 0; i < h.size(); i++) {
            detailGraph.append(a.selected.sampleTimeSec(i), h.get(i));
        }
    }

    // =====================================================================
    //  live updates (called from MainActivity after each real sample)
    // =====================================================================
    @Override
    public void onSample() {
        // list rows: in-place dbm/bars refresh (no rebuild vibration)
        for (Net n : a.nets) {
            RowRef ref = rows.get(n.bssid.toLowerCase());
            if (ref == null) continue;
            ref.dbm.setText(n.signalDbm + " dBm");
            ref.dbm.setTextColor(Ui.dbmColor(n.signalDbm));
            ref.bars.setDbm(n.signalDbm);
        }
        // detail pane
        Net sel = a.selected;
        if (sel != null) {
            if (liveDbm != null && liveQual != null) {
                Analyzer.Result an = Analyzer.analyze(sel);
                int col = Theme.named(an.signalColor);
                liveDbm.setText(sel.signalDbm + " dBm   ");
                liveDbm.setTextColor(col);
                liveQual.setText(an.signalLabel);
                liveQual.setTextColor(col);
            }
            if (detailGraph != null) {
                detailGraph.append(System.currentTimeMillis() / 1000.0,
                        sel.signalDbm);
            }
        }
    }
}
