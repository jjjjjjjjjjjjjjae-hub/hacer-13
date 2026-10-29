package com.hacer13.core;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ConfigStore {
    private ConfigStore() {}
    private static File active(Context c) { return new File(c.getFilesDir(), "active-menu.json"); }
    private static File backup(Context c) { return new File(c.getFilesDir(), "backup-menu.json"); }

    private static byte[] read(InputStream in) throws Exception {
        try (InputStream input = in; java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (output.size() + count > 32768) throw new Exception("Configuration too large");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
    private static byte[] bytes(File f) throws Exception {
        return read(new FileInputStream(f));
    }
    public static synchronized JSONObject current(Context c) {
        try { if (active(c).isFile()) return new JSONObject(new String(bytes(active(c)), StandardCharsets.UTF_8)); }
        catch (Exception ignored) {}
        try { return new JSONObject(new String(read(c.getAssets().open("initial_payload.json")), StandardCharsets.UTF_8)); }
        catch (Exception ignored) { return new JSONObject(); }
    }
    public static synchronized int revision(Context c) {
        return current(c).optInt("revision", 1);
    }
    private static void atomicWrite(File target, byte[] content) throws Exception {
        AtomicFile file = new AtomicFile(target);
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            stream.write(content);
            file.finishWrite(stream);
        } catch (Exception e) {
            if (stream != null) file.failWrite(stream);
            throw e;
        }
    }
    public static synchronized void install(Context c, byte[] payload) throws Exception {
        // Active config remains usable if atomic replacement fails.
        File old = active(c);
        if (old.isFile()) atomicWrite(backup(c), bytes(old));
        else {
            try { atomicWrite(backup(c), read(c.getAssets().open("initial_payload.json"))); }
            catch (Exception ignored) {}
        }
        atomicWrite(old, payload);
    }
    public static synchronized boolean rollback(Context c) {
        try {
            if (!backup(c).isFile()) return false;
            byte[] prior = bytes(backup(c));
            // Backup contains only a previously accepted configuration or the built-in default.
            atomicWrite(active(c), prior);
            return true;
        } catch (Exception e) { return false; }
    }
}
