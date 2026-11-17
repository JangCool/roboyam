package com.community.trial.service;

import com.community.media.service.ExifOrientation;
import com.community.media.service.ImageCodec;
import com.community.media.service.ImageFormat;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.awt.image.BufferedImage;
import org.springframework.stereotype.Component;

/**
 * 비공개 사진 다시 인코딩(구매 인증 사진 · 체험단 입고·발송 전·반납·파손 사진 — V241·V243) — JPEG·PNG·WebP 정지 그림 10MB 까지 받아 방향을 바로잡고 긴 변 2000px 의 JPEG 로 다시 만든다
 * (EXIF·위치 정보가 빠진다). 틀리면 400 {@code fields.file}. 결과 바이트는 부르는 쪽이 {@code DataCipher.encryptBytes} 로 잠근다.
 */
@Component
public class TrialPhotoCodec {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    public static final int LONG_SIDE = 2000;
    public static final String CONTENT_TYPE = "image/jpeg";

    private final ImageCodec codec;

    public TrialPhotoCodec(ImageCodec codec) {
        this.codec = codec;
    }

    public byte[] jpeg(byte[] data) {
        if (data == null || data.length == 0) {
            throw ValidationException.of("file", new MessageRef("trial.proof.file.required"));
        }
        if (data.length > MAX_BYTES) {
            throw ValidationException.of("file", new MessageRef("trial.proof.file.tooLarge", "10"));
        }
        ImageFormat format = ImageFormat.sniff(data);
        if (format == null || format == ImageFormat.GIF) {
            throw ValidationException.of("file", new MessageRef("trial.proof.file.format"));
        }
        BufferedImage image;
        try {
            image = codec.limited(() -> ExifOrientation.apply(codec.decode(data, format), format == ImageFormat.JPEG ? ExifOrientation.read(data) : 1));
        } catch (RuntimeException e) {
            throw ValidationException.of("file", new MessageRef("trial.proof.file.unreadable"));
        }
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (longest > LONG_SIDE) {
            double scale = (double) LONG_SIDE / longest;
            image = ImageCodec.resize(image, Math.max(1, (int) Math.round(image.getWidth() * scale)), Math.max(1, (int) Math.round(image.getHeight() * scale)));
        }
        return ImageCodec.jpeg(image, 0.88f);
    }
}
