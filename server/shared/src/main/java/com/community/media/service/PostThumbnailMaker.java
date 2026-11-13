package com.community.media.service;

import com.community.media.config.PostImageProperties;
import com.community.media.exception.ImageUnreadableException;
import com.community.media.service.ImageCodec.Info;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 글 목록 썸네일 한 장을 만든다 — 저장소·DB 는 모르는 순수 변환(docs/features/list-thumbnail-contract.md 「썸네일 규격」). 입력은 <b>이미 검사·저장된 글 이미지</b>
 * ({@link PostImageProcessor} 의 결과 — JPEG 는 EXIF 방향을 반영해 다시 만든 것이라 여기서는 방향을 보지 않는다).
 *
 * <ol>
 *   <li>{@code thumbWidth}×{@code thumbHeight}(기본 800×450) 상자를 덮을 만큼 비율을 지켜 줄인다 — 배율 = max(상자 가로/가로, 상자 세로/세로), 1 이상이면 줄이지 않는다(키우지 않음).</li>
 *   <li>가로가 상자 가로를 넘으면 가운데를, 세로가 {@code thumbMaxHeight}(기본 1200)를 넘으면 위쪽을 남기고 자른다.</li>
 *   <li>투명한 화소가 하나라도 있으면 PNG, 없으면 JPEG(품질 {@code thumbQuality}, 기본 0.80). 메타데이터는 쓰지 않는다.</li>
 * </ol>
 * 움직이는 GIF·WebP 는 첫 장면. WebP 는 TwelveMonkeys 리더로 읽는다({@link ImageCodec#decodeFirstFrame}). 디코딩 전에 화소 한도({@code maxPixels} — WebP 는
 * {@code WebpContainer.inspect})를 먼저 본다. 만들지 못하면(읽을 수 없는 그림·리더 없음) 빈 값 + 경고 로그 — 올리기는 그대로 성공한다(목록은 원본 주소를 쓴다).
 * 디코딩·인코딩은 동시 처리 수 제한({@link ImageGate}) 아래에서 한다 — 트랜잭션 밖에서 부른다. 자리가 나지 않으면 {@code ImageBusyException}(503)을 그대로 던진다.
 */
@Component
public class PostThumbnailMaker {

    private static final Logger log = LoggerFactory.getLogger(PostThumbnailMaker.class);

    private final ImageCodec codec;
    private final PostImageProperties props;

    public PostThumbnailMaker(ImageCodec codec, PostImageProperties props) {
        this.codec = codec;
        this.props = props;
    }

    /** 만든 썸네일 — 형식(JPEG·PNG)·파일·가로·세로 */
    public record Thumbnail(ImageFormat format, byte[] data, int width, int height) {
    }

    /**
     * 저장된 글 이미지 바이트로 썸네일을 만든다. 형식을 알 수 없거나 읽을 수 없으면 빈 값(경고 로그).
     *
     * @param label 로그에 남길 이름(파일 ID 등 — 개인정보 없음)
     */
    public Optional<Thumbnail> make(byte[] stored, String label) {
        ImageFormat format = ImageFormat.sniff(stored);
        if (format == null) {
            log.warn("글 이미지 썸네일을 만들지 못했다(형식을 모른다) {}", label);
            return Optional.empty();
        }
        try {
            Info info = codec.probe(stored, format);
            if ((long) info.width() * info.height() > props.maxPixels()) {
                log.warn("글 이미지 썸네일을 만들지 못했다(화소 한도 초과) {} {}x{}", label, info.width(), info.height());
                return Optional.empty();
            }
            return Optional.of(codec.limited(() -> render(codec.decodeFirstFrame(stored, format))));
        } catch (ImageUnreadableException e) {
            log.warn("글 이미지 썸네일을 만들지 못했다(읽을 수 없는 그림) {} 형식={} 원인={}", label, format, e.getMessage());
            return Optional.empty();
        } catch (IllegalStateException e) {
            log.warn("글 이미지 썸네일을 만들지 못했다(인코딩 실패) {} 형식={}", label, format, e);
            return Optional.empty();
        }
    }

    private Thumbnail render(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        double scale = Math.max((double) props.thumbWidth() / w, (double) props.thumbHeight() / h);
        BufferedImage scaled = source;
        if (scale < 1.0) {
            scaled = ImageCodec.resize(source, Math.max(1, (int) Math.round(w * scale)), Math.max(1, (int) Math.round(h * scale)));
        }
        BufferedImage cropped = crop(scaled, props.thumbWidth(), props.thumbMaxHeight());
        if (hasTransparency(cropped)) {
            return new Thumbnail(ImageFormat.PNG, ImageCodec.png(cropped, false), cropped.getWidth(), cropped.getHeight());
        }
        return new Thumbnail(ImageFormat.JPEG, ImageCodec.jpeg(cropped, props.thumbQuality()), cropped.getWidth(), cropped.getHeight());
    }

    /** 가로는 가운데, 세로는 위쪽을 남기고 {@code maxWidth}×{@code maxHeight} 안으로 자른다(넘치지 않으면 그대로) — 잘랐으면 새 그림으로 옮겨 그린다 */
    static BufferedImage crop(BufferedImage image, int maxWidth, int maxHeight) {
        int w = image.getWidth();
        int h = image.getHeight();
        int cw = Math.min(w, maxWidth);
        int ch = Math.min(h, maxHeight);
        if (cw == w && ch == h) {
            return image;
        }
        int x = (w - cw) / 2;
        BufferedImage out = new BufferedImage(cw, ch, image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(image, -x, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** 투명한(알파 255 미만) 화소가 하나라도 있는가 — 알파가 없는 색 모델이면 바로 false */
    static boolean hasTransparency(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return false;
        }
        int w = image.getWidth();
        int[] row = new int[w];
        for (int y = 0; y < image.getHeight(); y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int argb : row) {
                if ((argb >>> 24) != 0xff) {
                    return true;
                }
            }
        }
        return false;
    }
}
