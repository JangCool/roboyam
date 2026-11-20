package com.community.jjal.dto.response;

/**
 * 내 짤 하나(user-web {@code MyJjalItem}).
 *
 * @param id       짤 공개 ID
 * @param url      그림 주소 — 내려간 짤이면 null(그림을 내지 않는다)
 * @param width    저장본 가로(px, 긴 변 300 이하)
 * @param height   저장본 세로(px)
 * @param animated 움짤(GIF 2장 이상)
 * @param source   UPLOAD(내가 올림) · COLLECT(담음)
 * @param blocked  신고로 내려간 짤
 */
public record MyJjalItemView(String id, String url, int width, int height, boolean animated, String source, boolean blocked) {
}
