package com.community.nicknameimage.service;

import com.community.media.exception.ImageUnreadableException;
import com.community.media.service.ExifOrientation;
import com.community.media.service.ImageCodec;
import com.community.media.service.ImageCodec.Info;
import com.community.media.service.ImageFormat;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.awt.image.BufferedImage;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 닉네임 그림으로 올라온 파일을 검사하고 다시 만든다 — 저장소·DB 는 모르는 순수 변환(글 이미지 {@code PostImageProcessor} 와 같은 코덱·관문 {@code ImageGate}).
 * 이름·확장자·Content-Type 을 믿지 않고 <b>파일 내용</b>(첫 바이트의 서명·머리)으로 판정한다. 어기면 모두 {@code 400 VALIDATION fields.file}.
 *
 * <p><b>먼저 보는 것</b>({@link #precheck} — 이미지를 처리하기 전, 속도 제한 앞): 빈 파일 → 200KB → 형식(PNG·JPG·GIF·WebP).
 * <p><b>처리</b>({@link #process}): 머리 읽기(깨진 파일) → 화소 수 400만 이하 → 움직이는 WebP 거절 → GIF 장면 30개 이하 → 디코딩 → (JPEG 는 EXIF 방향 반영 뒤) 최소 120×30 →
 * 가로:세로 4:1(오차 1%) → 다시 만들기:
 * <ul>
 *   <li>PNG·JPG·한 장짜리 GIF: 240×60 보다 크면 줄이고(작으면 키우지 않는다) 다시 인코딩 — EXIF·뒤에 붙은 데이터가 저장되지 않는다(GIF 한 장은 PNG 로).</li>
 *   <li>움직이는 GIF: 움직임을 지키려고 원본을 그대로 두고(글 이미지와 같은 판단 — 프레임별 다시 쓰기는 하지 않는다) 첫 장면을 240×60 이하 PNG 정지 그림({@code still})으로 따로 만든다.
 *       화면은 240×60 으로 그린다.</li>
 *   <li>WebP: JDK 에 디코더가 없어 그림은 그대로 두고 컨테이너만 정리한다(EXIF·XMP·뒤에 붙은 데이터를 뗀다). 줄이지 않는다(화면이 240×60 으로 그린다).</li>
 * </ul>
 */
@Component
public class NicknameImageProcessor {

    private static final float JPEG_QUALITY = 0.9f;

    private final ImageCodec codec;

    public NicknameImageProcessor(ImageCodec codec) {
        this.codec = codec;
    }

    /**
     * 처리 결과.
     *
     * @param still 움직이는 GIF 의 첫 장면(PNG) — 그 밖 null
     */
    public record Processed(ImageFormat format, byte[] data, byte[] still, int width, int height, int frames) {
    }

    /** 처리 전에 보는 것 — 빈 파일 · 200KB · 형식. 형식을 돌려준다 */
    public ImageFormat precheck(byte[] data) {
        if (data == null || data.length == 0) {
            throw problem("nicknameImage.file.required");
        }
        if (data.length > NicknameImageRules.MAX_BYTES) {
            throw problem("nicknameImage.file.size", String.valueOf(NicknameImageRules.MAX_BYTES / 1024), String.valueOf((data.length + 1023) / 1024));
        }
        ImageFormat format = ImageFormat.sniff(data);
        if (format == null) {
            throw problem("nicknameImage.file.type");
        }
        return format;
    }

    public Processed process(byte[] data, ImageFormat format) {
        Info info;
        try {
            info = codec.probe(data, format);
        } catch (ImageUnreadableException e) {
            throw problem("nicknameImage.file.corrupt");
        }
        if ((long) info.width() * info.height() > NicknameImageRules.MAX_PIXELS) {
            throw problem("nicknameImage.file.pixels", String.format(Locale.ROOT, "%,d", NicknameImageRules.MAX_PIXELS));
        }
        if (format == ImageFormat.WEBP) {
            if (animatedWebp(data)) {
                throw problem("nicknameImage.file.animatedWebp");
            }
            checkShape(info.width(), info.height());
            return new Processed(ImageFormat.WEBP, codec.cleanWebp(data), null, info.width(), info.height(), 1);
        }
        try {
            return codec.limited(() -> render(data, format));
        } catch (ImageUnreadableException e) {
            throw problem("nicknameImage.file.corrupt");
        }
    }

    private Processed render(byte[] data, ImageFormat format) {
        int frames = format == ImageFormat.GIF ? codec.frameCount(data, format) : 1;
        if (frames > NicknameImageRules.MAX_GIF_FRAMES) {
            throw problem("nicknameImage.file.frames", String.valueOf(NicknameImageRules.MAX_GIF_FRAMES), String.valueOf(frames));
        }
        BufferedImage image = codec.decode(data, format); // 실제로 디코딩되는지 — 머리만 그럴듯한 파일을 여기서 거른다
        if (format == ImageFormat.JPEG) {
            image = ExifOrientation.apply(image, ExifOrientation.read(data));
        }
        checkShape(image.getWidth(), image.getHeight());
        if (format == ImageFormat.GIF && frames > 1) {
            BufferedImage still = fit(image);
            return new Processed(ImageFormat.GIF, data, ImageCodec.png(still, false), image.getWidth(), image.getHeight(), frames);
        }
        BufferedImage scaled = fit(image);
        if (format == ImageFormat.JPEG) {
            return new Processed(ImageFormat.JPEG, ImageCodec.jpeg(scaled, JPEG_QUALITY), null, scaled.getWidth(), scaled.getHeight(), 1);
        }
        return new Processed(ImageFormat.PNG, ImageCodec.png(scaled, false), null, scaled.getWidth(), scaled.getHeight(), 1);
    }

    /** 최소 크기 → 비율 */
    private static void checkShape(int width, int height) {
        if (width < NicknameImageRules.MIN_WIDTH || height < NicknameImageRules.MIN_HEIGHT) {
            throw problem("nicknameImage.file.tooSmall", String.valueOf(NicknameImageRules.MIN_WIDTH), String.valueOf(NicknameImageRules.MIN_HEIGHT),
                    String.valueOf(width), String.valueOf(height));
        }
        double ratio = (double) width / height;
        if (ratio < NicknameImageRules.RATIO_MIN || ratio > NicknameImageRules.RATIO_MAX) {
            throw problem("nicknameImage.file.ratio", NicknameImageRules.RATIO, String.valueOf(width), String.valueOf(height));
        }
    }

    /** 240×60 보다 크면 비율을 지켜 줄인다(작으면 그대로) */
    private static BufferedImage fit(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (w <= NicknameImageRules.WIDTH && h <= NicknameImageRules.HEIGHT) {
            return image;
        }
        double scale = Math.min((double) NicknameImageRules.WIDTH / w, (double) NicknameImageRules.HEIGHT / h);
        return ImageCodec.resize(image, Math.max(1, (int) Math.round(w * scale)), Math.max(1, (int) Math.round(h * scale)));
    }

    /** 움직이는 WebP 인가 — 확장 머리(VP8X)의 애니메이션 표시(flags 0x02) */
    static boolean animatedWebp(byte[] d) {
        return d.length >= 21 && d[12] == 'V' && d[13] == 'P' && d[14] == '8' && d[15] == 'X' && (d[20] & 0x02) != 0;
    }

    private static ValidationException problem(String key, Object... args) {
        return ValidationException.of("file", new MessageRef(key, args));
    }
}
