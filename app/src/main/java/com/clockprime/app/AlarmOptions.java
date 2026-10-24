package com.clockprime.app;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.*;

/** Unsaved alarm options in grouped cards: Schedule, Sound, Dismiss, Snooze. All writes happen only after validation. */
final class AlarmOptions {
    final MainActivity a;final Spinner repeat,every,monthDay,puzzle,difficulty;final ToggleButton[] days=new ToggleButton[7];
    final Switch defaults,autoDelete,sound,vibrate,holidays;final Spinner interval,limit;final List<Integer> durations=new ArrayList<>();final TextView ringtone;
    final LinearLayout dayRow,snoozeFields,deleteRow,toneRow,dateRow,everyRow,monthRow,startRow,endRow,holidayRow,pauseRow,difficultyRow;String tone;final Set<Uri> acquired=new HashSet<>();
    /** Once: optional fixed date. Repeating: optional first and last day. Pause: through a date, or for a number of rings. */
    LocalDate onDate,startDate,endDate,pauseUntil;int pauseRings;
    static final int ONCE=0,DAILY=1,WEEKDAYS=2,CUSTOM=3,EVERY_DAYS=4,EVERY_WEEKS=5,MONTHLY=6;
    static final int[] LIMITS={1,2,3,5,10,0};
    /** Same choices as the default snooze duration in Settings. */
    static final int[] DURATIONS={1,3,5,10,15,20,30};
    static final int[] PAUSE_RINGS={1,2,3,5,7,10,14,30};
    AlarmOptions(MainActivity a,LinearLayout parent,JSONObject saved){
        this.a=a;ClockSettings settings=new ClockSettings(a);int mask=saved.optInt("days");tone=saved.optString("tone","inherit");
        onDate=Schedule.date(saved.optString("date"));startDate=Schedule.date(saved.optString("start"));endDate=Schedule.date(saved.optString("end"));
        pauseUntil=Schedule.date(saved.optString(saved.has("draftPause")?"draftPause":saved.optBoolean("paused")?"skipDate":""));pauseRings=saved.optInt("draftPauseRings");

        LinearLayout schedule=section(parent,R.string.schedule_section);
        repeat=spinner(new String[]{a.tr(R.string.once),a.tr(R.string.repeat_daily),a.tr(R.string.repeat_weekdays),a.tr(R.string.customize),a.tr(R.string.repeat_every_days),a.tr(R.string.repeat_every_weeks),a.tr(R.string.repeat_monthly)});repeat.setContentDescription(a.tr(R.string.repeat_hint));
        LinearLayout repeatRow=line(schedule,R.string.repeat_hint,null);repeatRow.addView(repeat,new LinearLayout.LayoutParams(-2,-2));
        every=spinner(new String[0]);everyRow=line(schedule,R.string.repeat_every_label,null);everyRow.addView(every,new LinearLayout.LayoutParams(-2,-2));every.setContentDescription(a.tr(R.string.repeat_every_label));
        String[] monthDays=new String[32];for(int i=0;i<31;i++)monthDays[i]=String.valueOf(i+1);monthDays[31]=a.tr(R.string.month_last_day);
        monthDay=spinner(monthDays);int savedDay=saved.optInt("monthDay",LocalDate.now().getDayOfMonth());monthDay.setSelection(savedDay<0?31:Math.max(0,Math.min(30,savedDay-1)));monthDay.setContentDescription(a.tr(R.string.month_day_label));
        monthRow=line(schedule,R.string.month_day_label,null);monthRow.addView(monthDay,new LinearLayout.LayoutParams(-2,-2));
        dayRow=a.row();dayRow.setPadding(0,0,0,a.dp(12));
        for(int i=0;i<7;i++){final int day=i;ToggleButton b=new ToggleButton(a);String label=java.time.DayOfWeek.of(i+1).getDisplayName(java.time.format.TextStyle.NARROW,LocaleText.locale(a));b.setTextOn(label);b.setTextOff(label);b.setChecked((mask&(1<<i))!=0);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(a.dp(40));b.setMinimumHeight(a.dp(40));b.setPadding(0,0,0,0);b.setTextSize(13);b.setStateListAnimator(null);b.setBackground(chip());b.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{a.ON_ACCENT,a.TEXT}));b.setContentDescription(java.time.DayOfWeek.of(day+1).getDisplayName(java.time.format.TextStyle.FULL,LocaleText.locale(a)));days[i]=b;a.weighted(dayRow,b);}
        schedule.addView(dayRow);
        dateRow=dateLine(schedule,R.string.date_label,()->pickDate(onDate,d->{onDate=d;dates();},()->{onDate=null;dates();}));
        startRow=dateLine(schedule,R.string.starts_label,()->pickDate(startDate,d->{startDate=d;dates();},()->{startDate=null;dates();}));
        endRow=dateLine(schedule,R.string.ends_label,()->pickDate(endDate,d->{endDate=d;dates();},()->{endDate=null;dates();}));
        autoDelete=new Switch(a);autoDelete.setChecked(saved.optBoolean("autoDelete"));deleteRow=switchRow(schedule,R.string.auto_delete_alarm,null,autoDelete);
        holidays=new Switch(a);holidays.setChecked(saved.optBoolean("skipHolidays"));
        holidayRow=switchRow(schedule,R.string.skip_holidays,Holidays.summary(a,new ClockStore(a).data),holidays);
        pauseRow=dateLine(schedule,R.string.pause_label,this::choosePause);pauseRow.setTag("pause-row");
        int selected=saved.optInt("repeatMode",modeOf(saved));repeat.setSelection(selected);fillEvery(selected,saved.optInt("every",selected==EVERY_WEEKS?1:2));
        repeat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){int last=selected;public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(pos!=last&&(pos==EVERY_DAYS||pos==EVERY_WEEKS))fillEvery(pos,pos==EVERY_WEEKS?1:2);last=pos;visibility();}});

        LinearLayout soundCard=section(parent,R.string.sound_section);
        toneRow=line(soundCard,R.string.alarm_ringtone,null);ringtone=(TextView)((LinearLayout)toneRow.getChildAt(0)).getChildAt(1);
        ImageView arrow=a.icon("right",a.MUTED,20);toneRow.addView(arrow);toneRow.setBackground(a.ripple(Color.TRANSPARENT,12));toneRow.setOnClickListener(v->chooseTone());toneRow.setFocusable(true);
        sound=new Switch(a);sound.setChecked(saved.optBoolean("sound",true));switchRow(soundCard,R.string.alarm_sound,null,sound);
        vibrate=new Switch(a);vibrate.setChecked(saved.optBoolean("vibrate",true));switchRow(soundCard,R.string.vibrate,null,vibrate);
        sound.setOnCheckedChangeListener((v,on)->toneState());toneState();

        LinearLayout dismissCard=section(parent,R.string.dismiss_section);
        puzzle=spinner(new String[]{a.tr(R.string.challenge_none),a.tr(R.string.challenge_math),a.tr(R.string.challenge_retype_name),a.tr(R.string.challenge_shake_name)});puzzle.setContentDescription(a.tr(R.string.challenge_label));
        LinearLayout puzzleRow=line(dismissCard,R.string.challenge_label,null);puzzleRow.addView(puzzle,new LinearLayout.LayoutParams(-2,-2));
        puzzle.setSelection(Math.max(0,Arrays.asList(Challenge.KINDS).indexOf(saved.optString("challenge"))));
        difficulty=spinner(new String[]{a.tr(R.string.difficulty_easy),a.tr(R.string.difficulty_medium),a.tr(R.string.difficulty_hard)});difficulty.setSelection(Challenge.difficulty(saved)-1);difficulty.setContentDescription(a.tr(R.string.difficulty_label));
        difficultyRow=line(dismissCard,R.string.difficulty_label,null);difficultyRow.addView(difficulty,new LinearLayout.LayoutParams(-2,-2));
        puzzle.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){difficultyRow.setVisibility(pos==0?View.GONE:View.VISIBLE);}});

        LinearLayout snooze=section(parent,R.string.snooze_action);
        int limitDefault=settings.number("snooze_limit",0);String limitText=limitDefault==0?a.tr(R.string.unlimited):a.getResources().getQuantityString(R.plurals.times_value,limitDefault,limitDefault);
        defaults=new Switch(a);defaults.setChecked(!saved.has("snoozeMinutes"));switchRow(snooze,R.string.use_defaults,a.getResources().getQuantityString(R.plurals.minutes_value,settings.snoozeMinutes(),settings.snoozeMinutes())+" · "+limitText,defaults);
        snoozeFields=a.column();
        // An alarm saved with another 1-30 minute value keeps it as a choice.
        int current=Math.max(1,Math.min(30,saved.optInt("snoozeMinutes",settings.snoozeMinutes())));for(int n:DURATIONS)durations.add(n);if(!durations.contains(current)){durations.add(current);Collections.sort(durations);}
        String[] minutes=new String[durations.size()];for(int i=0;i<minutes.length;i++)minutes[i]=a.getResources().getQuantityString(R.plurals.minutes_value,durations.get(i),durations.get(i));
        interval=spinner(minutes);interval.setSelection(durations.indexOf(current));interval.setContentDescription(a.tr(R.string.snooze_duration));
        LinearLayout intervalRow=line(snoozeFields,R.string.snooze_duration,null);intervalRow.addView(interval,new LinearLayout.LayoutParams(-2,-2));
        String[] counts=new String[LIMITS.length];int chosen=0;for(int i=0;i<LIMITS.length;i++){int n=LIMITS[i];counts[i]=n==0?a.tr(R.string.unlimited):a.getResources().getQuantityString(R.plurals.times_value,n,n);if(n==saved.optInt("snoozeLimit",limitDefault))chosen=i;}
        limit=spinner(counts);limit.setSelection(chosen);limit.setContentDescription(a.tr(R.string.snooze_count));LinearLayout limitRow=line(snoozeFields,R.string.snooze_count,null);limitRow.addView(limit,new LinearLayout.LayoutParams(-2,-2));
        snooze.addView(snoozeFields);snoozeFields.setVisibility(defaults.isChecked()?View.GONE:View.VISIBLE);defaults.setOnCheckedChangeListener((v,on)->snoozeFields.setVisibility(on?View.GONE:View.VISIBLE));
        visibility();dates();updateTone();difficultyRow.setVisibility(puzzle.getSelectedItemPosition()==0?View.GONE:View.VISIBLE);
    }
    static int modeOf(JSONObject a){
        switch(a.optString("repeat")){case "days":return EVERY_DAYS;case "weeks":return EVERY_WEEKS;case "month":return MONTHLY;}
        int mask=a.optInt("days");return mask==0?ONCE:mask==127?DAILY:mask==31?WEEKDAYS:CUSTOM;
    }
    /** "Every" choices: 2-30 days, or 1-12 weeks (1 = weekly on the chosen days). */
    void fillEvery(int mode,int value){
        int from=mode==EVERY_WEEKS?1:2,to=mode==EVERY_WEEKS?12:30;String[] labels=new String[to-from+1];
        for(int n=from;n<=to;n++)labels[n-from]=a.getResources().getQuantityString(mode==EVERY_WEEKS?R.plurals.weeks_value:R.plurals.days_value,n,n);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(a,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);every.setAdapter(adapter);every.setSelection(Math.max(0,Math.min(to,value)-from));
    }
    int everyValue(){return every.getSelectedItemPosition()+(repeat.getSelectedItemPosition()==EVERY_WEEKS?1:2);}
    /** Small caption plus a rounded card, matching the Settings page. */
    LinearLayout section(LinearLayout parent,int title){
        TextView heading=a.text(a.tr(title),12,a.MUTED);heading.setLetterSpacing(.04f);heading.setPaddingRelative(a.dp(6),a.dp(14),0,a.dp(6));parent.addView(heading);
        LinearLayout card=a.column();card.setPaddingRelative(a.dp(16),a.dp(2),a.dp(12),a.dp(2));card.setBackground(a.bg(a.SOFT,18));parent.addView(card,new LinearLayout.LayoutParams(-1,-2));return card;
    }
    /** Row with a title (and optional detail) on the left; callers add the control on the right. */
    LinearLayout line(LinearLayout card,int title,String detail){
        LinearLayout r=a.row();r.setMinimumHeight(a.dp(56));LinearLayout words=a.column();TextView t=a.text(a.tr(title),15,a.TEXT);t.setPadding(0,0,0,0);words.addView(t);
        TextView value=a.text(detail==null?"":detail,12,detail==null&&title==R.string.alarm_ringtone?a.MINT:a.MUTED);value.setPadding(0,a.dp(2),0,0);value.setVisibility(detail==null&&title!=R.string.alarm_ringtone?View.GONE:View.VISIBLE);words.addView(value);
        r.addView(words,new LinearLayout.LayoutParams(0,-2,1));card.addView(r);return r;
    }
    /** Tappable row whose detail (in accent colour) shows the chosen value. */
    LinearLayout dateLine(LinearLayout card,int title,Runnable action){
        LinearLayout r=line(card,title,"");TextView value=detail(r);value.setTextColor(a.MINT);value.setVisibility(View.VISIBLE);
        r.addView(a.icon("right",a.MUTED,20));r.setBackground(a.ripple(Color.TRANSPARENT,12));r.setFocusable(true);r.setOnClickListener(v->action.run());return r;
    }
    static TextView detail(LinearLayout row){return (TextView)((LinearLayout)row.getChildAt(0)).getChildAt(1);}
    LinearLayout switchRow(LinearLayout card,int title,String detail,Switch control){
        LinearLayout r=line(card,title,detail);control.setContentDescription(a.tr(title));control.setMinimumHeight(a.dp(48));r.addView(control);r.setOnClickListener(v->control.toggle());return r;
    }
    StateListDrawable chip(){
        StateListDrawable s=new StateListDrawable();GradientDrawable on=a.bg(a.MINT,20),off=a.bg(Color.TRANSPARENT,20);off.setStroke(a.dp(1),(a.MUTED&0x00FFFFFF)|0x80000000);
        s.addState(new int[]{android.R.attr.state_checked},on);s.addState(new int[]{},off);return s;
    }
    Spinner spinner(String[] values){Spinner s=new Spinner(a);ArrayAdapter<String> adapter=new ArrayAdapter<>(a,android.R.layout.simple_spinner_item,values);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(adapter);s.setMinimumHeight(a.dp(48));return s;}
    void visibility(){
        int mode=repeat.getSelectedItemPosition();boolean once=mode==ONCE;
        dayRow.setVisibility(mode==CUSTOM||mode==EVERY_WEEKS?View.VISIBLE:View.GONE);everyRow.setVisibility(mode==EVERY_DAYS||mode==EVERY_WEEKS?View.VISIBLE:View.GONE);monthRow.setVisibility(mode==MONTHLY?View.VISIBLE:View.GONE);
        dateRow.setVisibility(once?View.VISIBLE:View.GONE);deleteRow.setVisibility(once?View.VISIBLE:View.GONE);autoDelete.setVisibility(once?View.VISIBLE:View.GONE);
        for(View v:new View[]{startRow,endRow,holidayRow,pauseRow})v.setVisibility(once?View.GONE:View.VISIBLE);
    }
    /** Shows the chosen dates and pause in the rows. */
    void dates(){
        detail(dateRow).setText(onDate==null?a.tr(R.string.date_next_possible):LocaleText.shortDate(a,onDate));
        detail(startRow).setText(startDate==null?a.tr(R.string.today):LocaleText.shortDate(a,startDate));
        detail(endRow).setText(endDate==null?a.tr(R.string.never):LocaleText.shortDate(a,endDate));
        detail(pauseRow).setText(pauseUntil!=null?a.tr(R.string.paused_until,LocaleText.shortDate(a,pauseUntil)):pauseRings>0?a.getResources().getQuantityString(R.plurals.pause_rings_value,pauseRings,pauseRings):a.tr(R.string.not_paused));
    }
    void pickDate(LocalDate current,java.util.function.Consumer<LocalDate> chosen,Runnable clear){
        LocalDate d=current==null?LocalDate.now():current;
        DatePickerDialog dialog=new DatePickerDialog(a,(v,y,m,day)->chosen.accept(LocalDate.of(y,m+1,day)),d.getYear(),d.getMonthValue()-1,d.getDayOfMonth());
        dialog.getDatePicker().setMinDate(System.currentTimeMillis()-1000);
        if(current!=null)dialog.setButton(DialogInterface.BUTTON_NEUTRAL,a.tr(R.string.clear),(x,y)->clear.run());dialog.show();
    }
    /** Pause through a date or for the next N rings; resumes by itself. */
    void choosePause(){
        List<String> items=new ArrayList<>(List.of(a.tr(R.string.pause_until_date),a.tr(R.string.pause_for_rings)));boolean paused=pauseUntil!=null||pauseRings>0;if(paused)items.add(a.tr(R.string.resume_now));
        new AlertDialog.Builder(a).setTitle(R.string.pause_label).setItems(items.toArray(new String[0]),(d,n)->{
            if(n==0)pickDate(pauseUntil,day->{pauseUntil=day;pauseRings=0;dates();},()->{pauseUntil=null;dates();});
            else if(n==1){String[] labels=new String[PAUSE_RINGS.length];for(int i=0;i<labels.length;i++)labels[i]=a.getResources().getQuantityString(R.plurals.pause_rings_value,PAUSE_RINGS[i],PAUSE_RINGS[i]);
                new AlertDialog.Builder(a).setTitle(R.string.pause_for_rings).setItems(labels,(x,i)->{pauseRings=PAUSE_RINGS[i];pauseUntil=null;dates();}).show();}
            else{pauseUntil=null;pauseRings=0;dates();}}).show();
    }
    /** The ringtone only matters while sound is on. */
    void toneState(){toneRow.setAlpha(sound.isChecked()?1f:.4f);toneRow.setEnabled(sound.isChecked());}
    int mask(){int mode=repeat.getSelectedItemPosition();if(mode==ONCE||mode==EVERY_DAYS||mode==MONTHLY)return 0;if(mode==DAILY)return 127;if(mode==WEEKDAYS)return 31;int mask=0;for(int i=0;i<7;i++)if(days[i].isChecked())mask|=1<<i;return mask;}
    void validate(){int mode=repeat.getSelectedItemPosition();if((mode==CUSTOM||mode==EVERY_WEEKS)&&mask()==0)throw new IllegalArgumentException(a.tr(R.string.choose_days));if(startDate!=null&&endDate!=null&&endDate.isBefore(startDate))throw new IllegalArgumentException(a.tr(R.string.end_before_start));}
    void write(JSONObject o,boolean draft){
        int mode=repeat.getSelectedItemPosition();ClockStore.put(o,"days",mask());
        for(String k:List.of("repeat","every","monthDay","start","end","date"))o.remove(k);
        if(mode==EVERY_DAYS||mode==EVERY_WEEKS){ClockStore.put(o,"repeat",mode==EVERY_DAYS?"days":"weeks");ClockStore.put(o,"every",everyValue());ClockStore.put(o,"start",(startDate==null?LocalDate.now():startDate).toString());}
        if(mode==MONTHLY){ClockStore.put(o,"repeat","month");int day=monthDay.getSelectedItemPosition();ClockStore.put(o,"monthDay",day==31?-1:day+1);}
        if(mode!=ONCE){if(startDate!=null)ClockStore.put(o,"start",startDate.toString());if(endDate!=null)ClockStore.put(o,"end",endDate.toString());}
        else if(onDate!=null)ClockStore.put(o,"date",onDate.toString());
        ClockStore.put(o,"skipHolidays",mode!=ONCE&&holidays.isChecked());
        int p=puzzle.getSelectedItemPosition();if(p==0){o.remove("challenge");o.remove("difficulty");}else{ClockStore.put(o,"challenge",Challenge.KINDS[p]);ClockStore.put(o,"difficulty",difficulty.getSelectedItemPosition()+1);}
        ClockStore.put(o,"autoDelete",mode==ONCE&&autoDelete.isChecked());ClockStore.put(o,"tone",tone);ClockStore.put(o,"sound",sound.isChecked());ClockStore.put(o,"vibrate",vibrate.isChecked());
        if(draft){ClockStore.put(o,"repeatMode",mode);if(pauseUntil!=null)ClockStore.put(o,"draftPause",pauseUntil.toString());ClockStore.put(o,"draftPauseRings",pauseRings);}
        if(defaults.isChecked()){o.remove("snoozeMinutes");o.remove("snoozeLimit");}else{ClockStore.put(o,"snoozeMinutes",durations.get(interval.getSelectedItemPosition()));ClockStore.put(o,"snoozeLimit",LIMITS[limit.getSelectedItemPosition()]);}
    }
    /** Applies the chosen pause after the alarm was saved and enabled (saving clears earlier skips). */
    void applyPause(ClockStore store,JSONObject o){if(!Schedule.repeats(o)||!o.optBoolean("enabled"))return;if(pauseUntil!=null)store.pause(o,pauseUntil);else if(pauseRings>0)store.pauseRings(o,pauseRings);}
    void updateTone(){String name=a.tr(R.string.default_tone);if(tone.equals("silent"))name=a.tr(R.string.silent);else if(tone.equals(ClockSettings.BUILTIN))name=a.tr(R.string.builtin_tone);else if(!tone.equals("inherit")){try{android.media.Ringtone r=RingtoneManager.getRingtone(a,tone.equals("default")?RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM):Uri.parse(tone));name=r==null?a.tr(R.string.sound_unavailable):r.getTitle(a);}catch(Exception e){name=a.tr(R.string.sound_unavailable);}}if(tone.startsWith("content:")){String display=SoundAccess.title(a,Uri.parse(tone));if(display!=null)name=display;}ringtone.setText(name);toneRow.setContentDescription(a.tr(R.string.alarm_ringtone)+", "+name);}
    void chooseTone(){new AlertDialog.Builder(a).setTitle(R.string.alarm_ringtone).setItems(new String[]{a.tr(R.string.default_tone),a.tr(R.string.builtin_tone),a.tr(R.string.choose_sound),a.tr(R.string.audio_file),a.tr(R.string.silent)},(d,n)->{if(n==0||n==1||n==4){tone=n==0?"inherit":n==1?ClockSettings.BUILTIN:"silent";updateTone();return;}Intent intent=n==3?new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION):new Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALARM).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,true);try{a.startActivityForResult(intent,n==3?302:301);}catch(ActivityNotFoundException e){a.toast(a.tr(R.string.picker_unavailable));}}).show();}
    void result(int request,int result,Intent data){if(result!=Activity.RESULT_OK||data==null)return;Uri uri=request==302?data.getData():data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI,Uri.class);if(uri==null){tone="silent";updateTone();return;}try{if(request==302){a.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);acquired.add(uri);try(android.content.res.AssetFileDescriptor fd=a.getContentResolver().openAssetFileDescriptor(uri,"r")){if(fd==null)throw new IllegalArgumentException();}}android.media.Ringtone r=RingtoneManager.getRingtone(a,uri);if(r==null)throw new IllegalArgumentException();tone=RingtoneManager.isDefault(uri)?"default":uri.toString();updateTone();}catch(Exception e){a.toast(a.tr(R.string.sound_unavailable));}}
    void releaseUnused(){SoundAccess.releaseUnused(a);}
}
