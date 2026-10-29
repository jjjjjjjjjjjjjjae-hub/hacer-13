package com.hacer13.embedded;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import android.util.Base64;
import javax.net.ssl.HttpsURLConnection;

/** Visible, reversible, in-game UI. Does NOT manipulate Unity/game state. */
public final class HacerHook {
  private static final String URL = "https://raw.githubusercontent.com/jjjjjjjjjjjjjjae-hub/hacer-13/main/updates/lac/release.json";
  private static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAiO7EXdgcZOfBjg9l5cB9R5LpyllamtPbdHPMU8IdZwI=";
  private static final String FALLBACK = "{\"schema\":1,\"revision\":1,\"min_core\":1,\"title\":\"HACER 13 - LAC\",\"features\":[{\"id\":\"demo\",\"title\":\"MOD MENU Preview\",\"description\":\"Тек интерфейс, ойын өзгермейді\"}]}";
  private static boolean attached = false;

  private HacerHook() {}
  public static void attach(Activity activity) {
    if (activity == null) return;
    activity.runOnUiThread(() -> {
      if (attached || activity.isFinishing()) return;
      attached = true;
      FrameLayout root = activity.findViewById(android.R.id.content);
      if (root == null) { attached = false; return; }
      Button button = new Button(activity);
      button.setAllCaps(false);
      button.setText("H13");
      button.setTextColor(Color.WHITE);
      button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff164d51));
      FrameLayout.LayoutParams pos = new FrameLayout.LayoutParams(dp(activity, 68), dp(activity, 50));
      pos.gravity = Gravity.TOP | Gravity.RIGHT;
      pos.topMargin = dp(activity, 45);
      pos.rightMargin = dp(activity, 8);
      root.addView(button, pos);
      button.setOnClickListener(v -> showMenu(activity));
      // Lightweight data-only refresh; never downloads code or runs as a persistent service.
      Context app = activity.getApplicationContext();
      long now = System.currentTimeMillis();
      android.content.SharedPreferences prefs = app.getSharedPreferences("hacer13_update_status", Context.MODE_PRIVATE);
      long previous = prefs.getLong("last_check_ms", 0L);
      if (Build.VERSION.SDK_INT >= 33 && (previous <= 0 || now < previous || now - previous >= 6L * 60 * 60 * 1000)) {
        prefs.edit().putLong("last_check_ms", now).apply();
        new Thread(() -> update(app), "Hacer13AutoCheck").start();
      }
    });
  }
  private static int dp(Context ctx, int x) { return (int) (ctx.getResources().getDisplayMetrics().density * x + 0.5f); }
  private static JSONObject current(Context ctx) {
    try {
      File f = new File(ctx.getFilesDir(), "hacer13-menu.json");
      if (!f.exists()) return new JSONObject(FALLBACK);
      try (FileInputStream in = new FileInputStream(f)) {
        byte[] data = read(in, 32768);
        return new JSONObject(new String(data, StandardCharsets.UTF_8));
      }
    } catch (Exception e) { try { return new JSONObject(FALLBACK); } catch (Exception ignored) { return new JSONObject(); } }
  }
  private static void showMenu(Activity a) {
    LinearLayout panel = new LinearLayout(a);
    panel.setPadding(dp(a, 18), dp(a, 8), dp(a, 18), dp(a, 8));
    panel.setOrientation(LinearLayout.VERTICAL);
    TextView status = new TextView(a);
    status.setText("HACER Core · data v" + current(a).optInt("revision", 1));
    status.setPadding(0, 0, 0, dp(a, 10));
    panel.addView(status);
    JSONArray items = current(a).optJSONArray("features");
    if (items != null) for (int i = 0; i < Math.min(items.length(), 24); i++) {
      JSONObject item = items.optJSONObject(i);
      if (item == null) continue;
      String id = item.optString("id", "unknown");
      CheckBox cb = new CheckBox(a);
      cb.setText(item.optString("title", "Feature") + "\n" + item.optString("description", ""));
      cb.setChecked(a.getPreferences(Context.MODE_PRIVATE).getBoolean("h13_" + id, false));
      cb.setOnCheckedChangeListener((v, checked) -> a.getPreferences(Context.MODE_PRIVATE).edit().putBoolean("h13_" + id, checked).apply());
      panel.addView(cb);
    }
    Button check = new Button(a);
    check.setText("GitHub жаңартуын тексеру");
    panel.addView(check);
    TextView demo = new TextView(a);
    demo.setText("Демо: батырмалар тек мәзірде сақталады, ойын параметрлерін өзгертпейді.");
    panel.addView(demo);
    ScrollView scroll = new ScrollView(a); scroll.addView(panel);
    AlertDialog dialog = new AlertDialog.Builder(a).setTitle("HACER 13 · MOD MENU")
        .setView(scroll).setNegativeButton("Жабу", (d,w) -> {}).create();
    check.setOnClickListener(v -> {
      status.setText("Қолтаңбаны тексеру...");
      new Thread(() -> {
        String message = update(a.getApplicationContext());
        a.runOnUiThread(() -> {
          status.setText(message + " · мәзірді қайта аш");
        });
      }, "HacerSignedUpdate").start();
    });
    dialog.show();
  }
  private static byte[] read(InputStream in, int limit) throws Exception {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream(); byte[] tmp = new byte[4096]; int n;
    while ((n = in.read(tmp)) != -1) {
      if (buffer.size() + n > limit) throw new Exception("Size limit exceeded");
      buffer.write(tmp, 0, n);
    }
    return buffer.toByteArray();
  }
  private static String update(Context ctx) {
    // Android 13 adds Ed25519 to its standard JCA provider.
    if (Build.VERSION.SDK_INT < 33) return "Мәзір жұмыс істейді, бірақ қолтаңбалы жаңарту үшін Android 13+ қажет";
    HttpsURLConnection connection = null;
    try {
      URL url = new URL(URL);
      if (!"https".equals(url.getProtocol()) || !"raw.githubusercontent.com".equals(url.getHost())) return "Сервер қатесі";
      connection = (HttpsURLConnection) url.openConnection();
      connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
      connection.setInstanceFollowRedirects(false);
      if (connection.getResponseCode() != 200) return "HTTP " + connection.getResponseCode();
      JSONObject env = new JSONObject(new String(read(connection.getInputStream(), 65536), StandardCharsets.UTF_8));
      if (!"Ed25519".equals(env.getString("algorithm"))) return "Қате алгоритм";
      byte[] payload = Base64.decode(env.getString("payload_b64"), Base64.DEFAULT);
      byte[] sig = Base64.decode(env.getString("signature_b64"), Base64.DEFAULT);
      if (payload.length > 32768 || sig.length != 64) return "Көлем қатесі";
      Signature verifier = Signature.getInstance("Ed25519");
      verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(
          new X509EncodedKeySpec(Base64.decode(PUBLIC_KEY, Base64.DEFAULT))));
      verifier.update(payload);
      if (!verifier.verify(sig)) return "Жарамсыз қолтаңба!";
      JSONObject data = new JSONObject(new String(payload, StandardCharsets.UTF_8));
      if (data.getInt("schema") != 1 || data.getInt("min_core") > 1) return "Сәйкессіз нұсқа";
      JSONArray items = data.getJSONArray("features");
      if (items.length() == 0 || items.length() > 24 || data.optString("title").length() > 80) return "Қате деректер";
      for (int i = 0; i < items.length(); i++) {
        JSONObject item = items.getJSONObject(i);
        if (!item.getString("id").matches("[a-z0-9_-]{1,32}") ||
            item.getString("title").length() > 70 || item.getString("description").length() > 180) return "Қате элемент";
      }
      int revision = data.getInt("revision");
      if (revision <= current(ctx).optInt("revision",1)) return "Жаңарту жоқ";
      File active = new File(ctx.getFilesDir(), "hacer13-menu.json");
      File tmp = new File(ctx.getFilesDir(), "hacer13-menu.tmp");
      try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(payload); out.getFD().sync(); }
      if (!tmp.renameTo(active)) { tmp.delete(); return "Жазу қатесі"; }
      return "Қолтаңба расталды, data v" + revision;
    } catch (Exception e) { return "Жаңарту қатесі: " + e.getClass().getSimpleName(); }
    finally { if (connection != null) connection.disconnect(); }
  }
}
