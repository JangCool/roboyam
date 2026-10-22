package com.community.post.dto;

import java.time.Instant;

/**
 * 글 목록 한 줄을 그리는 데 필요한 칸만 읽은 값(본문은 읽지 않는다) — {@code PostRepositoryQDSL} 의 투영(그래서 감싼 타입이다).
 * 작성 IP 는 비회원 글의 앞 두 자리 표시에만 쓰고 응답에 원문을 싣지 않는다.
 */
public record PostRowData(Long id, Long boardId, Long prefixId, Long authorId, String guestName, String authorIp, String title, Boolean hasImage,
                          Boolean adult, Integer viewCount, Integer likeCount, Integer commentCount, Instant pinnedAt, Instant hiddenAt, Instant hiddenUntil,
                          Instant createdAt) {

    public boolean hiddenAt(Instant now) {
        return hiddenAt != null && (hiddenUntil == null || hiddenUntil.isAfter(now));
    }
}
