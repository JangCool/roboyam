package com.community.media.service;

import com.community.media.config.PostImageProperties;
import com.community.media.exception.ImageUnreadableException;
import com.community.media.service.ImageCodec.Info;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.awt.image.BufferedImage;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 글 본문 이미지로 올라온 파일을 검사하고 다시 만든다 — 저장소·DB 는 모르는 순수 변환(관리자 앱 {@code LogoImageProcessor} 와 같은 코덱·관문을 쓴다).
 *
 * <p><b>파일을 믿지 않는다</b> — 이름·확장자·Content-Type 은 보지 않고 첫 바이트(서명)로 JPEG·PNG·WebP·GIF 만 받는다. 이미지로 꾸민 다른 파일(SVG·HTML·실행 파일 …)은 형식 오류다.
 *
 * <p>규칙(어기면 {@code 400 VALIDATION fields.file}, 이 순서로 본다): 빈 파일 → 형식 → 용량({@code maxBytes}) → 머리 읽기(깨진 파일) → 화소({@code maxPixels})
 * → 디코딩(실제로 그림인지 — 깨졌으면 같은 「읽을 수 없어요」).
 *
 * <p><b>다시 만들기</b>(EXIF·위치 정보 같은 메타데이터와 그림 뒤에 붙은 데이터가 저장되지 않게):
 * <ul>
 *   <li>JPEG: EXIF 방향을 반영해 돌린 뒤(다시 인코딩하면 EXIF 가 사라진다) 긴 변 {@code maxSide} 로 줄이고 품질 0.85 로 다시 인코딩.</li>
 *   <li>PNG: 줄이고 PNG 로 다시 인코딩(투명 유지).</li>
 *   <li>GIF: 움직이는 GIF(2장 이상)는 원본 그대로 둔다(움직임을 지키려고 — GIF 에는 EXIF 가 없다). 긴 변이 넘으면 첫 장을 줄여 PNG 로. 한 장짜리는 PNG 로.</li>
 *   <li>WebP: JDK 에 디코더가 없어 그림은 그대로 두고 컨테이너만 정리한다(EXIF·XMP·모르는 청크·뒤에 붙은 데이터를 뗀다). 줄이지 않는다.</li>
 * </ul>
 * 디코딩은 동시 처리 수 제한({@code ImageGate}) 아래에서 한다.
 */
@Component
public class PostImageProcessor {

    private static final float JPEG_QUALITY = 0.85f;

    private final ImageCodec codec;
    private final PostImageProperties props;

    public PostImageProcessor(ImageCodec codec, PostImageProperties props) {
        this.codec = codec;
        this.props = props;
    }

    /** 처리 결과 — 저장할 형식·파일·가로·세로 */
    public record Processed(ImageFormat format, byte[] data, int width, int height) {
    }

    public Processed process(byte[] data) {
        if (data == null || data.length == 0) {
            throw problem("site.image.empty");
        }
        ImageFormat format = ImageFormat.sniff(data);
        if (format == null) {
            throw problem("post.image.type");
        }
        if (data.length > props.maxBytes()) {
            throw problem("site.image.size", megabytes(props.maxBytes(), false), megabytes(data.length, true));
        }
        Info info;
        try {
            info = codec.probe(data, format);
        } catch (ImageUnreadableException e) {
            throw problem("site.image.corrupt");
        }
        if ((long) info.width() * info.height() > props.maxPixels()) {
            throw problem("post.image.pixels", String.format(Locale.ROOT, "%,d", props.maxPixels()), String.valueOf(info.width()), String.valueOf(info.height()));
        }
        try {
            return codec.limited(() -> render(data, format, info));
        } catch (ImageUnreadableException e) {
            throw problem("site.image.corrupt");
        }
    }

    private Processed render(byte[] data, ImageFormat format, Info info) {
        if (format == ImageFormat.WEBP) {
            return new Processed(ImageFormat.WEBP, codec.cleanWebp(data), info.width(), info.height());
        }
        BufferedImage image = codec.decode(data, format); // 실제로 디코딩되는지 — 머리만 그럴듯한 파일을 여기서 거른다
        if (format == ImageFormat.GIF) {
            boolean fits = Math.max(info.width(), info.height()) <= props.maxSide();
            if (fits && codec.frameCount(data, format) > 1) {
                return new Processed(ImageFormat.GIF, data, info.width(), info.height());
            }
            BufferedImage scaled = fit(image);
            return new Processed(ImageFormat.PNG, ImageCodec.png(scaled, false), scaled.getWidth(), scaled.getHeight());
        }
        if (format == ImageFormat.JPEG) {
            BufferedImage oriented = fit(ExifOrientation.apply(image, ExifOrientation.read(data)));
            return new Processed(ImageFormat.JPEG, ImageCodec.jpeg(oriented, JPEG_QUALITY), oriented.getWidth(), oriented.getHeight());
        }
        BufferedImage scaled = fit(image);
        return new Processed(ImageFormat.PNG, ImageCodec.png(scaled, false), scaled.getWidth(), scaled.getHeight());
    }

    /** 긴 변이 {@code maxSide} 를 넘으면 비율을 지켜 줄인다 */
    private BufferedImage fit(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int longest = Math.max(w, h);
        if (longest <= props.maxSide()) {
            return image;
        }
        double ratio = (double) props.maxSide() / longest;
        return ImageCodec.resize(image, Math.max(1, (int) Math.round(w * ratio)), Math.max(1, (int) Math.round(h * ratio)));
    }

    private static ValidationException problem(String key, Object... args) {
        return ValidationException.of("file", new MessageRef(key, args));
    }

    /** MB — 한도는 정수, 지금 용량은 소수 한 자리 올림(한도를 막 넘은 파일이 「지금 10.0MB」로 보이지 않게) */
    private static String megabytes(long bytes, boolean precise) {
        double mb = bytes / (1024.0 * 1024.0);
        return precise ? String.format(Locale.ROOT, "%.1f", Math.ceil(mb * 10) / 10) : String.valueOf((long) Math.floor(mb));
    }
}
