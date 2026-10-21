package com.clockprime.app;

import android.app.AlertDialog;
import android.view.Gravity;
import android.widget.*;
import java.util.*;
import org.json.JSONArray;

/** Group choice is part of the alarm draft; canceling the editor creates nothing. */
@android.annotation.SuppressLint("ViewConstructor") // Constructed in code with required state; never inflated from XML.
final class AlarmGroupField extends LinearLayout {
    final MainActivity activity;final TextView value;String selected;boolean newGroup;
    AlertDialog choices,nameDialog;EditText newName;
    AlarmGroupField(MainActivity activity,String selected){
        super(activity);this.activity=activity;this.selected=selected==null?"":selected;setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(activity.dp(12),activity.dp(10),activity.dp(12),activity.dp(10));setMinimumHeight(activity.dp(64));setBackground(activity.ripple(activity.SOFT,16));setFocusable(true);setTag("alarm-group");
        addView(activity.icon("group",activity.MINT,22));LinearLayout words=activity.column();words.setPadding(activity.dp(12),0,activity.dp(8),0);words.addView(activity.text(activity.tr(R.string.group_label),11,activity.MUTED));value=activity.text("",16,activity.TEXT);words.addView(value);addView(words,new LayoutParams(0,-2,1));addView(activity.icon("down",activity.MUTED,18));refresh();setOnClickListener(v->choose());
    }
    void refresh(){String label=selected.isEmpty()?activity.tr(R.string.no_group):selected;value.setText(label);setContentDescription(activity.tr(R.string.group_choice,label));}
    void choose(){
        List<String> groups=new ArrayList<>();groups.add("");JSONArray stored=new ClockStore(activity).list("groups");for(int i=0;i<stored.length();i++)groups.add(stored.optString(i));if(newGroup&&!groups.contains(selected))groups.add(selected);
        List<String> labels=new ArrayList<>();for(String group:groups)labels.add(group.isEmpty()?activity.tr(R.string.no_group):group);labels.add(activity.tr(R.string.create_group));
        choices=new AlertDialog.Builder(activity).setTitle(activity.tr(R.string.group_label)).setSingleChoiceItems(labels.toArray(new String[0]),groups.indexOf(selected),(d,which)->{d.dismiss();if(which==groups.size())promptNew();else{selected=groups.get(which);newGroup=true;for(int i=0;i<stored.length();i++)if(stored.optString(i).equals(selected))newGroup=false;if(selected.isEmpty())newGroup=false;refresh();}}).setNegativeButton(activity.tr(R.string.cancel),null).show();
    }
    void promptNew(){
        LinearLayout wrapper=activity.column();wrapper.setPadding(activity.dp(20),activity.dp(8),activity.dp(20),0);newName=activity.field(activity.tr(R.string.group_name),"");newName.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});wrapper.addView(newName);
        nameDialog=new AlertDialog.Builder(activity).setTitle(activity.tr(R.string.create_group)).setView(wrapper).setNegativeButton(activity.tr(R.string.cancel),null).setPositiveButton(activity.tr(R.string.create),null).create();
        nameDialog.setOnShowListener(v->{nameDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{selectNew(newName.getText().toString());nameDialog.dismiss();}catch(IllegalArgumentException e){newName.setError(e.getMessage());}});newName.requestFocus();nameDialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);});nameDialog.show();
    }
    void selectNew(String name){String clean=name.trim();if(clean.isEmpty())throw new IllegalArgumentException(activity.tr(R.string.enter_name));JSONArray existing=new ClockStore(activity).list("groups");for(int i=0;i<existing.length();i++)if(existing.optString(i).equalsIgnoreCase(clean))throw new IllegalArgumentException(activity.tr(R.string.group_exists));selected=clean;newGroup=true;refresh();}
    String saveInto(ClockStore store){
        if(newGroup&&!selected.isEmpty()){JSONArray groups=store.list("groups");for(int i=0;i<groups.length();i++)if(groups.optString(i).equalsIgnoreCase(selected))return groups.optString(i);groups.put(selected);store.collapse(selected,false);}
        return selected;
    }
}
