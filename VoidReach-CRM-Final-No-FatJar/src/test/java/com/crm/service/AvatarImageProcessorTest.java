package com.crm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.crm.service.AvatarImageProcessor.CropRegion;
import com.crm.service.AvatarImageProcessor.CropSelection;
import com.crm.service.AvatarImageProcessor.Source;
import com.crm.service.AvatarImageProcessor.ValidationException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AvatarImageProcessorTest {
    @TempDir
    Path temporaryDirectory;

    private final AvatarImageProcessor processor = new AvatarImageProcessor();

    @Test
    void enforcesUploadSizeBeforeDecode() {
        AvatarImageProcessor.validateFileSize(AvatarImageProcessor.MAX_UPLOAD_BYTES);

        ValidationException failure = assertThrows(ValidationException.class,
                () -> AvatarImageProcessor.validateFileSize(
                        AvatarImageProcessor.MAX_UPLOAD_BYTES + 1));

        assertEquals("La foto profilo non può superare 10 MB.", failure.getMessage());
    }

    @Test
    void acceptsOnlyDimensionsFromThreeHundredToTwentyThousand() {
        AvatarImageProcessor.validateDimensions(300, 300);
        AvatarImageProcessor.validateDimensions(300, 20_000);
        AvatarImageProcessor.validateDimensions(20_000, 20_000);

        assertThrows(ValidationException.class,
                () -> AvatarImageProcessor.validateDimensions(299, 300));
        assertThrows(ValidationException.class,
                () -> AvatarImageProcessor.validateDimensions(300, 299));
        assertThrows(ValidationException.class,
                () -> AvatarImageProcessor.validateDimensions(20_001, 300));
        assertThrows(ValidationException.class,
                () -> AvatarImageProcessor.validateDimensions(300, 20_001));
    }

    @Test
    void calculatesMemoryBoundedSubsamplingForTwentyThousandPixels() {
        assertEquals(10, AvatarImageProcessor.calculateSubsampling(20_000, 20_000, 2048));
        assertEquals(5, AvatarImageProcessor.calculateSubsampling(20_000, 20_000, 4096));
        assertEquals(1, AvatarImageProcessor.calculateSubsampling(300, 300, 2048));
    }

    @Test
    void resolvesCropInOriginalImageCoordinates() {
        assertEquals(new CropRegion(250, 0, 500),
                AvatarImageProcessor.resolveCropRegion(
                        1000, 500, new CropSelection(500, 250, 1)));
        assertEquals(new CropRegion(0, 250, 500),
                AvatarImageProcessor.resolveCropRegion(
                        500, 1000, new CropSelection(250, 500, 1)));
        assertEquals(new CropRegion(350, 150, 300),
                AvatarImageProcessor.resolveCropRegion(
                        1000, 600, new CropSelection(500, 300, 2)));
        assertEquals(new CropRegion(0, 0, 300),
                AvatarImageProcessor.resolveCropRegion(
                        1000, 600, new CropSelection(0, 0, 2)));
    }

    @Test
    void rejectsCorruptedFilesByContent() throws Exception {
        Path fakePng = temporaryDirectory.resolve("not-an-image.png");
        Files.writeString(fakePng, "not an image");

        assertThrows(ValidationException.class, () -> processor.prepareSource(fakePng));
    }

    @Test
    void preparesPreviewAndCreatesSquareMasterFromSourceRegion() throws Exception {
        Path sourcePath = temporaryDirectory.resolve("quadrants.png");
        BufferedImage sourceImage = quadrantImage(1200, 800);
        ImageIO.write(sourceImage, "png", sourcePath.toFile());

        Source source = processor.prepareSource(sourcePath);
        BufferedImage master = processor.createMaster(
                source, new CropSelection(600, 400, 1));

        assertEquals(1200, source.width());
        assertEquals(800, source.height());
        assertTrue(source.preview().getWidth() <= AvatarImageProcessor.PREVIEW_MAX_EDGE);
        assertTrue(source.preview().getHeight() <= AvatarImageProcessor.PREVIEW_MAX_EDGE);
        assertEquals(800, master.getWidth());
        assertEquals(800, master.getHeight());
        assertEquals(BufferedImage.TYPE_INT_ARGB, master.getType());
    }

    @Test
    void readsJpegByContentAndPreservesItsDimensions() throws Exception {
        Path sourcePath = temporaryDirectory.resolve("photo.jpg");
        BufferedImage jpeg = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = jpeg.createGraphics();
        graphics.setColor(new Color(24, 96, 180));
        graphics.fillRect(0, 0, jpeg.getWidth(), jpeg.getHeight());
        graphics.dispose();
        assertTrue(ImageIO.write(jpeg, "jpeg", sourcePath.toFile()));

        Source source = processor.prepareSource(sourcePath);

        assertEquals("jpeg", source.format());
        assertEquals(640, source.width());
        assertEquals(480, source.height());
    }

    @Test
    void lanczosDownscaleAveragesHighFrequencyDetailWithoutAliasing() {
        int sourceSize = 2048;
        BufferedImage checkerboard = new BufferedImage(
                sourceSize, sourceSize, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < sourceSize; y++) {
            for (int x = 0; x < sourceSize; x++) {
                int channel = ((x + y) & 1) == 0 ? 0 : 255;
                checkerboard.setRGB(x, y,
                        0xff000000 | (channel << 16) | (channel << 8) | channel);
            }
        }

        BufferedImage thumbnail = AvatarImageProcessor.resizeLanczos(checkerboard, 256);
        double total = 0;
        double squaredTotal = 0;
        int count = 0;
        for (int y = 8; y < thumbnail.getHeight() - 8; y++) {
            for (int x = 8; x < thumbnail.getWidth() - 8; x++) {
                int value = thumbnail.getRGB(x, y) & 0xff;
                total += value;
                squaredTotal += value * value;
                count++;
            }
        }

        double average = total / count;
        double deviation = Math.sqrt(squaredTotal / count - average * average);
        assertTrue(average >= 126 && average <= 129,
                "Average luminance was " + average);
        assertTrue(deviation < 3, "Luminance deviation was " + deviation);
    }

    @Test
    void lanczosDownscaleDoesNotCreateDarkHalosAroundTransparency() {
        BufferedImage source = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(128, 128, 256, 256);
        graphics.dispose();

        BufferedImage resized = AvatarImageProcessor.resizeLanczos(source, 64);
        int minimumEdgeChannel = 255;
        boolean foundTranslucentPixel = false;
        for (int y = 0; y < resized.getHeight(); y++) {
            for (int x = 0; x < resized.getWidth(); x++) {
                int pixel = resized.getRGB(x, y);
                int alpha = pixel >>> 24;
                if (alpha > 0 && alpha < 255) {
                    foundTranslucentPixel = true;
                    minimumEdgeChannel = Math.min(minimumEdgeChannel, pixel & 0xff);
                }
            }
        }

        assertTrue(foundTranslucentPixel);
        assertTrue(minimumEdgeChannel >= 240,
                "Transparent edge channel was " + minimumEdgeChannel);
    }

    private static BufferedImage quadrantImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int halfWidth = width / 2;
        int halfHeight = height / 2;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color;
                if (x < halfWidth && y < halfHeight) color = Color.RED;
                else if (x >= halfWidth && y < halfHeight) color = Color.GREEN;
                else if (x < halfWidth) color = Color.BLUE;
                else color = Color.YELLOW;
                image.setRGB(x, y, color.getRGB());
            }
        }
        return image;
    }
}
