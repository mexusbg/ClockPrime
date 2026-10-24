package com.clockprime.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * "Get alarms ready" checklist of the phone settings alarms depend on: shown once on first start and from
 * Settings > Reliability. Each row opens the right system screen; statuses refresh when the user comes back.
 */
final class SetupGuide {
    /** done == null: the phone offers no way to check (vendor autostart pages); a visit counts instead. */
    static final class Step{final String key;final int title,detail;final BooleanSupplier done;final Runnable fix;
        Step(String key,int title,int detail,BooleanSupplier done,Runnable fix){this.key=key;this.title=title;this.detail=detail;this.done=done;this.fix=fix;}}
    static final String SHOWN="setup_shown",AUTOSTART_VISITED="autostart_visited",NOTIFICATIONS_ASKED="notifications_asked";
    static final Set<String> VENDORS=Set.of("xiaomi","redmi","poco","samsung","huawei","honor","oppo","oneplus","realme","vivo");
    /** Weak: the open checklist must never keep a destroyed activity alive. */
    static java.lang.ref.WeakReference<SetupGuide> open=new java.lang.ref.WeakReference<>(null);
    final Activity a;final boolean dark;final int text,muted,accent,onAccent,soft,line;AlertDialog dialog;LinearLayout rows;TextView progress;

    SetupGuide(Activity a){
        this.a=a;dark=ThemeChoice.dark(a);
        text=Color.parseColor(dark?"#EEF3FF":"#17253C");muted=Color.parseColor(dark?"#A0ADC3":"#617189");accent=Color.parseColor(dark?"#9CBCFF":"#386BC0");
        onAccent=Color.parseColor(dark?"#132342":"#FFFFFF");soft=Color.parseColor(dark?"#253652":"#E8F0FC");line=Color.parseColor(dark?"#2B3950":"#E1E7F0");
    }
    static ClockSettings prefs(Context c){return new ClockSettings(c);}
    static boolean vendorAutostart(){return VENDORS.contains(Build.MANUFACTURER.toLowerCase(Locale.ROOT));}

    static List<Step> steps(Activity a){
        NotificationManager nm=a.getSystemService(NotificationManager.class);List<Step> out=new ArrayList<>();
        out.add(new Step("notifications",R.string.step_notifications,R.string.step_notifications_detail,nm::areNotificationsEnabled,()->requestNotifications(a)));
        if(Build.VERSION.SDK_INT>=34)out.add(new Step("fullscreen",R.string.fullscreen_label,R.string.step_fullscreen_detail,nm::canUseFullScreenIntent,
            ()->start(a,new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+a.getPackageName())))));
        out.add(new Step("overlay",R.string.launch_label,R.string.launch_detail,()->RingLaunch.canLaunch(a),()->RingLaunch.requestPermission(a)));
        out.add(new Step("battery",R.string.step_battery,R.string.step_battery_detail,()->a.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(a.getPackageName()),()->openBatterySettings(a)));
        // ClockPrime holds USE_EXACT_ALARM; the row only appears if a phone ever revokes it.
        if(!a.getSystemService(AlarmManager.class).canScheduleExactAlarms())out.add(new Step("exact",R.string.exact_alarms_label,R.string.step_exact_detail,()->a.getSystemService(AlarmManager.class).canScheduleExactAlarms(),
            ()->start(a,new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+a.getPackageName())))));
        if(vendorAutostart())out.add(new Step("autostart",R.string.step_autostart,R.string.step_autostart_detail,null,()->{prefs(a).put(AUTOSTART_VISITED,true);openBackgroundApps(a);}));
        return out;
    }
    static boolean done(Context c,Step s){return s.done==null?prefs(c).flag(AUTOSTART_VISITED,false):s.done.getAsBoolean();}
    static int doneCount(Activity a){List<Step> all=steps(a);return (int)all.stream().filter(s->done(a,s)).count();}

    /** First start (once, outside tests): shown instead of the bare notification-permission prompt. */
    static boolean pendingFirstRun(Activity a){return MainActivity.launchPrompt&&!prefs(a).flag(SHOWN,false);}
    static void showFirstRun(Activity a){if(!pendingFirstRun(a))return;prefs(a).put(SHOWN,true);new SetupGuide(a).show();}
    /** The calling activity forwards onResume and permission results so statuses follow the user's changes. */
    static void refreshOpen(){SetupGuide g=open.get();if(g!=null&&g.dialog!=null&&g.dialog.isShowing())g.refresh();}

    void show(){
        int pad=dp(22);LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(pad,dp(4),pad,dp(4));
        body.addView(label(a.getString(R.string.setup_intro),14,muted));
        progress=label("",13,accent);progress.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));progress.setPadding(0,dp(10),0,dp(6));body.addView(progress);
        rows=new LinearLayout(a);rows.setOrientation(LinearLayout.VERTICAL);body.addView(rows);
        ScrollView scroll=new ScrollView(a);scroll.addView(body);
        dialog=new AlertDialog.Builder(a).setTitle(R.string.setup_title).setView(scroll).setNegativeButton(R.string.setup_later,null).setPositiveButton(R.string.setup_done,null).create();
        dialog.setOnDismissListener(d->{if(open.get()==this)open.clear();});
        open=new java.lang.ref.WeakReference<>(this);refresh();dialog.show();
    }
    void refresh(){
        rows.removeAllViews();List<Step> all=steps(a);int done=0;
        for(Step s:all){boolean ok=done(a,s);if(ok)done++;rows.addView(row(s,ok));}
        progress.setText(a.getString(R.string.setup_progress,done,all.size()));
    }
    View row(Step s,boolean ok){
        LinearLayout r=new LinearLayout(a);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(10),0,dp(10));r.setTag("setup-"+s.key);
        LinearLayout words=new LinearLayout(a);words.setOrientation(LinearLayout.VERTICAL);
        words.addView(label(a.getString(s.title),16,text));TextView d=label(a.getString(s.detail),12,muted);d.setPadding(0,dp(2),0,0);words.addView(d);
        r.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView state=label(a.getString(ok?(s.done==null?R.string.setup_checked:R.string.setup_step_done):s.done==null?R.string.setup_open:R.string.allow),13,ok?accent:onAccent);
        state.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));state.setGravity(Gravity.CENTER);state.setPadding(dp(14),dp(8),dp(14),dp(8));state.setMinWidth(dp(76));
        GradientDrawable pill=new GradientDrawable();pill.setCornerRadius(dp(18));if(ok){pill.setColor(Color.TRANSPARENT);pill.setStroke(dp(1),line);}else pill.setColor(accent);state.setBackground(pill);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMargins(dp(12),0,0,0);r.addView(state,lp);
        // A finished step can still be reopened (e.g. to undo it), but only a pending one looks like a call to action.
        r.setBackground(new RippleDrawable(ColorStateList.valueOf(soft),null,new ColorDrawable(Color.WHITE)));r.setOnClickListener(v->s.fix.run());
        r.setContentDescription(a.getString(s.title)+", "+state.getText());
        return r;
    }
    TextView label(String value,int size,int color){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    int dp(float n){return Math.round(n*a.getResources().getDisplayMetrics().density);}

    static void start(Activity a,Intent i){try{a.startActivity(i);}catch(ActivityNotFoundException e){openAppDetails(a);}}
    /** The system dialog asks only twice; after that the notification settings page is the way in. */
    static void requestNotifications(Activity a){
        if(a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&(!prefs(a).flag(NOTIFICATIONS_ASKED,false)||a.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))){
            prefs(a).put(NOTIFICATIONS_ASKED,true);a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);return;}
        start(a,new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName()));
    }
    /** Alarm clocks are an accepted use of the direct exemption request (Play policy); F-Droid has no such rule. */
    @android.annotation.SuppressLint("BatteryLife")
    static void openBatterySettings(Activity a){
        try{a.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+a.getPackageName())));}
        catch(Exception e){start(a,new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}
    }
    /** The phone maker's own autostart / background page first; Android's generic battery list exists everywhere, so it comes last. */
    static void openBackgroundApps(Activity a){
        String pkg=a.getPackageName();
        Intent[] candidates={
            new Intent().setComponent(new ComponentName("com.miui.securitycenter","com.miui.permcenter.autostart.AutoStartManagementActivity")),
            new Intent().setComponent(new ComponentName("com.miui.powerkeeper","com.miui.powerkeeper.ui.HiddenAppsConfigActivity")).putExtra("package_name",pkg).putExtra("package_label",a.getString(R.string.app_name)),
            new Intent().setComponent(new ComponentName("com.samsung.android.lool","com.samsung.android.sm.battery.ui.BatteryActivity")),
            new Intent().setComponent(new ComponentName("com.samsung.android.sm","com.samsung.android.sm.battery.ui.BatteryActivity")),
            new Intent("com.samsung.android.sm.ACTION_BATTERY"),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager","com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager","com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
            new Intent().setComponent(new ComponentName("com.coloros.safecenter","com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.oplus.safecenter","com.oplus.safecenter.permission.startup.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.oneplus.security","com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
            new Intent().setAction("android.settings.APP_BATTERY_SETTINGS").setData(Uri.parse("package:"+pkg)),
            new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        };
        for(Intent intent:candidates){try{a.startActivity(intent);return;}catch(Exception ignored){}}
        Toast.makeText(a,R.string.open_app_settings,Toast.LENGTH_SHORT).show();openAppDetails(a);
    }
    static void openAppDetails(Activity a){a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+a.getPackageName())));}
}
