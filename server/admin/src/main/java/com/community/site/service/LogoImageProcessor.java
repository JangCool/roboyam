package com.community.site.service;

import com.community.media.exception.ImageUnreadableException;
import com.community.media.service.ImageCodec;
import com.community.media.service.ImageCodec.Info;
import com.community.media.service.ImageFormat;
import java.awt.image.BufferedImage;
import org.springframework.stereotype.Component;

/**
 * 로고로 올라온 그림 파일을 검사하고 다시 만든다. 저장소나 DB 는 모르는 순수 변환이다.
 *
 * <p><b>파일을 믿지 않는다</b> — 이름·확장자·Content-Type 은 보지 않고 첫 바이트(서명)로 PNG·JPEG·GIF·WEBP 만 받는다. SVG(첫 글자가 {@code <})와 그 밖의 형식은 같은 "그림 형식" 오류로 걸린다
 * (SVG 는 파일이 아니라 소스 입력으로만 받는다).
 *
 * <p>규칙(어기면 {@code 400 VALIDATION fields.file}, 이 순서로 본다): 빈 파일 → <b>형식</b> → <b>용량</b> 2MB 이하 → <b>읽기</b>(머리만 읽어 가로·세로를 잼) → <b>크기</b> 가로·세로 4000px 이하.
 *
 * <p><b>다시 만들기</b>(메타데이터·그림 뒤에 붙은 데이터가 저장되지 않게): PNG 는 PNG 로, JPEG 는 JPEG(품질 0.9)로 다시 인코딩한다. GIF 는 <b>첫 프레임을 PNG 로</b> 바꾼다(움직임은 버린다 —
 * 로고 자리는 20×20 이다). WEBP 는 JDK 에 디코더가 없어 그림은 그대로 두고 컨테이너만 정리한다(EXIF·XMP·모르는 청크·뒤에 붙은 데이터를 뗀다, {@code WebpContainer}).
 * 디코딩은 동시 처리 수 제한({@code ImageGate}) 아래에서 한다.
 */
@Component
public class LogoImageProcessor {

    private static final float JPEG_QUALITY = 0.9f;

    private final ImageCodec codec;

    public LogoImageProcessor(ImageCodec codec) {
        this.codec = codec;
    }

    /**
     * 처리 결과.
     *
     * @param format 저장할 형식(PNG · JPEG · WEBP — GIF 는 PNG 가 된다)
     * @param data   다시 만든 파일
     */
    public record Processed(ImageFormat format, byte[] data, int width, int height) {
    }

    public Processed process(byte[] data) {
        if (data == null || data.length == 0) {
            throw ImageProblems.empty();
        }
        ImageFormat format = ImageFormat.sniff(data);
        if (format == null) {
            throw ImageProblems.file("site.logo.type");
        }
        if (data.length > ImageProblems.MAX_BYTES) {
            throw ImageProblems.file("site.logo.size", String.valueOf(ImageProblems.MAX_MB));
        }
        Info info;
        try {
            info = codec.probe(data, format);
        } catch (ImageUnreadableException e) {
            throw ImageProblems.corrupt();
        }
        if (Math.max(info.width(), info.height()) > ImageProblems.MAX_SIDE) {
            throw ImageProblems.tooBig(info.width(), info.height());
        }
        try {
            return codec.limited(() -> render(data, format, info));
        } catch (ImageUnreadableException e) {
            throw ImageProblems.corrupt();
        }
    }

    private Processed render(byte[] data, ImageFormat format, Info info) {
        if (format == ImageFormat.WEBP) {
            return new Processed(ImageFormat.WEBP, codec.cleanWebp(data), info.width(), info.height());
        }
        BufferedImage image = codec.decode(data, format);
        if (format == ImageFormat.JPEG) {
            return new Processed(ImageFormat.JPEG, ImageCodec.jpeg(image, JPEG_QUALITY), image.getWidth(), image.getHeight());
        }
        return new Processed(ImageFormat.PNG, ImageCodec.png(image, false), image.getWidth(), image.getHeight());
    }
}
