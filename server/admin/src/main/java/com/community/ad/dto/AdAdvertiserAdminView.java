package com.community.ad.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 광고주 관리(admin-web {@code useAdAdvertiserApi.types.ts} — 모양 정본) 응답. 광고주 ID 는 직접 등록 {@code adv-<id>} · 사업자 {@code biz-<신원 id>}(ad-advertiser-contract 규칙 1).
 * 연락처는 가린 값만 싣는다(평문은 {@code POST /api/admin/ads/advertisers/{id}/reveal}).
 */
public final class AdAdvertiserAdminView {

    private AdAdvertiserAdminView() {
    }

    /** 연결된 사업자 신원 — {@code status} 신원 상태, {@code adService} 광고 서비스 줄 상태(없으면 OFF) */
    public record Business(String id, String name, String maskedRegNo, String status, String adService) {
    }

    /** 건 수 — 진행 · 예약(시작 전) · 대기(심사·결제·입금) · 전체(취소 포함) */
    public record Counts(int running, int scheduled, int waiting, int total) {
    }

    /** 목록 한 줄 — {@code state} ACTIVE·ARCHIVED(사업자는 늘 ACTIVE), {@code paidTotal} 누적 결제(VAT 포함, 환불을 빼지 않는다), {@code lastAdAt} 이미 시작한 결제 뒤 건의 가장 늦은 시작 */
    public record Row(String id, String kind, String name, String contactMasked, Business business, String state, Counts counts, long paidTotal, Instant lastAdAt,
                      Instant createdAt) {
    }

    /** 쪽 — {@code counts} 는 종류 거르기 전(상태·찾기는 따른다) 종류별 수 {@code ALL}·{@code BUSINESS}·{@code ADVERTISER} */
    public record Page(List<Row> items, long total, Map<String, Long> counts) {
    }

    public record Masked(String contactName, String contactPhone, String email) {
    }

    /** 결제·환불 합계(VAT 포함) — 결제 = 온라인 + 수동(입금 확인), 환불 = 승인된 환불 합 */
    public record Money(long paid, long online, long manual, long refunded) {
    }

    /** 광고 건 한 줄 — 일정표 {@link AdAdminView.Row} 칸 + 그 건의 결제·환불 */
    public record Booking(String id, String type, com.community.ad.service.AdAdvertiserService.Option advertiser, com.community.bizapplication.dto.BizAdminView.Period period,
                          String status, String paymentMethod, int weight, String source, String applicationNo, Instant lastActionAt, String domain, String takedownCause,
                          long paid, long refunded) {

        public static Booking of(AdAdminView.Row r, long paid, long refunded) {
            return new Booking(r.id(), r.type(), r.advertiser(), r.period(), r.status(), r.paymentMethod(), r.weight(), r.source(), r.applicationNo(), r.lastActionAt(),
                    r.domain(), r.takedownCause(), paid, refunded);
        }
    }

    /** 연락처 평문 열람 기록 */
    public record View(Instant at, String actor, String reason) {
    }

    /** 처리 기록 — {@code action} CREATE·UPDATE·ARCHIVE·UNARCHIVE, {@code fields} 바뀐 칸 이름(값은 없다) */
    public record Event(Instant at, String action, String actor, List<String> fields) {
    }

    /**
     * 상세 — {@link Row} 칸 + 가린 값과 그 원천({@code contactSource} ADVERTISER 광고 담당자 칸 · BUSINESS 사업자 신원 · 없으면 null) · 메모 · 광고 건(최근 시작 차례 50) · 돈 ·
     * 열람 기록(최근 20) · 처리 기록 · 서버 판정 {@code deletable}·{@code archivable}.
     */
    public record Detail(String id, String kind, String name, String contactMasked, Business business, String state, Counts counts, long paidTotal, Instant lastAdAt,
                         Instant createdAt, Masked masked, String contactSource, String memo, List<Booking> bookings, Money money, List<View> views,
                         List<Event> history, boolean deletable, boolean archivable) {
    }
}
