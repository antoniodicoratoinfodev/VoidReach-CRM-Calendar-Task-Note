package com.crm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.crm.service.AvatarImageProcessor.CropSelection;
import com.crm.service.AvatarImageProcessor.Source;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class AvatarLargeImageIT {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(90)
    void processesTwentyThousandPixelImageWithinLimitedHeap() throws Exception {
        Path sourcePath = temporaryDirectory.resolve("20000-square.png");
        BufferedImage compactSource = new BufferedImage(
                20_000, 20_000, BufferedImage.TYPE_BYTE_BINARY);
        assertTrue(ImageIO.write(compactSource, "png", sourcePath.toFile()));
        compactSource = null;
        System.gc();

        assertTrue(Files.size(sourcePath) < AvatarImageProcessor.MAX_UPLOAD_BYTES);

        AvatarImageProcessor processor = new AvatarImageProcessor();
        Source source = processor.prepareSource(sourcePath);
        BufferedImage master = processor.createMaster(
                source, new CropSelection(10_000, 10_000, 1));

        assertEquals(20_000, source.width());
        assertEquals(20_000, source.height());
        assertTrue(source.preview().getWidth() <= AvatarImageProcessor.PREVIEW_MAX_EDGE);
        assertTrue(source.preview().getHeight() <= AvatarImageProcessor.PREVIEW_MAX_EDGE);
        assertEquals(AvatarImageProcessor.MASTER_MAX_EDGE, master.getWidth());
        assertEquals(AvatarImageProcessor.MASTER_MAX_EDGE, master.getHeight());
    }
}
