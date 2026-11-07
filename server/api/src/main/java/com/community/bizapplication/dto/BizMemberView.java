package com.community.bizapplication.dto;

import com.community.bizapplication.dto.request.BizDealInfo;
import com.community.bizapplication.dto.response.BizDealPostView;
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
    public record Creative(int version, String imageUrl, String imageName, String linkUrl, String altText, String text, String imageFileId) {
    }

    /** 수동 결제 입금 안내 — {@code contract} WAITING·RECEIVED */
    public record Deposit(String bankName, String accountNo, String accountHolder, String depositorName, String contract, boolean depositConfirmed) {
    }

    /**
     * 취소·환불 — {@code type} FULL_AUTO·PRORATED_REVIEW·STOP_PRORATED(V127 광고주 요청 중단 — 예상액 {@code proposedAmount} = 일할 {@code proratedAmount} − 수수료
     * {@code feeAmount}, 비율 {@code feeRate}·기준 시각 {@code basisAt}. 다른 종류면 네 칸 null)·CLAIM(운영자 추가 환불 — V128, 바로 승인 · 사유 {@code reason}·{@code decisionReason} 은 null — 관리자만 봄),
     * {@code status} REQUESTED·APPROVED·DENIED
     */
    public record Refund(String type, String status, String reason, Instant requestedAt, long proposedAmount, Long refundAmount, String decisionReason,
                         Long proratedAmount, Integer feeRate, Long feeAmount, Instant basisAt) {
    }

    /**
     * 일시 정지·중단(V127 — biz-pause-contract 「데이터 모양」) — 정지할 수 있는 종류(날짜형 자리·사업자 핫딜 글)의 결제 뒤 건만, 아니면 상세 {@code pause} = null.
     *
     * @param pausedBy        지금 정지를 누가 — ME · OPERATOR(정지 중이 아니면 null)
     * @param channel         운영팀 대신 처리면 요청 경로 PHONE·EMAIL·OTHER(공개엔 메모 없음)
     * @param proratableTotal 일할 대상 금액(결제액 기준 — 광고 = 결제액, 핫딜 글 = 노출 자리 몫, 노출 없으면 0)
     * @param feeRate         이 건에 고정된 중도 해지 수수료율(%)
     */
    public record Pause(int used, int limit, Instant pausedAt, String pausedBy, String channel, int pausedMinutes, Instant stoppedAt, Instant refundFrom,
                        long proratableTotal, int feeRate) {
    }

    /** {@code GET /api/business/refund-policy} — 지금 환불 규정 버전 + 중도 해지 수수료율(신청 화면 동의 문구) */
    public record RefundPolicy(String version, int stopFeeRate) {
    }

    /** 처리 기록 — {@code actor} ME·OPERATOR·SYSTEM, {@code action} 코드(화면이 번역) */
    public record History(Instant at, String actor, String action, String note) {
    }

    /** @param takedownCause 내림({@code TAKEN_DOWN})·중단 뒤 취소의 까닭 EMERGENCY·SUSPENDED·ADVERTISER_PAUSE·ADVERTISER_STOP(V127) — 내려간 적 없으면 null */
    public record Summary(String id, String kind, String title, String adType, String placement, String startDate, String endDate, String status, String phase,
                          String paymentMethod, Amount amount, boolean amountConfirmed, Instant paymentDueAt, Instant depositDueAt, Instant submittedAt,
                          boolean refundRequested, String takedownCause) {
    }

    /**
     * @param refunds      환불 줄 전부(오래된 차례 — 중단 환불·추가 환불 등). {@code refund} 는 그중 가장 최근 줄
     * @param refundTotals 결제액·승인된 환불 합(결제 전이면 null)
     */
    public record Detail(String id, String kind, String title, String adType, String placement, String startDate, String endDate, String status, String phase,
                         String paymentMethod, Amount amount, boolean amountConfirmed, Instant paymentDueAt, Instant depositDueAt, Instant submittedAt,
                         boolean refundRequested, String takedownCause, Amount estimate, Amount confirmed, String adjustReason, Creative creative, BizDealInfo deal,
                         String rejectCode, String rejectReason, String takedownReason, Deposit deposit, Refund refund, List<History> history,
                         BizDealPostView post, Pause pause, List<RefundLine> refunds, RefundTotals refundTotals) {
    }

    /**
     * 공개 환불 내역 한 줄(biz-pause-contract 「추가 환불」 — 사유·운영자 메모는 싣지 않는다).
     *
     * @param amount     승인 금액(승인 뒤) 또는 제안 금액(심사 중·거절)
     * @param returnedAt 돈이 실제로 돌아간 시각 — 온라인 PG 취소 완료 · 수동 송금 완료(아직이면 null)
     */
    public record RefundLine(String type, String status, long amount, Instant requestedAt, Instant decidedAt, Instant returnedAt) {
    }

    /** 결제액(온라인 결제액 · 수동 입금 확인 금액)과 승인된 환불 합 */
    public record RefundTotals(long paid, long refunded) {
    }

    public record Page(List<Summary> items, long total, Map<String, Long> counts) {
    }

    public record Created(String id) {
    }

    public record AdProduct(String type, long dailyPrice, int maxDays, int maxLeadDays, int capacity, int openDays, boolean accepting, boolean onlinePayment) {
    }

    /** 핫딜 노출 자리 — {@code accepting} 은 요금표 받기(끄면 신청 409 {@code BIZ_PRODUCT_CLOSED}) */
    public record DealPlacement(String placement, long dailyPrice, int capacity, int maxDays, boolean accepting) {
    }

    /** 사업자 핫딜 요금 — {@code accepting} 은 글 게시({@code HOTDEAL_POST}) 받기. 꺼져 있으면 화면은 신청 대신 「준비 중」 안내 */
    public record DealProduct(long postPrice, boolean accepting, boolean onlinePayment, List<DealPlacement> placements, List<DealBoard> boards) {
    }

    /** 받는 핫딜 게시판(2차 — 켜진 DEAL 게시판, 말머리는 켜짐·운영진 전용 아님) — user-web {@code BizDealBoard} */
    public record DealBoard(String id, String slug, String name, List<Prefix> prefixes) {
    }

    public record Prefix(String id, String name) {
    }

    public record AvailabilityDay(String date, int remaining, int capacity) {
    }

    public record DealLinkCheck(String host, String shopName, boolean blocked, String blockedReason, boolean affiliate) {
    }

    /**
     * 결제 시작 — 결제 모듈 한 길({@code BIZ_APPLICATION}). {@code customer} = 결제창(포트원) 구매자 칸 — 사업자 신원의 상호·연락처 평문·이메일(본인 건만, 응답 no-store).
     * 공동구매 {@code GroupBuyMemberView.PaymentStart.customer} 와 같은 모양.
     */
    public record PaymentStart(String orderType, String orderRefId, Instant expiresAt, PaymentCheckoutView checkout, Customer customer) {
    }

    /** 결제창 구매자 — 이름(상호) · 전화번호(숫자) · 이메일 */
    public record Customer(String fullName, String phoneNumber, String email) {
    }

    /** 올린 광고 이미지 — user-web {@code BusinessFile} */
    public record File(String fileId, String name, long size, String contentType, String previewUrl) {
    }
}
