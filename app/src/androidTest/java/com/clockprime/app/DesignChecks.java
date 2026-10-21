package com.clockprime.app;

import android.app.AlertDialog;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.JSONObject;

/** Integration checks for editing and real touch gestures, including nested-scroll conflicts. */
final class DesignChecks {
    static void run(SmokeTests test){
        MainActivity activity=test.openActivity();test.waitForIdleSync();SystemClock.sleep(350);
        try{
            test.main(()->{
                activity.tab="Alarms";activity.render();
                test.check(activity.body.findViewWithTag("group-")==null,"Ungrouped alarms have no group header or fold control");
                for(JSONObject alarm:activity.store.objects("alarms"))if(alarm.optString("group").isEmpty())test.check(activity.alarmViews.get(alarm.optInt("id")).card.getVisibility()==View.VISIBLE,"Ungrouped row stays visible despite legacy folded preference");
                activity.alarmEditor(null,null);activity.editingName.setText("ClockPrime QA draft");activity.editingTime.wheels[0].setValue(7,false);activity.editingTime.wheels[1].setValue(23,false);
                activity.editingGroup.performClick();test.check(activity.editingGroup.choices.getListView().getAdapter().getCount()>=2,"Alarm group picker offers no group and create group");activity.editingGroup.choices.dismiss();activity.editingGroup.promptNew();activity.editingGroup.newName.setText("ClockPrime QA draft group");
            });test.waitForIdleSync();SystemClock.sleep(150);test.main(()->{
                activity.editingGroup.nameDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                test.check(activity.editingGroup.selected.equals("ClockPrime QA draft group")&&activity.editingTime.values()[1]==23&&activity.editingName.getText().toString().equals("ClockPrime QA draft"),"Creating a draft group preserves alarm name and time");
                test.check(!new ClockStore(activity).list("groups").toString().contains("ClockPrime QA draft group"),"New group remains uncommitted until alarm is saved");activity.alarmDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                test.check(!new ClockStore(activity).list("groups").toString().contains("ClockPrime QA draft group"),"Canceling alarm discards the draft group");
                activity.alarmEditor(null,null);activity.editingName.setText("ClockPrime QA inline group");activity.editingTime.wheels[0].setValue(7,false);activity.editingTime.wheels[1].setValue(23,false);activity.editingGroup.selectNew("ClockPrime QA saved group");
            });test.waitForIdleSync();SystemClock.sleep(150);test.main(()->{
                activity.alarmDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                ClockStore stored=new ClockStore(activity);JSONObject saved=null;for(JSONObject a:stored.objects("alarms"))if(a.optString("name").equals("ClockPrime QA inline group"))saved=a;
                test.check(saved!=null&&saved.optString("group").equals("ClockPrime QA saved group")&&saved.optInt("hour")==7&&saved.optInt("minute")==23&&saved.optBoolean("enabled"),"Save creates group and assigned alarm together");
                int id=saved.optInt("id");test.featureIds.add(id);activity.alarmEditor(activity.store.find("alarms",id),null);
            });test.waitForIdleSync();SystemClock.sleep(150);test.main(()->{
                int id=activity.store.objects("alarms").stream().filter(a->a.optString("name").equals("ClockPrime QA inline group")).findFirst().get().optInt("id");
                test.check(activity.editingGroup.selected.equals("ClockPrime QA saved group"),"Editing preselects the alarm's existing group");
                boolean duplicate=false;try{activity.editingGroup.selectNew("clockprime qa SAVED group");}catch(IllegalArgumentException e){duplicate=true;}test.check(duplicate,"Inline group creation rejects case-insensitive duplicates");
                TimeWheel hour=activity.editingTime.wheels[0];hour.beginEditing();hour.editor.setText("24");activity.alarmDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                test.check(activity.alarmDialog.isShowing()&&hour.editor.getError()!=null&&new ClockStore(activity).find("alarms",id).optInt("hour")==7,"Invalid typed hour keeps editor open without changing saved alarm");
                hour.editor.setText("23");hour.editor.onEditorAction(EditorInfo.IME_ACTION_DONE);test.check(hour.getValue()==23&&!hour.editing,"Keyboard Done commits typed hour and restores wheel");activity.alarmDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                ClockStore stored=new ClockStore(activity);stored.remove("alarms",stored.find("alarms",id));stored.save();activity.tab="Timers";activity.timerValue=new int[]{0,5,0};activity.render();
            });test.waitForIdleSync();SystemClock.sleep(500);
            final int[] scroll={0};final TimeWheel[] minute={null};
            test.main(()->{minute[0]=activity.timerInput.wheels[1];scroll[0]=activity.contentScroll.getScrollY();});
            drag(test,minute[0].surface,-1.2f,450);SystemClock.sleep(1100);test.waitForIdleSync();
            test.main(()->{
                int value=minute[0].getValue();test.check(value>5&&value<12,"Real finger drag advances minute wheel and snaps to a value");test.check(activity.contentScroll.getScrollY()==scroll[0],"Dragging a wheel does not scroll the surrounding page");
                minute[0].setValue(59,false);minute[0].surface.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null);
            });SystemClock.sleep(350);
            test.main(()->{test.check(minute[0].getValue()==0,"Minute wheel wraps from 59 to 00 through accessibility action");minute[0].surface.performClick();});SystemClock.sleep(400);test.sendStringSync("37");
            test.main(()->{minute[0].editor.onEditorAction(EditorInfo.IME_ACTION_DONE);test.check(activity.timerInput.values()[1]==37,"Tap-to-type accepts keyboard input in the minute wheel");
                TimeWheel original=activity.timerInput.wheels[1];activity.timerInput.set(new int[]{0,20,0});test.check(activity.timerInput.wheels[1]==original,"Preset animates existing wheels without rebuilding controls");test.check(activity.timerInput.values()[1]==20,"Immediate start after preset commits its target instead of an animation frame");
                activity.timerInput.wheels[0].setValue(99,false);activity.timerInput.wheels[0].surface.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null);
            });SystemClock.sleep(350);
            test.main(()->{test.check(activity.timerInput.values()[0]==0,"Timer hour wheel wraps from 99 to 00");minute[0].setValue(20,false);});
            drag(test,minute[0].surface,-1.4f,100);SystemClock.sleep(1800);
            test.main(()->{test.check(!minute[0].surface.flinging&&Math.abs(minute[0].position-Math.round(minute[0].position))<.001,"Fast fling finishes on an aligned number");int value=minute[0].getValue();test.check(value>=0&&value<=59,"Fling remains inside minute range");});
        }finally{test.main(()->{if(activity.alarmDialog!=null)activity.alarmDialog.dismiss();activity.finish();});}
    }
    static void drag(SmokeTests test,View view,float rows,int duration){
        final float[] geometry=new float[3];test.main(()->{int[] location=new int[2];view.getLocationOnScreen(location);geometry[0]=location[0]+view.getWidth()/2f;geometry[1]=location[1]+view.getHeight()*.6f;geometry[2]=rows*58*view.getResources().getDisplayMetrics().density;});
        long start=SystemClock.uptimeMillis();send(test,start,start,MotionEvent.ACTION_DOWN,geometry[0],geometry[1]);
        for(int i=1;i<=12;i++){SystemClock.sleep(duration/12);send(test,start,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,geometry[0],geometry[1]+geometry[2]*i/12);}
        send(test,start,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,geometry[0],geometry[1]+geometry[2]);
    }
    static void send(SmokeTests test,long start,long time,int action,float x,float y){MotionEvent event=MotionEvent.obtain(start,time,action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);test.sendPointerSync(event);event.recycle();}
}
