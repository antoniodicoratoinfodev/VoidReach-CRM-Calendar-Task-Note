package com.crm.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.crm.model.Contact;
import com.crm.model.CrmDataSnapshot;
import com.crm.model.Task;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalCrmDataRepositoryTest {
    @TempDir Path directory;

    @Test void loadsValidRecordsAndQuarantinesOnlyCorruptOnes() throws Exception {
        LocalCrmDataRepository repository = new LocalCrmDataRepository(directory);
        LocalDate date = LocalDate.of(2026, 7, 11);
        Contact validContact = new Contact("contact-valid", "Ada", "VoidReach", "CTO", "ada@example.com", "1", "today", "vip", "valid");
        Contact corruptContact = new Contact("contact-corrupt", "Bob", "VoidReach", "CEO", "bob@example.com", "2", "today", "lead", "corrupt me");
        Task validTask = new Task("task-valid", "Call", "Valid", 540, 30, "#112233");
        validTask.setCompleted(true);
        Task corruptTask = new Task("task-corrupt", "Meeting", "Corrupt me", 600, 45, "#445566");
        Task invalidScheduleTask = new Task("task-invalid-schedule", "Late", "Corrupt schedule", 1200, 30, "#778899");
        Map<LocalDate, List<Task>> tasks = new LinkedHashMap<>();
        tasks.put(date, new ArrayList<>(List.of(validTask, corruptTask, invalidScheduleTask)));
        repository.saveForUser("account-1", new CrmDataSnapshot(
                List.of(validContact, corruptContact), tasks, date, "Week", 1.5));

        Path file = directory.resolve("account-1.properties");
        Properties stored = load(file);
        stored.setProperty("contacts.count", "not-a-number");
        stored.setProperty("contact.1.email", "%%%not-base64%%%");
        stored.setProperty("task.1.date", encode("not-a-date"));
        stored.setProperty("task.2.startMin", encode("1438"));
        stored.setProperty("task.2.duration", encode("30"));
        stored.setProperty("calendar.zoom", encode("NaN"));
        storeDirectly(file, stored);

        CrmDataSnapshot loaded = repository.loadForUser("account-1");

        assertEquals(1, loaded.contacts().size());
        assertEquals("contact-valid", loaded.contacts().getFirst().getId());
        assertEquals(1, loaded.tasksByDate().get(date).size());
        assertEquals("task-valid", loaded.tasksByDate().get(date).getFirst().getId());
        assertTrue(loaded.tasksByDate().get(date).getFirst().isCompleted());
        assertEquals(date, loaded.selectedDate());
        assertEquals("Week", loaded.calendarViewMode());
        assertEquals(1.0, loaded.calendarZoom());

        Path quarantine = directory.resolve("account-1.properties.corrupt.properties");
        assertTrue(Files.isRegularFile(quarantine));
        assertEquals("5", load(quarantine).getProperty("records.count"));
    }

    private static Properties load(Path file) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
        return properties;
    }

    private static void storeDirectly(Path file, Properties properties) throws Exception {
        try (OutputStream output = Files.newOutputStream(file)) { properties.store(output, "test corruption"); }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
