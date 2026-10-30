package com.github.bogdanovmn.badmshot.content;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ImageResizerTest {

    private final ImageResizer resizer = new ImageResizer(1920, 0.78f);

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
    void scalesLargeJpegUsingSubsampledDecode() throws IOException {
        ScreenshotImage source = image(600, 300, "jpeg");
        ScreenshotImage result = resizer.crop(source, 100);

        assertEquals("jpeg", result.getFormat());
        assertEquals(100, result.getWidth());
        assertEquals(50, result.getHeight());
        assertNotNull(ImageIO.read(new ByteArrayInputStream(result.getData())));
    }

    @Test
    void returnsSourceForOriginalOrLargerWidth() {
        ScreenshotImage source = image(200, 100, "png");
        assertSame(source, resizer.crop(source, 500));
    }

    @Test
    void returnsSourceForOriginalWidthEvenAboveMaxWidth() {
        ImageResizer limited = new ImageResizer(100, 0.78f);
        ScreenshotImage source = image(400, 200, "png");
        assertSame(source, limited.crop(source, 400));
    }

    @Test
    void clampsToMaxWidth() {
        ImageResizer limited = new ImageResizer(100, 0.78f);
        ScreenshotImage source = image(400, 200, "png");
        ScreenshotImage result = limited.crop(source, 200);

        assertEquals(100, result.getWidth());
        assertEquals(50, result.getHeight());
    }

    @Test
    void normalizesRequestedWidth() {
        assertEquals(1, resizer.targetWidth(0));
        assertEquals(1920, resizer.targetWidth(1920));
        assertEquals(1920, resizer.targetWidth(5000));
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
