package com.github.bogdanovmn.badmshot.content;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

@Slf4j
@Component
class ImageResizer {

    private static final String OUTPUT_FORMAT = "jpeg";

    static {
        // Do not create temporary files for image streams: all sources are small byte arrays.
        ImageIO.setUseCache(false);
    }

    private final int maxWidth;
    private final float outputQuality;

    ImageResizer(
        @Value("${badmshot.image.max-width:3840}") int maxWidth,
        @Value("${badmshot.image.thumbnail-quality:0.78}") float outputQuality
    ) {
        this.maxWidth = maxWidth;
        this.outputQuality = Math.max(0f, Math.min(1f, outputQuality));
    }

    int targetWidth(int requestedWidth) {
        int width = Math.max(1, requestedWidth);
        return maxWidth > 0 ? Math.min(width, maxWidth) : width;
    }

    ScreenshotImage crop(ScreenshotImage source, int requestedWidth) {
        // Nothing to do when the client asks for the original size or larger: return the stored image as is.
        if (requestedWidth >= source.getWidth()) {
            return source;
        }
        int targetWidth = targetWidth(requestedWidth);
        BufferedImage original = decode(source, targetWidth);
        if (original == null) {
            return source;
        }
        int targetHeight = Math.max(1, Math.round(source.getHeight() * (float) targetWidth / source.getWidth()));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        byte[] data = encode(scaled);
        if (data == null) {
            return source;
        }
        return ScreenshotImage.builder()
            .data(data)
            .format(OUTPUT_FORMAT)
            .width(targetWidth)
            .height(targetHeight)
            .build();
    }

    private BufferedImage decode(ScreenshotImage source, int targetWidth) {
        byte[] data = source.getData();
        if (data == null || data.length == 0) {
            return null;
        }
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                ImageReadParam param = reader.getDefaultReadParam();
                if (isJpeg(source.getFormat())) {
                    // JPEG readers can decode a subsampled image, which is much faster for large sources.
                    int step = subsamplingStep(reader.getWidth(0), targetWidth);
                    if (step > 1) {
                        param.setSourceSubsampling(step, step, 0, 0);
                    }
                }
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Can't decode screenshot image: {}", e.getMessage());
            return null;
        }
    }

    private static boolean isJpeg(String format) {
        return "jpeg".equalsIgnoreCase(format) || "jpg".equalsIgnoreCase(format);
    }

    private static int subsamplingStep(int sourceWidth, int targetWidth) {
        int scale = sourceWidth / Math.max(1, targetWidth);
        if (scale <= 2) {
            return 1;
        }
        int step = scale - 1;
        while (step > 1 && sourceWidth / step < targetWidth) {
            step--;
        }
        return Math.max(1, step);
    }

    private byte[] encode(BufferedImage image) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName(OUTPUT_FORMAT).next();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(outputQuality);
            if (param.canWriteProgressive()) {
                param.setProgressiveMode(ImageWriteParam.MODE_DISABLED);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            log.warn("Can't encode screenshot image: {}", e.getMessage());
            return null;
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }
}
