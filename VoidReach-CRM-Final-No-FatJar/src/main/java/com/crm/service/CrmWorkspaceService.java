package com.crm.service;

import com.crm.model.CrmDataSnapshot;
import com.crm.model.UserAccount;
import com.crm.repository.CrmBackupService;
import com.crm.repository.CrmDataRepository;
import com.crm.repository.ExportOwner;
import com.crm.repository.ImportedWorkspace;
import com.crm.repository.LocalCrmDataRepository;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Coordinates per-account CRM persistence and rotating backups. */
public final class CrmWorkspaceService implements AutoCloseable {
    private static final long SAVE_DEBOUNCE_MILLIS = 400;
    private final CrmDataRepository repository;
    private final CrmBackupService backupService;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "voidreach-crm-io");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "voidreach-crm-save-debounce");
        thread.setDaemon(true);
        return thread;
    });
    private final Object pendingSaveLock = new Object();
    private UserAccount currentUser;
    private CrmDataSnapshot pendingSnapshot;
    private Consumer<SaveState> pendingListener;
    private ScheduledFuture<?> pendingSave;

    public CrmWorkspaceService() {
        this(new LocalCrmDataRepository(), new CrmBackupService());
    }

    CrmWorkspaceService(CrmDataRepository repository, CrmBackupService backupService) {
        this.repository = Objects.requireNonNull(repository);
        this.backupService = Objects.requireNonNull(backupService);
    }

    public CrmDataSnapshot open(UserAccount user) {
        close();
        currentUser = Objects.requireNonNull(user);
        try {
            return repository.loadForUser(user.getId());
        } finally {
            backupService.start(user.getId());
        }
    }

    public void save(CrmDataSnapshot snapshot) {
        if (currentUser == null) return;
        repository.saveForUser(currentUser.getId(), Objects.requireNonNull(snapshot));
    }

    /** Exports the open account's saved workspace to [target], stamped with that account's identity. */
    public void exportCurrentUser(Path target) {
        UserAccount user = currentUser;
        if (user == null) throw new IllegalStateException("No account is open.");
        repository.exportForUser(user.getId(), Objects.requireNonNull(target),
                new ExportOwner(user.getEmail(), user.getFullName()));
    }

    /** Reads a portable file without committing it, so the caller can vet the owning account first. */
    public ImportedWorkspace readImport(Path source) {
        return repository.readImport(Objects.requireNonNull(source));
    }

    public CompletableFuture<CrmDataSnapshot> openAsync(UserAccount user) {
        return CompletableFuture.supplyAsync(() -> open(user), ioExecutor);
    }

    /** Queues only the newest detached snapshot and serializes disk writes on one I/O thread. */
    public void requestSave(CrmDataSnapshot snapshot, Consumer<SaveState> listener) {
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(listener);
        synchronized (pendingSaveLock) {
            pendingSnapshot = snapshot;
            pendingListener = listener;
            if (pendingSave != null) pendingSave.cancel(false);
            pendingSave = debounceExecutor.schedule(this::enqueuePendingSave,
                    SAVE_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    private void enqueuePendingSave() {
        CrmDataSnapshot snapshot;
        Consumer<SaveState> listener;
        synchronized (pendingSaveLock) {
            snapshot = pendingSnapshot;
            listener = pendingListener;
            pendingSnapshot = null;
            pendingListener = null;
            pendingSave = null;
        }
        if (snapshot == null || listener == null) return;
        ioExecutor.execute(() -> saveSnapshot(snapshot, listener));
    }

    private void saveSnapshot(CrmDataSnapshot snapshot, Consumer<SaveState> listener) {
        listener.accept(SaveState.SAVING);
        try {
            save(snapshot);
            listener.accept(SaveState.SAVED);
        } catch (RuntimeException exception) {
            listener.accept(new SaveState(exception));
        }
    }

    /** Flushes the latest debounced snapshot before closing the active workspace. */
    public CompletableFuture<Void> closeAsync() {
        CrmDataSnapshot snapshot;
        Consumer<SaveState> listener;
        synchronized (pendingSaveLock) {
            if (pendingSave != null) pendingSave.cancel(false);
            pendingSave = null;
            snapshot = pendingSnapshot;
            listener = pendingListener;
            pendingSnapshot = null;
            pendingListener = null;
        }
        CrmDataSnapshot finalSnapshot = snapshot;
        Consumer<SaveState> finalListener = listener;
        return CompletableFuture.runAsync(() -> {
            try {
                if (finalSnapshot != null && finalListener != null) saveSnapshot(finalSnapshot, finalListener);
                close();
            } finally {
                debounceExecutor.shutdownNow();
                ioExecutor.shutdown();
            }
        }, ioExecutor);
    }

    @Override
    public void close() {
        backupService.close();
        currentUser = null;
    }

    public record SaveState(Throwable failure) {
        public static final SaveState SAVING = new SaveState(null);
        public static final SaveState SAVED = new SaveState(null);

        public boolean failed() { return failure != null; }
    }
}
