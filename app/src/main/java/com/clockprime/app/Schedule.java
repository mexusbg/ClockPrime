package com.clockprime.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * When an alarm rings. A weekly alarm uses the "days" weekday bitmask; days 0 without "repeat" is a one-time alarm,
 * optionally on a fixed "date". "repeat" adds: "days" (every "every" days counted from "start"), "weeks" (every "every"
 * weeks on the "days" weekdays, counted from the week of "start") and "month" ("monthDay" 1-31; -1 = last day).
 * Repeating alarms may be bounded by "start"/"end" and skip the shared holiday list ("skipHolidays").
 * "skipDate" is the inclusive end of skipped occurrences; with "paused" it is a pause chosen by the user.
 */
final class Schedule {
    /** Shared holiday dates (yyyy-MM-dd), loaded with the store so the static scheduling code can see them. */
    static volatile Set<String> holidays=Set.of();
    /** Days searched for the next ring: enough for a monthly 31st or an every-52-weeks rule. */
    static final int HORIZON=1500;
    static final List<String> KEYS=List.of("repeat","every","monthDay","start","end","date","skipHolidays","paused","challenge","difficulty");

    static boolean repeats(JSONObject a){return a.optInt("days")!=0||!a.optString("repeat").isEmpty();}
    static LocalDate date(String value){try{return value==null||value.isEmpty()?null:LocalDate.parse(value);}catch(java.time.format.DateTimeParseException e){return null;}}
    static Set<String> holidays(JSONObject data){JSONArray list=data.optJSONArray("holidays");Set<String> out=new HashSet<>();if(list!=null)for(int i=0;i<list.length();i++)out.add(list.optString(i));return Collections.unmodifiableSet(out);}
    static LocalDate monday(LocalDate d){return d.minusDays(d.getDayOfWeek().getValue()-1);}
    static long stamp(JSONObject a,LocalDate d,ZoneId zone){return d.atTime(a.optInt("hour"),a.optInt("minute")).atZone(zone).toInstant().toEpochMilli();}

    /** Whether a repeating alarm's rule includes this date (ignoring start, end, holidays and skips). */
    static boolean matches(JSONObject a,LocalDate d){
        int bit=1<<(d.getDayOfWeek().getValue()-1),days=a.optInt("days"),every=Math.max(1,a.optInt("every",1));LocalDate start=date(a.optString("start"));if(start==null)start=d;
        switch(a.optString("repeat")){
            case "days":return Math.floorMod(ChronoUnit.DAYS.between(start,d),every)==0;
            case "weeks":return (days&bit)!=0&&Math.floorMod(ChronoUnit.WEEKS.between(monday(start),monday(d)),every)==0;
            case "month":{int day=a.optInt("monthDay",1);return day<0?d.getDayOfMonth()==d.lengthOfMonth():d.getDayOfMonth()==day;}
            default:return (days&bit)!=0;
        }
    }

    /** Next ring after {@code after}, or 0 when the schedule has none left (a past date, or past its end). */
    static long next(JSONObject a,long after){
        ZoneId zone=ZoneId.systemDefault();LocalDate today=Instant.ofEpochMilli(after).atZone(zone).toLocalDate();
        LocalDate cutoff=date(a.optString("skipDate"));if(cutoff!=null&&!cutoff.isBefore(today))today=cutoff.plusDays(1);
        if(!repeats(a)){
            LocalDate on=date(a.optString("date"));
            if(on!=null){long stamp=stamp(a,on,zone);return stamp>after&&(cutoff==null||on.isAfter(cutoff))?stamp:0;}
            for(int i=0;i<2;i++){long stamp=stamp(a,today.plusDays(i),zone);if(stamp>after)return stamp;}
            return 0;
        }
        LocalDate start=date(a.optString("start")),end=date(a.optString("end"));if(start!=null&&start.isAfter(today))today=start;
        boolean skipHolidays=a.optBoolean("skipHolidays");Set<String> off=holidays;
        for(int i=0;i<HORIZON;i++){
            LocalDate d=today.plusDays(i);if(end!=null&&d.isAfter(end))return 0;
            if(!matches(a,d)||(skipHolidays&&off.contains(d.toString())))continue;
            long stamp=stamp(a,d,zone);if(stamp>after)return stamp;
        }
        return 0;
    }

    /** The date of the {@code count}-th upcoming ring, ignoring current skips; null when there are fewer. */
    static LocalDate nthRing(JSONObject a,int count,long now){
        JSONObject probe=copy(a);probe.remove("skipDate");long at=now;
        for(int i=0;i<count;i++){at=next(probe,at);if(at==0)return null;}
        return Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate();
    }
    static JSONObject copy(JSONObject a){try{return new JSONObject(a.toString());}catch(org.json.JSONException e){throw new IllegalStateException(e);}}

    /** Short repeat description for cards and widgets, e.g. "Every 3 days" or "Monthly on day 15 · until 1 Dec". */
    static String describe(Context c,JSONObject a){
        String text;
        switch(a.optString("repeat")){
            case "days":{int n=Math.max(1,a.optInt("every",1));text=c.getResources().getQuantityString(R.plurals.every_days,n,n);break;}
            case "weeks":{int n=Math.max(1,a.optInt("every",1));text=c.getResources().getQuantityString(R.plurals.every_weeks,n,n)+" · "+LocaleText.days(c,a.optInt("days"));break;}
            case "month":{int day=a.optInt("monthDay",1);text=day<0?c.getString(R.string.monthly_last):c.getString(R.string.monthly_on,day);break;}
            default:{LocalDate on=date(a.optString("date"));text=!repeats(a)&&on!=null?LocaleText.shortDate(c,on):LocaleText.days(c,a.optInt("days"));}
        }
        LocalDate end=date(a.optString("end"));if(repeats(a)&&end!=null)text=c.getString(R.string.schedule_until,text,LocaleText.shortDate(c,end));
        return text;
    }
}
