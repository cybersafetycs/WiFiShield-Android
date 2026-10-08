package com.cybersafetycs.wifishield;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal colored-PDF writer — Java port of the desktop pdfgen.py
 * (base-14 Helvetica, RGB rects/text, JPEG XObjects, clickable URI links).
 * Pure Java: no Android imports (testable on the desktop).
 */
public final class PdfCanvas {

    public static final double PAGE_W = 595.28, PAGE_H = 841.89;   // A4

    final double w, h;
    final List<String> ops = new ArrayList<>();
    final List<Object[]> links = new ArrayList<>();        // {x,y,w,h,url}
    final List<String[]> imageRefs = new ArrayList<>();    // {name, key}
    final Map<String, byte[]> imageData = new LinkedHashMap<>();
    final Map<String, int[]> imageSizes = new LinkedHashMap<>();

    public PdfCanvas() { this(PAGE_W, PAGE_H); }

    public PdfCanvas(double width, double height) {
        this.w = width;
        this.h = height;
    }

    // ---- transliteration / escaping (ported) ------------------------------
    static String translit(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\u2014': case '\u2013': out.append('-'); break;
                case '\u2018': case '\u2019': out.append('\''); break;
                case '\u201c': case '\u201d': out.append('"'); break;
                case '\u2026': out.append("..."); break;
                case '\u2022': out.append('-'); break;
                case '\u00a0': out.append(' '); break;
                case '\u2192': out.append("->"); break;
                case '\u2190': out.append("<-"); break;
                case '\u00d7': out.append('x'); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }

    static String esc(String s) {
        StringBuilder out = new StringBuilder();
        for (char ch : translit(String.valueOf(s)).toCharArray()) {
            if (ch == '\\' || ch == '(' || ch == ')') out.append('\\').append(ch);
            else if (ch > 126) out.append(ch <= 255 ? ch : '?');
            else out.append(ch);
        }
        return out.toString();
    }

    static double[] hexRgb(String c) {
        String s = c == null || c.isEmpty() ? "000000" : c.replaceFirst("^#", "");
        if (s.length() == 3) s = "" + s.charAt(0) + s.charAt(0)
                + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        try {
            return new double[]{
                    Integer.parseInt(s.substring(0, 2), 16) / 255.0,
                    Integer.parseInt(s.substring(2, 4), 16) / 255.0,
                    Integer.parseInt(s.substring(4, 6), 16) / 255.0};
        } catch (Exception e) {
            return new double[]{0, 0, 0};
        }
    }

    // ---- image placement --------------------------------------------------
    /** Place a JPEG from raw bytes. x,y = top-left corner (points). */
    public void image(byte[] jpeg, String key, double x, double y, double wd, double ht) {
        String name = null;
        for (String[] ir : imageRefs) if (ir[1].equals(key)) name = ir[0];
        if (name == null) {
            name = "Im" + (imageRefs.size() + 1);
            imageRefs.add(new String[]{name, key});
            imageData.put(key, jpeg);
            imageSizes.put(key, jpegSize(jpeg));
        }
        double py = h - y - ht;
        ops.add(String.format(java.util.Locale.US,
                "q %.2f 0 0 %.2f %.2f %.2f cm /%s Do Q", wd, ht, x, py, name));
    }

    public void link(double x, double y, double wd, double ht, String url) {
        links.add(new Object[]{x, y, wd, ht, url});
    }

    public void rect(double x, double y, double wd, double ht,
                     String fill, String stroke, double lineWidth) {
        double py = h - y - ht;
        if (fill != null) {
            double[] c = hexRgb(fill);
            ops.add(String.format(java.util.Locale.US, "%.4f %.4f %.4f rg", c[0], c[1], c[2]));
        }
        if (stroke != null) {
            double[] c = hexRgb(stroke);
            ops.add(String.format(java.util.Locale.US, "%.4f %.4f %.4f RG %.2f w",
                    c[0], c[1], c[2], lineWidth));
        }
        String op = (fill != null && stroke != null) ? "B" : fill != null ? "f" : "S";
        ops.add(String.format(java.util.Locale.US, "%.2f %.2f %.2f %.2f re %s",
                x, py, wd, ht, op));
    }

    public void rect(double x, double y, double wd, double ht, String fill) {
        rect(x, y, wd, ht, fill, null, 1.0);
    }

    /** Draw one line of text. y = baseline (screen space). */
    public void text(double x, double y, String s, double size, String color,
                     boolean bold, String align) {
        if (s == null) return;
        double py = h - y;
        double[] c = hexRgb(color);
        String font = bold ? "/F2" : "/F1";
        if (align != null && !align.equals("left")) {
            double tw = textWidth(s, size, bold);
            if (align.equals("center")) x -= tw / 2.0;
            else if (align.equals("right")) x -= tw;
        }
        ops.add(String.format(java.util.Locale.US,
                "BT %.4f %.4f %.4f rg %s %.2f Tf 1 0 0 1 %.2f %.2f Tm (%s) Tj ET",
                c[0], c[1], c[2], font, size, x, py, esc(s)));
    }

    public void text(double x, double y, String s, double size, String color, boolean bold) {
        text(x, y, s, size, color, bold, "left");
    }

    public static double textWidth(String s, double size, boolean bold) {
        double factor = bold ? 0.55 : 0.52;
        return String.valueOf(s).length() * size * factor;
    }

    byte[] stream() {
        return String.join("\n", ops).getBytes(StandardCharsets.ISO_8859_1);
    }

    // ---- JPEG SOF scan ----------------------------------------------------
    public static int[] jpegSize(byte[] data) {
        if (data.length < 4 || (data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8)
            throw new IllegalArgumentException("not a JPEG");
        int i = 2;
        while (i < data.length - 9) {
            if ((data[i] & 0xFF) != 0xFF) { i++; continue; }
            int marker = data[i + 1] & 0xFF;
            if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2 || marker == 0xC3
                    || marker == 0xC5 || marker == 0xC6 || marker == 0xC7
                    || marker == 0xC9 || marker == 0xCA || marker == 0xCB
                    || marker == 0xCD || marker == 0xCE || marker == 0xCF) {
                int hh = ((data[i + 5] & 0xFF) << 8) | (data[i + 6] & 0xFF);
                int ww = ((data[i + 7] & 0xFF) << 8) | (data[i + 8] & 0xFF);
                return new int[]{ww, hh};
            }
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                i += 2;
                continue;
            }
            int seglen = ((data[i + 2] & 0xFF) << 8) | (data[i + 3] & 0xFF);
            i += 2 + seglen;
        }
        throw new IllegalArgumentException("JPEG size not found");
    }

    // ---- multi-page writer -------------------------------------------------
    public static void writePdf(File file, PdfCanvas... canvases) throws IOException {
        List<byte[]> objects = new ArrayList<>();

        class Ref { final int id; Ref(int id) { this.id = id; } }

        // 1 catalog, 2 pages (placeholders)
        objects.add(new byte[0]);
        objects.add(new byte[0]);
        final int catalogId = 1, pagesId = 2;

        objects.add(("<</Type/Font/Subtype/Type1/BaseFont/Helvetica"
                + " /Encoding/WinAnsiEncoding>>").getBytes(StandardCharsets.ISO_8859_1));
        objects.add(("<</Type/Font/Subtype/Type1/BaseFont/Helvetica-Bold"
                + " /Encoding/WinAnsiEncoding>>").getBytes(StandardCharsets.ISO_8859_1));
        final int fontReg = 3, fontBold = 4;

        Map<String, Integer> imgIds = new LinkedHashMap<>();

        List<Integer> pageIds = new ArrayList<>();
        for (PdfCanvas c : canvases) {
            byte[] data = c.stream();
            byte[] contentHead = ("<< /Length " + data.length + " >>\nstream\n")
                    .getBytes(StandardCharsets.ISO_8859_1);
            byte[] contentTail = "\nendstream".getBytes(StandardCharsets.ISO_8859_1);
            byte[] content = concat(contentHead, data, contentTail);
            objects.add(content);
            int contentId = objects.size();

            StringBuilder res = new StringBuilder();
            res.append("/Font << /F1 ").append(fontReg).append(" 0 R /F2 ")
               .append(fontBold).append(" 0 R >>");
            if (!c.imageRefs.isEmpty()) {
                StringBuilder xo = new StringBuilder("/XObject << ");
                for (String[] ir : c.imageRefs) {
                    String key = ir[1];
                    Integer iid = imgIds.get(key);
                    if (iid == null) {
                        byte[] jpeg = c.imageData.get(key);
                        int[] sz = c.imageSizes.get(key);
                        byte[] head = String.format(java.util.Locale.US,
                                "<< /Type /XObject /Subtype /Image /Width %d /Height %d "
                                + "/ColorSpace /DeviceRGB /BitsPerComponent 8 "
                                + "/Filter /DCTDecode /Length %d >>\nstream\n",
                                sz[0], sz[1], jpeg.length)
                                .getBytes(StandardCharsets.ISO_8859_1);
                        byte[] body = concat(head, jpeg,
                                "\nendstream".getBytes(StandardCharsets.ISO_8859_1));
                        objects.add(body);
                        iid = objects.size();
                        imgIds.put(key, iid);
                    }
                    xo.append('/').append(ir[0]).append(' ').append(iid).append(" 0 R ");
                }
                xo.append(">>");
                res.append(' ').append(xo);
            }

            StringBuilder annots = new StringBuilder();
            if (!c.links.isEmpty()) {
                annots.append(" /Annots [");
                boolean first = true;
                for (Object[] L : c.links) {
                    double x = (Double) L[0], y = (Double) L[1];
                    double lw = (Double) L[2], lh = (Double) L[3];
                    String url = (String) L[4];
                    double x1 = x, y1 = c.h - (y + lh);
                    double x2 = x + lw, y2 = c.h - y;
                    String u = url.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
                    if (!first) annots.append(' ');
                    first = false;
                    annots.append(String.format(java.util.Locale.US,
                            "<< /Type /Annot /Subtype /Link /Rect [%.2f %.2f %.2f %.2f] "
                            + "/Border [0 0 0] /A << /S /URI /URI (%s) >> >>",
                            x1, y1, x2, y2, ascii(u)));
                }
                annots.append(']');
            }

            byte[] pageObj = String.format(java.util.Locale.US,
                    "<< /Type /Page /Parent %d 0 R /MediaBox [0 0 %.2f %.2f] "
                    + "/Resources << %s >> /Contents %d 0 R%s >>",
                    pagesId, c.w, c.h, res, contentId, annots)
                    .getBytes(StandardCharsets.ISO_8859_1);
            objects.add(pageObj);
            pageIds.add(objects.size());
        }

        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageIds.size(); i++) {
            if (i > 0) kids.append(' ');
            kids.append(pageIds.get(i)).append(" 0 R");
        }
        objects.set(catalogId - 1,
                ("<< /Type /Catalog /Pages " + pagesId + " 0 R >>")
                        .getBytes(StandardCharsets.ISO_8859_1));
        objects.set(pagesId - 1,
                ("<< /Type /Pages /Count " + pageIds.size() + " /Kids [" + kids + "] >>")
                        .getBytes(StandardCharsets.ISO_8859_1));

        // serialise with xref
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] head = {'%', 'P', 'D', 'F', '-', '1', '.', '4', '\n',
                       '%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n'};
        out.write(head, 0, head.length);
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            byte[] hdr = ((i + 1) + " 0 obj\n").getBytes(StandardCharsets.ISO_8859_1);
            out.write(hdr, 0, hdr.length);
            out.write(objects.get(i), 0, objects.get(i).length);
            byte[] ftr = "\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);
            out.write(ftr, 0, ftr.length);
        }
        int xrefPos = out.size();
        byte[] xref = ("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n")
                .getBytes(StandardCharsets.ISO_8859_1);
        out.write(xref, 0, xref.length);
        for (int i = 1; i < offsets.size(); i++) {
            byte[] line = String.format(java.util.Locale.US, "%010d 00000 n \n", offsets.get(i))
                    .getBytes(StandardCharsets.ISO_8859_1);
            out.write(line, 0, line.length);
        }
        byte[] trailer = ("trailer\n<< /Size " + (objects.size() + 1)
                + " /Root " + catalogId + " 0 R >>\nstartxref\n" + xrefPos + "\n%%EOF\n")
                .getBytes(StandardCharsets.ISO_8859_1);
        out.write(trailer, 0, trailer.length);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            out.writeTo(fos);
        }
    }

    static String ascii(String s) {
        return s.replaceAll("[^\\x20-\\x7E]", "?");
    }

    static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len];
        int o = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }
}
