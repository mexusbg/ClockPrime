package com.clockprime.app;

import android.content.Context;
import android.content.res.Configuration;

final class ThemeChoice {
    static final String[] MODES={"system","light","dark"};
    /** Before first unlock the choice is unreadable (credential storage); the ring screen then follows the system. */
    static String get(Context c){if(!c.getSystemService(android.os.UserManager.class).isUserUnlocked())return "system";return c.getSharedPreferences("appearance",Context.MODE_PRIVATE).getString("theme","system");}
    static void set(Context c,String mode){c.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putString("theme",mode).apply();}
    static boolean resolve(String mode,boolean systemDark){return "dark".equals(mode)||("system".equals(mode)&&systemDark);}
    static boolean dark(Context c){return resolve(get(c),(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);}
}
