package com.clockprime.app;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Original outline icons using familiar clock symbols; all coordinates use a 24-unit grid. */
final class ClockIcon extends Drawable {
    final String name;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    ClockIcon(String name,int color){this.name=name;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.65f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
    void line(Canvas c,float x,float y,float x2,float y2){c.drawLine(x,y,x2,y2,paint);}
    void path(Canvas c,float... points){Path p=new Path();p.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)p.lineTo(points[i],points[i+1]);c.drawPath(p,paint);}
    @Override public void draw(Canvas raw){int save=raw.save();Rect b=getBounds();raw.translate(b.left,b.top);raw.scale(b.width()/24f,b.height()/24f);Canvas c=raw;
        switch(name){
            case "Alarms":c.drawCircle(12,13,7.2f,paint);path(c,12,9,12,13,15,15);path(c,3,6,6,3);path(c,18,3,21,6);line(c,7,20,5,22);line(c,17,20,19,22);break;
            case "World":c.drawCircle(12,12,9,paint);c.drawOval(8,3,16,21,paint);line(c,3,12,21,12);break;
            case "Stopwatch":c.drawCircle(12,14,7.5f,paint);line(c,9,2,15,2);line(c,12,2,12,6);line(c,18,6,20,4);line(c,12,10,12,14);line(c,12,14,15,14);break;
            case "Timers":line(c,6,3,18,3);line(c,6,21,18,21);path(c,7,3,7,7,17,17,17,21);path(c,17,3,17,7,7,17,7,21);line(c,9,6,15,6);line(c,9,18,15,18);break;
            case "settings":
                Path gear=new Path();for(int i=0;i<48;i++){double angle=i*Math.PI/24;float radius=(i%6==0||i%6==5)?7.1f:9.3f;float x=12+(float)Math.cos(angle)*radius,y=12+(float)Math.sin(angle)*radius;if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);}gear.close();c.drawPath(gear,paint);c.drawCircle(12,12,3.1f,paint);break;
            case "plus":line(c,12,5,12,19);line(c,5,12,19,12);break;
            case "close":line(c,6,6,18,18);line(c,18,6,6,18);break;
            case "down":path(c,7,9,12,14,17,9);break;
            case "back":path(c,15,5,8,12,15,19);break;
            case "right":path(c,9,7,14,12,9,17);break;
            case "on":c.drawArc(4,4,20,20,-50,280,false,paint);line(c,12,2,12,12);break;
            case "off":c.drawArc(4,4,20,20,-50,280,false,paint);line(c,12,2,12,10);line(c,3,3,21,21);break;
            case "skip":path(c,4,5,14,12,4,19,4,5);line(c,18,5,18,19);break;
            case "delete":line(c,4,6,20,6);path(c,8,6,8,3,16,3,16,6);path(c,6,6,7,21,17,21,18,6);line(c,10,10,10,17);line(c,14,10,14,17);break;
            case "none":c.drawRoundRect(4,4,20,20,3,3,paint);break;
            case "all":c.drawRoundRect(4,4,20,20,3,3,paint);path(c,8,12,11,15,16,9);break;
            case "group":path(c,3,8,3,5,9,5,11,7,21,7,21,20,3,20,3,8);line(c,9,13,15,13);line(c,12,10,12,16);break;
        }raw.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
