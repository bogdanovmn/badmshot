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

@Component
class ImageResizer {

    private final int maxWidth;
    private final float jpegQuality;

    ImageResizer(
        @Value("${badmshot.image.max-width:1920}") int maxWidth,
        @Value("${badmshot.image.jpeg-quality:0.85}") float jpegQuality
    ) {
        this.maxWidth = maxWidth;
        this.jpegQuality = jpegQuality;
    }

    ScreenshotImage crop(ScreenshotImage source, int requestedWidth) {
        int targetWidth = Math.max(1, Math.min(requestedWidth, maxWidth));
        if (targetWidth >= source.getWidth()) {
            return source;
        }
        BufferedImage original = decode(source.getData());
        if (original == null) {
            return source;
        }
        int targetHeight = Math.max(1, Math.round(source.getHeight() * (float) targetWidth / source.getWidth()));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, targetType(original, source.getFormat()));
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        byte[] data = encode(scaled, source.getFormat());
        if (data == null) {
            return source;
        }
        return ScreenshotImage.builder()
            .data(data)
            .format(source.getFormat())
            .width(targetWidth)
            .height(targetHeight)
            .build();
    }

    private int targetType(BufferedImage original, String format) {
        if ("jpeg".equals(format)) {
            return BufferedImage.TYPE_INT_RGB;
        }
        return original.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_ARGB : original.getType();
    }

    private BufferedImage decode(byte[] data) {
        try {
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            return null;
        }
    }

    private byte[] encode(BufferedImage image, String format) {
        String writerFormat = switch (format) {
            case "jpeg" -> "jpg";
            case "png" -> "png";
            default -> null;
        };
        if (writerFormat == null) {
            return null;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            if ("jpg".equals(writerFormat)) {
                writeJpeg(image, output);
            } else {
                ImageIO.write(image, writerFormat, output);
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
            param.setCompressionQuality(jpegQuality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
