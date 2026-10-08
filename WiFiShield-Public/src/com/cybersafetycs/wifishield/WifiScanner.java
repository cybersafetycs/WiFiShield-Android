package com.cybersafetycs.wifishield;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real WiFi scanning via the platform WifiManager — the Android equivalent
 * of the desktop nmcli/netsh backends. Returns REAL results only; when
 * permission/location is missing the caller gets an empty list + error text
 * (never fake networks).
 */
public class WifiScanner {

    /** Marker error when Android blocks scans for missing Location access
     *  (same story as Windows 11 Location permission — handled in UI). */
    public static final String LOCATION_REQUIRED =
            "LOCATION_REQUIRED: WiFi scan needs Location permission + Location ON";

    public interface Listener {
        void onScanResults(List<Net> nets, String error);
    }

    private final Context ctx;
    private final WifiManager wifi;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered = false;
    private Listener listener;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            // EXTRA_RESULTS_UPDATED=false means the system handed us the
            // OLD cached list (e.g. your router was renamed) — never trust
            // it as final: fire a genuinely fresh scan (bounded retries).
            boolean updated = intent == null || intent.getBooleanExtra(
                    WifiManager.EXTRA_RESULTS_UPDATED, true);
            if (updated) {
                freshRetries = 0;
            } else if (freshRetries < 5 && hasScanPermission()) {
                freshRetries++;
                main.postDelayed(() -> {
                    try {
                        if (hasScanPermission()) wifi.startScan();
                    } catch (SecurityException ignored) {}
                }, 4000);
            }
            deliver();
        }
    };
    private int freshRetries = 0;

    public WifiScanner(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.wifi = (WifiManager) ctx.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
    }

    public void setListener(Listener l) { this.listener = l; }

    // ---- permissions / location gate ---------------------------------------
    public boolean hasScanPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            boolean nearby = ctx.checkSelfPermission(
                    Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
            boolean loc = ctx.checkSelfPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            // NEARBY_WIFI_DEVICES (neverForLocation) OR fine location is enough
            return nearby || loc;
        }
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** True when location permission is missing OR device location is OFF —
     *  Android refuses WiFi scan results in that case (mirrors Win11 gate). */
    public boolean locationBlocked() {
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return true;
        android.location.LocationManager lm = (android.location.LocationManager)
                ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        boolean gps = false, net = false;
        try {
            gps = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
            net = lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) {}
        return !gps && !net;
    }

    // ---- scanning ------------------------------------------------------------
    /** Fire a fresh scan (Android throttles these — ~4 per 2 min). */
    @SuppressLint("MissingPermission")
    public boolean startScan() {
        if (!hasScanPermission()) return false;
        try {
            return wifi.startScan();
        } catch (SecurityException e) {
            return false;
        }
    }

    /** Read current (cached) scan results immediately — used by the live
     *  sampler every 2 s, same cadence as the desktop quick re-measure. */
    @SuppressLint("MissingPermission")
    public List<Net> readResults(String[] errorOut) {
        if (locationBlocked()) {
            errorOut[0] = LOCATION_REQUIRED;
            return new ArrayList<>();
        }
        List<ScanResult> sr;
        try {
            sr = wifi.getScanResults();
        } catch (SecurityException e) {
            errorOut[0] = "SecurityException: " + e.getMessage();
            return new ArrayList<>();
        }
        return convert(sr);
    }

    public void register() {
        if (receiverRegistered) return;
        IntentFilter f = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            ctx.registerReceiver(receiver, f);
        }
        receiverRegistered = true;
    }

    public void unregister() {
        if (!receiverRegistered) return;
        try { ctx.unregisterReceiver(receiver); } catch (Exception ignored) {}
        receiverRegistered = false;
    }

    /** Broadcast scan results to the listener (main thread). */
    public void deliver() {
        final String[] err = {null};
        final List<Net> nets = readResults(err);
        final Listener l = listener;
        if (l == null) return;
        main.post(() -> l.onScanResults(nets, err[0]));
    }

    // ---- parsing -------------------------------------------------------------
    /** Real "connected to THIS network" check — cached WifiInfo on Android
     *  often keeps the previous BSSID after you disconnect, so require an
     *  actually completed link (networkId + supplicant state), not just BSSID. */
    @SuppressLint("MissingPermission")
    String connectedBssid() {
        try {
            if (wifi.getWifiState() != WifiManager.WIFI_STATE_ENABLED) return "";
            WifiInfo info = wifi.getConnectionInfo();
            if (info == null) return "";
            if (info.getNetworkId() == -1) return "";
            if (info.getSupplicantState() != android.net.wifi.SupplicantState.COMPLETED)
                return "";
            String b = info.getBSSID();
            if (b == null || b.equals("02:00:00:00:00:00")
                    || b.equals("<none>")) return "";
            return b;
        } catch (Exception e) {
            return "";
        }
    }

    List<Net> convert(List<ScanResult> results) {
        String myBssid = connectedBssid();
        Map<String, Net> byBssid = new LinkedHashMap<>();
        for (ScanResult sr : results) {
            Net n = new Net();
            n.ssid = sr.SSID == null ? "" : sr.SSID;
            n.bssid = sr.BSSID == null ? "" : sr.BSSID;
            n.frequency = sr.frequency;
            n.channel = Net.freqToChannel(sr.frequency);
            n.signalDbm = sr.level;
            n.caps = sr.capabilities == null ? "" : sr.capabilities;
            n.security = parseSecurity(n.caps);
            n.wps = n.caps.contains("WPS") ? "Enabled" : null;
            n.pmf = n.caps.contains("MFPR") ? "Required"
                    : n.caps.contains("MFPC") ? "Optional" : null;
            n.hidden = n.ssid.isEmpty();
            n.isConnected = !myBssid.isEmpty() && myBssid.equalsIgnoreCase(n.bssid);
            // real radio data (Android >= 30 reports the Wi-Fi generation)
            if (Build.VERSION.SDK_INT >= 30) n.standard = standardLabel(sr.getWifiStandard());
            n.width = widthLabel(sr.channelWidth);
            n.lastSeen = System.currentTimeMillis() / 1000L;
            byBssid.put(n.bssid.toLowerCase(), n);
        }
        return new ArrayList<>(byBssid.values());
    }

    /** Real 802.11 standard from ScanResult.getWifiStandard() (API 30+). */
    static String standardLabel(int s) {
        switch (s) {
            case ScanResult.WIFI_STANDARD_11BE: return "Wi-Fi 7 (802.11be)";
            case ScanResult.WIFI_STANDARD_11AX: return "Wi-Fi 6 (802.11ax)";
            case ScanResult.WIFI_STANDARD_11AC: return "Wi-Fi 5 (802.11ac)";
            case ScanResult.WIFI_STANDARD_11N:  return "Wi-Fi 4 (802.11n)";
            case ScanResult.WIFI_STANDARD_11AD: return "802.11ad";
            case ScanResult.WIFI_STANDARD_LEGACY: return "802.11a/b/g";
            default: return "";
        }
    }

    /** Real channel width from ScanResult.channelWidth. */
    static String widthLabel(int w) {
        switch (w) {
            case ScanResult.CHANNEL_WIDTH_20MHZ: return "20 MHz";
            case ScanResult.CHANNEL_WIDTH_40MHZ: return "40 MHz";
            case ScanResult.CHANNEL_WIDTH_80MHZ: return "80 MHz";
            case ScanResult.CHANNEL_WIDTH_160MHZ: return "160 MHz";
            case ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ: return "80+80 MHz";
            case ScanResult.CHANNEL_WIDTH_320MHZ: return "320 MHz";
            default: return "";
        }
    }

    /** Capabilities string -> security token understood by the Analyzer. */
    static String parseSecurity(String caps) {
        String c = caps == null ? "" : caps.toUpperCase();
        if (c.contains("SAE") || c.contains("WPA3")) return "WPA3";
        if (c.contains("EAP")) return "802.1X";
        if (c.contains("RSN") || c.contains("WPA2")) return "WPA2";
        if (c.contains("WPA")) return "WPA";
        if (c.contains("WEP")) return "WEP";
        return "OPEN";
    }
}
