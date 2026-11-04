package com.community.analytics.dto;

import java.time.Instant;

/**
 * 공개 {@code POST /api/visits/forget}(방문 기록 삭제 요청) 의 모양 — user-web {@code useVisitForgetApi.types.ts}(site-analytics-contract 「2단계 — 정보주체 요청」).
 */
public final class VisitForget {

    private VisitForget() {
    }

    /** 본문 — {@code visitorId} = 이 브라우저의 방문자 ID(UUID). 틀리면 400 {@code fields.visitorId} */
    public record Request(String visitorId) {
    }

    /** 202 응답 — 접수만 했다(지우기는 DB 크론이 몇 분 안에). {@code requestId} 는 관리자 「정보주체 요청」 화면의 번호와 같다 */
    public record Response(String requestId, Instant receivedAt) {
    }
}
