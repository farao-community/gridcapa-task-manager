package com.farao_community.farao.gridcapa.task_manager.app.entities.comparators;

import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;

import java.util.Comparator;

public class ReverseEventComparator implements Comparator<ProcessEvent> {

    @Override
    public int compare(ProcessEvent o1, ProcessEvent o2) {
        return o2.compareTo(o1);
    }
}
