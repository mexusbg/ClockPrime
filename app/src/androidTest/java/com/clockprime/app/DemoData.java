package com.clockprime.app;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import org.json.*;
import java.io.*;

/**
 * Store-listing screenshots (-e demo_data replace): replaces ALL app data with a tidy demo set and exports the
 * launcher icon to files/demo/icon.png. Emulator only — it wipes the alarms on the device it runs on.
 */
final class DemoData {
    static JSONObject alarm(ClockStore s,String name,String group,int hour,int minute,int days,boolean on){
        JSONObject a=new JSONObject();ClockStore.put(a,"id",s.id());ClockStore.put(a,"name",name);ClockStore.put(a,"group",group);ClockStore.put(a,"hour",hour);ClockStore.put(a,"minute",minute);
        ClockStore.put(a,"days",days);ClockStore.put(a,"sound",true);ClockStore.put(a,"vibrate",true);s.list("alarms").put(a);s.enable(a,on);return a;
    }
    static void run(SmokeTests t){
        t.main(()->{
            ClockStore s=new ClockStore(t.getTargetContext());
            for(String k:new String[]{"alarms","timers"})for(JSONObject o:s.objects(k))s.remove(k,o);
            ClockStore.put(s.data,"groups",new JSONArray());ClockStore.put(s.data,"cities",new JSONArray());s.stopwatchReset();
            for(String g:new String[]{"Work","Travel"})s.list("groups").put(g);
            int weekdays=31,weekend=96,mwf=1|4|16;
            alarm(s,"Wake up","",6,45,weekdays,true);alarm(s,"Weekend","",9,30,weekend,true);
            alarm(s,"Gym","Work",7,30,mwf,true);alarm(s,"Stand-up","Work",8,15,weekdays,true);alarm(s,"Lunch","Work",13,0,weekdays,false);
            alarm(s,"Airport","Travel",5,10,0,false);
            for(String zone:new String[]{"Europe/London","America/New_York","Asia/Tokyo"})s.list("cities").put(zone);
            for(Object[] tm:new Object[][]{{"Tea",4},{"Pasta",10}}){JSONObject x=new JSONObject();long d=((Integer)tm[1])*60000L;ClockStore.put(x,"id",s.id());ClockStore.put(x,"name",tm[0]);ClockStore.put(x,"duration",d);ClockStore.put(x,"remaining",d);s.list("timers").put(x);}
            s.save();s.syncStopwatch();
            Drawable icon=t.getTargetContext().getDrawable(R.mipmap.ic_launcher);Bitmap b=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);icon.setBounds(0,0,512,512);icon.draw(new Canvas(b));
            File dir=new File(t.getTargetContext().getExternalFilesDir(null),"demo");dir.mkdirs();
            try(FileOutputStream out=new FileOutputStream(new File(dir,"icon.png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}catch(IOException e){throw new AssertionError(e);}
        });
        Bundle out=new Bundle();out.putString("stream","Demo data loaded; icon at files/demo/icon.png\n");t.finish(-1,out);
    }
}
