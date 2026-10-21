package com.clockprime.app;

import android.content.SharedPreferences;
import android.os.SystemClock;
import org.json.*;

/** Once-per-boot recovery per storage copy, current-boot rings kept, locked-boot changes adopted after unlock. */
final class BootChecks {
    static void run(SmokeTests t){
        t.main(()->{
            int boot=ClockStore.bootCount(t.context);
            t.check(boot>=0,"Android boot count is readable");
            ClockStore s=new ClockStore(t.context);
            JSONObject stale=t.testAlarm(s,"","boot stale ring"),live=t.testAlarm(s,"","boot live ring");
            for(JSONObject a:new JSONObject[]{stale,live}){ClockStore.put(a,"days",127);ClockStore.put(a,"enabled",true);ClockStore.put(a,"ringing",true);ClockStore.put(a,"sound",false);ClockStore.put(a,"vibrate",false);ClockStore.put(a,"next",System.currentTimeMillis()+3600000);}
            ClockStore.put(stale,"ringBoot",boot-1);ClockStore.put(live,"ringBoot",boot);s.save(false);
            s.prefs().edit().putInt(ClockStore.BOOT_COUNT,boot-1).commit();
            ClockStore before=new ClockStore(t.context);t.check(before.needsRebootRecovery(),"Storage marked with an older boot needs recovery");
            before.restore(false,false);
            ClockStore after=new ClockStore(t.context);
            t.check(!after.find("alarms",stale.optInt("id")).optBoolean("ringing"),"Recovery clears a ring left from the previous boot");
            t.check(after.find("alarms",live.optInt("id")).optBoolean("ringing"),"Recovery keeps an alarm that started ringing this boot");
            t.check(!after.needsRebootRecovery()&&after.prefs().getInt(ClockStore.BOOT_COUNT,-1)==boot,"Recovery runs once per boot");

            // Simulate a locked boot: the device-protected copy rang and advanced this boot, credential copy is stale.
            JSONObject a=after.find("alarms",live.optInt("id"));ClockStore.put(a,"ringing",false);long oldNext=System.currentTimeMillis()-60000;ClockStore.put(a,"next",oldNext);after.save(false);
            SharedPreferences de=after.dePrefs();
            try{JSONObject mirror=new JSONObject(de.getString("state","{}"));JSONArray alarms=mirror.optJSONArray("alarms");long advanced=System.currentTimeMillis()+86400000L;
                for(int i=0;i<alarms.length();i++){JSONObject m=alarms.optJSONObject(i);if(m.optInt("id")==a.optInt("id")){ClockStore.put(m,"next",advanced);ClockStore.put(m,"ringing",true);ClockStore.put(m,"ringBoot",boot);}}
                de.edit().putString("state",mirror.toString()).putInt(ClockStore.BOOT_COUNT,boot).commit();
                after.prefs().edit().putInt(ClockStore.BOOT_COUNT,boot-1).commit();
                ClockStore unlocked=new ClockStore(t.context);unlocked.restore(false,false);
                JSONObject adopted=new ClockStore(t.context).find("alarms",a.optInt("id"));
                t.check(adopted.optLong("next")==advanced&&adopted.optBoolean("ringing")&&!adopted.optBoolean("missed"),"After unlock the locked-boot ring and schedule are adopted, not marked missed");
            }catch(JSONException e){throw new AssertionError(e);}
            ClockStore end=new ClockStore(t.context);for(JSONObject o:end.objects("alarms"))if(o.optString("name").startsWith("ClockPrime QA boot"))end.remove("alarms",o);end.save(false);end.refreshSound();
        });
        SystemClock.sleep(300);
    }
}
