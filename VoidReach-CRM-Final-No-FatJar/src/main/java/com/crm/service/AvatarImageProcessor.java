package com.crm.service;

import com.twelvemonkeys.image.ResampleOp;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Memory-bounded image decoding, crop geometry, and high-quality avatar resampling. */
public final class AvatarImageProcessor {
    public static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    public static final int MIN_SOURCE_EDGE = 300;
    public static final int MAX_SOURCE_EDGE = 20_000;
    public static final int MIN_CROP_EDGE = 256;
    public static final int MASTER_MAX_EDGE = 1024;
    static final int PREVIEW_MAX_EDGE = 2048;
    static final int DECODE_MAX_EDGE = 4096;
    private static final double MAX_ZOOM = 3.0;

    public record Source(
            Path path,
            long fileSize,
            String format,
            int width,
            int height,
            BufferedImage preview) {
    }

    public record CropSelection(double centerX, double centerY, double zoom) {
    }

    public record CropRegion(int x, int y, int size) {
    }

    public static final class ValidationException extends IllegalArgumentException {
        public ValidationException(String message) {
            super(message);
        }

        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public Source prepareSource(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new ValidationException("The selected file no longer exists.");
        }

        try {
            long fileSize = Files.size(path);
            validateFileSize(fileSize);

            try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
                if (input == null) {
                    throw new ValidationException("The selected file could not be read.");
                }

                ImageReader reader = firstReader(input);
                try {
                    reader.setInput(input, true, true);
                    String format = normalizeFormat(reader.getFormatName());
                    if (!"png".equals(format) && !"jpeg".equals(format)) {
                        throw new ValidationException("Only PNG and JPG images are supported.");
                    }

                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    validateDimensions(width, height);

                    ImageReadParam params = reader.getDefaultReadParam();
                    int subsampling = calculateSubsampling(width, height, PREVIEW_MAX_EDGE);
                    params.setSourceSubsampling(subsampling, subsampling, 0, 0);
                    BufferedImage preview = reader.read(0, params);
                    if (preview == null) {
                        throw new ValidationException("The image content cannot be read.");
                    }

                    return new Source(path.toAbsolutePath().normalize(), fileSize, format,
                            width, height, toArgb(preview));
                } finally {
                    reader.dispose();
                }
            }
        } catch (ValidationException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new ValidationException("The image is damaged or cannot be read.", ex);
        }
    }

    public BufferedImage createMaster(Source source, CropSelection selection) throws IOException {
        if (source == null || selection == null) {
            throw new IllegalArgumentException("The source and crop selection are required");
        }

        CropRegion crop = resolveCropRegion(source.width(), source.height(), selection);
        BufferedImage decoded = readRegion(source, crop);
        int targetSize = Math.min(MASTER_MAX_EDGE, crop.size());
        return resizeLanczos(decoded, targetSize);
    }

    public static void validateFileSize(long fileSize) {
        if (fileSize <= 0) {
            throw new ValidationException("The selected file is empty.");
        }
        if (fileSize > MAX_UPLOAD_BYTES) {
            throw new ValidationException("The profile picture cannot exceed 10 MB.");
        }
    }

    public static void validateDimensions(int width, int height) {
        if (width < MIN_SOURCE_EDGE || height < MIN_SOURCE_EDGE) {
            throw new ValidationException("The image must be at least 300 pixels wide and high.");
        }
        if (width > MAX_SOURCE_EDGE || height > MAX_SOURCE_EDGE) {
            throw new ValidationException("The image cannot exceed 20000 × 20000 pixels.");
        }
        if ((long) width * height > (long) MAX_SOURCE_EDGE * MAX_SOURCE_EDGE) {
            throw new ValidationException("The image contains too many pixels.");
        }
    }

    public static int calculateSubsampling(int width, int height, int maximumEdge) {
        if (width <= 0 || height <= 0 || maximumEdge <= 0) {
            throw new IllegalArgumentException("Invalid subsampling dimensions");
        }
        long largestEdge = Math.max(width, height);
        return (int) Math.max(1L, (largestEdge + maximumEdge - 1L) / maximumEdge);
    }

    public static double maxZoomFor(int width, int height) {
        validateDimensions(width, height);
        return Math.max(1.0, Math.min(MAX_ZOOM,
                Math.min(width, height) / (double) MIN_CROP_EDGE));
    }

    public static CropSelection clampSelection(int width, int height, CropSelection selection) {
        if (selection == null || !Double.isFinite(selection.centerX())
                || !Double.isFinite(selection.centerY()) || !Double.isFinite(selection.zoom())
                || selection.zoom() < 1.0 || selection.zoom() > maxZoomFor(width, height) + 1e-9) {
            throw new IllegalArgumentException("Invalid profile picture crop");
        }

        double side = Math.min(width, height) / selection.zoom();
        double half = side / 2.0;
        return new CropSelection(
                clamp(selection.centerX(), half, width - half),
                clamp(selection.centerY(), half, height - half),
                selection.zoom());
    }

    public static CropRegion resolveCropRegion(int width, int height, CropSelection selection) {
        CropSelection clamped = clampSelection(width, height, selection);
        int side = Math.max(MIN_CROP_EDGE,
                Math.min(Math.min(width, height),
                        (int) Math.round(Math.min(width, height) / clamped.zoom())));
        int x = clamp((int) Math.round(clamped.centerX() - side / 2.0), 0, width - side);
        int y = clamp((int) Math.round(clamped.centerY() - side / 2.0), 0, height - side);
        return new CropRegion(x, y, side);
    }

    public static BufferedImage resizeLanczos(BufferedImage source, int targetSize) {
        if (source == null || targetSize <= 0) {
            throw new IllegalArgumentException("Invalid image or profile picture size");
        }
        if (source.getWidth() == targetSize && source.getHeight() == targetSize
                && source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }

        BufferedImage argb = source.getColorModel().hasAlpha()
                ? toPremultipliedArgb(source)
                : toArgb(source);
        BufferedImage resized = new ResampleOp(targetSize, targetSize, ResampleOp.FILTER_LANCZOS)
                .filter(argb, null);
        return toArgb(resized);
    }

    private BufferedImage readRegion(Source source, CropRegion crop) throws IOException {
        long currentFileSize = Files.size(source.path());
        validateFileSize(currentFileSize);
        if (currentFileSize != source.fileSize()) {
            throw new IOException("The source file changed during cropping");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(source.path().toFile())) {
            if (input == null) {
                throw new IOException("The source file cannot be read");
            }

            ImageReader reader = firstReader(input);
            try {
                reader.setInput(input, true, true);
                if (reader.getWidth(0) != source.width() || reader.getHeight(0) != source.height()) {
                    throw new IOException("The source file changed during cropping");
                }

                ImageReadParam params = reader.getDefaultReadParam();
                params.setSourceRegion(new Rectangle(crop.x(), crop.y(), crop.size(), crop.size()));
                int subsampling = calculateSubsampling(crop.size(), crop.size(), DECODE_MAX_EDGE);
                params.setSourceSubsampling(subsampling, subsampling, 0, 0);
                BufferedImage decoded = reader.read(0, params);
                if (decoded == null) {
                    throw new IOException("The cropped image cannot be read");
                }
                return toArgb(decoded);
            } finally {
                reader.dispose();
            }
        }
    }

    private static ImageReader firstReader(ImageInputStream input) {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
            throw new ValidationException("The file does not contain a valid PNG or JPG image.");
        }
        return readers.next();
    }

    private static String normalizeFormat(String format) {
        String normalized = format == null ? "" : format.toLowerCase(Locale.ROOT);
        return "jpg".equals(normalized) ? "jpeg" : normalized;
    }

    private static BufferedImage toArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        BufferedImage converted = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private static BufferedImage toPremultipliedArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB_PRE) {
            return source;
        }
        BufferedImage converted = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
