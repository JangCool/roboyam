package com.community.group.dto.response;

/**
 * 소모임 대표 이미지(V154) — 상세 {@code image}·내 개설 신청 {@code image}. 목록 줄은 주소만({@code GroupRowView.imageUrl}).
 *
 * @param fileId 파일 ID(Snowflake, 문자열) — 정보 수정 때 그대로 보내면 이미지를 그대로 둔다
 * @param url    공개 주소({@code /media/group/…}) — 움직이는 GIF·WebP 는 원본 그대로
 */
public record GroupImageView(String fileId, String url) {
}
