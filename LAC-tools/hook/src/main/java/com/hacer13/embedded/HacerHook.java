package com.hacer13.embedded;
import android.app.Activity;
import android.view.*;
import android.widget.FrameLayout;
import com.hacer13.ui.MenuView;

/** Explicit source integration into a host you have permission to modify. */
public final class HacerHook {
    private static final String TAG="HACER13_VISIBLE_MENU";
    private HacerHook(){}
    public static void attach(Activity a){
        if(a==null)return;a.runOnUiThread(()->{
            if(a.isFinishing()||a.isDestroyed())return;ViewGroup content=a.findViewById(android.R.id.content);
            if(content==null||content.findViewWithTag(TAG)!=null)return;
            FrameLayout holder=new FrameLayout(a);holder.setTag(TAG);
            FrameLayout.LayoutParams pos=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);pos.leftMargin=12;pos.topMargin=100;
            MenuView menu=new MenuView(a,new MenuView.Host(){
                public void move(float dx,float dy){holder.setTranslationX(Math.max(-pos.leftMargin,Math.min(content.getWidth()-holder.getWidth()-pos.leftMargin,holder.getTranslationX()+dx)));holder.setTranslationY(Math.max(-pos.topMargin,Math.min(content.getHeight()-holder.getHeight()-pos.topMargin,holder.getTranslationY()+dy)));}
                public void resize(boolean open){holder.post(()->{ViewGroup.LayoutParams p=holder.getLayoutParams();if(p!=null){p.width=Math.min(Math.round((open?320:58)*a.getResources().getDisplayMetrics().density),Math.max(58,content.getWidth()));holder.setLayoutParams(p);move(0,0);}});}
                public void close(){content.removeView(holder);}
            });
            holder.addView(menu,new FrameLayout.LayoutParams(-1,-2));content.addView(holder,pos);
        });
    }
    public static void detach(Activity a){if(a==null)return;a.runOnUiThread(()->{ViewGroup c=a.findViewById(android.R.id.content);if(c!=null){View v=c.findViewWithTag(TAG);if(v!=null)c.removeView(v);}});}
}
