package com.hacer13.core;

import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private LinearLayout page;
    private TextView status, revision, catalog;
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(11, 14, 23));
        page = new LinearLayout(this);
        page.setPadding(dp(24), dp(38), dp(24), dp(40));
        page.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(page);
        setContentView(scroll);

        label("HACER 13", 32, Color.rgb(64, 228, 190), true);
        label("CORE 1.0   •   LAC MODULE", 14, Color.LTGRAY, true);
        label("Қауіпсіз модульдік басқару және GitHub жаңартулары", 16, Color.WHITE, false);
        revision = label("", 17, Color.rgb(64, 228, 190), true);
        status = label("Жаңартуды тексеруге дайын", 14, Color.LTGRAY, false);
        button("ЖАҢАРТУДЫ ТЕКСЕРУ", () -> check(true));
        button("ФОНДЫҚ АВТО ЖАҢАРТУ: ҚОСУ / ӨШІРУ", this::toggleScheduledUpdates);
        button("ҚАЛҚЫМАЛЫ MOD MENU", this::openOverlay);
        button("МӘЗІРДІ ANDROID ТЕРЕЗЕСІНДЕ СЫНАУ", () -> startActivity(new Intent(this, PreviewActivity.class)));
        button("LAC ОЙЫНЫН АШУ", () -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.MA.LAC");
            if (launch == null) status.setText("LAC орнатылмаған немесе қолжетімсіз"); else startActivity(launch);
        });
        button("БҰРЫНҒЫ НҰСҚАҒА ҚАЙТУ", () -> {
            status.setText(ConfigStore.rollback(this) ? "Алдыңғы баптаулар қалпына келді" : "Резервтік нұсқа жоқ");
            render();
        });
        label("ОРНАТЫЛҒАН МӘЗІР", 20, Color.WHITE, true);
        catalog = label("", 15, Color.LTGRAY, false);
        label("DEMO • Бұл меню ойын кодын өзгертпейді. Жасырын қолжетімділік, root және APK патчтау жоқ.", 12, Color.GRAY, false);
        render();
        // Network checks require a user action or an opted-in scheduled job.
    }
    @Override protected void onResume() { super.onResume(); if (catalog != null) render(); }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setPadding(0, dp(10), 0, dp(10));
        page.addView(v);
        return v;
    }
    private void button(String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
        lp.topMargin = dp(12);
        page.addView(button, lp);
        button.setOnClickListener(v -> action.run());
    }
    private void render() {
        JSONObject config = ConfigStore.current(this);
        revision.setText("Data revision: " + config.optInt("revision", 1));
        JSONArray list = config.optJSONArray("features");
        StringBuilder builder = new StringBuilder();
        if (list != null) for (int i = 0; i < list.length(); i++) {
            JSONObject feature = list.optJSONObject(i);
            if (feature != null) builder.append("• ").append(feature.optString("title"))
                    .append("\n   ").append(feature.optString("description")).append("\n\n");
        }
        catalog.setText(builder.toString());
    }
    private void check(boolean force) {
        status.setText("GitHub жаңартуы тексерілуде…");
        UpdateEngine.check(this, force, result -> { status.setText(result); render(); });
    }
    private void toggleScheduledUpdates() {
        JobScheduler scheduler = getSystemService(JobScheduler.class);
        if (scheduler == null) {
            status.setText("Құрылғы фондық жаңартуды қолдамайды");
            return;
        }
        final int jobId = 13013;
        boolean enabled = false;
        for (JobInfo job : scheduler.getAllPendingJobs()) {
            if (job.getId() == jobId) { enabled = true; break; }
        }
        if (enabled) {
            scheduler.cancel(jobId);
            status.setText("Фондық авто жаңарту өшірілді");
        } else {
            JobInfo job = new JobInfo.Builder(jobId, new ComponentName(this, UpdateJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(6L * 60L * 60L * 1000L)
                    .setPersisted(true)
                    .build();
            int result = scheduler.schedule(job);
            status.setText(result == JobScheduler.RESULT_SUCCESS
                    ? "Авто жаңарту қосылды · Android шамамен әр 6 сағатта тексереді"
                    : "Фондық тапсырманы тіркеу мүмкін болмады");
        }
    }

    private void openOverlay() {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            status.setText("H13 тоқтату хабарламасы үшін рұқсат беріп, батырманы қайта бас");
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 13);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            status.setText("Алдымен «Басқа қолданбалардың үстінде көрсету» рұқсатын бер");
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        startForegroundService(new Intent(this, OverlayService.class));
        status.setText("Қалқымалы мәзір іске қосылды · өзің жаба аласың");
    }
}
