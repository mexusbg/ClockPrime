package com.clockprime.app;

import android.appwidget.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Widget providers, their content and actions; also renders each widget to PNG (files/widget-shots) for visual review. */
final class WidgetChecks {
    static View render(Context c,RemoteViews v,int wDp,int hDp){
        // Collection adapters only apply inside a widget host view, as on a launcher.
        AppWidgetHostView host=new AppWidgetHostView(c);host.updateAppWidgetSize(new android.os.Bundle(),List.of(new android.util.SizeF(wDp,hDp)));host.updateAppWidget(v);
        float d=c.getResources().getDisplayMetrics().density;int w=(int)(wDp*d),h=(int)(hDp*d);
        for(int i=0;i<2;i++){host.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));host.layout(0,0,w,h);}
        return host;
    }
    /** A later update on the same host view, as launchers do: views keep state the new update does not set. */
    static View reapply(View host,RemoteViews v){((AppWidgetHostView)host).updateAppWidget(v);int w=host.getWidth(),h=host.getHeight();
        for(int i=0;i<2;i++){host.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));host.layout(0,0,w,h);}return host;}
    static void shot(View v,File f,boolean dark){
        Bitmap b=Bitmap.createBitmap(v.getWidth()+48,v.getHeight()+48,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);canvas.drawColor(dark?Color.rgb(36,40,48):Color.rgb(196,206,220));canvas.translate(24,24);v.draw(canvas);
        try(FileOutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,out);}catch(IOException e){throw new AssertionError(e);}
    }
    static String text(View root,int id){View v=root.findViewById(id);return v instanceof TextView tv?tv.getText().toString():null;}
    static Context themed(Context c,boolean dark){Configuration config=new Configuration(c.getResources().getConfiguration());config.uiMode=(config.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|(dark?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);return c.createConfigurationContext(config);}

    static void run(SmokeTests t){
        List<AppWidgetProviderInfo> installed=AppWidgetManager.getInstance(t.context).getInstalledProvidersForPackage(t.context.getPackageName(),null);
        t.check(installed.size()==Widgets.PROVIDERS.size()&&installed.size()==11,"All 11 widgets are offered ("+installed.size()+")");
        t.check(installed.stream().allMatch(p->p.previewLayout!=0&&p.loadDescription(t.context)!=null&&(p.resizeMode&AppWidgetProviderInfo.RESIZE_BOTH)==AppWidgetProviderInfo.RESIZE_BOTH),"Every widget has a live preview, a description and resizes both ways");
        final int[] ids={0,0};final List<Integer> timers=new ArrayList<>();final JSONObject stopwatchKeep=new JSONObject();
        try{
            t.main(()->{
                ClockStore s=new ClockStore(t.context);long now=System.currentTimeMillis();
                JSONObject soon=t.testAlarm(s,"","widget wake");ids[0]=soon.optInt("id");ClockStore.put(soon,"days",0);ClockStore.put(soon,"enabled",true);ClockStore.put(soon,"next",now+90*60000L);
                JSONObject off=t.testAlarm(s,"QA widget","widget off");ids[1]=off.optInt("id");ClockStore.put(off,"enabled",false);ClockStore.put(off,"next",0);
                s.save(false);
                ClockStore store=new ClockStore(t.context);JSONObject first=UpcomingAlarm.earliest(store,now);long at=first.optLong("next");
                View clock=render(t.context,Widgets.clock(t.context,store,now,true),300,150),only=render(t.context,Widgets.clock(t.context,store,now,false),300,120);
                t.check(clock.findViewById(R.id.clock_next).getVisibility()==View.VISIBLE&&text(clock,R.id.clock_next_text).contains(Widgets.time(t.context,at)),"Clock & alarm widget shows the next alarm");
                t.check(only.findViewById(R.id.clock_next)==null&&only.findViewById(R.id.clock_time)!=null&&text(only,R.id.clock_date).length()>0,"Clock widget shows only time and date");
                View full=render(t.context,Widgets.next(t.context,store,now,true),220,170),compact=render(t.context,Widgets.next(t.context,store,now,false),220,80);
                t.check(Widgets.time(t.context,at).equals(text(full,R.id.next_time))&&text(full,R.id.next_label).equals(Widgets.label(t.context,first)),"Next alarm widget shows the time and label");
                t.check(full.findViewById(R.id.next_actions).getVisibility()==View.VISIBLE&&compact.findViewById(R.id.next_actions).getVisibility()==View.GONE,"Skip and Turn off appear only when the widget is tall enough");
                t.check(t.context.getString(R.string.widget_in_hours,1,30).equals(Widgets.relative(t.context,now+90*60000L,now))&&t.context.getString(R.string.widget_in_minutes,1).equals(Widgets.relative(t.context,now+5000,now)),"Countdown reads in hours and minutes, never zero");
                t.check(t.context.getString(R.string.tomorrow).equals(Widgets.day(t.context,now+86400000L,now)),"Next day is labelled Tomorrow");
                ClockStore quiet=new ClockStore(t.context);for(JSONObject a:quiet.objects("alarms"))ClockStore.put(a,"enabled",false);
                View empty=render(t.context,Widgets.next(t.context,quiet,now,true),220,170);
                t.check(empty.findViewById(R.id.next_empty).getVisibility()==View.VISIBLE&&empty.findViewById(R.id.next_body).getVisibility()==View.GONE,"Next alarm widget has an empty state with Add alarm");
                ListView list=render(t.context,Widgets.alarms(t.context,store),300,320).findViewById(R.id.list_items);
                t.check(list.getAdapter()!=null&&list.getAdapter().getCount()==store.objects("alarms").size(),"Alarms widget lists every alarm ("+(list.getAdapter()==null?"no adapter":list.getAdapter().getClass().getSimpleName()+" "+list.getAdapter().getCount())+" vs "+store.objects("alarms").size()+")");
                ListView cities=render(t.context,Widgets.world(t.context,store),300,200).findViewById(R.id.list_items);
                t.check(cities.getAdapter()!=null&&cities.getAdapter().getCount()==store.list("cities").length(),"World clock widget lists every city ("+(cities.getAdapter()==null?"no adapter":cities.getAdapter().getCount())+")");
                // Widget switch: the template fill-in carries the checked state.
                new ClockReceiver().onReceive(t.context,new Intent(Widgets.TOGGLE).putExtra("kind","alarms").putExtra("id",ids[0]).putExtra(RemoteViews.EXTRA_CHECKED,false));
                t.check(!new ClockStore(t.context).find("alarms",ids[0]).optBoolean("enabled"),"Widget switch turns an alarm off");
                new ClockReceiver().onReceive(t.context,new Intent(Widgets.TOGGLE).putExtra("kind","alarms").putExtra("id",ids[1]).putExtra(RemoteViews.EXTRA_CHECKED,true));
                JSONObject on=new ClockStore(t.context).find("alarms",ids[1]);t.check(on.optBoolean("enabled")&&on.optLong("next")>System.currentTimeMillis(),"Widget switch turns an alarm on and schedules it");
                // Every provider builds and renders.
                boolean all=true;for(Class<?> k:Widgets.PROVIDERS)all&=render(t.context,Widgets.build(t.context,k,store,now),250,180)!=null;t.check(all,"All "+Widgets.PROVIDERS.size()+" widgets render");
                View analog=render(t.context,Widgets.analog(t.context),180,180);t.check(analog.findViewById(R.id.analog_clock) instanceof AnalogClock&&!text(analog,R.id.analog_date).isEmpty(),"Analog clock widget shows a dial and the day");
                View stacked=render(t.context,Widgets.stack(t.context,52),150,180);t.check(stacked.findViewById(R.id.stack_hours) instanceof TextClock&&stacked.findViewById(R.id.stack_minutes) instanceof TextClock,"Stacked clock widget shows hours above minutes");
                View dual=render(t.context,Widgets.dual(t.context,store),320,110);t.check(!text(dual,R.id.dual_local_city).isEmpty()&&!text(dual,R.id.dual_city).isEmpty(),"Dual clock widget shows local time beside a world city");
                // Group switch: off turns every alarm in the group off, on turns them all on.
                ClockStore g=new ClockStore(t.context);JSONArray groups=g.list("groups");boolean has=false;for(int i=0;i<groups.length();i++)has|="QA widget".equals(groups.optString(i));if(!has)groups.put("QA widget");g.save(false);
                new ClockReceiver().onReceive(t.context,new Intent(Widgets.GROUP).putExtra("group","QA widget").putExtra(RemoteViews.EXTRA_CHECKED,false));
                t.check(new ClockStore(t.context).objects("alarms").stream().filter(a->a.optString("group").equals("QA widget")).noneMatch(a->a.optBoolean("enabled")),"Group widget switch turns the whole group off");
                new ClockReceiver().onReceive(t.context,new Intent(Widgets.GROUP).putExtra("group","QA widget").putExtra(RemoteViews.EXTRA_CHECKED,true));
                t.check(new ClockStore(t.context).objects("alarms").stream().filter(a->a.optString("group").equals("QA widget")).allMatch(a->a.optBoolean("enabled")),"Group widget switch turns the whole group on");
                ClockStore grouped=new ClockStore(t.context);ListView groupList=render(t.context,Widgets.groups(t.context,grouped,now),300,200).findViewById(R.id.list_items);
                t.check(groupList.getAdapter()!=null&&groupList.getAdapter().getCount()==grouped.list("groups").length(),"Alarm groups widget lists every group");
                // Timer: presets when idle, a live countdown after a quick start, and the same timer reused next time.
                ClockStore idle=new ClockStore(t.context);ClockStore.put(idle.data,"timers",new JSONArray());
                View idleTimer=render(t.context,Widgets.timer(t.context,idle),220,170);t.check(idleTimer.findViewById(R.id.counter_presets).getVisibility()==View.VISIBLE&&text(idleTimer,R.id.preset_2).equals(t.context.getString(R.string.widget_minutes_short,5)),"Idle timer widget offers 1, 5 and 10 minute quick starts");
                Set<Integer> before=new HashSet<>();for(JSONObject x:new ClockStore(t.context).objects("timers"))before.add(x.optInt("id"));
                new ClockReceiver().onReceive(t.context,new Intent(Widgets.QUICK_TIMER).putExtra("minutes",5));
                ClockStore started=new ClockStore(t.context);JSONObject quick=started.objects("timers").stream().filter(x->!before.contains(x.optInt("id"))).findFirst().orElse(null);
                if(quick!=null)timers.add(quick.optInt("id"));
                t.check(quick!=null&&quick.optBoolean("running")&&quick.optLong("duration")==300000,"Quick start runs a 5-minute timer");
                View running=reapply(idleTimer,Widgets.timer(t.context,started));
                t.check(running.findViewById(R.id.counter_time).getVisibility()==View.VISIBLE&&running.findViewById(R.id.counter_presets).getVisibility()==View.GONE&&t.context.getString(R.string.pause).equals(text(running,R.id.counter_primary)),"Running timer widget counts down with Pause and Cancel");
                t.check(running.findViewById(R.id.counter_text).getVisibility()==View.GONE,"Updating an idle timer widget to running hides the idle hint");
                new ClockReceiver().onReceive(t.context,new Intent("CANCEL").putExtra("kind","timers").putExtra("id",quick.optInt("id")));
                new ClockReceiver().onReceive(t.context,new Intent(Widgets.QUICK_TIMER).putExtra("minutes",5));
                t.check(new ClockStore(t.context).objects("timers").stream().filter(x->!before.contains(x.optInt("id"))).count()==1,"Quick start reuses its idle timer instead of adding copies");
                new ClockReceiver().onReceive(t.context,new Intent("CANCEL").putExtra("kind","timers").putExtra("id",quick.optInt("id")));
                // Stopwatch: Start, then Pause and Lap, then Resume and Reset.
                ClockStore sw=new ClockStore(t.context);for(String k:List.of("swRunning","swStart","swTotal","laps"))if(sw.data.has(k))ClockStore.put(stopwatchKeep,k,sw.data.opt(k));sw.stopwatchReset();sw.save(false);
                View zero=render(t.context,Widgets.stopwatch(t.context,new ClockStore(t.context)),220,170);t.check(t.context.getString(R.string.start).equals(text(zero,R.id.counter_primary))&&zero.findViewById(R.id.counter_secondary).getVisibility()==View.GONE,"Stopped stopwatch widget offers Start only");
                new ClockReceiver().onReceive(t.context,new Intent("SW_RESUME"));new ClockReceiver().onReceive(t.context,new Intent("SW_LAP"));
                View ticking=render(t.context,Widgets.stopwatch(t.context,new ClockStore(t.context)),220,170);
                t.check(t.context.getString(R.string.lap).equals(text(ticking,R.id.counter_secondary))&&text(ticking,R.id.counter_note).equals(t.context.getResources().getQuantityString(R.plurals.widget_laps,1,1)),"Running stopwatch widget shows Pause, Lap and the lap count");
                new ClockReceiver().onReceive(t.context,new Intent("SW_PAUSE"));
                View paused=render(t.context,Widgets.stopwatch(t.context,new ClockStore(t.context)),220,170);t.check(t.context.getString(R.string.reset).equals(text(paused,R.id.counter_secondary))&&t.context.getString(R.string.resume).equals(text(paused,R.id.counter_primary)),"Paused stopwatch widget shows Resume and Reset");
                // Visual review in both themes.
                File dir=new File(t.context.getExternalFilesDir(null),"widget-shots");dir.mkdirs();ClockStore shown=new ClockStore(t.context);
                for(boolean dark:new boolean[]{false,true}){Context c=themed(t.context,dark);String suffix=dark?"-dark.png":"-light.png";
                    shot(render(c,Widgets.clock(c,shown,now,48,true),300,150),new File(dir,"clock"+suffix),dark);shot(render(c,Widgets.clock(c,shown,now,56,false),300,120),new File(dir,"clock-only"+suffix),dark);
                    shot(render(c,Widgets.next(c,shown,now,true),220,170),new File(dir,"next"+suffix),dark);
                    shot(render(c,Widgets.next(c,shown,now,false),220,80),new File(dir,"next-compact"+suffix),dark);
                    shot(render(c,Widgets.next(c,quiet,now,true),220,170),new File(dir,"next-empty"+suffix),dark);
                    shot(render(c,Widgets.alarms(c,shown),300,320),new File(dir,"alarms"+suffix),dark);
                    shot(render(c,Widgets.world(c,shown),300,190),new File(dir,"world"+suffix),dark);
                    shot(render(c,Widgets.analog(c),180,200),new File(dir,"analog"+suffix),dark);
                    // Picker previews at each widget's minimum size, where clipping shows first; and the live widget at that size.
                    float density=c.getResources().getDisplayMetrics().density;
                    for(AppWidgetProviderInfo p:installed){String name=p.provider.getClassName().substring(p.provider.getClassName().lastIndexOf('$')+1).toLowerCase(Locale.ROOT);int w=(int)(p.minWidth/density),h=(int)(p.minHeight/density);
                        try{Class<?> k=Class.forName(p.provider.getClassName());int tw=p.targetCellWidth*90,th=p.targetCellHeight*100;
                            shot(render(c,Widgets.build(c,k,shown,now),w,h),new File(dir,"min-"+name+suffix),dark);
                            shot(render(c,Widgets.build(c,k,shown,now),tw,th),new File(dir,"cell-"+name+suffix),dark);
                            shot(render(c,new RemoteViews(c.getPackageName(),p.previewLayout),tw,th),new File(dir,"preview-"+name+suffix),dark);}catch(ClassNotFoundException e){throw new AssertionError(e);}}
                    shot(render(c,Widgets.stack(c,52),150,190),new File(dir,"stack"+suffix),dark);
                    shot(render(c,Widgets.dual(c,shown),320,110),new File(dir,"dual"+suffix),dark);
                    shot(render(c,Widgets.timer(c,idle),220,170),new File(dir,"timer-idle"+suffix),dark);
                    shot(render(c,Widgets.timer(c,started),220,170),new File(dir,"timer"+suffix),dark);
                    shot(render(c,Widgets.stopwatch(c,new ClockStore(t.context)),220,170),new File(dir,"stopwatch"+suffix),dark);
                    shot(render(c,Widgets.groups(c,grouped,now),300,200),new File(dir,"groups"+suffix),dark);}
            });
            MainActivity main=(MainActivity)t.startActivitySync(new Intent(t.context,MainActivity.class).putExtra("tab","Alarms").putExtra("add","alarm").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));t.waitForIdleSync();
            try{t.main(()->{t.check(main.alarmDialog!=null&&main.alarmDialog.isShowing()&&main.editingOptions!=null,"Widget + opens a new alarm in the editor");main.alarmDialog.dismiss();
                    // Alarm cards carry no status lines; switching an alarm on says when it rings.
                    ClockStore s=new ClockStore(t.context);JSONObject x=s.find("alarms",ids[1]);ClockStore.put(x,"autoSilenced",true);ClockStore.put(x,"missed",true);s.save(false);main.render();
                    t.check(!HealthChecks.texts(main.body).contains(t.context.getString(R.string.auto_silenced)),"Alarm cards show no auto-silenced or missed line");
                    String set=main.alarmSet(new ClockStore(t.context).find("alarms",ids[1]));long at=new ClockStore(t.context).find("alarms",ids[1]).optLong("next");
                    t.check(set.contains(Widgets.time(t.context,at))&&set.contains(Widgets.relative(t.context,at,System.currentTimeMillis())),"Turning an alarm on reports when it rings: "+set);
                    // Selection bar: Skip day only applies to repeating alarms.
                    ClockStore.put(x,"days",0);s.save(false);main.render();main.selection.group="QA widget";main.selection.ids.clear();main.selection.ids.add(ids[1]);main.updateBottomBar();
                    boolean onceSkip=main.bottomBar.getChildAt(2).isEnabled();ClockStore.put(x,"days",127);s.save(false);main.render();main.updateBottomBar();
                    t.check(!onceSkip&&main.bottomBar.getChildAt(2).isEnabled(),"Skip day is off for one-time alarms and on for repeating ones");main.selection.clear();main.render();
                    main.onNewIntent(new Intent(t.context,MainActivity.class).putExtra("tab","World"));t.check("World".equals(main.tab),"Widget opens the World tab");});
            }finally{t.main(main::finish);}
        }finally{t.main(()->{ClockStore s=new ClockStore(t.context);for(int id:timers){JSONObject x=s.find("timers",id);if(x!=null)s.remove("timers",x);}if(stopwatchKeep.length()>0){s.stopwatchReset();for(Iterator<String> k=stopwatchKeep.keys();k.hasNext();){String key=k.next();ClockStore.put(s.data,key,stopwatchKeep.opt(key));}}else s.stopwatchReset();s.syncStopwatch();for(int id:ids){JSONObject a=s.find("alarms",id);if(a!=null)s.remove("alarms",a);}JSONArray g=s.list("groups");for(int i=g.length()-1;i>=0;i--)if("QA widget".equals(g.optString(i)))g.remove(i);s.save(false);});SystemClock.sleep(300);}
    }
}
