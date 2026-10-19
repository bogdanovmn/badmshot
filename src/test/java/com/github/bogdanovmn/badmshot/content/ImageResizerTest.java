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

    private final ImageResizer resizer = new ImageResizer(1920, 0.85f);

    @Test
    void returnsSourceWhenWidthNotLarger() {
        ScreenshotImage source = image(200, 100, "png");
        assertSame(source, resizer.crop(source, 200));
    }

    @Test
    void scalesPngProportionally() throws IOException {
        ScreenshotImage source = image(200, 100, "png");
        ScreenshotImage result = resizer.crop(source, 100);

        assertEquals("png", result.getFormat());
        assertEquals(100, result.getWidth());
        assertEquals(50, result.getHeight());
        assertNotNull(ImageIO.read(new ByteArrayInputStream(result.getData())));
    }

    @Test
    void scalesJpeg() throws IOException {
        ScreenshotImage source = image(200, 100, "jpeg");
        ScreenshotImage result = resizer.crop(source, 100);

        assertEquals("jpeg", result.getFormat());
        assertEquals(100, result.getWidth());
        assertEquals(50, result.getHeight());
        assertNotNull(ImageIO.read(new ByteArrayInputStream(result.getData())));
    }

    @Test
    void clampsToMaxWidth() {
        ImageResizer limited = new ImageResizer(100, 0.85f);
        ScreenshotImage source = image(400, 200, "png");
        ScreenshotImage result = limited.crop(source, 1000);

        assertEquals(100, result.getWidth());
        assertEquals(50, result.getHeight());
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
