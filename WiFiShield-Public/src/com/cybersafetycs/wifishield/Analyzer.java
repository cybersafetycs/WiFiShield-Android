package com.cybersafetycs.wifishield;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * WiFiShield security analysis engine — public API unchanged.
 *
 * The actual scoring logic (weights, findings, channel congestion) now
 * lives in the native engine (src/cpp/analyze.c → libwifishield.so) so
 * the algorithm ships as machine code instead of readable bytecode.
 * This class only marshals Net objects into the native call and parses
 * the JSON answer back into the same Result / ChannelResult shapes the
 * UI has always used.
 */
public final class Analyzer {

    private Analyzer() {}

    // ---- finding ---------------------------------------------------------
    public static final class Finding {
        public final String severity;   // good | info | warn | danger
        public final String message;
        public final String detail;

        public Finding(String severity, String message, String detail) {
            this.severity = severity;
            this.message = message;
            this.detail = detail == null ? "" : detail;
        }

        public int weight() {
            switch (severity) {
                case "good":   return 0;
                case "info":   return -5;
                case "warn":   return -15;
                case "danger": return -30;
                default:       return -5;
            }
        }

        public String kind() {
            switch (severity) {
                case "good":   return "Credit";
                case "info":   return "Note";
                case "warn":   return "Deduction";
                default:       return "Major deduction";
            }
        }
    }

    public static final class Result {
        public int score;
        public String scoreColor;          // green|amber|red
        public List<Finding> findings = new ArrayList<>();
        public List<String> recommendations = new ArrayList<>();
        public List<String[]> breakdown = new ArrayList<>();   // {kind, msg}
        public String encryptionLabel;
        public String encryptionColor;
        public String signalLabel;
        public String signalColor;
        public String securityRaw;
        public String band;
    }

    public static final class BandStats {
        public int count;
        public java.util.Map<Integer, Integer> counters = new java.util.LinkedHashMap<>();
        public java.util.Map<Integer, Integer> overlaps = new java.util.LinkedHashMap<>();
        public List<Integer> offChannel = new ArrayList<>();
        public String congestion;      // Low | Moderate | High
        public String congestionColor;
    }

    public static final class ChannelResult {
        public BandStats g24 = new BandStats();
        public BandStats g5 = new BandStats();
    }

    // ---- helpers kept in Java (display-only, trivial) ---------------------
    static final int[] DBM_TH = {-30, -50, -60, -70, -80};
    static final String[] DBM_LABEL = {"Excellent", "Good", "Fair", "Weak", "Very Weak"};
    static final String[] DBM_COLOR = {"green", "green", "amber", "amber", "red"};

    public static String[] signalQuality(int dbm) {
        for (int i = 0; i < DBM_TH.length; i++) {
            if (dbm >= DBM_TH[i]) return new String[]{DBM_LABEL[i], DBM_COLOR[i]};
        }
        return new String[]{"Very Weak", "red"};
    }

    public static String scoreColorName(int score) {
        return score >= 80 ? "green" : score >= 60 ? "amber" : "red";
    }

    // ---- analyze (native) --------------------------------------------------
    public static Result analyze(Net net) {
        Result r = new Result();
        try {
            if (!NativeAnalyzer.isAvailable()) return unavailable(r);
            String json = NativeAnalyzer.analyze(
                    net.security, net.signalDbm, net.frequency,
                    net.wps, net.pmf, net.hidden);
            JSONObject o = new JSONObject(json);
            r.score = o.getInt("score");
            r.scoreColor = o.getString("scoreColor");
            r.encryptionLabel = o.getString("encryptionLabel");
            r.encryptionColor = o.getString("encryptionColor");
            r.signalLabel = o.getString("signalLabel");
            r.signalColor = o.getString("signalColor");
            r.securityRaw = o.getString("securityRaw");
            r.band = o.getString("band");
            JSONArray fa = o.getJSONArray("findings");
            for (int i = 0; i < fa.length(); i++) {
                JSONObject f = fa.getJSONObject(i);
                r.findings.add(new Finding(
                        f.getString("severity"),
                        f.getString("message"),
                        f.getString("detail")));
            }
            JSONArray ra = o.getJSONArray("recommendations");
            for (int i = 0; i < ra.length(); i++) r.recommendations.add(ra.getString(i));
            for (Finding x : r.findings) r.breakdown.add(new String[]{x.kind(), x.message});
            return r;
        } catch (Throwable t) {
            return unavailable(r);
        }
    }

    /** Non-leaking stub: engine missing/crashed — never embeds the rules. */
    private static Result unavailable(Result r) {
        r.score = 0;
        r.scoreColor = "red";
        r.encryptionLabel = "";
        r.encryptionColor = "red";
        r.signalLabel = "";
        r.signalColor = "red";
        r.securityRaw = "";
        r.band = "";
        r.findings.add(new Finding("info", "Analysis engine unavailable", ""));
        return r;
    }

    // ---- channel analysis (native) ----------------------------------------
    public static ChannelResult channelAnalysis(List<Net> nets) {
        ChannelResult res = new ChannelResult();
        try {
            if (!NativeAnalyzer.isAvailable()) return unavailableChannels(res);
            int n = nets.size();
            int[] ch = new int[n];
            int[] fr = new int[n];
            for (int i = 0; i < n; i++) {
                ch[i] = nets.get(i).channel;
                fr[i] = nets.get(i).frequency;
            }
            String json = NativeAnalyzer.channelAnalysis(ch, fr);
            JSONObject o = new JSONObject(json);
            fillBand(o.getJSONObject("g24"), res.g24);
            fillBand(o.getJSONObject("g5"), res.g5);
            return res;
        } catch (Throwable t) {
            return unavailableChannels(res);
        }
    }

    private static ChannelResult unavailableChannels(ChannelResult res) {
        res.g24.count = 0; res.g24.congestion = "Low"; res.g24.congestionColor = "red";
        res.g5.count = 0;  res.g5.congestion = "Low";  res.g5.congestionColor = "red";
        return res;
    }

    private static void fillBand(JSONObject o, BandStats b) throws Exception {
        b.count = o.getInt("count");
        b.congestion = o.getString("congestion");
        b.congestionColor = o.getString("congestionColor");
        JSONObject co = o.getJSONObject("counters");
        java.util.Iterator<String> ki = co.keys();
        while (ki.hasNext()) {
            String k = ki.next();
            b.counters.put(Integer.parseInt(k), co.getInt(k));
        }
        JSONObject ov = o.getJSONObject("overlaps");
        ki = ov.keys();
        while (ki.hasNext()) {
            String k = ki.next();
            b.overlaps.put(Integer.parseInt(k), ov.getInt(k));
        }
        JSONArray off = o.getJSONArray("offChannel");
        for (int i = 0; i < off.length(); i++) b.offChannel.add(off.getInt(i));
    }
}
