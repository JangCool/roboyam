package com.community.avatarupload.service;

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
 * 아바타로 올라온 파일을 검사하고 다시 만든다 — 저장소·DB 는 모르는 순수 변환(닉네임 이미지 {@code NicknameImageProcessor} 와 같은 코덱·관문, commerce-motion-avatar-contract 「화면 확정 때 굳힌 것」).
 * 이름·확장자·Content-Type 을 믿지 않고 <b>파일 내용</b>으로 판정한다. 어기면 모두 {@code 400 VALIDATION fields.file}.
 *
 * <p><b>먼저 보는 것</b>({@link #precheck} — 처리 전, 속도 제한 앞): 빈 파일 → 300KB → 형식(PNG·JPG·GIF·WebP).
 * <p><b>처리</b>({@link #process}): 머리 읽기(깨진 파일) → 화소 수 400만 이하 → 움직이는 WebP 거절 → GIF 장면 30개 이하 → 디코딩 → (JPEG 는 EXIF 방향 반영 뒤) 정사각형(±2%) →
 * 가장 짧은 변 96px 이상 → 다시 만들기:
 * <ul>
 *   <li>PNG·JPG·한 장짜리 GIF: 256px 보다 크면 줄이고(작으면 키우지 않는다) 다시 인코딩 — EXIF·뒤에 붙은 데이터가 저장되지 않는다(GIF 한 장은 PNG 로).</li>
 *   <li>움직이는 GIF: 움직임을 지키려고 원본을 그대로 두고(닉네임 이미지·글 이미지와 같은 판단) 첫 장면을 256px 이하 PNG 정지 그림({@code still})으로 따로 만든다. 화면은 틀 크기로 그린다.</li>
 *   <li>WebP: JDK 에 디코더가 없어 그림은 그대로 두고 컨테이너만 정리한다(EXIF·XMP·뒤에 붙은 데이터를 뗀다). 줄이지 않는다(화면이 틀 크기로 그린다).</li>
 * </ul>
 */
@Component
public class AvatarUploadProcessor {

    private static final float JPEG_QUALITY = 0.9f;

    private final ImageCodec codec;

    public AvatarUploadProcessor(ImageCodec codec) {
        this.codec = codec;
    }

    /**
     * 처리 결과.
     *
     * @param still 움직이는 GIF 의 첫 장면(PNG) — 그 밖 null
     */
    public record Processed(ImageFormat format, byte[] data, byte[] still, int width, int height, int frames) {
    }

    /** 처리 전에 보는 것 — 빈 파일 · 300KB · 형식. 형식을 돌려준다 */
    public ImageFormat precheck(byte[] data) {
        if (data == null || data.length == 0) {
            throw problem("avatarUpload.file.required");
        }
        if (data.length > AvatarUploadRules.MAX_BYTES) {
            throw problem("avatarUpload.file.size", String.valueOf(AvatarUploadRules.MAX_BYTES / 1024), String.valueOf((data.length + 1023) / 1024));
        }
        ImageFormat format = ImageFormat.sniff(data);
        if (format == null) {
            throw problem("avatarUpload.file.type");
        }
        return format;
    }

    public Processed process(byte[] data, ImageFormat format) {
        Info info;
        try {
            info = codec.probe(data, format);
        } catch (ImageUnreadableException e) {
            throw problem("avatarUpload.file.corrupt");
        }
        if ((long) info.width() * info.height() > AvatarUploadRules.MAX_PIXELS) {
            throw problem("avatarUpload.file.pixels", String.format(Locale.ROOT, "%,d", AvatarUploadRules.MAX_PIXELS));
        }
        if (format == ImageFormat.WEBP) {
            if (animatedWebp(data)) {
                throw problem("avatarUpload.file.animatedWebp");
            }
            checkShape(info.width(), info.height());
            return new Processed(ImageFormat.WEBP, codec.cleanWebp(data), null, info.width(), info.height(), 1);
        }
        try {
            return codec.limited(() -> render(data, format));
        } catch (ImageUnreadableException e) {
            throw problem("avatarUpload.file.corrupt");
        }
    }

    private Processed render(byte[] data, ImageFormat format) {
        int frames = format == ImageFormat.GIF ? codec.frameCount(data, format) : 1;
        if (frames > AvatarUploadRules.MAX_GIF_FRAMES) {
            throw problem("avatarUpload.file.frames", String.valueOf(AvatarUploadRules.MAX_GIF_FRAMES), String.valueOf(frames));
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

    /** 정사각형(±2%) → 가장 짧은 변 96px 이상(화면 {@code UtilAvatarUpload.checkSize} 와 같은 차례) */
    static void checkShape(int width, int height) {
        if (Math.abs((double) width / height - 1) > AvatarUploadRules.RATIO_TOLERANCE) {
            throw problem("avatarUpload.file.ratio", String.valueOf(width), String.valueOf(height));
        }
        if (Math.min(width, height) < AvatarUploadRules.MIN_SIZE) {
            throw problem("avatarUpload.file.tooSmall", String.valueOf(AvatarUploadRules.MIN_SIZE), String.valueOf(width), String.valueOf(height));
        }
    }

    /** 256px 보다 크면 비율을 지켜 줄인다(작으면 그대로) */
    private static BufferedImage fit(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int max = AvatarUploadRules.MAX_SIZE;
        if (w <= max && h <= max) {
            return image;
        }
        double scale = Math.min((double) max / w, (double) max / h);
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
