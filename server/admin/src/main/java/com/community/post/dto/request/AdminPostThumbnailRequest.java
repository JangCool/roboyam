package com.community.post.dto.request;

/**
 * {@code PUT /api/admin/posts/{id}/thumbnail}(admin-web {@code AdminPostThumbnailRequest}, list-thumbnail-contract 「2단계」) — 운영자가 글의 대표 썸네일을 정한다.
 *
 * @param mode    AUTO(운영자·글쓴이 설정을 모두 지우고 자동으로 — 잠금이 풀린다) · IMAGE · UPLOAD · NONE. 모르는 값 400 {@code fields.mode}
 * @param imageId IMAGE 일 때 — 그 글에 쓰이는 본문 이미지 ID(아니면 400 {@code fields.imageId})
 * @param fileId  UPLOAD 일 때 — {@code POST /api/admin/files}({@code purpose=POST_THUMBNAIL})로 올린 파일 ID(아니면 400 {@code fields.fileId})
 */
public record AdminPostThumbnailRequest(String mode, String imageId, String fileId) {
}
