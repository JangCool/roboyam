package com.community.ad.dto;

import com.community.bizapplication.dto.BizAdminView;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 광고 자리 관리(admin-web {@code /ads/slots} — {@code useAdSlotApi.types.ts}, docs/features/ad-slot-manager-contract.md 「API」) 응답 모양. 광고는 자리마다가 아니라 종류(상품)마다
 * 관리된다 — 종류 이름은 광고 일정표 유형({@code BizProduct}) 그대로. 기간은 반열림 {@code [startAt, endAt)} ISO 순간.
 */
public final class AdSlotAdminView {

    private AdSlotAdminView() {
    }

    /**
     * 종류 하나의 요약 — {@code running}·{@code limit} 은 일정표 유형 요약({@link AdAdminView.TypeSummary})과 같은 셈. {@code adsense} FILLS(나가는 자리를 애드센스가 다 채움)·PARTIAL(일부만)·EMPTY·NA(이 종류는
     * 나가는 자리에 애드센스 틀이 없음). {@code places} 는 이 종류가 나가는 노출 자리(광고 성과의 자리 이름) — 노출 자리 설정(글 보기 중간 유형 · 날개 양쪽 · 목록 한 줄)을 따른다.
     */
    public record Summary(String type, int running, int limit, String adsense, List<String> places) {
    }

    /** {@code GET /api/admin/ads/slots} — 일정표 유형 차례 */
    public record SummaryView(List<Summary> types) {
    }

    /** 카드의 광고주 — 일정표 광고주 키({@code adv-}·{@code biz-}, 이름만 광고주는 null) · 이름 · BUSINESS|ADVERTISER */
    public record Advertiser(String id, String name, String kind) {
    }

    /**
     * 광고 카드 한 장(드로어 카드와 상세). {@code status} 는 일정표의 보이는 상태 그대로(RUNNING·SCHEDULED·AWAITING_PAYMENT·AWAITING_DEPOSIT·TAKEN_DOWN), {@code imageUrl}·
     * {@code linkUrl} 은 지금(가장 높은) 소재 버전 값(일정표 상세의 {@code current} 소재와 같다), {@code remainingDays} 는 오늘(KST) 부터 끝 날짜까지(끝 포함, 끝났으면 0),
     * {@code impressions7d}·{@code clicks7d} 는 오늘 포함 최근 7일(KST) 광고 성과 합, {@code exampleUrl} 은 링크 호스트가 예시·시험용 주소인가.
     */
    public record Card(String id, String type, String title, String status, String takedownCause, String imageUrl, String text, BizAdminView.Period period,
                       int remainingDays, long impressions7d, long clicks7d, Advertiser advertiser, String paymentMethod, BizAdminView.Amount amount, String domain,
                       String linkUrl, boolean exampleUrl) {
    }

    /** 거르기마다 건수 — {@code AWAITING_DEPOSIT} 은 결제 대기({@code AWAITING_PAYMENT})를 함께 센다 */
    public record Counts(@JsonProperty("ALL") int all, @JsonProperty("RUNNING") int running, @JsonProperty("SCHEDULED") int scheduled,
                         @JsonProperty("AWAITING_DEPOSIT") int awaitingDeposit, @JsonProperty("TAKEN_DOWN") int takenDown) {
    }

    /** {@code GET /api/admin/ads/slots/{type}} — 진행 중 → 예약 → 결제·입금 대기 → 내림, 같은 상태는 시작이 빠른 차례 */
    public record ListView(String type, List<Card> items, Counts counts) {
    }

    /** 못 내린 건 — 서버 오류 문구 그대로(요청 언어) */
    public record Failure(String id, String message) {
    }

    /** {@code POST /api/admin/ads/takedown} 결과 */
    public record TakedownResult(int takenDown, List<Failure> failed) {
    }
}
