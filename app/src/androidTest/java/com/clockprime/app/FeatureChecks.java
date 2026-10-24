package com.clockprime.app;

import android.content.Intent;
import org.json.*;
import java.time.*;
import java.util.*;

/** 0.13: flexible schedules, holidays, pause, wake-up puzzles, Android clock requests, automation, timer minutes, alarm series. */
final class FeatureChecks {
    static long at(int y,int m,int d,int h,int min){return LocalDate.of(y,m,d).atTime(h,min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}
    static JSONObject rule(String repeat,int every,int days,String start){JSONObject a=new JSONObject();ClockStore.put(a,"hour",7);ClockStore.put(a,"minute",0);ClockStore.put(a,"days",days);if(repeat!=null)ClockStore.put(a,"repeat",repeat);if(every>0)ClockStore.put(a,"every",every);if(start!=null)ClockStore.put(a,"start",start);return a;}

    static void run(SmokeTests t){
        t.main(()->{
            JSONObject everyThree=rule("days",3,0,"2026-11-02");
            t.check(Schedule.next(everyThree,at(2026,11,3,9,0))==at(2026,11,5,7,0),"Every 3 days counts from its start date");
            JSONObject everyOther=rule("weeks",2,1,"2026-11-02");
            t.check(Schedule.next(everyOther,at(2026,11,3,9,0))==at(2026,11,16,7,0),"Every 2 weeks skips the week in between");
            JSONObject last=rule("month",0,0,null);ClockStore.put(last,"monthDay",-1);
            t.check(Schedule.next(last,at(2026,2,10,9,0))==at(2026,2,28,7,0),"Monthly on the last day lands on 28 February");
            JSONObject day31=rule("month",0,0,null);ClockStore.put(day31,"monthDay",31);
            t.check(Schedule.next(day31,at(2026,4,1,9,0))==at(2026,5,31,7,0),"Monthly on the 31st skips months without one");
            JSONObject dated=rule(null,0,0,null);ClockStore.put(dated,"date","2026-12-24");
            t.check(Schedule.next(dated,at(2026,11,1,9,0))==at(2026,12,24,7,0)&&Schedule.next(dated,at(2026,12,25,9,0))==0,"One-time alarm on a date rings that day only");
            JSONObject ending=rule(null,0,127,null);ClockStore.put(ending,"end","2026-11-04");
            t.check(Schedule.next(ending,at(2026,11,4,6,0))==at(2026,11,4,7,0)&&Schedule.next(ending,at(2026,11,4,8,0))==0,"Repeating alarm stops after its end date");
            JSONObject starting=rule(null,0,127,"2026-11-10");
            t.check(Schedule.next(starting,at(2026,11,3,9,0))==at(2026,11,10,7,0),"Repeating alarm waits for its start date");
            Set<String> saved=Schedule.holidays;Schedule.holidays=Set.of("2026-11-04");
            JSONObject holiday=rule(null,0,127,null);ClockStore.put(holiday,"skipHolidays",true);JSONObject ignores=rule(null,0,127,null);
            t.check(Schedule.next(holiday,at(2026,11,3,9,0))==at(2026,11,5,7,0)&&Schedule.next(ignores,at(2026,11,3,9,0))==at(2026,11,4,7,0),"Only alarms set to skip holidays skip them");
            Schedule.holidays=saved;
            ClockStore.put(everyThree,"end","2026-12-01");
            t.check(Schedule.describe(t.context,everyThree).contains("3")&&!Schedule.describe(t.context,last).isEmpty(),"Cards describe the new repeat rules: "+Schedule.describe(t.context,everyThree));

            ClockStore s=new ClockStore(t.context);
            JSONObject p=t.testAlarm(s,"","pause");s.enable(p,true);LocalDate until=LocalDate.now().plusDays(3);s.pause(p,until);
            LocalDate resumes=Instant.ofEpochMilli(p.optLong("next")).atZone(ZoneId.systemDefault()).toLocalDate();
            t.check(p.optBoolean("paused")&&p.optBoolean("enabled")&&resumes.equals(until.plusDays(1)),"Pause until a date resumes the day after");
            s.restoreSkips(p);t.check(!p.has("paused")&&!Instant.ofEpochMilli(p.optLong("next")).atZone(ZoneId.systemDefault()).toLocalDate().isAfter(LocalDate.now().plusDays(1)),"Resume ends a pause");
            LocalDate third=Schedule.nthRing(p,3,System.currentTimeMillis());s.pauseRings(p,2);
            t.check(Instant.ofEpochMilli(p.optLong("next")).atZone(ZoneId.systemDefault()).toLocalDate().equals(third),"Pause for 2 rings rings again on the third");
            JSONObject ended=t.testAlarm(s,"","ended");ClockStore.put(ended,"end",LocalDate.now().minusDays(1).toString());s.enable(ended,true);
            t.check(!ended.optBoolean("enabled")&&ended.optLong("next")==0,"An alarm whose schedule has ended cannot be turned on");

            Random r=new Random(7);boolean positive=true;for(int level=1;level<=3;level++)for(int i=0;i<200;i++){Object[] m=Challenge.math(level,r);positive&=(Integer)m[1]>0;}
            String code=Challenge.code(2,r);
            t.check(positive&&code.replace("-","").length()==9&&Challenge.sameCode(code,code.toLowerCase().replace("-"," ")),"Wake-up puzzles have positive answers and forgiving codes");
            JSONObject puzzle=t.testAlarm(s,"","puzzle");ClockStore.put(puzzle,"challenge","math");ClockStore.put(puzzle,"ringing",true);s.save();
            new ClockReceiver().onReceive(t.context,new Intent("DISMISS").putExtra("kind","alarms").putExtra("id",puzzle.optInt("id")));
            ClockStore after=new ClockStore(t.context);t.check(after.find("alarms",puzzle.optInt("id")).optBoolean("ringing"),"A puzzle alarm cannot be dismissed from the notification");
            JSONObject stop=after.find("alarms",puzzle.optInt("id"));after.dismiss("alarms",stop);after.save();after.refreshSound();
        });

        t.main(()->{
            ClockStore s=new ClockStore(t.context);JSONObject tm=new JSONObject();ClockStore.put(tm,"id",s.id());ClockStore.put(tm,"name","ClockPrime QA adjust");ClockStore.put(tm,"duration",600000L);ClockStore.put(tm,"sound",false);ClockStore.put(tm,"vibrate",false);
            s.list("timers").put(tm);s.timerStart(tm,600000L);long before=ClockStore.remaining(tm);s.timerAdjust(tm,60000);long more=ClockStore.remaining(tm);s.timerAdjust(tm,-100000000L);
            t.check(Math.abs(more-before-60000)<1500&&ClockStore.remaining(tm)<=1000&&ClockStore.remaining(tm)>0,"Timers gain a minute and never drop below a second");
            boolean notified=Arrays.stream(s.timerNotification(tm).actions).anyMatch(x->x.title.toString().equals(t.context.getString(R.string.add_minute)));
            t.check(notified,"Running timer notification offers +1 min");
            s.remove("timers",tm);s.save();

            JSONObject source=new JSONObject();ClockStore.put(source,"days",31);ClockStore.put(source,"challenge","shake");ClockStore.put(source,"start","2026-11-02");ClockStore.put(source,"name","x");
            JSONObject template=MainActivity.template(source);
            t.check(template.optInt("days")==31&&template.optString("challenge").equals("shake")&&!template.has("start")&&!template.has("name"),"New alarms copy the last alarm's settings, not its dates or label");

        });
        holidays(t);
    }

    /** Holiday manager: ranges, labels, delete with undo; the scheduler still sees single days. */
    static void holidays(SmokeTests t){
        Set<String> savedDays=Schedule.holidays;
        t.main(()->{
            JSONObject d=new JSONObject();Holidays.add(d,LocalDate.of(2026,12,22),LocalDate.of(2027,1,2),"QA vacation");
            List<Holidays.Entry> e=Holidays.entries(d);
            t.check(e.size()==1&&e.get(0).days()==12&&e.get(0).name.equals("QA vacation")&&d.optJSONArray("holidays").length()==12&&Schedule.holidays.contains("2026-12-31")&&Schedule.holidays.size()==12,"A holiday range is stored as its days and listed as one entry");
            JSONObject daily=rule(null,0,127,null);ClockStore.put(daily,"skipHolidays",true);
            t.check(Schedule.next(daily,at(2026,12,21,9,0))==at(2027,1,3,7,0),"An alarm that skips holidays rings after the whole range");
            Holidays.add(d,LocalDate.of(2027,1,3),LocalDate.of(2027,1,3),"");
            t.check(Holidays.entries(d).size()==2,"A day added next to a range stays its own entry");
            JSONObject old=new JSONObject();ClockStore.put(old,"holidays",new JSONArray(List.of("2026-05-01","2026-05-02","2026-05-06")));
            List<Holidays.Entry> legacy=Holidays.entries(old);
            t.check(legacy.size()==2&&legacy.get(0).days()==2&&legacy.get(0).name.isEmpty(),"Older day lists group consecutive days into ranges");
            Holidays.remove(d,e.get(0));
            t.check(Holidays.entries(d).size()==1&&!Schedule.holidays.contains("2026-12-31")&&!Holidays.labels(d).has("2026-12-22"),"Deleting a range removes its days and its name");
            JSONObject merged=new JSONObject();Holidays.merge(merged,new JSONArray(List.of("2026-12-24","2026-12-25","bad")),new JSONObject(Map.of("2026-12-24","QA Christmas")));
            t.check(Holidays.entries(merged).size()==1&&Holidays.entries(merged).get(0).name.equals("QA Christmas")&&merged.optJSONArray("holidays").length()==2,"Backup import merges holiday days and names");
            Holidays.add(d,LocalDate.of(2026,1,1),LocalDate.of(2030,1,1),"");
            t.check(Holidays.entries(d).get(0).days()==Holidays.MAX_DAYS,"One entry covers at most a year");
        });
        Schedule.holidays=savedDays;
        // Screen: real store, restored afterwards.
        String[] saved=new String[2];
        t.main(()->{ClockStore s=new ClockStore(t.context);saved[0]=s.data.has("holidays")?s.list("holidays").toString():null;saved[1]=s.data.has("holidayLabels")?Holidays.labels(s.data).toString():null;});
        SettingsActivity screen=(SettingsActivity)t.startActivitySync(new Intent(t.context,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));t.waitForIdleSync();
        LocalDate from=LocalDate.now().plusYears(3).withMonth(12).withDayOfMonth(22);
        try{
            t.main(()->{
                screen.holidays();
                screen.changeHolidays(x->{Holidays.write(x,new TreeSet<>(),new JSONObject());},null);
                t.check(screen.body.findViewWithTag("holidays-empty")!=null&&screen.findViewById(android.R.id.content).findViewWithTag("holiday-add")!=null,"Holiday manager shows an empty state and an Add button");
                screen.editHoliday(null);screen.holidayRange[0]=from;screen.holidayRange[1]=from.plusDays(11);screen.holidayName.setText("ClockPrime QA vacation");
                screen.holidayEditor.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            });
            t.waitForIdleSync();
            t.main(()->{
                android.view.View row=screen.body.findViewWithTag("holiday-"+from);
                t.check(row!=null&&Schedule.holidays.size()==12&&Schedule.holidays.contains(from.plusDays(11).toString()),"Adding a range from the editor saves all its days");
                android.view.View bin=row.findViewWithTag("holiday-delete");
                t.check(bin!=null&&bin.getWidth()>=bin.getResources().getDisplayMetrics().density*47&&t.context.getString(R.string.holiday_delete,"ClockPrime QA vacation").contentEquals(bin.getContentDescription()),"Each holiday has a labelled recycle-bin button");
                bin.performClick();
                t.check(Schedule.holidays.isEmpty()&&screen.body.findViewWithTag("holiday-"+from)==null&&screen.findViewById(android.R.id.content).findViewWithTag("holiday-undo")!=null,"Deleting a holiday removes it and offers Undo");
                screen.findViewById(android.R.id.content).findViewWithTag("holiday-undo").performClick();
                ClockStore s=new ClockStore(t.context);
                t.check(Schedule.holidays.size()==12&&Holidays.entries(s.data).get(0).name.equals("ClockPrime QA vacation")&&screen.body.findViewWithTag("holiday-"+from)!=null,"Undo restores the deleted holiday with its name");
                screen.back();t.check(!screen.holidayPage()&&!screen.isFinishing(),"Back from Holidays returns to Settings");
            });
        }finally{
            t.main(()->{ClockStore s=new ClockStore(t.context);try{ClockStore.put(s.data,"holidays",saved[0]==null?new JSONArray():new JSONArray(saved[0]));if(saved[1]==null)s.data.remove("holidayLabels");else ClockStore.put(s.data,"holidayLabels",new JSONObject(saved[1]));}catch(JSONException x){throw new AssertionError(x);}
                if(saved[0]==null)s.data.remove("holidays");screen.saveHolidays(s);screen.finish();});
            t.waitForIdleSync();
        }
    }
}
