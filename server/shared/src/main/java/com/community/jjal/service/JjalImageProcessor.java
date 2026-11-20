package com.community.jjal.service;

import com.community.jjal.config.JjalProperties;
import com.community.media.exception.ImageUnreadableException;
import com.community.media.service.ExifOrientation;
import com.community.media.service.ImageCodec;
import com.community.media.service.ImageFormat;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.awt.image.BufferedImage;
import org.springframework.stereotype.Component;

/**
 * 내 짤로 올라온 파일을 검사하고 저장본을 만든다 — 저장소·DB 는 모르는 순수 변환(글 이미지 {@code PostImageProcessor} 와 같은 코덱·동시 처리 관문을 쓴다).
 * 브라우저가 긴 변 300px 로 줄여 올리고 서버는 <b>검사만</b> 한다(my-jjal-contract 「올리기」 — 사용자 확정 2026-11-20).
 *
 * <p>검사(어기면 {@code 400 VALIDATION fields.file}, 이 순서 — 용량 413 은 부르는 쪽이 먼저 본다): 빈 파일 → 형식(첫 바이트로 GIF·PNG·JPEG 만 — 이름·Content-Type 무시,
 * 위장 파일·SVG·WebP 거절) → 머리의 가로·세로(긴 변 {@code maxSide} 초과는 <b>디코딩 전에</b> 거절) → 실제 그림(디코딩 — 깨짐 거절) → GIF 는 장 수 {@code maxFrames}·재생 시간
 * {@code maxDurationMillis} 이하.
 *
 * <p>저장본: GIF 는 원본 그대로(움짤을 다시 만들지 않는다 — 글 이미지와 같다, GIF 에는 EXIF 가 없다). JPEG 는 EXIF 방향을 반영해 돌린 뒤 다시 인코딩, PNG 는 다시 인코딩
 * (투명 유지) — 휴대폰 사진의 위치 정보 같은 메타데이터가 저장되지 않는다. 해시는 부르는 쪽이 <b>원본</b> 바이트로 낸다.
 */
@Component
public class JjalImageProcessor {

    private static final float JPEG_QUALITY = 0.9f;

    private final ImageCodec codec;
    private final JjalProperties props;

    public JjalImageProcessor(ImageCodec codec, JjalProperties props) {
        this.codec = codec;
        this.props = props;
    }

    /** 저장본 — 형식·바이트·가로·세로·움짤(GIF 2장 이상) */
    public record Processed(ImageFormat format, byte[] data, int width, int height, boolean animated) {
    }

    public Processed process(byte[] data) {
        if (data == null || data.length == 0) {
            throw problem("site.image.empty");
        }
        ImageFormat format = ImageFormat.sniff(data);
        if (format == null || format == ImageFormat.WEBP) {
            throw problem("jjal.file.type");
        }
        ImageCodec.Info info;
        try {
            info = codec.probe(data, format);
        } catch (ImageUnreadableException e) {
            throw problem("site.image.corrupt");
        }
        if (Math.max(info.width(), info.height()) > props.maxSide()) {
            throw problem("jjal.file.side", props.maxSide(), String.valueOf(info.width()), String.valueOf(info.height()));
        }
        try {
            return codec.limited(() -> render(data, format, info));
        } catch (ImageUnreadableException e) {
            throw problem("site.image.corrupt");
        }
    }

    private Processed render(byte[] data, ImageFormat format, ImageCodec.Info info) {
        BufferedImage image = codec.decode(data, format); // 실제로 디코딩되는지 — 머리만 그럴듯한 파일을 여기서 거른다
        if (format == ImageFormat.GIF) {
            ImageCodec.GifFrames frames = codec.gifFrames(data, props.maxFrames());
            if (frames.count() > props.maxFrames()) {
                throw problem("jjal.file.frames", props.maxFrames());
            }
            if (frames.count() > 1 && frames.durationMillis() > props.maxDurationMillis()) {
                throw problem("jjal.file.duration", props.maxDurationMillis() / 1000);
            }
            return new Processed(ImageFormat.GIF, data, info.width(), info.height(), frames.count() > 1);
        }
        if (format == ImageFormat.JPEG) {
            BufferedImage oriented = ExifOrientation.apply(image, ExifOrientation.read(data));
            return new Processed(ImageFormat.JPEG, ImageCodec.jpeg(oriented, JPEG_QUALITY), oriented.getWidth(), oriented.getHeight(), false);
        }
        return new Processed(ImageFormat.PNG, ImageCodec.png(image, false), image.getWidth(), image.getHeight(), false);
    }

    private static ValidationException problem(String key, Object... args) {
        return ValidationException.of("file", new MessageRef(key, args));
    }
}
