package com.clockprime.app;

import android.content.Context;
import org.json.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Holiday entries for Settings › Holidays. Storage stays one day per string in "holidays" (what the scheduler,
 * Direct Boot mirror and backups use); "holidayLabels" maps the first day of each entry the user added to its
 * name ("" when unnamed). Consecutive days form one entry unless a labelled day starts a new one, so data from
 * before labels existed groups into ranges by itself.
 */
final class Holidays {
    /** Longest range one entry may add. */
    static final int MAX_DAYS=366;
    static final class Entry{final LocalDate from,to;final String name;Entry(LocalDate from,LocalDate to,String name){this.from=from;this.to=to;this.name=name;}
        int days(){return (int)(to.toEpochDay()-from.toEpochDay())+1;}boolean past(LocalDate today){return to.isBefore(today);}boolean now(LocalDate today){return !from.isAfter(today)&&!to.isBefore(today);}}

    static JSONObject labels(JSONObject data){JSONObject l=data.optJSONObject("holidayLabels");return l==null?new JSONObject():l;}
    static TreeSet<LocalDate> days(JSONObject data){TreeSet<LocalDate> out=new TreeSet<>();for(String s:Schedule.holidays(data)){LocalDate d=Schedule.date(s);if(d!=null)out.add(d);}return out;}
    static List<Entry> entries(JSONObject data){
        JSONObject labels=labels(data);List<Entry> out=new ArrayList<>();LocalDate from=null,to=null;
        for(LocalDate d:days(data)){
            if(from!=null&&d.equals(to.plusDays(1))&&!labels.has(d.toString())){to=d;continue;}
            if(from!=null)out.add(new Entry(from,to,labels.optString(from.toString())));from=to=d;
        }
        if(from!=null)out.add(new Entry(from,to,labels.optString(from.toString())));return out;
    }
    /** Adds every day of the range (overlaps merge) and labels its first day so it stays its own entry. */
    static void add(JSONObject data,LocalDate from,LocalDate to,String name){
        if(to.isBefore(from)){LocalDate t=from;from=to;to=t;}if(to.toEpochDay()-from.toEpochDay()>=MAX_DAYS)to=from.plusDays(MAX_DAYS-1);
        TreeSet<LocalDate> days=days(data);for(LocalDate d=from;!d.isAfter(to);d=d.plusDays(1))days.add(d);
        JSONObject labels=labels(data);ClockStore.put(labels,from.toString(),name==null?"":name.trim());write(data,days,labels);
    }
    static void remove(JSONObject data,Entry e){
        TreeSet<LocalDate> days=days(data);JSONObject labels=labels(data);
        for(LocalDate d=e.from;!d.isAfter(e.to);d=d.plusDays(1)){days.remove(d);labels.remove(d.toString());}write(data,days,labels);
    }
    static void removePast(JSONObject data,LocalDate today){for(Entry e:entries(data))if(e.past(today))remove(data,e);}
    /** Writes days sorted and drops labels whose day is gone. */
    static void write(JSONObject data,Set<LocalDate> days,JSONObject labels){
        JSONArray list=new JSONArray();Set<String> kept=new HashSet<>();for(LocalDate d:new TreeSet<>(days)){list.put(d.toString());kept.add(d.toString());}
        JSONObject clean=new JSONObject();for(Iterator<String> k=labels.keys();k.hasNext();){String key=k.next();if(kept.contains(key))ClockStore.put(clean,key,labels.optString(key));}
        ClockStore.put(data,"holidays",list);if(clean.length()==0)data.remove("holidayLabels");else ClockStore.put(data,"holidayLabels",clean);Schedule.holidays=Schedule.holidays(data);
    }
    /** Backup import: adds the backup's days and labels to the current ones. */
    static void merge(JSONObject data,JSONArray days,JSONObject labels){
        TreeSet<LocalDate> all=days(data);JSONObject mine=labels(data);
        for(int i=0;i<days.length();i++){LocalDate d=Schedule.date(days.optString(i));if(d!=null)all.add(d);}
        if(labels!=null)for(Iterator<String> k=labels.keys();k.hasNext();){String key=k.next();if(Schedule.date(key)!=null&&!mine.has(key))ClockStore.put(mine,key,labels.optString(key));}
        write(data,all,mine);
    }
    /** "Fri, Dec 24" for one day; "Dec 22 – Jan 2, 2027" for a range (years only where they differ from this year's). */
    static String range(Context c,Entry e){
        if(e.days()==1)return LocaleText.shortDate(c,e.from);int year=LocalDate.now().getYear();
        return e.from.format(LocaleText.formatter(c,e.from.getYear()!=year&&e.from.getYear()!=e.to.getYear()?"yMMMd":"MMMd"))+" – "+e.to.format(LocaleText.formatter(c,e.to.getYear()!=year?"yMMMd":"MMMd"));
    }
    static String title(Context c,Entry e){return e.name.isEmpty()?range(c,e):e.name;}
    /** Settings summary: "3 holidays · next Thu, Dec 24". */
    static String summary(Context c,JSONObject data){
        LocalDate today=LocalDate.now();List<Entry> all=entries(data);if(all.isEmpty())return c.getString(R.string.holidays_none);
        String count=c.getResources().getQuantityString(R.plurals.holiday_count,all.size(),all.size());
        for(Entry e:all)if(!e.past(today))return count+" · "+(e.now(today)?c.getString(R.string.holiday_now):c.getString(R.string.holiday_next,LocaleText.shortDate(c,e.from)));
        return count;
    }
}
