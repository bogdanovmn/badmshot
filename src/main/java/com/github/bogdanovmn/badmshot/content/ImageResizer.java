package com.github.bogdanovmn.badmshot.content;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

@Component
class ImageResizer {

    private static final Set<String> SUPPORTED_OUTPUT_FORMATS = Set.of("jpeg", "png");
    private static final float PNG_COMPRESSION_QUALITY = 0.9f;

    private final int maxWidth;
    private final String outputFormat;
    private final float outputQuality;

    ImageResizer(
        @Value("${badmshot.image.max-width:3840}") int maxWidth,
        @Value("${badmshot.image.thumbnail-format:jpeg}") String outputFormat,
        @Value("${badmshot.image.thumbnail-quality:0.78}") float outputQuality
    ) {
        this.maxWidth = maxWidth;
        this.outputFormat = outputFormat.toLowerCase(Locale.ROOT);
        this.outputQuality = outputQuality;
        if (!SUPPORTED_OUTPUT_FORMATS.contains(this.outputFormat)) {
            throw new IllegalArgumentException("Unsupported thumbnail format: " + outputFormat);
        }
    }

    ScreenshotImage crop(ScreenshotImage source, int requestedWidth) {
        int targetWidth = maxWidth > 0
            ? Math.max(1, Math.min(requestedWidth, maxWidth))
            : Math.max(1, requestedWidth);
        if (targetWidth >= source.getWidth()) {
            return source;
        }
        BufferedImage original = decode(source.getData());
        if (original == null) {
            return source;
        }
        int targetHeight = Math.max(1, Math.round(source.getHeight() * (float) targetWidth / source.getWidth()));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, targetType(original));
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
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
            .format(outputFormat)
            .width(targetWidth)
            .height(targetHeight)
            .build();
    }

    private int targetType(BufferedImage original) {
        if ("png".equals(outputFormat) && original.getColorModel().hasAlpha()) {
            return BufferedImage.TYPE_INT_ARGB;
        }
        return BufferedImage.TYPE_INT_RGB;
    }

    private BufferedImage decode(byte[] data) {
        try {
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            return null;
        }
    }

    private byte[] encode(BufferedImage image) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            if ("jpeg".equals(outputFormat)) {
                writeJpeg(image, output);
            } else {
                writePng(image, output);
            }
        } catch (IOException e) {
            return null;
        }
        return output.toByteArray();
    }

    private void writeJpeg(BufferedImage image, ByteArrayOutputStream output) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(outputQuality);
            if (param.canWriteProgressive()) {
                param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private void writePng(BufferedImage image, ByteArrayOutputStream output) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(PNG_COMPRESSION_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
