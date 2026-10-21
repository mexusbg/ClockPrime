package com.clockprime.app;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.*;
import org.json.JSONObject;

public class ClockReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        ClockStore s=new ClockStore(context);String action=intent.getAction();
        DebugLog.log(context,"receiver",action+(intent.hasExtra("kind")?" "+intent.getStringExtra("kind")+" "+intent.getIntExtra("id",-1):""));
        if(Intent.ACTION_LOCALE_CHANGED.equals(action)){s.refreshLocalizedNotifications();return;}
        if(Intent.ACTION_USER_UNLOCKED.equals(action)){
            s.restore(false,false);if(s.ringing())s.refreshSound();return;
        }
        if(Widgets.TICK.equals(action)){Widgets.update(context,s);return;}
        if(Widgets.QUICK_TIMER.equals(action)){Widgets.startQuickTimer(context,s,intent.getIntExtra("minutes",5));s.save();return;}
        if(Widgets.GROUP.equals(action)&&intent.hasExtra("group")){Widgets.toggleGroup(s,intent.getStringExtra("group"),intent.getBooleanExtra(android.widget.RemoteViews.EXTRA_CHECKED,true));s.save();s.refreshSound();return;}
        if("REFRESH_UPCOMING".equals(action)){UpcomingAlarm.refresh(context,s,System.currentTimeMillis());return;}
        if(action!=null&&action.startsWith("SW_")){
            boolean running=s.data.optBoolean("swRunning");
            switch(action){case "SW_PAUSE":if(running)s.stopwatchToggle();break;case "SW_RESUME":if(!running)s.stopwatchToggle();break;case "SW_LAP":if(running)s.stopwatchLap();break;case "SW_RESET":if(!running)s.stopwatchReset();break;}
            s.save();s.syncStopwatch();return;
        }
        if(Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)||Intent.ACTION_BOOT_COMPLETED.equals(action)
                ||Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                ||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)){
            // restore() detects a new boot itself, without touching storage that is still locked.
            boolean clockChanged=Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action);
            s.restore(false,clockChanged);
            if(s.ringing())s.refreshSound();
            return;
        }
        String kind=intent.getStringExtra("kind");if(kind==null)return;JSONObject o=s.find(kind,intent.getIntExtra("id",-1));if(o==null)return;
        if("SKIP_UPCOMING".equals(action)||"OFF_UPCOMING".equals(action)){
            // A stale notification must never affect a newer occurrence of this alarm.
            if(o.optBoolean("enabled")&&o.optLong("next")==intent.getLongExtra("expectedNext",-1)&&o.optLong("next")>System.currentTimeMillis()){
                if("SKIP_UPCOMING".equals(action))s.skipOnce(o);else s.enable(o,false);
                s.save();s.refreshSound();
            }else UpcomingAlarm.refresh(context,s,System.currentTimeMillis());
            return;
        }
        switch(action==null?"":action) {
            case "FIRE":fire(context,intent);break;
            case "SNOOZE":if(!o.optBoolean("ringing")||!kind.equals("alarms"))return;s.snooze(o,new ClockSettings(context).snoozeMinutes(o));s.save();s.refreshSound();break;
            case "DISMISS":s.dismiss(kind,o);s.save();s.refreshSound();break;
            case "PAUSE":if(kind.equals("timers")&&o.optBoolean("running")){s.timerPause(o);s.save();}break;
            case "RESUME":if(kind.equals("timers")&&!o.optBoolean("running")&&!o.optBoolean("ringing")){s.timerStart(o,o.optLong("remaining")>0?o.optLong("remaining"):o.optLong("duration"));s.save();}break;
            case "CANCEL":s.dismiss(kind,o);s.save();s.refreshSound();break;
            case Widgets.TOGGLE:if(kind.equals("alarms")){s.enable(o,intent.getBooleanExtra(android.widget.RemoteViews.EXTRA_CHECKED,!o.optBoolean("enabled")));s.save();s.refreshSound();}break;
        }
    }

    static int overlapId(int alarmId){return 0x40000000|alarmId;}
    /** Only one alarm rings: a newer one replaces one still ringing (maybe unheard), so later backup alarms always sound. */
    static void replaceRinging(Context context,ClockStore s,JSONObject active,JSONObject o){
        String stopped=label(context,active),started=label(context,o);int id=active.optInt("id");
        DebugLog.log(context,"fire","alarm "+o.optInt("id")+" replaces ringing alarm "+id);
        s.dismiss("alarms",active);ClockStore.put(active,"autoSilenced",true);active.remove("ringStartedElapsed");
        RingService.channels(context);
        Notification n=new Notification.Builder(context,RingService.SKIPPED_CHANNEL).setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(context.getString(R.string.alarm_replaced_title)).setContentText(context.getString(R.string.alarm_replaced_text,stopped,started))
                .setStyle(new Notification.BigTextStyle().bigText(context.getString(R.string.alarm_replaced_text,stopped,started)))
                .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PUBLIC).setAutoCancel(true).setContentIntent(s.open()).build();
        context.getSystemService(NotificationManager.class).notify(overlapId(id),n);
    }
    static String label(Context context,JSONObject a){
        String time=LocaleText.alarmTime(context,a.optInt("hour"),a.optInt("minute")),name=a.optString("name","").trim();
        return name.isEmpty()?time:time+" · "+name;
    }

    /**
     * Marks the target alarm/timer as ringing and starts playback.
     * Preferred entry is AlarmManager delivering an activity PendingIntent to {@link RingActivity}
     * so the system itself brings the UI up while the screen is unlocked.
     */
    static boolean fire(Context context,Intent intent){
        String kind=intent.getStringExtra("kind");if(kind==null)return false;
        ClockStore s=new ClockStore(context);JSONObject o=s.find(kind,intent.getIntExtra("id",-1));if(o==null){DebugLog.log(context,"fire","missing "+kind+" "+intent.getIntExtra("id",-1));return false;}
        String from=context.getClass().getSimpleName();
        if(kind.equals("alarms")){
            if(o.optBoolean("ringing")){s.refreshSound();return true;}
            if(!o.optBoolean("enabled")||o.optLong("next")>System.currentTimeMillis()+1000){DebugLog.log(context,"fire","ignored alarm "+o.optInt("id")+" via "+from+" enabled="+o.optBoolean("enabled")+" next="+o.optLong("next"));return false;}
            // A ring past its auto-silence limit (e.g. the app was killed while ringing) no longer blocks new alarms.
            if(RingPolicy.expire(s,new ClockSettings(context),android.os.SystemClock.elapsedRealtime()))s.save();
            for(JSONObject active:s.objects("alarms"))if(active.optBoolean("ringing")&&active.optInt("id")!=o.optInt("id"))replaceRinging(context,s,active,o);
            ClockStore.put(o,"missed",false);
            if(!o.optBoolean("snoozed"))ClockStore.put(o,"snoozeCount",0);ClockStore.put(o,"enabled",o.optInt("days")!=0);ClockStore.put(o,"next",o.optInt("days")==0?0:ClockStore.next(o,System.currentTimeMillis()));ClockStore.put(o,"snoozed",false);ClockStore.put(o,"postponed",false);
        }else{
            if(o.optBoolean("ringing")){s.refreshSound();return true;}
            if(!o.optBoolean("running")||ClockStore.remaining(o)>1000){DebugLog.log(context,"fire","ignored timer "+o.optInt("id")+" via "+from+" running="+o.optBoolean("running")+" remaining="+ClockStore.remaining(o));return false;}
            ClockStore.put(o,"running",false);ClockStore.put(o,"remaining",0);
        }
        DebugLog.log(context,"fire",kind+" "+o.optInt("id")+" via "+from+" "+DebugLog.state(context)+" "+AlarmHealth.describe(context));
        ClockStore.put(o,"ringStartedElapsed",android.os.SystemClock.elapsedRealtime());ClockStore.put(o,"ringBoot",ClockStore.bootCount(context));ClockStore.put(o,"autoSilenced",false);ClockStore.put(o,"ringing",true);s.schedule(kind,o);s.save();
        context.startForegroundService(new Intent(context,RingService.class));
        // If we are not already RingActivity (broadcast / timer poll), try to open it.
        if(!(context instanceof RingActivity))RingLaunch.open(context);
        return true;
    }
}
