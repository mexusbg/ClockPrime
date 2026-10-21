package com.clockprime.app;

import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * Opens {@link RingActivity}. The full-screen intent covers lock/screen-off. While the phone is
 * unlocked Android only shows a heads-up for it and blocks background activity starts, unless
 * the user allowed "display over other apps" (Xiaomi additionally needs "background pop-ups").
 */
final class RingLaunch {
    static final Handler handler=new Handler(Looper.getMainLooper());
    /** Xiaomi AppOps code for "Display pop-up windows while running in the background". */
    static final int OP_BACKGROUND_START_ACTIVITY=10021;

    static Intent intent(Context c){
        return new Intent(c,RingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP
                        |Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_USER_ACTION);
    }

    static PendingIntent pending(Context c){
        return PendingIntent.getActivity(c,1,intent(c),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    /** True when an activity start from the background is permitted for this app. */
    static boolean canLaunch(Context c){return Settings.canDrawOverlays(c)&&!Boolean.FALSE.equals(backgroundPopups(c));}

    /** Xiaomi-only background pop-up permission; null when not Xiaomi or not readable. */
    static Boolean backgroundPopups(Context c){
        if(!FocusIsland.isXiaomiFamily())return null;
        try{
            Object mode=AppOpsManager.class.getMethod("checkOpNoThrow",int.class,int.class,String.class)
                    .invoke(c.getSystemService(AppOpsManager.class),OP_BACKGROUND_START_ACTIVITY,android.os.Process.myUid(),c.getPackageName());
            return Integer.valueOf(AppOpsManager.MODE_ALLOWED).equals(mode);
        }catch(Exception e){return null;}
    }

    /** Settings screen that grants the missing permission: Xiaomi "Other permissions" or Android overlay access. */
    static void requestPermission(Context c){
        if(FocusIsland.isXiaomiFamily()&&Settings.canDrawOverlays(c)){
            for(String activity:new String[]{"com.miui.permcenter.permissions.PermissionsEditorActivity","com.miui.permcenter.permissions.AppPermissionsEditorActivity"})
                try{c.startActivity(new Intent("miui.intent.action.APP_PERM_EDITOR").setComponent(new ComponentName("com.miui.securitycenter",activity)).putExtra("extra_pkgname",c.getPackageName()));return;}catch(Exception ignored){}
        }
        c.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+c.getPackageName())));
    }

    static void open(Context c){
        Context app=c.getApplicationContext();
        if(RingActivity.visible)return;
        long started=android.os.SystemClock.elapsedRealtime();
        DebugLog.log(app,"launch","open "+DebugLog.state(app));
        wakeScreen(app);
        attempt(app);
        // The service may still be entering the foreground; one retry covers that window.
        handler.postDelayed(()->{if(!RingActivity.visible)attempt(app);},400);
        handler.postDelayed(()->{if(RingActivity.shownAt<started)DebugLog.log(app,"launch","ring screen never shown within 2s "+DebugLog.state(app));},2000);
    }

    static void attempt(Context c){
        try{c.startActivity(intent(c));}catch(Exception e){DebugLog.log(c,"launch","startActivity failed "+e);}
    }

    @SuppressWarnings("deprecation")
    static void wakeScreen(Context c){
        try{
            PowerManager pm=c.getSystemService(PowerManager.class);
            if(pm==null||pm.isInteractive())return;
            PowerManager.WakeLock wl=pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP|PowerManager.ON_AFTER_RELEASE,
                    "ClockPrime:ringScreen");
            wl.setReferenceCounted(false);
            wl.acquire(5000L);
        }catch(Exception e){DebugLog.log(c,"launch","wake failed "+e);}
    }
}
