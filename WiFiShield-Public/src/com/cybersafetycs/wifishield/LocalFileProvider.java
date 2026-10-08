package com.cybersafetycs.wifishield;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Minimal ContentProvider serving app files (reports/logs) as content:// URIs
 * so generated PDFs can be opened/shared with other apps without androidx.
 *
 * content://com.cybersafetycs.wifishield.fileprovider/reports/<name>.pdf
 */
public class LocalFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.cybersafetycs.wifishield.fileprovider";

    public static Uri uriFor(File file, ContextMarker ctx) {
        String root = ctx.filesDir().getAbsolutePath();
        String path = file.getAbsolutePath();
        String sub = path.startsWith(root) ? path.substring(root.length()) : file.getName();
        if (sub.startsWith("/")) sub = sub.substring(1);
        return Uri.parse("content://" + AUTHORITY + "/" + sub);
    }

    /** Tiny indirection so the static helper works in unit tests too. */
    public interface ContextMarker { File filesDir(); }

    private File resolve(Uri uri) {
        if (getContext() == null) return null;
        String path = uri.getPath();
        if (path == null || path.isEmpty()) return null;
        File base = getContext().getFilesDir();
        File f = new File(base, path.startsWith("/") ? path.substring(1) : path);
        try {
            String canonicalBase = base.getCanonicalPath();
            String canonicalFile = f.getCanonicalPath();
            if (!canonicalFile.startsWith(canonicalBase)) return null;   // traversal guard
        } catch (Exception e) {
            return null;
        }
        return f;
    }

    @Override
    public boolean onCreate() { return true; }

    @Override
    public String getType(Uri uri) {
        String p = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
        if (p.endsWith(".pdf")) return "application/pdf";
        if (p.endsWith(".txt") || p.endsWith(".log")) return "text/plain";
        return "application/octet-stream";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        if (f == null || !f.exists()) throw new FileNotFoundException(uri.toString());
        int m = ParcelFileDescriptor.parseMode(mode);
        return ParcelFileDescriptor.open(f, m);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File f = resolve(uri);
        if (f == null) return null;
        MatrixCursor cursor = new MatrixCursor(projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection);
        Object[] row = new Object[cursor.getColumnCount()];
        for (int i = 0; i < row.length; i++) {
            String col = cursor.getColumnName(i);
            if (OpenableColumns.DISPLAY_NAME.equals(col)) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(col)) row[i] = f.length();
            else row[i] = null;
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) { return 0; }
}
