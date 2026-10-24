package com.clockprime.app;

import android.app.*;
import android.os.SystemClock;
import android.view.View;
import org.json.JSONObject;
import java.util.*;

/** 0.7 behavior regression coverage; restores app preferences and records. */
final class RevisionChecks {
    static void run(SmokeTests t){
        ClockSettings prefs=new ClockSettings(t.context);Map<String,?> backup=new HashMap<>(prefs.prefs.getAll());MainActivity a=t.openActivity();t.waitForIdleSync();
        try{
            t.main(()->{
                prefs.put("timer_volume",80);prefs.put("timer_ascending",true);prefs.put("alarm_ascending",true);
                for(int seconds:new int[]{10,30,60,120}){prefs.put("timer_ramp",seconds);t.check(Math.abs(prefs.gain("timers",0)-.08f)<.001&&Math.abs(prefs.gain("timers",seconds*500L)-.44f)<.001&&Math.abs(prefs.gain("timers",seconds*1000L)-.8f)<.001,"Volume ramp start midpoint target: "+seconds+" seconds");}
                prefs.put("alarm_ramp",30);t.check(Math.abs(prefs.gain("alarms",0)-.1f)<.001&&Math.abs(prefs.gain("alarms",30000)-1f)<.001,"Alarm ramp rises to Android's alarm volume");
                prefs.put("timer_volume",0);t.check(prefs.gain("timers",60000)==0,"Gradual volume preserves zero target");prefs.put("timer_ascending",false);
                prefs.put("snooze_minutes",5);prefs.put("snooze_limit",3);JSONObject alarm=new JSONObject();t.check(prefs.snoozeMinutes(alarm)==5&&prefs.canSnooze(alarm),"Legacy alarm inherits snooze defaults");ClockStore.put(alarm,"snoozeMinutes",12);ClockStore.put(alarm,"snoozeLimit",5);ClockStore.put(alarm,"snoozeCount",3);t.check(prefs.snoozeMinutes(alarm)==12&&prefs.canSnooze(alarm),"Per-alarm snooze overrides defaults");ClockStore.put(alarm,"snoozeCount",5);t.check(!prefs.canSnooze(alarm),"Per-alarm snooze limit is enforced");ClockStore.put(alarm,"snoozeLimit",0);t.check(prefs.canSnooze(alarm),"Forever snooze ignores count");
                prefs.put("alarm_tone","silent");t.check(prefs.tone("alarms",alarm)==null,"Default ringtone inherits app silence");ClockStore.put(alarm,"tone","default");t.check(prefs.tone("alarms",alarm)!=null,"Individual system tone overrides silent default");ClockStore.put(alarm,"tone","silent");t.check(prefs.tone("alarms",alarm)==null,"Individual silent ringtone resolves to silence");
                ClockStore s=new ClockStore(a);
                JSONObject done=once(t,s,"delete dismissed");int id=done.optInt("id");s.dismiss("alarms",done);t.check(s.find("alarms",id)==null,"Auto-delete removes dismissed one-time alarm");
                JSONObject skipped=once(t,s,"delete skipped");id=skipped.optInt("id");ClockStore.put(skipped,"enabled",true);ClockStore.put(skipped,"next",System.currentTimeMillis()+300000);s.skipOnce(skipped);t.check(s.find("alarms",id)==null,"Auto-delete removes skipped one-time alarm");
                JSONObject disabled=once(t,s,"keep disabled");id=disabled.optInt("id");s.enable(disabled,false);t.check(s.find("alarms",id)!=null,"Manual disable keeps auto-delete alarm");s.remove("alarms",disabled);
                JSONObject snoozed=once(t,s,"keep snoozed");id=snoozed.optInt("id");s.snooze(snoozed,5);t.check(s.find("alarms",id)!=null&&snoozed.optBoolean("snoozed"),"Auto-delete retains snoozing alarm");s.remove("alarms",snoozed);
                JSONObject expired=once(t,s,"delete expired");id=expired.optInt("id");ClockStore.put(expired,"ringing",true);ClockStore.put(expired,"ringStartedElapsed",SystemClock.elapsedRealtime()-61000);prefs.put("alarm_silence",1);RingPolicy.expire(s,prefs,SystemClock.elapsedRealtime());t.check(s.find("alarms",id)==null,"Auto-silence removes completed one-time alarm");
                JSONObject missed=once(t,s,"delete missed");id=missed.optInt("id");ClockStore.put(missed,"enabled",true);ClockStore.put(missed,"next",System.currentTimeMillis()-60000);s.restore(true,false);t.check(s.find("alarms",id)==null,"Reboot recovery removes missed auto-delete alarm");
                JSONObject multi=t.testAlarm(s,"QA skips","multiple skips");ClockStore.put(multi,"days",127);s.enable(multi,true);long original=multi.optLong("next");
                for(int i=0;i<12;i++)s.skipOnce(multi);
                long advanced=multi.optLong("next");t.check(advanced>original+10*86400000L,"Repeated skips advance beyond a week");
                t.check(ClockStore.next(multi,System.currentTimeMillis())==advanced,"Schedule recalculation preserves every consecutive skip");
                s.save();ClockStore reloaded=new ClockStore(a);JSONObject persisted=reloaded.find("alarms",multi.optInt("id"));t.check(ClockStore.next(persisted,System.currentTimeMillis())==advanced,"Multiple skips persist across store reload");
                s.restoreSkips(multi);t.check(multi.optLong("next")==original&&multi.optString("skipDate").isEmpty(),"Restore clears all skips and restores normal next occurrence");
                s.enable(multi,false);s.restoreSkips(multi);t.check(!multi.optBoolean("enabled"),"Restore skips does not enable manually disabled alarms");s.remove("alarms",multi);
                JSONObject single=t.testAlarm(s,"QA skips","restore once");ClockStore.put(single,"days",0);s.enable(single,true);long onceNext=single.optLong("next");s.skipOnce(single);t.check(!single.optBoolean("enabled")&&single.optBoolean("skipDisabled"),"Non-deleting one-time skip records restorable state");s.restoreSkips(single);t.check(single.optBoolean("enabled")&&single.optLong("next")==onceNext,"Restore recovers skipped one-time alarm");s.remove("alarms",single);
                s.save();a.render();a.alarmEditor(null,null);t.check(a.editingOptions.defaults.isChecked(),"New alarm follows global snooze defaults");t.check(!a.editingOptions.autoDelete.isChecked(),"New alarm does not auto-delete by default");
                a.editingOptions.repeat.setSelection(1);
            });t.waitForIdleSync();
            t.main(()->{t.check(a.editingOptions.mask()==127&&a.editingOptions.autoDelete.getVisibility()==View.GONE,"Daily preset selects all days and hides auto-delete");a.editingOptions.repeat.setSelection(2);});t.waitForIdleSync();
            t.main(()->{t.check(a.editingOptions.mask()==31,"Weekday preset uses Monday through Friday");/* New alarms copy the last saved alarm's days; start Custom from none. */for(android.widget.ToggleButton d:a.editingOptions.days)d.setChecked(false);a.editingOptions.repeat.setSelection(3);});t.waitForIdleSync();
            t.main(()->{boolean rejected=false;try{a.editingOptions.validate();}catch(IllegalArgumentException e){rejected=true;}t.check(rejected,"Custom repeat rejects an empty day selection");a.editingOptions.days[6].setChecked(true);a.editingOptions.validate();t.check(a.editingOptions.mask()==64,"Custom repeat retains chosen weekday");a.editingTime.wheels[0].beginEditing();});t.waitForIdleSync();t.sendStringSync("09");t.waitForIdleSync();SystemClock.sleep(150);
            t.main(()->{t.check(a.editingTime.wheels[0].selected()==9&&a.editingTime.wheels[1].editing,"Two typed hour digits advance to minutes");a.alarmDialog.dismiss();a.tab="Timers";a.render();a.timerInput.wheels[1].beginEditing();});t.waitForIdleSync();t.sendStringSync("17");t.waitForIdleSync();SystemClock.sleep(150);
            t.main(()->{t.check(a.timerInput.wheels[1].selected()==17&&a.timerInput.wheels[2].editing,"Timer minute digits advance to seconds");a.timerInput.wheels[2].finishEditing();a.timerInput.wheels[2].hideKeyboard();a.tab="Alarms";a.render();ClockStore s=a.store;JSONObject member=t.testAlarm(s,"QA toggle all","selection");s.save();a.render();a.selectAlarm("QA toggle all",member.optInt("id"));t.check(a.allSelected(),"Single selected member counts as all selected");a.appHeader.getChildAt(a.appHeader.getChildCount()-1).performClick();t.check(a.selection.ids.isEmpty()&&a.selection.group!=null,"Top action deselects all without exiting selection");a.appHeader.getChildAt(a.appHeader.getChildCount()-1).performClick();t.check(a.selection.ids.size()==1,"Top action selects all again");a.onBackPressed();t.check(a.selection.group==null&&!a.isFinishing(),"Back cancels selection without closing activity");a.groupMutate("QA toggle all",8,0);t.check(new ClockStore(a).find("alarms",member.optInt("id"))==null,"Deleting group deletes member alarms");});
            final int[] notice={0};t.main(()->{ClockStore s=new ClockStore(a);JSONObject once=once(t,s,"notification");notice[0]=once.optInt("id");ClockStore.put(once,"enabled",true);ClockStore.put(once,"next",System.currentTimeMillis()+60000);s.save();});t.waitForIdleSync();SystemClock.sleep(400);
            t.main(()->{Notification n=t.upcoming();t.check(n!=null&&n.actions.length==1&&!n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),"One-time upcoming notification has only Skip and no countdown");try{n.actions[0].actionIntent.send();}catch(Exception e){throw new AssertionError(e);}});t.waitFor(()->new ClockStore(a).find("alarms",notice[0])==null);t.main(()->t.check(new ClockStore(a).find("alarms",notice[0])==null,"Notification Skip completes auto-delete alarm"));
            t.main(()->{
                ClockStore s=new ClockStore(a);
                t.check(s.data.optInt("schema")>=1,"Saved state includes schema version");
                t.check(ClockStore.requestCode("alarms",42,"FIRE")!=ClockStore.requestCode("alarms",42,"DISMISS"),"PendingIntent request codes differ by action");
                t.check(ClockStore.requestCode("alarms",42,"FIRE")!=ClockStore.requestCode("upcoming",42,"SKIP_UPCOMING"),"PendingIntent request codes differ by kind");
                JSONObject ring=t.testAlarm(s,"QA revision","sticky ring");ClockStore.put(ring,"days",0);ClockStore.put(ring,"enabled",false);ClockStore.put(ring,"next",0);ClockStore.put(ring,"ringing",true);ClockStore.put(ring,"ringStartedElapsed",SystemClock.elapsedRealtime());ClockStore.put(ring,"sound",false);ClockStore.put(ring,"vibrate",false);s.save(false);
                a.startForegroundService(new android.content.Intent(a,RingService.class));
            });SystemClock.sleep(800);t.waitForIdleSync();
            t.main(()->{
                t.check(Arrays.stream(a.getSystemService(NotificationManager.class).getActiveNotifications()).anyMatch(n->n.getId()==1),"Sticky RingService recovers notification from persisted ringing");
                ClockStore s=new ClockStore(a);JSONObject ring=s.objects("alarms").stream().filter(o->o.optString("name").contains("sticky ring")).findFirst().orElseThrow();
                ClockStore.put(s.data,"swRunning",true);ClockStore.put(s.data,"swStart",SystemClock.elapsedRealtime());s.save(false);
                ClockStore.markBootSeen(a);s.restore(false,false);
                t.check(ring.optBoolean("ringing")&&s.data.optBoolean("swRunning"),"Synthetic boot restore preserves ringing and stopwatch");
                s.restore(true,false);
                t.check(!ring.optBoolean("ringing")&&!s.data.optBoolean("swRunning"),"Real reboot restore clears ringing and stopwatch");
                JSONObject leftover=s.find("alarms",ring.optInt("id"));if(leftover!=null)s.remove("alarms",leftover);s.save(false);s.refreshSound();
            });
        }finally{t.main(()->{if(a.alarmDialog!=null)a.alarmDialog.dismiss();a.finish();android.content.SharedPreferences.Editor e=prefs.prefs.edit().clear();for(Map.Entry<String,?> v:backup.entrySet()){Object x=v.getValue();if(x instanceof Integer n)e.putInt(v.getKey(),n);else if(x instanceof Boolean b)e.putBoolean(v.getKey(),b);else if(x instanceof String s)e.putString(v.getKey(),s);}e.commit();});}
    }
    static JSONObject once(SmokeTests t,ClockStore s,String name){JSONObject a=t.testAlarm(s,"QA revision",name);ClockStore.put(a,"days",0);ClockStore.put(a,"autoDelete",true);return a;}
}
