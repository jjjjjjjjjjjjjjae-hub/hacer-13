package com.hacer13.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

public final class UpdateEngine {
    public interface Listener { void result(String message); }
    private UpdateEngine() {}
    public static final String UPDATE_URL =
            "https://raw.githubusercontent.com/jjjjjjjjjjjjjjae-hub/hacer-13/main/updates/lac/release.json";
    // Ed25519 public verification key (SPKI DER/base64). No private key belongs in the APK.
    private static final String PUBLIC_KEY =
            "MCowBQYDK2VwAyEAiO7EXdgcZOfBjg9l5cB9R5LpyllamtPbdHPMU8IdZwI=";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long INTERVAL_MS = 6L * 60L * 60L * 1000L;

    public static void check(Context context, boolean force, Listener listener) {
        Context app = context.getApplicationContext();
        long last = app.getSharedPreferences("update", 0).getLong("checked_at", 0);
        if (!force && System.currentTimeMillis() - last >= 0
                && System.currentTimeMillis() - last < INTERVAL_MS) {
            MAIN.post(() -> listener.result("Келесі автоматты тексеру кейінірек болады"));
            return;
        }
        EXECUTOR.execute(() -> {
            String message;
            try { message = checkNow(app); }
            catch (Exception e) { message = "Жаңарту қатесі: " + e.getMessage(); }
            final String result = message;
            MAIN.post(() -> listener.result(result));
        });
    }

    private static String checkNow(Context ctx) throws Exception {
        URL url = new URL(UPDATE_URL);
        if (!"https".equals(url.getProtocol()) ||
            !"raw.githubusercontent.com".equals(url.getHost())) throw new Exception("Bad update host");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (connection.getResponseCode() != 200) throw new Exception("HTTP " + connection.getResponseCode());
            byte[] body = bounded(connection.getInputStream(), 65536);
            JSONObject envelope = new JSONObject(new String(body, StandardCharsets.UTF_8));
            if (!"Ed25519".equals(envelope.optString("algorithm"))) throw new Exception("Unsupported signature");
            byte[] payload = Base64.decode(envelope.getString("payload_b64"), Base64.DEFAULT);
            byte[] signed = Base64.decode(envelope.getString("signature_b64"), Base64.DEFAULT);
            if (payload.length > 32768 || signed.length != 64) throw new Exception("Invalid payload size");

            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(
                new X509EncodedKeySpec(Base64.decode(PUBLIC_KEY, Base64.DEFAULT))));
            verifier.update(payload);
            if (!verifier.verify(signed)) throw new Exception("Signature FAILED — update rejected");

            JSONObject config = new JSONObject(new String(payload, StandardCharsets.UTF_8));
            validate(config);
            int incoming = config.getInt("revision");
            int installed = ConfigStore.revision(ctx);
            ctx.getSharedPreferences("update", 0).edit()
                    .putLong("checked_at", System.currentTimeMillis()).apply();
            if (incoming <= installed) return "Жаңарту жоқ · v" + installed;
            ConfigStore.install(ctx, payload);
            return "Қолтаңба расталды · v" + installed + " → v" + incoming;
        } finally { connection.disconnect(); }
    }

    private static void validate(JSONObject c) throws Exception {
        if (c.getInt("schema") != 1 || c.getInt("min_core") > 1 ||
            c.getInt("revision") < 1 || c.getInt("revision") > 1_000_000) {
            throw new Exception("Incompatible configuration");
        }
        String title = c.getString("title");
        if (title.isEmpty() || title.length() > 80) throw new Exception("Invalid title");
        JSONArray features = c.getJSONArray("features");
        if (features.length() < 1 || features.length() > 24) throw new Exception("Invalid feature count");
        for (int i = 0; i < features.length(); i++) {
            JSONObject f = features.getJSONObject(i);
            if (!f.getString("id").matches("[a-z0-9_-]{1,32}") ||
                f.getString("title").length() > 70 ||
                f.getString("description").length() > 180) {
                throw new Exception("Invalid menu item");
            }
        }
        // Only inert presentation fields are accepted. No scripts, DEX, native libraries,
        // filesystem operations, network commands or third-party app modification.
    }

    private static byte[] bounded(InputStream in, int max) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > max) throw new Exception("Download too large");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
}
