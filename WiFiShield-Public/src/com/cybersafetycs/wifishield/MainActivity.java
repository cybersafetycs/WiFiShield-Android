package com.cybersafetycs.wifishield;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.ConfigurationInfo;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * WiFiShield for Android — dark cyber UI, real scans only, security scoring,
 * coloured PDF reports. Mirrors the desktop app screen-for-screen.
 */
public class MainActivity extends Activity {

    // nav keys (same names as the desktop sidebar)
    static final String NET = "MITANDAO";
    static final String ANA = "UCHAMBUZI";
    static final String CAP = "CAPTURE";
    static final String GRPH = "GRAPHI";
    static final String REP = "RIPOTI";
    static final String HIST = "HISTORIA";
    static final String MAW = "MAWASILIANO";

    static final String[] NAV_ORDER = {NET, ANA, CAP, GRPH, REP, HIST, MAW};
    static final String[] NAV_SUB = {
            "Networks", "Analysis", "Live capture", "Signal graphs",
            "Reports", "History", "About / Contact"};
    static final int REQ_PERMS = 41;
    static final int REQ_NOTIF = 42;

    // ---- state -------------------------------------------------------------
    final List<Net> nets = new ArrayList<>();
    Net selected;
    String scanError;
    String currentScreen = NET;
    final Handler ui = new Handler(Looper.getMainLooper());
    WifiScanner scanner;
    FirebaseClient firebase;
    FileHolder files = new FileHolder();
    boolean notifAsked = false;
    String lastBanner = null;

    byte[] logoHeader, logoFooter, watermark;

    // ---- shell views ---------------------------------------------------------
    FrameLayout content;
    LinearLayout sidebar;
    final Map<String, TextView> navButtons = new LinkedHashMap<>();
    TextView stateDot, stateLabel;
    TextView bannerLabel;
    final Map<String, Ui.Screen> screens = new HashMap<>();

    // sampler
    boolean sampleBusy = false;
    long lastScanReq = 0;
    final Set<String> samplerErrs = new HashSet<>();
    boolean tickStarted = false;

    static final class FileHolder {
        java.io.File reportsDir;
    }

    // ======================================================================
    //  lifecycle
    // ======================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        files.reportsDir = LogStore.reportsDir(this);
        logoHeader = readAsset("logo_header.jpg");
        logoFooter = readAsset("logo_footer.jpg");
        watermark = readAsset("watermark.png");

        firebase = new FirebaseClient(this);

        // FCM notification channel (used by MessagingService + FCM system UI)
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    "wifishield_alerts", "WiFiShield alerts",
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }

        // Remote Config / Crashlytics / App Check / FCM init (no-op in
        // the SDK-less build.sh fallback — FbKit checks for fb.Fb)
        FbKit.init(this);

        scanner = new WifiScanner(this);
        scanner.setListener(this::onScanResult);

        screens.put(NET, new NetworksScreen(this));
        screens.put(ANA, new AnalysisScreen(this));
        screens.put(CAP, new CaptureScreen(this));
        screens.put(GRPH, new GraphsScreen(this));
        screens.put(REP, new ReportsScreen(this));
        screens.put(HIST, new HistoryScreen(this));
        screens.put(MAW, new AboutScreen(this));

        buildShell();
        LogStore.log(this, "WiFiShield started v1.4.6");
        showScreen(NET);
        ensurePermissions();

        scanner.register();
        lastScanReq = System.currentTimeMillis();
        ui.postDelayed(this::startScan, 400);
        if (!tickStarted) {
            tickStarted = true;
            ui.postDelayed(this::tick, 2000);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // user may have just enabled Location / granted permission or
        // switched WiFi in system settings while we were backgrounded
        if (hasAnyScanPermission() && !scanBlockedByLocation()) {
            ui.postDelayed(this::startScan, 300);
        }
        Ui.Screen s = screens.get(currentScreen);
        if (s != null) s.build();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { scanner.unregister(); } catch (Exception ignored) {}
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Ui.Screen s = screens.get(currentScreen);
        if (s != null) s.build();
    }

    // ======================================================================
    //  shell: sidebar + content
    // ======================================================================
    private void buildShell() {
        LinearLayout root = Ui.h(this);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);

        // sidebar
        sidebar = Ui.v(this);
        sidebar.setBackgroundColor(Theme.SIDEBAR);
        int sw = Ui.dp(this, 118);
        root.addView(sidebar, new LinearLayout.LayoutParams(
                sw, ViewGroup.LayoutParams.MATCH_PARENT));

        // brand
        LinearLayout brand = Ui.v(this);
        brand.setGravity(Gravity.CENTER_HORIZONTAL);
        int bp = Ui.dp(this, 10);
        brand.setPadding(bp, bp + Ui.dp(this, 6), bp, bp);
        if (watermark != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(android.graphics.BitmapFactory
                    .decodeByteArray(watermark, 0, watermark.length));
            int sz = Ui.dp(this, 44);
            brand.addView(iv, new LinearLayout.LayoutParams(sz, sz));
        }
        TextView tag = Ui.label(this, "CYBER\nSAFETYCS", 9, Theme.RED, true);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(0, Ui.dp(this, 4), 0, 0);
        brand.addView(tag);
        sidebar.addView(brand);

        // nav buttons
        for (String key : NAV_ORDER) {
            final String k = key;
            LinearLayout item = Ui.v(this);
            TextView t = Ui.label(this, key, 10, Theme.TEXT_DIM, true);
            TextView sub = Ui.label(this, NAV_SUB[navSubIndex(key)], 7, Theme.TEXT_MUTE, false);
            int p = Ui.dp(this, 9);
            item.setPadding(p + Ui.dp(this, 6), p, p, p);
            item.addView(t);
            item.addView(sub);
            item.setOnClickListener(v -> showScreen(k));
            sidebar.addView(item, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            navButtons.put(key, t);
        }

        // spacer
        View spacer = new View(this);
        sidebar.addView(spacer, new LinearLayout.LayoutParams(
                0, 0, 1));

        // status footer
        LinearLayout foot = Ui.v(this);
        int fp = Ui.dp(this, 10);
        foot.setPadding(fp, fp, fp, fp + Ui.dp(this, 8));
        LinearLayout st = Ui.h(this);
        st.setGravity(Gravity.CENTER_VERTICAL);
        stateDot = Ui.label(this, "\u25cf", 11, Theme.AMBER, false);
        st.addView(stateDot);
        stateLabel = Ui.label(this, "SCANNING", 8, Theme.AMBER, true);
        stateLabel.setPadding(Ui.dp(this, 6), 0, 0, 0);
        st.addView(stateLabel);
        foot.addView(st);

        // Remote Config banner (hidden until banner_text param is published)
        bannerLabel = Ui.label(this, "", 7, Theme.AMBER, true);
        bannerLabel.setPadding(0, Ui.dp(this, 4), 0, 0);
        bannerLabel.setVisibility(View.GONE);
        foot.addView(bannerLabel);

        TextView ver = Ui.label(this, "v1.4.6  \u00b7  CYBERSAFETYCS", 7, Theme.TEXT_MUTE, false);
        ver.setPadding(0, Ui.dp(this, 4), 0, 0);
        foot.addView(ver);
        sidebar.addView(foot);

        // content
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private static int navSubIndex(String key) {
        for (int i = 0; i < NAV_ORDER.length; i++) if (NAV_ORDER[i].equals(key)) return i;
        return 0;
    }

    void showScreen(String key) {
        currentScreen = key;
        for (Map.Entry<String, TextView> e : navButtons.entrySet()) {
            boolean sel = e.getKey().equals(key);
            e.getValue().setTextColor(sel ? Theme.RED : Theme.TEXT_DIM);
            LinearLayout item = (LinearLayout) e.getValue().getParent();
            item.setBackgroundColor(sel ? Theme.RED_BG : 0x00000000);
        }
        content.removeAllViews();
        Ui.Screen s = screens.get(key);
        if (s != null) s.build();
    }

    /** Standard screen header: title + subtitle (+ optional right button). */
    LinearLayout header(String title, String subtitle, View right) {
        LinearLayout bar = Ui.v(this);
        bar.setBackgroundColor(Theme.BG);
        int p = Ui.dp(this, 20);
        LinearLayout row = Ui.h(this);
        row.setPadding(p, p + Ui.dp(this, 6), p, p / 2);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout cols = Ui.v(this);
        TextView t = Ui.label(this, title, 17, Theme.TEXT, true);
        TextView s = Ui.label(this, subtitle, 10, Theme.TEXT_MUTE, false);
        cols.addView(t);
        cols.addView(s);
        row.addView(cols, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (right != null) row.addView(right);
        bar.addView(row);
        return bar;
    }

    // ======================================================================
    //  permissions / location gate (same story as Windows 11 Location)
    // ======================================================================
    boolean hasAnyScanPermission() {
        return scanner != null && scanner.hasScanPermission();
    }

    boolean scanBlockedByLocation() {
        return scanner != null && scanner.locationBlocked();
    }

    void ensurePermissions() {
        // Android 13+: runtime notification permission (FCM push display)
        if (Build.VERSION.SDK_INT >= 33 && !notifAsked) {
            notifAsked = true;
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIF);
            }
        }
        if (hasAnyScanPermission()) return;
        List<String> need = new ArrayList<>();
        need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) {
            need.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        requestPermissions(need.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) {
            boolean ok = grantResults.length > 0
                    && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            LogStore.log(this, "Notification permission "
                    + (ok ? "GRANTED" : "DENIED (pushes won't display)"));
            return;
        }
        boolean granted = hasAnyScanPermission();
        LogStore.log(this, "Location permission "
                + (granted ? "GRANTED" : "DENIED"));
        Ui.Screen s = screens.get(currentScreen);
        if (s != null) s.build();
        if (granted) ui.postDelayed(this::startScan, 300);
    }

    void openLocationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            LogStore.log(this, "User opened Location settings");
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Location settings",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ======================================================================
    //  scanning (real results only — never faked)
    // ======================================================================
    void startScan() {
        setState("SCANNING", Theme.AMBER);
        if (!hasAnyScanPermission()) {
            scanError = WifiScanner.LOCATION_REQUIRED;
            setState("LOCATION NEEDED", Theme.RED);
            LogStore.logScan(this, 0, "wifi", scanError);
            Ui.Screen s = screens.get(currentScreen);
            if (s != null) s.build();
            return;
        }
        if (scanBlockedByLocation()) {
            scanError = WifiScanner.LOCATION_REQUIRED;
            setState("LOCATION OFF", Theme.RED);
            Ui.Screen s = screens.get(currentScreen);
            if (s != null) s.build();
            return;
        }
        scanError = null;
        setState("SCANNING", Theme.AMBER);
        lastScanReq = System.currentTimeMillis();
        scanner.startScan();
        // NOTE: never push cached results here — only real fresh broadcasts
        // (EXTRA_RESULTS_UPDATED) are shown, so stale SSIDs never appear.
    }

    /** Called on the main thread by WifiScanner (broadcast or immediate). */
    void onScanResult(List<Net> fresh, String err) {
        if (err != null && !err.isEmpty()) {
            scanError = err;
            setState(err.startsWith("LOCATION") ? "LOCATION NEEDED" : "SCAN ERROR",
                    Theme.RED);
            LogStore.logScan(this, 0, "wifi", err);
            Ui.Screen s = screens.get(currentScreen);
            if (s != null) s.build();
            return;
        }
        scanError = null;
        mergeFull(fresh);
        firebase.syncScans(nets);
        SdkStatus.logEvent(this, "scan_synced", "networks", nets.size());
        FbKit.breadcrumb("scan ok n=" + nets.size());
        String t = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(new java.util.Date());
        setState(nets.isEmpty() ? "0 NETWORKS" : "READY (" + nets.size() + ") \u00b7 " + t,
                nets.isEmpty() ? Theme.TEXT_MUTE : Theme.GREEN);
        LogStore.logScan(this, nets.size(), "wifi", null);
        Ui.Screen s = screens.get(currentScreen);
        if (s != null) s.build();
        // full-screen GRAPHI overlay survives the rebuild — reseed its graph
        if (graphDetailGraph != null) reseedGraphDetail();
    }

    /** Full refresh: keep existing Net objects (history + selection survive). */
    void mergeFull(List<Net> fresh) {
        long nowMs = System.currentTimeMillis();
        Map<String, Net> old = new LinkedHashMap<>();
        for (Net n : nets) old.put(n.bssid.toLowerCase(), n);
        List<Net> merged = new ArrayList<>();
        for (Net f : fresh) {
            Net o = old.get(f.bssid.toLowerCase());
            if (o != null) {
                int prev = o.signalDbm;
                o.ssid = f.ssid;
                o.channel = f.channel;
                o.frequency = f.frequency;
                o.signalDbm = f.signalDbm;
                o.security = f.security;
                o.caps = f.caps;
                o.standard = f.standard;
                o.width = f.width;
                o.wps = f.wps;
                o.pmf = f.pmf;
                o.hidden = f.hidden;
                o.isConnected = f.isConnected;
                o.lastSeen = f.lastSeen;
                // append a real sample only when the value changed or >5 s passed
                if (o.history.isEmpty()) {
                    o.history.add(f.signalDbm);
                    o.historyWhen.add(nowMs);
                } else {
                    long lastWhen = o.historyWhen.isEmpty() ? 0
                            : o.historyWhen.get(o.historyWhen.size() - 1);
                    if (prev != f.signalDbm || nowMs - lastWhen > 5000) {
                        o.history.add(f.signalDbm);
                        o.historyWhen.add(nowMs);
                    }
                }
                trimHistory(o);
                merged.add(o);
            } else {
                f.history.add(f.signalDbm);
                f.historyWhen.add(nowMs);
                merged.add(f);
            }
        }
        nets.clear();
        nets.addAll(merged);
    }

    private static void trimHistory(Net n) {
        if (n.history.size() > 900) {
            int cut = n.history.size() - 900;
            n.history = new ArrayList<>(n.history.subList(cut, n.history.size()));
            if (n.historyWhen.size() > n.history.size()) {
                int wc = n.historyWhen.size() - n.history.size();
                n.historyWhen = new ArrayList<>(n.historyWhen.subList(
                        wc, n.historyWhen.size()));
            }
        }
    }

    Net netByBssid(String bssid) {
        if (bssid == null) return null;
        for (Net n : nets) if (n.bssid.equalsIgnoreCase(bssid)) return n;
        return null;
    }

    // ======================================================================
    //  live sampler: real quick re-measure every 2 s (like the desktop)
    // ======================================================================
    private void tick() {
        ui.postDelayed(this::tick, 2000);
        // real periodic re-scan — interval comes from Remote Config param
        // scan_interval_sec (default 45 s, throttle-safe ~4 fg scans/2 min)
        long now = System.currentTimeMillis();
        long iv = FbKit.scanIntervalSec() * 1000L;
        if (now - lastScanReq >= iv
                && hasAnyScanPermission() && !scanBlockedByLocation()) {
            lastScanReq = now;
            scanner.startScan();
        }
        // Remote Config banner_text → footer (hidden while empty)
        String b = FbKit.banner();
        if (!b.equals(lastBanner == null ? "" : lastBanner)) {
            lastBanner = b;
            if (bannerLabel != null) {
                bannerLabel.setText(b);
                bannerLabel.setVisibility(b.isEmpty() ? View.GONE : View.VISIBLE);
            }
        }
        // re-check the CONNECTED mark every tick — cached WifiInfo can lag
        // after you disconnect/switch; only an actually completed link counts
        if (!nets.isEmpty() && hasAnyScanPermission()) {
            String cb = scanner.connectedBssid();
            boolean changed = false;
            for (Net n : nets) {
                boolean conn = !cb.isEmpty() && cb.equalsIgnoreCase(n.bssid);
                if (n.isConnected != conn) { n.isConnected = conn; changed = true; }
            }
            if (changed) {
                Ui.Screen s = screens.get(currentScreen);
                if (s != null) s.build();
            }
        }
        String key = currentScreen;
        if (!key.equals(NET) && !key.equals(CAP) && !key.equals(GRPH)) return;
        if (scanError != null && scanError.startsWith("LOCATION")) return;
        if (sampleBusy) return;
        if (!hasAnyScanPermission() || scanBlockedByLocation()) return;
        sampleBusy = true;
        new Thread(() -> {
            String[] err = {null};
            List<Net> res = scanner.readResults(err);
            ui.post(() -> {
                sampleBusy = false;
                if (err[0] != null && !err[0].isEmpty()) {
                    if (samplerErrs.add(err[0])) {
                        LogStore.log(this, "live sample failed: " + err[0],
                                "WARNING");
                    }
                    return;
                }
                if (!res.isEmpty()) applySample(res);
            });
        }, "wifishield-sample").start();
    }

    /** Merge a fresh real measurement into existing nets + feed visible graph. */
    void applySample(List<Net> fresh) {
        Map<String, Net> byBssid = new HashMap<>();
        for (Net n : fresh) byBssid.put(n.bssid.toLowerCase(), n);
        long nowMs = System.currentTimeMillis();
        for (Net old : nets) {
            Net m = byBssid.get(old.bssid.toLowerCase());
            if (m == null) continue;
            old.signalDbm = m.signalDbm;
            old.standard = m.standard;
            old.width = m.width;
            old.lastSeen = nowMs / 1000L;
            old.history.add(m.signalDbm);
            old.historyWhen.add(nowMs);
            trimHistory(old);
        }
        Ui.Screen s = screens.get(currentScreen);
        if (s != null) s.onSample();
        // full-screen GRAPHI overlay: append only when a REAL sample arrived
        if (graphDetailGraph != null && graphDetailNet != null
                && graphDetailNet.history.size() > graphDetailHistSize) {
            graphDetailHistSize = graphDetailNet.history.size();
            graphDetailGraph.append(nowMs / 1000.0, graphDetailNet.signalDbm);
            if (graphDetailSignal != null) {
                Analyzer.Result an = Analyzer.analyze(graphDetailNet);
                graphDetailSignal.setText(graphDetailNet.signalDbm + " dBm   ");
                graphDetailSignal.setTextColor(Theme.named(an.signalColor));
            }
        }
    }

    // ======================================================================
    //  GRAPHI full-screen signal graph overlay (tap a curve / network name)
    // ======================================================================
    Net graphDetailNet;
    GraphView graphDetailGraph;
    View graphDetailOverlay;
    TextView graphDetailSignal;
    int graphDetailHistSize;

    void openGraphDetail(Net n) {
        closeGraphDetail();
        graphDetailNet = n;
        selected = n;
        LogStore.log(this, "Graph detail opened: " + n.displayName());

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Theme.BG);
        graphDetailOverlay = scroll;
        LinearLayout body = Ui.v(this);
        int p = Ui.dp(this, 20);
        body.setPadding(p, p, p, p + Ui.dp(this, 16));
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- top bar: BACK + title ---------------------------------------
        LinearLayout top = Ui.h(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.darkBtn(this, "\u2190 BACK");
        top.addView(back);
        back.setOnClickListener(v -> closeGraphDetail());
        TextView title = Ui.label(this, "SIGNAL GRAPH", 13, Theme.TEXT, true);
        title.setPadding(Ui.dp(this, 14), 0, 0, 0);
        top.addView(title);
        LinearLayout.LayoutParams tlp = matchWrap();
        tlp.setMargins(0, 0, 0, Ui.dp(this, 8));
        body.addView(top, tlp);

        // ---- identity + score card ---------------------------------------
        Analyzer.Result an = Analyzer.analyze(n);
        LinearLayout header = Ui.card(this, n.isConnected ? Theme.RED_BG : Theme.PANEL);
        body.addView(header, matchWrap());
        LinearLayout hf = Ui.h(this);
        hf.setGravity(Gravity.CENTER_VERTICAL);
        int hp = Ui.dp(this, 12);
        hf.setPadding(hp, Ui.dp(this, 10), hp, Ui.dp(this, 10));
        header.addView(hf);
        TextView dot = Ui.label(this, "\u25cf", 14, Theme.RED, false);
        hf.addView(dot);
        LinearLayout info = Ui.v(this);
        info.setPadding(Ui.dp(this, 8), 0, 0, 0);
        info.addView(Ui.label(this, n.displayName(), 14, Theme.TEXT, true));
        info.addView(Ui.label(this, an.encryptionLabel + "      BSSID "
                + (n.bssid.isEmpty() ? "N/A" : n.bssid), 9,
                Theme.named(an.encryptionColor), false));
        hf.addView(info, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout sp = Ui.v(this);
        sp.setGravity(Gravity.END);
        TextView score = Ui.label(this, String.valueOf(an.score), 28,
                Theme.named(an.scoreColor), true);
        score.setGravity(Gravity.END);
        sp.addView(score);
        TextView over = Ui.label(this, "/ 100", 9, Theme.TEXT_MUTE, false);
        over.setGravity(Gravity.END);
        sp.addView(over);
        hf.addView(sp);
        LinearLayout bar = Ui.scoreBar(this, an.score, Theme.named(an.scoreColor));
        LinearLayout.LayoutParams blp = matchWrap();
        blp.setMargins(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 12));
        header.addView(bar, blp);

        // ---- big realistic signal graph ----------------------------------
        LinearLayout card = Ui.card(this, Theme.PANEL);
        body.addView(card, cardLp());
        graphDetailGraph = new GraphView(this);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 240));
        glp.setMargins(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 4));
        card.addView(graphDetailGraph, glp);

        LinearLayout win = Ui.h(this);
        win.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 8));
        win.setGravity(Gravity.CENTER_VERTICAL);
        TextView wl = Ui.label(this, "SIGNAL HISTORY", 9, Theme.TEXT_DIM, true);
        win.addView(wl, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        for (final String k : new String[]{GraphView.WIN_LIVE, GraphView.WIN_30,
                GraphView.WIN_60, GraphView.WIN_300}) {
            TextView b = Ui.button(this, k, Theme.PANEL_ALT, Theme.TEXT_DIM, false);
            b.setPadding(Ui.dp(this, 7), Ui.dp(this, 3), Ui.dp(this, 7), Ui.dp(this, 3));
            LinearLayout.LayoutParams bw = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bw.setMargins(Ui.dp(this, 2), 0, 0, 0);
            win.addView(b, bw);
            final GraphView g = graphDetailGraph;
            b.setOnClickListener(v -> g.setWindow(k));
        }
        card.addView(win);

        // ---- stats -------------------------------------------------------
        LinearLayout statsRow = Ui.h(this);
        LinearLayout.LayoutParams splp = matchWrap();
        splp.setMargins(0, Ui.dp(this, 10), 0, 0);
        body.addView(statsRow, splp);
        String[][] items = {
                {"Samples", String.valueOf(n.history.size())},
                {"Signal", n.signalDbm + " dBm"},
                {"Security", n.security == null ? "OPEN" : n.security},
        };
        for (String[] item : items) {
            LinearLayout cc = Ui.card(this, Theme.PANEL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            clp.setMargins(Ui.dp(this, 5), 0, Ui.dp(this, 5), 0);
            statsRow.addView(cc, clp);
            TextView v = Ui.label(this, item[1], 17,
                    "Signal".equals(item[0])
                            ? Theme.named(an.signalColor) : Theme.GREEN, true);
            v.setGravity(Gravity.CENTER);
            v.setPadding(0, Ui.dp(this, 8), 0, 0);
            cc.addView(v);
            if ("Signal".equals(item[0])) graphDetailSignal = v;
            TextView k = Ui.label(this, item[0], 9, Theme.TEXT_MUTE, false);
            k.setGravity(Gravity.CENTER);
            k.setPadding(0, 0, 0, Ui.dp(this, 8));
            cc.addView(k);
        }

        // ---- network details grid ----------------------------------------
        LinearLayout details = Ui.card(this, Theme.PANEL);
        details.addView(Ui.section(this, "MAELEZO YA MTANDAO / NETWORK DETAILS"));
        LinearLayout inner = Ui.v(this);
        int ip = Ui.dp(this, 12);
        inner.setPadding(ip, Ui.dp(this, 4), ip, Ui.dp(this, 12));
        details.addView(inner);
        grid3(inner, new String[][]{
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
        body.addView(details, cardLp());

        // ---- security findings -------------------------------------------
        LinearLayout findings = Ui.card(this, Theme.PANEL);
        findings.addView(Ui.section(this, "UCHAMBUZI WA USALAMA / SECURITY FINDINGS"));
        LinearLayout fin = Ui.v(this);
        fin.setPadding(ip, Ui.dp(this, 2), ip, Ui.dp(this, 12));
        findings.addView(fin);
        for (Analyzer.Finding f : an.findings) {
            LinearLayout row = Ui.h(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
            TextView fd = Ui.label(this, "\u25cf", 12,
                    Theme.severityColor(f.severity), false);
            row.addView(fd);
            LinearLayout tx = Ui.v(this);
            tx.setPadding(Ui.dp(this, 6), 0, 0, 0);
            tx.addView(Ui.label(this, f.message, 9, Theme.TEXT, false));
            if (!f.detail.isEmpty()) {
                tx.addView(Ui.label(this, f.detail, 8, Theme.TEXT_MUTE, false));
            }
            row.addView(tx, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            fin.addView(row);
        }
        body.addView(findings, cardLp());

        // ---- actions ------------------------------------------------------
        LinearLayout acts = Ui.h(this);
        LinearLayout.LayoutParams alp = matchWrap();
        alp.setMargins(0, Ui.dp(this, 12), 0, 0);
        body.addView(acts, alp);
        TextView run = Ui.redBtn(this, "RUN ANALYSIS");
        run.setPadding(Ui.dp(this, 18), run.getPaddingTop(),
                Ui.dp(this, 18), run.getPaddingBottom());
        acts.addView(run);
        run.setOnClickListener(v -> {
            selected = n;
            closeGraphDetail();
            showScreen(ANA);
        });
        TextView close = Ui.darkBtn(this, "\u2190 CLOSE");
        LinearLayout.LayoutParams rlp = matchWrap();
        rlp.setMargins(Ui.dp(this, 10), 0, 0, 0);
        acts.addView(close, rlp);
        close.setOnClickListener(v -> closeGraphDetail());

        // seed from real history, then put it on top of everything
        reseedGraphDetail();
        addContentView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Feed the overlay graph from the net's real timestamps (never fabricated). */
    void reseedGraphDetail() {
        if (graphDetailGraph == null || graphDetailNet == null) return;
        graphDetailGraph.clearSamples();
        List<Integer> h = graphDetailNet.history;
        for (int i = 0; i < h.size(); i++) {
            graphDetailGraph.append(graphDetailNet.sampleTimeSec(i), h.get(i));
        }
        graphDetailHistSize = h.size();
    }

    void closeGraphDetail() {
        if (graphDetailOverlay == null) return;
        ViewGroup parent = (ViewGroup) graphDetailOverlay.getParent();
        if (parent != null) parent.removeView(graphDetailOverlay);
        graphDetailOverlay = null;
        graphDetailGraph = null;
        graphDetailSignal = null;
        graphDetailNet = null;
        Ui.Screen s = screens.get(currentScreen);
        if (s != null) s.build();
    }

    @Override
    public void onBackPressed() {
        if (graphDetailOverlay != null) { closeGraphDetail(); return; }
        super.onBackPressed();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        return lp;
    }

    /** 3-column key/value grid (detail overlay). */
    private void grid3(LinearLayout f, String[][] pairs) {
        for (int i = 0; i < pairs.length; i += 3) {
            LinearLayout row = Ui.h(this);
            for (int j = i; j < i + 3 && j < pairs.length; j++) {
                LinearLayout cell = Ui.v(this);
                cell.addView(Ui.label(this, pairs[j][0].toUpperCase(),
                        7, Theme.TEXT_MUTE, true));
                cell.addView(Ui.label(this, pairs[j][1], 9, Theme.TEXT, false));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                clp.setMargins(0, 0, Ui.dp(this, 8), Ui.dp(this, 6));
                cell.setLayoutParams(clp);
                row.addView(cell);
            }
            f.addView(row);
        }
    }

    // ======================================================================
    //  state label
    // ======================================================================
    void setState(String text, int color) {
        if (stateLabel == null) return;
        stateLabel.setText(text);
        stateLabel.setTextColor(color);
        stateDot.setTextColor(color);
    }

    // ======================================================================
    //  report generation (called by ReportsScreen / AnalysisScreen / detail)
    // ======================================================================
    void generateReport() {
        if (selected == null) {
            Toast.makeText(this, "Select a network first (Mitandao).",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final Net n = selected;
        new Thread(() -> {
            try {
                Analyzer.Result a = Analyzer.analyze(n);
                final java.io.File pdf = ReportPdf.generate(n, a,
                        logoHeader, logoFooter, files.reportsDir);
                LogStore.log(this, "Report generated: " + pdf.getName());
                firebase.syncReport(pdf, n, a.score);
                SdkStatus.logEvent(this, "report_generated", "score", a.score);
                FbKit.breadcrumb("report generated score=" + a.score);
                ui.post(() -> new ReportPreviewDialog(this, pdf).show());
            } catch (final Exception e) {
                FbKit.nonFatal("report", String.valueOf(e));
                ui.post(() -> Toast.makeText(this,
                        "Could not generate PDF: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }, "wifishield-report").start();
    }

    // ======================================================================
    //  assets
    // ======================================================================
    private byte[] readAsset(String name) {
        try (InputStream in = getAssets().open(name);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } catch (Exception e) {
            LogStore.log(this, "asset missing: " + name, "WARNING");
            return null;
        }
    }

    boolean isLandscape() {
        return getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }
}
