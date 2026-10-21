package com.clockprime.app;
import android.content.*;
import java.util.*;
import org.json.JSONObject;

final class SoundAccess {
    static String title(Context c,android.net.Uri uri){try(android.database.Cursor cursor=c.getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())return cursor.getString(0);}catch(Exception ignored){}return null;}

    static void releaseUnused(Context c){Set<String> used=new HashSet<>();ClockSettings settings=new ClockSettings(c);used.add(settings.text("alarm_tone","default"));used.add(settings.text("timer_tone","default"));for(JSONObject a:new ClockStore(c).objects("alarms"))used.add(a.optString("tone"));for(android.content.UriPermission grant:c.getContentResolver().getPersistedUriPermissions())if(!used.contains(grant.getUri().toString()))try{c.getContentResolver().releasePersistableUriPermission(grant.getUri(),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}}
}
