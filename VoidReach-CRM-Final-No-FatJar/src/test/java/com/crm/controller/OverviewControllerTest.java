package com.crm.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.crm.model.Task;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OverviewControllerTest {
    @Test
    void countsOnlyActivitiesInsideTheInclusiveSevenDayWindow() {
        LocalDate today = LocalDate.of(2026, 7, 12);
        Map<LocalDate, List<Task>> tasks = Map.of(
                today.minusDays(1), List.of(task()),
                today, List.of(task(), task()),
                today.plusDays(6), List.of(task()),
                today.plusDays(7), List.of(task()));

        assertEquals(3, OverviewController.countBetween(tasks, today, today.plusDays(6)));
    }

    @Test
    void formatsPlannedDurationWithoutLosingRemainingMinutes() {
        assertEquals("0 h", OverviewController.formatHours(0));
        assertEquals("2 h", OverviewController.formatHours(120));
        assertEquals("2 h 35 m", OverviewController.formatHours(155));
    }

    private static Task task() {
        return new Task("Attività", "", 9 * 60, 30, "Blue");
    }
}
