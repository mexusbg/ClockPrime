package com.clockprime.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.time.LocalTime;
import java.util.List;

/** Lock-screen full-screen UI while an alarm or timer is ringing. */
public class RingActivity extends Activity {
    boolean dark;int bg,text,muted,mint,gold,soft,onAccent;
    LinearLayout root,list;TextView clock,date;FrameLayout halo;ImageView haloIcon;android.animation.ObjectAnimator pulse;
    final Handler handler=new Handler(Looper.getMainLooper());
    boolean volumeKey;String shownKey;Challenge challenge;
    /** Lets {@link RingLaunch} skip redundant starts while this screen is in front. */
    static volatile boolean visible;static volatile long shownAt;
    final Runnable tick=new Runnable(){public void run(){refresh();handler.postDelayed(this,1000);}};
    final Runnable storeChanged=()->{if(!isFinishing())handler.post(this::refresh);};

    @Override public void onCreate(Bundle b){
        dark=ThemeChoice.dark(this);setTheme(dark?R.style.AppTheme_Dark:R.style.AppTheme_Light);super.onCreate(b);
        setShowWhenLocked(true);setTurnScreenOn(true);
        setVolumeControlStream(android.media.AudioManager.STREAM_ALARM);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                |WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);
        // No requestDismissKeyguard: on a secure lock screen it would cover the alarm with the PIN prompt.
        DebugLog.log(this,"ring-ui","create action="+getIntent().getAction()+" "+DebugLog.state(this));
        consumeFire(getIntent());
        build();refresh();
    }

    @Override public void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);consumeFire(i);refresh();}

    /** AlarmManager delivers FIRE straight here so the UI opens even when unlocked. */
    void consumeFire(Intent intent){
        if(intent==null||!"FIRE".equals(intent.getAction()))return;
        ClockReceiver.fire(this,intent);
        intent.setAction(null);
        setIntent(intent);
    }
    @Override public void onResume(){super.onResume();visible=true;shownAt=android.os.SystemClock.elapsedRealtime();ClockStore.onChanged=()->handler.post(storeChanged);startPulse();handler.removeCallbacks(tick);handler.post(tick);}
    @Override public void onPause(){visible=false;if(challenge!=null){challenge.stop();challenge=null;shownKey=null;}if(pulse!=null)pulse.cancel();handler.removeCallbacks(tick);if(ClockStore.onChanged==storeChanged)ClockStore.onChanged=null;super.onPause();}
    @Override public void onDestroy(){handler.removeCallbacks(tick);super.onDestroy();}

    void palette(){bg=Color.parseColor(dark?"#101722":"#F3F6FC");text=Color.parseColor(dark?"#EEF3FF":"#17253C");muted=Color.parseColor(dark?"#A0ADC3":"#617189");mint=Color.parseColor(dark?"#9CBCFF":"#386BC0");gold=Color.parseColor(dark?"#F5CE8D":"#996319");soft=Color.parseColor(dark?"#253652":"#E8F0FC");onAccent=Color.parseColor(dark?"#132342":"#FFFFFF");}
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    RippleDrawable ripple(int color,int radius){return new RippleDrawable(ColorStateList.valueOf(dark?0x30FFFFFF:0x16000000),shape(color,radius),null);}

    void build(){
        palette();
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);
        int[] colors=dark?new int[]{Color.rgb(28,44,74),bg,Color.rgb(10,15,24)}:new int[]{Color.rgb(226,236,255),bg,Color.rgb(214,226,244)};
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,colors));
        // The keyboard (puzzle answers) pushes the cards up; the halo and date make room for it.
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets a=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());int ime=insets.getInsets(WindowInsets.Type.ime()).bottom;boolean typing=ime>a.bottom;
            v.setPadding(dp(24)+a.left,a.top+dp(16),dp(24)+a.right,Math.max(a.bottom,ime)+dp(typing?8:20));if(halo!=null){halo.setVisibility(typing?View.GONE:View.VISIBLE);date.setVisibility(typing?View.GONE:View.VISIBLE);}return insets;});
        setContentView(LocaleText.direction(root));
        WindowInsetsController bars=getWindow().getInsetsController();
        if(bars!=null)bars.setSystemBarsAppearance(dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        // Top: pulsing halo, large current time and today's date.
        halo=new FrameLayout(this);GradientDrawable ring=new GradientDrawable();ring.setShape(GradientDrawable.OVAL);ring.setColor((gold&0x00FFFFFF)|0x2E000000);ring.setStroke(dp(2),(gold&0x00FFFFFF)|0x66000000);halo.setBackground(ring);
        haloIcon=new ImageView(this);haloIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);halo.addView(haloIcon,new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.CENTER));
        LinearLayout.LayoutParams haloLp=new LinearLayout.LayoutParams(dp(96),dp(96));haloLp.setMargins(0,dp(24),0,dp(8));root.addView(halo,haloLp);
        clock=label("",72,text);clock.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));clock.setFontFeatureSettings("tnum");clock.setGravity(Gravity.CENTER);clock.setIncludeFontPadding(false);clock.setPadding(0,dp(8),0,0);root.addView(clock);
        date=label("",16,muted);date.setGravity(Gravity.CENTER);date.setPadding(0,dp(4),0,0);root.addView(date);
        // Bottom: what is ringing and its actions, within thumb reach.
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);// Opened without a touch, the scroll view takes focus and Android would draw its grey focus highlight over the cards.
        scroll.setDefaultFocusHighlightEnabled(false);scroll.setFocusable(false);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root.requestApplyInsets();
    }

    /** Gentle breathing halo; skipped when the system has animations turned off. */
    void startPulse(){
        if(halo==null||!android.animation.ValueAnimator.areAnimatorsEnabled())return;
        if(pulse==null){pulse=android.animation.ObjectAnimator.ofPropertyValuesHolder(halo,android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X,1f,1.12f),android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y,1f,1.12f),android.animation.PropertyValuesHolder.ofFloat(View.ALPHA,1f,.7f));pulse.setDuration(1100);pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);pulse.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());}
        if(!pulse.isStarted())pulse.start();
    }

    TextView label(String value,float size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}

    Button action(String title,boolean primary,Runnable run){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(17);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setMinHeight(dp(64));b.setMinimumHeight(dp(64));b.setStateListAnimator(null);
        if(primary){b.setTextColor(onAccent);b.setBackground(ripple(mint,32));}else{b.setTextColor(mint);b.setBackground(ripple(soft,32));}
        b.setOnClickListener(v->run.run());return b;
    }

    void refresh(){
        ClockStore store=new ClockStore(this);ClockSettings settings=new ClockSettings(this);
        LocalTime now=LocalTime.now();
        clock.setText(LocaleText.alarmTime(this,now.getHour(),now.getMinute()));
        date.setText(java.time.LocalDate.now().format(LocaleText.formatter(this,"EEEEdMMMM")));
        // Rebuild only when the ringing set changes, so a button is never replaced mid-press.
        StringBuilder key=new StringBuilder();List<JSONObject> items=new java.util.ArrayList<>();List<String> kinds=new java.util.ArrayList<>();
        for(String kind:List.of("alarms","timers"))for(JSONObject item:store.objects(kind))if(item.optBoolean("ringing")){items.add(item);kinds.add(kind);key.append(kind).append(item.optInt("id")).append(item.optString("name")).append(settings.canSnooze(item)).append(settings.snoozeMinutes(item)).append(';');}
        if(items.isEmpty()){finish();return;}
        if(key.toString().equals(shownKey))return;
        shownKey=key.toString();
        haloIcon.setImageDrawable(new ClockIcon(kinds.get(0).equals("alarms")?"Alarms":"Timers",gold));
        list.removeAllViews();
        for(int i=0;i<items.size();i++)compact(kinds.get(i),items.get(i),settings);
    }

    String detail(String kind,JSONObject item){
        if(!kind.equals("alarms"))return getString(R.string.finished);
        String time=LocaleText.alarmTime(this,item.optInt("hour"),item.optInt("minute")),group=item.optString("group");
        return group.isEmpty()?time:time+" · "+group;
    }
    String title(String kind,JSONObject item){String name=item.optString("name","").trim();return name.isEmpty()?getString(kind.equals("alarms")?R.string.alarm:R.string.timer):name;}

    /** One card per ringing alarm or timer, with side-by-side Snooze and Dismiss. */
    void compact(String kind,JSONObject item,ClockSettings settings){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(14),dp(18),dp(14));
        GradientDrawable surface=shape(Color.parseColor(dark?"#192334":"#FFFFFF"),24);surface.setStroke(dp(1),Color.parseColor(dark?"#2B3950":"#E1E7F0"));card.setBackground(surface);
        LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);cardLp.setMargins(0,dp(6),0,dp(6));list.addView(card,cardLp);
        TextView name=label(title(kind,item),20,text);name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(name);
        card.addView(label(detail(kind,item),14,muted));
        LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(12),0,0);final int id=item.optInt("id");
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-2,1);half.setMarginEnd(dp(8));
        if(kind.equals("alarms")&&settings.canSnooze(item))actions.addView(action(getString(R.string.snooze_action),false,()->act(kind,id,true)),half);
        boolean puzzle=kind.equals("alarms")&&Challenge.required(item);
        actions.addView(action(getString(R.string.dismiss),true,()->{if(!puzzle){act(kind,id,false);return;}
            // The puzzle replaces the buttons in this card; Back brings them back, snooze stays reachable there.
            if(challenge!=null)challenge.stop();int at=card.indexOfChild(actions);card.removeView(actions);
            challenge=new Challenge(this,item,()->{challenge=null;act(kind,id,false);},()->{challenge=null;card.removeViewAt(at);card.addView(actions,at);});card.addView(challenge.panel,at);}),new LinearLayout.LayoutParams(0,-2,1));
        card.addView(actions);
    }

    /** Acts on freshly loaded state; the cards may be older than the latest save. */
    void act(String kind,int id,boolean snooze){
        ClockStore store=new ClockStore(this);JSONObject target=store.find(kind,id);
        if(target!=null&&target.optBoolean("ringing")){
            DebugLog.log(this,"ring-ui",(snooze?"snooze ":"dismiss ")+kind+" "+id);
            if(snooze)store.snooze(target,new ClockSettings(this).snoozeMinutes(target));else store.dismiss(kind,target);
            store.save(true);store.refreshSound();
        }
        refresh();
    }

    @Override public boolean onKeyUp(int code,KeyEvent e){if(volumeKey&&(code==KeyEvent.KEYCODE_VOLUME_UP||code==KeyEvent.KEYCODE_VOLUME_DOWN)){volumeKey=false;return true;}return super.onKeyUp(code,e);}
    @Override public boolean onKeyDown(int code,KeyEvent event){
        if(code==KeyEvent.KEYCODE_VOLUME_UP||code==KeyEvent.KEYCODE_VOLUME_DOWN){
            if(volumeKey)return true;
            ClockSettings settings=new ClockSettings(this);String action=settings.text("volume_buttons","volume");
            if(!action.equals("volume")){
                volumeKey=true;
                if(event.getRepeatCount()==0){
                    ClockStore store=new ClockStore(this);
                    for(String kind:List.of("alarms","timers"))for(JSONObject a:store.objects(kind))if(a.optBoolean("ringing")){
                        if(action.equals("snooze")&&kind.equals("alarms")&&settings.canSnooze(a))store.snooze(a,settings.snoozeMinutes(a));
                        else if(!(kind.equals("alarms")&&Challenge.required(a)))store.dismiss(kind,a);
                    }
                    store.save(true);store.refreshSound();refresh();
                }
                return true;
            }
        }
        return super.onKeyDown(code,event);
    }

    @Override public void onBackPressed(){/* Stay until dismiss/snooze — matches alarm UX. */}
}
