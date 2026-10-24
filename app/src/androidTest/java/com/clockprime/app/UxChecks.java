package com.clockprime.app;

import android.app.AlertDialog;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.*;
import java.util.*;

/** 0.14 design-review fixes: 12-hour entry, undo for single deletes, header, tabs, ringtone list, selection, timers, group menu. */
final class UxChecks {
    static void run(SmokeTests t){
        ClockSettings prefs=new ClockSettings(t.context);String hourFormat=prefs.text("hour_format",null);String[] cities={null};
        t.main(()->{
            t.check(MainActivity.hour12(0)==12&&MainActivity.hour12(12)==12&&MainActivity.hour12(13)==1&&MainActivity.hour12(23)==11,"24-hour clock maps to 12-hour faces (0 → 12 AM, 12 → 12 PM, 13 → 1 PM)");
            t.check(MainActivity.to24(12,false)==0&&MainActivity.to24(12,true)==12&&MainActivity.to24(1,true)==13&&MainActivity.to24(11,false)==11,"12 AM is 0, 12 PM is 12, 1 PM is 13");
            boolean round=true;for(int h=0;h<24;h++)round&=MainActivity.to24(MainActivity.hour12(h),h>=12)==h;t.check(round,"Every hour survives the 12-hour round trip");
            t.check(new Palette(false).muted==android.graphics.Color.parseColor("#56657C")&&new Palette(true).muted==android.graphics.Color.parseColor("#A0ADC3"),"Shared palette uses the darker light-mode muted colour");
            cities[0]=new ClockStore(t.context).list("cities").toString();prefs.put("hour_format","12");
        });
        MainActivity a=t.openActivity();t.waitForIdleSync();SystemClock.sleep(300);
        try{
            t.main(()->{
                a.tab="Alarms";a.render();
                ViewGroup.LayoutParams header=a.appHeader.getLayoutParams();t.check(header.height==ViewGroup.LayoutParams.WRAP_CONTENT&&a.appHeader.getMinimumHeight()==a.dp(80),"Page header wraps its title with an 80dp minimum");
                View active=null;for(int i=0;i<a.bottomBar.getChildCount();i++)if(a.bottomBar.getChildAt(i).isSelected())active=a.bottomBar.getChildAt(i);
                t.check(active!=null&&a.getString(R.string.alarms).contentEquals(active.getContentDescription())&&active.createAccessibilityNodeInfo().isSelected(),"Active bottom tab reports selected to accessibility");
                // 12-hour entry
                a.alarmEditor(null,null);MainActivity.TimeEntry e=a.editingTime;
                t.check(e.twelve&&e.meridiem!=null&&e.wheels.length==2&&e.wheels[0].minimum==1&&e.wheels[0].maximum==12,"12-hour format shows 1-12 hours and an AM/PM wheel");
                e.setTime(0,5,false);t.check(Arrays.equals(Arrays.copyOf(e.values(),2),new int[]{0,5})&&e.wheels[0].selected()==12&&e.meridiem.selected()==0,"12:05 AM saves as 00:05");
                e.setTime(12,0,false);t.check(e.values()[0]==12&&e.meridiem.selected()==1,"12 PM saves as 12:00");
                e.setTime(13,45,false);t.check(e.values()[0]==13&&e.wheels[0].selected()==1,"1 PM saves as 13:00");
                TimeWheel hour=e.wheels[0];hour.beginEditing();hour.editor.setText("13");boolean rejected=false;try{hour.finishEditing();}catch(TimeWheel.OutOfRange x){rejected=true;}
                t.check(rejected,"Typed hour 13 is out of range on the 12-hour wheel");hour.editor.setText("11");hour.finishEditing();e.meridiem.setValue(1,false);
                t.check(e.values()[0]==23,"Typed 11 with PM is 23");
                JSONObject draft=a.alarmDraft();t.check(draft.optInt("hour")==23,"Editor draft stores the 24-hour value");
                e.meridiem.surface.performClick();
            });SystemClock.sleep(400);
            t.main(()->{
                t.check(a.editingTime.values()[0]==11,"Tapping the AM/PM wheel flips PM to AM");
                a.alarmDialog.dismiss();JSONObject draft=new JSONObject();ClockStore.put(draft,"editingId",-1);ClockStore.put(draft,"hour",0);ClockStore.put(draft,"minute",30);a.restoringDraft=draft;a.alarmEditor(null,null);
                t.check(a.editingTime.values()[0]==0&&a.editingTime.values()[1]==30&&a.editingTime.wheels[0].selected()==12,"Restored draft at 00:30 shows 12:30 AM");
                // Ringtone list: no Silent, current choice checked
                a.editingOptions.chooseTone();ListView list=a.editingOptions.toneDialog.getListView();List<String> items=new ArrayList<>();for(int i=0;i<list.getAdapter().getCount();i++)items.add(String.valueOf(list.getAdapter().getItem(i)));
                t.check(items.size()==4&&!items.contains(a.getString(R.string.silent)),"Per-alarm ringtone list has no Silent");t.check(list.getCheckedItemPosition()==0,"Ringtone dialog checks the current choice");a.editingOptions.toneDialog.dismiss();
            });t.waitForIdleSync();
            t.main(()->{
                a.editingName.setText("ClockPrime QA twelve");a.editingTime.setTime(23,15,false);a.alarmDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                JSONObject saved=null;for(JSONObject x:new ClockStore(a).objects("alarms"))if(x.optString("name").equals("ClockPrime QA twelve"))saved=x;
                t.check(saved!=null&&saved.optInt("hour")==23&&saved.optInt("minute")==15,"Saving from the 12-hour editor stores 23:15");t.featureIds.add(saved.optInt("id"));
                // Legacy silent tone opens as sound off
                ClockStore.put(saved,"tone","silent");ClockStore.put(saved,"sound",true);a.alarmEditor(saved,"");
                t.check(!a.editingOptions.sound.isChecked()&&a.editingOptions.tone.equals("inherit"),"An alarm saved with the old Silent ringtone opens with sound off");a.alarmDialog.dismiss();
                prefs.put("hour_format","24");a.alarmEditor(null,null);t.check(!a.editingTime.twelve&&a.editingTime.meridiem==null&&a.editingTime.wheels[0].maximum==23,"24-hour format keeps the 0-23 wheel");a.alarmDialog.dismiss();
                // Undo: alarm
                ClockStore s=new ClockStore(a);JSONObject alarm=t.testAlarm(s,"","undo");s.enable(alarm,true);s.save(false);a.render();int id=alarm.optInt("id");int index=-1;JSONArray all=a.store.list("alarms");for(int i=0;i<all.length();i++)if(all.optJSONObject(i).optInt("id")==id)index=i;
                long beforeNext=a.store.find("alarms",id).optLong("next");a.removeWithUndo("alarms",a.store.find("alarms",id),"QA");
                t.check(new ClockStore(a).find("alarms",id)==null&&a.root.findViewWithTag("undo-bar")!=null,"Deleting one alarm removes it and offers Undo");
                a.root.findViewWithTag("undo").performClick();ClockStore after=new ClockStore(a);JSONObject back=after.find("alarms",id);int at=-1;all=after.list("alarms");for(int i=0;i<all.length();i++)if(all.optJSONObject(i).optInt("id")==id)at=i;
                t.check(back!=null&&back.optBoolean("enabled")&&back.optString("name").equals("ClockPrime QA undo")&&back.optLong("next")==beforeNext&&at==index,"Undo restores the deleted alarm exactly, in place and still on");
                t.check(a.root.findViewWithTag("undo-bar")==null,"Undo bar closes after Undo");
                // Undo: world clock
                JSONArray list2=after.list("cities");String zone="Pacific/Chatham";boolean had=false;for(int i=0;i<list2.length();i++)had|=list2.optString(i).equals(zone);if(!had){list2.put(zone);after.save(false);}a.render();
                int cityIndex=-1;JSONArray c=new ClockStore(a).list("cities");for(int i=0;i<c.length();i++)if(c.optString(i).equals(zone))cityIndex=i;
                a.removeCity(zone);t.check(!new ClockStore(a).list("cities").toString().contains(zone),"Removing a world clock takes it off the list");
                a.root.findViewWithTag("undo").performClick();c=new ClockStore(a).list("cities");int cityAt=-1;for(int i=0;i<c.length();i++)if(c.optString(i).equals(zone))cityAt=i;
                t.check(cityAt==cityIndex,"Undo puts the world clock back in its place");
                // Undo: timer, compact rows, presets, named buttons
                ClockStore ts=new ClockStore(a);JSONObject timer=new JSONObject();int tid=ts.id();ClockStore.put(timer,"id",tid);ClockStore.put(timer,"name","ClockPrime QA ux timer");ClockStore.put(timer,"duration",600000L);ClockStore.put(timer,"remaining",600000L);ClockStore.put(timer,"sound",false);ClockStore.put(timer,"vibrate",false);ts.list("timers").put(timer);ts.timerStart(timer,600000L);ts.save(false);
                a.tab="Timers";a.render();
                Button preset=a.body.findViewWithTag("preset-5");t.check(preset!=null&&preset.getText().toString().equals(a.getString(R.string.widget_minutes_short,5)),"Timer presets read like 5 min");
                View card=a.body.findViewWithTag("timer-"+tid);t.check(card!=null&&card.findViewWithTag("timer-progress")!=null&&a.body.indexOfChild(card)<=2,"Running timer comes first with a progress bar");
                View trash=card.findViewWithTag("timer-delete");t.check(trash!=null&&a.getString(R.string.timer_delete_named,"ClockPrime QA ux timer").contentEquals(trash.getContentDescription()),"Timer delete is an icon button named after the timer");
                List<CharSequence> descriptions=new ArrayList<>();collect(card,descriptions);t.check(descriptions.contains(a.getString(R.string.timer_pause_named,"ClockPrime QA ux timer"))&&descriptions.contains(a.getString(R.string.timer_reset_named,"ClockPrime QA ux timer")),"Timer Pause and Reset buttons include the timer name");
                trash.performClick();t.check(new ClockStore(a).find("timers",tid)==null,"Timer delete removes the timer");a.root.findViewWithTag("undo").performClick();
                JSONObject restored=new ClockStore(a).find("timers",tid);t.check(restored!=null&&restored.optBoolean("running")&&ClockStore.remaining(restored)>500000,"Undo restores a running timer still counting down");
                ClockStore cleanup=new ClockStore(a);cleanup.remove("timers",cleanup.find("timers",tid));cleanup.save(false);
                // Selection across groups and the group menu
                a.tab="Alarms";ClockStore g=new ClockStore(a);g.list("groups").put("QA ux one");g.list("groups").put("QA ux two");JSONObject one=t.testAlarm(g,"QA ux one","one"),two=t.testAlarm(g,"QA ux two","two");g.save(false);a.render();
                a.selectAlarm("QA ux one",one.optInt("id"));MainActivity.AlarmRowViews other=a.alarmViews.get(two.optInt("id")),mine=a.alarmViews.get(one.optInt("id"));
                t.check(!other.card.isEnabled()&&other.card.getImportantForAccessibility()==View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS&&other.check.getVisibility()!=View.VISIBLE&&mine.check.getVisibility()==View.VISIBLE,"Selection hides other groups' checkboxes and their cards from TalkBack");
                a.selection.clear();a.updateSelectionUi();t.check(other.card.isEnabled()&&other.card.getImportantForAccessibility()==View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,"Leaving selection restores other groups' cards");
                a.groupActions("QA ux one");View menu=a.groupMenu.getWindow().getDecorView();
                t.check(menu.findViewWithTag("group-menu-enable_all")!=null&&menu.findViewWithTag("group-menu-disable_all")==null&&menu.findViewWithTag("group-menu-skip_occurrences")==null,"Group menu offers only Turn all on when every alarm is off");
                TextView delete=menu.findViewWithTag("group-menu-delete_group");LinearLayout items2=(LinearLayout)delete.getParent();t.check(items2.getChildAt(items2.getChildCount()-1)==delete&&delete.getCurrentTextColor()==a.GOLD,"Group Delete comes last in the warning colour");a.groupMenu.dismiss();
                a.groupMutate("QA ux one",8,0);a.groupMutate("QA ux two",8,0);
                long soon=java.time.LocalDate.now().atTime(23,59).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();t.check(LocaleText.relative(a,soon).startsWith(a.getString(R.string.today)),"Alarm dates read relative (Today …)");
            });
        }finally{t.main(()->{if(a.alarmDialog!=null)a.alarmDialog.dismiss();if(a.groupMenu!=null)a.groupMenu.dismiss();a.finish();if(hourFormat==null)prefs.prefs.edit().remove("hour_format").commit();else prefs.put("hour_format",hourFormat);
            ClockStore s=new ClockStore(t.context);try{ClockStore.put(s.data,"cities",new JSONArray(cities[0]));}catch(JSONException x){throw new AssertionError(x);}JSONArray groups=s.list("groups");for(int i=groups.length()-1;i>=0;i--)if(groups.optString(i).startsWith("QA ux"))groups.remove(i);s.save(false);});}
    }
    static void collect(View v,List<CharSequence> out){if(v.getContentDescription()!=null)out.add(v.getContentDescription());if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i),out);}
}
