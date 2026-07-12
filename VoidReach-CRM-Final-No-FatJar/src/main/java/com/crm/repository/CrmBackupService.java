package com.crm.repository;

import com.crm.model.CrmDataSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Creates three rotating, password-free CRM snapshots for the active account. */
public final class CrmBackupService implements AutoCloseable {
    static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(2);
    static final int MAX_BACKUPS_PER_USER = 3;

    private final LocalCrmDataRepository repository;
    private final Path backupRoot;
    private final Duration interval;
    private ScheduledExecutorService executor;

    public CrmBackupService() {
        this(new LocalCrmDataRepository(),
                LocalUserRepository.applicationDataDirectory().resolve("backup"), DEFAULT_INTERVAL);
    }

    CrmBackupService(LocalCrmDataRepository repository, Path backupRoot, Duration interval) {
        this.repository = Objects.requireNonNull(repository);
        this.backupRoot = Objects.requireNonNull(backupRoot);
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("The backup interval must be positive");
        }
        this.interval = interval;
    }

    public synchronized void start(String userId) {
        close();
        if (userId == null || userId.isBlank()) return;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "voidreach-crm-backup");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        long delayMillis = interval.toMillis();
        executor.scheduleWithFixedDelay(
                () -> createBackupSafely(userId), delayMillis, delayMillis, TimeUnit.MILLISECONDS);
    }

    void createBackupNow(String userId) {
        Path source = repository.dataFile(userId);
        if (!Files.isRegularFile(source)) return;

        CrmDataSnapshot snapshot = repository.loadForUser(userId);
        Path accountDirectory = backupRoot.resolve(safeUserDirectory(userId));
        try {
            Files.createDirectories(accountDirectory);
            long sequence = nextSequence(accountDirectory);
            Path target = accountDirectory.resolve(String.format("crm-data-%013d.properties", sequence));
            repository.writeSnapshot(target, snapshot, "VoidReach CRM automatic backup for one account");
            pruneOldBackups(accountDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("The automatic backup could not be created", e);
        }
    }

    private void createBackupSafely(String userId) {
        try {
            createBackupNow(userId);
        } catch (RuntimeException ignored) {
            // Backups must never interrupt or slow down the JavaFX user interface.
        }
    }

    private long nextSequence(Path accountDirectory) throws IOException {
        long highest = 0;
        try (var files = Files.list(accountDirectory)) {
            for (Path file : files.filter(this::isBackupFile).toList()) {
                String name = file.getFileName().toString();
                try {
                    highest = Math.max(highest, Long.parseLong(name.substring("crm-data-".length(), name.length() - ".properties".length())));
                } catch (NumberFormatException ignored) {
                    // Unknown files in the user's backup folder are left untouched.
                }
            }
        }
        return Math.max(System.currentTimeMillis(), highest + 1);
    }

    private void pruneOldBackups(Path accountDirectory) throws IOException {
        List<Path> backups;
        try (var files = Files.list(accountDirectory)) {
            backups = files.filter(this::isBackupFile)
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        }
        for (int i = MAX_BACKUPS_PER_USER; i < backups.size(); i++) Files.deleteIfExists(backups.get(i));
    }

    private boolean isBackupFile(Path path) {
        String name = path.getFileName().toString();
        return Files.isRegularFile(path) && name.startsWith("crm-data-") && name.endsWith(".properties");
    }

    private String safeUserDirectory(String userId) {
        String safe = userId.replaceAll("[^A-Za-z0-9_-]", "_");
        return safe.isBlank() ? "account" : safe;
    }

    @Override public synchronized void close() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
