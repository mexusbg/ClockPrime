package com.clockprime.app;

import android.app.*;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.os.SystemClock;
import org.json.*;
import java.util.*;

/** Built-in tone, audibility warnings, island permission query and alarm backup. */
final class HealthChecks {
    static List<String> texts(android.view.View v){List<String> out=new ArrayList<>();if(v instanceof android.widget.TextView tv)out.add(tv.getText().toString());if(v instanceof android.view.ViewGroup g)for(int i=0;i<g.getChildCount();i++)out.addAll(texts(g.getChildAt(i)));return out;}
    static void shell(SmokeTests t,String command){try(android.os.ParcelFileDescriptor out=t.getUiAutomation().executeShellCommand(command);java.io.InputStream in=new java.io.FileInputStream(out.getFileDescriptor())){in.readAllBytes();}catch(java.io.IOException e){throw new AssertionError(e);}}
    static void run(SmokeTests t){
        t.main(()->{
            t.check(RingtoneManager.getRingtone(t.context,RingService.builtIn(t.context))!=null,"Built-in fallback alarm sound loads");
            t.check(new ClockIcon("back",0).isAutoMirrored()&&new ClockIcon("right",0).isAutoMirrored()&&!new ClockIcon("plus",0).isAutoMirrored(),"Direction arrows mirror in right-to-left languages");
            android.view.View probe=LocaleText.direction(new android.view.View(t.context));t.check(probe.getTextDirection()==android.view.View.TEXT_DIRECTION_LOCALE,"Screens lay out text in the UI language's direction");
            try(android.content.res.AssetFileDescriptor fd=t.context.getResources().openRawResourceFd(R.raw.fallback_alarm)){t.check(fd.getLength()>10000,"Built-in fallback sound is bundled");}catch(java.io.IOException e){throw new AssertionError(e);}
            t.check(FocusIsland.canShowFocus(t.context)==null,"Island permission is unknown on non-HyperOS phones");
        });

        AudioManager audio=t.context.getSystemService(AudioManager.class);int volume=audio.getStreamVolume(AudioManager.STREAM_ALARM);final int[] id={0};
        try{
            t.main(()->{
                audio.setStreamVolume(AudioManager.STREAM_ALARM,audio.getStreamMinVolume(AudioManager.STREAM_ALARM),0);
                t.check(AlarmHealth.problems(t.context).contains(AlarmHealth.VOLUME),"Alarm volume at its minimum is reported");
                ClockStore s=new ClockStore(t.context);JSONObject a=t.testAlarm(s,"","health upcoming");id[0]=a.optInt("id");ClockStore.put(a,"days",0);ClockStore.put(a,"enabled",true);ClockStore.put(a,"next",System.currentTimeMillis()+120000);s.save();
            });SystemClock.sleep(400);
            t.main(()->{Notification n=t.upcoming();t.check(n!=null&&String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TEXT)).startsWith("⚠"),"Upcoming notification warns about muted alarms");});
            t.main(()->{audio.setStreamVolume(AudioManager.STREAM_ALARM,Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)/2),0);t.check(!AlarmHealth.problems(t.context).contains(AlarmHealth.VOLUME),"Audible alarm volume clears the warning");});
        }finally{t.main(()->{audio.setStreamVolume(AudioManager.STREAM_ALARM,volume,0);ClockStore s=new ClockStore(t.context);JSONObject a=s.find("alarms",id[0]);if(a!=null)s.remove("alarms",a);s.save();});}

        NotificationManager nm=t.context.getSystemService(NotificationManager.class);final String[] saved={null};
        t.main(()->{ClockStore s=new ClockStore(t.context);JSONObject keep=new JSONObject();for(String k:List.of("swRunning","swStart","swTotal","laps"))if(s.data.has(k))ClockStore.put(keep,k,s.data.opt(k));saved[0]=keep.toString();
            s.stopwatchReset();s.stopwatchToggle();s.save();s.syncStopwatch();});SystemClock.sleep(500);
        try{
            t.main(()->{Notification n=Arrays.stream(nm.getActiveNotifications()).filter(x->x.getId()==ClockStore.STOPWATCH_ID).map(x->x.getNotification()).findFirst().orElse(null);
                t.check(n!=null&&n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)&&n.actions.length==2&&t.context.getString(R.string.lap).contentEquals(n.actions[1].title),"Running stopwatch shows a ticking notification with Pause and Lap");
                JSONObject run=FocusIsland.stopwatchParam(t.context,"QA",65000,true).optJSONObject("param_v2");JSONObject info=run.optJSONObject("param_island").optJSONObject("bigIslandArea").optJSONObject("sameWidthDigitInfo").optJSONObject("timerInfo");
                t.check(info.optInt("timerType")==1&&Math.abs(info.optLong("timerSystemCurrent")-info.optLong("timerWhen")-65000)<5&&run.optJSONArray("actions").toString().contains(FocusIsland.LAP),"Stopwatch island counts up from the elapsed time with Lap");
                JSONObject paused=FocusIsland.stopwatchParam(t.context,"QA",65000,false).optJSONObject("param_v2");
                t.check(paused.optJSONObject("highlightInfo").optJSONObject("timerInfo").optInt("timerType")==2&&paused.optJSONArray("actions").toString().contains(FocusIsland.RESET),"Paused stopwatch island freezes with Resume and Reset");
                try{n.actions[1].actionIntent.send();}catch(Exception e){throw new AssertionError(e);}});
            t.waitFor(()->{JSONArray l=new ClockStore(t.context).data.optJSONArray("laps");return l!=null&&l.length()==1;});
            // Android drops notification updates posted in quick succession; space the taps like a person would.
            SystemClock.sleep(600);
            t.main(()->{t.check(new ClockStore(t.context).data.optJSONArray("laps").length()==1,"Lap from the notification is recorded");
                Notification n=Arrays.stream(nm.getActiveNotifications()).filter(x->x.getId()==ClockStore.STOPWATCH_ID).map(x->x.getNotification()).findFirst().orElseThrow();
                try{n.actions[0].actionIntent.send();}catch(Exception e){throw new AssertionError(e);}});
            t.waitFor(()->!new ClockStore(t.context).data.optBoolean("swRunning")&&Arrays.stream(nm.getActiveNotifications()).anyMatch(x->x.getId()==ClockStore.STOPWATCH_ID&&t.context.getString(R.string.resume).contentEquals(x.getNotification().actions[0].title)));
            SystemClock.sleep(600);
            t.main(()->{Notification n=Arrays.stream(nm.getActiveNotifications()).filter(x->x.getId()==ClockStore.STOPWATCH_ID).map(x->x.getNotification()).findFirst().orElse(null);
                t.check(!new ClockStore(t.context).data.optBoolean("swRunning")&&n!=null&&t.context.getString(R.string.resume).contentEquals(n.actions[0].title),"Pause from the notification keeps a paused stopwatch notification");
                try{n.actions[1].actionIntent.send();}catch(Exception e){throw new AssertionError(e);}});
            t.waitFor(()->Arrays.stream(nm.getActiveNotifications()).noneMatch(x->x.getId()==ClockStore.STOPWATCH_ID));
            t.main(()->t.check(Arrays.stream(nm.getActiveNotifications()).noneMatch(x->x.getId()==ClockStore.STOPWATCH_ID)&&new ClockStore(t.context).stopwatchTime()==0,"Reset from the notification clears the stopwatch and its notification"));
        }finally{t.main(()->{ClockStore s=new ClockStore(t.context);try{JSONObject keep=new JSONObject(saved[0]);s.stopwatchReset();for(java.util.Iterator<String> k=keep.keys();k.hasNext();){String key=k.next();ClockStore.put(s.data,key,keep.opt(key));}}catch(JSONException e){throw new AssertionError(e);}s.save();s.syncStopwatch();});}

        int original=audio.getStreamVolume(AudioManager.STREAM_ALARM),max=audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),min=audio.getStreamMinVolume(AudioManager.STREAM_ALARM);
        final int upcoming=new ClockSettings(t.context).upcomingMinutes();
        SettingsActivity screen=(SettingsActivity)t.startActivitySync(new android.content.Intent(t.context,SettingsActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));t.waitForIdleSync();
        try{
            t.main(()->{t.check(screen.alarmSlider!=null&&screen.alarmSlider.getMax()==max-min,"Alarm slider spans Android's alarm volume steps");screen.setAlarmVolume(max);t.check(audio.getStreamVolume(AudioManager.STREAM_ALARM)==max,"Alarm slider sets Android's alarm volume");audio.setStreamVolume(AudioManager.STREAM_ALARM,min,0);});
            SystemClock.sleep(800);t.waitForIdleSync();
            t.main(()->t.check(screen.alarmSlider.getProgress()==0,"Alarm slider follows Android volume changes"));
            t.main(()->{List<String> all=texts(screen.body);
                t.check(all.contains(t.context.getString(R.string.about_title))&&all.contains(t.context.getString(R.string.about_license))&&all.contains("github.com/mexusbg/ClockPrime"),"About shows the attribution, GPL notice and source link");
                // Setup checklist: one row per step, reachable from Settings, battery request permission declared.
                t.check(all.contains(t.context.getString(R.string.setup_row)),"Settings offers the setup checklist");
                SetupGuide g=new SetupGuide(screen);g.show();List<SetupGuide.Step> steps=SetupGuide.steps(screen);
                t.check(g.rows.getChildCount()==steps.size()&&steps.stream().anyMatch(x->x.key.equals("overlay"))&&steps.stream().anyMatch(x->x.key.equals("battery"))&&steps.stream().anyMatch(x->x.key.equals("notifications")),"Setup checklist lists notifications, overlay and battery ("+steps.size()+" steps)");
                t.check(g.progress.getText().toString().equals(t.context.getString(R.string.setup_progress,SetupGuide.doneCount(screen),steps.size())),"Setup checklist shows how many steps are done");
                t.check(texts(g.rows.findViewWithTag("setup-notifications")).contains(t.context.getString(R.string.setup_step_done))==t.context.getSystemService(NotificationManager.class).areNotificationsEnabled(),"Notification step reflects the real permission");
                g.dialog.dismiss();
                try{t.check(java.util.Arrays.asList(t.context.getPackageManager().getPackageInfo(t.context.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions).contains(android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS),"Battery request permission is declared, so the battery step can open its dialog");}catch(android.content.pm.PackageManager.NameNotFoundException e){throw new AssertionError(e);}
                android.view.View kofi=screen.body.findViewWithTag("support");t.check(kofi!=null&&kofi.isClickable()&&t.context.getString(R.string.support_kofi).contentEquals(kofi.getContentDescription())&&SettingsActivity.KOFI_URL.equals("https://ko-fi.com/mexus"),"About has the Ko-fi support button");});
            t.main(()->{List<String> shown=texts(screen.body);
                t.check(shown.contains(t.context.getString(R.string.troubleshooting))&&shown.contains(t.context.getString(R.string.edit_system_time)),"Settings groups tools under Troubleshooting and date & time under Appearance");
                t.check(!shown.contains(t.context.getString(R.string.exact_alarms_label))||!t.context.getSystemService(AlarmManager.class).canScheduleExactAlarms(),"Exact alarm row only appears when blocked");
                t.check(shown.contains(t.context.getString(R.string.island_label))==FocusIsland.shouldAttach(t.context),"Island row only appears on HyperOS phones");
                screen.prefs.put("upcoming_minutes",0);screen.render();
                t.check(!texts(screen.body).contains(t.context.getString(R.string.lock_screen_toggle)),"Lock-screen toggle hides when upcoming notification is off");
                for(int m=1;m<=1440;m++){int[] split=ClockSettings.splitMinutes(m);boolean exact=m<60||m%60==0;if(exact&&ClockSettings.toMinutes(split[0],split[1]==1)!=m)throw new AssertionError("Lead time round trip "+m);}
                t.check(true,"Lead time converts both ways for 1-59 minutes and 1-24 hours");
                screen.chooseUpcoming();t.check(!screen.upcomingOn.isChecked()&&!screen.upcomingAmount.isEnabled(),"Off lead time disables the wheels");
                screen.upcomingOn.setChecked(true);screen.upcomingUnit.setValue(0);screen.upcomingAmount.setMaxValue(59);screen.upcomingAmount.setValue(45);screen.upcomingDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();});
            t.waitForIdleSync();
            t.main(()->{t.check(screen.prefs.upcomingMinutes()==45,"Lead time can be 45 minutes");
                screen.chooseUpcoming();t.check(screen.upcomingUnit.getValue()==0&&screen.upcomingAmount.getValue()==45&&screen.upcomingAmount.getMaxValue()==59,"Lead time dialog reopens on minutes");
                screen.upcomingUnit.setValue(1);screen.upcomingAmount.setMaxValue(24);screen.upcomingAmount.setValue(24);screen.upcomingDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();});
            t.waitForIdleSync();
            t.main(()->{t.check(screen.prefs.upcomingMinutes()==1440&&screen.prefs.upcomingWindowMillis()==24*3600000L,"Lead time can be 24 hours");
                screen.chooseUpcoming();t.check(screen.upcomingUnit.getValue()==1&&screen.upcomingAmount.getValue()==24,"Lead time dialog reopens on hours");screen.upcomingDialog.dismiss();
                screen.prefs.put("upcoming_minutes",upcoming);screen.render();t.check(texts(screen.body).contains(t.context.getString(R.string.lock_screen_toggle))==(upcoming>0),"Lock-screen toggle returns with upcoming notification");});
        }finally{t.main(()->{audio.setStreamVolume(AudioManager.STREAM_ALARM,original,0);screen.prefs.put("upcoming_minutes",upcoming);screen.finish();});}

        MainActivity main=t.openActivity();t.waitForIdleSync();
        try{t.main(()->{
            boolean ungrouped=main.store.objects("alarms").stream().anyMatch(x->x.optString("group").isEmpty());
            t.check((main.body.findViewWithTag("next-summary")!=null)==ungrouped,"Overall next-alarm line shows only when some alarms are ungrouped");
            ClockStore s=new ClockStore(t.context);JSONObject loose=t.testAlarm(s,"","summary ungrouped");s.save();main.render();
            t.check(main.body.findViewWithTag("next-summary")!=null,"Overall next-alarm line appears with an ungrouped alarm");
            s=new ClockStore(t.context);s.remove("alarms",s.find("alarms",loose.optInt("id")));s.save();main.render();
            t.check((main.body.findViewWithTag("next-summary")!=null)==ungrouped,"Overall next-alarm line follows ungrouped alarms after removal");
            main.alarmEditor(null,null);AlarmOptions o=main.editingOptions;
            t.check(t.context.getString(R.string.alarm_name).contentEquals(main.editingName.getHint()),"Alarm editor shows the Label field");
            o.sound.setChecked(false);t.check(!o.toneRow.isEnabled()&&o.toneRow.getAlpha()<1f,"Ringtone row dims while sound is off");o.sound.setChecked(true);t.check(o.toneRow.isEnabled(),"Ringtone row returns with sound on");
            t.check(o.toneRow.getParent()==o.sound.getParent().getParent(),"Ringtone sits in the Sound card with the sound switch");
            o.defaults.setChecked(false);o.interval.setSelection(o.durations.indexOf(15));JSONObject draft=new JSONObject();o.write(draft,false);
            t.check(o.interval instanceof android.widget.Spinner&&draft.optInt("snoozeMinutes")==15,"Snooze duration is a dropdown and saves the chosen minutes");
            main.alarmDialog.dismiss();
            JSONObject odd=new JSONObject();ClockStore.put(odd,"snoozeMinutes",12);main.alarmEditor(odd,"");
            t.check(main.editingOptions.durations.contains(12)&&main.editingOptions.durations.get(main.editingOptions.interval.getSelectedItemPosition())==12,"Existing non-standard snooze duration stays selectable");
            main.alarmDialog.dismiss();
        });}finally{t.main(main::finish);}

        t.main(()->{
            ClockStore s=new ClockStore(t.context);
            JSONObject on=t.testAlarm(s,"QA backup","backup on");ClockStore.put(on,"days",31);ClockStore.put(on,"snoozeMinutes",7);s.enable(on,true);
            JSONObject off=t.testAlarm(s,"QA backup","backup off");ClockStore.put(off,"hour",6);ClockStore.put(off,"minute",45);s.enable(off,false);
            JSONArray groups=s.list("groups");groups.put("QA backup");s.save();
            JSONObject backup=AlarmBackup.create(t.context);
            t.check("clockprime-backup".equals(backup.optString("format"))&&backup.optJSONArray("alarms").toString().contains("backup on"),"Backup contains alarms");
            t.check(!backup.optJSONArray("alarms").toString().contains("\"id\"")&&!backup.optJSONArray("alarms").toString().contains("ringing"),"Backup leaves out ids and runtime state");
            for(JSONObject a:List.of(on,off))s.remove("alarms",a);s.save();
            JSONObject parsed=AlarmBackup.parse(backup.toString());
            JSONArray only=new JSONArray();JSONArray all=parsed.optJSONArray("alarms");for(int i=0;i<all.length();i++)if(all.optJSONObject(i).optString("name").startsWith("ClockPrime QA backup"))only.put(all.optJSONObject(i));
            ClockStore.put(parsed,"alarms",only);
            int added=AlarmBackup.restore(t.context,parsed,false);
            ClockStore after=new ClockStore(t.context);
            JSONObject restoredOn=after.objects("alarms").stream().filter(a->a.optString("name").endsWith("backup on")).findFirst().orElse(null);
            JSONObject restoredOff=after.objects("alarms").stream().filter(a->a.optString("name").endsWith("backup off")).findFirst().orElse(null);
            t.check(added==2&&restoredOn!=null&&restoredOff!=null,"Import adds backed-up alarms");
            t.check(restoredOn.optBoolean("enabled")&&restoredOn.optLong("next")>System.currentTimeMillis()&&restoredOn.optInt("snoozeMinutes")==7,"Imported enabled alarm is scheduled with its options");
            t.check(!restoredOff.optBoolean("enabled")&&restoredOff.optInt("hour")==6&&restoredOff.optInt("minute")==45,"Imported disabled alarm keeps its time and stays off");
            t.check(after.list("groups").toString().contains("QA backup"),"Import keeps groups");
            boolean rejected=false;try{AlarmBackup.parse("{\"format\":\"other\",\"alarms\":[]}");}catch(IllegalArgumentException e){rejected=true;}t.check(rejected,"Import rejects files that are not ClockPrime backups");
            rejected=false;try{AlarmBackup.parse("{\"format\":\"clockprime-backup\",\"alarms\":[{\"hour\":25,\"minute\":0,\"days\":0}]}");}catch(IllegalArgumentException e){rejected=true;}t.check(rejected,"Import rejects invalid alarm times");
            for(JSONObject a:after.objects("alarms"))if(a.optString("name").startsWith("ClockPrime QA backup"))after.remove("alarms",a);
            JSONArray g=after.list("groups");for(int i=g.length()-1;i>=0;i--)if("QA backup".equals(g.optString(i)))g.remove(i);after.save();
        });

        // Do Not Disturb set to total silence: detected, warned only when the option is off, and the alarm still sounds.
        int filter=nm.getCurrentInterruptionFilter();ClockSettings settings=new ClockSettings(t.context);boolean ringInDnd=settings.ringInDnd();final int[] dnd={0};
        t.check(ringInDnd||settings.prefs.contains("alarm_dnd"),"Ring during Do Not Disturb is on by default");
        try{
            shell(t,"cmd notification set_dnd none");SystemClock.sleep(600);
            t.main(()->{
                t.check(AlarmHealth.blocksAlarms(nm),"Total-silence Do Not Disturb is detected as blocking alarms");
                settings.put("alarm_dnd",false);t.check(AlarmHealth.problems(t.context).contains(AlarmHealth.DND),"Blocked alarms are reported when Ring during Do Not Disturb is off");
                settings.put("alarm_dnd",true);boolean silentMedia=audio.getStreamVolume(AudioManager.STREAM_ACCESSIBILITY)<=audio.getStreamMinVolume(AudioManager.STREAM_ACCESSIBILITY);
                t.check(AlarmHealth.problems(t.context).contains(AlarmHealth.DND)==silentMedia,"Ring during Do Not Disturb clears the warning unless media volume is silent");
                ClockStore s=new ClockStore(t.context);JSONObject a=t.testAlarm(s,"","dnd ring");dnd[0]=a.optInt("id");ClockStore.put(a,"days",0);ClockStore.put(a,"enabled",true);ClockStore.put(a,"next",System.currentTimeMillis());ClockStore.put(a,"sound",true);ClockStore.put(a,"vibrate",false);s.save(false);
                t.check(ClockReceiver.fire(t.context,new android.content.Intent("FIRE").putExtra("kind","alarms").putExtra("id",dnd[0])),"Alarm fires during Do Not Disturb");
            });t.waitFor(()->audio.getActivePlaybackConfigurations().stream().map(c->c.getAudioAttributes()).anyMatch(x->x.getUsage()==android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY));
            t.main(()->t.check(audio.getActivePlaybackConfigurations().stream().map(c->c.getAudioAttributes()).anyMatch(x->x.getUsage()==android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY&&x.getContentType()==android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION),"Alarm blocked by Do Not Disturb plays on the unmuted accessibility channel"));
        }finally{
            shell(t,"cmd notification set_dnd "+switch(filter){case NotificationManager.INTERRUPTION_FILTER_PRIORITY->"priority";case NotificationManager.INTERRUPTION_FILTER_NONE->"none";case NotificationManager.INTERRUPTION_FILTER_ALARMS->"alarms";default->"off";});
            settings.put("alarm_dnd",ringInDnd);
            t.main(()->{ClockStore s=new ClockStore(t.context);JSONObject a=s.find("alarms",dnd[0]);if(a!=null)s.remove("alarms",a);s.save(false);s.refreshSound();});SystemClock.sleep(500);
        }
    }
}
