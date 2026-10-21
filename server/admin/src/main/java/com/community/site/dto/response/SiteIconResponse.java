package com.community.site.dto.response;

/**
 * 사이트 아이콘 상태.
 *
 * @param custom            올려 둔 아이콘이 있는가. false 면 기본 아이콘이 쓰인다
 * @param previewUrl        올려 둔 아이콘(512px PNG)의 주소. {@code custom=false} 면 null
 * @param defaultPreviewUrl 기본 아이콘의 주소
 */
public record SiteIconResponse(boolean custom, String previewUrl, String defaultPreviewUrl) {
}
