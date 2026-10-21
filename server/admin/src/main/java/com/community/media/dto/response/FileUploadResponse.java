package com.community.media.dto.response;

/**
 * 파일 올리기 결과. 올린 파일은 어디에 쓰겠다고 지정하기 전까지(예: {@code PUT /api/admin/site/logo}) 아무 데도 쓰이지 않는다.
 *
 * @param fileId 파일 번호(문자열)
 */
public record FileUploadResponse(String fileId) {
}
