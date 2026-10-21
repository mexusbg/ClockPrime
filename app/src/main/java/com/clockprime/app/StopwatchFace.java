package com.clockprime.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.function.LongSupplier;

/** A quiet minute sweep around the elapsed time; no instructional copy. */
@android.annotation.SuppressLint("ViewConstructor") // Constructed in code with required state; never inflated from XML.
final class StopwatchFace extends View {
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final LongSupplier elapsed;final boolean running;final int text,muted,accent,track;
    StopwatchFace(Context c,LongSupplier elapsed,boolean running,int text,int muted,int accent,int track){super(c);this.elapsed=elapsed;this.running=running;this.text=text;this.muted=muted;this.accent=accent;this.track=track;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);paint.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));paint.setFontFeatureSettings("tnum");}
    float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);long time=elapsed.getAsLong();float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-dp(12);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(track);canvas.drawCircle(cx,cy,r,paint);
        for(int i=0;i<60;i++){double angle=i*Math.PI/30-Math.PI/2;paint.setColor(i%5==0?muted:track);float inner=r-dp(i%5==0?11:6);canvas.drawLine(cx+(float)Math.cos(angle)*inner,cy+(float)Math.sin(angle)*inner,cx+(float)Math.cos(angle)*(r-dp(3)),cy+(float)Math.sin(angle)*(r-dp(3)),paint);}
        paint.setColor(accent);paint.setStrokeWidth(dp(3));canvas.drawArc(cx-r,cy-r,cx+r,cy+r,-90,(time%60000)/60000f*360,false,paint);
        String value=MainActivity.elapsed(time);if(time<3600000)value=value.substring(3);int split=value.lastIndexOf('.');String whole=value.substring(0,split),fraction=value.substring(split);
        paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.LEFT);float size=dp(time<3600000?44:31);paint.setTextSize(size);float leftWidth=paint.measureText(whole);paint.setTextSize(size*.55f);float rightWidth=paint.measureText(fraction);float start=cx-(leftWidth+rightWidth)/2,baseline=cy+size*.32f;
        paint.setTextSize(size);paint.setColor(text);canvas.drawText(whole,start,baseline,paint);paint.setTextSize(size*.55f);paint.setColor(muted);canvas.drawText(fraction,start+leftWidth,baseline,paint);
        if(running&&isShown())postInvalidateOnAnimation();
    }
}
