package com.community.site.dto.response;

/**
 * 링크 미리보기 이미지(og:image) 상태.
 *
 * @param configured 올려 둔 이미지가 있는가. false 면 기본 이미지가 쓰인다
 * @param url        올려 둔 이미지(1200×630 PNG)의 주소. {@code configured=false} 면 null
 * @param width      저장된 이미지의 가로(px). {@code configured=false} 면 null
 * @param height     저장된 이미지의 세로(px). {@code configured=false} 면 null
 * @param defaultUrl 기본 이미지의 주소
 */
public record SiteOgImageResponse(boolean configured, String url, Integer width, Integer height, String defaultUrl) {
}
