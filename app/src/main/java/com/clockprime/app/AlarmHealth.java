package com.clockprime.app;

import android.app.*;
import android.content.*;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.Settings;
import java.util.*;

/** Phone conditions that would make an alarm silent or keep its notification from showing. */
final class AlarmHealth {
    static final String VOLUME="volume",DND="dnd",NOTIFICATIONS="notifications",EXACT="exact";
    static List<String> problems(Context c){
        List<String> out=new ArrayList<>();
        // Many phones keep alarm volume at a minimum of 1 instead of 0; both are effectively unheard.
        AudioManager audio=c.getSystemService(AudioManager.class);
        if(audio.getStreamVolume(AudioManager.STREAM_ALARM)<=audio.getStreamMinVolume(AudioManager.STREAM_ALARM)&&!audio.isStreamMute(AudioManager.STREAM_ALARM))out.add(VOLUME);
        new ClockSettings(c).alarmLevel(c);
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        // With "Ring during Do Not Disturb" a blocked alarm plays at media volume instead.
        if(blocksAlarms(nm)&&(!new ClockSettings(c).ringInDnd()||audio.getStreamVolume(AudioManager.STREAM_ACCESSIBILITY)<=audio.getStreamMinVolume(AudioManager.STREAM_ACCESSIBILITY)))out.add(DND);
        NotificationChannel ring=nm.getNotificationChannel("alerts_ring");
        if(!nm.areNotificationsEnabled()||(ring!=null&&ring.getImportance()<NotificationManager.IMPORTANCE_HIGH))out.add(NOTIFICATIONS);
        if(!c.getSystemService(AlarmManager.class).canScheduleExactAlarms())out.add(EXACT);
        return out;
    }
    static boolean blocksAlarms(NotificationManager nm){
        int filter=nm.getCurrentInterruptionFilter();
        if(filter==NotificationManager.INTERRUPTION_FILTER_NONE)return true;
        if(filter!=NotificationManager.INTERRUPTION_FILTER_PRIORITY)return false;
        // Android 15+: the consolidated policy also covers scheduled and app modes, not only the manual one.
        try{NotificationManager.Policy policy=android.os.Build.VERSION.SDK_INT>=35?nm.getConsolidatedNotificationPolicy():nm.getNotificationPolicy();return (policy.priorityCategories&NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS)==0;}catch(RuntimeException e){return false;}
    }
    static int title(String problem){
        return switch(problem){case VOLUME->R.string.health_volume;case DND->R.string.health_dnd;case NOTIFICATIONS->R.string.health_notifications;default->R.string.health_exact;};
    }
    static int detail(String problem){
        return switch(problem){case VOLUME->R.string.health_volume_detail;case DND->R.string.health_dnd_detail;case NOTIFICATIONS->R.string.health_notifications_detail;default->R.string.health_exact_detail;};
    }
    static void fix(Activity a,String problem){
        String pkg=a.getPackageName();
        Intent intent=switch(problem){
            case VOLUME->new Intent(Settings.Panel.ACTION_VOLUME);
            case DND->new Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS);
            case NOTIFICATIONS->new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,pkg);
            default->new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+pkg));
        };
        try{a.startActivity(intent);}catch(ActivityNotFoundException e){a.startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));}
    }
    /** Short state for the debug log. */
    static String describe(Context c){
        AudioManager audio=c.getSystemService(AudioManager.class);
        return "alarmVolume="+audio.getStreamVolume(AudioManager.STREAM_ALARM)+"/"+audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)+" dndFilter="+c.getSystemService(NotificationManager.class).getCurrentInterruptionFilter()+" problems="+problems(c);
    }
}
