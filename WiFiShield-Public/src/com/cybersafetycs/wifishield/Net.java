package com.cybersafetycs.wifishield;

import java.util.ArrayList;
import java.util.List;

/** One scanned WiFi network (mirrors the desktop WiFiNetwork dataclass). */
public class Net {
    public String ssid = "";
    public String bssid = "";
    public int channel = 0;
    public int frequency = 0;
    public int signalDbm = -100;
    public String security = "OPEN";     // token: OPEN/WEP/WPA/WPA2/WPA3/WPA2/802.1X
    public String caps = "";             // raw Android capabilities string
    public String standard = "";         // real: ScanResult.getWifiStandard() (API 30+)
    public String width = "";            // real: ScanResult.channelWidth (20/40/80/160/320 MHz)
    public boolean isConnected = false;
    public boolean hidden = false;
    public String wps = null;            // "Enabled"/null (not advertised in caps)
    public String pmf = null;            // "Required"/"Optional"/null (not advertised)
    public long lastSeen = 0;
    public List<Integer> history = new ArrayList<>();
    /** Real epoch-ms timestamp for every history sample (never fabricated). */
    public List<Long> historyWhen = new ArrayList<>();

    /** Real epoch-seconds for history sample i — never a fabricated spacing. */
    public double sampleTimeSec(int i) {
        if (historyWhen.isEmpty()) return System.currentTimeMillis() / 1000.0;
        int j = Math.max(0, Math.min(i, historyWhen.size() - 1));
        return historyWhen.get(j) / 1000.0;
    }

    /** Real Wi-Fi + bandwidth cell, e.g. "Wi-Fi 6 (802.11ax) · 80 MHz". */
    public String wifiCell() {
        String s = standard == null ? "" : standard;
        String w = width == null ? "" : width;
        if (s.isEmpty() && w.isEmpty()) return "not reported";
        if (s.isEmpty()) return w;
        if (w.isEmpty()) return s;
        return s + " · " + w;
    }

    public boolean isOpen() {
        return security == null || security.isEmpty() || security.equals("OPEN");
    }

    public String band() {
        return bandLabel(frequency);
    }

    public static String bandLabel(int freqMhz) {
        if (freqMhz <= 0) return "?";
        return freqMhz < 5000 ? "2.4 GHz" : "5 GHz";
    }

    public static int channelToFreq(int ch) {
        if (ch == 14) return 2484;
        if (ch >= 1 && ch <= 13) return 2412 + (ch - 1) * 5;
        if (ch >= 34 && ch <= 165) return 5000 + ch * 5;
        return 0;
    }

    /** freq (MHz) -> channel number (Android gives frequency, not channel). */
    public static int freqToChannel(int freq) {
        if (freq == 2484) return 14;
        if (freq >= 2412 && freq <= 2472) return (freq - 2407) / 5;
        if (freq >= 5000 && freq <= 5885) return (freq - 5000) / 5;
        return 0;
    }

    public String displayName() {
        return (ssid == null || ssid.isEmpty()) ? "<hidden>" : ssid;
    }
}
