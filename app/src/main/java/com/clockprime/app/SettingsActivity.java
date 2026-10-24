package com.clockprime.app;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;

/** Full-screen settings with app-owned sound preferences and native ringtone selection. */
public class SettingsActivity extends Activity {
    boolean dark,advanced;int bg,card,text,muted,accent,line;LinearLayout body;ScrollView scroll;ClockSettings prefs;
    Ringtone preview;final Handler handler=new Handler(Looper.getMainLooper());int savedScroll;
    String tr(int id,Object... args){return args.length==0?getString(id):getString(id,args);}
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    String minutes(int value){return value==0?tr(R.string.never):getResources().getQuantityString(R.plurals.minutes_value,value,value);}
    String durationLabel(int minutes){
        if(minutes==0)return tr(R.string.never);
        if(minutes%60==0){int hours=minutes/60;return getResources().getQuantityString(R.plurals.hours_value,hours,hours);}
        return getResources().getQuantityString(R.plurals.minutes_value,minutes,minutes);
    }
    @Override public void onCreate(Bundle b){dark=ThemeChoice.dark(this);setTheme(dark?R.style.AppTheme_Dark:R.style.AppTheme_Light);super.onCreate(b);prefs=new ClockSettings(this);if(b!=null){advanced=b.getBoolean("advanced");savedScroll=b.getInt("scroll");}setVolumeControlStream(AudioManager.STREAM_ALARM);getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,this::back);}
    Boolean islandAllowed;
    @Override public void onResume(){super.onResume();render();SetupGuide.refreshOpen();registerReceiver(volumeChanged,new IntentFilter("android.media.VOLUME_CHANGED_ACTION"),Context.RECEIVER_EXPORTED);
        if(FocusIsland.shouldAttach(this))new Thread(()->{Boolean allowed=FocusIsland.canShowFocus(this);handler.post(()->{if(!isFinishing()&&!java.util.Objects.equals(allowed,islandAllowed)){islandAllowed=allowed;render();}});},"island-check").start();}
    @Override public void onPause(){stopPreview();unregisterReceiver(volumeChanged);super.onPause();}
    @Override public void onSaveInstanceState(Bundle b){b.putBoolean("advanced",advanced);b.putInt("scroll",scroll==null?0:scroll.getScrollY());super.onSaveInstanceState(b);}
    void back(){finish();}
    @Override public void onBackPressed(){back();}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    TextView label(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    void gap(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    void render(){
        int offset=scroll==null?savedScroll:scroll.getScrollY();savedScroll=0;
        bg=Color.parseColor(dark?"#101722":"#F3F6FC");card=Color.parseColor(dark?"#192334":"#FFFFFF");text=Color.parseColor(dark?"#EEF3FF":"#17253C");muted=Color.parseColor(dark?"#A0ADC3":"#617189");accent=Color.parseColor(dark?"#9CBCFF":"#386BC0");line=Color.parseColor(dark?"#2B3950":"#E1E7F0");
        LinearLayout root=column();root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.parseColor(dark?"#18263F":"#E7F0FF"),bg}));root.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets in=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(dp(20)+in.left,in.top,dp(20)+in.right,in.bottom);return i;});setContentView(root);
        WindowInsetsController bars=getWindow().getInsetsController();if(bars!=null)bars.setSystemBarsAppearance(dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        LinearLayout header=row();ImageButton back=new ImageButton(this);back.setImageDrawable(new ClockIcon("back",accent));back.setPadding(dp(12),dp(12),dp(12),dp(12));back.setBackground(new RippleDrawable(ColorStateList.valueOf(0x209CBCFF),shape(Color.TRANSPARENT,16),null));back.setContentDescription(tr(R.string.back));back.setOnClickListener(v->back());header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=label(tr(R.string.settings),26,text);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));header.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(header,new LinearLayout.LayoutParams(-1,dp(80)));
        scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);body=column();body.setPadding(0,dp(8),0,dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));general();root.requestApplyInsets();scroll.post(()->scroll.scrollTo(0,offset));
    }
    LinearLayout section(int title){TextView heading=label(tr(title),12,muted);heading.setPadding(dp(8),dp(20),0,dp(10));body.addView(heading);LinearLayout c=column();GradientDrawable d=shape(card,22);d.setStroke(dp(1),line);c.setBackground(d);c.setPadding(dp(16),dp(4),dp(16),dp(4));body.addView(c,new LinearLayout.LayoutParams(-1,-2));return c;}
    void divider(LinearLayout c){if(c.getChildCount()>0){View v=new View(this);v.setBackgroundColor(line);c.addView(v,new LinearLayout.LayoutParams(-1,dp(1)));}}
    static final String LICENSE_URL="https://www.gnu.org/licenses/gpl-3.0.html",SOURCE_URL="https://github.com/mexusbg/ClockPrime",KOFI_URL="https://ko-fi.com/mexus";
    /** Warm gradient pill: coffee cup + "Support ClockPrime on Ko-fi". */
    android.widget.LinearLayout supportButton(){
        int ink=Color.parseColor("#2A1606");
        LinearLayout b=row();b.setGravity(Gravity.CENTER);b.setPadding(dp(20),0,dp(20),0);
        GradientDrawable fill=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.parseColor("#F5CE8D"),Color.parseColor("#F2A07B"),Color.parseColor("#E9837E")});fill.setCornerRadius(dp(29));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF),fill,null));b.setElevation(dp(2));
        ImageView cup=new ImageView(this);cup.setImageDrawable(new ClockIcon("coffee",ink));cup.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);b.addView(cup,new LinearLayout.LayoutParams(dp(24),dp(24)));
        TextView t=label(tr(R.string.support_kofi),16,ink);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));t.setPadding(dp(10),0,0,0);b.addView(t);
        b.setContentDescription(tr(R.string.support_kofi));b.setFocusable(true);b.setClickable(true);b.setOnClickListener(v->openLink(KOFI_URL));b.setTag("support");
        return b;
    }
    void openLink(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(android.content.ActivityNotFoundException e){android.widget.Toast.makeText(this,url,android.widget.Toast.LENGTH_LONG).show();}}
    void item(LinearLayout c,int title,String value,String detail,Runnable action){
        divider(c);LinearLayout r=row();r.setPadding(0,dp(12),0,dp(12));r.setMinimumHeight(dp(56));LinearLayout words=column();words.addView(label(tr(title),16,text));if(value!=null){gap(words,5);words.addView(label(value,14,accent));}if(detail!=null){gap(words,5);words.addView(label(detail,12,muted));}r.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        if(action!=null){ImageView arrow=new ImageView(this);arrow.setImageDrawable(new ClockIcon("right",muted));r.addView(arrow,new LinearLayout.LayoutParams(dp(22),dp(22)));r.setBackground(new RippleDrawable(ColorStateList.valueOf(0x209CBCFF),null,shape(Color.WHITE,12)));r.setFocusable(true);r.setOnClickListener(v->action.run());}c.addView(r);
    }
    void toggle(LinearLayout c,int title,String detail,String key,boolean fallback){divider(c);LinearLayout r=row();r.setPadding(0,dp(14),0,dp(14));LinearLayout words=column();words.addView(label(tr(title),16,text));if(detail!=null){gap(words,5);words.addView(label(detail,12,muted));}r.addView(words,new LinearLayout.LayoutParams(0,-2,1));Switch value=new Switch(this);value.setChecked(prefs.flag(key,fallback));value.setContentDescription(tr(title));value.setMinimumHeight(dp(48));r.addView(value);value.setOnCheckedChangeListener((v,on)->{prefs.put(key,on);changed();});r.setOnClickListener(v->value.toggle());c.addView(r);}
    void volume(LinearLayout c,String kind){if(kind.equals("alarms")){alarmVolume(c);return;}divider(c);LinearLayout controls=column();controls.setPadding(0,dp(16),0,dp(12));LinearLayout r=row();LinearLayout words=column();words.addView(label(tr(R.string.timer_volume),16,text));words.addView(label(tr(R.string.timer_volume_detail),12,muted));r.addView(words,new LinearLayout.LayoutParams(0,-2,1));TextView amount=label(percent(prefs.volume(kind)),14,accent);r.addView(amount);controls.addView(r);SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(prefs.volume(kind));slider.setMinimumHeight(dp(48));slider.setContentDescription(tr(R.string.timer_volume));slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int value,boolean user){amount.setText(percent(value));if(user){if(preview!=null)preview.setVolume(value/100f);else scheduleVolumePreview(kind,value);}}
            public void onStartTrackingTouch(SeekBar s){previewAt(kind,s.getProgress(),0);}
            public void onStopTrackingTouch(SeekBar s){prefs.put("timer_volume",s.getProgress());changed();previewAt(kind,s.getProgress(),1000);}
        });controls.addView(slider);c.addView(controls);}
    SeekBar alarmSlider;TextView alarmAmount;boolean alarmTracking;
    AudioManager audio(){return getSystemService(AudioManager.class);}
    String alarmPercent(){return percent(Math.round(100f*audio().getStreamVolume(AudioManager.STREAM_ALARM)/audio().getStreamMaxVolume(AudioManager.STREAM_ALARM)));}
    /** Sets Android's alarm volume, shared with every alarm app; the slider spans the phone's own steps. */
    void setAlarmVolume(int index){
        try{audio().setStreamVolume(AudioManager.STREAM_ALARM,index,0);}catch(SecurityException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        if(alarmAmount!=null)alarmAmount.setText(alarmPercent());
    }
    void alarmVolume(LinearLayout c){
        divider(c);LinearLayout controls=column();controls.setPadding(0,dp(16),0,dp(12));LinearLayout r=row();LinearLayout words=column();words.addView(label(tr(R.string.alarm_volume),16,text));words.addView(label(tr(R.string.alarm_volume_detail),12,muted));r.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        alarmAmount=label(alarmPercent(),14,accent);r.addView(alarmAmount);controls.addView(r);
        int min=audio().getStreamMinVolume(AudioManager.STREAM_ALARM);
        alarmSlider=new SeekBar(this);alarmSlider.setMax(audio().getStreamMaxVolume(AudioManager.STREAM_ALARM)-min);alarmSlider.setProgress(audio().getStreamVolume(AudioManager.STREAM_ALARM)-min);alarmSlider.setMinimumHeight(dp(48));alarmSlider.setContentDescription(tr(R.string.alarm_volume));
        alarmSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int value,boolean user){if(user){setAlarmVolume(min+value);if(preview==null)scheduleVolumePreview("alarms",100);}}
            public void onStartTrackingTouch(SeekBar s){alarmTracking=true;previewAt("alarms",100,0);}
            public void onStopTrackingTouch(SeekBar s){alarmTracking=false;changed();previewAt("alarms",100,1000);}
        });controls.addView(alarmSlider);c.addView(controls);
    }
    /** Keeps the slider in step when the volume changes in Android settings or with the volume keys. */
    final BroadcastReceiver volumeChanged=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
        if(intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE",-1)!=AudioManager.STREAM_ALARM||alarmSlider==null||alarmTracking)return;
        alarmSlider.setProgress(audio().getStreamVolume(AudioManager.STREAM_ALARM)-audio().getStreamMinVolume(AudioManager.STREAM_ALARM));alarmAmount.setText(alarmPercent());
    }};
    void scheduleVolumePreview(String kind,int level){handler.removeCallbacksAndMessages("volumePreview");handler.postDelayed(()->previewAt(kind,level,0),"volumePreview",120);}
    String percent(int n){return java.text.NumberFormat.getPercentInstance(LocaleText.locale(this)).format(n/100d);}
    void changed(){ClockStore s=new ClockStore(this);UpcomingAlarm.refresh(this,s,System.currentTimeMillis());Widgets.update(this,s);if(s.ringing())s.refreshSound();}
    String toneName(String kind){Uri uri=prefs.tone(kind);if(uri==null)return tr(R.string.silent);if(RingtoneManager.isDefault(uri))return tr(R.string.system_alarm_sound);try{Ringtone tone=RingtoneManager.getRingtone(this,uri);if(tone!=null)return tone.getTitle(this);}catch(Exception ignored){}return tr(R.string.sound_unavailable);}
    void gradual(LinearLayout parent,String kind){
        String key=ClockSettings.prefix(kind);divider(parent);LinearLayout r=row();r.setPadding(0,dp(12),0,dp(12));TextView title=label(tr(R.string.gradual_volume),16,text);r.addView(title,new LinearLayout.LayoutParams(0,-2,1));Switch enabled=new Switch(this);enabled.setContentDescription(tr(R.string.gradual_volume));enabled.setMinimumHeight(dp(48));enabled.setChecked(prefs.flag(key+"_ascending",false));r.addView(enabled);parent.addView(r);
        LinearLayout duration=column();item(duration,R.string.ramp_duration,getResources().getQuantityString(R.plurals.seconds_value,prefs.rampSeconds(kind),prefs.rampSeconds(kind)),tr(R.string.ramp_detail),()->{int[] values={10,30,60,120};String[] labels=new String[values.length];int selected=0;for(int i=0;i<values.length;i++){labels[i]=getResources().getQuantityString(R.plurals.seconds_value,values[i],values[i]);if(values[i]==prefs.rampSeconds(kind))selected=i;}new AlertDialog.Builder(this).setTitle(R.string.ramp_duration).setSingleChoiceItems(labels,selected,(d,i)->{prefs.put(key+"_ramp",values[i]);d.dismiss();changed();render();}).setNegativeButton(R.string.cancel,null).show();});parent.addView(duration);duration.setVisibility(enabled.isChecked()?View.VISIBLE:View.GONE);enabled.setOnCheckedChangeListener((v,on)->{prefs.put(key+"_ascending",on);duration.setVisibility(on?View.VISIBLE:View.GONE);changed();});r.setOnClickListener(v->enabled.toggle());
    }
    void general(){
        LinearLayout alarms=section(R.string.alarms);
        item(alarms,R.string.default_alarm_ringtone,toneName("alarms"),null,()->pickSound("alarms"));volume(alarms,"alarms");gradual(alarms,"alarms");
        toggle(alarms,R.string.vibrate_alarms,null,"alarm_vibrate",true);toggle(alarms,R.string.ring_in_dnd,tr(R.string.ring_in_dnd_detail),"alarm_dnd",true);
        item(alarms,R.string.alarm_auto_silence,minutes(prefs.number("alarm_silence",10)),null,()->chooseNumber(R.string.alarm_auto_silence,"alarm_silence",10,new int[]{0,1,2,5,10,15,30,60},false));
        item(alarms,R.string.snooze_duration,minutes(prefs.snoozeMinutes()),null,()->chooseNumber(R.string.snooze_duration,"snooze_minutes",5,new int[]{1,3,5,10,15,20,30},false));
        int count=prefs.number("snooze_limit",0);item(alarms,R.string.snooze_count,count==0?tr(R.string.unlimited):getResources().getQuantityString(R.plurals.times_value,count,count),null,()->chooseNumber(R.string.snooze_count,"snooze_limit",0,new int[]{0,1,2,3,5,10},true));
        String action=prefs.text("volume_buttons","volume");item(alarms,R.string.volume_buttons,tr(action.equals("snooze")?R.string.snooze_action:action.equals("dismiss")?R.string.dismiss:R.string.adjust_volume),tr(R.string.volume_buttons_detail),this::buttons);
        LinearLayout timers=section(R.string.timers);
        item(timers,R.string.default_timer_ringtone,toneName("timers"),null,()->pickSound("timers"));volume(timers,"timers");gradual(timers,"timers");toggle(timers,R.string.vibrate_timers,null,"timer_vibrate",true);
        item(timers,R.string.timer_auto_silence,minutes(prefs.number("timer_silence",10)),null,()->chooseNumber(R.string.timer_auto_silence,"timer_silence",10,new int[]{0,1,2,5,10,15,30,60},false));
        LinearLayout notifications=section(R.string.notifications_label);
        item(notifications,R.string.upcoming_toggle,durationLabel(prefs.upcomingMinutes()),tr(R.string.upcoming_static_detail),this::chooseUpcoming);
        // Meaningless without an upcoming notification.
        if(prefs.upcomingMinutes()>0)toggle(notifications,R.string.lock_screen_toggle,null,"lockscreen",false);
        reliability(getSystemService(NotificationManager.class));
        LinearLayout appearance=section(R.string.appearance);String mode=ThemeChoice.get(this);item(appearance,R.string.theme_label,tr(mode.equals("dark")?R.string.dark:mode.equals("light")?R.string.light:R.string.follow_system),null,this::theme);
        item(appearance,R.string.date_format_label,dateFormatLabel(),null,this::chooseDateFormat);
        item(appearance,R.string.hour_format_label,hourFormatLabel(),null,this::chooseHourFormat);
        item(appearance,R.string.edit_system_time,null,null,()->startActivity(new Intent(Settings.ACTION_DATE_SETTINGS)));
        LinearLayout backup=section(R.string.backup_section);
        item(backup,R.string.export_alarms,null,tr(R.string.export_alarms_detail),this::exportAlarms);
        item(backup,R.string.import_alarms,null,tr(R.string.import_alarms_detail),this::importAlarms);
        LinearLayout tools=section(R.string.troubleshooting);
        item(tools,R.string.test_alarm,null,null,this::scheduleTestAlarm);
        item(tools,R.string.export_logs,null,tr(R.string.export_logs_detail),()->DebugLog.share(this));
        String version;try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){version="";}
        // GPL-3.0 "Appropriate Legal Notices": copyright/attribution, no warranty, and where to read the license (LICENSE, section 7(b)).
        LinearLayout about=section(R.string.about_section);
        item(about,R.string.about_title,version,tr(R.string.about_license),()->openLink(LICENSE_URL));
        item(about,R.string.source_code,SOURCE_URL.replace("https://",""),null,()->openLink(SOURCE_URL));
        gap(body,14);body.addView(supportButton(),new LinearLayout.LayoutParams(-1,dp(58)));
        gap(body,20);TextView foot=label(tr(R.string.build_info,version),12,muted);foot.setGravity(Gravity.CENTER);body.addView(foot);
    }
    AlertDialog upcomingDialog;NumberPicker upcomingAmount,upcomingUnit;Switch upcomingOn;
    /** On/off plus amount and unit wheels: 1–59 minutes or 1–24 hours. */
    void chooseUpcoming(){
        int current=prefs.upcomingMinutes();int[] split=ClockSettings.splitMinutes(current==0?720:current);
        LinearLayout box=column();box.setPadding(dp(24),dp(8),dp(24),0);
        Switch on=new Switch(this);on.setText(tr(R.string.upcoming_enabled));on.setTextSize(16);on.setTextColor(text);on.setMinHeight(dp(48));on.setChecked(current>0);box.addView(on);
        LinearLayout pickers=row();pickers.setGravity(Gravity.CENTER);
        NumberPicker amount=new NumberPicker(this),unit=new NumberPicker(this);
        unit.setMinValue(0);unit.setMaxValue(1);unit.setDisplayedValues(new String[]{tr(R.string.unit_minutes),tr(R.string.unit_hours)});unit.setValue(split[1]);unit.setWrapSelectorWheel(false);
        amount.setMinValue(1);amount.setMaxValue(split[1]==1?24:59);amount.setValue(split[0]);
        // Lowering the maximum clamps the amount, e.g. 45 minutes becomes 24 hours.
        unit.setOnValueChangedListener((picker,old,value)->amount.setMaxValue(value==1?24:59));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(110),-2);lp.setMargins(dp(8),0,dp(8),0);
        pickers.addView(amount,lp);pickers.addView(unit,lp);box.addView(pickers);
        Runnable sync=()->{amount.setEnabled(on.isChecked());unit.setEnabled(on.isChecked());pickers.setAlpha(on.isChecked()?1f:.4f);};
        on.setOnCheckedChangeListener((b,checked)->sync.run());sync.run();
        upcomingAmount=amount;upcomingUnit=unit;upcomingOn=on;
        upcomingDialog=new AlertDialog.Builder(this).setTitle(R.string.upcoming_toggle).setView(box).setNegativeButton(R.string.cancel,null)
                .setPositiveButton(R.string.save,(d,w)->{prefs.put("upcoming_minutes",on.isChecked()?ClockSettings.toMinutes(amount.getValue(),unit.getValue()==1):0);changed();render();}).show();
    }
    String dateFormatLabel(){
        return switch(prefs.dateFormat()){
            case "dmy"->tr(R.string.date_format_dmy);
            case "mdy"->tr(R.string.date_format_mdy);
            case "ymd"->tr(R.string.date_format_ymd);
            default->tr(R.string.follow_system)+" · "+LocaleText.datePattern(this);
        };
    }
    String hourFormatLabel(){
        return switch(prefs.hourFormat()){
            case "24"->tr(R.string.hour_format_24);
            case "12"->tr(R.string.hour_format_12);
            default->tr(R.string.follow_system)+" · "+tr(prefs.use24Hour(this)?R.string.hour_format_24:R.string.hour_format_12);
        };
    }
    void chooseDateFormat(){
        String[] keys=ClockSettings.DATE_FORMATS;
        String[] labels={tr(R.string.follow_system),tr(R.string.date_format_dmy),tr(R.string.date_format_mdy),tr(R.string.date_format_ymd)};
        int selected=Arrays.asList(keys).indexOf(prefs.dateFormat());
        new AlertDialog.Builder(this).setTitle(R.string.date_format_label).setSingleChoiceItems(labels,Math.max(0,selected),(d,i)->{prefs.put("date_format",keys[i]);changed();d.dismiss();render();}).setNegativeButton(R.string.cancel,null).show();
    }
    void chooseHourFormat(){
        String[] keys=ClockSettings.HOUR_FORMATS;
        String[] labels={tr(R.string.follow_system),tr(R.string.hour_format_24),tr(R.string.hour_format_12)};
        int selected=Arrays.asList(keys).indexOf(prefs.hourFormat());
        new AlertDialog.Builder(this).setTitle(R.string.hour_format_label).setSingleChoiceItems(labels,Math.max(0,selected),(d,i)->{prefs.put("hour_format",keys[i]);changed();d.dismiss();render();}).setNegativeButton(R.string.cancel,null).show();
    }
    void reliability(NotificationManager nm){
        LinearLayout section=section(R.string.reliability);
        List<SetupGuide.Step> steps=SetupGuide.steps(this);item(section,R.string.setup_row,tr(R.string.setup_progress,SetupGuide.doneCount(this),steps.size()),null,()->new SetupGuide(this).show());
        // ClockPrime holds USE_EXACT_ALARM, so this only appears if a phone ever revokes it.
        if(!getSystemService(AlarmManager.class).canScheduleExactAlarms())item(section,R.string.exact_alarms_label,tr(R.string.exact_alarms_blocked),null,()->startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()))));
        item(section,R.string.notification_settings,tr(nm.areNotificationsEnabled()?R.string.allowed:R.string.blocked),null,()->startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())));
        if(Build.VERSION.SDK_INT>=34)item(section,R.string.fullscreen_label,tr(nm.canUseFullScreenIntent()?R.string.allowed:R.string.set_up),null,()->startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName()))));
        item(section,R.string.launch_label,tr(RingLaunch.canLaunch(this)?R.string.allowed:R.string.set_up),tr(R.string.launch_detail),()->RingLaunch.requestPermission(this));
        boolean unrestricted=getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        item(section,R.string.battery_restriction,tr(unrestricted?R.string.battery_unrestricted:R.string.battery_optimized),unrestricted?null:tr(R.string.battery_detail),()->SetupGuide.openBatterySettings(this));
        item(section,R.string.background_apps,null,tr(R.string.background_apps_detail),()->SetupGuide.openBackgroundApps(this));
        if(FocusIsland.shouldAttach(this)){String island=islandAllowed==null?FocusIsland.supportLabel(this):tr(islandAllowed?R.string.island_allowed:R.string.island_denied);item(section,R.string.island_label,island,tr(R.string.island_detail),null);}
    }
    void scheduleTestAlarm(){
        try{
            ClockStore s=new ClockStore(this);JSONObject a=new JSONObject();
            long when=System.currentTimeMillis()+15000;java.time.LocalTime at=java.time.Instant.ofEpochMilli(when).atZone(java.time.ZoneId.systemDefault()).toLocalTime();
            ClockStore.put(a,"id",s.id());ClockStore.put(a,"name",tr(R.string.test_alarm_name));ClockStore.put(a,"hour",at.getHour());ClockStore.put(a,"minute",at.getMinute());
            ClockStore.put(a,"days",0);ClockStore.put(a,"sound",true);ClockStore.put(a,"vibrate",true);ClockStore.put(a,"enabled",true);
            ClockStore.put(a,"autoDelete",true);ClockStore.put(a,"next",when);DebugLog.log(this,"settings","test alarm "+a.optInt("id"));
            s.list("alarms").put(a);s.schedule("alarms",a);s.save(true);
            Toast.makeText(this,R.string.test_alarm_scheduled,Toast.LENGTH_LONG).show();
        }catch(IllegalStateException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    void chooseNumber(int title,String key,int fallback,int[] values,boolean count){String[] labels=new String[values.length];int selected=0;for(int i=0;i<values.length;i++){int value=values[i];labels[i]=count?(value==0?tr(R.string.unlimited):getResources().getQuantityString(R.plurals.times_value,value,value)):minutes(value);if(value==prefs.number(key,fallback))selected=i;}new AlertDialog.Builder(this).setTitle(tr(title)).setSingleChoiceItems(labels,selected,(d,index)->{prefs.put(key,values[index]);changed();d.dismiss();render();}).setNegativeButton(tr(R.string.cancel),null).show();}
    void buttons(){String[] keys={"volume","snooze","dismiss"};String[] labels={tr(R.string.adjust_volume),tr(R.string.snooze_action),tr(R.string.dismiss)};new AlertDialog.Builder(this).setTitle(tr(R.string.volume_buttons)).setSingleChoiceItems(labels,Arrays.asList(keys).indexOf(prefs.text("volume_buttons","volume")),(d,i)->{prefs.put("volume_buttons",keys[i]);d.dismiss();render();}).setNegativeButton(tr(R.string.cancel),null).show();}
    void theme(){String[] labels={tr(R.string.follow_system),tr(R.string.light),tr(R.string.dark)};new AlertDialog.Builder(this).setTitle(tr(R.string.appearance)).setSingleChoiceItems(labels,Arrays.asList(ThemeChoice.MODES).indexOf(ThemeChoice.get(this)),(d,i)->{ThemeChoice.set(this,ThemeChoice.MODES[i]);d.dismiss();recreate();}).setNegativeButton(tr(R.string.cancel),null).show();}
    void pickSound(String kind){Intent i=new Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALARM).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,true).putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,prefs.tone(kind)).putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,tr(kind.equals("alarms")?R.string.default_alarm_ringtone:R.string.default_timer_ringtone));try{startActivityForResult(i,kind.equals("alarms")?201:202);}catch(ActivityNotFoundException e){Toast.makeText(this,R.string.picker_unavailable,Toast.LENGTH_LONG).show();}}
    void exportAlarms(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"clockprime-alarms-"+java.time.LocalDate.now()+".json");
        try{startActivityForResult(i,401);}catch(ActivityNotFoundException e){Toast.makeText(this,R.string.picker_unavailable,Toast.LENGTH_LONG).show();}
    }
    void importAlarms(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"});
        try{startActivityForResult(i,402);}catch(ActivityNotFoundException e){Toast.makeText(this,R.string.picker_unavailable,Toast.LENGTH_LONG).show();}
    }
    void backupResult(int request,Uri uri){
        try{
            if(request==401){try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")){out.write(AlarmBackup.create(this).toString(1).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                Toast.makeText(this,R.string.export_done,Toast.LENGTH_SHORT).show();return;}
            String text;try(java.io.InputStream in=getContentResolver().openInputStream(uri)){text=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
            org.json.JSONObject backup=AlarmBackup.parse(text);int count=backup.optJSONArray("alarms").length();String alarms=getResources().getQuantityString(R.plurals.alarm_count,count,count);
            new AlertDialog.Builder(this).setTitle(R.string.import_title).setMessage(tr(R.string.import_summary,alarms)).setNegativeButton(R.string.cancel,null)
                    .setPositiveButton(R.string.import_add,(d,w)->imported(backup,false))
                    .setNeutralButton(R.string.import_replace,(d,w)->new AlertDialog.Builder(this).setTitle(R.string.import_replace).setMessage(R.string.import_replace_confirm).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.import_replace,(x,y)->imported(backup,true)).show()).show();
        }catch(Exception e){DebugLog.error(this,"backup",e);Toast.makeText(this,request==401?e.toString():tr(R.string.import_failed),Toast.LENGTH_LONG).show();}
    }
    void imported(org.json.JSONObject backup,boolean replace){
        int added=AlarmBackup.restore(this,backup,replace);
        Toast.makeText(this,tr(R.string.import_done,getResources().getQuantityString(R.plurals.alarm_count,added,added)),Toast.LENGTH_LONG).show();
        if(replace)recreate();else render();
    }
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if((request==401||request==402)&&result==RESULT_OK&&data!=null&&data.getData()!=null){backupResult(request,data.getData());return;}if((request==201||request==202)&&result==RESULT_OK&&data!=null){Uri uri=data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI,Uri.class);prefs.put(request==201?"alarm_tone":"timer_tone",uri==null?"silent":RingtoneManager.isDefault(uri)?"default":uri.toString());changed();render();}}
    void preview(String kind){previewAt(kind,prefs.volume(kind),3000);}
    /** @param stopAfterMs 0 keeps playing until the next stop; otherwise auto-stops after that delay. */
    void previewAt(String kind,int level,long stopAfterMs){
        handler.removeCallbacksAndMessages("volumePreview");
        if(level<=0){stopPreview();return;}
        Uri uri=prefs.tone(kind);if(uri==null){stopPreview();return;}
        try{
            if(preview==null){preview=RingtoneManager.getRingtone(this,uri);if(preview==null)throw new IllegalArgumentException();preview.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());preview.setLooping(true);preview.play();}
            preview.setVolume(Math.max(0,Math.min(100,level))/100f);
            handler.removeCallbacksAndMessages("stopPreview");
            if(stopAfterMs>0)handler.postDelayed(this::stopPreview,"stopPreview",stopAfterMs);
        }catch(Exception e){stopPreview();Toast.makeText(this,R.string.sound_unavailable,Toast.LENGTH_LONG).show();}
    }
    void stopPreview(){handler.removeCallbacksAndMessages("volumePreview");handler.removeCallbacksAndMessages("stopPreview");if(preview!=null){preview.stop();preview=null;}}
}
