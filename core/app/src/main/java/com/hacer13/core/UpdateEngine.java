package com.hacer13.core;
import android.content.Context;
import android.os.*;
import java.util.concurrent.*;
public final class UpdateEngine {
    public interface Listener{void result(String message);}
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private UpdateEngine(){}
    public static void check(Context c,boolean force,Listener listener){
        Context app=c.getApplicationContext();IO.execute(()->{
            String result;long now=System.currentTimeMillis(),last=app.getSharedPreferences("update",0).getLong("checked_at",0);
            if(!force&&now>=last&&now-last<6L*60*60*1000)result="Келесі автоматты тексеру кейінірек болады";
            else try{result=ConfigStore.engine(app).check();app.getSharedPreferences("update",0).edit().putLong("checked_at",now).apply();}
            catch(Exception e){result="Жаңарту қабылданбады: "+e.getMessage();}
            final String message=result;new Handler(Looper.getMainLooper()).post(()->listener.result(message));
        });
    }
}
