package com.community.post.dto.response;

/**
 * 글 보기({@code GET·PUT /api/posts/{id}})의 대표 썸네일 — 글쓴이 본인에게만 싣는다(고치기 화면이 쓴다, 그 밖의 보는 사람·비회원 글·소모임 글은 null). user-web {@code PostThumbnailView}.
 *
 * @param mode    AUTO · IMAGE · UPLOAD · NONE — 고른 이미지를 본문에서 뺐으면 AUTO 로 보인다
 * @param setBy   AUTHOR · OPERATOR, 자동이면 null. OPERATOR 면 글쓴이는 바꿀 수 없다(저장하면 409 {@code THUMBNAIL_LOCKED})
 * @param imageId IMAGE 일 때 본문 이미지 ID
 * @param fileId  UPLOAD 일 때 올린 그림 ID
 * @param url     지금 목록에 보이는 썸네일 주소, 없으면 null
 */
public record PostThumbnailView(String mode, String setBy, String imageId, String fileId, String url) {
}
