package com.community.rewardevent.dto.response;

/** 댓글 후보 미리 보기(admin-web {@code RewardCommentPreview}) — 기간 안 댓글 수(지운 댓글 제외) · 1인 1회로 센 후보 수 · 뺀 수 */
public record RewardCommentPreviewView(String postId, String postTitle, String authorNickname, long commentCount, long candidateCount, Excluded excluded) {

    public record Excluded(long author, long operators, long suspended, long withdrawn) {
    }
}
