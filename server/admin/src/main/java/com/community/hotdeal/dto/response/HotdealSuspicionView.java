package com.community.hotdeal.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 관리자 위장 홍보 의심 후보({@code /api/admin/hotdeal-suspicions}) 응답 모양 — admin-web {@code useHotdealSuspicionApi.types.ts} 그대로.
 * 회원 후보(MEMBER)는 {@code member}·{@code othersCount}, 판매처 후보(STORE)는 {@code memberCount}·{@code otherRatioPercent} 만 싣는다(종류에 없는 칸은 나가지 않는다).
 */
public final class HotdealSuspicionView {

    private HotdealSuspicionView() {
    }

    public record Member(String id, String nickname, boolean withdrawn) {
    }

    /**
     * 후보 한 줄.
     *
     * @param host        판매처 호스트(정규화 — 최종 호스트, 없으면 호스트)
     * @param storeName   판매처 이름(글들의 쇼핑몰 이름 가운데 가장 많은 것)
     * @param postCount   기간 안 이 판매처 글 수(회원 후보는 그 회원의 글만)
     * @param windowDays  감지 기준 기간(일)
     * @param dismissedBy 무시한 운영자 이름
     */
    public record Suspicion(String id, String kind, String host, String storeName, int postCount, int windowDays, Instant firstDetectedAt, Instant lastPostAt,
                            String status, String dismissedBy, Instant dismissedAt, String note,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Member member,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Integer othersCount,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Integer memberCount,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Integer otherRatioPercent) {
    }

    /**
     * 한 쪽.
     *
     * @param counts     상태별 건수(종류 조건 포함) — OPEN·DISMISSED
     * @param kindCounts 종류별 건수(상태 조건 포함) — ALL·MEMBER·STORE
     */
    public record Page(long total, int page, int size, List<Suspicion> items, Map<String, Long> counts, Map<String, Long> kindCounts) {
    }

    public record Post(String postId, String title, HotdealAdminView.BoardRef board, Member author, long dealPrice, String view, Instant createdAt, int openReports) {
    }

    /** 판매처 후보의 회원 한 명 — 기간 안 이 판매처 글 수 · 다른 판매처 핫딜 수 */
    public record StoreMember(Member member, long postCount, long otherDealCount) {
    }

    /** 상세 — 회원 후보는 그 회원의 그 판매처 글 최근 10개, 판매처 후보는 그 판매처 글 최근 30개 + 회원 요약(회원 후보는 빈 목록) */
    public record Detail(Suspicion suspicion, List<Post> posts, List<StoreMember> members) {
    }
}
