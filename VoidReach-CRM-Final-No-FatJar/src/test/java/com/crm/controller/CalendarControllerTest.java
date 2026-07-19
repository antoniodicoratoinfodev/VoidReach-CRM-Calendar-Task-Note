package com.crm.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CalendarControllerTest {
    @Test
    void miniCalendarDaysUseAllAvailableWidthAtNormalSizes() {
        assertEquals(29.0, CalendarController.miniCalendarCellSize(221, 3), 0.0001);
        assertEquals(34.0, CalendarController.miniCalendarCellSize(256, 3), 0.0001);
    }

    @Test
    void miniCalendarDaysStayInsideNarrowGridsAndStopGrowingAtTheVisualMaximum() {
        assertEquals(10.0, CalendarController.miniCalendarCellSize(88, 3), 0.0001);
        assertEquals(40.0, CalendarController.miniCalendarCellSize(400, 3), 0.0001);
    }

    @Test
    void timelineUsesTheActualTimeColumnWidth() {
        assertEquals(928.0, CalendarController.timelineWidthFor(1000, 72), 0.0001);
    }

    @Test
    void taskEntryStaysInsideItsDayMargins() {
        double dayWidth = 140;
        double margin = 5;
        double left = 6 * dayWidth + margin;
        double right = left + CalendarController.taskEntryWidth(dayWidth, margin);

        assertEquals(130.0, CalendarController.taskEntryWidth(dayWidth, margin), 0.0001);
        assertEquals(7 * dayWidth - margin, right, 0.0001);
    }

    @Test
    void taskHeightAlwaysMatchesItsScheduledDuration() {
        assertEquals(3.75, CalendarController.taskEntryHeight(5, 0.75), 0.0001);
        assertEquals(60.0, CalendarController.taskEntryHeight(60, 1.0), 0.0001);
    }

    @Test
    void taskTitleFontShrinksWithShortTasksAndNeverExceedsItsNormalSize() {
        assertEquals(4.0, CalendarController.taskTitleFontSize(3.75), 0.0001);
        assertEquals(7.5, CalendarController.taskTitleFontSize(15.0), 0.0001);
        assertEquals(12.0, CalendarController.taskTitleFontSize(24.0), 0.0001);
        assertEquals(12.0, CalendarController.taskTitleFontSize(180.0), 0.0001);
    }

    @Test
    void resizingUsesTheWholeDragAndKeepsDurationWithinValidBounds() {
        assertEquals(10, CalendarController.taskDurationAfterResize(5, 15, 3, 120));
        assertEquals(5, CalendarController.taskDurationAfterResize(30, -100, 1, 120));
        assertEquals(20, CalendarController.taskDurationAfterResize(15, 100, 1, 20));
    }

    @Test
    void resizeHitAreaIncludesTheBottomEdgeAndSpaceImmediatelyBelowIt() {
        org.junit.jupiter.api.Assertions.assertTrue(
                CalendarController.taskResizeHit(50, 106, 10, 100, 100, 5));
        org.junit.jupiter.api.Assertions.assertFalse(
                CalendarController.taskResizeHit(50, 115, 10, 100, 100, 5));
        org.junit.jupiter.api.Assertions.assertFalse(
                CalendarController.taskResizeHit(120, 106, 10, 100, 100, 5));
    }
}
