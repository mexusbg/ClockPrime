package com.clockprime.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.Switch;

/** The app's two-theme colours, shared by every screen. Light MUTED is dark enough for 4.5:1 on SOFT. */
final class Palette {
    final boolean dark;final int bg,card,text,muted,accent,gold,line,soft,onAccent,inverseAccent;
    Palette(Context c){this(ThemeChoice.dark(c));}
    Palette(boolean dark){this.dark=dark;bg=c("#101722","#F3F6FC");card=c("#192334","#FFFFFF");text=c("#EEF3FF","#17253C");muted=c("#A0ADC3","#56657C");accent=c("#9CBCFF","#386BC0");gold=c("#F5CE8D","#996319");line=c("#2B3950","#E1E7F0");soft=c("#253652","#E8F0FC");onAccent=c("#132342","#FFFFFF");inverseAccent=c("#386BC0","#9CBCFF");}
    int c(String night,String day){return Color.parseColor(dark?night:day);}
    /** Off thumb in MUTED (≥3:1 on cards and SOFT in both themes), on in accent. */
    void tint(Switch s){int[][] states={{android.R.attr.state_checked},{}};s.setThumbTintList(new ColorStateList(states,new int[]{accent,muted}));s.setTrackTintList(new ColorStateList(states,new int[]{accent,muted}));}
}
