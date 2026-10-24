package com.clockprime.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.Locale;

/** A continuous, wrapping wheel with a real numeric editor in its selected row. */
@android.annotation.SuppressLint("ViewConstructor") // Constructed in code with required state; never inflated from XML.
final class TimeWheel extends FrameLayout {
    final int minimum,maximum;final String[] labels;final String label;final Wheel surface;final EditText editor;
    /** Thrown for a typed value outside 0..maximum; the wheel shows the range itself, callers need no toast. */
    static final class OutOfRange extends IllegalArgumentException{OutOfRange(String message){super(message);}}
    final int foreground,muted,highlight,warning;final TextView hint;final float textSize,rowHeight;
    float position;ValueAnimator animator;boolean editing;Integer pendingValue;
    Runnable nextEditor;boolean populating;java.util.function.BooleanSupplier beforeEdit=()->true;
    TimeWheel(Context context,int maximum,int value,String label,boolean compact,int foreground,int muted,int highlight,int warning){this(context,0,maximum,value,label,compact,foreground,muted,highlight,warning,null);}
    /** minimum..maximum (12-hour clocks use 1..12); with labels, a non-wrapping wheel of those words (AM/PM) that a tap flips. */
    TimeWheel(Context context,int minimum,int maximum,int value,String label,boolean compact,int foreground,int muted,int highlight,int warning,String[] labels){
        super(context);this.minimum=minimum;this.maximum=maximum;this.labels=labels;this.label=label;this.foreground=foreground;this.muted=muted;this.highlight=highlight;this.warning=warning;
        textSize=sp(labels!=null?30:compact?36:46);rowHeight=Math.max(dp(58),textSize+dp(12));position=value;
        surface=new Wheel(context);addView(surface,new LayoutParams(-1,-1));
        editor=new EditText(context){@Override public boolean onKeyPreIme(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP){try{finishEditing();}catch(IllegalArgumentException ignored){}}return super.onKeyPreIme(key,event);}};editor.setSingleLine();editor.setGravity(Gravity.CENTER);editor.setPadding(0,0,0,0);editor.setBackgroundColor(Color.TRANSPARENT);editor.setTextColor(foreground);editor.setTextSize(compact?36:46);editor.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));editor.setFontFeatureSettings("tnum");editor.setInputType(InputType.TYPE_CLASS_NUMBER);editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});editor.setSelectAllOnFocus(true);editor.setContentDescription(label);editor.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_EXTRACT_UI);editor.setVisibility(GONE);
        addView(editor,new LayoutParams(-1,(int)rowHeight,Gravity.CENTER));
        hint=new TextView(context);hint.setTextSize(12);hint.setTextColor(warning);hint.setGravity(Gravity.CENTER);hint.setPadding(Math.round(dp(10)),Math.round(dp(3)),Math.round(dp(10)),Math.round(dp(3)));hint.setVisibility(GONE);hint.setText(context.getString(R.string.time_range_short,minimum,maximum));
        android.graphics.drawable.GradientDrawable pill=new android.graphics.drawable.GradientDrawable();pill.setColor(highlight);pill.setCornerRadius(dp(12));hint.setBackground(pill);hint.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams hintSpace=new LayoutParams(-2,-2,Gravity.CENTER);hintSpace.topMargin=Math.round(rowHeight);addView(hint,hintSpace);
        editor.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE||action==EditorInfo.IME_ACTION_NEXT){try{finishEditing();if(nextEditor!=null)nextEditor.run();else hideKeyboard();}catch(IllegalArgumentException ignored){}return true;}return false;});
        editor.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(android.text.Editable text){if(!populating&&hint.getVisibility()==VISIBLE)clearError();if(!editing||populating||text.length()!=2||nextEditor==null)return;try{int n=Integer.parseInt(text.toString());if(n>=minimum&&n<=maximum)editor.post(()->{if(isAttachedToWindow()&&editing&&editor.getText().length()==2){try{finishEditing();nextEditor.run();}catch(IllegalArgumentException ignored){}}});}catch(NumberFormatException ignored){}}});
        setMinimumWidth((int)dp(64));
    }
    float dp(float value){return value*getResources().getDisplayMetrics().density;}
    float sp(float value){return value*getResources().getDisplayMetrics().scaledDensity;}
    int wrap(int value){return labels!=null?Math.max(minimum,Math.min(maximum,value)):Math.floorMod(value-minimum,maximum-minimum+1)+minimum;}
    float clamp(float p){return labels==null?p:Math.max(minimum,Math.min(maximum,p));}
    int selected(){return wrap(Math.round(position));}
    String format(int value){return labels!=null?labels[value-minimum]:String.format(LocaleText.locale(getContext()),minimum==1?"%d":"%02d",value);}
    void stopMotion(){pendingValue=null;if(animator!=null){animator.cancel();animator=null;}surface.scroller.forceFinished(true);surface.flinging=false;}
    void animateTo(float target,int duration){
        stopMotion();if(!ValueAnimator.areAnimatorsEnabled()){position=target;settled();return;}
        target=clamp(target);animator=ValueAnimator.ofFloat(position,target);animator.setDuration(duration);animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(a->{position=(float)a.getAnimatedValue();surface.invalidate();});
        animator.addListener(new android.animation.AnimatorListenerAdapter(){boolean canceled;@Override public void onAnimationCancel(android.animation.Animator a){canceled=true;}@Override public void onAnimationEnd(android.animation.Animator a){if(!canceled)settled();}});animator.start();
    }
    void settled(){pendingValue=null;position=wrap(Math.round(position));surface.invalidate();surface.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);}
    void setValue(int value,boolean animate){
        if(value<minimum||value>maximum)throw new IllegalArgumentException("Time component out of range");
        editing=false;editor.clearFocus();editor.setVisibility(GONE);clearError();surface.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        float delta=value-selected();int count=maximum-minimum+1;if(labels==null){if(delta>count/2f)delta-=count;if(delta<-count/2f)delta+=count;}
        if(animate){animateTo(Math.round(position)+delta,280);pendingValue=value;}else{stopMotion();position=value;surface.invalidate();}
    }
    int getValue(){finishEditing();Integer target=pendingValue;stopMotion();position=target==null?Math.round(position):target;settled();return selected();}
    void beginEditing(){
        if(!beforeEdit.getAsBoolean())return;stopMotion();position=Math.round(position);editing=true;clearError();populating=true;editor.setText(format(selected()));populating=false;editor.setVisibility(VISIBLE);surface.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);editor.requestFocus();editor.selectAll();surface.invalidate();
        editor.post(()->getContext().getSystemService(InputMethodManager.class).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT));
    }
    void finishEditing(){
        if(!editing)return;String value=editor.getText().toString().trim();int parsed=-1;try{parsed=Integer.parseInt(value);}catch(NumberFormatException ignored){}
        if(parsed<minimum||parsed>maximum){String message=getContext().getString(R.string.time_range,minimum,maximum);showError(message);editor.requestFocus();throw new OutOfRange(message);}
        position=parsed;editing=false;clearError();editor.clearFocus();editor.setVisibility(GONE);surface.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);settled();
    }
    /** Gold digits, a short shake and the valid range in place of the next row, in the app's own style instead of the red system popup. */
    void showError(String message){editor.setTextColor(warning);hint.setVisibility(VISIBLE);surface.invalidate();android.animation.ObjectAnimator.ofFloat(editor,"translationX",0,dp(8),-dp(8),dp(5),-dp(5),0).setDuration(320).start();announceForAccessibility(message);}
    void clearError(){editor.setTextColor(foreground);hint.setVisibility(GONE);surface.invalidate();}
    void hideKeyboard(){getContext().getSystemService(InputMethodManager.class).hideSoftInputFromWindow(getWindowToken(),0);}
    @Override protected void onDetachedFromWindow(){stopMotion();super.onDetachedFromWindow();}
    final class Wheel extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final OverScroller scroller;VelocityTracker velocity;float downY,lastY;boolean dragged,flinging;int previous;
        Wheel(Context c){super(c);scroller=new OverScroller(c);setFocusable(true);setClickable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));paint.setFontFeatureSettings("tnum");}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float center=getHeight()/2f;paint.setColor(highlight);paint.setAlpha(255);canvas.drawRoundRect(dp(2),center-rowHeight/2,getWidth()-dp(2),center+rowHeight/2,dp(16),dp(16),paint);
            int nearest=Math.round(position);for(int i=nearest-2;i<=nearest+2;i++){
                float distance=i-position,y=center+distance*rowHeight;if(editing&&(i==nearest||hint.getVisibility()==VISIBLE))continue;if(labels!=null&&(i<minimum||i>maximum))continue;
                float emphasis=Math.max(0,1-Math.abs(distance));paint.setTextSize(textSize*(.64f+.36f*emphasis));String number=format(wrap(i));float width=paint.measureText(number);if(width>getWidth()-dp(8))paint.setTextSize(paint.getTextSize()*(getWidth()-dp(8))/width);
                paint.setColor(emphasis>.5f?foreground:muted);paint.setAlpha(Math.round(255*Math.max(0,1-Math.abs(distance)*.43f)));Paint.FontMetrics metrics=paint.getFontMetrics();canvas.drawText(number,getWidth()/2f,y-(metrics.ascent+metrics.descent)/2,paint);
            }paint.setAlpha(255);
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            if(editing&&event.getActionMasked()==MotionEvent.ACTION_DOWN){try{finishEditing();hideKeyboard();}catch(IllegalArgumentException e){return false;}}switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:if(!beforeEdit.getAsBoolean())return false;stopMotion();getParent().requestDisallowInterceptTouchEvent(true);downY=lastY=event.getY();dragged=false;previous=selected();velocity=VelocityTracker.obtain();velocity.addMovement(event);return true;
                case MotionEvent.ACTION_MOVE:velocity.addMovement(event);float y=event.getY();if(Math.abs(y-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop())dragged=true;if(dragged){position-=(y-lastY)/rowHeight;if(labels!=null)position=Math.max(minimum-.4f,Math.min(maximum+.4f,position));int value=selected();if(value!=previous){performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);previous=value;}invalidate();}lastY=y;return true;
                case MotionEvent.ACTION_UP:
                    getParent().requestDisallowInterceptTouchEvent(false);velocity.addMovement(event);velocity.computeCurrentVelocity(1000,5000);float speed=-velocity.getYVelocity();velocity.recycle();velocity=null;
                    if(!dragged){float relative=(event.getY()-getHeight()/2f)/rowHeight;if(Math.abs(relative)<.5f)performClick();else animateTo(Math.round(position)+(relative>0?1:-1),180);}
                    else if(Math.abs(speed)>ViewConfiguration.get(getContext()).getScaledMinimumFlingVelocity()){flinging=true;scroller.fling(0,Math.round(position*rowHeight),0,Math.round(speed),0,0,labels!=null?Math.round(minimum*rowHeight):-10000000,labels!=null?Math.round(maximum*rowHeight):10000000);postInvalidateOnAnimation();}else animateTo(Math.round(position),160);return true;
                case MotionEvent.ACTION_CANCEL:getParent().requestDisallowInterceptTouchEvent(false);if(velocity!=null){velocity.recycle();velocity=null;}animateTo(Math.round(position),160);return true;
            }return super.onTouchEvent(event);
        }
        @Override public void computeScroll(){if(scroller.computeScrollOffset()){position=scroller.getCurrY()/rowHeight;postInvalidateOnAnimation();}else if(flinging){flinging=false;animateTo(Math.round(position),160);}}
        @Override public boolean performClick(){super.performClick();if(labels!=null)animateTo(selected()==minimum?maximum:minimum,180);else beginEditing();return true;}
        @Override public boolean onKeyDown(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){animateTo(Math.round(position)+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1),160);return true;}if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)return performClick();return super.onKeyDown(key,event);}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(NumberPicker.class.getName());info.setContentDescription(label+", "+format(selected()));info.setScrollable(true);info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,minimum,maximum,selected()));info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);}
        @Override public boolean performAccessibilityAction(int action,Bundle args){if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){animateTo(Math.round(position)+(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1),160);return true;}return super.performAccessibilityAction(action,args);}
    }
}
