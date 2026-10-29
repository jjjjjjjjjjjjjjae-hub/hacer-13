package com.hacer13.core;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import com.hacer13.ui.MenuView;

/** Explicit user-started overlay, with Stop in both the menu and persistent notification. */
public class OverlayService extends Service {
    public static final String STOP="com.hacer13.core.STOP_OVERLAY";
    private WindowManager manager;private MenuView menu;private WindowManager.LayoutParams pos;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("h13-overlay","H13 қалқымалы мәзірі",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,OverlayService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,"h13-overlay").setSmallIcon(android.R.drawable.ic_menu_manage).setContentTitle("HACER 13 мәзірі қосулы").setContentText("DEMO · тоқтату үшін хабарламаны қолдан").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Тоқтату",stop).build()).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(13,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(13,n);
        if(menu!=null)return START_NOT_STICKY;manager=getSystemService(WindowManager.class);
        pos=new WindowManager.LayoutParams(dp(58),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        pos.gravity=Gravity.TOP|Gravity.LEFT;pos.x=dp(12);pos.y=dp(100);
        menu=new MenuView(this,new MenuView.Host(){
            public void move(float dx,float dy){pos.x+=Math.round(dx);pos.y+=Math.round(dy);clamp();refresh();}
            public void resize(boolean expanded){pos.width=Math.min(dp(expanded?320:58),getResources().getDisplayMetrics().widthPixels);if(menu!=null)menu.post(()->{clamp();refresh();});}
            public void close(){stopSelf();}
        });
        try{manager.addView(menu,pos);}catch(RuntimeException e){menu=null;stopSelf();}return START_NOT_STICKY;
    }
    private void clamp(){pos.x=Math.max(0,Math.min(pos.x,getResources().getDisplayMetrics().widthPixels-pos.width));pos.y=Math.max(0,Math.min(pos.y,getResources().getDisplayMetrics().heightPixels-(menu==null?dp(58):menu.getHeight())-dp(32)));}
    private void refresh(){if(menu!=null&&menu.isAttachedToWindow())try{manager.updateViewLayout(menu,pos);}catch(RuntimeException e){stopSelf();}}
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);clamp();refresh();}
    @Override public void onDestroy(){if(menu!=null&&manager!=null)try{manager.removeView(menu);}catch(RuntimeException ignored){}menu=null;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
