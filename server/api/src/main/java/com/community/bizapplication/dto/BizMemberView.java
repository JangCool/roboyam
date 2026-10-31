package com.community.bizapplication.dto;

import com.community.bizapplication.dto.request.BizDealInfo;
import com.community.payment.dto.response.PaymentCheckoutView;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 사업자 센터 신청 건 응답 모양(user-web {@code useBusinessApi.types.ts} 의 신청 건 부분). ID 는 문자열, 날짜는 한국 날짜 {@code YYYY-MM-DD}(끝 포함), 시각은 ISO 순간.
 */
public final class BizMemberView {

    private BizMemberView() {
    }

    public record Amount(long supply, long vat, long total) {
    }

    /** 광고 소재 — 배너는 이미지·링크·대체 글, 한 줄은 문구·링크 */
    public record Creative(int version, String imageUrl, String imageName, String linkUrl, String altText, String text) {
    }

    /** 수동 결제 입금 안내 — {@code contract} WAITING·RECEIVED */
    public record Deposit(String bankName, String accountNo, String accountHolder, String depositorName, String contract, boolean depositConfirmed) {
    }

    /** 취소·환불 — {@code type} FULL_AUTO·PRORATED_REVIEW, {@code status} REQUESTED·APPROVED·DENIED */
    public record Refund(String type, String status, String reason, Instant requestedAt, long proposedAmount, Long refundAmount, String decisionReason) {
    }

    /** 처리 기록 — {@code actor} ME·OPERATOR·SYSTEM, {@code action} 코드(화면이 번역) */
    public record History(Instant at, String actor, String action, String note) {
    }

    public record Summary(String id, String kind, String title, String adType, String placement, String startDate, String endDate, String status, String phase,
                          String paymentMethod, Amount amount, boolean amountConfirmed, Instant paymentDueAt, Instant depositDueAt, Instant submittedAt,
                          boolean refundRequested) {
    }

    public record Detail(String id, String kind, String title, String adType, String placement, String startDate, String endDate, String status, String phase,
                         String paymentMethod, Amount amount, boolean amountConfirmed, Instant paymentDueAt, Instant depositDueAt, Instant submittedAt,
                         boolean refundRequested, Amount estimate, Amount confirmed, String adjustReason, Creative creative, BizDealInfo deal, String rejectCode,
                         String rejectReason, String takedownReason, Deposit deposit, Refund refund, List<History> history) {
    }

    public record Page(List<Summary> items, long total, Map<String, Long> counts) {
    }

    public record Created(String id) {
    }

    public record AdProduct(String type, long dailyPrice, int maxDays, int maxLeadDays, int capacity, int openDays, boolean accepting) {
    }

    public record DealPlacement(String placement, long dailyPrice, int capacity, int maxDays) {
    }

    public record DealProduct(long postPrice, List<DealPlacement> placements) {
    }

    public record AvailabilityDay(String date, int remaining, int capacity) {
    }

    public record DealLinkCheck(String host, String shopName, boolean blocked, String blockedReason, boolean affiliate) {
    }

    /** 결제 시작 — 결제 모듈 한 길({@code BIZ_APPLICATION}) */
    public record PaymentStart(String orderType, String orderRefId, Instant expiresAt, PaymentCheckoutView checkout) {
    }

    /** 올린 광고 이미지 — user-web {@code BusinessFile} */
    public record File(String fileId, String name, long size, String contentType, String previewUrl) {
    }
}
