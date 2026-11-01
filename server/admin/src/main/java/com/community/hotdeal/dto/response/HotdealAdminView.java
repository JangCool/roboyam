package com.community.hotdeal.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 관리자 핫딜 글 API({@code /api/admin/hotdeals})의 응답 모양 — admin-web {@code useHotdealApi.types.ts} 의 칸 이름·null 규칙 그대로(서버·화면 공용 API 메모 0절).
 * 한 화면(핫딜 글 목록·서랍)의 작은 모양들이라 한 곳에 둔다(사업자 신청 심사 {@code BizAdminView} 와 같은 방식). 딜 칸은 공개 쪽과 같은 {@link PostDealView}.
 */
public final class HotdealAdminView {

    private HotdealAdminView() {
    }

    public record BoardRef(String id, String slug, String name, String nameEn) {
    }

    public record PrefixRef(String id, String name) {
    }

    /** 작성자 — 탈퇴하면 {@code withdrawn}(닉네임은 남은 프로필 값, 없으면 빈 글자) */
    public record Author(String id, String nickname, boolean withdrawn) {
    }

    /**
     * 목록 한 줄.
     *
     * @param openReports 처리 대기 신고 수(글 관리 목록과 같은 뜻)
     * @param blockedHost 지금 링크 규칙에서 차단된 호스트(상품·최종 — 진행 중 글만, 그 밖 null)
     * @param clickCount  구매 링크 클릭 수(하루 같은 방문자 1번 — 관리자 덧붙임 선택 칸)
     * @param source      출처 MEMBER · BUSINESS(= {@code deal.source}, V123)
     * @param business    사업자 글이면 상호와 그 글을 올린 신청 건(핫딜 심사 {@code /hotdeal-reviews?id=}), 회원 글 null
     */
    public record Row(String postId, String title, BoardRef board, PrefixRef prefix, Author author, Instant createdAt, int openReports, String blockedHost,
                      PostDealView deal, long clickCount, String source, BusinessRef business) {
    }

    /** 사업자 핫딜 글의 사업자 — 상호 스냅샷 · 신청 건 ID */
    public record BusinessRef(String name, String applicationId) {
    }

    /**
     * 한 쪽.
     *
     * @param counts             상태별 건수 — 상태 조건만 뺀 같은 조건(ACTIVE·FLAGGED·ENDED·EXPIRED·ALL)
     * @param blockedActiveCount 차단 도메인 진행 중 건수 — 상태·차단 조건을 뺀 같은 조건
     */
    public record Page(long total, int page, int size, List<Row> items, Map<String, Long> counts, long blockedActiveCount) {
    }

    /** 같은 딜 한 줄(진행 중 · 공개 글) */
    public record SameDeal(String postId, String title, long dealPrice, Instant createdAt, String view) {
    }

    /**
     * 상세(서랍).
     *
     * @param endedByName 종료한 작성자·운영진 표시 이름(자동이면 null) — 운영자는 운영자 이름, 게시판 매니저·스텝·작성자는 회원 닉네임
     * @param endedByRole 종료한 사람 종류 OPERATOR · BOARD_MANAGER · BOARD_STAFF · AUTHOR(자동 종료·진행 중이면 null)
     * @param endReason   운영진이 종료할 때 적은 사유
     * @param keptAt      작성자가 마지막으로 「아직 진행 중」을 누른 시각
     * @param publicUrl   공개 글 주소(사이트 공개 주소를 정하지 않았으면 null)
     * @param sameDeals   진행 중인 같은 딜 — 최근 것부터 5개까지
     */
    public record Detail(Row row, String endedByName, String endedByRole, String endReason, Instant keptAt, String publicUrl, List<SameDeal> sameDeals, String affiliateMode) {
    }

    /** 수정 이력의 칸 하나 — 값은 그 칸의 저장 모양(금액 정수 · 시각 ISO · 코드 글자 · 불 · 없으면 null) */
    public record RevisionChange(String field, Object before, Object after) {
    }

    /** 누가 — AUTHOR · MANAGER */
    public record RevisionActor(String kind, String name) {
    }

    public record Revision(String id, Instant at, RevisionActor actor, List<RevisionChange> changes, boolean hostChanged) {
    }

    /** 수정 이력(최근 것부터) + 보존 개월 수 */
    public record RevisionList(List<Revision> items, int retentionMonths) {
    }

    /** 핫딜 게시판 고르기 */
    public record BoardOption(String id, String slug, String name, String nameEn, boolean active, List<PrefixRef> prefixes) {
    }

    public record BoardOptions(List<BoardOption> boards) {
    }
}
