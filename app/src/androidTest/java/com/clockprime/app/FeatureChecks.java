package com.clockprime.app;

import android.content.Intent;
import org.json.*;
import java.time.*;
import java.util.*;

/** 0.13: flexible schedules. */
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
            ClockStore.put(everyThree,"end","2026-12-01");
            t.check(Schedule.describe(t.context,everyThree).contains("3")&&!Schedule.describe(t.context,last).isEmpty(),"Cards describe the new repeat rules: "+Schedule.describe(t.context,everyThree));

            ClockStore s=new ClockStore(t.context);
            JSONObject ended=t.testAlarm(s,"","ended");ClockStore.put(ended,"end",LocalDate.now().minusDays(1).toString());s.enable(ended,true);
            t.check(!ended.optBoolean("enabled")&&ended.optLong("next")==0,"An alarm whose schedule has ended cannot be turned on");

        });

    }
}
