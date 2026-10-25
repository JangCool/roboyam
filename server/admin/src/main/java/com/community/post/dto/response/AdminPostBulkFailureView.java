package com.community.post.dto.response;

/** 일괄 조치에서 처리하지 못한 글 — {@code code}: POST_NOT_FOUND · POST_STATE_INVALID(그 밖 오류는 그 코드) */
public record AdminPostBulkFailureView(String id, String code) {
}
