package com.community.post.dto;

import com.community.post.entity.PostEntity;
import com.community.report.entity.ReportReasons;
import java.time.Instant;

/**
 * 글 목록 한 줄을 그리는 데 필요한 칸만 읽은 값(본문은 읽지 않는다) — {@code PostRepositoryQDSL} 의 투영(그래서 감싼 타입이다).
 * 작성 IP 는 비회원 글의 앞 두 자리 표시에만 쓰고 응답에 원문을 싣지 않는다.
 *
 * @param hiddenReason 숨김 까닭(REPORTED · ILLEGAL · RIGHTS · STAFF) — 공개 응답에는 {@link #publicHiddenReason} 으로만 낸다
 */
public record PostRowData(Long id, Long boardId, Long prefixId, Long authorId, String guestName, String authorIp, String title, Boolean hasImage,
                          Boolean adult, Integer viewCount, Integer likeCount, Integer commentCount, Instant pinnedAt, Instant hiddenAt, Instant hiddenUntil,
                          String hiddenReason, Instant createdAt) {

    public boolean hiddenAt(Instant now) {
        return hiddenAt != null && (hiddenUntil == null || hiddenUntil.isAfter(now));
    }

    /**
     * 공개 목록의 숨김 까닭 — 숨기지 않았으면 null. 운영자가 숨긴 글은 게시판 옵션 「운영자가 숨긴 글임을 밝히기」({@code staffHiddenNotice})가 켜졌을 때만
     * {@code STAFF}, 그 밖(신고 숨김·옵션 끔)은 모두 {@code REPORTED}(docs/features/post-admin-contract.md 「결정」).
     */
    public String publicHiddenReason(Instant now, boolean staffHiddenNotice) {
        if (!hiddenAt(now)) {
            return null;
        }
        return staffHiddenNotice && PostEntity.STAFF_HIDDEN.equals(hiddenReason) ? ReportReasons.HIDDEN_STAFF : ReportReasons.HIDDEN_REPORTED;
    }
}
