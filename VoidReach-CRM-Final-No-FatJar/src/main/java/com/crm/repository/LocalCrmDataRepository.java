package com.crm.repository;

import com.crm.model.Contact;
import com.crm.model.CrmDataSnapshot;
import com.crm.model.Task;
import com.crm.repository.CorruptRecordQuarantine.RejectedRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

/** One file per user: local equivalent of owner_user_id filtering in SQL. */
public class LocalCrmDataRepository implements CrmDataRepository {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_RECORDS = 100_000;
    private static final String FILE_TYPE = "voidreach.crm-data";
    private final Path dataDirectory;

    LocalCrmDataRepository(Path dataDirectory) { this.dataDirectory = dataDirectory; }
    public LocalCrmDataRepository() { this(LocalUserRepository.applicationDataDirectory().resolve("data")); }

    @Override public synchronized CrmDataSnapshot loadForUser(String userId) {
        Path file = dataFile(userId);
        Properties properties = load(file);
        List<RejectedRecord> rejected = new ArrayList<>();
        List<Contact> contacts = new ArrayList<>();
        Map<LocalDate, List<Task>> tasks = new HashMap<>();

        for (int index : recordIndexes(properties, "contact.", "contacts.count", rejected)) {
            String prefix = "contact." + index + ".";
            try {
                contacts.add(readContact(properties, prefix));
            } catch (RuntimeException failure) {
                rejected.add(CorruptRecordQuarantine.capture(properties, "contact", String.valueOf(index), prefix, failure));
            }
        }

        for (int index : recordIndexes(properties, "task.", "tasks.count", rejected)) {
            String prefix = "task." + index + ".";
            try {
                LocalDate date = LocalDate.parse(requiredValue(properties, prefix + "date"));
                Task task = new Task(
                        requiredNonBlank(properties, prefix + "id"),
                        requiredValue(properties, prefix + "title"),
                        optionalValue(properties, prefix + "description", ""),
                        requiredInteger(properties, prefix + "startMin"),
                        requiredInteger(properties, prefix + "duration"),
                        optionalValue(properties, prefix + "color", "Blue"),
                        Boolean.parseBoolean(optionalValue(properties, prefix + "completed", "false")));
                tasks.computeIfAbsent(date, ignored -> new ArrayList<>()).add(task);
            } catch (RuntimeException failure) {
                rejected.add(CorruptRecordQuarantine.capture(properties, "task", String.valueOf(index), prefix, failure));
            }
        }

        LocalDate selectedDate = preference(properties, "calendar.selectedDate", LocalDate.now(), LocalDate::parse, rejected);
        String viewMode = preference(properties, "calendar.viewMode", "Day", value -> {
            if (!"Day".equals(value) && !"Week".equals(value)) throw new IllegalArgumentException("Invalid calendar view mode");
            return value;
        }, rejected);
        double zoom = preference(properties, "calendar.zoom", 1.0, value -> {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed <= 0) throw new IllegalArgumentException("Invalid calendar zoom");
            return parsed;
        }, rejected);

        CorruptRecordQuarantine.writeBestEffort(file, rejected);
        return new CrmDataSnapshot(contacts, tasks, selectedDate, viewMode, zoom);
    }

    @Override public synchronized void saveForUser(String userId, CrmDataSnapshot data) {
        writeSnapshot(dataFile(userId), data, "VoidReach CRM data for one account");
    }

    synchronized void writeSnapshot(Path target, CrmDataSnapshot data, String comment) {
        Properties properties = serialize(data);
        try {
            AtomicPropertiesStore.store(target, properties, comment);
        } catch (IOException e) {
            throw new IllegalStateException("Local data could not be saved", e);
        }
    }

    private Properties serialize(CrmDataSnapshot data) {
        Properties properties = new Properties();
        properties.setProperty(AtomicPropertiesStore.SCHEMA_VERSION_KEY, String.valueOf(SCHEMA_VERSION));
        properties.setProperty(AtomicPropertiesStore.FILE_TYPE_KEY, FILE_TYPE);
        properties.setProperty("contacts.count", String.valueOf(data.contacts().size()));
        for (int i = 0; i < data.contacts().size(); i++) {
            Contact contact = data.contacts().get(i);
            String prefix = "contact." + i + ".";
            put(properties, prefix + "id", contact.getId());
            put(properties, prefix + "name", contact.nameProperty().get());
            put(properties, prefix + "company", contact.companyProperty().get());
            put(properties, prefix + "title", contact.titleProperty().get());
            put(properties, prefix + "email", contact.emailProperty().get());
            put(properties, prefix + "phone", contact.phoneProperty().get());
            put(properties, prefix + "lastInteraction", contact.lastInteractionProperty().get());
            put(properties, prefix + "tags", contact.tagsProperty().get());
            put(properties, prefix + "description", contact.descriptionProperty().get());
        }

        List<Map.Entry<LocalDate, Task>> allTasks = new ArrayList<>();
        data.tasksByDate().forEach((date, tasks) -> tasks.forEach(task -> allTasks.add(Map.entry(date, task))));
        properties.setProperty("tasks.count", String.valueOf(allTasks.size()));
        for (int i = 0; i < allTasks.size(); i++) {
            LocalDate date = allTasks.get(i).getKey();
            Task task = allTasks.get(i).getValue();
            String prefix = "task." + i + ".";
            put(properties, prefix + "id", task.getId());
            put(properties, prefix + "date", date.toString());
            put(properties, prefix + "title", task.getTitle());
            put(properties, prefix + "description", task.getDescription());
            put(properties, prefix + "startMin", String.valueOf(task.getStartMin()));
            put(properties, prefix + "duration", String.valueOf(task.getDuration()));
            put(properties, prefix + "color", task.getColor());
            put(properties, prefix + "completed", String.valueOf(task.isCompleted()));
        }
        put(properties, "calendar.selectedDate", data.selectedDate().toString());
        put(properties, "calendar.viewMode", data.calendarViewMode());
        put(properties, "calendar.zoom", String.valueOf(data.calendarZoom()));
        return properties;
    }

    private Contact readContact(Properties properties, String prefix) {
        return new Contact(
                requiredNonBlank(properties, prefix + "id"),
                requiredValue(properties, prefix + "name"),
                optionalValue(properties, prefix + "company", ""),
                optionalValue(properties, prefix + "title", ""),
                optionalValue(properties, prefix + "email", ""),
                optionalValue(properties, prefix + "phone", ""),
                optionalValue(properties, prefix + "lastInteraction", ""),
                optionalValue(properties, prefix + "tags", ""),
                optionalValue(properties, prefix + "description", ""));
    }

    private SortedSet<Integer> recordIndexes(Properties properties, String prefix, String countKey, List<RejectedRecord> rejected) {
        SortedSet<Integer> indexes = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) continue;
            int separator = key.indexOf('.', prefix.length());
            if (separator < 0) continue;
            try {
                int index = Integer.parseInt(key.substring(prefix.length(), separator));
                if (index >= 0 && index < MAX_RECORDS) indexes.add(index);
            } catch (NumberFormatException ignored) {
                // The malformed key cannot identify a record and will be represented by the count diagnostic below.
            }
        }
        String rawCount = properties.getProperty(countKey);
        if (rawCount == null && indexes.isEmpty()) return indexes;
        try {
            int count = flexibleInteger(rawCount, countKey);
            if (count < 0 || count > MAX_RECORDS) throw new IllegalArgumentException("Count is out of range");
            for (int i = 0; i < count; i++) indexes.add(i);
        } catch (RuntimeException failure) {
            rejected.add(CorruptRecordQuarantine.singleProperty("metadata", countKey, rawCount, failure));
        }
        return indexes;
    }

    private <T> T preference(Properties properties, String key, T fallback, Parser<T> parser, List<RejectedRecord> rejected) {
        if (!properties.containsKey(key)) return fallback;
        try {
            return parser.parse(requiredValue(properties, key));
        } catch (RuntimeException failure) {
            rejected.add(CorruptRecordQuarantine.singleProperty("preference", key, properties.getProperty(key), failure));
            return fallback;
        }
    }

    private Properties load(Path file) {
        try {
            return AtomicPropertiesStore.load(file, FILE_TYPE, SCHEMA_VERSION,
                    properties -> properties.containsKey("contacts.count")
                            || properties.containsKey("tasks.count")
                            || properties.containsKey("calendar.selectedDate"));
        } catch (IOException e) {
            throw new IllegalStateException("Local data could not be read", e);
        }
    }

    Path dataFile(String userId) { return dataDirectory.resolve(userId + ".properties"); }

    private int requiredInteger(Properties properties, String key) { return flexibleInteger(properties.getProperty(key), key); }

    private int flexibleInteger(String rawValue, String key) {
        if (rawValue == null) throw new IllegalArgumentException("Missing property: " + key);
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException plainFailure) {
            try {
                return Integer.parseInt(decode(rawValue, key));
            } catch (RuntimeException encodedFailure) {
                encodedFailure.addSuppressed(plainFailure);
                throw encodedFailure;
            }
        }
    }

    private String requiredNonBlank(Properties properties, String key) {
        String value = requiredValue(properties, key);
        if (value.isBlank()) throw new IllegalArgumentException("Empty property: " + key);
        return value;
    }

    private String requiredValue(Properties properties, String key) {
        String rawValue = properties.getProperty(key);
        if (rawValue == null) throw new IllegalArgumentException("Missing property: " + key);
        return decode(rawValue, key);
    }

    private String optionalValue(Properties properties, String key, String fallback) {
        String rawValue = properties.getProperty(key);
        return rawValue == null ? fallback : decode(rawValue, key);
    }

    private String decode(String rawValue, String key) {
        try {
            return new String(Base64.getDecoder().decode(rawValue), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64: " + key, e);
        }
    }

    private void put(Properties properties, String key, String value) {
        properties.setProperty(key, Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
    }

    @FunctionalInterface
    private interface Parser<T> { T parse(String value); }
}
