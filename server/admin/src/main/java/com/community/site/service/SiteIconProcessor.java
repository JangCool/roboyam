package com.community.site.service;

import com.community.media.exception.ImageUnreadableException;
import com.community.media.service.ImageCodec;
import com.community.media.service.ImageCodec.Info;
import com.community.media.service.ImageFormat;
import com.community.site.service.SiteMediaKeys.IconFile;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 사이트 아이콘으로 올라온 파일(또는 SVG 소스)을 검사하고 다섯 크기의 PNG 로 만든다. 저장소나 DB 는 모르는 순수 변환이다.
 *
 * <p><b>PNG 파일</b> — 이름·확장자·Content-Type 은 보지 않고 첫 바이트(PNG 서명)로 형식을 판별한다(SVG·JPEG·WebP·GIF 는 모두 같은 "PNG 만" 오류).
 * 규칙(어기면 {@code 400 VALIDATION fields.file}, 이 순서로 본다): 빈 파일 → <b>형식</b> → <b>용량</b> 2MB 이하 → <b>읽기</b>(머리만 읽어 가로·세로를 잼) → <b>너무 큼</b> 한 변 4000px 초과 →
 * <b>정사각 아님</b> → <b>너무 작음</b> 한 변 512px 미만. 그림을 <b>다시 인코딩</b>하므로 메타데이터·뒤에 붙은 데이터가 저장되지 않는다. 투명은 그대로 두고 iOS 홈 화면 아이콘(180) 한 장만 흰 배경에 얹는다.
 * 줄이기만 하고 키우지 않는다(512 미만은 거절).
 *
 * <p><b>SVG 소스</b>({@link #processSvg}) — {@link SvgInspector} 로 검사(정사각 포함, {@code fields.svgSource})하고 {@link SvgRasterizer} 로 크기마다 벡터로 그린다.
 *
 * <p>무거운 일(디코딩·그리기)은 동시 처리 수 제한 아래에서 한다.
 */
@Component
public class SiteIconProcessor {

    /** 올리는 파일의 한 변 하한(px) */
    public static final int MIN_SIDE = 512;

    private final ImageCodec codec;

    public SiteIconProcessor(ImageCodec codec) {
        this.codec = codec;
    }

    /**
     * 처리 결과.
     *
     * @param sourceSide 올린 그림의 한 변(px, 정사각이라 가로 = 세로). SVG 는 가장 큰 결과물의 한 변(512)
     * @param files      만든 PNG — 크기 종류({@link IconFile}) 선언 순서
     */
    public record Result(int sourceSide, Map<IconFile, byte[]> files) {
    }

    /** 올라온 PNG 의 바이트를 검사해 다섯 크기로 만든다. 규칙에 맞지 않으면 {@code ValidationException}({@code fields.file}) */
    public Result process(byte[] data) {
        if (data == null || data.length == 0) {
            throw ImageProblems.empty();
        }
        if (ImageFormat.sniff(data) != ImageFormat.PNG) {
            throw ImageProblems.file("site.icon.format");
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
        if (info.width() != info.height()) {
            throw ImageProblems.file("site.icon.notSquare", String.valueOf(info.width()), String.valueOf(info.height()));
        }
        if (info.width() < MIN_SIDE) {
            throw ImageProblems.file("site.icon.tooSmall", String.valueOf(MIN_SIDE), String.valueOf(info.width()), String.valueOf(info.height()));
        }
        try {
            Map<IconFile, byte[]> files = codec.limited(() -> render(data));
            return new Result(info.width(), files);
        } catch (ImageUnreadableException e) {
            throw ImageProblems.corrupt();
        }
    }

    /** SVG 소스를 검사해 그리고 다섯 크기로 만든다. 규칙에 맞지 않으면 {@code ValidationException}({@code fields.svgSource}) */
    public Result processSvg(String svgSource) {
        SvgInspector.Inspected inspected = SvgInspector.inspect(svgSource, true);
        IconFile[] sizes = IconFile.values();
        int[] sides = new int[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            sides[i] = sizes[i].side();
        }
        List<BufferedImage> drawn = codec.limited(() -> SvgRasterizer.rasterize(inspected.source(), sides));
        Map<IconFile, byte[]> files = new LinkedHashMap<>();
        for (int i = 0; i < sizes.length; i++) {
            files.put(sizes[i], ImageCodec.png(drawn.get(i), sizes[i].flatten()));
        }
        return new Result(IconFile.ICON_512.side(), files);
    }

    private Map<IconFile, byte[]> render(byte[] data) {
        BufferedImage source = codec.decode(data, ImageFormat.PNG);
        Map<IconFile, byte[]> files = new LinkedHashMap<>();
        for (IconFile file : IconFile.values()) {
            files.put(file, ImageCodec.png(ImageCodec.resize(source, file.side(), file.side()), file.flatten()));
        }
        return files;
    }
}
