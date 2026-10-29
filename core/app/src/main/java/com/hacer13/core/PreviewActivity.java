package com.hacer13.core;
import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;
import com.hacer13.embedded.HacerHook;
/** Real Android source-level integration host. This screen is not LAC. */
public class PreviewActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);TextView text=new TextView(this);text.setBackgroundColor(0xff111827);
        text.setText("HACER 13\nAndroid мәзірін сынау\n\nОйынға интеграция әлі расталмаған");text.setTextColor(0xffa5b4c8);text.setTextSize(22);text.setGravity(Gravity.CENTER);setContentView(text);
        HacerHook.attach(this);
    }
    @Override protected void onDestroy(){HacerHook.detach(this);super.onDestroy();}
}
