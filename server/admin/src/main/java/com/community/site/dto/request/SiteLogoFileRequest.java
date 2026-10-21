package com.community.site.dto.request;

/**
 * 올려 둔 그림을 로고로 삼는 요청.
 *
 * @param fileId {@code POST /api/admin/files} 가 돌려준 파일 번호(문자열)
 */
public record SiteLogoFileRequest(String fileId) {
}
