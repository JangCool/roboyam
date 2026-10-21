package com.community.site.dto;

/**
 * 머리를 채운 {@code index.html} 한 장.
 *
 * @param body UTF-8 바이트
 * @param etag 본문 지문(따옴표 없는 16진수)
 */
public record RenderedIndex(byte[] body, String etag) {

    @Override
    public String toString() {
        return "RenderedIndex[bytes=" + body.length + ", etag=" + etag + "]";
    }
}
