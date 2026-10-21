package com.clockprime.app;

import java.util.LinkedHashSet;
import java.util.Set;

/** Empty string means Ungrouped; null means no selection session. */
final class AlarmSelection {
    String group;
    final Set<Integer> ids=new LinkedHashSet<>();
    boolean accepts(String candidate){return group==null||group.equals(candidate);}
    boolean toggle(String candidate,int id){if(!accepts(candidate))return false;if(group==null)group=candidate;if(!ids.add(id))ids.remove(id);return true;}
    void clear(){group=null;ids.clear();}
}
