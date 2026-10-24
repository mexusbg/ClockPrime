package com.clockprime.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Portable JSON backup of alarms, groups, world clocks and preferences (Settings > Backup). */
final class AlarmBackup {
    static final String FORMAT="clockprime-backup";
    static final List<String> ALARM_KEYS=List.of("name","group","hour","minute","days","enabled","sound","vibrate","autoDelete","tone","snoozeMinutes","snoozeLimit","repeatMode","repeat","every","monthDay","start","end","date","skipHolidays","challenge","difficulty");

    static JSONObject create(Context c){
        ClockStore store=new ClockStore(c);JSONObject out=new JSONObject();JSONArray alarms=new JSONArray();
        for(JSONObject a:store.objects("alarms")){JSONObject copy=new JSONObject();for(String k:ALARM_KEYS)if(a.has(k))ClockStore.put(copy,k,a.opt(k));alarms.put(copy);}
        JSONObject settings=new JSONObject();for(Map.Entry<String,?> e:new ClockSettings(c).prefs.getAll().entrySet())ClockStore.put(settings,e.getKey(),e.getValue());
        String version;try{version=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception e){version="";}
        ClockStore.put(out,"format",FORMAT);ClockStore.put(out,"version",1);ClockStore.put(out,"app",version);ClockStore.put(out,"exported",java.time.ZonedDateTime.now().toString());
        ClockStore.put(out,"alarms",alarms);ClockStore.put(out,"groups",store.list("groups"));if(store.list("holidays")!=null)ClockStore.put(out,"holidays",store.list("holidays"));if(store.data.has("holidayLabels"))ClockStore.put(out,"holidayLabels",Holidays.labels(store.data));ClockStore.put(out,"cities",store.list("cities"));
        ClockStore.put(out,"settings",settings);ClockStore.put(out,"theme",ThemeChoice.get(c));
        return out;
    }

    /** Rejects anything that is not a well-formed backup before the store is touched. */
    static JSONObject parse(String text){
        JSONObject b;try{b=new JSONObject(text);}catch(JSONException e){throw new IllegalArgumentException("Not JSON",e);}
        if(!FORMAT.equals(b.optString("format"))||b.optJSONArray("alarms")==null)throw new IllegalArgumentException("Not a ClockPrime backup");
        JSONArray alarms=b.optJSONArray("alarms");
        for(int i=0;i<alarms.length();i++){JSONObject a=alarms.optJSONObject(i);
            if(a==null||a.optInt("hour",-1)<0||a.optInt("hour")>23||a.optInt("minute",-1)<0||a.optInt("minute")>59||a.optInt("days",-1)<0||a.optInt("days")>127)throw new IllegalArgumentException("Invalid alarm "+i);}
        return b;
    }

    /**
     * Adds the backup's alarms (new ids, groups merged) or, with {@code replace}, first removes every
     * alarm and group and also restores world clocks, preferences and theme. Returns alarms added.
     */
    static int restore(Context c,JSONObject b,boolean replace){
        ClockStore store=new ClockStore(c);
        if(replace){for(JSONObject a:store.objects("alarms"))store.remove("alarms",a);ClockStore.put(store.data,"groups",new JSONArray());store.data.remove("collapsedGroups");
            JSONArray cities=b.optJSONArray("cities");if(cities!=null)ClockStore.put(store.data,"cities",cities);}
        Set<String> groups=new LinkedHashSet<>();JSONArray existing=store.list("groups");for(int i=0;i<existing.length();i++)groups.add(existing.optString(i));
        JSONArray backupGroups=b.optJSONArray("groups");if(backupGroups!=null)for(int i=0;i<backupGroups.length();i++)if(!backupGroups.optString(i).isEmpty())groups.add(backupGroups.optString(i));
        JSONArray backupHolidays=b.optJSONArray("holidays");if(backupHolidays!=null)Holidays.merge(store.data,backupHolidays,b.optJSONObject("holidayLabels"));
        JSONArray alarms=b.optJSONArray("alarms");int added=0;
        for(int i=0;i<alarms.length();i++){JSONObject source=alarms.optJSONObject(i),a=new JSONObject();
            for(String k:ALARM_KEYS)if(source.has(k))ClockStore.put(a,k,source.opt(k));
            ClockStore.put(a,"id",store.id());if(!a.optString("group").isEmpty())groups.add(a.optString("group"));
            store.list("alarms").put(a);
            if(a.optBoolean("enabled"))store.enable(a,true);else{ClockStore.put(a,"next",0);store.schedule("alarms",a);}
            added++;}
        ClockStore.put(store.data,"groups",new JSONArray(groups));
        if(replace){JSONObject settings=b.optJSONObject("settings");
            if(settings!=null){SharedPreferences.Editor e=new ClockSettings(c).prefs.edit().clear();
                for(Iterator<String> keys=settings.keys();keys.hasNext();){String k=keys.next();Object v=settings.opt(k);
                    if(v instanceof Boolean x)e.putBoolean(k,x);else if(v instanceof Integer x)e.putInt(k,x);else if(v instanceof Long x)e.putLong(k,x);else if(v!=null)e.putString(k,v.toString());}
                e.commit();}
            String theme=b.optString("theme");if(Arrays.asList(ThemeChoice.MODES).contains(theme))ThemeChoice.set(c,theme);}
        store.save();store.refreshSound();
        DebugLog.log(c,"backup",(replace?"replaced with ":"added ")+added+" alarms");
        return added;
    }
}
