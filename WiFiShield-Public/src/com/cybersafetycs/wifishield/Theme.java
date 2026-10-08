package com.cybersafetycs.wifishield;

/** WiFiShield dark cyber theme — palette mirrored from the desktop app. */
public final class Theme {
    private Theme() {}

    public static final int BG          = 0xFF0B0E13;
    public static final int SIDEBAR     = 0xFF11151D;
    public static final int PANEL       = 0xFF151A24;
    public static final int PANEL_ALT   = 0xFF1A202C;
    public static final int CARD        = 0xFF171D28;
    public static final int BORDER      = 0xFF33405C;
    public static final int BORDER_LIT  = 0xFF46577C;

    public static final int TEXT        = 0xFFF2F5FA;
    public static final int TEXT_DIM    = 0xFFA7B1C6;
    public static final int TEXT_MUTE   = 0xFF8791A8;

    public static final int RED         = 0xFFFF3B4A;
    public static final int RED_DIM     = 0xFFA5222D;
    public static final int RED_BG      = 0xFF2A1318;

    public static final int GREEN       = 0xFF35E07F;
    public static final int GREEN_BG    = 0xFF122416;
    public static final int AMBER       = 0xFFFFB43A;
    public static final int AMBER_BG    = 0xFF2B2113;
    public static final int CYAN        = 0xFF3AD4FF;
    public static final int CYAN_BG     = 0xFF0F2329;
    public static final int BLUE        = 0xFF4D92FF;
    public static final int WHITE       = 0xFFFFFFFF;

    /** severity ('good','info','warn','danger') -> color */
    public static int severityColor(String sev) {
        if (sev == null) return TEXT_DIM;
        switch (sev) {
            case "good":  return GREEN;
            case "info":  return CYAN;
            case "warn":  return AMBER;
            case "danger":return RED;
            default:      return TEXT_DIM;
        }
    }

    /** analyzer named color ("green","amber","red","cyan") -> color */
    public static int named(String c) {
        if (c == null) return TEXT_DIM;
        switch (c.toLowerCase()) {
            case "green": return GREEN;
            case "amber": return AMBER;
            case "red":   return RED;
            case "cyan":  return CYAN;
            case "blue":  return BLUE;
            case "white": return WHITE;
            default:      return TEXT_DIM;
        }
    }

    public static int scoreColor(int score) {
        return score >= 80 ? GREEN : score >= 60 ? AMBER : RED;
    }
}
