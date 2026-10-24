package com.clockprime.app;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import org.json.*;
import java.time.*;
import java.util.*;

/** All mutations run on the application's main thread, including receiver and service. */
public final class ClockStore {
    static final int SCHEMA=1;
    static final String PREFS="clockprime";
    static final String DE_PREFS="clockprime_de";
    static final String BOOT_ELAPSED="boot_elapsed",BOOT_COUNT="boot_count";
    /** Optional UI hook; collapse/cosmetic saves may skip notification. */
    static volatile Runnable onChanged;
    final Context context;
    final boolean directBoot;
    final JSONObject data;
    ClockStore(Context context) {
        Context app=context.getApplicationContext();
        boolean unlocked=app.getSystemService(android.os.UserManager.class).isUserUnlocked();
        this.directBoot=!unlocked;
        this.context=directBoot?app.createDeviceProtectedStorageContext():app;
        JSONObject parsed;
        try { parsed = new JSONObject(prefs().getString("state", "{}")); } catch (JSONException e) { throw new IllegalStateException("Cannot read saved clock data", e); }
        data = parsed;
        if (!data.has("alarms")) { put(data,"alarms",new JSONArray()); put(data,"timers",new JSONArray()); put(data,"groups",new JSONArray()); put(data,"cities",new JSONArray(List.of("Europe/Sofia","Europe/London","America/New_York"))); }
        migrate();Schedule.holidays=Schedule.holidays(data);
    }
    android.content.SharedPreferences prefs() {
        if(directBoot) return context.getSharedPreferences(DE_PREFS,Context.MODE_PRIVATE);
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    SharedPreferences dePrefs() { return context.createDeviceProtectedStorageContext().getSharedPreferences(DE_PREFS,Context.MODE_PRIVATE); }
    void migrate() {
        int version=data.optInt("schema",0);
        if(version>=SCHEMA)return;
        put(data,"schema",SCHEMA);
    }
    static void put(JSONObject o,String key,Object val) { try { o.put(key,val); } catch(JSONException e) { throw new IllegalArgumentException(e); } }
    JSONArray list(String key) { return data.optJSONArray(key); }
    List<JSONObject> objects(String key) { List<JSONObject> result=new ArrayList<>(); JSONArray a=list(key); for(int i=0;i<a.length();i++) result.add(a.optJSONObject(i)); return result; }
    JSONObject find(String key,int id) { for(JSONObject o:objects(key)) if(o.optInt("id")==id) return o; return null; }
    int id() { int n=data.optInt("id",100)+1; put(data,"id",n); return n; }
    void save() { save(true); }
    void save(boolean notify) {
        put(data,"schema",SCHEMA);Schedule.holidays=Schedule.holidays(data);
        if(!prefs().edit().putString("state",data.toString()).putLong(BOOT_ELAPSED,SystemClock.elapsedRealtime()).commit()) throw new IllegalStateException("Could not save clock data");
        if(!directBoot)mirrorDirectBoot();
        boolean running=objects("timers").stream().anyMatch(t->t.optBoolean("running"));
        // Stopping a service before it called startForeground crashes the app (start then quick cancel), so a pending start is let through: it promotes itself and stops.
        if(running||TimerService.pendingStart){TimerService.pendingStart=true;context.startForegroundService(new Intent(context,TimerService.class));}
        else context.stopService(new Intent(context,TimerService.class));
        UpcomingAlarm.refresh(context, this, System.currentTimeMillis());
        Widgets.update(context,this);
        if(notify){Runnable hook=onChanged;if(hook!=null)hook.run();}
    }
    /** Minimal schedule mirror for Direct Boot / pre-unlock delivery. */
    void mirrorDirectBoot() {
        try {
            JSONObject mirror=new JSONObject();
            JSONArray alarms=new JSONArray();
            for(JSONObject a:objects("alarms"))if(a.optBoolean("enabled")||a.optBoolean("ringing")){
                JSONObject m=new JSONObject();
                for(String k:List.of("id","name","hour","minute","days","enabled","next","ringing","ringBoot","snoozed","postponed","skipDate","sound","vibrate","autoDelete","snoozeCount","snoozeMinutes","snoozeLimit","ringStartedElapsed","autoSilenced","missed","tone"))
                    if(a.has(k))m.put(k,a.get(k));
                for(String k:Schedule.KEYS)if(a.has(k))m.put(k,a.get(k));
                alarms.put(m);
            }
            JSONArray timers=new JSONArray();
            for(JSONObject t:objects("timers"))if(t.optBoolean("running")||t.optBoolean("ringing")){
                JSONObject m=new JSONObject();
                for(String k:List.of("id","name","duration","remaining","running","paused","ringing","ringBoot","endElapsed","endWall","sound","vibrate","ringStartedElapsed","autoSilenced"))
                    if(t.has(k))m.put(k,t.get(k));
                timers.put(m);
            }
            put(mirror,"alarms",alarms);put(mirror,"timers",timers);if(data.has("holidays"))put(mirror,"holidays",data.get("holidays"));put(mirror,"id",data.optInt("id",100));put(mirror,"schema",SCHEMA);
            dePrefs().edit().putString("state",mirror.toString()).putLong(BOOT_ELAPSED,SystemClock.elapsedRealtime()).commit();
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
    /** Android's boot counter; -1 when unavailable. */
    static int bootCount(Context c){return android.provider.Settings.Global.getInt(c.getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,-1);}
    /**
     * This storage copy (credential or device-protected) has not been reconciled for the current boot.
     * Reads only its own storage, so it is safe before first unlock.
     */
    boolean needsRebootRecovery(){
        SharedPreferences p=prefs();int boot=bootCount(context);
        if(boot>=0&&p.contains(BOOT_COUNT))return p.getInt(BOOT_COUNT,-1)!=boot;
        // Stores written before boot counts were kept: elapsed realtime only goes backwards across a reboot.
        return !p.contains(BOOT_ELAPSED)||SystemClock.elapsedRealtime()<p.getLong(BOOT_ELAPSED,0);
    }
    /** Marks this copy reconciled; once unlocked the device-protected mirror is rewritten from it, so mark that too. */
    void markBootSeen(){
        long elapsed=SystemClock.elapsedRealtime();int boot=bootCount(context);
        prefs().edit().putLong(BOOT_ELAPSED,elapsed).putInt(BOOT_COUNT,boot).commit();
        if(!directBoot)dePrefs().edit().putLong(BOOT_ELAPSED,elapsed).putInt(BOOT_COUNT,boot).commit();
    }
    static void markBootSeen(Context context){new ClockStore(context).markBootSeen();}
    /** A ring left over from before the current boot; rings started this boot carry its boot count. */
    static boolean staleRing(JSONObject o,int boot){return o.optBoolean("ringing")&&(boot<0||o.optInt("ringBoot",-1)!=boot);}
    /** Next ring after {@code after}; 0 when the schedule has none left. See {@link Schedule}. */
    static long next(JSONObject a,long after){return Schedule.next(a,after);}
    /** Moves "next" to the following ring; a schedule with nothing left (past date or end) turns the alarm off. */
    void advance(JSONObject a,long after){long n=next(a,after);if(n==0){put(a,"enabled",false);put(a,"next",0);}else put(a,"next",n);}
    static long remaining(JSONObject t) { return t.optBoolean("running") ? Math.max(0,t.optLong("endElapsed")-SystemClock.elapsedRealtime()) : t.optLong("remaining"); }
    static int requestCode(String kind,int id,String action) {
        return Objects.hash(kind,id,action)&0x7fffffff;
    }
    PendingIntent pending(String kind,int id,String action) {
        // Foreground delivery: notification and island buttons should act without background-queue delay.
        Intent i=new Intent(context,ClockReceiver.class).setAction(action).setData(android.net.Uri.parse("clockprime://"+kind+"/"+id+"/"+action)).putExtra("kind",kind).putExtra("id",id).addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        return PendingIntent.getBroadcast(context,requestCode(kind,id,action),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    /**
     * System-delivered activity start so the ring UI can appear while the screen is unlocked.
     * Request code differs from broadcast FIRE so both can be armed together.
     */
    PendingIntent fireActivity(String kind,int id){
        Intent i=new Intent(context,RingActivity.class).setAction("FIRE")
                .setData(android.net.Uri.parse("clockprime://ring/"+kind+"/"+id+"/FIRE"))
                .putExtra("kind",kind).putExtra("id",id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP
                        |Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        return PendingIntent.getActivity(context,requestCode(kind,id,"FIRE_UI"),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    PendingIntent open() { return PendingIntent.getActivity(context,0,new Intent(context,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); }
    void schedule(String kind,JSONObject o) {
        AlarmManager manager=context.getSystemService(AlarmManager.class); int id=o.optInt("id");
        PendingIntent broadcast=pending(kind,id,"FIRE");PendingIntent ui=fireActivity(kind,id);
        manager.cancel(broadcast);manager.cancel(ui);
        boolean armAlarms=kind.equals("alarms") && o.optBoolean("enabled") && o.optLong("next")>0;
        boolean armTimers=kind.equals("timers") && o.optBoolean("running");
        if(!armAlarms&&!armTimers){
            if(kind.equals("timers"))syncTimerNotification(o);
            return;
        }
        if(!manager.canScheduleExactAlarms()) throw new IllegalStateException(context.getString(R.string.exact_required));
        long when=armAlarms?o.optLong("next"):System.currentTimeMillis()+remaining(o);
        // Broadcast keeps state/sound reliable; exact activity PI is what opens the UI when unlocked.
        manager.setAlarmClock(new AlarmManager.AlarmClockInfo(when,open()),broadcast);
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,ui);
        if(kind.equals("alarms"))DebugLog.log(context,"schedule","alarm "+id+" at "+Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()).toLocalDateTime());
        if(armTimers){
            // Update in place: cancelling first makes the island collapse and re-announce.
            RingService.channels(context);
            notifyTimer(o);
        }
    }
    /** One notification per running timer; {@link TimerService} reuses it as its foreground notification. */
    Notification timerNotification(JSONObject o){int id=o.optInt("id");String name=o.optString("name",context.getString(R.string.timer));
        boolean running=o.optBoolean("running");long left=remaining(o),end=System.currentTimeMillis()+left;
        PendingIntent toggle=pending("timers",id,running?"PAUSE":"RESUME"),cancel=pending("timers",id,"CANCEL");
        String toggleLabel=context.getString(running?R.string.pause:R.string.resume);
        Notification.Builder b=new Notification.Builder(context,RingService.TIMER_CHANNEL).setSmallIcon(R.drawable.ic_stat_notify).setContentTitle(name).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_STOPWATCH).setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(open()).addAction(0,toggleLabel,toggle).addAction(0,context.getString(R.string.cancel),cancel);
        if(running)b.addAction(0,context.getString(R.string.add_minute),pending("timers",id,"ADD_MINUTE"));
        if(running)b.setContentText(context.getString(R.string.timer_running)).setWhen(end).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true);
        else b.setContentText(MainActivity.duration(left)+" · "+context.getString(R.string.timer_paused)).setShowWhen(false);
        Notification n=b.build();
        FocusIsland.attachTimer(context,n,name,end,o.optLong("duration"),running,toggle,cancel);
        return n;
    }
    /** Running and paused timers keep a notification (and Xiaomi island); others have none. */
    static boolean timerShown(JSONObject t){return t.optBoolean("running")||(t.optBoolean("paused")&&!t.optBoolean("ringing"));}
    void syncTimerNotification(JSONObject t){if(t!=null&&timerShown(t)){RingService.channels(context);notifyTimer(t);}else if(t!=null)context.getSystemService(NotificationManager.class).cancel(t.optInt("id"));}
    void timerPause(JSONObject t){put(t,"remaining",remaining(t));put(t,"running",false);put(t,"paused",true);schedule("timers",t);}
    void notifyTimer(JSONObject o){context.getSystemService(NotificationManager.class).notify(o.optInt("id"),timerNotification(o));}
    static final int STOPWATCH_ID=4;
    long stopwatchTime(){return data.optLong("swTotal")+(data.optBoolean("swRunning")?SystemClock.elapsedRealtime()-data.optLong("swStart"):0);}
    void stopwatchToggle(){put(data,"swTotal",stopwatchTime());put(data,"swStart",SystemClock.elapsedRealtime());put(data,"swRunning",!data.optBoolean("swRunning"));}
    void stopwatchLap(){JSONArray laps=data.optJSONArray("laps");if(laps==null){laps=new JSONArray();put(data,"laps",laps);}laps.put(stopwatchTime());}
    void stopwatchReset(){put(data,"swRunning",false);put(data,"swTotal",0);put(data,"swStart",0);put(data,"laps",new JSONArray());}
    PendingIntent stopwatchAction(String action){
        Intent i=new Intent(context,ClockReceiver.class).setAction(action).addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        return PendingIntent.getBroadcast(context,requestCode("stopwatch",0,action),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    /** A running or paused stopwatch keeps a notification (and HyperOS island); reset removes it. */
    void syncStopwatch(){
        NotificationManager nm=context.getSystemService(NotificationManager.class);boolean running=data.optBoolean("swRunning");long elapsed=stopwatchTime();
        if(!running&&elapsed==0){nm.cancel(STOPWATCH_ID);return;}
        RingService.channels(context);
        JSONArray laps=data.optJSONArray("laps");int lapCount=laps==null?0:laps.length();String title=context.getString(R.string.stopwatch);
        PendingIntent toggle=stopwatchAction(running?"SW_PAUSE":"SW_RESUME"),second=stopwatchAction(running?"SW_LAP":"SW_RESET");
        PendingIntent open=PendingIntent.getActivity(context,STOPWATCH_ID,new Intent(context,MainActivity.class).putExtra("tab","Stopwatch").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(context,RingService.STOPWATCH_CHANNEL).setSmallIcon(R.drawable.ic_stat_notify).setContentTitle(title).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_STOPWATCH).setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(open)
                .addAction(0,context.getString(running?R.string.pause:R.string.resume),toggle).addAction(0,context.getString(running?R.string.lap:R.string.reset),second);
        String status=lapCount>0?context.getString(R.string.lap_number,lapCount):context.getString(running?R.string.stopwatch_running:R.string.timer_paused);
        if(running)b.setUsesChronometer(true).setWhen(System.currentTimeMillis()-elapsed).setShowWhen(true).setContentText(status);
        else b.setShowWhen(false).setContentText(MainActivity.elapsed(elapsed)+" · "+status);
        Notification n=b.build();FocusIsland.attachStopwatch(context,n,title,elapsed,running,toggle,second);
        nm.notify(STOPWATCH_ID,n);
    }
    void refreshLocalizedNotifications(){RingService.channels(context);syncStopwatch();for(JSONObject t:objects("timers"))if(timerShown(t))notifyTimer(t);UpcomingAlarm.refresh(context,this,System.currentTimeMillis());Widgets.update(context,this);}
    void enable(JSONObject a,boolean enabled) { put(a,"snoozeCount",0);put(a,"autoSilenced",false); put(a,"enabled",enabled); put(a,"ringing",false); put(a,"snoozed",false); put(a,"postponed",false); a.remove("skipDate"); a.remove("skipDisabled"); a.remove("paused"); long n=enabled?next(a,System.currentTimeMillis()):0; put(a,"enabled",enabled&&n>0); put(a,"next",n); schedule("alarms",a); }
    void skipOnce(JSONObject a) {
        if(!a.optBoolean("enabled"))return;
        long due=Math.max(System.currentTimeMillis(),a.optLong("next"));
        LocalDate skipped=Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate();
        put(a,"ringing",false);put(a,"snoozed",false);put(a,"postponed",false);
        if(!Schedule.repeats(a)){if(autoDelete(a))remove("alarms",a);else {enable(a,false);put(a,"skipDisabled",true);put(a,"skipDate",skipped.toString());}}
        else {put(a,"skipDate",skipped.toString());advance(a,due);schedule("alarms",a);}
    }
    /** Pauses a repeating alarm through {@code until} (inclusive); it resumes by itself afterwards. One-time alarms are left alone. */
    void pause(JSONObject a,LocalDate until){
        if(!Schedule.repeats(a)||until==null)return;
        put(a,"ringing",false);put(a,"snoozed",false);put(a,"postponed",false);put(a,"enabled",true);a.remove("skipDisabled");
        put(a,"skipDate",until.toString());put(a,"paused",true);advance(a,System.currentTimeMillis());schedule("alarms",a);
    }
    /** Pauses through the {@code count}-th upcoming ring of this alarm. */
    void pauseRings(JSONObject a,int count){pause(a,Schedule.nthRing(a,count,System.currentTimeMillis()));}
    void restoreSkips(JSONObject a) {
        if(a.optString("skipDate").isEmpty())return;
        boolean resumeOnce=a.optBoolean("skipDisabled");
        a.remove("skipDate");a.remove("skipDisabled");a.remove("paused");
        if(resumeOnce)put(a,"enabled",true);
        if(a.optBoolean("enabled")&&!a.optBoolean("ringing")&&!a.optBoolean("snoozed")&&!a.optBoolean("postponed"))
            advance(a,System.currentTimeMillis());
        schedule("alarms",a);
    }
    boolean collapsed(String group) { JSONObject folded=data.optJSONObject("collapsedGroups");return !group.isEmpty()&&folded!=null&&folded.optBoolean(group); }
    void collapse(String group,boolean value) { if(group.isEmpty())return;JSONObject folded=data.optJSONObject("collapsedGroups");if(folded==null){folded=new JSONObject();put(data,"collapsedGroups",folded);}put(folded,group,value); }
    /** Validate the whole selection before mutating anything. An empty group is its own scope. */
    void bulk(String group,Set<Integer> ids,String action) {
        if(!Set.of("ON","OFF","SKIP","DELETE").contains(action))throw new IllegalArgumentException("Unknown selection action");
        List<JSONObject> targets=new ArrayList<>();
        for(int id:ids){JSONObject a=find("alarms",id);if(a==null||!a.optString("group").equals(group))throw new IllegalArgumentException(context.getString(R.string.one_group_only));targets.add(a);}
        for(JSONObject a:targets)switch(action){
            case "ON":if(!a.optBoolean("enabled"))enable(a,true);break;
            case "OFF":enable(a,false);break;
            case "SKIP":skipOnce(a);break;
            case "DELETE":remove("alarms",a);break;
        }
    }
    /** Adds (or with a negative value removes) time on a running or paused timer; never below one second left. */
    boolean timerAdjust(JSONObject t,long delta){
        if(t.optBoolean("ringing")||!(t.optBoolean("running")||t.optBoolean("paused")))return false;
        long left=remaining(t),target=Math.max(1000,left+delta);if(target==left)return false;long change=target-left;
        if(t.optBoolean("running")){put(t,"endElapsed",t.optLong("endElapsed")+change);put(t,"endWall",t.optLong("endWall")+change);}
        put(t,"remaining",target);schedule("timers",t);return true;
    }
    void timerStart(JSONObject t,long duration) { put(t,"autoSilenced",false);t.remove("ringStartedElapsed"); put(t,"remaining",duration); put(t,"running",true); put(t,"paused",false); put(t,"ringing",false); put(t,"endElapsed",SystemClock.elapsedRealtime()+duration); put(t,"endWall",System.currentTimeMillis()+duration); schedule("timers",t); }
    boolean autoDelete(JSONObject a){return !Schedule.repeats(a)&&a.optBoolean("autoDelete",false);}
    void dismiss(String kind,JSONObject o) { if(kind.equals("alarms")&&autoDelete(o)){remove(kind,o);return;} put(o,"ringing",false);put(o,"snoozeCount",0); if(kind.equals("timers")) {put(o,"running",false);put(o,"paused",false);put(o,"remaining",o.optLong("duration")); schedule(kind,o);} }
    void snooze(JSONObject a,int minutes) { if(!new ClockSettings(context).canSnooze(a))return;put(a,"snoozeCount",a.optInt("snoozeCount")+1);a.remove("ringStartedElapsed"); put(a,"ringing",false); put(a,"enabled",true); put(a,"snoozed",true); put(a,"next",System.currentTimeMillis()+minutes*60000L); schedule("alarms",a); }
    void remove(String kind,JSONObject o) { put(o,"enabled",false);put(o,"running",false);put(o,"paused",false); schedule(kind,o);JSONArray arr=list(kind);for(int i=0;i<arr.length();i++)if(arr.optJSONObject(i).optInt("id")==o.optInt("id")){arr.remove(i);break;} }
    boolean ringing() { for(String k:List.of("alarms","timers"))for(JSONObject o:objects(k))if(o.optBoolean("ringing"))return true;return false; }
    void refreshSound() { if(!ringing())context.stopService(new Intent(context,RingService.class));else context.startForegroundService(new Intent(context,RingService.class)); }
    /**
     * After unlock, adopts what the device-protected copy did while the phone was still locked this
     * boot (rang, snoozed, dismissed, advanced). Returns true when anything was adopted.
     */
    boolean reconcileAfterUnlock() {
        if(directBoot)return false;
        int boot=bootCount(context);SharedPreferences de=dePrefs();
        if(boot<0||de.getInt(BOOT_COUNT,-1)!=boot||prefs().getInt(BOOT_COUNT,-2)==boot)return false;
        try {
            JSONObject mirror=new JSONObject(de.getString("state","{}"));boolean adopted=false;
            for(String kind:List.of("alarms","timers")){JSONArray copies=mirror.optJSONArray(kind);if(copies==null)continue;
                for(int i=0;i<copies.length();i++){JSONObject m=copies.optJSONObject(i);JSONObject local=m==null?null:find(kind,m.optInt("id"));if(local==null)continue;
                    for(Iterator<String> keys=m.keys();keys.hasNext();){String key=keys.next();put(local,key,m.opt(key));}adopted=true;}}
            if(adopted)DebugLog.log(context,"restore","adopted changes made before unlock");
            return adopted;
        } catch (JSONException e) {DebugLog.error(context,"restore",e);return false;}
    }
    void restore(boolean reboot,boolean clockChanged) {
        // Each copy is recovered once per boot by whichever entry point sees the new boot first
        // (locked boot, unlock, boot completed or opening the app).
        reconcileAfterUnlock();
        reboot|=needsRebootRecovery();int boot=bootCount(context);
        if(reboot)DebugLog.log(context,"restore","reboot recovery in "+(directBoot?"device-protected":"credential")+" storage");
        long now=System.currentTimeMillis();
        for(JSONObject a:objects("alarms")) {
            if(reboot&&autoDelete(a)&&staleRing(a,boot)){remove("alarms",a);continue;}
            if(a.optBoolean("enabled") && ((clockChanged&&!a.optBoolean("snoozed")&&!a.optBoolean("postponed"))||((reboot||clockChanged)&&a.optLong("next")<=now))) {
                if(a.optLong("next")<=now&&autoDelete(a)){remove("alarms",a);continue;}
                if(a.optLong("next")<=now) put(a,"missed",true);
                if(!Schedule.repeats(a) && a.optLong("next")<=now) {put(a,"enabled",false);put(a,"next",0);} else advance(a,now);
            } schedule("alarms",a);
        }
        for(JSONObject t:objects("timers")) {
            if(reboot&&t.optBoolean("running")) {
                long left=t.optLong("endWall")-now;
                if(left<=0){put(t,"running",false);put(t,"remaining",0);put(t,"missed",true);}else put(t,"endElapsed",SystemClock.elapsedRealtime()+left);
            } schedule("timers",t);
        }
        if(reboot) {put(data,"swRunning",false);put(data,"swStart",0);for(String k:List.of("alarms","timers"))for(JSONObject o:objects(k))if(staleRing(o,boot))put(o,"ringing",false);}
        save(true);
        if(reboot)syncStopwatch();
        markBootSeen();
    }
}
