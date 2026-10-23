package com.community.media.dto.response;

/**
 * {@code POST /api/images} 응답 — 에디터는 본문에 {@code <img data-image-id="{id}">} 표식만 넣으면 되고(주소·크기는 글을 저장할 때 서버가 채운다), 고르는 동안
 * 미리 보이려고 {@code url}·크기를 쓴다.
 *
 * @param id  이미지(파일) ID — Snowflake, 문자열
 * @param url 공개 주소({@code /media/post-image/…})
 */
public record ImageUploadResponse(String id, String url, int width, int height) {
}
