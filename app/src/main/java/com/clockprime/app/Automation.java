package com.clockprime.app;

import android.content.*;
import org.json.JSONObject;

/**
 * Opt-in automation (Settings > Automation). Events: broadcasts when an alarm rings, is snoozed or dismissed and when a
 * timer finishes, for apps such as Tasker (extras: id, label, group, hour, minute). Control: {@link Control} turns
 * alarms or whole groups on or off, or skips their next ring, by label or group name.
 */
final class Automation {
    static final String PREFIX="com.clockprime.app.action.",RINGING=PREFIX+"ALARM_RINGING",SNOOZED=PREFIX+"ALARM_SNOOZED",DISMISSED=PREFIX+"ALARM_DISMISSED",
        TIMER_FINISHED=PREFIX+"TIMER_FINISHED",TIMER_DISMISSED=PREFIX+"TIMER_DISMISSED",
        ENABLE=PREFIX+"ENABLE",DISABLE=PREFIX+"DISABLE",SKIP=PREFIX+"SKIP_NEXT",EVENTS_KEY="automation_events",CONTROL_KEY="automation_control";

    /** Sent only to receivers registered at runtime (Android blocks manifest receivers for implicit broadcasts). */
    static void event(Context c,String action,JSONObject o){
        if(!new ClockSettings(c).flag(EVENTS_KEY,false))return;
        Intent i=new Intent(action).putExtra("id",o.optInt("id")).putExtra("label",o.optString("name")).addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        if(o.has("hour"))i.putExtra("hour",o.optInt("hour")).putExtra("minute",o.optInt("minute")).putExtra("group",o.optString("group"));
        c.sendBroadcast(i);
    }

    /**
     * Applies ENABLE / DISABLE / SKIP_NEXT to alarms matching extra "group" (all alarms of that group) or "label"
     * (alarm name, case-insensitive). Returns how many alarms changed; nothing happens while control is off.
     */
    static int control(Context c,Intent intent){
        if(!new ClockSettings(c).flag(CONTROL_KEY,false)){DebugLog.log(c,"automation","ignored "+intent.getAction()+": control is off");return 0;}
        String group=intent.getStringExtra("group"),label=intent.getStringExtra("label"),action=intent.getAction();if(group==null&&label==null)return 0;
        ClockStore s=new ClockStore(c);int changed=0;
        for(JSONObject a:s.objects("alarms")){
            boolean match=group!=null?a.optString("group").equalsIgnoreCase(group.trim()):a.optString("name").trim().equalsIgnoreCase(label.trim());if(!match)continue;
            if(ENABLE.equals(action)){if(!a.optBoolean("enabled")){s.enable(a,true);changed++;}}
            else if(DISABLE.equals(action)){if(a.optBoolean("enabled")){s.enable(a,false);changed++;}}
            else if(SKIP.equals(action)&&a.optBoolean("enabled")){s.skipOnce(a);changed++;}
        }
        if(changed>0){s.save();s.refreshSound();}
        DebugLog.log(c,"automation",action+" changed "+changed);return changed;
    }

    /** Exported receiver for automation apps; it does nothing unless the user turned control on. */
    public static final class Control extends BroadcastReceiver {
        @Override public void onReceive(Context c,Intent intent){control(c,intent);}
    }
}
