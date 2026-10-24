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
import java.util.*;

/** Unsaved alarm options in grouped cards: Repeat, Sound, Snooze. All writes happen only after validation. */
final class AlarmOptions {
    final MainActivity a;final Spinner repeat;final ToggleButton[] days=new ToggleButton[7];
    final Switch defaults,autoDelete,sound,vibrate;final Spinner interval,limit;final List<Integer> durations=new ArrayList<>();final TextView ringtone;
    final LinearLayout dayRow,snoozeFields,deleteRow,toneRow;String tone;final Set<Uri> acquired=new HashSet<>();
    static final int[] LIMITS={1,2,3,5,10,0};
    /** Same choices as the default snooze duration in Settings. */
    static final int[] DURATIONS={1,3,5,10,15,20,30};
    AlarmOptions(MainActivity a,LinearLayout parent,JSONObject saved){
        this.a=a;ClockSettings settings=new ClockSettings(a);int mask=saved.optInt("days");tone=saved.optString("tone","inherit");

        LinearLayout schedule=section(parent,R.string.schedule_section);
        repeat=spinner(new String[]{a.tr(R.string.once),a.tr(R.string.repeat_daily),a.tr(R.string.repeat_weekdays),a.tr(R.string.customize)});repeat.setContentDescription(a.tr(R.string.repeat_hint));
        LinearLayout repeatRow=line(schedule,R.string.repeat_hint,null);repeatRow.addView(repeat,new LinearLayout.LayoutParams(-2,-2));
        dayRow=a.row();dayRow.setPadding(0,0,0,a.dp(12));
        for(int i=0;i<7;i++){final int day=i;ToggleButton b=new ToggleButton(a);String label=java.time.DayOfWeek.of(i+1).getDisplayName(java.time.format.TextStyle.NARROW,LocaleText.locale(a));b.setTextOn(label);b.setTextOff(label);b.setChecked((mask&(1<<i))!=0);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(a.dp(40));b.setMinimumHeight(a.dp(40));b.setPadding(0,0,0,0);b.setTextSize(13);b.setStateListAnimator(null);b.setBackground(chip());b.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{a.ON_ACCENT,a.TEXT}));b.setContentDescription(java.time.DayOfWeek.of(day+1).getDisplayName(java.time.format.TextStyle.FULL,LocaleText.locale(a)));days[i]=b;a.weighted(dayRow,b);}
        schedule.addView(dayRow);
        autoDelete=new Switch(a);autoDelete.setChecked(saved.optBoolean("autoDelete"));deleteRow=switchRow(schedule,R.string.auto_delete_alarm,null,autoDelete);
        int selected=saved.optInt("repeatMode",mask==0?0:mask==127?1:mask==31?2:3);repeat.setSelection(selected);repeat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){visibility();}});

        LinearLayout soundCard=section(parent,R.string.sound_section);
        toneRow=line(soundCard,R.string.alarm_ringtone,null);ringtone=(TextView)((LinearLayout)toneRow.getChildAt(0)).getChildAt(1);
        ImageView arrow=a.icon("right",a.MUTED,20);toneRow.addView(arrow);toneRow.setBackground(a.ripple(Color.TRANSPARENT,12));toneRow.setOnClickListener(v->chooseTone());toneRow.setFocusable(true);
        sound=new Switch(a);sound.setChecked(saved.optBoolean("sound",true));switchRow(soundCard,R.string.alarm_sound,null,sound);
        vibrate=new Switch(a);vibrate.setChecked(saved.optBoolean("vibrate",true));switchRow(soundCard,R.string.vibrate,null,vibrate);
        sound.setOnCheckedChangeListener((v,on)->toneState());toneState();

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
        visibility();updateTone();
    }
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
    LinearLayout switchRow(LinearLayout card,int title,String detail,Switch control){
        LinearLayout r=line(card,title,detail);control.setContentDescription(a.tr(title));control.setMinimumHeight(a.dp(48));r.addView(control);r.setOnClickListener(v->control.toggle());return r;
    }
    StateListDrawable chip(){
        StateListDrawable s=new StateListDrawable();GradientDrawable on=a.bg(a.MINT,20),off=a.bg(Color.TRANSPARENT,20);off.setStroke(a.dp(1),(a.MUTED&0x00FFFFFF)|0x80000000);
        s.addState(new int[]{android.R.attr.state_checked},on);s.addState(new int[]{},off);return s;
    }
    Spinner spinner(String[] values){Spinner s=new Spinner(a);ArrayAdapter<String> adapter=new ArrayAdapter<>(a,android.R.layout.simple_spinner_item,values);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(adapter);s.setMinimumHeight(a.dp(48));return s;}
    void visibility(){int mode=repeat.getSelectedItemPosition();dayRow.setVisibility(mode==3?View.VISIBLE:View.GONE);deleteRow.setVisibility(mode==0?View.VISIBLE:View.GONE);autoDelete.setVisibility(mode==0?View.VISIBLE:View.GONE);}
    /** The ringtone only matters while sound is on. */
    void toneState(){toneRow.setAlpha(sound.isChecked()?1f:.4f);toneRow.setEnabled(sound.isChecked());}
    int mask(){int mode=repeat.getSelectedItemPosition();if(mode<3)return mode==0?0:mode==1?127:31;int mask=0;for(int i=0;i<7;i++)if(days[i].isChecked())mask|=1<<i;return mask;}
    void validate(){if(repeat.getSelectedItemPosition()==3&&mask()==0)throw new IllegalArgumentException(a.tr(R.string.choose_days));}
    void write(JSONObject o,boolean draft){ClockStore.put(o,"days",mask());ClockStore.put(o,"autoDelete",repeat.getSelectedItemPosition()==0&&autoDelete.isChecked());ClockStore.put(o,"tone",tone);ClockStore.put(o,"sound",sound.isChecked());ClockStore.put(o,"vibrate",vibrate.isChecked());if(draft)ClockStore.put(o,"repeatMode",repeat.getSelectedItemPosition());if(defaults.isChecked()){o.remove("snoozeMinutes");o.remove("snoozeLimit");}else{ClockStore.put(o,"snoozeMinutes",durations.get(interval.getSelectedItemPosition()));ClockStore.put(o,"snoozeLimit",LIMITS[limit.getSelectedItemPosition()]);}}
    void updateTone(){String name=a.tr(R.string.default_tone);if(tone.equals("silent"))name=a.tr(R.string.silent);else if(!tone.equals("inherit")){try{android.media.Ringtone r=RingtoneManager.getRingtone(a,tone.equals("default")?RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM):Uri.parse(tone));name=r==null?a.tr(R.string.sound_unavailable):r.getTitle(a);}catch(Exception e){name=a.tr(R.string.sound_unavailable);}}if(tone.startsWith("content:")){String display=SoundAccess.title(a,Uri.parse(tone));if(display!=null)name=display;}ringtone.setText(name);toneRow.setContentDescription(a.tr(R.string.alarm_ringtone)+", "+name);}
    void chooseTone(){new AlertDialog.Builder(a).setTitle(R.string.alarm_ringtone).setItems(new String[]{a.tr(R.string.default_tone),a.tr(R.string.choose_sound),a.tr(R.string.audio_file),a.tr(R.string.silent)},(d,n)->{if(n==0||n==3){tone=n==0?"inherit":"silent";updateTone();return;}Intent intent=n==2?new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION):new Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALARM).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,true);try{a.startActivityForResult(intent,n==2?302:301);}catch(ActivityNotFoundException e){a.toast(a.tr(R.string.picker_unavailable));}}).show();}
    void result(int request,int result,Intent data){if(result!=Activity.RESULT_OK||data==null)return;Uri uri=request==302?data.getData():data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI,Uri.class);if(uri==null){tone="silent";updateTone();return;}try{if(request==302){a.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);acquired.add(uri);try(android.content.res.AssetFileDescriptor fd=a.getContentResolver().openAssetFileDescriptor(uri,"r")){if(fd==null)throw new IllegalArgumentException();}}android.media.Ringtone r=RingtoneManager.getRingtone(a,uri);if(r==null)throw new IllegalArgumentException();tone=RingtoneManager.isDefault(uri)?"default":uri.toString();updateTone();}catch(Exception e){a.toast(a.tr(R.string.sound_unavailable));}}
    void releaseUnused(){SoundAccess.releaseUnused(a);}
}
