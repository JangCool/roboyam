package com.community.group.dto.response;

/**
 * {@code POST /api/groups/images} 응답 — 개설 신청({@code imageFileId})·정보 수정({@code imageFileId})이 {@code fileId} 로 붙인다. 화면은 {@code url} 로 미리 본다.
 *
 * @param fileId 파일 ID(Snowflake, 문자열)
 * @param url    공개 주소({@code /media/group/…})
 * @param width  다시 만든 뒤의 너비(px)
 * @param height 다시 만든 뒤의 높이(px)
 */
public record GroupImageUploadView(String fileId, String url, int width, int height) {
}
