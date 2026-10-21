package com.clockprime.app;

import android.app.*;
import android.content.*;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import org.json.JSONObject;

/** A visible countdown service covers very short timers below AlarmManager's minimum futurity. */
public class TimerService extends Service {
    final Handler handler=new Handler(Looper.getMainLooper());PowerManager.WakeLock wake;
    final Runnable update=new Runnable(){public void run(){
        ClockStore s=new ClockStore(TimerService.this);long nearest=Long.MAX_VALUE;
        for(JSONObject t:s.objects("timers"))if(t.optBoolean("running")){
            long remaining=ClockStore.remaining(t);
            if(remaining==0)ClockReceiver.fire(TimerService.this,new Intent("FIRE").putExtra("kind","timers").putExtra("id",t.optInt("id")));
            else nearest=Math.min(nearest,remaining);
        }
        if(nearest==Long.MAX_VALUE){stopSelf();return;}
        if(nearest<=10000&&(wake==null||!wake.isHeld())){wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"ClockPrime:timer-finish");wake.acquire(nearest+5000);}
        handler.postDelayed(this,Math.min(1000,nearest));
    }};
    int foregroundId;
    /** Main thread only: a startForegroundService() call has not reached onStartCommand yet. */
    static boolean pendingStart;
    /** Promotes the soonest timer's own notification, so a running timer never shows twice. */
    @Override public int onStartCommand(Intent intent,int flags,int id){
        RingService.channels(this);ClockStore s=new ClockStore(this);
        JSONObject nearest=s.objects("timers").stream().filter(t->t.optBoolean("running")).min(java.util.Comparator.comparingLong(ClockStore::remaining)).orElse(null);
        if(nearest==null){
            startForeground(2,new Notification.Builder(this,RingService.TIMER_CHANNEL).setSmallIcon(R.drawable.ic_stat_notify).setContentTitle(getString(R.string.timers_title)).build());
            pendingStart=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;
        }
        int next=nearest.optInt("id");startForeground(next,s.timerNotification(nearest));pendingStart=false;
        if(foregroundId!=0&&foregroundId!=next)release(s,foregroundId);
        if(foregroundId!=next)DebugLog.log(this,"timer-service","foreground timer "+next);
        foregroundId=next;handler.removeCallbacks(update);handler.post(update);return START_STICKY;
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration){super.onConfigurationChanged(configuration);onStartCommand(null,0,0);}
    /** A former foreground notification stays only for a paused timer; otherwise it is removed. */
    void release(ClockStore s,int id){JSONObject t=s.find("timers",id);if(t!=null&&ClockStore.timerShown(t))s.notifyTimer(t);else getSystemService(NotificationManager.class).cancel(id);}
    @Override public void onDestroy(){handler.removeCallbacks(update);
        // Detach so a paused timer keeps its notification and island after the service stops.
        stopForeground(STOP_FOREGROUND_DETACH);if(foregroundId!=0)release(new ClockStore(this),foregroundId);if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
