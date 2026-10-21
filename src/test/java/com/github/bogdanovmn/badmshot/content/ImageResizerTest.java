package com.github.bogdanovmn.badmshot.content;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageResizerTest {

    private final ImageResizer resizer = new ImageResizer(1920, "jpeg", 0.78f);

    @Test
    void returnsSourceWhenWidthNotLarger() {
        ScreenshotImage source = image(200, 100, "png");
        assertSame(source, resizer.crop(source, 200));
    }

    @Test
    void scalesPngSourceToJpegThumbnail() throws IOException {
        ScreenshotImage source = image(200, 100, "png");
        ScreenshotImage result = resizer.crop(source, 100);

        assertEquals("jpeg", result.getFormat());
        assertEquals(100, result.getWidth());
        assertEquals(50, result.getHeight());
        assertNotNull(ImageIO.read(new ByteArrayInputStream(result.getData())));
    }

    @Test
    void scalesJpegSource() throws IOException {
        ScreenshotImage source = image(200, 100, "jpeg");
        ScreenshotImage result = resizer.crop(source, 100);

        assertEquals("jpeg", result.getFormat());
        assertEquals(100, result.getWidth());
        assertEquals(50, result.getHeight());
        assertNotNull(ImageIO.read(new ByteArrayInputStream(result.getData())));
    }

    @Test
    void scalesWebpSourceToJpegThumbnail() throws IOException {
        ScreenshotImage source = webpImage(200, 100);
        ScreenshotImage result = resizer.crop(source, 100);

        assertEquals("jpeg", result.getFormat());
        assertEquals(100, result.getWidth());
        assertEquals(50, result.getHeight());
        assertNotNull(ImageIO.read(new ByteArrayInputStream(result.getData())));
    }

    @Test
    void producesPngWhenConfigured() throws IOException {
        ImageResizer pngResizer = new ImageResizer(1920, "png", 0.78f);
        ScreenshotImage source = image(200, 100, "jpeg");
        ScreenshotImage result = pngResizer.crop(source, 100);

        assertEquals("png", result.getFormat());
        assertEquals(100, result.getWidth());
        assertEquals(50, result.getHeight());
        assertNotNull(ImageIO.read(new ByteArrayInputStream(result.getData())));
    }

    @Test
    void clampsToMaxWidth() {
        ImageResizer limited = new ImageResizer(100, "jpeg", 0.78f);
        ScreenshotImage source = image(400, 200, "png");
        ScreenshotImage result = limited.crop(source, 1000);

        assertEquals(100, result.getWidth());
        assertEquals(50, result.getHeight());
    }

    @Test
    void rejectsUnsupportedOutputFormat() {
        assertThrows(IllegalArgumentException.class, () -> new ImageResizer(1920, "webp", 0.78f));
    }

    private ScreenshotImage webpImage(int width, int height) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/images/sample.webp")) {
            assertNotNull(input);
            return ScreenshotImage.builder()
                .data(input.readAllBytes())
                .format("webp")
                .width(width)
                .height(height)
                .build();
        }
    }

    private ScreenshotImage image(int width, int height, String format) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpeg".equals(format) ? "jpg" : format, output);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return ScreenshotImage.builder()
            .data(output.toByteArray())
            .format(format)
            .width(width)
            .height(height)
            .build();
    }
}
