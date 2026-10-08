package com.cybersafetycs.wifishield;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Coloured PDF security report — Java port of report.py's PDF renderer
 * (light/printable palette, brand header with small logo, clickable
 * YouTube channel link, findings + recommendations pages).
 */
public final class ReportPdf {

    private ReportPdf() {}

    // PDF palette (LIGHT theme — matches desktop C_* values)
    static final String C_BG = "#e9edf3", C_PANEL = "#f4f6f9", C_BORDER = "#d5dbe6";
    static final String C_TEXT = "#12161f", C_DIM = "#4a5568", C_MUTE = "#718096";
    static final String C_RED = "#e02b3a", C_GREEN = "#16a34a", C_AMBER = "#d97706";
    static final String C_CYAN = "#0891b2", C_ON_ACC = "#ffffff";

    static final String YOUTUBE_URL = "https://www.youtube.com/@cybersafetycs";
    static final String YOUTUBE_LABEL = "@cybersafetycs";

    static String sevColor(String sev) {
        switch (sev) {
            case "good": return C_GREEN;
            case "warn": return C_AMBER;
            case "danger": return C_RED;
            default: return C_CYAN;
        }
    }

    static String sevName(String sev) {
        switch (sev) {
            case "good": return "GOOD";
            case "warn": return "WARNING";
            case "danger": return "RISK";
            default: return "INFO";
        }
    }

    static String scoreColor(int score) {
        return score >= 80 ? C_GREEN : score >= 60 ? C_AMBER : C_RED;
    }

    /** Generate the full report PDF. logo* may be null when assets missing. */
    public static File generate(Net net, Analyzer.Result a, byte[] logoHeader,
                                byte[] logoFooter, File outDir) throws IOException {
        if (!outDir.exists()) outDir.mkdirs();
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File file = new File(outDir, "wifishield_" + slug(net.displayName()) + "_" + stamp + ".pdf");

        PdfCanvas c1 = new PdfCanvas();
        double nextY = page1(c1, net, a, logoHeader);
        if (nextY > 260) {
            findings(c1, net, a, nextY, logoFooter);
            PdfCanvas.writePdf(file, c1);
        } else {
            PdfCanvas c2 = new PdfCanvas();
            findings(c2, net, a, 60, logoFooter);
            PdfCanvas.writePdf(file, c1, c2);
        }
        return file;
    }

    static String slug(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : (s == null ? "net" : s).toCharArray())
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        String out = sb.toString();
        return out.isEmpty() ? "net" : out.substring(0, Math.min(32, out.length()));
    }

    // ---- page 1 ------------------------------------------------------------
    static double page1(PdfCanvas c, Net net, Analyzer.Result a, byte[] logoHeader) {
        double W = c.w, M = 42;

        // brand header bar
        c.rect(0, 0, W, 74, C_RED);
        double bx = M;
        if (logoHeader != null) {
            c.image(logoHeader, "logo_header.jpg", M, 14, 46, 46);
            bx = M + 58;
        }
        c.text(bx, 34, "CYBERSAFETYCS", 13, "#ffffff", true);
        c.text(bx, 56, "WiFiShield  \u00b7  WiFi Security Report", 17, "#ffffff", true);
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        c.text(W - M, 34, now, 10, "#ffd9dc", false, "right");
        // clickable YouTube channel link (right side of header)
        String yt = "youtube.com/@cybersafetycs";
        c.text(W - M, 56, yt, 10, "#ffffff", true, "right");
        double ytw = PdfCanvas.textWidth(yt, 10, true);
        c.link(W - M - ytw, 44, ytw + 4, 16, YOUTUBE_URL);

        double y = 100;
        String ssid = net.displayName();
        c.text(M, y, ssid, 22, C_TEXT, true);
        y += 20;
        c.text(M, y, "BSSID " + (net.bssid == null || net.bssid.isEmpty() ? "N/A" : net.bssid)
                + "   \u00b7   " + (net.isConnected ? "CONNECTED" : "SCANNED NETWORK"),
                10, C_DIM, false);
        y += 26;

        // score card
        int score = a.score;
        String scol = scoreColor(score);
        double cardH = 104;
        c.rect(M, y, W - 2 * M, cardH, C_PANEL, C_BORDER, 1.0);
        c.text(M + 20, y + 34, String.valueOf(score), 46, scol, true);
        c.text(M + 20, y + 56, "/ 100", 13, C_MUTE, false);
        c.text(M + 20, y + 78, "SECURITY SCORE", 9, C_DIM, true);

        double bx2 = M + 140, bw = W - 2 * M - 170;
        c.rect(bx2, y + 44, bw, 14, C_BG);
        c.rect(bx2, y + 44, bw * Math.max(0, Math.min(100, score)) / 100.0, 14, scol);
        c.text(bx2, y + 32, a.encryptionLabel, 12, C_TEXT, true);
        c.text(bx2, y + 78, "Signal " + net.signalDbm + " dBm (" + a.signalLabel + ")"
                + "   \u00b7   " + net.band() + "   \u00b7   CH " + net.channel,
                10, C_DIM, false);
        y += cardH + 20;

        // info grid
        String[][] cells = {
                {"SSID", ssid},
                {"BSSID", net.bssid == null || net.bssid.isEmpty() ? "N/A" : net.bssid},
                {"SECURITY", a.securityRaw == null || a.securityRaw.isEmpty() ? "OPEN" : a.securityRaw},
                {"SIGNAL", net.signalDbm + " dBm"},
                {"CHANNEL", String.valueOf(net.channel > 0 ? net.channel : "?")},
                {"FREQUENCY", (net.frequency > 0 ? net.frequency : "?") + " MHz"},
                {"BAND", net.band()},
                {"WI-FI", net.wifiCell()},
        };
        int cols = 4, gap = 10, chh = 48;
        double cw = (W - 2 * M - gap * (cols - 1)) / cols;
        for (int i = 0; i < cells.length; i++) {
            double cx = M + (i % cols) * (cw + gap);
            double cy = y + (i / cols) * (chh + gap);
            c.rect(cx, cy, cw, chh, C_PANEL, C_BORDER, 1.0);
            c.text(cx + 10, cy + 18, cells[i][0], 8, C_DIM, true);
            String v = cells[i][1];
            c.text(cx + 10, cy + 37, v.length() > 26 ? v.substring(0, 26) : v, 11, C_TEXT, true);
        }
        y += 2 * (chh + gap) + 14;

        // score breakdown
        y = section(c, M, y, "SCORE BREAKDOWN");
        for (String[] br : a.breakdown) {
            c.rect(M, y - 12, 6, 6, C_CYAN);
            c.text(M + 14, y, br[0] + ":  " + br[1], 10, C_TEXT, false);
            y += 16;
        }
        return y + 8;
    }

    // ---- findings page -----------------------------------------------------
    static void findings(PdfCanvas c, Net net, Analyzer.Result a, double y, byte[] logoFooter) {
        double W = c.w, M = 42;

        y = section(c, M, y, "MATATIZO YALIYOGUNDULIWA  /  FINDINGS");
        for (Analyzer.Finding f : a.findings) {
            String col = sevColor(f.severity);
            String name = sevName(f.severity);
            double chipW = PdfCanvas.textWidth(name, 8, true) + 16;
            c.rect(M, y - 11, chipW, 15, col);
            c.text(M + chipW / 2, y, name, 8, C_ON_ACC, true, "center");
            double tx = M + chipW + 12;
            for (String line : wrap(f.message, (int) ((W - M - tx) / 5.2))) {
                c.text(tx, y, line, 10, C_TEXT, false);
                y += 15;
            }
            y += 7;
        }
        y += 14;

        y = section(c, M, y, "MAPENDEKEZO  /  RECOMMENDATIONS");
        int i = 1;
        for (String rec : a.recommendations) {
            c.text(M, y, i + ".", 10, C_CYAN, true);
            for (String line : wrap(rec, 88)) {
                c.text(M + 18, y, line, 10, C_TEXT, false);
                y += 15;
            }
            y += 5;
            i++;
        }

        // footer
        c.rect(M, c.h - 46, W - 2 * M, 1, C_BORDER);
        c.text(W / 2, c.h - 36,
                "CyberSafetyCS WiFiShield \u2014 automated scan report. "
                + "Signal & security data reflects the probe at scan time.",
                8, C_MUTE, false, "center");
        String yt = "YouTube: https://www.youtube.com/@cybersafetycs";
        double ytw = PdfCanvas.textWidth(yt, 9, true);
        double total = ytw + (logoFooter != null ? 26 : 0);
        double lx = W / 2 - total / 2;
        if (logoFooter != null) c.image(logoFooter, "logo_footer.jpg", lx, c.h - 27, 16, 16);
        double tx = lx + (logoFooter != null ? 24 : 0);
        c.text(tx, c.h - 18, yt, 9, C_RED, true);
        c.link(tx - 2, c.h - 27, ytw + 4, 14, YOUTUBE_URL);
    }

    static double section(PdfCanvas c, double M, double y, String title) {
        c.rect(M, y - 4, 4, 14, C_CYAN);
        c.text(M + 12, y + 7, title, 11, C_CYAN, true);
        return y + 28;
    }

    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        String cur = "";
        for (String wd : String.valueOf(text).split(" ")) {
            String trial = (cur + " " + wd).trim();
            if (trial.length() <= width) {
                cur = trial;
            } else {
                if (!cur.isEmpty()) lines.add(cur);
                cur = wd;
            }
        }
        if (!cur.isEmpty()) lines.add(cur);
        if (lines.isEmpty()) lines.add("");
        return lines;
    }
}
