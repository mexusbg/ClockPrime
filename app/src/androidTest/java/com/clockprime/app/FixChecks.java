package com.clockprime.app;

import android.app.*;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 0.8 regressions: single timer notification, island payloads, stable ring screen, debug export. */
final class FixChecks {
    static void run(SmokeTests t){
        NotificationManager nm=t.context.getSystemService(NotificationManager.class);
        final int[] timer={0};
        t.main(()->{
            ClockStore s=new ClockStore(t.context);JSONObject q=new JSONObject();timer[0]=s.id();
            ClockStore.put(q,"id",timer[0]);ClockStore.put(q,"name","ClockPrime QA island timer");ClockStore.put(q,"duration",600000L);ClockStore.put(q,"sound",false);ClockStore.put(q,"vibrate",false);
            s.list("timers").put(q);s.timerStart(q,600000L);s.save();
        });SystemClock.sleep(1200);t.waitForIdleSync();
        try{
            t.main(()->{
                ClockStore s=new ClockStore(t.context);long running=s.objects("timers").stream().filter(o->o.optBoolean("running")).count();
                long shown=Arrays.stream(nm.getActiveNotifications()).filter(n->RingService.TIMER_CHANNEL.equals(n.getNotification().getChannelId())).count();
                t.check(shown==running,"Each running timer has exactly one notification ("+shown+" for "+running+")");
                t.check(Arrays.stream(nm.getActiveNotifications()).anyMatch(n->n.getId()==timer[0]),"Running timer notification uses the timer id");
                t.check(Arrays.stream(nm.getActiveNotifications()).noneMatch(n->n.getId()==2),"No separate generic timer service notification");
                long end=System.currentTimeMillis()+300000;
                JSONObject timerParam=FocusIsland.timerParam(t.context,"QA",end,600000L,true).optJSONObject("param_v2");
                JSONObject digits=timerParam.optJSONObject("param_island").optJSONObject("bigIslandArea").optJSONObject("sameWidthDigitInfo");
                t.check(digits!=null&&digits.optJSONObject("timerInfo").optLong("timerWhen")==end&&!digits.has("digit"),"Timer island ticks to the countdown end");
                JSONObject card=timerParam.optJSONObject("highlightInfo");
                t.check(card.optJSONObject("timerInfo").optInt("timerType")==-1&&card.optJSONObject("timerInfo").optLong("timerTotal")==600000L&&!card.has("title"),"Expanded timer card shows running countdown");
                JSONObject ring=FocusIsland.ringParam(t.context,"Alarm","07:30","Wake up",true).optJSONObject("param_v2");
                JSONObject left=ring.optJSONObject("param_island").optJSONObject("bigIslandArea").optJSONObject("imageTextInfoLeft").optJSONObject("textInfo");
                t.check("07:30".equals(left.optString("title"))&&"Wake up".equals(ring.optJSONObject("param_island").optJSONObject("bigIslandArea").optJSONObject("textInfo").optString("title")),"Ringing island shows alarm time and label");
                JSONArray buttons=ring.optJSONArray("textButton");
                t.check(buttons!=null&&buttons.length()==2&&buttons.toString().contains(FocusIsland.DISMISS)&&buttons.toString().contains(FocusIsland.SNOOZE),"Ringing island offers Dismiss and Snooze");
                JSONArray dismissOnly=FocusIsland.ringParam(t.context,"Timer","Done","",false).optJSONObject("param_v2").optJSONArray("textButton");
                t.check(dismissOnly!=null&&dismissOnly.length()==1,"Island without snooze offers Dismiss only");
                t.check(FocusIsland.ringParam(t.context,"Alarm","07:30","A long alarm label used for checking size",true).toString().getBytes(StandardCharsets.UTF_8).length<FocusIsland.MAX_PARAM,"Island payload fits Xiaomi size limit");
            });
            t.main(()->{ClockStore s=new ClockStore(t.context);s.timerPause(s.find("timers",timer[0]));s.save();});SystemClock.sleep(1200);t.waitForIdleSync();
            t.main(()->{
                Notification paused=Arrays.stream(nm.getActiveNotifications()).filter(n->n.getId()==timer[0]).map(n->n.getNotification()).findFirst().orElse(null);
                t.check(paused!=null&&paused.actions.length==2&&t.context.getString(R.string.resume).contentEquals(paused.actions[0].title),"Paused timer keeps its notification with Resume");
                t.check(!paused.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),"Paused timer notification does not tick");
                JSONObject frozen=FocusIsland.timerParam(t.context,"QA",System.currentTimeMillis()+120000,600000L,false).optJSONObject("param_v2");
                t.check(frozen.optJSONObject("highlightInfo").optJSONObject("timerInfo").optInt("timerType")==-2&&frozen.optJSONArray("actions").toString().contains(FocusIsland.RESUME),"Paused island freezes the countdown and offers Resume");
                try{paused.actions[0].actionIntent.send();}catch(Exception e){throw new AssertionError(e);}
            });SystemClock.sleep(1500);t.waitForIdleSync();
            t.main(()->{JSONObject q=new ClockStore(t.context).find("timers",timer[0]);t.check(q.optBoolean("running")&&!q.optBoolean("paused")&&ClockStore.remaining(q)<600000L,"Resume continues the paused timer");
                for(String id:new String[]{RingService.TIMER_CHANNEL,RingService.UPCOMING_CHANNEL}){NotificationChannel ch=nm.getNotificationChannel(id);t.check(ch!=null&&ch.getImportance()>=NotificationManager.IMPORTANCE_DEFAULT&&ch.getSound()==null,"Channel "+id+" is quiet but shown on the lock screen");}
                t.check(nm.getNotificationChannel("upcoming")==null&&nm.getNotificationChannel("timers")==null,"Old silent channels are removed");});
        }finally{t.main(()->{ClockStore s=new ClockStore(t.context);JSONObject q=s.find("timers",timer[0]);if(q!=null){s.remove("timers",q);s.save();}});}
        SystemClock.sleep(800);
        t.main(()->t.check(Arrays.stream(nm.getActiveNotifications()).noneMatch(n->n.getId()==timer[0]),"Removed timer notification disappears"));

        final int[] alarm={0};
        t.main(()->{ClockStore s=new ClockStore(t.context);JSONObject a=t.testAlarm(s,"","ring screen");alarm[0]=a.optInt("id");ClockStore.put(a,"days",0);ClockStore.put(a,"enabled",false);ClockStore.put(a,"next",0);ClockStore.put(a,"ringing",true);ClockStore.put(a,"ringStartedElapsed",SystemClock.elapsedRealtime());ClockStore.put(a,"sound",false);ClockStore.put(a,"vibrate",false);s.save(false);});
        RingActivity screen=(RingActivity)t.startActivitySync(new Intent(t.context,RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));t.waitForIdleSync();
        try{
            final View[] first={null};
            t.main(()->{first[0]=screen.list.getChildAt(0);t.check(first[0]!=null&&RingActivity.visible,"Ring screen shows the ringing alarm");});
            SystemClock.sleep(1600);t.waitForIdleSync();
            t.main(()->{t.check(screen.list.getChildAt(0)==first[0],"Ring screen keeps its buttons between clock ticks");
                ClockStore s=new ClockStore(t.context);ClockStore.put(s.find("alarms",alarm[0]),"name","ClockPrime QA ring screen renamed");s.save(false);
                screen.act("alarms",alarm[0],false);
                t.check(!new ClockStore(t.context).find("alarms",alarm[0]).optBoolean("ringing"),"Dismiss from ring screen stops the alarm");
                t.check(new ClockStore(t.context).find("alarms",alarm[0]).optString("name").endsWith("renamed"),"Ring screen action keeps newer saved changes");});
        }finally{t.main(()->{if(!screen.isFinishing())screen.finish();ClockStore s=new ClockStore(t.context);JSONObject a=s.find("alarms",alarm[0]);if(a!=null)s.remove("alarms",a);s.save(false);s.refreshSound();});}

        final int[] pair={0,0,0};
        t.main(()->{
            ClockStore s=new ClockStore(t.context);long now=System.currentTimeMillis();
            JSONObject first=t.testAlarm(s,"","overlap ringing");pair[0]=first.optInt("id");ClockStore.put(first,"days",0);ClockStore.put(first,"enabled",false);ClockStore.put(first,"next",0);ClockStore.put(first,"ringing",true);ClockStore.put(first,"ringStartedElapsed",SystemClock.elapsedRealtime());ClockStore.put(first,"sound",false);ClockStore.put(first,"vibrate",false);
            JSONObject once=t.testAlarm(s,"","overlap once");pair[1]=once.optInt("id");ClockStore.put(once,"days",0);ClockStore.put(once,"enabled",true);ClockStore.put(once,"next",now);ClockStore.put(once,"sound",false);ClockStore.put(once,"vibrate",false);
            JSONObject daily=t.testAlarm(s,"","overlap daily");pair[2]=daily.optInt("id");ClockStore.put(daily,"days",127);ClockStore.put(daily,"enabled",true);ClockStore.put(daily,"next",now);ClockStore.put(daily,"sound",false);ClockStore.put(daily,"vibrate",false);
            s.save(false);
            t.check(ClockReceiver.fire(t.context,new Intent("FIRE").putExtra("kind","alarms").putExtra("id",pair[1])),"Overlapping alarm fires");
            ClockStore mid=new ClockStore(t.context);JSONObject r=mid.find("alarms",pair[0]),o=mid.find("alarms",pair[1]);
            t.check(!r.optBoolean("ringing")&&r.optBoolean("autoSilenced")&&o.optBoolean("ringing"),"A newer alarm replaces the one still ringing");
            t.check(!o.optBoolean("enabled")&&!o.optBoolean("missed"),"Replacing one-time alarm rings normally");
            t.check(ClockReceiver.fire(t.context,new Intent("FIRE").putExtra("kind","alarms").putExtra("id",pair[2])),"Third overlapping alarm fires");
            ClockStore after=new ClockStore(t.context);JSONObject d=after.find("alarms",pair[2]);
            t.check(!after.find("alarms",pair[1]).optBoolean("ringing")&&d.optBoolean("ringing")&&d.optBoolean("enabled")&&d.optLong("next")>now,"Only the newest alarm rings and a repeating one keeps its schedule");
            t.check(after.objects("alarms").stream().filter(x->x.optBoolean("ringing")).count()==1,"Exactly one alarm rings after overlaps");
        });SystemClock.sleep(600);
        t.main(()->{
            for(int[] p:new int[][]{{pair[0],pair[1]},{pair[1],pair[2]}}){Notification n=Arrays.stream(nm.getActiveNotifications()).filter(x->x.getId()==ClockReceiver.overlapId(p[0])).map(x->x.getNotification()).findFirst().orElse(null);
                t.check(n!=null&&RingService.SKIPPED_CHANNEL.equals(n.getChannelId())&&String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TEXT)).contains(p[1]==pair[1]?"overlap once":"overlap daily"),"Replaced alarm notification names the alarm that took over");}
        });
        t.main(()->{ClockStore s=new ClockStore(t.context);for(int id:pair){JSONObject a=s.find("alarms",id);if(a!=null)s.remove("alarms",a);nm.cancel(ClockReceiver.overlapId(id));}s.save(false);s.refreshSound();});

        t.main(()->{
            String marker="QA marker "+SystemClock.elapsedRealtime();DebugLog.log(t.context,"qa",marker);
            try{Uri uri=DebugLog.export(t.context);StringBuilder text=new StringBuilder();
                try(BufferedReader r=new BufferedReader(new InputStreamReader(t.context.getContentResolver().openInputStream(uri),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)text.append(line).append('\n');}
                t.check(text.indexOf("ClockPrime debug report")==0&&text.indexOf(marker)>0,"Debug export contains device report and recent events");
                t.check(text.indexOf("== Logcat")>0&&text.indexOf("Device ")>0,"Debug export includes device details and logcat");
            }catch(IOException e){throw new AssertionError(e);}
        });
    }
}
