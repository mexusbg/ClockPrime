package com.clockprime.app;
import org.json.JSONObject;
import java.util.List;

/** Independent occurrence deadlines, based on monotonic time instead of wall-clock changes. */
final class RingPolicy {
    static boolean expire(ClockStore store,ClockSettings settings,long elapsed){
        boolean changed=false;
        for(String kind:List.of("alarms","timers"))for(JSONObject o:store.objects(kind))if(o.optBoolean("ringing")){
            if(!o.has("ringStartedElapsed")){ClockStore.put(o,"ringStartedElapsed",elapsed);changed=true;}
            long limit=settings.silenceMillis(kind);
            if(limit>0&&elapsed-o.optLong("ringStartedElapsed")>=limit){store.dismiss(kind,o);ClockStore.put(o,"autoSilenced",true);changed=true;}
        }
        return changed;
    }
}
