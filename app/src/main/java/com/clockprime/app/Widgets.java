package com.clockprime.app;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;
import org.json.*;
import java.time.*;
import java.util.*;

/**
 * Home-screen widgets: clock, next alarm, alarm list and world clock. Every store save redraws them, and a
 * non-waking minute tick keeps countdowns and day labels current while any widget exists. Launchers inflate
 * layouts in the system language, so all text is set here in the app's language.
 */
final class Widgets {
    static final String TICK="WIDGET_TICK",TOGGLE="WIDGET_TOGGLE",GROUP="WIDGET_GROUP",QUICK_TIMER="WIDGET_QUICK_TIMER";
    static final int[] PRESETS={1,5,10};
    public static class Provider extends AppWidgetProvider{
        @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){update(c,new ClockStore(c));}
        @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){update(c,new ClockStore(c));}
        @Override public void onDisabled(Context c){update(c,new ClockStore(c));}
    }
    public static class Clock extends Provider{}
    public static class ClockOnly extends Provider{}
    public static class Next extends Provider{}
    public static class Alarms extends Provider{}
    public static class World extends Provider{}
    public static class Analog extends Provider{}
    public static class Stack extends Provider{}
    public static class Dual extends Provider{}
    public static class Timer extends Provider{}
    public static class Stopwatch extends Provider{}
    public static class Groups extends Provider{}
    static final List<Class<?>> PROVIDERS=List.of(Clock.class,ClockOnly.class,Next.class,Alarms.class,World.class,Analog.class,Stack.class,Dual.class,Timer.class,Stopwatch.class,Groups.class);

    static void update(Context c,ClockStore store){
        AppWidgetManager m=AppWidgetManager.getInstance(c);if(m==null)return;long now=System.currentTimeMillis();boolean any=false;
        try{
            for(Class<?> k:PROVIDERS){int[] ids=m.getAppWidgetIds(new ComponentName(c,k));if(ids.length==0)continue;any=true;
                m.updateAppWidget(ids,build(c,k,store,now));}
        }catch(RuntimeException e){DebugLog.error(c,"widgets",e);}
        tick(c,any,now);
    }
    static RemoteViews build(Context c,Class<?> k,ClockStore store,long now){
        if(k==Clock.class)return clock(c,store,now,true);if(k==ClockOnly.class)return clock(c,store,now,false);if(k==Next.class)return next(c,store,now);
        if(k==Alarms.class)return alarms(c,store);if(k==World.class)return world(c,store);if(k==Analog.class)return analog(c);if(k==Stack.class)return stack(c);
        if(k==Dual.class)return dual(c,store);if(k==Timer.class)return timer(c,store);if(k==Stopwatch.class)return stopwatch(c,store);return groups(c,store,now);
    }
    /** RTC (not wakeup) at the next minute: asleep phones are not woken just to redraw a countdown. */
    static void tick(Context c,boolean any,long now){
        AlarmManager am=c.getSystemService(AlarmManager.class);PendingIntent pi=PendingIntent.getBroadcast(c,0x5717,new Intent(c,ClockReceiver.class).setAction(TICK),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        if(!any){am.cancel(pi);return;}
        long at=now-now%60000+60000+500;
        if(am.canScheduleExactAlarms())am.setExact(AlarmManager.RTC,at,pi);else am.set(AlarmManager.RTC,at,pi);
    }
    static PendingIntent open(Context c,String tab,String add){
        Intent i=new Intent(c,MainActivity.class).putExtra("tab",tab).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);if(add!=null)i.putExtra("add",add);
        return PendingIntent.getActivity(c,ClockStore.requestCode("widget",tab.hashCode(),add==null?"open":add),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    static String pattern(Context c,String skeleton){return DateFormat.getBestDateTimePattern(LocaleText.formatLocale(c),skeleton);}
    /** Hour format follows Settings; with "system" the launcher's TextClock picks 12/24 itself. */
    static void timeFormats(Context c,RemoteViews v,int time,int ampm,boolean marker){
        String mode=new ClockSettings(c).hourFormat(),h24=pattern(c,"Hm"),h12=pattern(c,"hm");
        if(!marker)h12=h12.replace("a","").trim();
        String f12=mode.equals("24")?h24:h12,f24=mode.equals("12")?h12:h24;
        v.setCharSequence(time,"setFormat12Hour",f12);v.setCharSequence(time,"setFormat24Hour",f24);
        if(ampm!=0){v.setCharSequence(ampm,"setFormat12Hour",mode.equals("24")?"":"a");v.setCharSequence(ampm,"setFormat24Hour",mode.equals("12")?"a":"");}
    }
    static String time(Context c,long at){return Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(LocaleText.formatter(c,LocaleText.use24Hour(c)?"Hm":"hm"));}
    /** Today, Tomorrow, weekday within a week, else the date. */
    static String day(Context c,long at,long now){
        LocalDate d=Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate(),today=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate();
        if(d.equals(today))return c.getString(R.string.today);if(d.equals(today.plusDays(1)))return c.getString(R.string.tomorrow);
        return d.format(LocaleText.formatter(c,d.isBefore(today.plusDays(7))?"EEEE":"EEEMMMd"));
    }
    static String relative(Context c,long at,long now){
        long minutes=Math.max(1,(at-now+59999)/60000);
        if(minutes<60)return c.getString(R.string.widget_in_minutes,(int)minutes);
        if(minutes<1440)return c.getString(R.string.widget_in_hours,(int)(minutes/60),(int)(minutes%60));
        int days=(int)(minutes/1440);return c.getResources().getQuantityString(R.plurals.widget_in_days,days,days);
    }
    static String label(Context c,JSONObject a){String name=a.optString("name","").trim(),group=a.optString("group");if(name.isEmpty())name=c.getString(R.string.alarm);return group.isEmpty()?name:name+" · "+group;}

    /**
     * Launchers pick the largest variant that fits the widget (at most 16), so each variant sizes its
     * digits to the space left: height minus the fixed rows, and width for about {@code ems} characters.
     */
    static final float[] WIDTHS={110,180,250,320},HEIGHTS={60,90,120,160};
    interface Variant{RemoteViews build(float digits,float w,float h);}
    static RemoteViews sized(float reserved,float lines,float ems,Variant variant){
        Map<SizeF,RemoteViews> sizes=new HashMap<>();
        for(float w:WIDTHS)for(float h:HEIGHTS)sizes.put(new SizeF(w,h),variant.build(fit(w,h,reserved,lines,ems),w,h));
        return new RemoteViews(sizes);
    }
    static float fit(float w,float h,float reserved,float lines,float ems){return Math.max(16,Math.min(96,Math.min((h-reserved)/(1.25f*lines),w/ems)));}
    /** Without the alarm row there is room for larger digits. */
    static RemoteViews clock(Context c,ClockStore store,long now,boolean alarm){
        return sized(alarm?78:54,1,3.6f,(digits,w,h)->clock(c,store,now,digits,alarm));
    }
    static RemoteViews clock(Context c,ClockStore store,long now,float digits,boolean alarm){
        RemoteViews v=new RemoteViews(c.getPackageName(),alarm?R.layout.widget_clock:R.layout.widget_clock_only);v.setTextViewTextSize(R.id.clock_time,android.util.TypedValue.COMPLEX_UNIT_SP,digits);v.setTextViewTextSize(R.id.clock_ampm,android.util.TypedValue.COMPLEX_UNIT_SP,Math.max(11,digits*.3f));
        timeFormats(c,v,R.id.clock_time,R.id.clock_ampm,false);
        v.setTextViewText(R.id.clock_date,LocalDate.now().format(LocaleText.formatter(c,"EEEEdMMMM")));
        JSONObject a=alarm?UpcomingAlarm.earliest(store,now):null;
        if(alarm)v.setViewVisibility(R.id.clock_next,a==null?View.GONE:View.VISIBLE);
        if(a!=null)v.setTextViewText(R.id.clock_next_text,day(c,a.optLong("next"),now)+" "+time(c,a.optLong("next"))+" · "+label(c,a));
        v.setOnClickPendingIntent(android.R.id.background,open(c,"Alarms",null));
        return v;
    }

    /** Compact when short: time and day only; from 160dp tall it adds the label and Skip / Turn off. */
    static RemoteViews next(Context c,ClockStore store,long now){
        return sized(0,1,1,(digits,w,h)->{boolean full=h>=160;RemoteViews v=next(c,store,now,full);v.setTextViewTextSize(R.id.next_time,android.util.TypedValue.COMPLEX_UNIT_SP,fit(w-90,h,full?120:52,1,2.6f));return v;});
    }
    static RemoteViews next(Context c,ClockStore store,long now,boolean full){
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_next);JSONObject a=UpcomingAlarm.earliest(store,now);
        List<String> problems=AlarmHealth.problems(c);
        v.setTextViewText(R.id.next_title,problems.isEmpty()?c.getString(R.string.widget_next_name):"⚠ "+c.getString(AlarmHealth.title(problems.get(0))));
        v.setOnClickPendingIntent(android.R.id.background,open(c,"Alarms",null));
        v.setViewVisibility(R.id.next_body,a==null?View.GONE:View.VISIBLE);v.setViewVisibility(R.id.next_in,a==null?View.GONE:View.VISIBLE);
        v.setViewVisibility(R.id.next_actions,a!=null&&full?View.VISIBLE:View.GONE);v.setViewVisibility(R.id.next_empty,a==null?View.VISIBLE:View.GONE);
        if(a==null){v.setTextViewText(R.id.next_add,c.getString(R.string.add_alarm));v.setViewVisibility(R.id.next_add,full?View.VISIBLE:View.GONE);v.setOnClickPendingIntent(R.id.next_add,open(c,"Alarms","alarm"));
            v.setTextViewText(R.id.next_empty_text,c.getString(R.string.widget_no_alarms));return v;}
        long at=a.optLong("next");
        v.setTextViewText(R.id.next_time,time(c,at));v.setTextViewText(R.id.next_day,day(c,at,now));v.setTextViewText(R.id.next_in,relative(c,at,now));
        v.setTextViewText(R.id.next_label,label(c,a));v.setViewVisibility(R.id.next_label,full?View.VISIBLE:View.GONE);
        v.setTextViewText(R.id.next_skip,c.getString(R.string.skip_once));v.setOnClickPendingIntent(R.id.next_skip,UpcomingAlarm.action(c,a,"SKIP_UPCOMING"));
        v.setTextViewText(R.id.next_off,c.getString(R.string.turn_off));v.setOnClickPendingIntent(R.id.next_off,UpcomingAlarm.action(c,a,"OFF_UPCOMING"));
        return v;
    }

    static RemoteViews list(Context c,int icon,String title,String summary,PendingIntent header,PendingIntent add,String addLabel,String empty){
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_list);
        v.setImageViewResource(R.id.list_icon,icon);v.setTextViewText(R.id.list_title,title);v.setTextViewText(R.id.list_summary,summary);v.setViewVisibility(R.id.list_summary,View.VISIBLE);v.setViewVisibility(R.id.list_add,View.VISIBLE);
        v.setOnClickPendingIntent(R.id.list_header,header);v.setOnClickPendingIntent(R.id.list_add,add);v.setContentDescription(R.id.list_add,addLabel);
        v.setTextViewText(R.id.list_empty,empty);v.setEmptyView(R.id.list_items,R.id.list_empty);
        return v;
    }
    /** Every alarm by time of day, each with its own switch (a fill-in on one mutable template). */
    static RemoteViews alarms(Context c,ClockStore store){
        long now=System.currentTimeMillis();JSONObject upcoming=UpcomingAlarm.earliest(store,now);
        RemoteViews v=list(c,R.drawable.ic_widget_alarm,c.getString(R.string.alarms),upcoming==null?c.getString(R.string.schedule_summary):c.getString(R.string.next_alarm,day(c,upcoming.optLong("next"),now)+" "+time(c,upcoming.optLong("next"))),
                open(c,"Alarms",null),open(c,"Alarms","alarm"),c.getString(R.string.add_alarm),c.getString(R.string.empty_description));
        List<JSONObject> alarms=store.objects("alarms");alarms.sort(Comparator.<JSONObject>comparingInt(a->a.optInt("hour")*60+a.optInt("minute")).thenComparingInt(a->a.optInt("id")));
        RemoteViews.RemoteCollectionItems.Builder items=new RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1);
        for(JSONObject a:alarms){
            RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_alarm_row);boolean on=a.optBoolean("enabled");String time=LocaleText.alarmTime(c,a.optInt("hour"),a.optInt("minute"));
            row.setTextViewText(R.id.row_time,time);row.setTextViewText(R.id.row_detail,label(c,a)+" · "+Schedule.describe(c,a));
            row.setFloat(R.id.row_time,"setAlpha",on?1f:.55f);row.setFloat(R.id.row_detail,"setAlpha",on?1f:.55f);
            row.setCompoundButtonChecked(R.id.row_switch,on);row.setContentDescription(R.id.row_switch,c.getString(R.string.enable_alarm,label(c,a),time));
            row.setOnCheckedChangeResponse(R.id.row_switch,RemoteViews.RemoteResponse.fromFillInIntent(new Intent().putExtra("kind","alarms").putExtra("id",a.optInt("id"))));
            items.addItem(a.optInt("id"),row);
        }
        v.setRemoteAdapter(R.id.list_items,items.build());
        v.setPendingIntentTemplate(R.id.list_items,PendingIntent.getBroadcast(c,0x5718,new Intent(c,ClockReceiver.class).setAction(TOGGLE),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE));
        return v;
    }
    static RemoteViews world(Context c,ClockStore store){
        ZoneId local=ZoneId.systemDefault();
        RemoteViews v=list(c,R.drawable.ic_widget_world,c.getString(R.string.widget_world_name),"",open(c,"World",null),open(c,"World","city"),c.getString(R.string.add_city),c.getString(R.string.add_world));
        JSONArray cities=store.list("cities");RemoteViews.RemoteCollectionItems.Builder items=new RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1);
        for(int i=0;i<cities.length();i++){
            String zone=cities.optString(i);ZonedDateTime there;try{there=ZonedDateTime.now(ZoneId.of(zone));}catch(DateTimeException e){continue;}
            RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_world_row);
            int diff=there.getOffset().getTotalSeconds()-ZonedDateTime.now(local).getOffset().getTotalSeconds();
            // Narrow row: day plus a signed offset only when it differs from local time.
            String offset=diff==0?"":" · "+String.format(LocaleText.locale(c),"%s%d:%02d",diff<0?"−":"+",Math.abs(diff)/3600,Math.abs(diff)/60%60);
            row.setTextViewText(R.id.row_city,LocaleText.city(c,zone));row.setTextViewText(R.id.row_detail,LocaleText.day(c,there)+offset);
            row.setString(R.id.row_clock,"setTimeZone",zone);timeFormats(c,row,R.id.row_clock,0,true);
            row.setOnClickFillInIntent(R.id.row_root,new Intent());
            items.addItem(i,row);
        }
        v.setRemoteAdapter(R.id.list_items,items.build());
        v.setViewVisibility(R.id.list_summary,View.GONE);
        v.setPendingIntentTemplate(R.id.list_items,PendingIntent.getActivity(c,0x5719,new Intent(c,MainActivity.class).putExtra("tab","World").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE));
        return v;
    }

    static String shortDay(Context c){return LocaleText.day(c,ZonedDateTime.now());}
    static RemoteViews analog(Context c){
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_analog);v.setTextViewText(R.id.analog_date,shortDay(c));
        v.setOnClickPendingIntent(android.R.id.background,open(c,"Alarms",null));return v;
    }
    /** Hours over minutes; sized like the clock widget. */
    static RemoteViews stack(Context c){return sized(48,2,1.4f,(digits,w,h)->stack(c,digits));}
    static RemoteViews stack(Context c,float digits){
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_stack);String mode=new ClockSettings(c).hourFormat();
        v.setCharSequence(R.id.stack_hours,"setFormat12Hour",mode.equals("24")?"HH":"hh");v.setCharSequence(R.id.stack_hours,"setFormat24Hour",mode.equals("12")?"hh":"HH");
        v.setCharSequence(R.id.stack_minutes,"setFormat12Hour","mm");v.setCharSequence(R.id.stack_minutes,"setFormat24Hour","mm");
        for(int id:new int[]{R.id.stack_hours,R.id.stack_minutes})v.setTextViewTextSize(id,android.util.TypedValue.COMPLEX_UNIT_SP,digits);
        v.setTextViewText(R.id.stack_date,shortDay(c));v.setOnClickPendingIntent(android.R.id.background,open(c,"Alarms",null));return v;
    }
    /** Local time beside the first world-clock city in another zone (or the first city); one row tall shows times only. */
    static RemoteViews dual(Context c,ClockStore store){
        return new RemoteViews(Map.of(new SizeF(180,40),dual(c,store,false),new SizeF(180,96),dual(c,store,true)));
    }
    static RemoteViews dual(Context c,ClockStore store,boolean full){
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_dual);
        for(int id:new int[]{R.id.dual_local_day,R.id.dual_day})v.setViewVisibility(id,full?View.VISIBLE:View.GONE);v.setViewVisibility(R.id.dual_time,View.VISIBLE);
        for(int id:new int[]{R.id.dual_local_time,R.id.dual_time})v.setTextViewTextSize(id,android.util.TypedValue.COMPLEX_UNIT_SP,full?32:26);ZoneId local=ZoneId.systemDefault();ZonedDateTime here=ZonedDateTime.now(local);
        v.setTextViewText(R.id.dual_local_city,LocaleText.city(c,local.getId()));v.setTextViewText(R.id.dual_local_day,LocaleText.day(c,here));timeFormats(c,v,R.id.dual_local_time,0,true);
        String other=null;JSONArray cities=store.list("cities");
        for(int i=0;i<cities.length()&&other==null;i++)try{if(!ZoneId.of(cities.optString(i)).getRules().getOffset(Instant.now()).equals(here.getOffset())||i==cities.length()-1)other=cities.optString(i);}catch(DateTimeException ignored){}
        if(other==null){v.setTextViewText(R.id.dual_city,c.getString(R.string.add_city));v.setViewVisibility(R.id.dual_time,View.GONE);v.setTextViewText(R.id.dual_day,"");v.setOnClickPendingIntent(android.R.id.background,open(c,"World","city"));return v;}
        ZonedDateTime there=ZonedDateTime.now(ZoneId.of(other));int diff=there.getOffset().getTotalSeconds()-here.getOffset().getTotalSeconds();
        v.setTextViewText(R.id.dual_city,LocaleText.city(c,other));v.setString(R.id.dual_time,"setTimeZone",other);timeFormats(c,v,R.id.dual_time,0,true);
        v.setTextViewText(R.id.dual_day,LocaleText.day(c,there)+(diff==0?"":String.format(LocaleText.locale(c)," · %s%d:%02d",diff<0?"−":"+",Math.abs(diff)/3600,Math.abs(diff)/60%60)));
        v.setOnClickPendingIntent(android.R.id.background,open(c,"World",null));return v;
    }
    static void chip(RemoteViews v,int id,String text,PendingIntent action){v.setViewVisibility(id,View.VISIBLE);v.setTextViewText(id,text);v.setOnClickPendingIntent(id,action);}
    /** Live countdown of the soonest running or paused timer; a finished one offers Dismiss; otherwise quick-start presets. */
    /** Launchers re-apply updates onto the existing views: anything one state hides must be shown again by the others. */
    static void counterDefaults(RemoteViews v){
        for(int id:new int[]{R.id.counter_time,R.id.counter_actions,R.id.counter_primary,R.id.counter_secondary})v.setViewVisibility(id,View.VISIBLE);
        v.setViewVisibility(R.id.counter_text,View.GONE);v.setViewVisibility(R.id.counter_presets,View.GONE);v.setChronometerCountDown(R.id.counter_time,false);
    }
    static RemoteViews timer(Context c,ClockStore store){return sized(80,1,3.8f,(digits,w,h)->counterSize(timerViews(c,store),digits));}
    static RemoteViews stopwatch(Context c,ClockStore store){return sized(80,1,3.8f,(digits,w,h)->counterSize(stopwatchViews(c,store),digits));}
    static RemoteViews counterSize(RemoteViews v,float digits){v.setTextViewTextSize(R.id.counter_time,android.util.TypedValue.COMPLEX_UNIT_SP,digits);return v;}
    static RemoteViews timerViews(Context c,ClockStore store){
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_counter);counterDefaults(v);v.setImageViewResource(R.id.counter_icon,R.drawable.ic_widget_timer);v.setOnClickPendingIntent(android.R.id.background,open(c,"Timers",null));
        JSONObject ringing=store.objects("timers").stream().filter(t->t.optBoolean("ringing")).findFirst().orElse(null);
        List<JSONObject> active=store.objects("timers").stream().filter(ClockStore::timerShown).sorted(Comparator.comparingLong(ClockStore::remaining)).collect(java.util.stream.Collectors.toList());
        JSONObject t=ringing!=null?ringing:active.isEmpty()?null:active.get(0);
        int others=active.size()-(ringing==null&&t!=null?1:0);
        v.setViewVisibility(R.id.counter_presets,t==null?View.VISIBLE:View.GONE);v.setViewVisibility(R.id.counter_actions,t==null?View.GONE:View.VISIBLE);
        if(t==null){
            v.setTextViewText(R.id.counter_title,c.getString(R.string.timer));v.setTextViewText(R.id.counter_note,"");v.setChronometer(R.id.counter_time,android.os.SystemClock.elapsedRealtime(),null,false);v.setViewVisibility(R.id.counter_time,View.GONE);
            int[] ids={R.id.preset_1,R.id.preset_2,R.id.preset_3};
            for(int i=0;i<PRESETS.length;i++)chip(v,ids[i],c.getString(R.string.widget_minutes_short,PRESETS[i]),quickTimer(c,PRESETS[i]));
            v.setViewVisibility(R.id.counter_text,View.VISIBLE);v.setTextViewText(R.id.counter_text,c.getString(R.string.widget_quick_start));v.setTextViewTextSize(R.id.counter_text,android.util.TypedValue.COMPLEX_UNIT_SP,18);v.setColorStateList(R.id.counter_text,"setTextColor",R.color.w_muted);
            return v;
        }
        String name=t.optString("name","").trim();v.setTextViewText(R.id.counter_title,(name.isEmpty()?c.getString(R.string.timer):name)+(others>0?" +"+others:""));int id=t.optInt("id");
        if(t.optBoolean("ringing")){
            v.setViewVisibility(R.id.counter_time,View.GONE);v.setViewVisibility(R.id.counter_text,View.VISIBLE);v.setTextViewText(R.id.counter_text,c.getString(R.string.finished));v.setTextViewTextSize(R.id.counter_text,android.util.TypedValue.COMPLEX_UNIT_SP,28);v.setColorStateList(R.id.counter_text,"setTextColor",R.color.w_text);v.setTextViewText(R.id.counter_note,"");
            chip(v,R.id.counter_primary,c.getString(R.string.dismiss),store.pending("timers",id,"DISMISS"));v.setViewVisibility(R.id.counter_secondary,View.GONE);return v;
        }
        boolean running=t.optBoolean("running");long left=ClockStore.remaining(t);
        v.setChronometer(R.id.counter_time,android.os.SystemClock.elapsedRealtime()+left,null,running);v.setChronometerCountDown(R.id.counter_time,true);
        v.setTextViewText(R.id.counter_note,running?"":c.getString(R.string.widget_paused));
        chip(v,R.id.counter_primary,c.getString(running?R.string.pause:R.string.resume),store.pending("timers",id,running?"PAUSE":"RESUME"));
        chip(v,R.id.counter_secondary,c.getString(R.string.cancel),store.pending("timers",id,"CANCEL"));
        return v;
    }
    static PendingIntent quickTimer(Context c,int minutes){
        return PendingIntent.getBroadcast(c,ClockStore.requestCode("widget-timer",minutes,QUICK_TIMER),new Intent(c,ClockReceiver.class).setAction(QUICK_TIMER).putExtra("minutes",minutes).addFlags(Intent.FLAG_RECEIVER_FOREGROUND),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    /** Starts an idle timer of that length, reusing one the widget made before instead of piling up copies. */
    static JSONObject startQuickTimer(Context c,ClockStore s,int minutes){
        String name=c.getResources().getQuantityString(R.plurals.minutes_value,minutes,minutes);long duration=minutes*60000L;
        JSONObject t=s.objects("timers").stream().filter(x->x.optLong("duration")==duration&&name.equals(x.optString("name"))&&!ClockStore.timerShown(x)&&!x.optBoolean("ringing")).findFirst().orElse(null);
        if(t==null){t=new JSONObject();ClockStore.put(t,"id",s.id());ClockStore.put(t,"name",name);ClockStore.put(t,"duration",duration);s.list("timers").put(t);}
        s.timerStart(t,duration);return t;
    }
    static RemoteViews stopwatchViews(Context c,ClockStore store){
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_stopwatch);counterDefaults(v);v.setImageViewResource(R.id.counter_icon,R.drawable.ic_widget_stopwatch);v.setOnClickPendingIntent(android.R.id.background,open(c,"Stopwatch",null));
        boolean running=store.data.optBoolean("swRunning");long elapsed=store.stopwatchTime();JSONArray laps=store.data.optJSONArray("laps");int lapCount=laps==null?0:laps.length();
        v.setTextViewText(R.id.counter_title,c.getString(R.string.stopwatch));
        v.setTextViewText(R.id.counter_note,lapCount>0?c.getResources().getQuantityString(R.plurals.widget_laps,lapCount,lapCount):running||elapsed==0?"":c.getString(R.string.widget_paused));
        v.setChronometer(R.id.counter_time,android.os.SystemClock.elapsedRealtime()-elapsed,null,running);
        chip(v,R.id.counter_primary,c.getString(running?R.string.pause:elapsed>0?R.string.resume:R.string.start),store.stopwatchAction(running?"SW_PAUSE":"SW_RESUME"));
        if(running)chip(v,R.id.counter_secondary,c.getString(R.string.lap),store.stopwatchAction("SW_LAP"));
        else if(elapsed>0)chip(v,R.id.counter_secondary,c.getString(R.string.reset),store.stopwatchAction("SW_RESET"));
        else v.setViewVisibility(R.id.counter_secondary,View.GONE);
        return v;
    }
    /** One row per group: how many alarms are on, its next alarm, and a switch for the whole group. */
    static RemoteViews groups(Context c,ClockStore store,long now){
        RemoteViews v=list(c,R.drawable.ic_widget_group,c.getString(R.string.widget_groups_name),"",open(c,"Alarms",null),open(c,"Alarms",null),c.getString(R.string.alarms),c.getString(R.string.widget_no_groups));
        v.setViewVisibility(R.id.list_summary,View.GONE);v.setViewVisibility(R.id.list_add,View.GONE);
        JSONArray groups=store.list("groups");RemoteViews.RemoteCollectionItems.Builder items=new RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1);
        for(int i=0;i<groups.length();i++){
            String group=groups.optString(i);List<JSONObject> members=store.objects("alarms").stream().filter(a->a.optString("group").equals(group)).collect(java.util.stream.Collectors.toList());
            int on=(int)members.stream().filter(a->a.optBoolean("enabled")).count();JSONObject next=members.stream().filter(a->a.optBoolean("enabled")&&a.optLong("next")>now).min(Comparator.comparingLong(a->a.optLong("next"))).orElse(null);
            RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_group_row);
            row.setTextViewText(R.id.row_time,group);
            row.setTextViewText(R.id.row_detail,c.getString(R.string.group_count,c.getResources().getQuantityString(R.plurals.alarm_count,members.size(),members.size()),on)+(next==null?"":" · "+day(c,next.optLong("next"),now)+" "+time(c,next.optLong("next"))));
            row.setCompoundButtonChecked(R.id.row_switch,on>0);row.setContentDescription(R.id.row_switch,group);
            row.setOnCheckedChangeResponse(R.id.row_switch,RemoteViews.RemoteResponse.fromFillInIntent(new Intent().putExtra("group",group)));
            items.addItem(i,row);
        }
        v.setRemoteAdapter(R.id.list_items,items.build());
        v.setPendingIntentTemplate(R.id.list_items,PendingIntent.getBroadcast(c,0x571A,new Intent(c,ClockReceiver.class).setAction(GROUP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE));
        return v;
    }
    /** Group switch: on turns every alarm in the group on, off turns them all off. */
    static void toggleGroup(ClockStore s,String group,boolean on){for(JSONObject a:s.objects("alarms"))if(a.optString("group").equals(group)&&a.optBoolean("enabled")!=on)s.enable(a,on);}
}
