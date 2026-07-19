package com.crm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TaskTest {
    @Test void acceptsSchedulesInsideOneCalendarDay() {
        Task firstMinute = new Task("First", "", 0, 1, "Blue");
        Task lastMinute = new Task("Last", "", 1439, 1, "Blue");
        Task untilMidnight = new Task("Late", "", 1380, 60, "Blue");

        assertEquals(1, firstMinute.getDuration());
        assertEquals(1439, lastMinute.getStartMin());
        assertEquals(1440, untilMidnight.getStartMin() + untilMidnight.getDuration());
    }

    @Test void rejectsSchedulesOutsideOneCalendarDay() {
        assertThrows(IllegalArgumentException.class, () -> new Task("Bad", "", -1, 5, "Blue"));
        assertThrows(IllegalArgumentException.class, () -> new Task("Bad", "", 1440, 5, "Blue"));
        assertThrows(IllegalArgumentException.class, () -> new Task("Bad", "", 60, 0, "Blue"));
        assertThrows(IllegalArgumentException.class, () -> new Task("Bad", "", 1439, 2, "Blue"));
        assertThrows(IllegalArgumentException.class, () -> new Task("Bad", "", 1200, 300, "Blue"));
    }

    @Test void rejectedDragOrResizeLeavesThePreviousScheduleUntouched() {
        Task task = new Task("Safe", "", 600, 60, "Blue");

        assertThrows(IllegalArgumentException.class, () -> task.setStartMin(1400));
        assertThrows(IllegalArgumentException.class, () -> task.setDuration(900));

        assertEquals(600, task.getStartMin());
        assertEquals(60, task.getDuration());
    }
}
