package com.hacer13.ui;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.hacer13.update.*;
import org.json.*;
import java.io.File;
import java.util.concurrent.*;

/** Shared native menu. All switches are explicitly DEMO; no game-state adapter is installed. */
public final class MenuView extends LinearLayout {
    public interface Host { void move(float dx,float dy); void resize(boolean expanded); void close(); }
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private final Host host; private boolean expanded; private TextView status; private int accent=0xff40e4be;
    public MenuView(Context c,Host h){super(c);host=h;setOrientation(VERTICAL);render(false);}
    public int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size,int color){TextView t=new TextView(getContext());t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(8),dp(8),dp(8),dp(8));return t;}
    private void button(String text,Runnable action){Button b=new Button(getContext());b.setAllCaps(false);b.setText(text);b.setTextSize(12);b.setTextColor(Color.WHITE);b.setBackgroundTintList(ColorStateList.valueOf(0xff202a38));b.setOnClickListener(v->action.run());addView(b,new LayoutParams(-1,dp(46)));}
    public void render(boolean open){
        expanded=open;removeAllViews();setPadding(dp(open?12:2),dp(open?10:2),dp(open?12:2),dp(open?10:2));
        SignedUpdater.Snapshot data=null;String message="Офлайн мәзір · DEMO";
        try{data=new SignedUpdater(new File(getContext().getFilesDir(),"h13-v2")).current();accent=Color.parseColor(data.theme.getString("accent"));message="Data v"+data.revision+" · DEMO";}catch(Exception e){message="Жаңарту күйін тексеру қажет · DEMO";}
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff0d121b);bg.setCornerRadius(dp(open?22:30));bg.setStroke(dp(1),0xff29313e);setBackground(bg);setElevation(dp(12));
        if(!open){TextView b=label("H13",16,accent);b.setGravity(Gravity.CENTER);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setContentDescription("H13 мәзірін ашу; жылжыту үшін сүйреу");addView(b,new LayoutParams(dp(54),dp(54)));drag(b,()->render(true));}
        else{
            TextView title=label("HACER 13     •     CORE 1.0",15,accent);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setContentDescription("Мәзірді жылжыту");addView(title);drag(title,()->{});
            status=label(message,12,0xffa5b4c8);addView(status);
            if(data!=null&&data.badge!=null){ImageView image=new ImageView(getContext());image.setImageBitmap(BitmapFactory.decodeFile(data.badge.getAbsolutePath()));image.setContentDescription("HACER badge");addView(image,new LayoutParams(-1,dp(40)));}
            ScrollView scroll=new ScrollView(getContext());LinearLayout rows=new LinearLayout(getContext());rows.setOrientation(VERTICAL);scroll.addView(rows);
            JSONArray features;try{features=(data==null?new JSONObject(Trust.FALLBACK):data.menu).getJSONArray("features");}catch(Exception e){features=new JSONArray();}
            for(int i=0;i<features.length();i++){JSONObject f=features.optJSONObject(i);if(f==null)continue;String id=f.optString("id");Switch toggle=new Switch(getContext());toggle.setText(f.optString("title")+"  ·  DEMO\n"+f.optString("description"));toggle.setTextColor(Color.WHITE);toggle.setTextSize(13);toggle.setPadding(dp(8),dp(10),dp(8),dp(10));toggle.setMinHeight(dp(64));toggle.setThumbTintList(ColorStateList.valueOf(accent));toggle.setChecked(getContext().getSharedPreferences("h13-demo",0).getBoolean(id,false));toggle.setOnCheckedChangeListener((v,on)->getContext().getSharedPreferences("h13-demo",0).edit().putBoolean(id,on).apply());rows.addView(toggle,new LayoutParams(-1,-2));}
            int screenHeight=getResources().getDisplayMetrics().heightPixels;
            addView(scroll,new LayoutParams(-1,Math.min(Math.min(dp(235),screenHeight/3),Math.max(dp(48),screenHeight-dp(300)))));
            addView(label("DEMO: ойын параметрлері өзгермейді",11,0xffa5b4c8));
            button("GitHub жаңартуын тексеру",()->{status.setText("Ed25519 + SHA-256 тексерілуде…");IO.execute(()->{String result;try{result=new SignedUpdater(new File(getContext().getFilesDir(),"h13-v2")).check();}catch(Exception e){result="Қабылданбады: "+e.getMessage();}final String msg=result;new Handler(Looper.getMainLooper()).post(()->{if(expanded){render(true);status.setText(msg);}});});});
            button("Жинау → H13",()->render(false));button("Мәзірді толық жабу",host::close);
        }
        host.resize(open);setAlpha(.65f);animate().alpha(1f).setDuration(160).start();
    }
    private void drag(View handle,Runnable click){
        handle.setOnClickListener(v->click.run());
        handle.setOnTouchListener(new View.OnTouchListener(){
            float x,y,downX,downY;boolean moved;final int slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();
            public boolean onTouch(View v,MotionEvent e){
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=x=e.getRawX();downY=y=e.getRawY();moved=false;return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE){if(Math.hypot(e.getRawX()-downX,e.getRawY()-downY)>slop)moved=true;if(moved)host.move(e.getRawX()-x,e.getRawY()-y);x=e.getRawX();y=e.getRawY();return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_UP){if(!moved)v.performClick();return true;}return e.getActionMasked()==MotionEvent.ACTION_CANCEL;
            }
        });
    }
}
