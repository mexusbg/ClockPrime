package com.clockprime.app;

import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.Notification;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import org.json.JSONObject;
import java.time.*;
import java.util.*;

/** Device integration tests: real AlarmManager delivery, no mocked receivers. */
public class SmokeTests extends Instrumentation {
    Context context;String backup;int leadBackup=-2;int alarmId,timerId;List<String> results=new ArrayList<>();Set<Integer> featureIds=new HashSet<>();boolean passed,designOnly;
    String qaAlarmIn,qaTimerIn;
    @Override public void onCreate(Bundle args){super.onCreate(args);designOnly="true".equals(args.getString("design_only"));qaAlarmIn=args.getString("qa_alarm_in");qaTimerIn=args.getString("qa_timer_in");start();}
    /** Manual reboot QA (-e qa_alarm_in <seconds>): schedules one real one-time alarm and exits without running checks. */
    void scheduleQaAlarm(){
        long when=System.currentTimeMillis()+Long.parseLong(qaAlarmIn)*1000L;LocalTime at=Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()).toLocalTime();
        main(()->{ClockStore s=new ClockStore(getTargetContext());JSONObject a=new JSONObject();ClockStore.put(a,"id",s.id());ClockStore.put(a,"name","ClockPrime QA reboot");ClockStore.put(a,"hour",at.getHour());ClockStore.put(a,"minute",at.getMinute());ClockStore.put(a,"days",0);ClockStore.put(a,"enabled",true);ClockStore.put(a,"sound",true);ClockStore.put(a,"vibrate",false);ClockStore.put(a,"next",when);s.list("alarms").put(a);s.schedule("alarms",a);
            if(qaTimerIn!=null){JSONObject q=new JSONObject();long d=Long.parseLong(qaTimerIn)*1000L;ClockStore.put(q,"id",s.id());ClockStore.put(q,"name","ClockPrime QA tea");ClockStore.put(q,"duration",d);ClockStore.put(q,"sound",false);ClockStore.put(q,"vibrate",false);s.list("timers").put(q);s.timerStart(q,d);}
            s.save();});
        Bundle out=new Bundle();out.putString("stream","QA alarm at "+at+"\n");finish(-1,out);
    }
    /** Puts back the upcoming lead time changed by the upcoming-notification checks (-1: was unset). */
    void restoreLead(){if(leadBackup==-2)return;ClockSettings lead=new ClockSettings(context);if(leadBackup<0)lead.prefs.edit().remove("upcoming_minutes").commit();else lead.put("upcoming_minutes",leadBackup);leadBackup=-2;}
    void check(boolean value,String message){if(!value)throw new AssertionError(message);results.add("PASS "+message);}
    void main(Runnable block){java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<>();runOnMainSync(()->{try{block.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError(error.get());}
    @Override public void onStart(){
        if(qaAlarmIn!=null){scheduleQaAlarm();return;}
        MainActivity.launchPrompt=false;context=getTargetContext();main(()->{ClockStore s=new ClockStore(context);for(String k:List.of("alarms","timers"))for(JSONObject o:s.objects(k))if(o.optString("name").startsWith("ClockPrime QA "))s.remove(k,o);s.save();s.refreshSound();});backup=new ClockStore(context).data.toString();Bundle report=new Bundle();
        try{
            if(!designOnly){main(()->{
                ClockStore s=new ClockStore(context);JSONObject a=new JSONObject();alarmId=s.id();ClockStore.put(a,"id",alarmId);ClockStore.put(a,"name","ClockPrime QA alarm");ClockStore.put(a,"hour",8);ClockStore.put(a,"minute",0);ClockStore.put(a,"days",31);ClockStore.put(a,"sound",false);ClockStore.put(a,"vibrate",false);
                ZoneId z=ZoneId.systemDefault();long friday=LocalDate.of(2026,10,16).atTime(9,0).atZone(z).toInstant().toEpochMilli();check(Instant.ofEpochMilli(ClockStore.next(a,friday)).atZone(z).getDayOfWeek()==DayOfWeek.MONDAY,"Weekday alarm skips weekend");
                ClockStore.put(a,"days",0);check(ClockStore.next(a,friday)==LocalDate.of(2026,10,17).atTime(8,0).atZone(z).toInstant().toEpochMilli(),"One-time alarm rolls to tomorrow");
                ClockStore.put(a,"enabled",true);ClockStore.put(a,"next",System.currentTimeMillis()+4500);s.list("alarms").put(a);s.schedule("alarms",a);s.save();check(new ClockStore(context).find("alarms",alarmId)!=null,"Alarm saved persistently");
            });
            // Real AlarmManager delivery can lag on a busy (just-booted) device: wait for the ring, up to 30 s.
            SystemClock.sleep(6500);for(int wait=0;wait<47&&Arrays.stream(context.getSystemService(NotificationManager.class).getActiveNotifications()).noneMatch(n->n.getId()==1);wait++)SystemClock.sleep(500);
            main(()->{ClockStore s=new ClockStore(context);JSONObject a=s.find("alarms",alarmId);check(a.optBoolean("ringing"),"Real exact alarm delivered in background");check(!a.optBoolean("enabled"),"One-time alarm deactivates after firing");check(Arrays.stream(context.getSystemService(NotificationManager.class).getActiveNotifications()).anyMatch(n->n.getId()==1),"Ringing notification posted");s.snooze(a,5);s.save();s.refreshSound();check(a.optBoolean("snoozed")&&a.optLong("next")>System.currentTimeMillis()+290000,"Snooze schedules next occurrence");s.enable(a,false);s.save();s.refreshSound();check(!a.optBoolean("snoozed")&&!a.optBoolean("enabled"),"Disable cancels snooze");
                JSONObject t=new JSONObject();timerId=s.id();ClockStore.put(t,"id",timerId);ClockStore.put(t,"name","ClockPrime QA timer");ClockStore.put(t,"duration",4500);ClockStore.put(t,"sound",false);ClockStore.put(t,"vibrate",false);s.list("timers").put(t);s.timerStart(t,4500);s.save();});
            SystemClock.sleep(1000);
            main(()->{ClockStore s=new ClockStore(context);JSONObject t=s.find("timers",timerId);long remaining=ClockStore.remaining(t);check(remaining>0&&remaining<4000,"Timer uses elapsed time");ClockStore.put(t,"remaining",remaining);ClockStore.put(t,"running",false);s.schedule("timers",t);s.save();});
            long paused=ClockStore.remaining(new ClockStore(context).find("timers",timerId));SystemClock.sleep(700);check(ClockStore.remaining(new ClockStore(context).find("timers",timerId))==paused,"Paused timer remains stable");
            main(()->{ClockStore s=new ClockStore(context);JSONObject t=s.find("timers",timerId);s.timerStart(t,t.optLong("remaining"));s.save();});SystemClock.sleep(5000);
            main(()->{ClockStore s=new ClockStore(context);JSONObject t=s.find("timers",timerId);check(t.optBoolean("ringing")&&!t.optBoolean("running"),"Resumed timer delivers real expiry");s.dismiss("timers",t);s.save();s.refreshSound();check(t.optLong("remaining")==4500&&!t.optBoolean("ringing"),"Dismiss restores timer duration");});
            featureTests();interfaceTests();localeTests();settingsTests();}DesignChecks.run(this);RevisionChecks.run(this);FixChecks.run(this);HealthChecks.run(this);BootChecks.run(this);WidgetChecks.run(this);passed=true;
            report.putString("stream",String.join("\n",results)+"\nALL "+results.size()+" CHECKS PASSED\n");
        }catch(Throwable error){report.putString("stream",String.join("\n",results)+"\nFAIL "+android.util.Log.getStackTraceString(error));}
        finally{main(()->{restoreLead();ClockStore s=new ClockStore(context);JSONObject a=s.find("alarms",alarmId),t=s.find("timers",timerId);if(a!=null)s.remove("alarms",a);if(t!=null)s.remove("timers",t);for(int id:featureIds){JSONObject f=s.find("alarms",id);if(f!=null)s.remove("alarms",f);}s.save();s.refreshSound();s.prefs().edit().putString("state",backup).commit();new ClockStore(context).restore(false,false);});}
        finish(passed?-1:0,report);
    }
    JSONObject testAlarm(ClockStore s,String group,String name){JSONObject a=new JSONObject();int id=s.id();featureIds.add(id);ClockStore.put(a,"id",id);ClockStore.put(a,"name","ClockPrime QA "+name);ClockStore.put(a,"group",group);ClockStore.put(a,"hour",8);ClockStore.put(a,"minute",0);ClockStore.put(a,"days",127);ClockStore.put(a,"sound",false);ClockStore.put(a,"vibrate",false);ClockStore.put(a,"enabled",false);s.list("alarms").put(a);return a;}
    Notification upcoming(){return Arrays.stream(context.getSystemService(NotificationManager.class).getActiveNotifications()).filter(n->n.getId()==UpcomingAlarm.NOTIFICATION_ID).map(n->n.getNotification()).findFirst().orElse(null);}
    void featureTests(){
        final int[] ids=new int[3];
        main(()->{
            ClockStore s=new ClockStore(context);JSONObject a=testAlarm(s,"QA A","first"),b=testAlarm(s,"QA A","second"),u=testAlarm(s,"","ungrouped");ids[0]=a.optInt("id");ids[1]=b.optInt("id");ids[2]=u.optInt("id");
            AlarmSelection selected=new AlarmSelection();selected.toggle("QA A",ids[0]);check(!selected.toggle("QA B",999)&&!selected.toggle("",ids[2])&&selected.ids.size()==1,"Selection rejects other groups and Ungrouped");selected.toggle("QA A",ids[0]);check(!selected.accepts(""),"Empty selection stays scoped until Done");selected.clear();check(selected.toggle("",ids[2])&&!selected.toggle("QA A",ids[0]),"Ungrouped selection rejects grouped alarms");
            boolean rejected=false;try{s.bulk("QA A",Set.of(ids[0],ids[2]),"ON");}catch(IllegalArgumentException ex){rejected=true;}check(rejected&&!a.optBoolean("enabled")&&!u.optBoolean("enabled"),"Mixed bulk action rejected before any mutation");
            s.bulk("QA A",Set.of(ids[0],ids[1]),"ON");check(a.optBoolean("enabled")&&b.optBoolean("enabled")&&!u.optBoolean("enabled"),"Bulk enable affects selected group only");
            long moved=a.optLong("next")+30*60000;ClockStore.put(a,"next",moved);ClockStore.put(a,"postponed",true);s.bulk("QA A",Set.of(ids[0]),"ON");check(a.optLong("next")==moved&&a.optBoolean("postponed"),"Enable keeps already-enabled postponements");
            s.bulk("QA A",Set.of(ids[1]),"OFF");check(a.optBoolean("enabled")&&!b.optBoolean("enabled"),"Bulk off leaves unselected alarms enabled");
            String skipped=Instant.ofEpochMilli(moved).atZone(ZoneId.systemDefault()).toLocalDate().toString();s.bulk("QA A",Set.of(ids[0],ids[1]),"SKIP");check(a.optBoolean("enabled")&&a.optString("skipDate").equals(skipped)&&a.optLong("next")>moved&&!b.optBoolean("enabled"),"Skip day advances repeats and leaves disabled alarms off");
            s.collapse("QA A",true);s.collapse("",true);s.save();ClockStore reloaded=new ClockStore(context);check(reloaded.collapsed("QA A")&&!reloaded.collapsed(""),"Named groups fold but ungrouped alarms never fold");
            s.restore(false,true);a=s.find("alarms",ids[0]);check(!Instant.ofEpochMilli(a.optLong("next")).atZone(ZoneId.systemDefault()).toLocalDate().toString().equals(skipped),"Skipped day survives time-change rescheduling");
            ClockStore.put(b,"days",0);s.enable(b,true);s.skipOnce(b);check(!b.optBoolean("enabled")&&b.optLong("next")==0,"Skipping a one-time alarm turns it off");
            // A 1-minute lead time keeps the phone's own alarms (hours away) out of the threshold check.
            ClockSettings lead=new ClockSettings(context);leadBackup=lead.number("upcoming_minutes",-1);lead.put("upcoming_minutes",1);
            s.enable(a,false);long now=System.currentTimeMillis();ClockStore.put(a,"enabled",true);ClockStore.put(a,"next",now+60000+6500);s.schedule("alarms",a);s.save();check(upcoming()==null,"No upcoming notification outside the lead time");
        });
        SystemClock.sleep(8200);
        main(()->{Notification n=upcoming();check(n!=null,"Notification appears when the lead-time threshold is crossed with app closed");check((n.flags&Notification.FLAG_ONGOING_EVENT)!=0&&!n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),"Upcoming notification is ongoing with a static time");
            // 30 minutes covers the 10- and 20-minute QA alarms but not the phone's own alarms.
            new ClockSettings(context).put("upcoming_minutes",30);
            ClockStore s=new ClockStore(context);JSONObject a=s.find("alarms",ids[0]),b=s.find("alarms",ids[1]);ClockStore.put(a,"days",127);ClockStore.put(a,"next",System.currentTimeMillis()+600000);ClockStore.put(a,"enabled",true);ClockStore.put(b,"days",127);ClockStore.put(b,"next",System.currentTimeMillis()+1200000);ClockStore.put(b,"enabled",true);s.schedule("alarms",a);s.schedule("alarms",b);s.save();
        });
        waitForIdleSync();SystemClock.sleep(500);
        final android.app.PendingIntent[] stale=new android.app.PendingIntent[1];
        main(()->{Notification n=upcoming();check(n.extras.getString(Notification.EXTRA_TEXT).contains("first")&&n.actions.length==2,"Earliest alarm chosen and both actions available");stale[0]=n.actions[1].actionIntent;try{n.actions[0].actionIntent.send();}catch(Exception e){throw new AssertionError(e);}});SystemClock.sleep(600);waitForIdleSync();
        main(()->{ClockStore s=new ClockStore(context);check(s.find("alarms",ids[0]).optBoolean("enabled")&&upcoming().extras.getString(Notification.EXTRA_TEXT).contains("second"),"Notification Skip advances to next eligible alarm");try{stale[0].send();}catch(Exception e){throw new AssertionError(e);}});SystemClock.sleep(400);waitForIdleSync();
        main(()->{ClockStore s=new ClockStore(context);check(s.find("alarms",ids[0]).optBoolean("enabled"),"Stale notification action cannot disable a newer occurrence");/* The skipped 08:00 alarm can be within 12 hours after 20:00. Remove it before testing the last eligible notification. */s.enable(s.find("alarms",ids[0]),false);s.save();try{upcoming().actions[1].actionIntent.send();}catch(Exception e){throw new AssertionError(e);}});SystemClock.sleep(400);waitForIdleSync();
        main(()->{ClockStore s=new ClockStore(context);check(!s.find("alarms",ids[1]).optBoolean("enabled"),"Notification Turn off disables its alarm");check(upcoming()==null,"Notification disappears when no alarm qualifies");s.bulk("QA A",Set.of(ids[0],ids[1]),"DELETE");check(s.find("alarms",ids[0])==null&&s.find("alarms",ids[1])==null&&s.find("alarms",ids[2])!=null,"Bulk delete preserves Ungrouped alarms");s.remove("alarms",s.find("alarms",ids[2]));restoreLead();s.save();});
    }
    MainActivity openActivity(){return (MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    void interfaceTests(){
        String originalTheme=ThemeChoice.get(context);MainActivity[] activity={null};final List<Integer> rows=new ArrayList<>();
        try{
            main(()->{ClockStore s=new ClockStore(context);s.list("groups").put("QA scrolling");s.collapse("QA scrolling",false);for(int i=0;i<20;i++)rows.add(testAlarm(s,"QA scrolling","row "+i).optInt("id"));s.save();ThemeChoice.set(context,"light");});
            activity[0]=openActivity();waitForIdleSync();SystemClock.sleep(400);
            main(()->{
                MainActivity a=activity[0];check(!a.dark,"Light override applies to activity palette");
                a.contentScroll.scrollTo(0,a.alarmViews.get(rows.get(10)).card.getTop());
            });waitForIdleSync();SystemClock.sleep(300);
            final int[] offset={0};final android.view.View[] card={null};
            main(()->{MainActivity a=activity[0];offset[0]=a.contentScroll.getScrollY();check(offset[0]>0,"Selection regression starts with list scrolled down");card[0]=a.alarmViews.get(rows.get(10)).card;card[0].performLongClick();});waitForIdleSync();SystemClock.sleep(300);
            main(()->{MainActivity a=activity[0];check(a.contentScroll.getScrollY()==offset[0],"Long press preserves exact scroll position");check(a.alarmViews.get(rows.get(10)).card==card[0],"Selection updates existing rows without rebuilding list");check(a.bottomBar.getChildCount()==4&&a.bottomBar.getParent()==a.root,"Four selection actions stay outside scrolling content");a.alarmViews.get(rows.get(11)).check.performClick();});waitForIdleSync();
            main(()->{MainActivity a=activity[0];check(a.selection.ids.size()==2&&a.contentScroll.getScrollY()==offset[0],"Selecting another alarm keeps scroll position");a.selection.clear();a.updateSelectionUi();});waitForIdleSync();
            main(()->{check(activity[0].contentScroll.getScrollY()==offset[0],"Leaving selection keeps scroll position");activity[0].finish();ThemeChoice.set(context,"dark");});waitForIdleSync();activity[0]=openActivity();waitForIdleSync();
            main(()->{check(activity[0].dark,"Dark override applies to activity palette");check(ThemeChoice.get(context).equals("dark"),"Theme override persists across activity launches");activity[0].finish();ThemeChoice.set(context,"system");});waitForIdleSync();activity[0]=openActivity();waitForIdleSync();
            main(()->{boolean systemDark=(context.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;check(activity[0].dark==systemDark,"Follow system matches device appearance");check(ThemeChoice.resolve("system",true)&&!ThemeChoice.resolve("system",false)&&ThemeChoice.resolve("dark",false)&&!ThemeChoice.resolve("light",true),"System changes and manual overrides resolve correctly");});
        }finally{main(()->{if(activity[0]!=null)activity[0].finish();ThemeChoice.set(context,originalTheme);});}
    }

    void settingsTests(){
        ClockSettings preferences=new ClockSettings(context);Map<String,?> original=new HashMap<>(preferences.prefs.getAll());SettingsActivity[] screen={null};
        try{
            main(()->{
                preferences.put("alarm_tone","silent");preferences.put("timer_tone","default");preferences.put("timer_volume",65);
                ClockSettings reloaded=new ClockSettings(context);check(reloaded.tone("alarms")==null&&reloaded.tone("timers")!=null,"Alarm and timer ringtone choices persist independently");check(reloaded.volume("alarms")==100&&reloaded.volume("timers")==65,"Alarms follow Android volume; timer volume persists");
                preferences.put("timer_ramp",30);preferences.put("timer_ascending",true);check(Math.abs(preferences.gain("timers",0)-.065f)<.001&&Math.abs(preferences.gain("timers",30000)-.65f)<.001,"Ascending volume starts at ten percent and reaches configured volume");check(preferences.gain("timers",15000)<preferences.gain("timers",30000),"Ascending volume increases over elapsed time");
                preferences.put("alarm_vibrate",false);preferences.put("timer_vibrate",true);check(!reloaded.vibrate("alarms")&&reloaded.vibrate("timers"),"Alarm and timer vibration switches are independent");
                ClockStore s=new ClockStore(context);JSONObject a=testAlarm(s,"QA settings","snooze");ClockStore.put(a,"ringing",true);preferences.put("snooze_minutes",10);preferences.put("snooze_limit",1);s.save();long before=System.currentTimeMillis();new ClockReceiver().onReceive(context,new Intent("SNOOZE").putExtra("kind","alarms").putExtra("id",a.optInt("id")));
                ClockStore actual=new ClockStore(context);a=actual.find("alarms",a.optInt("id"));check(a.optLong("next")>=before+600000&&a.optInt("snoozeCount")==1,"Notification snooze uses configured ten-minute duration");check(!preferences.canSnooze(a),"Snooze count limit enforced");long next=a.optLong("next");actual.snooze(a,10);check(a.optLong("next")==next&&a.optInt("snoozeCount")==1,"Exhausted snooze cannot move the alarm again");actual.enable(a,false);check(preferences.canSnooze(a),"Enabling or disabling an alarm resets snooze count");actual.remove("alarms",a);actual.save();actual.refreshSound();
                s=new ClockStore(context);a=testAlarm(s,"QA settings","expired");JSONObject b=testAlarm(s,"QA settings","still ringing");long now=SystemClock.elapsedRealtime();long future=System.currentTimeMillis()+86400000;
                for(JSONObject o:List.of(a,b)){ClockStore.put(o,"ringing",true);ClockStore.put(o,"enabled",true);ClockStore.put(o,"next",future);}ClockStore.put(a,"ringStartedElapsed",now-61000);ClockStore.put(b,"ringStartedElapsed",now-10000);JSONObject t=s.find("timers",timerId);ClockStore.put(t,"ringing",true);ClockStore.put(t,"ringStartedElapsed",now-1000000);preferences.put("alarm_silence",1);preferences.put("timer_silence",0);
                check(RingPolicy.expire(s,preferences,now)&&!a.optBoolean("ringing")&&a.optBoolean("autoSilenced"),"Expired alarm is automatically silenced");check(b.optBoolean("ringing")&&a.optLong("next")==future&&a.optBoolean("enabled"),"Auto-silence preserves other ringing alarms and next repeat");check(t.optBoolean("ringing"),"Never auto-silence leaves timer ringing");s.remove("alarms",a);s.remove("alarms",b);
                preferences.put("timer_silence",1);ClockStore.put(t,"ringStartedElapsed",now-59000);s.save();context.startForegroundService(new Intent(context,RingService.class));
            });SystemClock.sleep(2200);waitForIdleSync();
            main(()->{JSONObject t=new ClockStore(context).find("timers",timerId);check(!t.optBoolean("ringing")&&t.optBoolean("autoSilenced")&&t.optLong("remaining")==t.optLong("duration"),"Real ring service enforces timer silence deadline and resets timer");check(Arrays.stream(context.getSystemService(NotificationManager.class).getActiveNotifications()).noneMatch(n->n.getId()==1),"Auto-silence removes final ringing notification");
                ClockStore s=new ClockStore(context);JSONObject a=testAlarm(s,"QA settings","upcoming");ClockStore.put(a,"enabled",true);ClockStore.put(a,"next",System.currentTimeMillis()+600000);preferences.put("upcoming_minutes",0);s.save();check(upcoming()==null,"Upcoming notification preference hides and cancels it");preferences.put("upcoming_minutes",720);preferences.put("lockscreen",true);UpcomingAlarm.refresh(context,s,System.currentTimeMillis());SystemClock.sleep(300);check(upcoming()!=null&&upcoming().visibility==Notification.VISIBILITY_PUBLIC,"Lock-screen preference exposes upcoming notification content");preferences.put("lockscreen",false);UpcomingAlarm.refresh(context,s,System.currentTimeMillis());SystemClock.sleep(300);check(upcoming().visibility==Notification.VISIBILITY_SECRET,"Lock-screen off hides upcoming notification");s.remove("alarms",a);s.save();
            });
            screen[0]=(SettingsActivity)startActivitySync(new Intent(context,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            main(()->{SettingsActivity a=screen[0];check(a.body.getChildCount()>0&&!a.advanced,"Full-screen settings activity renders");String prior=preferences.text("alarm_tone","default");a.onActivityResult(201,android.app.Activity.RESULT_CANCELED,new Intent());check(preferences.text("alarm_tone","default").equals(prior),"Canceling ringtone picker preserves previous sound");a.onActivityResult(201,android.app.Activity.RESULT_OK,new Intent().putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI,(android.net.Uri)null));check(preferences.tone("alarms")==null,"Silent ringtone picker result persists");preferences.put("timer_volume",1);a.preview("timers");check(a.preview!=null&&Math.abs(a.preview.getVolume()-.01f)<.001,"Sound preview uses configured app volume");check(a.body.getChildCount()>=10,"One page contains all five settings sections");});
            SystemClock.sleep(3300);sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);waitForIdleSync();main(()->{check(screen[0].isFinishing(),"System Back closes the single settings page");check(screen[0].preview==null,"Sound preview stops automatically after three seconds");screen[0].finish();});
            final int[] keyAlarm={0};main(()->{ClockStore s=new ClockStore(context);JSONObject a=testAlarm(s,"QA settings","volume key");keyAlarm[0]=a.optInt("id");ClockStore.put(a,"ringing",true);s.save();preferences.put("volume_buttons","snooze");preferences.put("snooze_minutes",3);});
            MainActivity active=openActivity();waitForIdleSync();main(()->{
                active.onKeyDown(android.view.KeyEvent.KEYCODE_VOLUME_DOWN,new android.view.KeyEvent(0,android.view.KeyEvent.KEYCODE_VOLUME_DOWN));active.onKeyUp(android.view.KeyEvent.KEYCODE_VOLUME_DOWN,new android.view.KeyEvent(1,android.view.KeyEvent.KEYCODE_VOLUME_DOWN));ClockStore s=new ClockStore(context);JSONObject a=s.find("alarms",keyAlarm[0]);check(a.optBoolean("snoozed")&&a.optLong("next")>System.currentTimeMillis()+175000,"Volume key snoozes visible ringing alarm using chosen duration");s.remove("alarms",a);JSONObject t=s.find("timers",timerId);ClockStore.put(t,"ringing",true);s.save();active.onKeyDown(android.view.KeyEvent.KEYCODE_VOLUME_UP,new android.view.KeyEvent(0,android.view.KeyEvent.KEYCODE_VOLUME_UP));active.onKeyUp(android.view.KeyEvent.KEYCODE_VOLUME_UP,new android.view.KeyEvent(1,android.view.KeyEvent.KEYCODE_VOLUME_UP));check(!new ClockStore(context).find("timers",timerId).optBoolean("ringing"),"Snooze volume-key action dismisses a ringing timer");active.finish();
            });
        }finally{main(()->{if(screen[0]!=null)screen[0].finish();android.content.SharedPreferences.Editor edit=preferences.prefs.edit().clear();for(Map.Entry<String,?> e:original.entrySet()){Object value=e.getValue();if(value instanceof String v)edit.putString(e.getKey(),v);else if(value instanceof Integer v)edit.putInt(e.getKey(),v);else if(value instanceof Boolean v)edit.putBoolean(e.getKey(),v);}edit.commit();new ClockStore(context).refreshLocalizedNotifications();});}
    }
    Context language(String tag){android.content.res.Configuration config=new android.content.res.Configuration(context.getResources().getConfiguration());config.setLocales(android.os.LocaleList.forLanguageTags(tag));return context.createConfigurationContext(config);}
    void localeTests(){final int[] savedId={0};final String[] savedAlarm={null};main(()->{
        Context en=language("en-US"),bg=language("bg-BG"),fr=language("fr-FR");
        check(en.getString(R.string.alarms).equals("Alarms"),"English resources resolve");
        check(bg.getString(R.string.alarms).equals("\u0410\u043b\u0430\u0440\u043c\u0438"),"Bulgarian resources resolve");
        check(fr.getString(R.string.alarms).equals("Alarms"),"Unsupported language falls back to English");
        check(en.getResources().getQuantityString(R.plurals.alarm_count,1,1).equals("1 alarm")&&en.getResources().getQuantityString(R.plurals.alarm_count,2,2).equals("2 alarms"),"English singular and plural alarm counts");
        check(!bg.getResources().getQuantityString(R.plurals.alarm_count,1,1).equals(bg.getResources().getQuantityString(R.plurals.alarm_count,2,2)),"Bulgarian count resources format quantities");
        long stamp=LocalDate.of(2026,10,14).atTime(8,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        check(!LocaleText.dateTime(en,stamp).equals(LocaleText.dateTime(bg,stamp)),"Date and weekday formatting follows locale");
        check(!LocaleText.city(bg,"Europe/Sofia").equals(LocaleText.city(en,"Europe/Sofia")),"City names localize while zone IDs remain stable");
        ClockStore s=new ClockStore(context);JSONObject a=testAlarm(s,"QA language","locale label");int id=a.optInt("id");ClockStore.put(a,"enabled",true);ClockStore.put(a,"next",System.currentTimeMillis()+600000);String before=a.toString();s.save();
        savedId[0]=id;savedAlarm[0]=before;UpcomingAlarm.refresh(bg,s,System.currentTimeMillis());
        });waitForIdleSync();SystemClock.sleep(250);main(()->{Context bg=language("bg-BG");ClockStore s=new ClockStore(context);int id=savedId[0];JSONObject a=s.find("alarms",id);String before=savedAlarm[0];Notification n=upcoming();
        check(n!=null&&n.actions[0].title.toString().equals(bg.getString(R.string.skip_once))&&n.actions[1].title.toString().equals(bg.getString(R.string.turn_off)),"Upcoming notification actions translate to Bulgarian");
        check(n.extras.getString(Notification.EXTRA_TITLE).startsWith(bg.getString(R.string.next_alarm,"").trim()),"Upcoming title translates to Bulgarian");
        RingService.channels(bg);check(context.getSystemService(NotificationManager.class).getNotificationChannel("alerts_ring").getName().toString().equals(bg.getString(R.string.ringing_channel)),"Existing notification channels can change language");
        new ClockReceiver().onReceive(context,new Intent(Intent.ACTION_LOCALE_CHANGED));check(new ClockStore(context).find("alarms",id).toString().equals(before),"Locale broadcast preserves alarm name and schedule");
        s.remove("alarms",a);s.save();RingService.channels(context);
    });}

}
