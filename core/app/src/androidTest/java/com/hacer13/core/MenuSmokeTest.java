package com.hacer13.core;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.hacer13.ui.MenuView;
import com.hacer13.update.SignedUpdater;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MenuSmokeTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private Activity activity;
    private void launch(Class<?> cls){activity=(Activity)inst.startActivitySync(new Intent(inst.getTargetContext(),cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();}
    @After public void finish(){if(activity!=null)inst.runOnMainSync(()->activity.finish());}
    private View find(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View x=find(((ViewGroup)v).getChildAt(i),text);if(x!=null)return x;}return null;}
    private int switches(View v){int n=v instanceof Switch?1:0;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)n+=switches(((ViewGroup)v).getChildAt(i));return n;}
    private void click(String text){inst.runOnMainSync(()->{View v=find(activity.getWindow().getDecorView(),text);assertNotNull(text,v);assertTrue(v.performClick());});inst.waitForIdleSync();}
    private void shell(String command)throws Exception{try(ParcelFileDescriptor p=inst.getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(p)){while(in.read()!=-1){}}}
    @Test public void menuOpensMovesCollapsesClosesAndReattaches()throws Exception{
        launch(PreviewActivity.class);click("H13");
        inst.runOnMainSync(()->assertEquals(3,switches(activity.getWindow().getDecorView())));
        // Save a screenshot of the actual Android UI, explicitly labelled preview host.
        Thread.sleep(200);
        Bitmap screenshot=inst.getUiAutomation().takeScreenshot();assertNotNull(screenshot);
        screenshot.recycle();
        // Gradle uninstalls test packages after instrumentation; app-owned files are removed.
        // Shell-owned evidence survives cleanup on this disposable emulator.
        shell("screencap -p /data/local/tmp/H13-Android-preview.png");
        inst.runOnMainSync(()->{
            View title=find(activity.getWindow().getDecorView(),"HACER 13     •     CORE 1.0");assertNotNull(title);
            MenuView menu=(MenuView)title.getParent();View holder=(View)menu.getParent();float before=holder.getTranslationY();long now=SystemClock.uptimeMillis();
            for(MotionEvent e:new MotionEvent[]{MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,5,5,0),MotionEvent.obtain(now,now+30,MotionEvent.ACTION_MOVE,25,55,0),MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,25,55,0)}){title.dispatchTouchEvent(e);e.recycle();}
            assertTrue(holder.getTranslationY()>before);
        });
        click("Жинау → H13");click("H13");click("Мәзірді толық жабу");
        inst.runOnMainSync(()->assertNull(find(activity.getWindow().getDecorView(),"H13")));
        inst.runOnMainSync(()->com.hacer13.embedded.HacerHook.attach(activity));inst.waitForIdleSync();click("H13");
        // Attaching twice must not create another menu.
        inst.runOnMainSync(()->com.hacer13.embedded.HacerHook.attach(activity));inst.waitForIdleSync();
        inst.runOnMainSync(()->assertEquals(3,switches(activity.getWindow().getDecorView())));
    }
    @Test public void realAndroidSignedGitHubUpdate()throws Exception{
        File directory=new File(inst.getTargetContext().getFilesDir(),"instrumentation-update-"+System.nanoTime());
        SignedUpdater updater=new SignedUpdater(directory);String result=updater.check();
        assertTrue(result,result.contains("SHA-256"));assertTrue(updater.current().revision>=1);
        assertEquals("demo",updater.current().menu.getJSONArray("features").getJSONObject(0).getString("kind"));
        assertTrue(updater.check().contains("Жаңарту жоқ"));
    }
    @Test public void foregroundOverlayStartsAndStops()throws Exception{
        launch(MainActivity.class);Context c=inst.getTargetContext();String pkg=c.getPackageName();
        // Test-device grants through Android's shell interface; shipping code always asks the user.
        shell("appops set "+pkg+" SYSTEM_ALERT_WINDOW allow");shell("pm grant "+pkg+" android.permission.POST_NOTIFICATIONS");
        try{
            inst.runOnMainSync(()->c.startForegroundService(new Intent(c,OverlayService.class)));Thread.sleep(1000);
            boolean running=false;for(ActivityManager.RunningServiceInfo s:c.getSystemService(ActivityManager.class).getRunningServices(30))if(s.service.getClassName().equals(OverlayService.class.getName()))running=s.foreground;
            assertTrue("Overlay must be a foreground service",running);
            inst.runOnMainSync(()->c.startService(new Intent(c,OverlayService.class).setAction(OverlayService.STOP)));Thread.sleep(500);
            for(ActivityManager.RunningServiceInfo s:c.getSystemService(ActivityManager.class).getRunningServices(30))assertNotEquals(OverlayService.class.getName(),s.service.getClassName());
        }finally{c.stopService(new Intent(c,OverlayService.class));shell("appops set "+pkg+" SYSTEM_ALERT_WINDOW default");}
    }
    @Test public void scheduledUpdatesOptInRunAndOptOut()throws Exception{
        launch(MainActivity.class);Context c=inst.getTargetContext();android.app.job.JobScheduler scheduler=c.getSystemService(android.app.job.JobScheduler.class);scheduler.cancel(13013);
        click("ФОНДЫҚ АВТО ЖАҢАРТУ: ҚОСУ / ӨШІРУ");assertNotNull("User opt-in must schedule the network job",scheduler.getPendingJob(13013));
        try{
            shell("cmd jobscheduler run -f "+c.getPackageName()+" 13013");
            long deadline=SystemClock.elapsedRealtime()+25000;
            while(ConfigStore.engine(c).current().revision==0&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(250);
            assertTrue("Scheduled JobService must install the signed data without a manual update click",ConfigStore.engine(c).current().revision>=1);
            click("ФОНДЫҚ АВТО ЖАҢАРТУ: ҚОСУ / ӨШІРУ");assertNull("User opt-out must cancel the job",scheduler.getPendingJob(13013));
        }finally{scheduler.cancel(13013);}
    }
}
