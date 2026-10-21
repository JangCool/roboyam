package com.community.site.service;

import com.community.media.exception.ImageUnreadableException;
import com.community.media.service.ImageCodec;
import com.community.media.service.ImageCodec.Info;
import com.community.media.service.ImageFormat;
import java.awt.image.BufferedImage;
import org.springframework.stereotype.Component;

/**
 * 링크 미리보기 이미지(og:image)로 올라온 파일을 검사하고 1200×630 불투명 PNG 한 장으로 만든다. 저장소나 DB 는 모르는 순수 변환이다. 사이트 아이콘({@link SiteIconProcessor})과 같은 틀이다.
 *
 * <p>규칙(어기면 {@code 400 VALIDATION fields.file}, 이 순서로 본다): 빈 파일 → <b>형식</b> PNG 만(첫 바이트 서명으로 판별) → <b>용량</b> 2MB 이하 → <b>읽기</b> →
 * <b>너무 큼</b> 가로·세로 4000px 초과 → <b>비율</b> 1200:630(약 1.91:1)에서 ±2% 밖 → <b>너무 작음</b> 가로 1200px 미만(비율이 크기 부족보다 먼저 — 모양부터 고치게).
 * <b>가운데를 잘라 맞추지 않는다</b> — 비율이 안 맞으면 거절한다. 허용 오차 안에서 결과물과 조금 어긋나는 것만 1200×630 에 맞춰 늘이고 줄인다. 링크 미리보기는 투명을 그리지 못하므로
 * <b>흰 배경에 얹어</b> 불투명 RGB PNG 로 만든다. 다시 인코딩하므로 메타데이터·뒤에 붙은 데이터가 저장되지 않는다.
 */
@Component
public class SiteOgImageProcessor {

    /** 가로 하한(px) — 결과물이 1200 이라 그보다 작으면 키워야 한다(키우지 않는다) */
    public static final int MIN_WIDTH = SiteMediaKeys.OG_WIDTH;
    /** 비율 허용 오차(%) */
    public static final int RATIO_TOLERANCE_PERCENT = 2;

    private final ImageCodec codec;

    public SiteOgImageProcessor(ImageCodec codec) {
        this.codec = codec;
    }

    /**
     * 처리 결과.
     *
     * @param sourceWidth  올린 그림의 가로(px)
     * @param sourceHeight 올린 그림의 세로(px)
     * @param data         1200×630 불투명 PNG
     */
    public record Result(int sourceWidth, int sourceHeight, byte[] data) {
    }

    public Result process(byte[] data) {
        if (data == null || data.length == 0) {
            throw ImageProblems.empty();
        }
        if (ImageFormat.sniff(data) != ImageFormat.PNG) {
            throw ImageProblems.file("site.og.format");
        }
        if (data.length > ImageProblems.MAX_BYTES) {
            throw ImageProblems.tooHeavy(data.length);
        }
        Info info;
        try {
            info = codec.probe(data, ImageFormat.PNG);
        } catch (ImageUnreadableException e) {
            throw ImageProblems.corrupt();
        }
        if (Math.max(info.width(), info.height()) > ImageProblems.MAX_SIDE) {
            throw ImageProblems.tooBig(info.width(), info.height());
        }
        if (!ratioFits(info.width(), info.height())) {
            throw ImageProblems.file("site.og.wrongRatio", String.valueOf(SiteMediaKeys.OG_WIDTH), String.valueOf(SiteMediaKeys.OG_HEIGHT),
                    String.valueOf(info.width()), String.valueOf(info.height()));
        }
        if (info.width() < MIN_WIDTH) {
            throw ImageProblems.file("site.og.tooSmall", String.valueOf(MIN_WIDTH), String.valueOf(info.width()), String.valueOf(info.height()));
        }
        try {
            byte[] png = codec.limited(() -> render(data));
            return new Result(info.width(), info.height(), png);
        } catch (ImageUnreadableException e) {
            throw ImageProblems.corrupt();
        }
    }

    /** 가로:세로가 1200:630 에서 허용 오차 안쪽인가 */
    static boolean ratioFits(int width, int height) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        double target = (double) SiteMediaKeys.OG_WIDTH / SiteMediaKeys.OG_HEIGHT;
        return Math.abs((double) width / height - target) / target <= RATIO_TOLERANCE_PERCENT / 100.0;
    }

    private byte[] render(byte[] data) {
        BufferedImage source = codec.decode(data, ImageFormat.PNG);
        BufferedImage fitted = ImageCodec.resize(source, SiteMediaKeys.OG_WIDTH, SiteMediaKeys.OG_HEIGHT);
        return ImageCodec.png(fitted, true);
    }
}
