package com.crm.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.crm.model.Contact;
import com.crm.model.CrmDataSnapshot;
import com.crm.model.Note;
import com.crm.model.NoteFolder;
import com.crm.model.NoteFormat;
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

    @Test void exportStampsOwnerAndImportReadsItBack() {
        LocalCrmDataRepository repository = new LocalCrmDataRepository(directory);
        LocalDate date = LocalDate.of(2026, 7, 14);
        repository.saveForUser("account-1", new CrmDataSnapshot(
                List.of(new Contact("c1", "Ada", "", "", "", "", "", "", "")), Map.of(), date, "Day", 1.0));

        Path exported = directory.resolve("portable.properties");
        repository.exportForUser("account-1", exported, new ExportOwner("Ada@Example.test", "Ada Lovelace"));

        ImportedWorkspace imported = repository.readImport(exported);
        assertEquals("Ada@Example.test", imported.owner().email());
        assertEquals("Ada Lovelace", imported.owner().name());
        assertEquals("c1", imported.snapshot().contacts().get(0).getId());
    }

    @Test void importReadsLegacyFileWithNoOwnerStamp() {
        LocalCrmDataRepository repository = new LocalCrmDataRepository(directory);
        LocalDate date = LocalDate.of(2026, 7, 14);
        repository.saveForUser("account-1", new CrmDataSnapshot(
                List.of(new Contact("c1", "Ada", "", "", "", "", "", "", "")), Map.of(), date, "Day", 1.0));

        Path exported = directory.resolve("legacy.properties");
        repository.exportForUser("account-1", exported, null);

        ImportedWorkspace imported = repository.readImport(exported);
        assertNull(imported.owner());
        assertEquals(1, imported.snapshot().contacts().size());
    }

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

    @Test void roundTripsContactCustomFields() {
        LocalCrmDataRepository repository = new LocalCrmDataRepository(directory);
        LocalDate date = LocalDate.of(2026, 7, 14);
        Contact contact = new Contact("contact-custom", "Ada", "VoidReach", "CTO",
                "ada@example.com", "1", "today", "vip", "long description\nwith a second line");
        contact.setCustomField("Automobile", "Tesla Model 3");
        contact.setCustomField("Città", "Bari");
        repository.saveForUser("account-custom", new CrmDataSnapshot(
                List.of(contact), new LinkedHashMap<>(), List.of(), List.of(), date, "Day", 1.0,
                List.of("Automobile", "Città")));

        CrmDataSnapshot loaded = repository.loadForUser("account-custom");

        assertEquals(List.of("Automobile", "Città"), loaded.contactCustomFields());
        Contact reloaded = loaded.contacts().getFirst();
        assertEquals("Tesla Model 3", reloaded.customFieldValue("Automobile"));
        assertEquals("Bari", reloaded.customFieldValue("Città"));
        assertEquals("long description\nwith a second line", reloaded.descriptionProperty().get());
    }

    @Test void preservesNoteOrderFormatsMarkdownAndTaskLinks() {
        LocalCrmDataRepository repository = new LocalCrmDataRepository(directory);
        LocalDate date = LocalDate.of(2026, 7, 12);
        Task task = new Task("task-linked", "Ship release", "", 600, 45, "Blue");
        NoteFolder workspace = new NoteFolder("folder-workspace", "Workspace");
        NoteFolder projects = new NoteFolder("folder-projects", "Projects", workspace.getId());
        Note markdown = new Note("note-md", "Release plan", "# Plan\n\n- [ ] Ship\n[[Research]] ✨",
                NoteFormat.MARKDOWN, task.getId(), "Georgia", 22, 700, true,
                "Arial", 26, "#224466", projects.getId());
        markdown.linkTask("task-review");
        Note text = new Note("note-txt", "Research", "Plain text\nwith multiple lines",
                NoteFormat.TEXT, "");

        repository.saveForUser("notes-account", new CrmDataSnapshot(
                List.of(), Map.of(date, List.of(task)), List.of(markdown, text), List.of(workspace, projects),
                date, "Day", 1.0));

        CrmDataSnapshot loaded = repository.loadForUser("notes-account");

        assertEquals(List.of("note-md", "note-txt"), loaded.notes().stream().map(Note::getId).toList());
        assertEquals(NoteFormat.MARKDOWN, loaded.notes().getFirst().getFormat());
        assertEquals("# Plan\n\n- [ ] Ship\n[[Research]] ✨", loaded.notes().getFirst().getContent());
        assertEquals(List.of("task-linked", "task-review"), loaded.notes().getFirst().getLinkedTaskIds());
        assertEquals("Georgia", loaded.notes().getFirst().getFontFamily());
        assertEquals(22, loaded.notes().getFirst().getFontSize());
        assertEquals(700, loaded.notes().getFirst().getFontWeight());
        assertTrue(loaded.notes().getFirst().isItalic());
        assertEquals("Arial", loaded.notes().getFirst().getPreviewFontFamily());
        assertEquals(26, loaded.notes().getFirst().getPreviewFontSize());
        assertEquals("#224466", loaded.notes().getFirst().getPreviewTextColor());
        assertEquals("folder-projects", loaded.notes().getFirst().getFolderId());
        assertEquals(List.of("folder-workspace", "folder-projects"),
                loaded.noteFolders().stream().map(NoteFolder::getId).toList());
        assertEquals("folder-workspace", loaded.noteFolders().get(1).getParentFolderId());
        assertEquals("Projects", loaded.noteFolders().get(1).getName());
        assertEquals(NoteFormat.TEXT, loaded.notes().get(1).getFormat());
        assertEquals(Note.DEFAULT_FONT_SIZE, loaded.notes().get(1).getFontSize());
        assertEquals("", loaded.notes().get(1).getFolderId());
    }

    @Test void loadsTheLegacySingleTaskLinkWhenTheNewLinkListIsAbsent() throws Exception {
        LocalCrmDataRepository repository = new LocalCrmDataRepository(directory);
        Note note = new Note("note-legacy", "Legacy", "", NoteFormat.TEXT, "task-old");
        LocalDate date = LocalDate.of(2026, 7, 13);
        repository.saveForUser("legacy-links", new CrmDataSnapshot(
                List.of(), Map.of(), List.of(note), date, "Day", 1.0));

        Path file = directory.resolve("legacy-links.properties");
        Properties stored = load(file);
        stored.remove("note.0.linkedTaskIds.count");
        stored.remove("note.0.linkedTaskIds.0");
        storeDirectly(file, stored);

        assertEquals(List.of("task-old"),
                repository.loadForUser("legacy-links").notes().getFirst().getLinkedTaskIds());
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
