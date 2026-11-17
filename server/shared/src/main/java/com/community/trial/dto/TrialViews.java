package com.community.trial.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * 체험단 API 모양(trial-group-contract 「구현 결정」 8·9·10) — 관리자 · 공개 · 내 체험단 · 광고주(사업자 센터)가 같이 쓰는 응답·요청. ID 는 글자(Snowflake).
 * 금액은 원(정수). 시각은 순간 ISO-8601.
 */
public final class TrialViews {

    private TrialViews() {
    }

    // ── 공통 조각 ──────────────────────────────────────────────────────────

    public record Ref(String id, String name) {
    }

    public record BoardRef(String id, String slug, String name) {
    }

    /**
     * 회원 조각. {@code memberNo} 는 회원 관리의 회원번호(회원 ID 와 같다 — 관리자 화면이 회원 상세로 가는 링크, 칭호 관리 {@code MemberView} 와 같은 규칙)로 관리자 응답에서만
     * 채운다. 공개·내 체험단·광고주·게시판 매니저 응답에서는 null(JSON 에서 빠짐).
     */
    public record Member(String id, String nickname, String avatarUrl, @JsonInclude(JsonInclude.Include.NON_NULL) String memberNo) {

        public Member(String id, String nickname, String avatarUrl) {
            this(id, nickname, avatarUrl, null);
        }

        /** 관리자 응답용 — 회원번호를 채운 사본 */
        public Member withMemberNo() {
            return new Member(id, nickname, avatarUrl, id);
        }
    }

    public record Photo(String id, String kind, String url, Instant createdAt) {
    }

    /** 신청자 줄 숫자 — 사용기 수(사용기 게시판 글) · 평균 추천 · 약속 지킨 비율(판정된 줄 중 지킨 % — 판정 없으면 null) */
    public record ApplicantStats(long reviewPosts, double averageLikes, Integer keptRate, long judged) {
    }

    // ── 관리자 ─────────────────────────────────────────────────────────────

    /**
     * 모집 만들기·고치기 본문({@code POST·PUT /api/admin/trials}). {@code status} 는 고치기에서만 — PUBLISH 모집 열기(DRAFT→RECRUITING) · CLOSE_APPLY 신청 마감(→SELECTING) ·
     * CLOSE 끝내기(RUNNING→CLOSED) · CANCEL 취소(뽑기 전). 빠지면 상태 그대로.
     */
    public record TrialForm(String advertiserId, String title, String productName, Long productPrice, String form, String reviewBoardId, Integer recruitCount,
                            Integer reviewerShare, Integer reviewerGoldHours, Integer reviewerSilverHours, String applyStartAt, String applyEndAt, Integer reviewDays,
                            Integer returnDays, String shipper, String description, String imageKey, String action) {
    }

    public record SelectRequest(List<String> applicationIds) {
    }

    public record StockRequest(Integer quantity, String note, List<String> photoIds) {
    }

    public record ShipmentRequest(String carrier, String trackingNo, List<String> photoIds) {
    }

    public record PhotosRequest(List<String> photoIds) {
    }

    public record DamageRequest(String description, List<String> photoIds) {
    }

    public record DecideRequest(String verdict, String note) {
    }

    public record RecommendRequest(Boolean recommended) {
    }

    public record NoteRequest(String note) {
    }

    public record ReasonRequest(String reason) {
    }

    /** 모집 목록 한 줄 */
    public record TrialRow(String id, String title, String productName, String form, String shipper, String status, Ref advertiser, int recruitCount,
                           int reviewerShare, long applicantCount, Integer selectedCount, Instant applyStartAt, Instant applyEndAt, BoardRef reviewBoard, Long amount,
                           Instant createdAt) {
    }

    /** 입고·발송 탭의 입고 칸(운영자가 대신 보낼 때만) — {@code keepUntil} 보관 기한(입고 + 설정 일수) */
    public record Stock(int quantity, String note, Instant receivedAt, Instant keepUntil, String returnCarrier, String returnTrackingNo, Instant returnShippedAt,
                        List<Photo> photos) {
    }

    /**
     * 뽑힌 한 줄(관리자·광고주). 받는 분·연락처·주소는 볼 수 있을 때만(운영자 = 운영자가 보내는 모집 · 광고주 = 광고주가 보내는 모집 + 제공 동의, 또는 과실·미반납으로 이름·연락처를 넘긴 뒤)
     * 채우고 그 밖·파기 뒤 null({@code addressPurged}).
     */
    public record Participant(String id, String applicationId, Member member, String quota, String recipient, String phone, String postcode, String address1,
                              String address2, String memo, boolean addressPurged, String carrier, String trackingNo, Instant shippedAt, String shippedBy,
                              Instant receivedAt, String receivedBy, Ref reviewPost, Instant reviewDueAt, Instant returnDueAt, Instant returnedAt, Boolean kept,
                              Instant reviewLateAt, Instant unreturnedAt, Instant contactProvidedAt, List<Photo> photos) {
    }

    public record DamageReport(String id, String participantId, Member member, String reporterType, String description, String status, Instant objectionDueAt,
                               String objection, Instant objectedAt, String verdict, String decisionNote, Instant decidedAt, List<Photo> photos, Instant createdAt) {
    }

    /** 단계별 수 */
    public record Counts(long applied, long selected, long shipped, long received, long reviewed, long returned, long kept, long unreturned) {
    }

    /** 모집 상세(관리자) — 금액은 뽑기 뒤 확정({@code amount}), 그 전에는 null */
    public record TrialDetail(String id, String title, String productName, long productPrice, String form, String shipper, String status, Ref advertiser,
                              Ref bizApplication, BoardRef reviewBoard, int recruitCount, int reviewerShare, int reviewerSlots, int reviewerGoldHours,
                              int reviewerSilverHours, Instant applyStartAt, Instant applyEndAt, int reviewDays, Integer returnDays, String description, String imageKey,
                              String imageUrl, String priceUnit, long unitPrice, long shippingFeePerPerson, Integer selectedCount, Long amount, Counts counts, Stock stock,
                              List<Participant> participants, List<DamageReport> damageReports, Instant createdAt, Instant updatedAt) {
    }

    /** 신청자 한 줄(관리자 뽑기 · 광고주 추천) — 리뷰어 단계 · 몫 · 숫자 · 추천 */
    public record Applicant(String id, Member member, String status, String quota, String reviewerTier, boolean recommended, ApplicantStats stats, List<String> consents,
                            Instant createdAt) {
    }

    /** 신청자 목록 — 리뷰어 몫·일반 몫 인원(뽑기 화면이 따로 센다) */
    public record ApplicantPage(int recruitCount, int reviewerSlots, int generalSlots, List<Applicant> items) {
    }

    public record Uploaded(String id, String kind, String key, String url) {
    }

    // ── 공개 ───────────────────────────────────────────────────────────────

    /**
     * 모집 카드(공개 목록) — {@code ad} 늘 true(「광고」 딱지 + 광고주 이름). {@code status} RECRUITING · SELECTING · RUNNING · CLOSED. 금·은 리뷰어 먼저 신청 시작 시각.
     */
    public record TrialCard(String id, String title, String productName, long productPrice, String form, String status, boolean ad, String advertiserName,
                            String imageUrl, int recruitCount, long applicantCount, Instant applyStartAt, Instant applyEndAt, Instant goldOpenAt, Instant silverOpenAt,
                            BoardRef reviewBoard) {
    }

    /** 끝난 모집의 사용기 한 줄 */
    public record Review(String postId, String title, String boardSlug, Member author, Instant createdAt) {
    }

    /** 보는 회원의 신청 상태 — 신청할 수 있나 · 언제부터 · 몫·단계. 비회원이면 상세의 {@code mine} 이 null */
    public record Mine(String applicationId, String applicationStatus, boolean canApply, boolean canCancel, Instant applyOpenAt, String quota, String reviewerTier,
                       String blockedReason) {
    }

    /**
     * 모집 상세(공개) — 신청 동의 항목({@code consents} — 보내는 쪽·형태로 정해진다) · 대여형 청구 한도({@code damageChargeLimit} = 제품값) · 끝난 모집이면 사용기 모아 보기.
     */
    public record TrialPublicDetail(TrialCard card, String description, int reviewDays, Integer returnDays, String shipper, int reviewerShare, int reviewerGoldHours,
                                    int reviewerSilverHours, List<String> consents, Long damageChargeLimit, Mine mine, List<Review> reviews) {
    }

    public record ApplyRequest(String addressId, List<String> consents) {
    }

    public record ObjectionRequest(String text) {
    }

    // ── 내 체험단 ──────────────────────────────────────────────────────────

    /** 내 파손 신고 한 줄 — {@code canObject} 이의 기한 안이고 아직 이의하지 않았다 */
    public record MyDamage(String id, String description, String status, Instant objectionDueAt, boolean canObject, String objection, String verdict,
                           String decisionNote, List<Photo> photos, Instant createdAt) {
    }

    /**
     * 내 뽑힌 줄 — {@code canReceive} 송장이 있고 아직 받지 않았다 · {@code canWrite} 연결 사용기가 없다(쓰러 가기 = {@code writeBoard} 글쓰기 + {@code participantId}).
     */
    public record MyParticipation(String id, String carrier, String trackingNo, Instant shippedAt, Instant receivedAt, String receivedBy, boolean canReceive,
                                  Instant reviewDueAt, String reviewPostId, boolean canWrite, BoardRef writeBoard, Instant returnDueAt, Instant returnedAt, Boolean kept,
                                  Instant unreturnedAt, List<MyDamage> damageReports) {
    }

    public record MyTrial(String applicationId, String applicationStatus, String quota, Instant appliedAt, boolean canCancel, TrialCard trial,
                          MyParticipation participation) {
    }

    /** 글쓰기 「뽑힌 체험단 사용기로 쓰기」 후보 — 그 게시판에 쓸 수 있는 연결 전 줄 */
    public record Writable(String participantId, String trialId, String trialTitle, BoardRef board) {
    }

    // ── 광고주(사업자 센터) ─────────────────────────────────────────────────

    /** 광고주 모집 상세 — 신청자(추천)·뽑힌 줄(동의 범위 안 주소)·파손 신고 */
    public record BusinessTrialDetail(TrialDetail trial, ApplicantPage applicants) {
    }

    // ── 구매 인증 ───────────────────────────────────────────────────────────

    /**
     * 구매 인증 한 줄(확인할 일·스텝 화면) — 사진은 {@code photoUrl}(확인하는 사람만, 열람 기록). {@code reviewerName} 확인한 사람(운영자 = 관리자 이름 · 게시판 매니저 = 닉네임 —
     * 칭호 신청 {@code reviewedBy} 와 같은 규칙), 확인 전이거나 그 계정을 찾을 수 없으면 null(JSON 에서 빠짐).
     */
    public record Proof(String id, String postId, String postTitle, BoardRef board, Member author, String status, String rejectReason, boolean purged,
                        Instant createdAt, Instant reviewedAt, @JsonInclude(JsonInclude.Include.NON_NULL) String reviewerName) {
    }

    public record ProofResult(String id, String status) {
    }
}
