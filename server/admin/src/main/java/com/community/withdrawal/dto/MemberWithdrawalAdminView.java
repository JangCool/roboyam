package com.community.withdrawal.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 회원 탈퇴 관리 API 응답(admin-web {@code useMemberWithdrawalApi.types.ts} 와 같은 모양, member-withdrawal-contract 「관리자」). 시각은 UTC ISO, ID 는 문자열.
 * 개인정보: 완료된 탈퇴의 닉네임은 없다(익명화) — {@code nickname} 은 진행 중·취소 건에만.
 */
public final class MemberWithdrawalAdminView {

    private MemberWithdrawalAdminView() {
    }

    /**
     * 남은 일 한 건 — 화면 타입 {@code WithdrawalPendingItem}.
     *
     * @param refId 관리자 화면 링크를 만들 대상 ID(모르면 null)
     */
    public record PendingItem(String code, String label, String refId, Instant waitUntil) {
    }

    /**
     * 모임장인 소모임의 처리 — 화면 타입 {@code WithdrawalGroupChoice}.
     *
     * @param choice     {@code TRANSFER} · {@code DISBAND}(서버 저장값 DISSOLVE) · {@code AUTO}
     * @param transferTo {@code TRANSFER} 일 때 넘길 멤버의 닉네임(탈퇴·익명화됐으면 null)
     */
    public record GroupChoice(String groupId, String groupName, String choice, String transferTo) {
    }

    /** 고른 것 — 화면 타입 {@code WithdrawalChoicesView}. 서버는 정규화한 값을 늘 가지고 있어 null 이 아니다 */
    public record Choices(String paidPointChoice, String deliveredChoice, String bizChoice, List<GroupChoice> groupChoices) {
    }

    /** 포기 동의 금액 — 포기한 것이 없으면 모두 0, {@code consentedAt} null. {@code emoticonEarnings} = 정산 정보 없이 남은 로보티콘 수익을 받지 않기로 한 금액(V148) */
    public record Forfeit(long paidPoints, long refundEstimate, long bizAmount, Instant consentedAt, long emoticonEarnings) {
    }

    /**
     * 처리 이력 한 줄 — {@code REQUESTED · FORCED · PROGRESS_MAILED · COMPLETED · CANCELED · OPERATOR_CANCELED}. 신청 줄의 칸에서 조립한다(별도 표 없음).
     *
     * @param operatorName 운영자가 한 일(강제 탈퇴·대리 취소)이면 그 이름 — 시스템·회원이면 null
     * @param note         덧붙일 말(강제 탈퇴·대리 취소의 운영 메모) — 없으면 null
     */
    public record Event(String kind, Instant at, String operatorName, String note) {
    }

    /** 운영자 강제 탈퇴 정보(회원 신청이면 null) */
    public record Force(String reasonKind, String memo, String operatorName) {
    }

    /** 운영자 대리 취소 정보(회원 본인 취소·취소 아님이면 null) */
    public record Cancel(String requestChannel, String memo, String operatorName) {
    }

    /**
     * 목록 한 줄 — 화면 타입 {@code MemberWithdrawalRow}.
     *
     * @param pendingCounts 남은 일 종류별 개수 — 스냅샷({@code pending_snapshot}: 진행 중 건은 배치가 30분마다 다시 센 것, 완료·취소 건은 끝날 때의 마지막 모습)
     */
    public record Row(String id, String memberNo, String nickname, String status, String source, Instant requestedAt, Map<String, Integer> pendingCounts, int pendingCount,
                      Instant expectedCompleteAt, Instant progressNotifiedAt, boolean hasPaymentHistory, Instant completedAt, Instant canceledAt) {
    }

    /**
     * 상세 — 화면 타입 {@code MemberWithdrawalDetail}(목록 한 줄 + 아래). 진행 중 건의 {@code pending}·{@code pendingCounts}·{@code expectedCompleteAt} 는 <b>지금 다시 센 값</b>.
     *
     * @param rejoinUntil 재가입 가능 시각 — 완료 건만(진행 중은 null)
     */
    public record Detail(String id, String memberNo, String nickname, String status, String source, Instant requestedAt, Map<String, Integer> pendingCounts, int pendingCount,
                         Instant expectedCompleteAt, Instant progressNotifiedAt, boolean hasPaymentHistory, Instant completedAt, Instant canceledAt,
                         String priorStatus, String reason, String reasonNote, Choices choices, Forfeit forfeit, List<PendingItem> pending, Instant rejoinUntil, Force force,
                         Cancel cancel, List<Event> events) {
    }

    public record Page(List<Row> items, long total, int page, int size) {
    }

    /** 요약 4칸 — 진행 중 · 오늘 완료 · 이번 달 완료 · 신청 뒤 10일 넘긴 진행 중(서비스 하루 기준) */
    public record Summary(long scheduled, long completedToday, long completedThisMonth, long overdue) {
    }

    /** 사유 분포 한 칸 — {@code reason} null 은 「고르지 않음」 */
    public record ReasonCount(String reason, long count) {
    }

    public record ReasonStats(long total, List<ReasonCount> items) {
    }

    public record OwnedGroup(String groupId, String groupName) {
    }

    /**
     * 강제 탈퇴 미리보기 — 화면 타입 {@code ForceWithdrawPreview}. 선택값 기본(배송·광고 기다리기 · 충전 환불 · 모임 자동)으로 센 남은 일.
     *
     * @param emoticonCreator 로보티콘 작가 요약 — 사이트 탈퇴 미리 보기({@code GET /api/me/withdrawal})와 같은 계산(shared {@code EmoticonCreatorWithdrawal.summary}).
     *                        작가(만든 팩이 있거나 정산 정보를 등록함)가 아니면 null
     */
    public record Preview(String memberNo, String nickname, List<PendingItem> pending, long earnedPoints, long paidPoints, int activeBizCount, List<OwnedGroup> ownedGroups,
                          boolean hasPaymentHistory, Instant rejoinUntil, EmoticonCreator emoticonCreator) {
    }

    /**
     * 강제 탈퇴 미리보기의 로보티콘 작가 요약 — 판매 중 내 팩(탈퇴하면 새 판매가 멈춘다) · 심사 대기(반려로 닫힌다) · 받지 못한 수익(원천징수 전 · 최소 지급액과 상관없이) · 정산 정보 등록 여부.
     * 정산 정보가 없고 수익이 있으면 탈퇴 완료 때 시스템 보류로 남는다(운영자가 수동 지급 기록 또는 포기 처리로 끝낸다).
     */
    public record EmoticonCreator(int onSalePacks, int pendingReviews, long unpaidEarnings, boolean payoutAccountRegistered) {
    }

    /** 강제 탈퇴 결과 — 화면 타입 {@code ForceWithdrawResult} */
    public record ForceResult(String withdrawalId, String status, int pendingCount, Instant expectedCompleteAt, Instant completedAt) {
    }
}
