package com.crm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.crm.model.UserAccount;
import com.crm.repository.UserRepository;
import com.crm.service.AvatarImageProcessor.CropSelection;
import com.crm.service.AvatarImageProcessor.Source;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;
import javax.imageio.ImageIO;
import javafx.scene.image.Image;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AvatarServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesVersionedMasterAndBuildsExactDisplayRendition() throws Exception {
        Path sourcePath = temporaryDirectory.resolve("source.png");
        BufferedImage input = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < input.getHeight(); y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                int red = x * 255 / (input.getWidth() - 1);
                int green = y * 255 / (input.getHeight() - 1);
                input.setRGB(x, y, 0xff000000 | (red << 16) | (green << 8) | 96);
            }
        }
        ImageIO.write(input, "png", sourcePath.toFile());

        RecordingRepository repository = new RecordingRepository();
        AvatarImageProcessor processor = new AvatarImageProcessor();
        Path avatarDirectory = temporaryDirectory.resolve("avatars");
        AvatarService service = new AvatarService(repository, avatarDirectory, processor);
        UserAccount user = new UserAccount();
        user.setId("account-1");
        Source source = service.prepareSource(sourcePath);

        service.updateAvatar(user, source, new CropSelection(300, 300, 1));

        String firstFileName = user.getAvatarFileName();
        Path firstAvatar = service.getAvatarPath(user);
        assertNotNull(firstFileName);
        assertTrue(firstFileName.startsWith("account-1-"));
        assertTrue(java.nio.file.Files.isRegularFile(firstAvatar));
        assertEquals(600, ImageIO.read(firstAvatar.toFile()).getWidth());
        assertEquals(1, repository.saveCount);

        Image oneX = service.loadAvatar(user, 56);
        Image twoX = service.loadAvatar(user, 112);
        assertNotNull(oneX);
        assertNotNull(twoX);
        assertEquals(56, (int) oneX.getWidth());
        assertEquals(112, (int) twoX.getWidth());

        service.updateAvatar(user, source, new CropSelection(300, 300, 1));

        assertNotEquals(firstFileName, user.getAvatarFileName());
        assertFalse(java.nio.file.Files.exists(firstAvatar));
        assertEquals(2, repository.saveCount);
    }

    @Test
    void repositoryFailureKeepsPreviouslyStoredAvatar() throws Exception {
        Path sourcePath = temporaryDirectory.resolve("source.png");
        BufferedImage input = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        assertTrue(ImageIO.write(input, "png", sourcePath.toFile()));

        RecordingRepository repository = new RecordingRepository();
        AvatarImageProcessor processor = new AvatarImageProcessor();
        Path avatarDirectory = temporaryDirectory.resolve("avatars");
        AvatarService service = new AvatarService(repository, avatarDirectory, processor);
        UserAccount user = new UserAccount();
        user.setId("account-rollback");
        Source source = service.prepareSource(sourcePath);
        CropSelection crop = new CropSelection(150, 150, 1);
        service.updateAvatar(user, source, crop);
        String previousFileName = user.getAvatarFileName();
        Path previousPath = service.getAvatarPath(user);

        repository.failSaves = true;
        assertThrows(IllegalStateException.class,
                () -> service.updateAvatar(user, source, crop));

        assertEquals(previousFileName, user.getAvatarFileName());
        assertTrue(java.nio.file.Files.isRegularFile(previousPath));
        try (var files = java.nio.file.Files.list(avatarDirectory)) {
            assertEquals(1, files.count());
        }
    }

    private static final class RecordingRepository implements UserRepository {
        int saveCount;
        boolean failSaves;

        @Override
        public Optional<UserAccount> findByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public void save(UserAccount user) {
            if (failSaves) throw new IllegalStateException("Repository unavailable");
            saveCount++;
        }
    }
}
