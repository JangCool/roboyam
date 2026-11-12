package com.community.site.service;

import com.community.media.service.ImageCodec;
import com.community.site.service.SiteMediaKeys.IconFile;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 사이트 아이콘·링크 미리보기 이미지를 저장할 PNG 로 그리는 규칙 — 관리자 올리기({@code SiteIconProcessor}·{@code SiteOgImageProcessor})와
 * 설치의 데모 데이터(아이로빗 브랜드 그림)가 같은 결과를 만들도록 한 곳에 둔다. 검사(형식·크기·비율)는 부르는 쪽이 먼저 한다.
 */
public final class SiteBrandRender {

    private SiteBrandRender() {
    }

    /** 정사각 원본에서 아이콘 다섯 장(32·48·180 흰 배경·192·512)을 만든다 — {@link IconFile} 선언 순서 */
    public static Map<IconFile, byte[]> iconFiles(BufferedImage source) {
        Map<IconFile, byte[]> files = new LinkedHashMap<>();
        for (IconFile file : IconFile.values()) {
            files.put(file, ImageCodec.png(ImageCodec.resize(source, file.side(), file.side()), file.flatten()));
        }
        return files;
    }

    /** 원본을 1200×630 불투명 PNG 로 맞춘다(투명은 흰색) */
    public static byte[] ogPng(BufferedImage source) {
        return ImageCodec.png(ImageCodec.resize(source, SiteMediaKeys.OG_WIDTH, SiteMediaKeys.OG_HEIGHT), true);
    }
}
