package com.cybersafetycs.wifishield;

/**
 * JNI bridge to the native analysis engine (libwifishield.so).
 * The real scoring logic lives in C/C++ (see src/cpp/analyze.c);
 * this class only marshals data across the boundary.
 */
public final class NativeAnalyzer {

    private static boolean available = false;

    static {
        try {
            System.loadLibrary("wifishield");
            available = true;
        } catch (Throwable t) {
            available = false;
        }
    }

    private NativeAnalyzer() { }

    public static boolean isAvailable() {
        return available;
    }

    /**
     * @param security raw security token from the scan (e.g. "WPA2-PSK", "OPEN")
     * @param signal   RSSI in dBm (real scanner value)
     * @param frequency center frequency in MHz
     * @param wps      "Enabled" / "Disabled" / null
     * @param pmf      "Required" / "Optional" / null
     * @param hidden   hidden-SSID flag
     * @return analysis result as a JSON string
     */
    public static native String analyze(String security, int signal, int frequency,
                                        String wps, String pmf, boolean hidden);

    /**
     * @param channels   channel numbers (same order as frequencies)
     * @param frequencies center frequencies in MHz
     * @return congestion report as a JSON string
     */
    public static native String channelAnalysis(int[] channels, int[] frequencies);
}
