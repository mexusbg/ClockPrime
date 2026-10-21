package com.clockprime.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Rotating on-device event log in device-protected storage, so Direct Boot events are kept too. */
final class DebugLog {
    static final long MAX=384*1024;
    static final DateTimeFormatter STAMP=DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS");
    static File dir(Context c){return c.getApplicationContext().createDeviceProtectedStorageContext().getFilesDir();}
    static synchronized void log(Context c,String tag,String message){
        android.util.Log.i("ClockPrime",tag+": "+message);
        try{
            File file=new File(dir(c),"debug.log");
            if(file.length()>MAX){File old=new File(dir(c),"debug.1.log");if(!old.delete()&&old.exists())return;if(!file.renameTo(old))return;}
            try(Writer w=new OutputStreamWriter(new FileOutputStream(file,true),StandardCharsets.UTF_8)){w.write(LocalDateTime.now().format(STAMP)+" "+tag+" "+message+"\n");}
        }catch(IOException ignored){}
    }
    static void error(Context c,String tag,Throwable error){log(c,tag,android.util.Log.getStackTraceString(error));}
    static String state(Context c){
        NotificationManager nm=c.getSystemService(NotificationManager.class);PowerManager pm=c.getSystemService(PowerManager.class);KeyguardManager km=c.getSystemService(KeyguardManager.class);
        return "interactive="+pm.isInteractive()+" locked="+km.isKeyguardLocked()+" overlay="+Settings.canDrawOverlays(c)+" bgPopup="+RingLaunch.backgroundPopups(c)+" fsi="+(Build.VERSION.SDK_INT<34||nm.canUseFullScreenIntent());
    }
    static String property(String key){
        try{Object v=Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,key,"");return v==null?"":v.toString();}catch(Exception e){return "";}
    }
    static void appendFile(StringBuilder out,File file){
        if(!file.exists())return;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)out.append(line).append('\n');}catch(IOException e){out.append("(unreadable: ").append(e).append(")\n");}
    }
    /** Writes a full report to the cache and returns a shareable content URI. */
    static Uri export(Context c) throws IOException {
        StringBuilder out=new StringBuilder();
        NotificationManager nm=c.getSystemService(NotificationManager.class);AlarmManager am=c.getSystemService(AlarmManager.class);PowerManager pm=c.getSystemService(PowerManager.class);
        String version;try{android.content.pm.PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);version=p.versionName+" ("+p.getLongVersionCode()+")";}catch(Exception e){version="?";}
        out.append("ClockPrime debug report ").append(ZonedDateTime.now()).append('\n');
        out.append("App ").append(version).append('\n');
        out.append("Device ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" (").append(Build.DEVICE).append(") Android ").append(Build.VERSION.RELEASE).append(" SDK ").append(Build.VERSION.SDK_INT).append(" build ").append(Build.DISPLAY).append('\n');
        out.append("HyperOS ").append(property("ro.mi.os.version.name")).append(" MIUI ").append(property("ro.miui.ui.version.name")).append(" OneUI ").append(property("ro.build.version.oneui")).append(" island ").append(FocusIsland.deviceSupportsIsland()).append(" focusProtocol ").append(FocusIsland.protocol(c)).append('\n');
        out.append("State ").append(state(c)).append(" exact=").append(am.canScheduleExactAlarms()).append(" notifications=").append(nm.areNotificationsEnabled()).append(" batteryUnrestricted=").append(pm.isIgnoringBatteryOptimizations(c.getPackageName())).append('\n');
        out.append("Health ").append(AlarmHealth.describe(c)).append(" islandAllowed=").append(FocusIsland.canShowFocus(c)).append('\n');
        android.content.ContentResolver cr=c.getContentResolver();
        out.append("Lock screen notifications show=").append(Settings.Secure.getInt(cr,"lock_screen_show_notifications",-1)).append(" silent=").append(Settings.Secure.getInt(cr,"lock_screen_show_silent_notifications",-1)).append(" private=").append(Settings.Secure.getInt(cr,"lock_screen_allow_private_notifications",-1)).append('\n');
        for(android.service.notification.StatusBarNotification n:nm.getActiveNotifications())out.append("Active ").append(n.getId()).append(" tag=").append(n.getTag()).append(" pkg=").append(n.getOpPkg()).append(" group=").append(n.getGroupKey()).append(" summary=").append((n.getNotification().flags&Notification.FLAG_GROUP_SUMMARY)!=0).append(" title=").append(n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE)).append(" posted=").append(Instant.ofEpochMilli(n.getPostTime()).atZone(ZoneId.systemDefault()).toLocalTime()).append(' ').append(n.getNotification().getChannelId()).append(" ongoing=").append(n.isOngoing()).append(" visibility=").append(n.getNotification().visibility).append(" island=").append(n.getNotification().extras.containsKey("miui.focus.param")).append('\n');
        for(NotificationChannel ch:nm.getNotificationChannels())out.append("Channel ").append(ch.getId()).append(" importance=").append(ch.getImportance()).append(" lock=").append(ch.getLockscreenVisibility()).append(" dnd=").append(ch.canBypassDnd()).append('\n');
        AlarmManager.AlarmClockInfo next=am.getNextAlarmClock();out.append("System next alarm clock ").append(next==null?"none":Instant.ofEpochMilli(next.getTriggerTime()).atZone(ZoneId.systemDefault())).append('\n');
        for(ApplicationExitInfo exit:c.getSystemService(ActivityManager.class).getHistoricalProcessExitReasons(null,0,10))
            out.append("Exit ").append(Instant.ofEpochMilli(exit.getTimestamp()).atZone(ZoneId.systemDefault())).append(" reason=").append(exit.getReason()).append(" importance=").append(exit.getImportance()).append(' ').append(exit.getDescription()).append('\n');
        out.append("\n== Settings ==\n").append(new TreeMap<>(new ClockSettings(c).prefs.getAll())).append('\n');
        out.append("\n== Saved clock data ==\n");try{out.append(new ClockStore(c).data.toString(1));}catch(Exception e){out.append(e);}out.append('\n');
        out.append("\n== Event log ==\n");appendFile(out,new File(dir(c),"debug.1.log"));appendFile(out,new File(dir(c),"debug.log"));
        out.append("\n== Logcat (this app) ==\n");
        try{java.lang.Process p=new ProcessBuilder("logcat","-d","-v","threadtime","-t","3000","--pid="+android.os.Process.myPid()).redirectErrorStream(true).start();
            try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)out.append(line).append('\n');}
        }catch(IOException e){out.append(e).append('\n');}
        File folder=new File(c.getCacheDir(),"logs");File[] stale=folder.listFiles();if(stale!=null)for(File f:stale)if(!f.delete())f.deleteOnExit();
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Cannot create "+folder);
        String name="clockprime-debug-"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+".txt";
        try(Writer w=new OutputStreamWriter(new FileOutputStream(new File(folder,name)),StandardCharsets.UTF_8)){w.write(out.toString());}
        return Uri.parse("content://"+LogProvider.AUTHORITY+"/"+name);
    }
    /** Builds the report off the main thread (logcat and HyperOS queries are slow), then opens the share sheet. */
    static void share(Activity a){
        new Thread(()->{
            try{Uri uri=export(a);
                Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_SUBJECT,uri.getLastPathSegment()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                send.setClipData(ClipData.newRawUri(uri.getLastPathSegment(),uri));
                a.runOnUiThread(()->{if(!a.isFinishing())a.startActivity(Intent.createChooser(send,a.getString(R.string.export_logs)));});
            }catch(Exception e){error(a,"export",e);a.runOnUiThread(()->android.widget.Toast.makeText(a,e.toString(),android.widget.Toast.LENGTH_LONG).show());}
        },"debug-export").start();
    }
}
