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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

/** Validates, processes, and stores one lossless avatar master per account. */
public class AvatarService {
    private static final int MAX_CACHED_RENDITIONS = 2;

    private final UserRepository users;
    private final Path avatars;
    private final AvatarImageProcessor processor;
    private final Map<RenditionKey, BufferedImage> renditionCache =
            new LinkedHashMap<>(MAX_CACHED_RENDITIONS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<RenditionKey, BufferedImage> eldest) {
                    return size() > MAX_CACHED_RENDITIONS;
                }
            };

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

    public synchronized void updateAvatar(UserAccount user, Source source, CropSelection selection) {
        if (user == null || user.getId() == null || user.getId().isBlank()) {
            throw new IllegalArgumentException("Invalid account");
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
                throw new IOException("PNG encoder is not available");
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
                deleteAvatarAndRenditions(previous);
            }
        } catch (IOException ex) {
            if (target != null && !target.equals(resolveStoredAvatar(user.getAvatarFileName()))) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ignored) {
                    // The new file is not referenced and can be cleaned up on a later update.
                }
            }
            throw new IllegalStateException("The profile picture could not be saved", ex);
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

    /** Builds the two navbar sizes while the startup splash is still visible. */
    public PreloadedAvatars preloadCommonRenditions(UserAccount user) {
        return new PreloadedAvatars(
                loadAvatarRendition(user, 56),
                loadAvatarRendition(user, 112));
    }

    /** Performs file decoding and resizing without constructing JavaFX image objects. */
    public synchronized BufferedImage loadAvatarRendition(UserAccount user, int pixelSize) {
        Path path = getAvatarPath(user);
        if (path == null || pixelSize <= 0 || !Files.isRegularFile(path)) {
            return null;
        }
        RenditionKey key = new RenditionKey(path, pixelSize);
        BufferedImage cached = renditionCache.get(key);
        if (cached != null) return cached;

        try {
            Path cachePath = renditionPath(path, pixelSize);
            BufferedImage diskCached = readValidRendition(cachePath, pixelSize);
            if (diskCached != null) {
                renditionCache.put(key, diskCached);
                pruneRenditions(path, cachePath);
                return diskCached;
            }

            BufferedImage master = ImageIO.read(path.toFile());
            if (master == null || master.getWidth() <= 0 || master.getHeight() <= 0) {
                return null;
            }
            BufferedImage rendition = AvatarImageProcessor.resizeLanczos(master, pixelSize);
            renditionCache.put(key, rendition);
            writeRenditionBestEffort(path, cachePath, rendition);
            return rendition;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private BufferedImage readValidRendition(Path cachePath, int pixelSize) {
        if (!Files.isRegularFile(cachePath)) return null;
        try {
            BufferedImage rendition = ImageIO.read(cachePath.toFile());
            if (rendition != null && rendition.getWidth() == pixelSize
                    && rendition.getHeight() == pixelSize) {
                return rendition;
            }
        } catch (IOException | RuntimeException ignored) {
            // A damaged derived file is disposable and will be rebuilt below.
        }
        try {
            Files.deleteIfExists(cachePath);
        } catch (IOException ignored) {
            // Failure to remove a disposable cache must not hide the avatar.
        }
        return null;
    }

    private void writeRenditionBestEffort(Path avatarPath, Path cachePath, BufferedImage rendition) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile(avatars, cachePath.getFileName().toString(), ".tmp");
            if (!ImageIO.write(rendition, "png", temporary.toFile())) return;
            moveAtomically(temporary, cachePath);
            temporary = null;
            pruneRenditions(avatarPath, cachePath);
        } catch (IOException | RuntimeException ignored) {
            // The master remains authoritative if the optional cache cannot be written.
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best-effort cleanup of a disposable cache file.
                }
            }
        }
    }

    private Path renditionPath(Path avatarPath, int pixelSize) {
        return avatarPath.resolveSibling(
                avatarPath.getFileName() + "." + pixelSize + ".rendition.png");
    }

    private void pruneRenditions(Path avatarPath, Path keepPath) {
        String cachePrefix = avatarPath.getFileName() + ".";
        try (var files = Files.list(avatars)) {
            List<Path> renditions = files.filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(cachePrefix) && name.endsWith(".rendition.png");
                    })
                    .sorted(Comparator.<Path>comparingInt(path -> path.equals(keepPath) ? 1 : 0)
                            .thenComparingLong(this::lastModified)
                            .reversed())
                    .toList();
            for (int index = MAX_CACHED_RENDITIONS; index < renditions.size(); index++) {
                Files.deleteIfExists(renditions.get(index));
            }
        } catch (IOException ignored) {
            // Cache pruning is best effort; it never affects the authoritative master.
        }
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private void deleteAvatarAndRenditions(Path avatarPath) {
        renditionCache.keySet().removeIf(key -> key.path().equals(avatarPath));
        try {
            Files.deleteIfExists(avatarPath);
        } catch (IOException ignored) {
            // A stale version does not affect the newly persisted avatar.
        }

        String cachePrefix = avatarPath.getFileName() + ".";
        try (var files = Files.list(avatars)) {
            files.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith(cachePrefix) && name.endsWith(".rendition.png");
            }).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Derived files are harmless if cleanup must be retried later.
                }
            });
        } catch (IOException ignored) {
            // Cache cleanup is best effort and must not fail an avatar update.
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

    private record RenditionKey(Path path, int pixelSize) {}

    public record PreloadedAvatars(BufferedImage oneX, BufferedImage twoX) {
        public BufferedImage closestTo(int pixelSize) {
            if (oneX == null) return twoX;
            if (twoX == null) return oneX;
            return Math.abs(pixelSize - 56) <= Math.abs(pixelSize - 112) ? oneX : twoX;
        }

        public int closestSizeTo(int pixelSize) {
            if (oneX == null && twoX != null) return 112;
            if (twoX == null && oneX != null) return 56;
            return Math.abs(pixelSize - 56) <= Math.abs(pixelSize - 112) ? 56 : 112;
        }
    }
}
