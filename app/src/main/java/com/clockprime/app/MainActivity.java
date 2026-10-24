package com.clockprime.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    int BG,CARD,TEXT,MUTED,MINT,GOLD,LINE,SOFT,ON_ACCENT;boolean dark;
    ScrollView contentScroll;LinearLayout appHeader,bottomBar;String renderedTab="";int savedScroll;
    final Map<Integer,AlarmRowViews> alarmViews=new HashMap<>();final List<View> groupControls=new ArrayList<>();
    static class AlarmRowViews {LinearLayout card;CheckBox check;Switch toggle;String group;}
    Palette palette;
    void applyPalette(){Palette p=new Palette(this);palette=p;dark=p.dark;BG=p.bg;CARD=p.card;TEXT=p.text;MUTED=p.muted;MINT=p.accent;GOLD=p.gold;LINE=p.line;SOFT=p.soft;ON_ACCENT=p.onAccent;}
    /** Above about 130% text, a row's control moves below its label instead of squeezing it. */
    boolean largeFont(){return getResources().getConfiguration().fontScale>1.3f;}

    LinearLayout root,body;String tab="Alarms";ClockStore store;Handler handler=new Handler(Looper.getMainLooper());List<Runnable> ticks=new ArrayList<>();String snapshot="";TimeEntry timerInput;int[] timerValue={0,5,0};EditText timerName;String timerLabel="";
    final AlarmSelection selection=new AlarmSelection();
    AlarmOptions editingOptions;CompoundButton editingSound,editingVibration;int editingId=-1;JSONObject restoringDraft;
    AlertDialog alarmDialog;TimeEntry editingTime;AlarmGroupField editingGroup;EditText editingName;
    final Runnable ticker=new Runnable(){public void run(){for(Runnable r:new ArrayList<>(ticks))r.run();handler.postDelayed(this,250);}};
    final Runnable storeChanged=()->{if(isFinishing())return;ClockStore current=new ClockStore(MainActivity.this);if(!current.data.toString().equals(snapshot)){captureTimer();render();}};
    String formatKey(){ClockSettings s=new ClockSettings(this);return s.dateFormat()+"|"+s.hourFormat();}
    String formatSnapshot="";
    @Override public void onCreate(Bundle b){applyPalette();setTheme(dark?R.style.AppTheme_Dark:R.style.AppTheme_Light);super.onCreate(b);if(b!=null){tab=b.getString("tab","Alarms");savedScroll=b.getInt("scrollY");timerValue=b.getIntArray("timerValue")==null?timerValue:b.getIntArray("timerValue");timerLabel=b.getString("timerLabel","");selection.group=b.getString("selectionGroup");ArrayList<Integer> ids=b.getIntegerArrayList("selectionIds");if(ids!=null)selection.ids.addAll(ids);}if(b!=null&&b.containsKey("alarmDraft"))try{restoringDraft=new JSONObject(b.getString("alarmDraft"));}catch(Exception ignored){}RingService.channels(this);ClockStore boot=new ClockStore(this);boot.restore(false,false);if(boot.ringing())boot.refreshSound();setVolumeControlStream(android.media.AudioManager.STREAM_ALARM);handleIntent(getIntent());render();if(b==null)runAfterRender();else afterRender=null;if(restoringDraft!=null){JSONObject draft=restoringDraft;int id=draft.optInt("editingId",-1);alarmEditor(id<0?null:store.find("alarms",id),draft.optString("group"));}if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!SetupGuide.pendingFirstRun(this))requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);}
    @Override public void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleIntent(i);render();runAfterRender();}
    void runAfterRender(){Runnable r=afterRender;afterRender=null;if(r!=null)r.run();}
    Runnable afterRender;
    void handleIntent(Intent i){String to=i.getStringExtra("tab");if(to!=null&&List.of("Alarms","World","Stopwatch","Timers").contains(to)){tab=to;selection.clear();}
        String add=i.getStringExtra("add");i.removeExtra("add");afterRender="alarm".equals(add)?()->alarmEditor(null,null):"city".equals(add)?this::addCity:null;boolean ring=i.getBooleanExtra("ring",false);setShowWhenLocked(ring);setTurnScreenOn(ring);if(ring)tab="Alarms";}
    @Override public void onResume(){super.onResume();if(ThemeChoice.dark(this)!=dark){recreate();return;}ClockStore.onChanged=()->handler.post(storeChanged);if(root==null||!new ClockStore(this).data.toString().equals(snapshot)||!formatKey().equals(formatSnapshot)||!AlarmHealth.problems(this).toString().equals(healthSnapshot))render();UpcomingAlarm.refresh(this,store==null?new ClockStore(this):store,System.currentTimeMillis());handler.removeCallbacks(ticker);handler.post(ticker);if(!forceStopNotice())SetupGuide.showFirstRun(this);SetupGuide.refreshOpen();}
    /** After a force stop the alarms were cancelled; onCreate's restore has re-armed them. */
    boolean forceStopNotice(){
        android.content.SharedPreferences p=ClockApp.state(this);
        if(!launchPrompt||!p.getBoolean(ClockApp.FORCE_STOPPED,false))return false;
        p.edit().putBoolean(ClockApp.FORCE_STOPPED,false).apply();
        if(store==null||store.objects("alarms").stream().noneMatch(a->a.optBoolean("enabled")))return false;
        new AlertDialog.Builder(this).setTitle(tr(R.string.force_stop_title)).setMessage(tr(R.string.force_stop_detail)).setPositiveButton(android.R.string.ok,null).show();
        return true;
    }
    /** Instrumentation turns this off so the one-time dialog cannot cover checked screens. */
    static boolean launchPrompt=true;
    @Override public void onPause(){captureTimer();handler.removeCallbacks(ticker);if(ClockStore.onChanged!=null)ClockStore.onChanged=null;super.onPause();}
    @Override public void onDestroy(){if(alarmDialog!=null)alarmDialog.dismiss();if(editingGroup!=null){if(editingGroup.choices!=null)editingGroup.choices.dismiss();if(editingGroup.nameDialog!=null)editingGroup.nameDialog.dismiss();}super.onDestroy();}
    @Override public void onSaveInstanceState(Bundle b){captureTimer();if(alarmDialog!=null&&alarmDialog.isShowing())b.putString("alarmDraft",alarmDraft().toString());b.putInt("scrollY",contentScroll==null?0:contentScroll.getScrollY());b.putIntArray("timerValue",timerValue);b.putString("timerLabel",timerLabel);b.putString("tab",tab);b.putString("selectionGroup",selection.group);b.putIntegerArrayList("selectionIds",new ArrayList<>(selection.ids));super.onSaveInstanceState(b);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);SetupGuide.refreshOpen();UpcomingAlarm.refresh(this,new ClockStore(this),System.currentTimeMillis());}
    String tr(int id,Object... args){return args.length==0?getString(id):getString(id,args);}
    String quantity(int id,int count){return getResources().getQuantityString(id,count,count);}
    String tabLabel(String key){return tr(switch(key){case "Alarms"->R.string.alarms;case "World"->R.string.world;case "Stopwatch"->R.string.stopwatch;default->R.string.timers;});}
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(4));return t;}
    TextView mono(String s,int size){TextView t=text(s,size,TEXT);t.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));t.setFontFeatureSettings("tnum");return t;}
    RippleDrawable ripple(int color,int radius){return new RippleDrawable(ColorStateList.valueOf(dark?0x30FFFFFF:0x16000000),bg(color,radius),null);}
    Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(MINT);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(12),0,dp(12),0);b.setStateListAnimator(null);b.setBackground(ripple(SOFT,14));b.setOnClickListener(v->action.run());return b;}
    boolean rtl(){return getResources().getConfiguration().getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;}
    ImageView icon(String name,int color,int size){ImageView v=new ImageView(this);v.setImageDrawable(new ClockIcon(name,color));v.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return v;}
    View iconButton(String symbol,String description,boolean filled,Runnable action){return iconButton(symbol,description,filled,MINT,action);}
    View iconButton(String symbol,String description,boolean filled,int color,Runnable action){FrameLayout hit=new FrameLayout(this);hit.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));hit.setBackground(ripple(filled?SOFT:Color.TRANSPARENT,16));ImageView glyph=icon(symbol,color,24);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER);hit.addView(glyph,p);hit.setContentDescription(description);hit.setTooltipText(description);hit.setFocusable(true);hit.setOnClickListener(v->action.run());return hit;}
    View labeledIcon(String symbol,String label,boolean enabled,boolean active,Runnable action){LinearLayout v=column();v.setGravity(Gravity.CENTER);v.setPadding(dp(2),dp(8),dp(2),dp(8));GradientDrawable pill=bg(active?SOFT:Color.TRANSPARENT,18);if(active&&!dark)pill.setStroke(dp(1.5f),MINT);v.setBackground(new RippleDrawable(ColorStateList.valueOf(dark?0x30FFFFFF:0x16000000),pill,null));v.addView(icon(symbol,active?MINT:TEXT,24));TextView t=text(label,11,active?MINT:MUTED);t.setGravity(Gravity.CENTER);t.setMaxLines(2);/* Never below 11sp; large text sizes grow only to 140% so a word like Stopwatch is not split. */t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,11*Math.max(1f,Math.min(1.4f,getResources().getConfiguration().fontScale)));t.setMinHeight(dp(28));v.addView(t,new LinearLayout.LayoutParams(-1,-2));v.setContentDescription(label);v.setFocusable(true);v.setEnabled(enabled);v.setSelected(active);v.setAlpha(enabled?1f:.32f);v.setOnClickListener(w->action.run());return v;}
    /** TalkBack: "Alarms, selected, Tab, 1 of 4". */
    void tabRole(View v,int index,boolean active){v.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setSelected(active);info.getExtras().putCharSequence("AccessibilityNodeInfo.roleDescription",tr(R.string.tab_role));info.setCollectionItemInfo(new android.view.accessibility.AccessibilityNodeInfo.CollectionItemInfo(0,1,index,1,false,active));}});}
    void weighted(LinearLayout row,View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(v,p);}
    void gap(LinearLayout l,int h){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    LinearLayout card(){LinearLayout c=column();c.setPadding(dp(16),dp(12),dp(16),dp(12));GradientDrawable surface=bg(CARD,20);surface.setStroke(dp(1),LINE);c.setBackground(surface);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(6));body.addView(c,p);return c;}
    EditText field(String hint,String value){EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setTextSize(16);e.setHint(hint);e.setText(value);e.setPadding(dp(8),dp(10),dp(8),dp(10));return e;}
    /** Filled field with a small caption above it that stays visible once text is entered. */
    LinearLayout captioned(EditText e,String caption){LinearLayout box=column();TextView c=text(caption,12,MUTED);c.setPaddingRelative(dp(6),0,0,dp(2));c.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);box.addView(c);e.setBackground(bg(SOFT,16));e.setPaddingRelative(dp(16),dp(14),dp(16),dp(14));box.addView(e,new LinearLayout.LayoutParams(-1,-2));
        Runnable sync=()->c.setVisibility(e.getText().length()>0?View.VISIBLE:View.INVISIBLE);sync.run();e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence x,int a,int b,int d){}public void onTextChanged(CharSequence x,int a,int b,int d){}public void afterTextChanged(Editable x){sync.run();}});return box;}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    /** "Alarm set for Tomorrow 07:30 · in 8 h 12 min" after an alarm is switched on or saved. */
    String alarmSet(JSONObject a){return alarmSetText(this,a);}
    static String alarmSetText(Context c,JSONObject a){long at=a.optLong("next"),now=System.currentTimeMillis();return c.getString(R.string.alarm_set_for,Widgets.day(c,at,now)+" "+Widgets.time(c,at),Widgets.relative(c,at,now));}
    void commit(){store.save(false);store.refreshSound();render();}
    String undoText;Runnable undoAction;LinearLayout undoHolder;
    /** "Removed X · Undo" above the bottom bar for 8 s, like Settings › Holidays. */
    void offerUndo(String message,Runnable restore){undoText=message;undoAction=restore;handler.removeCallbacksAndMessages("undo");handler.postDelayed(()->{undoAction=null;updateUndo();},"undo",8000);updateUndo();}
    void updateUndo(){
        if(undoHolder==null)return;undoHolder.removeAllViews();if(undoAction==null)return;
        LinearLayout bar=row();bar.setPaddingRelative(dp(18),0,dp(6),0);bar.setMinimumHeight(dp(52));bar.setBackground(bg(TEXT,16));bar.setElevation(dp(3));bar.setTag("undo-bar");bar.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        TextView message=text(undoText,14,BG);message.setMaxLines(2);message.setEllipsize(TextUtils.TruncateAt.END);bar.addView(message,new LinearLayout.LayoutParams(0,-2,1));
        TextView undo=text(tr(R.string.undo),14,palette.inverseAccent);undo.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));undo.setGravity(Gravity.CENTER);undo.setMinHeight(dp(48));undo.setPaddingRelative(dp(14),0,dp(14),0);undo.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30FFFFFF),null,bg(Color.WHITE,12)));undo.setTag("undo");
        Runnable action=undoAction;undo.setOnClickListener(v->{undoAction=null;handler.removeCallbacksAndMessages("undo");action.run();updateUndo();});bar.addView(undo);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(2));undoHolder.addView(bar,lp);
    }
    /** Deletes one alarm or timer at once and offers Undo, which puts back exactly what was there. */
    void removeWithUndo(String kind,JSONObject o,String label){
        JSONArray list=store.list(kind);int index=0;for(int i=0;i<list.length();i++)if(list.optJSONObject(i).optInt("id")==o.optInt("id"))index=i;String saved=o.toString();final int at=index;
        store.remove(kind,o);commit();
        offerUndo(tr(R.string.holiday_removed,label),()->{ClockStore s=new ClockStore(this);try{s.restoreRemoved(kind,new JSONObject(saved),at);}catch(JSONException e){throw new IllegalStateException(e);}store=s;commit();});
    }
    String alarmLabel(JSONObject a){String name=a.optString("name").trim();String time=LocaleText.alarmTime(this,a.optInt("hour"),a.optInt("minute"));return name.isEmpty()?time:name+" · "+time;}
    void captureTimer(){if(timerInput!=null){try{timerValue=timerInput.values();}catch(Exception ignored){}if(timerName!=null)timerLabel=timerName.getText().toString();}}
    void updateHeader(){
        appHeader.removeAllViews();boolean selecting="Alarms".equals(tab)&&selection.group!=null;
        if(selecting)appHeader.addView(iconButton("close",tr(R.string.end_selection),false,()->{selection.clear();updateSelectionUi();}));
        LinearLayout title=column();title.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);TextView brand=text(selecting?groupLabel(selection.group):"ClockPrime",11,MUTED);brand.setLetterSpacing(.05f);title.addView(brand);TextView heading=text(selecting?quantity(R.plurals.selected_count,selection.ids.size()):tabLabel(tab),selecting?24:30,TEXT);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.addView(heading);appHeader.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        if(selecting)appHeader.addView(iconButton(allSelected()?"none":"all",tr(allSelected()?R.string.deselect_all:R.string.select_all,groupLabel(selection.group)),false,()->{List<JSONObject> scope=store.objects("alarms").stream().filter(a->a.optString("group").equals(selection.group)).collect(java.util.stream.Collectors.toList());boolean all=scope.stream().allMatch(a->selection.ids.contains(a.optInt("id")));selection.ids.clear();if(!all)for(JSONObject a:scope)selection.ids.add(a.optInt("id"));updateSelectionUi();}));
        else {if(tab.equals("Alarms"))appHeader.addView(iconButton("plus",tr(R.string.add_alarm),true,()->alarmEditor(null,null)));if(tab.equals("World"))appHeader.addView(iconButton("plus",tr(R.string.add_city),true,this::addCity));appHeader.addView(iconButton("settings",tr(R.string.settings),false,this::settings));}
    }
    boolean allSelected(){return !selection.ids.isEmpty()&&store.objects("alarms").stream().filter(a->a.optString("group").equals(selection.group)).allMatch(a->selection.ids.contains(a.optInt("id")));}
    final android.window.OnBackInvokedCallback selectionBack=()->{selection.clear();updateSelectionUi();};boolean backRegistered;
    void syncSelectionBack(){boolean active=selection.group!=null;if(active&&!backRegistered){getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,selectionBack);backRegistered=true;}else if(!active&&backRegistered){getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(selectionBack);backRegistered=false;}}
    @Override public void onBackPressed(){if(selection.group!=null){selection.clear();updateSelectionUi();}else super.onBackPressed();}
    void updateBottomBar(){
        bottomBar.removeAllViews();
        if(tab.equals("Alarms")&&selection.group!=null){
            // Skipping a one-time alarm is the same as turning it off, so Skip day needs a repeating alarm that is on.
            boolean anyOn=false,anyOff=false,anyRepeatOn=false;for(int id:selection.ids){JSONObject a=store.find("alarms",id);if(a!=null){anyOn|=a.optBoolean("enabled");anyOff|=!a.optBoolean("enabled");anyRepeatOn|=a.optBoolean("enabled")&&Schedule.repeats(a);}}
            weighted(bottomBar,labeledIcon("on",tr(R.string.turn_on),anyOff,false,()->selectionAction("ON")));
            weighted(bottomBar,labeledIcon("off",tr(R.string.turn_off),anyOn,false,()->selectionAction("OFF")));
            weighted(bottomBar,labeledIcon("skip",tr(R.string.skip_day),anyRepeatOn,false,()->selectionAction("SKIP")));
            weighted(bottomBar,labeledIcon("delete",tr(R.string.delete),!selection.ids.isEmpty(),false,()->new AlertDialog.Builder(this).setTitle(quantity(R.plurals.delete_count,selection.ids.size())).setMessage(tr(R.string.delete_selection,groupLabel(selection.group))).setNegativeButton(tr(R.string.cancel),null).setPositiveButton(tr(R.string.delete),(d,w)->selectionAction("DELETE")).show()));
        } else {List<String> tabs=List.of("Alarms","World","Stopwatch","Timers");for(String name:tabs){View v=labeledIcon(name,tabLabel(name),true,name.equals(tab),()->{if(tab.equals(name))return;captureTimer();selection.clear();tab=name;render();});tabRole(v,tabs.indexOf(name),name.equals(tab));weighted(bottomBar,v);}
            bottomBar.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setCollectionInfo(new android.view.accessibility.AccessibilityNodeInfo.CollectionInfo(1,tabs.size(),false,android.view.accessibility.AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE));}});}
    }
    void updateSelectionUi(){
        int offset=contentScroll==null?0:contentScroll.getScrollY();boolean selecting=selection.group!=null;
        for(Map.Entry<Integer,AlarmRowViews> item:alarmViews.entrySet()){
            AlarmRowViews row=item.getValue();boolean selected=selection.ids.contains(item.getKey()),allowed=selection.accepts(row.group);
            row.check.setOnCheckedChangeListener(null);row.check.setChecked(selected);row.check.setEnabled(allowed);row.check.setVisibility(selecting?(allowed?View.VISIBLE:View.INVISIBLE):View.GONE);row.toggle.setVisibility(selecting?View.GONE:View.VISIBLE);
            // Other groups' alarms cannot join this selection: dimmed, inert and hidden from TalkBack.
            boolean inert=selecting&&!allowed;row.card.setEnabled(!inert);row.card.setImportantForAccessibility(inert?View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            row.check.setOnCheckedChangeListener((v,on)->selectAlarm(row.group,item.getKey()));
            GradientDrawable surface=bg(selected?SOFT:CARD,20);surface.setStroke(dp(selected?2:1),selected?MINT:LINE);row.card.setBackground(surface);row.card.setAlpha(selecting&&!allowed?.4f:1f);
        }
        for(View control:groupControls)control.setVisibility(selecting?View.INVISIBLE:View.VISIBLE);
        syncSelectionBack();updateHeader();updateBottomBar();if(contentScroll!=null)contentScroll.post(()->contentScroll.scrollTo(0,offset));
    }
    void render(){
        int offset=contentScroll==null?savedScroll:(tab.equals(renderedTab)?contentScroll.getScrollY():0);savedScroll=0;renderedTab=tab;
        store=new ClockStore(this);snapshot=store.data.toString();formatSnapshot=formatKey();ticks.clear();alarmViews.clear();groupControls.clear();timerInput=null;timerName=null;
        root=column();int[] colors=dark?new int[]{Color.rgb(24,38,63),BG,Color.rgb(19,24,36)}:new int[]{Color.rgb(231,240,255),Color.rgb(248,250,254),Color.rgb(235,239,250)};
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors));root.setPadding(dp(20),0,dp(20),0);root.setForceDarkAllowed(false);
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets a=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());v.setPadding(dp(20)+a.left,a.top,dp(20)+a.right,Math.max(a.bottom,ime.bottom));return insets;});setContentView(LocaleText.direction(root));
        WindowInsetsController controller=getWindow().getInsetsController();if(controller!=null)controller.setSystemBarsAppearance(dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        appHeader=row();appHeader.setMinimumHeight(dp(80));appHeader.setPadding(0,dp(8),0,dp(8));root.addView(appHeader,new LinearLayout.LayoutParams(-1,-2));
        contentScroll=new ScrollView(this);contentScroll.setFillViewport(true);contentScroll.setClipToPadding(false);contentScroll.setVerticalScrollBarEnabled(false);body=column();body.setPadding(0,dp(8),0,dp(20));contentScroll.addView(body);root.addView(contentScroll,new LinearLayout.LayoutParams(-1,0,1));undoHolder=column();root.addView(undoHolder,new LinearLayout.LayoutParams(-1,-2));updateUndo();
        activeAlerts();switch(tab){case "Alarms":alarms();break;case "World":world();break;case "Stopwatch":stopwatch();break;default:timers();}
        bottomBar=row();bottomBar.setPadding(dp(2),dp(4),dp(2),dp(4));bottomBar.setMinimumHeight(dp(88));root.addView(bottomBar,new LinearLayout.LayoutParams(-1,-2));
        updateSelectionUi();root.requestApplyInsets();contentScroll.post(()->contentScroll.scrollTo(0,offset));
    }
    void activeAlerts(){for(String kind:List.of("alarms","timers"))for(JSONObject a:store.objects(kind))if(a.optBoolean("ringing")){LinearLayout c=card();c.addView(text(kind.equals("alarms")?tr(R.string.alarm_ringing):tr(R.string.timer_finished),12,GOLD));c.addView(text(a.optString("name","ClockPrime"),24,TEXT));LinearLayout r=row();weighted(r,button(tr(R.string.dismiss),()->{if(kind.equals("alarms")&&Challenge.required(a)){startActivity(new Intent(this,RingActivity.class));return;}store.dismiss(kind,a);commit();}));if(kind.equals("alarms")&&new ClockSettings(this).canSnooze(a))weighted(r,button(new ClockSettings(this).snoozeLabel(this,a),()->{store.snooze(a,new ClockSettings(this).snoozeMinutes(a));commit();}));c.addView(r);}}
    String groupLabel(String group){return group.isEmpty()?tr(R.string.ungrouped):group;}
    void selectAlarm(String group,int id){
        if(!selection.toggle(group,id))return;
        updateSelectionUi();
    }
    void selectionAction(String action){
        store=new ClockStore(this);
        try{store.bulk(selection.group,new HashSet<>(selection.ids),action);selection.clear();commit();}
        catch(IllegalArgumentException e){selection.clear();render();toast(e.getMessage());}
    }
    void alarms(){
        selection.ids.removeIf(id->{JSONObject a=store.find("alarms",id);return a==null||!a.optString("group").equals(selection.group);});
        // Group headers show their own next alarm; the overall line only helps when some alarms are ungrouped.
        if(store.objects("alarms").stream().anyMatch(x->x.optString("group").isEmpty())){JSONObject upcoming=UpcomingAlarm.earliest(store,System.currentTimeMillis());TextView summary=text(upcoming==null?tr(R.string.schedule_summary):tr(R.string.next_alarm,date(upcoming.optLong("next"))),13,MUTED);summary.setTag("next-summary");summary.setPaddingRelative(dp(4),0,0,dp(12));body.addView(summary);}
        healthCard();
        if(store.objects("alarms").isEmpty()&&store.list("groups").length()==0){LinearLayout c=card();c.addView(text(tr(R.string.empty_title),22,TEXT));c.addView(text(tr(R.string.empty_description),14,MUTED));}
        List<String> groups=new ArrayList<>();groups.add("");JSONArray array=store.list("groups");for(int i=0;i<array.length();i++)groups.add(array.optString(i));
        for(String group:groups){
            List<JSONObject> members=new ArrayList<>();for(JSONObject a:store.objects("alarms"))if(a.optString("group").equals(group))members.add(a);if(group.isEmpty()&&members.isEmpty())continue;
            members.sort(Comparator.comparingInt(a->a.optInt("hour")*60+a.optInt("minute")));boolean folded=!group.isEmpty()&&store.collapsed(group);long enabled=members.stream().filter(a->a.optBoolean("enabled")).count();
            List<View> memberViews=new ArrayList<>();
            if(!group.isEmpty()){
                LinearLayout groupHeader=column();groupHeader.setTag("group-"+group);groupHeader.setPadding(dp(4),dp(16),dp(4),dp(4));LinearLayout h=row(),heading=row();
                ImageView chevron=icon("right",MUTED,20);chevron.setRotation(folded?0:rtl()?-90:90);heading.addView(chevron);LinearLayout name=column();name.setPaddingRelative(dp(8),0,0,0);TextView title=text(group,18,TEXT);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);name.addView(title);name.addView(text(tr(R.string.group_count,quantity(R.plurals.alarm_count,members.size()),enabled),11,MUTED));JSONObject next=members.stream().filter(a->a.optBoolean("enabled")&&a.optLong("next")>System.currentTimeMillis()).min(Comparator.comparingLong(a->a.optLong("next"))).orElse(null);name.addView(text(next==null?tr(R.string.no_upcoming):tr(R.string.next_alarm,date(next.optLong("next"))),11,MUTED));heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));heading.setBackground(ripple(Color.TRANSPARENT,12));heading.setContentDescription(tr(folded?R.string.expand_group:R.string.collapse_group,group));heading.setFocusable(true);
                heading.setOnClickListener(v->{boolean collapse=!store.collapsed(group);android.transition.TransitionManager.beginDelayedTransition(body,new android.transition.AutoTransition().setDuration(180));for(View member:memberViews)member.setVisibility(collapse?View.GONE:View.VISIBLE);chevron.animate().rotation(collapse?0:90).setDuration(180).start();heading.setContentDescription(tr(collapse?R.string.expand_group:R.string.collapse_group,group));store.collapse(group,collapse);store.save(false);snapshot=store.data.toString();});h.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
                View gear=iconButton("more",tr(R.string.manage_group,group),false,()->groupActions(group));h.addView(gear);groupControls.add(gear);groupHeader.addView(h);
                body.addView(groupHeader);
            }
            for(JSONObject a:members){
                int id=a.optInt("id");LinearLayout c=card();c.setPadding(dp(14),dp(8),dp(14),dp(8));LinearLayout.LayoutParams compact=(LinearLayout.LayoutParams)c.getLayoutParams();compact.setMargins(0,dp(4),0,dp(4));memberViews.add(c);c.setVisibility(folded?View.GONE:View.VISIBLE);c.setTag("alarm-"+id);LinearLayout r=row();LinearLayout words=column();TextView time=mono(LocaleText.alarmTime(this,a.optInt("hour"),a.optInt("minute")),28);time.setPadding(0,0,0,dp(2));words.addView(time);words.addView(text(tr(R.string.alarm_subtitle,a.optString("name",tr(R.string.alarm)),Schedule.describe(this,a)),12,MUTED));
                if(a.optBoolean("enabled")){String note=date(a.optLong("next"));if(a.optBoolean("snoozed"))note=tr(R.string.snoozed_time,note);else if(a.optBoolean("postponed"))note=tr(R.string.postponed_time,note);if(a.optString("skipDate").compareTo(LocalDate.now().toString())>=0)note=a.optBoolean("paused")?tr(R.string.paused_card,note):tr(R.string.skipped_time,note);words.addView(text(note,11,MINT));}
                r.addView(words,new LinearLayout.LayoutParams(0,-2,1));
                FrameLayout controls=new FrameLayout(this);r.addView(controls,new LinearLayout.LayoutParams(dp(48),dp(48)));Switch toggle=new Switch(this);toggle.setChecked(a.optBoolean("enabled"));toggle.setContentDescription(tr(R.string.enable_alarm,a.optString("name"),time.getText()));toggle.setOnCheckedChangeListener((b,on)->{store.enable(a,on);commit();if(on)toast(alarmSet(a));});palette.tint(toggle);controls.addView(toggle,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
                // The whole 48dp frame flips the switch (or selects), so a near miss never opens the editor.
                controls.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);controls.setOnClickListener(v->{if(selection.group!=null){if(selection.accepts(group))selectAlarm(group,id);}else toggle.toggle();});
                CheckBox check=new CheckBox(this);check.setMinHeight(dp(48));check.setMinimumHeight(dp(48));check.setButtonTintList(ColorStateList.valueOf(MINT));check.setContentDescription(tr(R.string.select_alarm,a.optString("name"),groupLabel(group)));controls.addView(check,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));c.addView(r);
                AlarmRowViews views=new AlarmRowViews();views.card=c;views.check=check;views.toggle=toggle;views.group=group;alarmViews.put(id,views);
                c.setOnClickListener(v->{if(selection.group!=null)selectAlarm(group,id);else alarmEditor(a,group);});c.setOnLongClickListener(v->{selectAlarm(group,id);return true;});
            }
            gap(body,8);
        }
        LinearLayout add=row();add.setGravity(Gravity.CENTER);add.addView(icon("group",MINT,20));TextView newGroup=text(tr(R.string.new_group),13,MINT);newGroup.setPaddingRelative(dp(8),0,0,0);add.addView(newGroup);add.setBackground(ripple(Color.TRANSPARENT,14));add.setMinimumHeight(dp(48));add.setContentDescription(tr(R.string.new_group));add.setOnClickListener(v->groupName(null));groupControls.add(add);body.addView(add);

    }
    String healthSnapshot="";
    /** Shown only while an alarm is on: conditions that would keep it from being heard. */
    void healthCard(){
        List<String> problems=AlarmHealth.problems(this);healthSnapshot=problems.toString();
        if(problems.isEmpty()||store.objects("alarms").stream().noneMatch(a->a.optBoolean("enabled")))return;
        LinearLayout c=card();c.setTag("health");c.addView(text(tr(R.string.health_title),16,GOLD));
        for(String problem:problems){LinearLayout r=row();LinearLayout words=column();words.addView(text(tr(AlarmHealth.title(problem)),14,TEXT));words.addView(text(tr(AlarmHealth.detail(problem)),12,MUTED));r.addView(words,new LinearLayout.LayoutParams(0,-2,1));r.addView(button(tr(R.string.fix),()->AlarmHealth.fix(this,problem)));c.addView(r);}
    }
    String days(int mask){return LocaleText.days(this,mask);}
    String date(long millis){return LocaleText.relative(this,millis);}
    void groupName(String old){EditText e=field(tr(R.string.group_name),old==null?"":old);AlertDialog d=new AlertDialog.Builder(this).setTitle(old==null?tr(R.string.new_group):tr(R.string.rename_group)).setView(LocaleText.direction(e)).setNegativeButton(tr(R.string.cancel),null).setPositiveButton(tr(R.string.save),null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{String n=e.getText().toString().trim();if(n.isEmpty()){e.setError(tr(R.string.enter_name));return;}JSONArray g=store.list("groups");for(int i=0;i<g.length();i++)if(g.optString(i).equalsIgnoreCase(n)&&!g.optString(i).equals(old)){e.setError(tr(R.string.group_exists));return;}if(old!=null){for(int i=0;i<g.length();i++)if(g.optString(i).equals(old))g.remove(i);for(JSONObject a:store.objects("alarms"))if(a.optString("group").equals(old))ClockStore.put(a,"group",n);}if(old!=null){store.collapse(n,store.collapsed(old));JSONObject folded=store.data.optJSONObject("collapsedGroups");if(folded!=null)folded.remove(old);}g.put(n);d.dismiss();commit();}));d.show();}
    /** Group menu in blocks: add · on/off (only what applies) · skip, pause, restore · rename · delete, set apart in the warning colour. */
    AlertDialog groupMenu;
    void groupActions(String group){
        List<JSONObject> members=new ArrayList<>();for(JSONObject a:store.objects("alarms"))if(a.optString("group").equals(group))members.add(a);
        boolean anyOn=members.stream().anyMatch(a->a.optBoolean("enabled")),anyOff=members.stream().anyMatch(a->!a.optBoolean("enabled")),anySkipped=members.stream().anyMatch(a->!a.optString("skipDate").isEmpty()||a.optBoolean("skipDisabled"));
        LinearLayout list=column();list.setPadding(0,dp(4),0,dp(8));
        List<List<Object[]>> blocks=new ArrayList<>();
        blocks.add(Collections.<Object[]>singletonList(new Object[]{R.string.add_alarm,(Runnable)()->alarmEditor(null,group)}));
        List<Object[]> power=new ArrayList<>();if(anyOff)power.add(new Object[]{R.string.enable_all,(Runnable)()->groupMutate(group,1,0)});if(anyOn)power.add(new Object[]{R.string.disable_all,(Runnable)()->groupMutate(group,2,0)});blocks.add(power);
        List<Object[]> skips=new ArrayList<>();if(anyOn){skips.add(new Object[]{R.string.skip_occurrences,(Runnable)()->groupMutate(group,4,0)});skips.add(new Object[]{R.string.pause_group,(Runnable)()->pauseGroup(group)});}if(anySkipped)skips.add(new Object[]{R.string.restore_skips,(Runnable)()->groupMutate(group,9,0)});blocks.add(skips);
        blocks.add(Collections.<Object[]>singletonList(new Object[]{R.string.rename_group,(Runnable)()->groupName(group)}));
        blocks.add(Collections.<Object[]>singletonList(new Object[]{R.string.delete_group,(Runnable)()->new AlertDialog.Builder(this).setTitle(tr(R.string.delete_group)).setMessage(tr(R.string.delete_group_members,group,quantity(R.plurals.alarm_count,members.size()))).setNegativeButton(tr(R.string.cancel),null).setPositiveButton(tr(R.string.delete),(x,y)->groupMutate(group,8,0)).show()}));
        for(List<Object[]> block:blocks){if(block.isEmpty())continue;if(list.getChildCount()>0){View line=new View(this);line.setBackgroundColor(LINE);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(dp(20),dp(4),dp(20),dp(4));list.addView(line,lp);}
            for(Object[] item:block){int label=(Integer)item[0];boolean destructive=label==R.string.delete_group;TextView t=text(tr(label),16,destructive?GOLD:TEXT);t.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);t.setMinHeight(dp(48));t.setPaddingRelative(dp(24),0,dp(24),0);t.setBackground(ripple(Color.TRANSPARENT,0));t.setTag("group-menu-"+getResources().getResourceEntryName(label));
                t.setOnClickListener(v->{if(groupMenu!=null)groupMenu.dismiss();((Runnable)item[1]).run();});list.addView(t,new LinearLayout.LayoutParams(-1,-2));}}
        ScrollView scroll=new ScrollView(this);scroll.addView(list);groupMenu=new AlertDialog.Builder(this).setTitle(group).setView(LocaleText.direction(scroll)).show();
    }
    /** Pauses every repeating, enabled alarm of a group through a date or for its next N rings. */
    void pauseGroup(String group){
        String[] items={tr(R.string.pause_until_date),tr(R.string.pause_for_rings)};
        new AlertDialog.Builder(this).setTitle(tr(R.string.pause_group)).setItems(items,(d,n)->{
            if(n==0){LocalDate today=LocalDate.now();DatePickerDialog picker=new DatePickerDialog(this,(v,y,m,day)->{LocalDate until=LocalDate.of(y,m+1,day);for(JSONObject a:store.objects("alarms"))if(a.optString("group").equals(group)&&a.optBoolean("enabled"))store.pause(a,until);commit();},today.getYear(),today.getMonthValue()-1,today.getDayOfMonth());picker.getDatePicker().setMinDate(System.currentTimeMillis()-1000);picker.show();}
            else{int[] counts=AlarmOptions.PAUSE_RINGS;String[] labels=new String[counts.length];for(int i=0;i<counts.length;i++)labels[i]=getResources().getQuantityString(R.plurals.pause_rings_value,counts[i],counts[i]);
                new AlertDialog.Builder(this).setTitle(tr(R.string.pause_for_rings)).setItems(labels,(x,i)->{for(JSONObject a:store.objects("alarms"))if(a.optString("group").equals(group)&&a.optBoolean("enabled"))store.pauseRings(a,counts[i]);commit();}).show();}
        }).show();
    }
    void groupMutate(String group,int action,int minutes){
        for(JSONObject a:store.objects("alarms"))if(a.optString("group").equals(group)){if(action==1&&!a.optBoolean("enabled"))store.enable(a,true);if(action==2)store.enable(a,false);if(action==4)store.skipOnce(a);if(action==9)store.restoreSkips(a);if(action==8)store.remove("alarms",a);}
        if(action==8){JSONObject folded=store.data.optJSONObject("collapsedGroups");if(folded!=null)folded.remove(group);JSONArray groups=store.list("groups");for(int i=0;i<groups.length();i++)if(groups.optString(i).equals(group)){groups.remove(i);break;}}
        commit();
    }
    static final int SERIES_MAX=10;
    /** Minutes of day for {@code count} alarms spread evenly from start to end (past midnight if end is earlier). */
    static int[] seriesTimes(int start,int end,int count){int span=Math.floorMod(end-start,1440);if(span==0)span=60;int[] out=new int[count];for(int i=0;i<count;i++)out[i]=Math.floorMod(start+Math.round(span*i/(float)(count-1)),1440);return out;}
    /** Settings a new alarm starts from: the schedule, sound, dismiss and snooze choices of the last saved alarm. */
    static JSONObject template(JSONObject a){JSONObject t=new JSONObject();for(String k:List.of("days","repeat","every","monthDay","skipHolidays","tone","sound","vibrate","snoozeMinutes","snoozeLimit","challenge","difficulty","autoDelete"))if(a.has(k))ClockStore.put(t,k,a.opt(k));return t;}
    void alarmEditor(JSONObject original,String group){
        LinearLayout content=column();content.setBackgroundColor(CARD);content.setPadding(dp(18),dp(8),dp(18),dp(8));ScrollView scroll=new ScrollView(this);scroll.addView(content);
        LocalTime now=LocalTime.now().plusMinutes(1);TimeEntry entry=new TimeEntry(false,new int[]{original==null?now.getHour():original.optInt("hour"),original==null?now.getMinute():original.optInt("minute"),0});editingTime=entry;content.addView(entry);
        JSONObject template=store.data.optJSONObject("alarmTemplate");JSONObject source=restoringDraft!=null?restoringDraft:original!=null?original:template!=null?Schedule.copy(template):new JSONObject();editingId=original==null?-1:original.optInt("id");
        EditText name=field(tr(R.string.alarm_name),source.optString("name"));editingName=name;LinearLayout.LayoutParams nameSpace=new LinearLayout.LayoutParams(-1,-2);nameSpace.setMargins(0,dp(4),0,dp(8));content.addView(captioned(name,tr(R.string.alarm_name)),nameSpace);
        AlarmGroupField picker=new AlarmGroupField(this,source.optString("group",group==null?"":group));editingGroup=picker;if(restoringDraft!=null){picker.newGroup=source.optBoolean("draftGroup");picker.refresh();}content.addView(picker);gap(content,8);
        AlarmOptions options=new AlarmOptions(this,content,source);editingOptions=options;
        CompoundButton sound=options.sound,vibration=options.vibrate;editingSound=sound;editingVibration=vibration;gap(content,8);
        // New alarms only: create a series spread evenly from the chosen time to a last time, all with these settings.
        Spinner seriesCount=null;int[] seriesEnd={-1};
        if(original==null){LinearLayout holder=column();LinearLayout card=options.section(holder,R.string.series_section);
            String[] counts=new String[SERIES_MAX];counts[0]=tr(R.string.series_single);for(int n=2;n<=SERIES_MAX;n++)counts[n-1]=quantity(R.plurals.alarm_count,n);
            Spinner count=options.spinner(counts);count.setContentDescription(tr(R.string.series_label));count.setTag("series-count");seriesCount=count;options.pick(card,R.string.series_label,count);
            LinearLayout endRow=options.dateLine(card,R.string.series_last,()->{});endRow.setTag("series-last");
            Runnable showEnd=()->{AlarmOptions.detail(endRow).setText(seriesEnd[0]<0?tr(R.string.series_last_default):LocaleText.alarmTime(this,seriesEnd[0]/60,seriesEnd[0]%60));};
            endRow.setOnClickListener(v->{int[] t;try{t=entry.values();}catch(IllegalArgumentException e){return;}int start=t[0]*60+t[1],last=seriesEnd[0]<0?(start+60)%1440:seriesEnd[0];
                new android.app.TimePickerDialog(this,(x,h,m)->{seriesEnd[0]=h*60+m;showEnd.run();},last/60,last%60,LocaleText.use24Hour(this)).show();});showEnd.run();
            endRow.setVisibility(View.GONE);count.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){endRow.setVisibility(pos==0?View.GONE:View.VISIBLE);}});
            content.addView(holder,content.indexOfChild(picker)+1);}
        final Spinner series=seriesCount;
        if(restoringDraft!=null){entry.setTime(source.optInt("hour"),source.optInt("minute"),false);restoringDraft=null;}
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(original==null?tr(R.string.new_alarm):tr(R.string.edit_alarm)).setView(LocaleText.direction(scroll)).setNegativeButton(tr(R.string.cancel),null).setPositiveButton(tr(R.string.save),null);
        if(original!=null)builder.setNeutralButton(tr(R.string.delete),(d,w)->removeWithUndo("alarms",original,alarmLabel(original)));AlertDialog dialog=builder.create();alarmDialog=dialog;dialog.setOnShowListener(v->{Button remove=dialog.getButton(AlertDialog.BUTTON_NEUTRAL);if(remove!=null)remove.setTextColor(GOLD);dialog.getButton(-1).setOnClickListener(w->{int[] time;try{time=entry.values();options.validate();JSONObject probe=new JSONObject();ClockStore.put(probe,"hour",time[0]);ClockStore.put(probe,"minute",time[1]);options.write(probe,false);if(Schedule.next(probe,System.currentTimeMillis())==0)throw new IllegalArgumentException(tr(R.string.no_upcoming_schedule));}catch(IllegalArgumentException ex){if(!(ex instanceof TimeWheel.OutOfRange))toast(ex.getMessage());return;}JSONObject a=original==null?new JSONObject():original;if(original==null){ClockStore.put(a,"id",store.id());store.list("alarms").put(a);}ClockStore.put(a,"hour",time[0]);ClockStore.put(a,"minute",time[1]);ClockStore.put(a,"name",name.getText().toString().trim().isEmpty()?tr(R.string.alarm):name.getText().toString().trim());ClockStore.put(a,"group",picker.saveInto(store));options.write(a,false);ClockStore.put(a,"sound",sound.isChecked());ClockStore.put(a,"vibrate",vibration.isChecked());ClockStore.put(a,"missed",false);ClockStore.put(a,"postponed",false);store.enable(a,original==null||a.optBoolean("enabled"));options.applyPause(store,a);ClockStore.put(store.data,"alarmTemplate",template(a));
            int count=series==null?1:series.getSelectedItemPosition()+1;if(count>1){int start=time[0]*60+time[1];int[] times=seriesTimes(start,seriesEnd[0]<0?start+60:seriesEnd[0],count);
                for(int i=1;i<count;i++){int at=times[i];JSONObject copy=Schedule.copy(a);ClockStore.put(copy,"id",store.id());ClockStore.put(copy,"hour",at/60);ClockStore.put(copy,"minute",at%60);for(String k:List.of("next","skipDate","paused","ringing","snoozed"))copy.remove(k);store.list("alarms").put(copy);store.enable(copy,true);options.applyPause(store,copy);}}
            commit();dialog.dismiss();if(count>1)toast(tr(R.string.series_created,quantity(R.plurals.alarm_count,count)));else if(a.optBoolean("enabled"))toast(alarmSet(a));});});dialog.setOnDismissListener(v->{if(!isChangingConfigurations())options.releaseUnused();});dialog.show();
    }
    JSONObject alarmDraft(){JSONObject d=new JSONObject();ClockStore.put(d,"editingId",editingId);ClockStore.put(d,"name",editingName.getText().toString());ClockStore.put(d,"group",editingGroup.selected);ClockStore.put(d,"draftGroup",editingGroup.newGroup);ClockStore.put(d,"sound",editingSound.isChecked());ClockStore.put(d,"vibrate",editingVibration.isChecked());int[] hm=new int[2];for(int i=0;i<2;i++){TimeWheel w=editingTime.wheels[i];int value=w.selected();if(w.editing)try{int n=Integer.parseInt(w.editor.getText().toString());if(n>=w.minimum&&n<=w.maximum)value=n;}catch(Exception ignored){}hm[i]=value;}if(editingTime.twelve)hm[0]=to24(hm[0],editingTime.meridiem.selected()==1);ClockStore.put(d,"hour",hm[0]);ClockStore.put(d,"minute",hm[1]);editingOptions.write(d,true);return d;}
    /** 12-hour clock face: 0 → 12 AM, 12 → 12 PM, 13 → 1 PM. */
    static int hour12(int hour){return hour%12==0?12:hour%12;}
    static int to24(int hour12,boolean pm){return hour12%12+(pm?12:0);}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if((request==301||request==302)&&editingOptions!=null)editingOptions.result(request,result,data);}
    class TimeEntry extends LinearLayout {
        /** Alarms follow the 12/24-hour setting: 1–12 hours plus an AM/PM wheel (not in {@link #wheels}); values() is always 24-hour. */
        final boolean seconds,twelve;final TimeWheel[] wheels;TimeWheel meridiem;
        TimeEntry(boolean seconds,int[] initial){super(MainActivity.this);this.seconds=seconds;twelve=!seconds&&!LocaleText.use24Hour(MainActivity.this);setOrientation(HORIZONTAL);setLayoutDirection(LAYOUT_DIRECTION_LTR);setGravity(Gravity.CENTER_VERTICAL);setPadding(0,dp(4),0,dp(8));int count=seconds?3:2;wheels=new TimeWheel[count];
            for(int i=0;i<count;i++){LinearLayout col=column();String name=tr(i==0?R.string.hours:i==1?R.string.minutes:R.string.seconds);TextView label=text(name,11,MUTED);label.setGravity(Gravity.CENTER);col.addView(label);TimeWheel wheel=i==0&&twelve?new TimeWheel(MainActivity.this,1,12,hour12(initial[0]),name,false,TEXT,MUTED,SOFT,GOLD,null):new TimeWheel(MainActivity.this,i==0?(seconds?99:23):59,initial[i],name,seconds,TEXT,MUTED,SOFT,GOLD);wheels[i]=wheel;col.addView(wheel,new LinearLayout.LayoutParams(-1,Math.round(wheel.rowHeight*3)));addView(col,new LinearLayout.LayoutParams(0,-2,1));
                if(i<count-1){TextView colon=mono(":",seconds?26:32);colon.setGravity(Gravity.CENTER);colon.setTextColor(MUTED);colon.setPadding(0,dp(20),0,0);addView(colon,new LinearLayout.LayoutParams(dp(seconds?14:20),-1));}
            }
            if(twelve){LinearLayout col=column();String name=tr(R.string.am_pm);TextView label=text(name,11,MUTED);label.setGravity(Gravity.CENTER);label.setMaxLines(1);label.setEllipsize(TextUtils.TruncateAt.END);col.addView(label);
                String[] marks=java.text.DateFormatSymbols.getInstance(LocaleText.formatLocale(MainActivity.this)).getAmPmStrings();meridiem=new TimeWheel(MainActivity.this,0,1,initial[0]>=12?1:0,name,false,TEXT,MUTED,SOFT,GOLD,marks);
                col.addView(meridiem,new LinearLayout.LayoutParams(-1,Math.round(wheels[0].rowHeight*3)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,.8f);lp.setMarginStart(dp(10));addView(col,lp);}
            for(int i=0;i<wheels.length-1;i++){final TimeWheel next=wheels[i+1];wheels[i].nextEditor=next::beginEditing;wheels[i].editor.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);}
            List<TimeWheel> all=new ArrayList<>(Arrays.asList(wheels));if(meridiem!=null)all.add(meridiem);
            for(TimeWheel wheel:all)wheel.beforeEdit=()->{for(TimeWheel other:wheels)if(other!=wheel)try{other.finishEditing();}catch(IllegalArgumentException e){return false;}return true;};
        }
        int[] values(){int[] value={0,0,0};for(int i=0;i<wheels.length;i++)value[i]=wheels[i].getValue();if(twelve)value[0]=to24(value[0],meridiem.getValue()==1);return value;}
        void setTime(int hour,int minute,boolean animate){wheels[0].setValue(twelve?hour12(hour):hour,animate);wheels[1].setValue(minute,animate);if(twelve)meridiem.setValue(hour>=12?1:0,animate);}
        void set(int[] value){for(int i=0;i<wheels.length;i++)wheels[i].setValue(value[i],true);wheels[0].hideKeyboard();}
    }
    void timers(){
        LinearLayout c=card();timerInput=new TimeEntry(true,timerValue);c.addView(timerInput);timerName=field(tr(R.string.timer_name),timerLabel);LinearLayout.LayoutParams labelSpace=new LinearLayout.LayoutParams(-1,-2);labelSpace.setMargins(0,dp(4),0,dp(8));c.addView(captioned(timerName,tr(R.string.timer_name)),labelSpace);
        for(int[] presetRow:new int[][]{{5,10,15},{20,30}}){LinearLayout chips=row();for(int minutes:presetRow){Button preset=button(tr(R.string.widget_minutes_short,minutes),()->{timerValue=new int[]{0,minutes,0};timerInput.set(timerValue);});preset.setTag("preset-"+minutes);weighted(chips,preset);}c.addView(chips);}
        Button start=button(tr(R.string.start_timer),()->{try{timerValue=timerInput.values();long duration=(timerValue[0]*3600L+timerValue[1]*60L+timerValue[2])*1000;if(duration==0){toast(tr(R.string.positive_duration));return;}JSONObject t=new JSONObject();ClockStore.put(t,"id",store.id());String name=timerName.getText().toString().trim();ClockStore.put(t,"name",name.isEmpty()?tr(R.string.timer):name);ClockStore.put(t,"duration",duration);store.list("timers").put(t);store.timerStart(t,duration);commit();}catch(IllegalArgumentException ex){if(!(ex instanceof TimeWheel.OutOfRange))toast(ex.getMessage());}});start.setTextColor(ON_ACCENT);start.setBackground(bg(MINT,16));LinearLayout.LayoutParams startSpace=new LinearLayout.LayoutParams(-1,-2);startSpace.setMargins(0,dp(8),0,0);c.addView(start,startSpace);
        // Compact rows; running (and ringing) timers first, outlined in the accent colour with a bar of the time left.
        List<JSONObject> list=store.objects("timers");list.sort(Comparator.comparingInt(t->t.optBoolean("running")||t.optBoolean("ringing")?0:1));
        for(JSONObject t:list){boolean running=t.optBoolean("running");String name=t.optString("name");LinearLayout item=card();item.setPaddingRelative(dp(16),dp(8),dp(8),dp(6));item.setTag("timer-"+t.optInt("id"));LinearLayout.LayoutParams compact=(LinearLayout.LayoutParams)item.getLayoutParams();compact.setMargins(0,dp(4),0,dp(4));
            if(running){GradientDrawable s=bg(CARD,20);s.setStroke(dp(2),MINT);item.setBackground(s);}
            LinearLayout top=row();if(largeFont()){top.setOrientation(LinearLayout.VERTICAL);top.setGravity(Gravity.START);}LinearLayout words=column();TextView title=text(name,16,running?MINT:TEXT);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);words.addView(title);
            words.addView(text(t.optBoolean("autoSilenced")?tr(R.string.auto_silenced):t.optBoolean("ringing")?tr(R.string.finished):t.optBoolean("missed")?tr(R.string.finished_offline):running?tr(R.string.counting_down):t.optBoolean("paused")?tr(R.string.timer_paused):tr(R.string.timer_ready),12,MUTED));top.addView(words,largeFont()?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
            TextView time=mono("",32);time.setPaddingRelative(largeFont()?0:dp(8),0,dp(8),0);top.addView(time);item.addView(top);
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgressTintList(ColorStateList.valueOf(MINT));bar.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);bar.setTag("timer-progress");LinearLayout.LayoutParams barSpace=new LinearLayout.LayoutParams(-1,dp(4));barSpace.setMargins(0,dp(4),dp(8),dp(2));
            if(running||t.optBoolean("paused"))item.addView(bar,barSpace);
            Runnable tick=()->{long left=ClockStore.remaining(t);time.setText(duration(left));long total=Math.max(1,t.optLong("duration"));bar.setProgress((int)Math.max(0,Math.min(1000,1000*left/total)));};ticks.add(tick);tick.run();
            LinearLayout r=row();Button go=button(running?tr(R.string.pause):tr(R.string.start),()->{if(t.optBoolean("running"))store.timerPause(t);else store.timerStart(t,t.optLong("remaining")>0?t.optLong("remaining"):t.optLong("duration"));commit();});go.setContentDescription(tr(running?R.string.timer_pause_named:R.string.timer_start_named,name));weighted(r,go);
            Button reset=button(tr(R.string.reset),()->{store.dismiss("timers",t);commit();});reset.setContentDescription(tr(R.string.timer_reset_named,name));weighted(r,reset);
            if(running||(t.optBoolean("paused")&&!t.optBoolean("ringing"))){Button less=button(tr(R.string.remove_minute),()->{if(store.timerAdjust(t,-60000))commit();});less.setTag("timer-less-"+t.optInt("id"));weighted(r,less);Button more=button(tr(R.string.add_minute),()->{if(store.timerAdjust(t,60000))commit();});more.setTag("timer-more-"+t.optInt("id"));weighted(r,more);}
            View trash=iconButton("delete",tr(R.string.timer_delete_named,name),false,GOLD,()->removeWithUndo("timers",t,name));trash.setTag("timer-delete");r.addView(trash);item.addView(r);}
    }
    static String duration(long ms){long s=Math.max(0,(ms+999)/1000);return String.format(Locale.getDefault(),"%02d:%02d:%02d",s/3600,s/60%60,s%60);}
    static String elapsed(long ms){long cent=Math.max(0,ms/10);return String.format(Locale.getDefault(),"%02d:%02d:%02d.%02d",cent/360000,cent/6000%60,cent/100%60,cent%100);}
    /** Lap times as the dial shows them: hours only once the stopwatch passes one hour. */
    static String lapTime(long ms,boolean hours){String v=elapsed(ms);return hours?v:v.substring(3);}
    long stopwatchTime(){return store.stopwatchTime();}
    void stopwatch(){LinearLayout c=card();boolean running=store.data.optBoolean("swRunning");StopwatchFace display=new StopwatchFace(this,this::stopwatchTime,running,TEXT,MUTED,MINT,LINE);c.addView(display,new LinearLayout.LayoutParams(-1,dp(280)));c.setContentDescription(tr(R.string.stopwatch));Runnable tick=()->{c.setContentDescription(tr(R.string.stopwatch)+", "+elapsed(stopwatchTime()));display.invalidate();};ticks.add(tick);tick.run();LinearLayout r=row();Button primary=button(running?tr(R.string.pause):tr(R.string.start),()->{store.stopwatchToggle();store.save();store.syncStopwatch();render();});primary.setTextColor(ON_ACCENT);primary.setBackground(ripple(MINT,18));weighted(r,primary);weighted(r,button(running?tr(R.string.lap):tr(R.string.reset),()->{if(running)store.stopwatchLap();else store.stopwatchReset();store.save();store.syncStopwatch();render();}));c.addView(r);JSONArray laps=store.data.optJSONArray("laps");if(laps==null||laps.length()==0)return;
        boolean hours=laps.optLong(laps.length()-1)>=3600000;
        long fastest=Long.MAX_VALUE,slowest=0;for(int i=0;i<laps.length();i++){long split=laps.optLong(i)-(i==0?0:laps.optLong(i-1));fastest=Math.min(fastest,split);slowest=Math.max(slowest,split);}LinearLayout headings=row();headings.setPaddingRelative(0,dp(8),0,0);weighted(headings,text(tr(R.string.laps),11,MUTED));weighted(headings,text(tr(R.string.split_label),11,MUTED));weighted(headings,text(tr(R.string.total_label),11,MUTED));
        View copy=iconButton("copy",tr(R.string.copy_laps),false,()->{StringBuilder out=new StringBuilder(tr(R.string.export_header).trim()).append("\n");long prev=0;for(int i=0;i<laps.length();i++){long total=laps.optLong(i);out.append(tr(R.string.lap_number,i+1)).append(" | ").append(elapsed(total-prev)).append(" | ").append(elapsed(total)).append("\n");prev=total;}getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText(tr(R.string.laps),out));toast(tr(R.string.laps_copied));});copy.setTag("copy-laps");headings.addView(copy);body.addView(headings);
        for(int i=laps.length()-1;i>=0;i--){long total=laps.optLong(i),split=total-(i==0?0:laps.optLong(i-1));LinearLayout item=row();item.setMinimumHeight(dp(48));boolean best=laps.length()>1&&split==fastest,worst=laps.length()>1&&split==slowest&&!best;
            // Fastest and slowest are written out and coloured, not arrows whose direction readers have to guess.
            TextView lapLabel=text(best?tr(R.string.fastest_lap,String.valueOf(i+1)):worst?tr(R.string.slowest_lap,String.valueOf(i+1)):String.valueOf(i+1),13,best?MINT:worst?GOLD:TEXT);lapLabel.setTag(best?"lap-fastest":worst?"lap-slowest":null);lapLabel.setContentDescription(best?tr(R.string.fastest_lap,tr(R.string.lap_number,i+1)):worst?tr(R.string.slowest_lap,tr(R.string.lap_number,i+1)):tr(R.string.lap_number,i+1));
            weighted(item,lapLabel);TextView splitText=mono(lapTime(split,hours),15);weighted(item,splitText);TextView totalText=mono(lapTime(total,hours),15);totalText.setTextColor(MUTED);weighted(item,totalText);item.addView(new View(this),new LinearLayout.LayoutParams(dp(48),1));body.addView(item);}
    }
    void world(){JSONArray cities=store.list("cities");for(int i=0;i<cities.length();i++){String zone=cities.optString(i);LinearLayout c=card();LinearLayout r=row();TextView name=text(LocaleText.city(this,zone),22,MINT);r.addView(name,new LinearLayout.LayoutParams(0,-2,1));View remove=iconButton("close",tr(R.string.remove_city,LocaleText.city(this,zone)),false,GOLD,()->removeCity(zone));remove.setTag("remove-city");r.addView(remove);c.addView(r);TextView time=mono("",42),detail=text("",13,MUTED);c.addView(time);c.addView(detail);Runnable update=()->{ZonedDateTime now=ZonedDateTime.now(ZoneId.of(zone));time.setText(LocaleText.worldTime(this,now));int diff=now.getOffset().getTotalSeconds()-ZonedDateTime.now().getOffset().getTotalSeconds();String offset=diff==0?tr(R.string.same_local):tr(R.string.local_difference,String.format(LocaleText.locale(this),"%s%d:%02d",diff<0?"−":"+",Math.abs(diff)/3600,Math.abs(diff)/60%60));detail.setText(LocaleText.day(this,now)+" · "+offset);};ticks.add(update);update.run();}}
    void removeCity(String zone){
        JSONArray cities=store.list("cities");int index=-1;for(int j=0;j<cities.length();j++)if(cities.optString(j).equals(zone)){index=j;cities.remove(j);break;}if(index<0)return;final int at=index;store.save();render();
        offerUndo(tr(R.string.holiday_removed,LocaleText.city(this,zone)),()->{ClockStore s=new ClockStore(this);JSONArray now=s.list("cities");List<Object> items=new ArrayList<>();for(int j=0;j<now.length();j++){if(now.optString(j).equals(zone))return;items.add(now.opt(j));}items.add(Math.min(at,items.size()),zone);while(now.length()>0)now.remove(0);for(Object x:items)now.put(x);s.save();store=s;render();});
    }
    static class CityOption {final String zone,label;CityOption(String z,String l){zone=z;label=l;}@Override public String toString(){return label;}}
    void addCity(){
        LinearLayout c=column();EditText search=field(tr(R.string.search_city),"");c.addView(search);ListView list=new ListView(this);
        List<CityOption> zones=new ArrayList<>();Set<String> canonicalIds=new HashSet<>();for(String zone:ZoneId.getAvailableZoneIds()){String canonical=android.icu.util.TimeZone.getCanonicalID(zone);if(canonical!=null&&canonical.contains("/")&&!canonical.startsWith("Etc/")&&!canonical.startsWith("SystemV/")&&canonicalIds.add(canonical))zones.add(new CityOption(canonical,LocaleText.city(this,canonical)));}java.text.Collator collator=java.text.Collator.getInstance(LocaleText.locale(this));zones.sort((a,b)->collator.compare(a.label,b.label));
        ArrayAdapter<CityOption> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>(zones));list.setAdapter(adapter);c.addView(list,new LinearLayout.LayoutParams(-1,dp(370)));
        AlertDialog d=new AlertDialog.Builder(this).setTitle(tr(R.string.add_world)).setView(LocaleText.direction(c)).setNegativeButton(tr(R.string.cancel),null).create();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){adapter.clear();String query=s.toString().toLowerCase(LocaleText.locale(MainActivity.this));for(CityOption zone:zones)if((zone.label+" "+zone.zone).replace('_',' ').toLowerCase(LocaleText.locale(MainActivity.this)).contains(query))adapter.add(zone);}public void afterTextChanged(Editable e){}});
        list.setOnItemClickListener((p,v,pos,id)->{String zone=adapter.getItem(pos).zone;JSONArray cities=store.list("cities");for(int i=0;i<cities.length();i++)if(cities.optString(i).equals(zone)){toast(tr(R.string.already_added));return;}cities.put(zone);store.save();d.dismiss();render();});d.show();
    }
    void chooseTheme(){String[] labels={tr(R.string.follow_system),tr(R.string.light),tr(R.string.dark)};int selected=Arrays.asList(ThemeChoice.MODES).indexOf(ThemeChoice.get(this));new AlertDialog.Builder(this).setTitle(tr(R.string.appearance)).setSingleChoiceItems(labels,selected,(d,index)->{ThemeChoice.set(this,ThemeChoice.MODES[index]);d.dismiss();recreate();}).setNegativeButton(tr(R.string.cancel),null).show();}
    void settings(){startActivity(new Intent(this,SettingsActivity.class));}
    boolean ringVolumeKey;
    @Override public boolean onKeyUp(int code,KeyEvent e){if(ringVolumeKey&&(code==KeyEvent.KEYCODE_VOLUME_UP||code==KeyEvent.KEYCODE_VOLUME_DOWN)){ringVolumeKey=false;return true;}return super.onKeyUp(code,e);}
    @Override public boolean onKeyDown(int code,KeyEvent event){
        if(code==KeyEvent.KEYCODE_VOLUME_UP||code==KeyEvent.KEYCODE_VOLUME_DOWN){if(ringVolumeKey)return true;
            ClockSettings settings=new ClockSettings(this);String action=settings.text("volume_buttons","volume");ClockStore current=new ClockStore(this);
            if(current.ringing()&&!action.equals("volume")){ringVolumeKey=true;if(event.getRepeatCount()==0){for(String kind:List.of("alarms","timers"))for(JSONObject a:current.objects(kind))if(a.optBoolean("ringing")){if(action.equals("snooze")&&kind.equals("alarms")&&settings.canSnooze(a))current.snooze(a,settings.snoozeMinutes(a));else if(!(kind.equals("alarms")&&Challenge.required(a)))current.dismiss(kind,a);}current.save();current.refreshSound();render();}return true;}
        }
        return super.onKeyDown(code,event);
    }
}
