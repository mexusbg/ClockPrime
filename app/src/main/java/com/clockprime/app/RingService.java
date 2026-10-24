package com.clockprime.app;
import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import org.json.JSONObject;
import java.util.List;

public class RingService extends Service {
    MediaPlayer tone;Vibrator vibrator;PowerManager.WakeLock wake;long toneStarted;boolean fallbackTone,mutedByDnd,muteChecked;int accessibilityRestore=-1;
    final Handler handler=new Handler(Looper.getMainLooper());String playingKey="",playingBase="",notificationKey="";long wakeRenewed;
    final Runnable tick=new Runnable(){public void run(){if(refresh(false))handler.postDelayed(this,500);}};
    static final String TIMER_CHANNEL="timer_status",UPCOMING_CHANNEL="next_alarm",SKIPPED_CHANNEL="skipped_alarms",STOPWATCH_CHANNEL="stopwatch";
    static NotificationChannel quiet(String id,String name){
        NotificationChannel channel=new NotificationChannel(id,name,NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(null,null);channel.enableVibration(false);channel.setShowBadge(false);channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        return channel;
    }
    static void channels(Context c){
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        // Fresh channel id so importance / FSI-related attrs apply even if an older alerts channel was muted.
        nm.deleteNotificationChannel("alerts");
        NotificationChannel alerts=new NotificationChannel("alerts_ring",c.getString(R.string.ringing_channel),NotificationManager.IMPORTANCE_HIGH);
        alerts.setSound(null,null);alerts.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);alerts.setBypassDnd(true);alerts.enableVibration(true);alerts.setDescription(c.getString(R.string.ringing_channel));
        nm.createNotificationChannel(alerts);
        // Silent but default importance: Android and HyperOS hide low-importance ("silent") notifications on the lock screen.
        nm.deleteNotificationChannel("timers");
        nm.createNotificationChannel(quiet(TIMER_CHANNEL,c.getString(R.string.timers_channel)));
        nm.createNotificationChannel(quiet(SKIPPED_CHANNEL,c.getString(R.string.skipped_channel)));
        nm.createNotificationChannel(quiet(STOPWATCH_CHANNEL,c.getString(R.string.stopwatch)));
    }
    /** Sticky so ordinary process death can recreate playback from persisted ringing state. */
    @Override public int onStartCommand(Intent intent,int flags,int id){channels(this);handler.removeCallbacks(tick);if(refresh(true))handler.postDelayed(tick,500);else stopSelf();return START_STICKY;}
    boolean refresh(boolean force){
        ClockStore store=new ClockStore(this);ClockSettings settings=new ClockSettings(this);long now=SystemClock.elapsedRealtime();
        // Unlocked while ringing: adopt the locked-boot ring before reading credential storage, or it would stop.
        if(store.reconcileAfterUnlock()){store.restore(false,false);store=new ClockStore(this);}
        if(RingPolicy.expire(store,settings,now))store.save(true);
        JSONObject first=null,audible=null;String kind="",audioKind="";int count=0;boolean vibrate=false;
        for(String k:List.of("alarms","timers"))for(JSONObject o:store.objects(k))if(o.optBoolean("ringing")){
            count++;if(first==null){first=o;kind=k;}
            if(audible==null&&o.optBoolean("sound",true)&&settings.tone(k,o)!=null&&settings.volume(k)>0){audible=o;audioKind=k;}
            vibrate|=o.optBoolean("vibrate",true)&&settings.vibrate(k);
        }
        if(first==null){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return false;}
        String nextNotification=kind+first.toString()+count+settings.snoozeMinutes(first)+settings.canSnooze(first);
        if(force||!notificationKey.equals(nextNotification)){
            PendingIntent full=RingLaunch.pending(this);
            String title=ringTitle(kind,first,count);
            String detail=ringDetail(kind,first);
            PendingIntent dismiss=kind.equals("alarms")&&Challenge.required(first)?full:store.pending(kind,first.optInt("id"),"DISMISS"),snooze=kind.equals("alarms")&&settings.canSnooze(first)?store.pending(kind,first.optInt("id"),"SNOOZE"):null;
            Notification.Builder n=new Notification.Builder(this,"alerts_ring").setSmallIcon(R.drawable.ic_stat_notify).setContentTitle(title).setContentText(detail).setStyle(new Notification.BigTextStyle().bigText(detail)).setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(full).setFullScreenIntent(full,true).setVisibility(Notification.VISIBILITY_PUBLIC).addAction(0,getString(R.string.dismiss),dismiss);
            if(snooze!=null)n.addAction(0,settings.snoozeLabel(this,first),snooze);
            String name=first.optString("name","").trim();
            String primary=kind.equals("alarms")?LocaleText.alarmTime(this,first.optInt("hour"),first.optInt("minute")):name.isEmpty()?getString(R.string.timer):name;
            String secondary=kind.equals("alarms")?name:getString(R.string.finished);
            Notification built=n.build();FocusIsland.attachRinging(this,built,title,primary,secondary,dismiss,snooze);startForeground(1,built);notificationKey=nextNotification;
            DebugLog.log(this,"ring-service","notification "+kind+" "+first.optInt("id")+" count="+count+" "+DebugLog.state(this));
            RingLaunch.open(this);
        }
        if(wake==null){wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"ClockPrime:ring");wake.setReferenceCounted(false);}
        if(!wake.isHeld()||now-wakeRenewed>60*60000L){wake.acquire(2*60*60000L);wakeRenewed=now;}
        String base=audible==null?"":audioKind+":"+audible.optInt("id")+":"+settings.tone(audioKind,audible);
        if(!base.equals(playingBase)){mutedByDnd=false;muteChecked=false;}
        boolean dnd=audible!=null&&audioKind.equals("alarms")&&settings.ringInDnd(),bypass=dnd&&(mutedByDnd||AlarmHealth.blocksAlarms(getSystemService(NotificationManager.class)));
        float gain=audible==null?0:settings.gain(audioKind,now-audible.optLong("ringStartedElapsed",now));String key=base+":"+bypass;
        if(!key.equals(playingKey)){release();playingKey=key;playingBase=base;
            if(audible!=null){fallbackTone=false;tone=start(loadTone(settings.tone(audioKind,audible),settings.tone(audioKind)),gain,now,bypass);if(tone==null){fallbackTone=true;tone=start(builtIn(this),gain,now,bypass);}}
        }
        // A tone that loaded but never started (missing file, codec, audio focus) switches to the built-in sound.
        if(tone!=null&&audible!=null&&!fallbackTone&&now-toneStarted>1500&&!tone.isPlaying()){
            DebugLog.log(this,"ring-service","tone not playing, using built-in sound "+audioState());
            release();fallbackTone=true;tone=start(builtIn(this),gain,now,bypass);
        }
        // Do Not Disturb modes the policy check cannot see (vendor or scheduled) show up as a muted player.
        if(tone!=null&&!muteChecked&&now-toneStarted>1500){muteChecked=true;String state=playerState();DebugLog.log(this,"ring-service","player "+state);
            if(dnd&&!bypass&&(state.contains("opPlayAudio")||state.contains("streamMute"))){mutedByDnd=true;DebugLog.log(this,"ring-service","muted by Do Not Disturb, ringing anyway");}}
        if(tone!=null)tone.setVolume(gain,gain);
        if(vibrate&&vibrator==null){vibrator=getSystemService(Vibrator.class);vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,450,500},0),new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());}
        if(!vibrate&&vibrator!=null){vibrator.cancel();vibrator=null;}
        return true;
    }
    String ringTitle(String kind,JSONObject item,int count){
        String type=getString(kind.equals("alarms")?R.string.alarm:R.string.timer);
        if(count>1)return getString(R.string.ringing_more,type,count-1);
        return type;
    }
    /** Alarm: time · label (label omitted when empty). Timer: label, or finished text. */
    String ringDetail(String kind,JSONObject item){
        String name=item.optString("name","").trim();
        if(kind.equals("alarms")){
            String time=LocaleText.alarmTime(this,item.optInt("hour"),item.optInt("minute"));
            return name.isEmpty()?time:time+" · "+name;
        }
        return name.isEmpty()?getString(R.string.timer_finished_notification):name;
    }
    static Uri builtIn(Context c){return Uri.parse("android.resource://"+c.getPackageName()+"/"+R.raw.fallback_alarm);}
    /**
     * MediaPlayer rather than Ringtone: Ringtone refuses to start while Do Not Disturb mutes the alarm stream.
     * Do Not Disturb never silences accessibility audio, so a blocked alarm plays there at the alarm volume.
     */
    MediaPlayer start(Uri uri,float volume,long now,boolean bypass){
        if(uri==null){DebugLog.log(this,"ring-service","no playable tone");return null;}
        MediaPlayer player=new MediaPlayer();
        try{if(bypass)matchAccessibilityVolume();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(bypass?AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY:AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(this,uri);player.setLooping(true);player.prepare();player.setVolume(volume,volume);player.start();toneStarted=now;
            DebugLog.log(this,"ring-service","tone started bypassDnd="+bypass+" "+audioState());return player;
        }catch(Exception e){DebugLog.log(this,"ring-service","tone failed "+uri+" "+e.getClass().getSimpleName());player.release();return null;}
    }
    void matchAccessibilityVolume(){
        AudioManager audio=getSystemService(AudioManager.class);int stream=AudioManager.STREAM_ACCESSIBILITY;
        int want=Math.max(1,Math.round(audio.getStreamMaxVolume(stream)*new ClockSettings(this).alarmLevel(this)/100f));
        if(accessibilityRestore<0)accessibilityRestore=audio.getStreamVolume(stream);
        try{audio.setStreamVolume(stream,want,0);}catch(RuntimeException e){DebugLog.log(this,"ring-service","accessibility volume "+e.getClass().getSimpleName());}
    }
    /** Why our player is muted, from the audio service (API 34+); apps see it anonymized, so it is matched by its attributes. */
    String playerState(){
        for(AudioPlaybackConfiguration c:getSystemService(AudioManager.class).getActivePlaybackConfigurations()){
            AudioAttributes a=c.getAudioAttributes();String line=c.toString();int at=line.indexOf("mutedState:");
            if(a.getContentType()==AudioAttributes.CONTENT_TYPE_SONIFICATION&&(a.getUsage()==AudioAttributes.USAGE_ALARM||a.getUsage()==AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY))return at<0?"active":line.substring(at).split(" ")[0];
        }
        return "not active";
    }
    String audioState(){AudioManager audio=getSystemService(AudioManager.class);return "alarmVolume="+audio.getStreamVolume(AudioManager.STREAM_ALARM)+" accessibilityVolume="+audio.getStreamVolume(AudioManager.STREAM_ACCESSIBILITY)+" muted="+audio.isStreamMute(AudioManager.STREAM_ALARM)+" ringer="+audio.getRingerMode()+" dndFilter="+getSystemService(NotificationManager.class).getCurrentInterruptionFilter();}
    void release(){if(tone!=null){try{tone.stop();}catch(IllegalStateException ignored){}tone.release();tone=null;}
        if(accessibilityRestore>=0){try{getSystemService(AudioManager.class).setStreamVolume(AudioManager.STREAM_ACCESSIBILITY,accessibilityRestore,0);}catch(RuntimeException ignored){}accessibilityRestore=-1;}}
    Uri loadTone(Uri uri,Uri fallback){
        // Before first unlock, user files may be unavailable; fall back to the system alarm tone, then the built-in sound.
        for(Uri candidate:new Uri[]{uri,fallback,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),builtIn(this)}){
            if(candidate==null)continue;
            try{if(!RingtoneManager.isDefault(candidate))try(android.content.res.AssetFileDescriptor fd=getContentResolver().openAssetFileDescriptor(candidate,"r")){if(fd==null)continue;}
                if(candidate!=uri)DebugLog.log(this,"ring-service","tone fallback to "+candidate);return candidate;
            }catch(Exception e){DebugLog.log(this,"ring-service","tone unavailable "+candidate+" "+e.getClass().getSimpleName());}
        }
        return null;
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration){super.onConfigurationChanged(configuration);channels(this);refresh(true);}
    @Override public void onDestroy(){handler.removeCallbacks(tick);release();if(vibrator!=null)vibrator.cancel();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
