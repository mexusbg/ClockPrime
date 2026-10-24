package com.clockprime.app;
import android.content.*;
import android.media.RingtoneManager;
import android.net.Uri;
import android.text.format.DateFormat;
import org.json.JSONObject;

/** App-owned preferences; never changes the phone's default ringtone or stream volume. */
final class ClockSettings {
    static final String[] DATE_FORMATS={"system","dmy","mdy","ymd"};
    static final String[] HOUR_FORMATS={"system","24","12"};
    /** Tone value for ClockPrime's own sound; resolved at play time because raw resource ids change between builds. */
    static final String BUILTIN="builtin";
    final SharedPreferences prefs;final Context app;
    ClockSettings(Context c){
        app=c.getApplicationContext();
        boolean unlocked=app.getSystemService(android.os.UserManager.class).isUserUnlocked();
        prefs=(unlocked?app:app.createDeviceProtectedStorageContext()).getSharedPreferences("clock_settings",Context.MODE_PRIVATE);
    }
    static String prefix(String kind){return kind.equals("alarms")?"alarm":"timer";}
    int number(String key,int fallback){return prefs.getInt(key,fallback);}
    boolean flag(String key,boolean fallback){return prefs.getBoolean(key,fallback);}
    String text(String key,String fallback){return prefs.getString(key,fallback);}
    // commit(): settings are read immediately after writes (tones, upcoming, lock screen).
    void put(String key,int value){prefs.edit().putInt(key,value).commit();}
    void put(String key,boolean value){prefs.edit().putBoolean(key,value).commit();}
    void put(String key,String value){prefs.edit().putString(key,value).commit();}
    Uri tone(String kind){return resolve(text(prefix(kind)+"_tone","default"));}
    Uri resolve(String value){return value.equals("silent")?null:value.equals("default")?RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM):value.equals(BUILTIN)?RingService.builtIn(app):Uri.parse(value);}
    /** Alarms play at Android's shared alarm volume; timers at a percentage of it. */
    int volume(String kind){return kind.equals("alarms")?100:Math.max(0,Math.min(100,number("timer_volume",100)));}
    boolean vibrate(String kind){return flag(prefix(kind)+"_vibrate",true);}
    /** Alarms sound even when Do Not Disturb would silence them. */
    boolean ringInDnd(){return flag("alarm_dnd",true);}
    /** Android alarm volume in percent; while Do Not Disturb mutes the stream it reads 0, so the last audible level is kept. */
    int alarmLevel(Context c){
        android.media.AudioManager audio=c.getSystemService(android.media.AudioManager.class);int s=android.media.AudioManager.STREAM_ALARM,v=audio.getStreamVolume(s);
        if(audio.isStreamMute(s)||v<=0)return number("alarm_level",80);
        int level=Math.round(100f*v/audio.getStreamMaxVolume(s));if(level!=number("alarm_level",-1))put("alarm_level",level);return level;
    }
    long silenceMillis(String kind){return Math.max(0,number(prefix(kind)+"_silence",10))*60000L;}
    int snoozeMinutes(){return Math.max(1,Math.min(30,number("snooze_minutes",5)));}
    int snoozeMinutes(JSONObject a){return Math.max(1,Math.min(30,a.optInt("snoozeMinutes",snoozeMinutes())));}
    boolean canSnooze(JSONObject a){int limit=a.optInt("snoozeLimit",number("snooze_limit",0));return limit==0||a.optInt("snoozeCount")<limit;}
    String snoozeLabel(Context c){return c.getString(R.string.snooze_for,c.getResources().getQuantityString(R.plurals.minutes_value,snoozeMinutes(),snoozeMinutes()));}
    String snoozeLabel(Context c,JSONObject a){int n=snoozeMinutes(a);return c.getString(R.string.snooze_for,c.getResources().getQuantityString(R.plurals.minutes_value,n,n));}
    Uri tone(String kind,JSONObject a){String value=kind.equals("alarms")?a.optString("tone","inherit"):"inherit";return value.equals("inherit")?tone(kind):resolve(value);}
    int rampSeconds(String kind){int n=number(prefix(kind)+"_ramp",30);return n==10||n==30||n==60||n==120?n:30;}
    float gain(String kind,long elapsed){float target=volume(kind)/100f;if(!flag(prefix(kind)+"_ascending",false))return target;return target*Math.min(1f,.1f+.9f*Math.max(0,elapsed)/(rampSeconds(kind)*1000f));}
    /** Minutes before an alarm to show the upcoming notification; 0 disables. Default 12 hours. */
    int upcomingMinutes(){
        if(prefs.contains("upcoming_minutes"))return Math.max(0,Math.min(1440,number("upcoming_minutes",720)));
        return flag("upcoming",true)?720:0;
    }
    /** Lead time from the picker: 1–59 minutes or 1–24 hours. */
    static int toMinutes(int amount,boolean hours){return hours?Math.max(1,Math.min(24,amount))*60:Math.max(1,Math.min(59,amount));}
    /** Inverse of {@link #toMinutes}: {amount, 1 for hours or 0 for minutes}. */
    static int[] splitMinutes(int minutes){return minutes>=60&&minutes%60==0?new int[]{Math.min(24,minutes/60),1}:new int[]{Math.max(1,Math.min(59,minutes)),0};}
    long upcomingWindowMillis(){int n=upcomingMinutes();return n<=0?0:n*60000L;}
    String dateFormat(){String v=text("date_format","system");return java.util.Arrays.asList(DATE_FORMATS).contains(v)?v:"system";}
    String hourFormat(){String v=text("hour_format","system");return java.util.Arrays.asList(HOUR_FORMATS).contains(v)?v:"system";}
    boolean use24Hour(Context c){
        String mode=hourFormat();
        if("24".equals(mode))return true;
        if("12".equals(mode))return false;
        return DateFormat.is24HourFormat(c);
    }
    char[] dateOrder(Context c){
        return switch(dateFormat()){
            case "dmy"->new char[]{'d','M','y'};
            case "mdy"->new char[]{'M','d','y'};
            case "ymd"->new char[]{'y','M','d'};
            default->DateFormat.getDateFormatOrder(LocaleText.formatContext(c));
        };
    }
}
