package com.community.post.dto.request;

/**
 * 글쓰기·고치기의 대표 썸네일({@code thumbnail} — user-web {@code PostThumbnailInput}, docs/features/list-thumbnail-contract.md 「2단계」). 게시판 정책 {@code authorThumbnailEnabled}
 * 가 켜진 게시판의 회원 글에서만 받고, 꺼졌거나 비회원·소모임 글이면 칸을 무시한다(오류 아님).
 *
 * @param mode    AUTO(자동 — 첫 이미지) · IMAGE(본문 이미지 하나) · UPLOAD(따로 올린 그림) · NONE(대표 이미지 없음). 모르는 값 400 {@code fields.thumbnail}
 * @param imageId IMAGE 일 때 — 저장할 본문에 있는 이미지 ID(아니면 400 {@code fields.thumbnail})
 * @param fileId  UPLOAD 일 때 — {@code POST /api/images/thumbnails} 로 올린 그림 ID(내가 올린 쓰이지 않은 것, 또는 이 글이 이미 쓰는 그 그림 — 아니면 400 {@code fields.thumbnail})
 */
public record PostThumbnailInput(String mode, String imageId, String fileId) {
}
