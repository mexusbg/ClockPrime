package com.clockprime.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Xiaomi HyperOS focus notification / Super Island extras (client protocol, dev.mi.com pId=2131).
 * <p>
 * {@code miui.focus.param} holds {@code {"param_v2":{...}}}; images and buttons are referenced by
 * keys into the {@code miui.focus.pics} and {@code miui.focus.actions} bundles. SystemUI drops the
 * extras and shows the normal notification when the package lacks focus permission.
 * <p>
 * Rendering rules verified against SystemUI: island big-area left text shows only {@code title};
 * a ticking countdown needs {@code sameWidthDigitInfo.timerInfo} without {@code digit}, and
 * {@code highlightInfo.timerInfo} without {@code title}.
 */
final class FocusIsland {
    static final String PIC="miui.focus.pic_clock",PAUSE_PIC="miui.focus.pic_pause",PLAY_PIC="miui.focus.pic_play",CANCEL_PIC="miui.focus.pic_cancel",LAP_PIC="miui.focus.pic_lap",RESET_PIC="miui.focus.pic_reset";
    static final String DISMISS="miui.focus.action_dismiss",SNOOZE="miui.focus.action_snooze",PAUSE="miui.focus.action_pause",RESUME="miui.focus.action_resume",CANCEL="miui.focus.action_cancel",LAP="miui.focus.action_lap",RESET="miui.focus.action_reset";
    /** Xiaomi limits the param string to 3072 bytes. */
    static final int MAX_PARAM=3072;

    static boolean deviceSupportsIsland(){
        try{
            Class<?> props=Class.forName("android.os.SystemProperties");
            Object value=props.getMethod("getBoolean",String.class,boolean.class).invoke(null,"persist.sys.feature.island",false);
            return value instanceof Boolean b&&b;
        }catch(Exception ignored){return false;}
    }

    /** 1 = HyperOS 1, 2 = HyperOS 2, 3 = HyperOS 3 (island); 0 when absent. */
    static int protocol(Context c){return android.provider.Settings.System.getInt(c.getContentResolver(),"notification_focus_protocol",0);}

    static boolean isXiaomiFamily(){
        String m=android.os.Build.MANUFACTURER==null?"":android.os.Build.MANUFACTURER.toLowerCase(java.util.Locale.US);
        String b=android.os.Build.BRAND==null?"":android.os.Build.BRAND.toLowerCase(java.util.Locale.US);
        for(String name:new String[]{"xiaomi","redmi","poco"})if(m.equals(name)||b.equals(name))return true;
        return false;
    }

    static boolean shouldAttach(Context c){return isXiaomiFamily()||deviceSupportsIsland();}

    /** HyperOS focus permission for this package; null when not a HyperOS island phone or unknown. Slow: call off the main thread. */
    static Boolean canShowFocus(Context c){
        if(!shouldAttach(c))return null;
        try{
            Bundle in=new Bundle();in.putString("package",c.getPackageName());
            Bundle result=c.getContentResolver().call(android.net.Uri.parse("content://miui.statusbar.notification.public"),"canShowFocus",null,in);
            return result==null||!result.containsKey("canShowFocus")?null:result.getBoolean("canShowFocus");
        }catch(Exception e){return null;}
    }

    static String supportLabel(Context c){
        if(deviceSupportsIsland())return c.getString(R.string.island_supported);
        if(isXiaomiFamily())return c.getString(R.string.island_xiaomi_needs_permission);
        return c.getString(R.string.island_unavailable);
    }

    static JSONObject pic(String key){return put(new JSONObject(),"type",1,"pic",key);}
    static JSONObject put(JSONObject o,Object... pairs){
        try{for(int i=0;i<pairs.length;i+=2)o.put((String)pairs[i],pairs[i+1]);}catch(JSONException e){throw new IllegalArgumentException(e);}
        return o;
    }
    static JSONObject timerInfo(long end,long duration,boolean running){
        long now=System.currentTimeMillis();
        return put(new JSONObject(),"timerType",running?-1:-2,"timerWhen",end,"timerTotal",Math.max(1,duration),"timerSystemCurrent",now);
    }

    /** Countdown digits on the island and in the expanded card; frozen (type -2) while paused. */
    static JSONObject timerParam(Context c,String name,long end,long duration,boolean running){
        String[] toggle=running?new String[]{PAUSE,PAUSE_PIC,c.getString(R.string.pause)}:new String[]{RESUME,PLAY_PIC,c.getString(R.string.resume)};
        long timeout=running?Math.max(60,(end-System.currentTimeMillis())/1000L+120):3600;
        return countParam(c,"timer",name,c.getString(running?R.string.timer_running:R.string.timer_paused),timerInfo(end,duration,running),timeout,toggle,new String[]{CANCEL,CANCEL_PIC,c.getString(R.string.cancel)});
    }

    /** Count-up digits (type 1 running, 2 paused); timerWhen is the virtual start time. */
    static JSONObject stopwatchParam(Context c,String name,long elapsed,boolean running){
        long now=System.currentTimeMillis();
        JSONObject info=put(new JSONObject(),"timerType",running?1:2,"timerWhen",now-elapsed,"timerTotal",Math.max(1,elapsed),"timerSystemCurrent",now);
        String[] first=running?new String[]{PAUSE,PAUSE_PIC,c.getString(R.string.pause)}:new String[]{RESUME,PLAY_PIC,c.getString(R.string.resume)};
        String[] second=running?new String[]{LAP,LAP_PIC,c.getString(R.string.lap)}:new String[]{RESET,RESET_PIC,c.getString(R.string.reset)};
        return countParam(c,"stopwatch",name,c.getString(running?R.string.stopwatch_running:R.string.timer_paused),info,24*3600,first,second);
    }

    /** Shared countdown / count-up payload; each button is {action key, icon key, title}. */
    static JSONObject countParam(Context c,String business,String name,String status,JSONObject timer,long islandTimeout,String[]... buttons){
        JSONObject big=put(new JSONObject(),
                "imageTextInfoLeft",put(new JSONObject(),"type",1,"picInfo",pic(PIC)),
                "sameWidthDigitInfo",put(new JSONObject(),"timerInfo",timer,"showHighlightColor",true));
        JSONObject island=put(new JSONObject(),"islandProperty",1,"islandTimeout",islandTimeout,"highlightColor","#9CBCFF",
                "bigIslandArea",big,"smallIslandArea",put(new JSONObject(),"picInfo",pic(PIC)),
                "shareData",put(new JSONObject(),"pic",PIC,"title",name,"content",status,"shareContent",name));
        JSONArray actions=new JSONArray();
        for(String[] b:buttons)actions.put(put(new JSONObject(),"action",b[0],"actionIcon",b[1],"actionTitle",b[2],"actionIntentType",2));
        JSONObject v2=put(new JSONObject(),"protocol",1,"business",business,"updatable",true,"enableFloat",false,"islandFirstFloat",false,"timeout",1440,
                "ticker",name,"tickerPic",PIC,"aodTitle",name,"aodPic",PIC,
                // No title: a title replaces the chronometer in highlightInfo.
                "highlightInfo",put(new JSONObject(),"timerInfo",timer,"subContent",name,"colorSubContent","#386BC0","colorSubContentDark","#9CBCFF","picFunction",PIC),
                "actions",actions,"param_island",island);
        return put(new JSONObject(),"param_v2",v2);
    }

    /** Ringing alarm or finished timer: time and label, with Dismiss and optional Snooze buttons. */
    static JSONObject ringParam(Context c,String ticker,String primary,String secondary,boolean snooze){
        String label=secondary==null||secondary.isEmpty()?ticker:secondary;
        JSONObject big=put(new JSONObject(),
                "imageTextInfoLeft",put(new JSONObject(),"type",1,"picInfo",pic(PIC),"textInfo",put(new JSONObject(),"title",primary,"narrowFont",true,"showHighlightColor",true)),
                "textInfo",put(new JSONObject(),"title",label));
        JSONObject island=put(new JSONObject(),"islandProperty",2,"islandTimeout",3600,"highlightColor","#F5CE8D",
                "bigIslandArea",big,"smallIslandArea",put(new JSONObject(),"picInfo",pic(PIC)),
                "shareData",put(new JSONObject(),"pic",PIC,"title",primary,"content",label,"shareContent",primary+" "+label));
        JSONArray buttons=new JSONArray();
        if(snooze)buttons.put(put(new JSONObject(),"type",2,"action",SNOOZE,"actionTitle",c.getString(R.string.snooze_action),"actionTitleColor","#FFFFFF","actionBgColor","#3A4A66","actionIntentType",2));
        buttons.put(put(new JSONObject(),"type",2,"action",DISMISS,"actionTitle",c.getString(R.string.dismiss),"actionTitleColor","#132342","actionBgColor","#F5CE8D","actionIntentType",2));
        String full=primary+" · "+label;
        JSONObject v2=put(new JSONObject(),"protocol",1,"business","alarm","updatable",true,"enableFloat",true,"islandFirstFloat",true,"timeout",120,
                "ticker",full,"tickerPic",PIC,"aodTitle",full,"aodPic",PIC,
                // baseInfo is the HyperOS 2 card; iconTextInfo + textButton is the HyperOS 3 card with buttons.
                "baseInfo",put(new JSONObject(),"type",2,"title",primary,"content",label),
                "iconTextInfo",put(new JSONObject(),"title",primary,"content",label,"animIconInfo",put(new JSONObject(),"type",0,"src",PIC)),
                "textButton",buttons,"param_island",island);
        return put(new JSONObject(),"param_v2",v2);
    }

    static Notification.Action action(Context c,int icon,String title,PendingIntent intent){
        return new Notification.Action.Builder(Icon.createWithResource(c,icon),title,intent).build();
    }

    static void attachTimer(Context c,Notification n,String name,long end,long duration,boolean running,PendingIntent toggle,PendingIntent cancel){
        if(!shouldAttach(c)||end<=System.currentTimeMillis())return;
        Bundle actions=new Bundle();
        if(running)actions.putParcelable(PAUSE,action(c,R.drawable.ic_action_pause,c.getString(R.string.pause),toggle));
        else actions.putParcelable(RESUME,action(c,R.drawable.ic_action_play,c.getString(R.string.resume),toggle));
        actions.putParcelable(CANCEL,action(c,R.drawable.ic_action_cancel,c.getString(R.string.cancel),cancel));
        putExtras(c,n,timerParam(c,name,end,duration,running),actions);
    }

    static void attachStopwatch(Context c,Notification n,String name,long elapsed,boolean running,PendingIntent toggle,PendingIntent second){
        if(!shouldAttach(c))return;
        Bundle actions=new Bundle();
        if(running){actions.putParcelable(PAUSE,action(c,R.drawable.ic_action_pause,c.getString(R.string.pause),toggle));actions.putParcelable(LAP,action(c,R.drawable.ic_action_lap,c.getString(R.string.lap),second));}
        else{actions.putParcelable(RESUME,action(c,R.drawable.ic_action_play,c.getString(R.string.resume),toggle));actions.putParcelable(RESET,action(c,R.drawable.ic_action_reset,c.getString(R.string.reset),second));}
        putExtras(c,n,stopwatchParam(c,name,elapsed,running),actions);
    }

    static void attachRinging(Context c,Notification n,String ticker,String primary,String secondary,PendingIntent dismiss,PendingIntent snooze){
        if(!shouldAttach(c))return;
        Bundle actions=new Bundle();
        actions.putParcelable(DISMISS,action(c,R.drawable.ic_action_cancel,c.getString(R.string.dismiss),dismiss));
        if(snooze!=null)actions.putParcelable(SNOOZE,action(c,R.drawable.ic_stat_notify,c.getString(R.string.snooze_action),snooze));
        putExtras(c,n,ringParam(c,ticker,primary,secondary,snooze!=null),actions);
    }

    static void putExtras(Context c,Notification n,JSONObject param,Bundle actions){
        String json=param.toString();
        if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_PARAM){DebugLog.log(c,"island","param too large "+json.length());return;}
        Bundle pics=new Bundle();
        pics.putParcelable(PIC,Icon.createWithResource(c,R.drawable.ic_stat_notify));
        pics.putParcelable(PAUSE_PIC,Icon.createWithResource(c,R.drawable.ic_action_pause));
        pics.putParcelable(PLAY_PIC,Icon.createWithResource(c,R.drawable.ic_action_play));
        pics.putParcelable(LAP_PIC,Icon.createWithResource(c,R.drawable.ic_action_lap));
        pics.putParcelable(RESET_PIC,Icon.createWithResource(c,R.drawable.ic_action_reset));
        pics.putParcelable(CANCEL_PIC,Icon.createWithResource(c,R.drawable.ic_action_cancel));
        n.extras.putBundle("miui.focus.pics",pics);
        n.extras.putBundle("miui.focus.actions",actions);
        n.extras.putString("miui.focus.param",json);
    }
}
