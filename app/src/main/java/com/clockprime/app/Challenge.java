package com.clockprime.app;

import android.content.Context;
import android.hardware.*;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.JSONObject;
import java.util.Locale;
import java.util.Random;

/**
 * A wake-up puzzle that must be solved before an alarm can be dismissed: per alarm "challenge" (math, retype, shake)
 * and "difficulty" 1-3. Snooze stays available, and auto-silence still ends the ring, so nobody gets locked in.
 */
final class Challenge implements SensorEventListener {
    static final String[] KINDS={"","math","retype","shake"};
    static final int[] SHAKES={15,30,50},CODE_LENGTH={6,9,12};
    /** No 0/O, 1/I/L: a code that is easy to read half asleep. */
    static final String CODE_CHARS="ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static boolean required(JSONObject a){String k=a.optString("challenge");return k.equals("math")||k.equals("retype")||k.equals("shake");}
    static int difficulty(JSONObject a){return Math.max(1,Math.min(3,a.optInt("difficulty",1)));}

    /** Math problem for a difficulty: {question, answer}. Answers are always positive. */
    static Object[] math(int level,Random r){
        if(level<=1){int x=10+r.nextInt(40),y=10+r.nextInt(40);return new Object[]{x+" + "+y,x+y};}
        if(level==2){int x=3+r.nextInt(7),y=11+r.nextInt(9),z=10+r.nextInt(90);return new Object[]{x+" × "+y+" + "+z,x*y+z};}
        int x=11+r.nextInt(9),y=11+r.nextInt(9),z=10+r.nextInt(90);return new Object[]{x+" × "+y+" − "+z,x*y-z};
    }
    static String code(int level,Random r){StringBuilder b=new StringBuilder();int n=CODE_LENGTH[level-1];for(int i=0;i<n;i++){if(i>0&&i%3==0)b.append('-');b.append(CODE_CHARS.charAt(r.nextInt(CODE_CHARS.length())));}return b.toString();}
    /** Retyped codes ignore case, spaces and dashes. */
    static boolean sameCode(String expected,String typed){return expected.replaceAll("[^A-Za-z0-9]","").equalsIgnoreCase(typed.replaceAll("[^A-Za-z0-9]",""));}

    final RingActivity a;final String kind;final int level;final Runnable solved;final Random random=new Random();
    final LinearLayout panel;TextView status;ProgressBar progress;EditText answer;Object expected;
    SensorManager sensors;int shakes;long lastShake;

    Challenge(RingActivity a,JSONObject alarm,Runnable solved,Runnable cancel){
        this.a=a;kind=alarm.optString("challenge");level=difficulty(alarm);this.solved=solved;
        panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(0,a.dp(12),0,0);panel.setTag("challenge");
        panel.addView(a.label(a.getString(R.string.challenge_title),13,a.muted));
        if(kind.equals("shake")){
            TextView title=a.label(a.getString(R.string.challenge_shake),22,a.text);title.setPadding(0,a.dp(8),0,a.dp(8));panel.addView(title);
            progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(SHAKES[level-1]);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(a.mint));panel.addView(progress,new LinearLayout.LayoutParams(-1,a.dp(10)));
            status=a.label("",14,a.muted);status.setPadding(0,a.dp(6),0,0);panel.addView(status);update();
            sensors=a.getSystemService(SensorManager.class);Sensor accel=sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if(accel==null){status.setText(R.string.challenge_no_sensor);a.handler.postDelayed(solved,1500);}else sensors.registerListener(this,accel,SensorManager.SENSOR_DELAY_GAME);
        }else{
            TextView question=a.label("",kind.equals("math")?34:28,a.text);question.setTypeface(android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL));question.setFontFeatureSettings("tnum");question.setPadding(0,a.dp(6),0,a.dp(6));question.setTag("challenge-question");panel.addView(question);
            if(kind.equals("math")){Object[] p=math(level,random);question.setText(p[0]+" ="); expected=p[1];}else{expected=code(level,random);question.setText((String)expected);question.setLetterSpacing(.08f);}
            answer=new EditText(a);answer.setSingleLine();answer.setTextSize(22);answer.setTextColor(a.text);answer.setHintTextColor(a.muted);answer.setHint(kind.equals("math")?R.string.challenge_answer:R.string.challenge_retype);answer.setTag("challenge-answer");
            answer.setInputType(kind.equals("math")?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            answer.setImeOptions(EditorInfo.IME_ACTION_DONE);answer.setOnEditorActionListener((v,action,e)->{check();return true;});
            answer.setBackground(a.shape(a.soft,16));answer.setPadding(a.dp(16),a.dp(12),a.dp(16),a.dp(12));panel.addView(answer,new LinearLayout.LayoutParams(-1,-2));
            status=a.label("",13,a.gold);status.setPadding(0,a.dp(6),0,0);status.setVisibility(android.view.View.GONE);status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);panel.addView(status);
        }
        LinearLayout buttons=new LinearLayout(a);buttons.setPadding(0,a.dp(12),0,0);
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-2,1);half.setMarginEnd(a.dp(8));buttons.addView(a.action(a.getString(R.string.back),false,()->{stop();cancel.run();}),half);
        if(!kind.equals("shake")){android.widget.Button check=a.action(a.getString(R.string.challenge_check),true,this::check);check.setTag("challenge-check");buttons.addView(check,new LinearLayout.LayoutParams(0,-2,1));}
        panel.addView(buttons);
        if(answer!=null){answer.requestFocus();answer.post(()->a.getSystemService(android.view.inputmethod.InputMethodManager.class).showSoftInput(answer,0));}
    }
    void check(){
        String typed=answer.getText().toString().trim();
        boolean ok=kind.equals("math")?typed.matches("\\d{1,6}")&&Integer.parseInt(typed)==(Integer)expected:sameCode((String)expected,typed);
        if(ok){stop();solved.run();return;}
        answer.setText("");boolean again=status.getVisibility()==android.view.View.VISIBLE;status.setText(R.string.challenge_wrong);status.setVisibility(android.view.View.VISIBLE);if(again)status.announceForAccessibility(status.getText());
        android.animation.ObjectAnimator.ofFloat(answer,"translationX",0,a.dp(8),-a.dp(8),a.dp(5),-a.dp(5),0).setDuration(320).start();
    }
    void update(){status.setText(String.format(Locale.getDefault(),"%d / %d",shakes,SHAKES[level-1]));progress.setProgress(shakes);}
    @Override public void onSensorChanged(SensorEvent e){
        float x=e.values[0],y=e.values[1],z=e.values[2];double g=Math.sqrt(x*x+y*y+z*z)/SensorManager.GRAVITY_EARTH;long now=android.os.SystemClock.elapsedRealtime();
        // A firm shake: more than 1.9 g, counted at most every 250 ms.
        if(g>1.9&&now-lastShake>250){lastShake=now;shakes++;update();if(shakes>=SHAKES[level-1]){stop();solved.run();}}
    }
    @Override public void onAccuracyChanged(Sensor s,int accuracy){}
    void stop(){if(sensors!=null){sensors.unregisterListener(this);sensors=null;}}
}
