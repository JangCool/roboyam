package com.community.popular.dto.response;

import java.time.Instant;

/**
 * 인기글 설정 화면의 미리보기 한 줄 — 실시간(REALTIME) 순위 스냅샷의 상위 10개(지금 볼 수 없는 글·뺀 글은 건너뛰고 번호는 스냅샷 그대로).
 *
 * @param score 계산 시점의 점수. 추천·댓글·조회는 지금 값
 * @param fill  채운 줄의 출처 YESTERDAY · LAST_WEEK, 자기 기간 글이면 null
 */
public record PopularPreviewItemView(int rank, String postId, String title, String boardName, String boardNameEn, double score, int likeCount,
                                     int commentCount, int viewCount, Instant createdAt, String fill) {
}
