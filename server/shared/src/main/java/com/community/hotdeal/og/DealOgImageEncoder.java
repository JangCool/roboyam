package com.community.hotdeal.og;

import com.community.media.exception.ImageUnreadableException;
import com.community.media.service.ExifOrientation;
import com.community.media.service.ImageCodec;
import com.community.media.service.ImageFormat;
import java.awt.image.BufferedImage;
import org.springframework.stereotype.Component;

/**
 * OG 이미지 사본의 형식을 정하는 <b>한 곳</b>(D1 — 2026-11-01 서버 단계 결정). 나중에 WebP 인코더를 들이면 여기만 바꾼다.
 *
 * <ul>
 *   <li>PNG·JPEG·GIF(첫 장): 가로 {@value #WIDTH}px 로 줄이고(더 작으면 그대로 — 늘이지 않는다) <b>투명은 흰 바탕</b>으로 채워 JPEG. 품질을 단계로 낮춰
 *       {@value #MAX_BYTES} 바이트 이하가 되면 그것, 끝까지 넘으면 이미지 없음.</li>
 *   <li>WebP: JDK 에 인코더·디코더가 없어 다시 만들지 않는다 — {@value #MAX_BYTES} 바이트 이하 <b>그리고</b> 가로 {@value #WEBP_MAX_WIDTH}px 이하일 때만 메타데이터만 떼어
 *       ({@link ImageCodec#cleanWebp}) 그대로, 넘으면 이미지 없음.</li>
 *   <li>그 밖 형식·깨진 파일·화소가 너무 많은 그림: 이미지 없음.</li>
 * </ul>
 * 디코딩은 {@link ImageCodec#limited}(동시 처리 제한) 안에서 한다. 「이미지 없음」은 {@code null} — 부르는 쪽이 OG 상태 {@code NONE} 으로 낸다.
 */
@Component
public class DealOgImageEncoder {

    public static final int WIDTH = 600;
    public static final int MAX_BYTES = 300 * 1024;
    public static final int WEBP_MAX_WIDTH = 1200;
    /** 디코딩 전에 거절하는 화소 수(디코딩 폭탄 방지) */
    public static final long MAX_PIXELS = 40_000_000L;
    private static final float[] QUALITIES = {0.85f, 0.75f, 0.65f, 0.55f, 0.45f, 0.35f};

    private final ImageCodec codec;

    public DealOgImageEncoder(ImageCodec codec) {
        this.codec = codec;
    }

    /** 사본 — 형식·파일·가로·세로 */
    public record Encoded(ImageFormat format, byte[] data, int width, int height) {
    }

    /** @return 사본, 만들 수 없으면 null(이미지 없음) */
    public Encoded encode(byte[] source) {
        if (source == null || source.length == 0) {
            return null;
        }
        ImageFormat format = ImageFormat.sniff(source);
        if (format == null) {
            return null;
        }
        ImageCodec.Info info;
        try {
            info = codec.probe(source, format);
        } catch (ImageUnreadableException e) {
            return null;
        }
        if ((long) info.width() * info.height() > MAX_PIXELS) {
            return null;
        }
        if (format == ImageFormat.WEBP) {
            if (source.length > MAX_BYTES || info.width() > WEBP_MAX_WIDTH) {
                return null;
            }
            try {
                byte[] cleaned = codec.cleanWebp(source);
                return cleaned.length > MAX_BYTES ? null : new Encoded(ImageFormat.WEBP, cleaned, info.width(), info.height());
            } catch (ImageUnreadableException e) {
                return null;
            }
        }
        try {
            return codec.limited(() -> jpeg(source, format));
        } catch (ImageUnreadableException e) {
            return null;
        }
    }

    private Encoded jpeg(byte[] source, ImageFormat format) {
        BufferedImage image = codec.decode(source, format);
        if (format == ImageFormat.JPEG) {
            image = ExifOrientation.apply(image, ExifOrientation.read(source));
        }
        if (image.getWidth() > WIDTH) {
            int height = Math.max(1, (int) Math.round((double) image.getHeight() * WIDTH / image.getWidth()));
            image = ImageCodec.resize(image, WIDTH, height);
        }
        for (float quality : QUALITIES) {
            byte[] data = ImageCodec.jpeg(image, quality); // 투명은 흰 바탕에 얹는다
            if (data.length <= MAX_BYTES) {
                return new Encoded(ImageFormat.JPEG, data, image.getWidth(), image.getHeight());
            }
        }
        return null;
    }
}
