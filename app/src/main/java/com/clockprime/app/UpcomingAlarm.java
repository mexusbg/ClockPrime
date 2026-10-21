package com.clockprime.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import org.json.JSONObject;
import java.util.Comparator;

/** One ongoing notification for the earliest enabled occurrence inside the configured lead time. */
final class UpcomingAlarm {
    static final int NOTIFICATION_ID=3;
    /** Default lead time used by tests when preferences are untouched (12 hours). */
    static final long WINDOW=12*60*60*1000L;
    static long windowMillis(Context c){return new ClockSettings(c).upcomingWindowMillis();}
    static JSONObject earliest(ClockStore store,long now) {
        return store.objects("alarms").stream().filter(a->a.optBoolean("enabled")&&a.optLong("next")>now)
                .min(Comparator.<JSONObject>comparingLong(a->a.optLong("next")).thenComparingInt(a->a.optInt("id"))).orElse(null);
    }
    static PendingIntent refreshIntent(Context c) {
        return PendingIntent.getBroadcast(c,NOTIFICATION_ID,new Intent(c,ClockReceiver.class).setAction("REFRESH_UPCOMING"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static PendingIntent action(Context c,JSONObject a,String action) {
        int id=a.optInt("id");long due=a.optLong("next");
        Intent i=new Intent(c,ClockReceiver.class).setAction(action).setData(Uri.parse("clockprime://upcoming/"+id+"/"+due+"/"+action))
                .putExtra("kind","alarms").putExtra("id",id).putExtra("expectedNext",due);
        return PendingIntent.getBroadcast(c,ClockStore.requestCode("upcoming",id,action),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static void refresh(Context c,ClockStore store,long now) {
        NotificationManager nm=c.getSystemService(NotificationManager.class);AlarmManager am=c.getSystemService(AlarmManager.class);
        nm.deleteNotificationChannel("upcoming");
        NotificationChannel channel=RingService.quiet(RingService.UPCOMING_CHANNEL,c.getString(R.string.upcoming_channel));
        channel.setDescription(c.getString(R.string.upcoming_description));nm.createNotificationChannel(channel);
        PendingIntent boundary=refreshIntent(c);am.cancel(boundary);
        long window=windowMillis(c);
        if(window<=0){nm.cancel(NOTIFICATION_ID);return;}
        JSONObject a=earliest(store,now);
        if(a==null){nm.cancel(NOTIFICATION_ID);return;}
        long due=a.optLong("next");
        if(due-now>=window){
            nm.cancel(NOTIFICATION_ID);
            if(am.canScheduleExactAlarms()){
                // Same alarm-clock path as real alarms for consistent Doze delivery.
                PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(due-window+1,open),boundary);
            }
            return;
        }
        String time=LocaleText.dateTime(c,due);
        String group=a.optString("group");String label=a.optString("name",c.getString(R.string.alarm))+(group.isEmpty()?"":" · "+group);
        java.util.List<String> problems=AlarmHealth.problems(c);
        if(!problems.isEmpty())label="⚠ "+c.getString(AlarmHealth.title(problems.get(0)))+" · "+label;
        Notification.Builder builder=new Notification.Builder(c,RingService.UPCOMING_CHANNEL).setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(c.getString(R.string.next_alarm,time)).setContentText(label).setShowWhen(false)
                .setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setOnlyAlertOnce(true)
                .setContentIntent(store.open()).setVisibility(new ClockSettings(c).flag("lockscreen",false)?Notification.VISIBILITY_PUBLIC:Notification.VISIBILITY_SECRET)
                .addAction(0,c.getString(R.string.skip_once),action(c,a,"SKIP_UPCOMING"));
        if(a.optInt("days")!=0)builder.addAction(0,c.getString(R.string.turn_off),action(c,a,"OFF_UPCOMING"));
        nm.notify(NOTIFICATION_ID,builder.build());
    }
}
