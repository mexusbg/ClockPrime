package com.clockprime.app;

import android.content.Context;
import android.content.res.Configuration;
import android.icu.text.TimeZoneNames;
import android.os.LocaleList;
import android.text.format.DateFormat;
import java.time.*;
import java.time.format.*;
import java.util.Locale;

/** Display-only formatting. Persisted dates, group IDs, and schedules stay locale independent. */
final class LocaleText {
    /** UI language from the app configuration (strings, city names, weekday labels). */
    /** Text inside v follows the UI language's direction, so a Latin alarm or group name still lines up with a right-to-left UI. */
    static <T extends android.view.View> T direction(T v){v.setTextDirection(android.view.View.TEXT_DIRECTION_LOCALE);return v;}
    static Locale locale(Context c){return c.getResources().getConfiguration().getLocales().get(0);}
    /**
     * Locale for numeric date/time order. {@code locales_config} often resolves to a language-only
     * tag such as {@code en}, and CLDR treats bare English as US (MM/DD). Prefer the system locale
     * with its country (e.g. {@code en-GB}, {@code bg-BG}) so DD/MM matches the phone.
     */
    static Locale formatLocale(Context c){
        Locale app=locale(c);
        if(!app.getCountry().isEmpty())return app;
        LocaleList system=LocaleList.getDefault();
        for(int i=0;i<system.size();i++){
            Locale sys=system.get(i);
            if(sys.getLanguage().equals(app.getLanguage())&&!sys.getCountry().isEmpty())return sys;
        }
        Locale format=Locale.getDefault(Locale.Category.FORMAT);
        if(!format.getCountry().isEmpty())return format;
        for(int i=0;i<system.size();i++){
            Locale sys=system.get(i);
            if(!sys.getCountry().isEmpty())return sys;
        }
        Locale def=Locale.getDefault();
        return def.getCountry().isEmpty()?app:def;
    }
    static Context formatContext(Context c){
        Locale format=formatLocale(c);
        if(format.equals(locale(c)))return c;
        Configuration config=new Configuration(c.getResources().getConfiguration());
        config.setLocales(new LocaleList(format));
        return c.createConfigurationContext(config);
    }
    static boolean use24Hour(Context c){return new ClockSettings(c).use24Hour(c);}
    static DateTimeFormatter formatter(Context c,String skeleton){Locale l=formatLocale(c);return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(l,skeleton),l).withDecimalStyle(DecimalStyle.of(l));}
    static String alarmTime(Context c,int hour,int minute){return LocalTime.of(hour,minute).format(formatter(c,use24Hour(c)?"Hm":"hm"));}
    static String datePattern(Context c){
        char[] order=new ClockSettings(c).dateOrder(c);
        StringBuilder pattern=new StringBuilder();
        for(int i=0;i<order.length;i++){
            if(i>0)pattern.append('/');
            switch(order[i]){case 'd'->pattern.append("dd");case 'M'->pattern.append("MM");case 'y'->pattern.append("yyyy");default->pattern.append(order[i]);}
        }
        return pattern.toString();
    }
    static String dateTime(Context c,long millis){
        Locale locale=formatLocale(c);
        ZonedDateTime when=Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault());
        String date=when.format(DateTimeFormatter.ofPattern(datePattern(c),locale));
        String time=when.format(formatter(c,use24Hour(c)?"Hm":"hm"));
        return date+", "+time;
    }
    static String worldTime(Context c,ZonedDateTime time){return time.format(formatter(c,use24Hour(c)?"Hms":"hms"));}
    static String day(Context c,ZonedDateTime time){return time.format(formatter(c,"MMMdE"));}
    static String days(Context c,int mask){if(mask==0)return c.getString(R.string.once);if(mask==127)return c.getString(R.string.every_day);if(mask==31)return c.getString(R.string.weekdays);java.util.StringJoiner j=new java.util.StringJoiner(" ");for(int i=0;i<7;i++)if((mask&(1<<i))!=0)j.add(DayOfWeek.of(i+1).getDisplayName(TextStyle.SHORT,locale(c)));return j.toString();}
    static String shortDate(Context c,java.time.LocalDate d){return d.format(formatter(c,d.getYear()==java.time.LocalDate.now().getYear()?"MMMEd":"yMMMEd"));}
    static String city(Context c,String zone){String canonical=android.icu.util.TimeZone.getCanonicalID(zone);String name=TimeZoneNames.getInstance(locale(c)).getExemplarLocationName(canonical==null?zone:canonical);return name==null?zone.substring(zone.lastIndexOf('/')+1).replace('_',' '):name;}
}
