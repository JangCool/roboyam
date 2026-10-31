package com.community.ad.dto;

import com.community.ad.service.AdAdvertiserService;
import com.community.bizapplication.dto.BizAdminView;
import java.time.Instant;
import java.util.List;

/**
 * 광고 일정표(admin-web {@code useAdApi.types.ts}) 응답 모양. 장부 = 신청 건이라 ID·번호·처리 기록 코드는 신청 건과 같다. 기간은 반열림 {@code [startAt, endAt)} ISO 순간.
 * 목업과 다르게·더 정한 것: {@code status} 에 심사 대기 {@code SUBMITTED} 와 승인 뒤 취소 {@code CANCELED} 가 있다 · {@code history.action} 은 신청 건 처리 기록 코드(화면이 번역) ·
 * {@code domain}(AD·HOTDEAL — 「심사에서 열기」 주소) · {@code takedown.mail}(SENT·FAILED·NO_ADDRESS) · 운영자 등록 건의 {@code proposedSupply}·{@code registrant}.
 */
public final class AdAdminView {

    private AdAdminView() {
    }

    /** 유형 요약 — 한도 · 진행 · 예약(시작 전) · 결제·입금 대기(자리를 이미 잡고 있다) */
    public record TypeSummary(String type, int limit, int running, int scheduled, int awaitingPayment) {
    }

    public record DaySlot(String date, int remaining, int limit) {
    }

    /** 달력 — 유형 요약 + 그 달 날짜별 남은 칸 + 그 달에 걸친 장부 건(이름·상태·기간) */
    public record Calendar(TypeSummary summary, List<DaySlot> days, List<Row> bookings) {
    }

    /**
     * 장부 한 줄. {@code source} APPLICATION(사업자 신청)·OPERATOR(운영자 등록), {@code paymentMethod} 승인 전이면 희망 방식.
     */
    public record Row(String id, String type, AdAdvertiserService.Option advertiser, BizAdminView.Period period, String status, String paymentMethod, int weight,
                      String source, String applicationNo, Instant lastActionAt, String domain) {
    }

    public record Page(List<Row> items, long total) {
    }

    /** 소재 버전 — 승인 시각·승인자는 승인 때 고정된 버전만 */
    public record Creative(int version, String imageUrl, String text, String linkUrl, String altText, Instant approvedAt, String approvedBy, boolean current) {
    }

    /** 내림 — {@code cause} EMERGENCY(긴급)·SUSPENDED(사업자 정지), {@code mail} 광고주 메일 결과(보내지 않았으면 null) */
    public record Takedown(Instant at, String cause, String reason, String by, boolean mailSent, String mail) {
    }

    public record History(Instant at, String action, String actor, String note) {
    }

    /** 장부 상세 — {@link Row} 칸 + 금액 · 소재 버전 · 결제 · 계약서 · 입금 · 내림 · 환불(가장 최근) · 처리 기록 */
    public record Detail(String id, String type, AdAdvertiserService.Option advertiser, BizAdminView.Period period, String status, String paymentMethod, int weight,
                         String source, String applicationNo, Instant lastActionAt, String domain, BizAdminView.Amount amount, Long proposedSupply,
                         List<Creative> creatives, BizAdminView.OnlinePayment onlinePayment, List<BizAdminView.Contract> contracts, BizAdminView.Deposit deposit,
                         Takedown takedown, BizAdminView.Refund refund, String memo, BizAdminView.Registrant registrant, List<History> history) {
    }

    /** 기간 판정 — 가장 빠듯한 날의 남은 칸 · 꽉 찬 날 · 요금표 계산 */
    public record Availability(int remaining, int limit, List<String> fullDates, BizAdminView.Quote quote) {
    }

    /** 운영자가 올린 광고 이미지 */
    public record Image(String id, String fileName, long size, String mimeType, String url) {
    }
}
