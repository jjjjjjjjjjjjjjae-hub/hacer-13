package com.hacer13.core;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

public class OverlayService extends Service {
    private WindowManager manager;
    private View panel;
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (panel != null || !Settings.canDrawOverlays(this)) return START_NOT_STICKY;
        manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackgroundColor(Color.rgb(25, 31, 44));
        TextView title = new TextView(this);
        title.setText("HACER 13  •  MOD MENU");
        title.setTextColor(Color.rgb(64, 228, 190));
        title.setTextSize(17);
        root.addView(title);
        JSONObject config = ConfigStore.current(this);
        JSONArray list = config.optJSONArray("features");
        if (list != null) for (int j = 0; j < Math.min(list.length(), 12); j++) {
            JSONObject f = list.optJSONObject(j);
            if (f == null) continue;
            TextView item = new TextView(this);
            item.setText("◉ " + f.optString("title") + "\n" + f.optString("description"));
            item.setTextColor(Color.WHITE);
            item.setTextSize(13);
            item.setPadding(0, dp(10), 0, dp(6));
            root.addView(item);
        }
        TextView info = new TextView(this);
        info.setText("DEMO — ойын параметрлері өзгермейді");
        info.setTextColor(Color.LTGRAY);
        root.addView(info);
        Button close = new Button(this);
        close.setText("ЖАБУ");
        close.setOnClickListener(v -> stopSelf());
        root.addView(close);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(285), -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.y = dp(100);
        panel = root;
        try { manager.addView(panel, params); }
        catch (Exception e) { panel = null; stopSelf(); }
        return START_NOT_STICKY;
    }
    @Override public void onDestroy() {
        if (manager != null && panel != null) {
            try { manager.removeView(panel); } catch (Exception ignored) {}
        }
        panel = null;
        super.onDestroy();
    }
}
