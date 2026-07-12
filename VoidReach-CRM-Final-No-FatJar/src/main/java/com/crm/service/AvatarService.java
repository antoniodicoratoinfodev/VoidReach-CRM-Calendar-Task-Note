package com.crm.service;

import com.crm.model.UserAccount;
import com.crm.repository.LocalUserRepository;
import com.crm.repository.UserRepository;
import com.crm.service.AvatarImageProcessor.CropSelection;
import com.crm.service.AvatarImageProcessor.Source;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import javax.imageio.ImageIO;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

/** Validates, processes, and stores one lossless avatar master per account. */
public class AvatarService {
    private final UserRepository users;
    private final Path avatars;
    private final AvatarImageProcessor processor;

    public AvatarService(UserRepository users) {
        this(users, LocalUserRepository.applicationDataDirectory().resolve("avatars"),
                new AvatarImageProcessor());
    }

    AvatarService(UserRepository users, Path avatars, AvatarImageProcessor processor) {
        this.users = users;
        this.avatars = avatars.toAbsolutePath().normalize();
        this.processor = processor;
    }

    public Source prepareSource(Path sourceFile) {
        return processor.prepareSource(sourceFile);
    }

    public void updateAvatar(UserAccount user, Source source, CropSelection selection) {
        if (user == null || user.getId() == null || user.getId().isBlank()) {
            throw new IllegalArgumentException("Account non valido");
        }

        Path temporary = null;
        Path target = null;
        String previousFileName = user.getAvatarFileName();
        try {
            BufferedImage master = processor.createMaster(source, selection);
            Files.createDirectories(avatars);

            String safeAccountId = user.getId().replaceAll("[^A-Za-z0-9_-]", "_");
            if (safeAccountId.length() < 3) safeAccountId = "avatar";
            String nextFileName = safeAccountId + "-" + UUID.randomUUID() + ".png";
            target = avatars.resolve(nextFileName);
            temporary = Files.createTempFile(avatars, safeAccountId + "-", ".png.tmp");
            if (!ImageIO.write(master, "png", temporary.toFile())) {
                throw new IOException("Encoder PNG non disponibile");
            }
            moveAtomically(temporary, target);
            temporary = null;

            user.setAvatarFileName(nextFileName);
            try {
                users.save(user);
            } catch (RuntimeException ex) {
                user.setAvatarFileName(previousFileName);
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ignored) {
                    // The failed version is unreferenced and can be cleaned up later.
                }
                throw ex;
            }

            Path previous = resolveStoredAvatar(previousFileName);
            if (previous != null && !previous.equals(target)) {
                try {
                    Files.deleteIfExists(previous);
                } catch (IOException ignored) {
                    // A stale version does not affect the newly persisted avatar.
                }
            }
        } catch (IOException ex) {
            if (target != null && !target.equals(resolveStoredAvatar(user.getAvatarFileName()))) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ignored) {
                    // The new file is not referenced and can be cleaned up on a later update.
                }
            }
            throw new IllegalStateException("Impossibile salvare l'immagine profilo", ex);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best-effort cleanup for an interrupted write.
                }
            }
        }
    }

    public Image loadAvatar(UserAccount user, int pixelSize) {
        BufferedImage rendition = loadAvatarRendition(user, pixelSize);
        return rendition == null ? null : SwingFXUtils.toFXImage(rendition, null);
    }

    /** Performs file decoding and resizing without constructing JavaFX image objects. */
    public BufferedImage loadAvatarRendition(UserAccount user, int pixelSize) {
        Path path = getAvatarPath(user);
        if (path == null || pixelSize <= 0 || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            BufferedImage master = ImageIO.read(path.toFile());
            if (master == null || master.getWidth() <= 0 || master.getHeight() <= 0) {
                return null;
            }
            return AvatarImageProcessor.resizeLanczos(master, pixelSize);
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    public Path getAvatarPath(UserAccount user) {
        return user == null ? null : resolveStoredAvatar(user.getAvatarFileName());
    }

    private Path resolveStoredAvatar(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        Path resolved = avatars.resolve(fileName).normalize();
        return resolved.startsWith(avatars) ? resolved : null;
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
