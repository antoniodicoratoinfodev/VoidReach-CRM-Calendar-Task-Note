package com.crm.repository;

import com.crm.model.CrmDataSnapshot;

import java.nio.file.Path;

/** Owner-scoped boundary; a JDBC implementation will filter every query by owner_user_id. */
public interface CrmDataRepository {
    CrmDataSnapshot loadForUser(String userId);
    void saveForUser(String userId, CrmDataSnapshot data);

    /** Writes the account's saved workspace to [target] as a portable file stamped with [owner]. */
    void exportForUser(String userId, Path target, ExportOwner owner);

    /** Parses a portable file without persisting it, so the caller can vet the owner first. */
    ImportedWorkspace readImport(Path source);
}
