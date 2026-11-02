package com.community.bizapplication.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 사업자 신청 건 심사(admin-web {@code useBizApplicationApi.types.ts}) 응답 모양. 기간은 반열림 {@code [startAt, endAt)} ISO 순간.
 * 설계 메모와 같게 바뀐 것: {@code refund.status} REQUESTED·APPROVED·DENIED, {@code history.action} 은 코드(화면이 번역)·{@code actor} 는 표시 이름, 환불 줄에 송금 상태.
 */
public final class BizAdminView {

    private BizAdminView() {
    }

    public record Period(Instant startAt, Instant endAt) {
    }

    public record Business(String id, String name, String maskedRegNo) {
    }

    public record Amount(long supply, long vat, long total) {
    }

    /** 요금표 계산 — 자리를 차지하는 줄(없으면 첫 줄)의 단가·단위 + 합계. {@code lines} 는 줄 전부(핫딜 글 + 노출) */
    public record Quote(long unitPrice, int units, String unit, long supply, long vat, long total, List<QuoteLine> lines) {
    }

    public record QuoteLine(String product, String unit, long unitPrice, int units, long supply) {
    }

    public record Approval(String method, Amount amount, String adjustReason, Instant approvedAt, String approvedBy) {
    }

    public record OnlinePayment(Instant paidAt, String pgTxId) {
    }

    public record Contract(String id, String fileName, String mimeType, long size, Instant uploadedAt, String uploadedBy, Instant replacedAt, String replacedBy) {
    }

    public record Deposit(long amount, LocalDate depositedOn, String depositorName, String mismatchReason, String confirmedBy, Instant confirmedAt) {
    }

    /**
     * 환불 — {@code type} FULL_AUTO·PRORATED·SUSPEND_PRORATED·DOWNTIME·STOP_PRORATED, {@code status} REQUESTED·APPROVED·DENIED, {@code method} ONLINE·MANUAL,
     * {@code transferStatus} PENDING·DONE(수동만). 광고주 요청 중단(STOP_PRORATED, V127)이면 제안 내역 {@code proratedAmount}(일할)·{@code feeRate}·{@code feeAmount}(수수료)·
     * {@code basisAt}(기준 시각) — 예상액 = {@code proposedAmount} = 일할 − 수수료. 다른 종류면 네 칸 null
     */
    public record Refund(Instant requestedAt, String reason, long paidAmount, long proposedAmount, int usedDays, int totalDays, String status, String type, String method,
                         Long approvedAmount, String decisionReason, String transferStatus, Instant transferredAt, Instant completedAt, Long proratedAmount,
                         Integer feeRate, Long feeAmount, Instant basisAt) {

        public static Refund of(com.community.bizapplication.repository.master.BizRefundStore.RefundRow r) {
            return r == null ? null : new Refund(r.requestedAt(), r.reason(), r.paidAmount(), r.proposedAmount(), r.usedDays(), r.totalDays(), r.status().name(),
                    r.type().name(), r.method().name(), r.approvedAmount(), r.decisionReason(), r.transferStatus(), r.transferredAt(), r.completedAt(), r.proratedAmount(),
                    r.feeRate(), r.feeAmount(), r.basisAt());
        }
    }

    public record Rejection(String code, String text) {
    }

    /**
     * 내림 — {@code cause} SUSPENDED(사업자 정지)·EMERGENCY(긴급)·ADVERTISER_PAUSE·ADVERTISER_STOP(광고주 요청 — V127). 광고주 요청을 운영자가 대신 처리했으면 {@code channel}(PHONE·EMAIL·OTHER)·
     * {@code memo}, {@code by} = 내린 사람 표시 이름(운영자 이름 · 사업자 닉네임, 정지 훅·알 수 없으면 null)
     */
    public record Takedown(Instant at, String cause, String text, String channel, String memo, String by) {
    }

    /**
     * 광고주 요청 일시 정지·중단(V127 — 공개 상세 {@code pause} 와 같은 칸 + {@code actor}·{@code byOperator}). 정지할 수 있는 종류의 결제 뒤 건만, 아니면 null.
     *
     * @param actor      지금 정지를 건 사람 표시 이름(정지 중이 아니면 null)
     * @param byOperator 지금 정지를 운영자가 대신 걸었나
     */
    public record Pause(int used, int limit, Instant pausedAt, String pausedBy, String actor, boolean byOperator, String channel, int pausedMinutes, Instant stoppedAt,
                        Instant refundFrom, long proratableTotal, int feeRate) {

        public static Pause of(com.community.bizapplication.service.BizPauseService.PauseInfo p) {
            if (p == null) {
                return null;
            }
            String by = p.pausedBy() == null ? null : p.pausedBy() == com.community.bizapplication.entity.BizActorType.OPERATOR ? "OPERATOR" : "ME";
            return new Pause(p.used(), p.limit(), p.pausedAt(), by, p.actorName(), "OPERATOR".equals(by), p.channel() == null ? null : p.channel().name(),
                    p.pausedMinutes(), p.stoppedAt(), p.refundFrom(), p.proratableTotal(), p.feeRate());
        }
    }

    public record History(Instant at, String action, String actor, String note) {
    }

    /**
     * 큐 한 줄 — {@code source} BUSINESS(사업자 신청)·OPERATOR(운영자 등록 — 화면이 「운영자 등록」 Tag), {@code takedownCause} 내림(·중단 뒤 취소) 까닭(V127 — 화면 「정지 중」·「중단」, 내려간 적
     * 없으면 null)
     */
    public record Row(String id, String no, String kind, String adType, String status, Business business, Instant submittedAt, Period period, String preferredMethod,
                      long total, Instant deadlineAt, boolean hasRefundRequest, String source, String takedownCause) {
    }

    public record Detail(String id, String no, String kind, String adType, String status, Business business, Instant submittedAt, Period period, String preferredMethod,
                         long total, Instant deadlineAt, boolean hasRefundRequest, String source, String takedownCause, String applicant, Map<String, Object> content,
                         String memo, Quote quote, int remainingSlots, int slotLimit, Approval approval, OnlinePayment onlinePayment, List<Contract> contracts,
                         Deposit deposit, Refund refund, Rejection rejection, Takedown takedown, String policyAgreedVersion, List<History> history,
                         Long proposedSupply, int weight, Registrant registrant, String selfApproval,
                         com.community.bizapplication.dto.response.BizDealPostView post, Pause pause) {
    }

    /**
     * 운영자 등록 건의 등록자 — {@code me} 면 승인이 자기 승인이다(04 §2 — 화면이 경고와 메모 칸을 보인다). 사업자 신청 건은 null.
     */
    public record Registrant(String id, String name, boolean me) {
    }

    public record Page(List<Row> items, Map<String, Long> counts) {
    }

    public record Availability(Quote quote, int remainingSlots, int slotLimit) {
    }

    /** 계약서 사본 열람 — 사업자등록증 열람과 같은 모양 */
    public record ContractFile(String url, String mimeType, String fileName) {
    }
}
