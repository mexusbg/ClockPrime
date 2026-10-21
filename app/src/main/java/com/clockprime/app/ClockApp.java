package com.clockprime.app;

import android.app.Application;

/** Records process starts and crashes in the debug log before the platform handler runs. */
public class ClockApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        Thread.UncaughtExceptionHandler platform=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{DebugLog.error(this,"crash "+thread.getName(),error);if(platform!=null)platform.uncaughtException(thread,error);});
        DebugLog.log(this,"process","start pid="+android.os.Process.myPid()+" "+DebugLog.state(this));
        noteForceStop(this);
    }
    static final String EXIT_SEEN="last_exit_seen",FORCE_STOPPED="force_stopped";
    /** Device-protected so it also works when the process starts before first unlock. */
    static android.content.SharedPreferences state(android.content.Context c){return c.createDeviceProtectedStorageContext().getSharedPreferences("process_state",MODE_PRIVATE);}
    /**
     * A force stop cancels every alarm this app scheduled (other apps' alarms are unaffected) and
     * nothing runs again until the app is opened, which re-arms them; remember it so the UI can say so.
     */
    static void noteForceStop(android.content.Context c){
        try{
            java.util.List<android.app.ApplicationExitInfo> exits=c.getSystemService(android.app.ActivityManager.class).getHistoricalProcessExitReasons(null,0,1);
            if(exits.isEmpty())return;
            android.app.ApplicationExitInfo last=exits.get(0);android.content.SharedPreferences p=state(c);
            if(last.getTimestamp()<=p.getLong(EXIT_SEEN,0))return;
            boolean forced=last.getReason()==android.app.ApplicationExitInfo.REASON_USER_REQUESTED||last.getReason()==android.app.ApplicationExitInfo.REASON_USER_STOPPED;
            p.edit().putLong(EXIT_SEEN,last.getTimestamp()).putBoolean(FORCE_STOPPED,forced||p.getBoolean(FORCE_STOPPED,false)).apply();
            if(forced)DebugLog.log(c,"process","force stop detected at "+java.time.Instant.ofEpochMilli(last.getTimestamp())+" "+last.getDescription());
        }catch(RuntimeException e){DebugLog.error(c,"process",e);}
    }
}
