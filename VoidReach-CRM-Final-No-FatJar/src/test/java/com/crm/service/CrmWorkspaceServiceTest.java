package com.crm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.crm.model.Contact;
import com.crm.model.CrmDataSnapshot;
import com.crm.model.UserAccount;
import com.crm.repository.CrmBackupService;
import com.crm.repository.CrmDataRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CrmWorkspaceServiceTest {
    @Test void debouncePersistsOnlyTheNewestDetachedSnapshot() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        CrmWorkspaceService service = new CrmWorkspaceService(repository, new CrmBackupService());
        try {
            service.open(account());
            CountDownLatch saved = new CountDownLatch(1);
            service.requestSave(snapshot("first"), state -> {
                if (state == CrmWorkspaceService.SaveState.SAVED) saved.countDown();
            });
            service.requestSave(snapshot("latest"), state -> {
                if (state == CrmWorkspaceService.SaveState.SAVED) saved.countDown();
            });

            assertTrue(saved.await(2, TimeUnit.SECONDS));
            assertEquals(1, repository.saveCount.get());
            assertEquals("latest", repository.lastSnapshot.get().contacts().getFirst().nameProperty().get());
        } finally {
            service.closeAsync().get(2, TimeUnit.SECONDS);
        }
    }

    private static UserAccount account() {
        UserAccount user = new UserAccount();
        user.setId("account-1");
        return user;
    }

    private static CrmDataSnapshot snapshot(String name) {
        return CrmDataSnapshot.detachedCopyOf(List.of(new Contact("id-1", name, "", "", "", "", "", "", "")),
                Map.of(), LocalDate.of(2026, 7, 12), "Day", 1.0);
    }

    private static final class RecordingRepository implements CrmDataRepository {
        private final AtomicInteger saveCount = new AtomicInteger();
        private final AtomicReference<CrmDataSnapshot> lastSnapshot = new AtomicReference<>();

        @Override public CrmDataSnapshot loadForUser(String userId) { return snapshot("loaded"); }

        @Override public void saveForUser(String userId, CrmDataSnapshot data) {
            saveCount.incrementAndGet();
            lastSnapshot.set(data);
        }
    }
}
