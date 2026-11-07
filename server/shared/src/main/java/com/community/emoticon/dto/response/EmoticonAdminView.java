package com.community.emoticon.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 로보티콘 운영(관리자) API 의 응답 모양 — admin-web {@code useEmoticonAdminApi.types.ts} 와 같은 이름·칸. 판매·정산 칸(S3·S4)은 0·null 이다.
 */
public final class EmoticonAdminView {

    private EmoticonAdminView() {
    }

    /** 작가(회원) — 탈퇴한 회원이면 화면이 링크를 두지 않는다 */
    public record CreatorRef(String memberNo, String nickname, boolean withdrawn) {
    }

    /** 로보티콘 하나 — {@code id} 는 로보티콘 ID(올린 직후 응답은 그림 ID). 프레임·무한 반복은 GIF 만(PNG 는 null) */
    public record ItemView(String id, String imageUrl, String alt, String format, int width, int height, Integer frames, Boolean loopForever, int bytes,
                           String contentHash) {
    }

    /** 대표 이미지 — {@code id} 는 그림 ID(운영 팩 저장의 {@code coverId}) */
    public record CoverView(String id, String imageUrl, String format, int width, int height, int bytes) {
    }

    /** 작가 신청 동의 한 가지 — {@code ORIGINAL}·{@code LICENSE}·{@code POLICY} */
    public record ConsentRecord(String kind, Instant agreedAt, String textVersion) {
    }

    /** 심사 기준(서버 설정의 지금 값 — 유료 판매 켜짐 · 가격 단계) */
    public record PackRules(int itemMin, int itemMax, boolean paidSalesEnabled, List<Integer> priceTiers) {
    }

    /** 판 하나의 내용 */
    public record VersionView(int version, String saleWish, String priceRequestMemo, String name, String description, List<String> tags, CoverView cover,
                              List<ItemView> items) {
    }

    /**
     * 처리 기록 — {@code cause} = 누가 닫았나({@code OPERATOR} 운영자 · {@code CREATOR_WITHDRAWN} 작가 탈퇴로 시스템이 반려 — 그때 {@code decidedBy} null, 후속 결정 6)
     */
    public record Decision(String status, String reason, Instant decidedAt, String decidedBy, String cause) {
    }

    /**
     * {@code GET /api/admin/emoticons/reviews} 의 한 줄 — {@code cause} = 처리한 까닭({@code OPERATOR} 운영자 · {@code CREATOR_WITHDRAWN} 작가 탈퇴로 시스템이 닫음), 대기 중이면 null
     * (상세 {@link Decision#cause} 와 같은 판정)
     */
    public record ReviewRow(String id, String packId, String packName, String coverUrl, CreatorRef creator, int itemCount, String saleWish, int currentPrice,
                            Instant submittedAt, String kind, int version, String status, String cause) {
    }

    /** 탭 숫자 {@code PENDING}·{@code UPDATE}·{@code REJECTED} */
    public record ReviewList(List<ReviewRow> items, Map<String, Integer> counts) {
    }

    /** 작가의 가장 최근 상품화 계약 동의 — {@code current} 는 그 판이 지금 판인가. 동의한 적 없으면 null */
    public record ContractConsent(int version, Instant agreedAt, boolean current) {
    }

    /** {@code GET /api/admin/emoticons/reviews/{id}} */
    public record ReviewDetail(String id, String packId, String packName, String coverUrl, CreatorRef creator, int itemCount, String saleWish, int currentPrice,
                               Instant submittedAt, String kind, int version, String status, VersionView next, VersionView current, List<ConsentRecord> consents,
                               PackRules rules, Decision decision, int creatorPendingCount, ContractConsent contractConsent, boolean payoutRegistered) {
    }

    /** 판매 중단 기록 */
    public record Suspension(String reason, Instant at, String by) {
    }

    /**
     * {@code GET /api/admin/emoticons/packs} 의 한 줄 — 운영 팩은 {@code rewardTotal} null, 유료로 나온 적 없는 팩은 판매 칸 null.
     * {@code suspendKind} = 판매 중단의 종류({@code OPERATOR} 운영자 · {@code CREATOR_WITHDRAWN} 작가 탈퇴 — 판매 중단이 아니면 null, 후속 결정 6).
     */
    public record PackRow(String id, String name, String coverUrl, String source, CreatorRef creator, int itemCount, int price, int acquireCount, Long rewardTotal,
                          Integer salesCount, Long salesAmount, String status, boolean pendingUpdate, Instant updatedAt, String suspendKind) {
    }

    public record PackList(List<PackRow> items) {
    }

    /** 작가 팩의 유료 판매 준비(무료 → 유료 전환 조건 — 지금 판 계약 동의 · 정산 정보) — 운영 팩은 null */
    public record PaidReady(boolean contractAgreed, boolean payoutRegistered) {
    }

    /** 가격 전환 한 줄 — 새 판 승인으로 바뀐 가격은 {@code reason} null */
    public record PriceEvent(Instant at, String by, int from, int to, String reason) {
    }

    /**
     * {@code GET /api/admin/emoticons/packs/{id}}. {@code suspendKind} 는 목록과 같다. {@code resumable} = 「다시 판매」를 받는가(운영자 판매 중단만 true — 작가 탈퇴로 멈춘 팩은 false,
     * 후속 결정 6). 작가 탈퇴로 멈춘 팩의 {@code suspension.by} 는 null(시스템).
     */
    public record PackDetail(String id, String name, String coverUrl, String source, CreatorRef creator, int itemCount, int price, int acquireCount, Long rewardTotal,
                             Integer salesCount, Long salesAmount, String status, boolean pendingUpdate, Instant updatedAt, String description, List<String> tags,
                             CoverView cover, List<ItemView> items, Suspension suspension, String pendingReviewId, Instant approvedAt, PaidReady paidReady,
                             List<PriceEvent> priceEvents, String suspendKind, boolean resumable) {
    }

    /** 상품화 계약 요약(지금 판 · 시행일 · 그 판에 동의한 작가 수) — 게시본이 없으면 null */
    public record ContractView(int version, String effectiveDate, int agreedCount) {
    }

    /** 설정의 기본값 */
    public record SettingDefaults(int rewardPerAcquire, int rewardDailyCap, int itemMin, int itemMax, int pendingLimit, int feeRatePercent, int settlementDay,
                                  int payoutMinimum, int refundDays, boolean paidSalesEnabled, List<Integer> priceTiers) {
    }

    /**
     * {@code GET /api/admin/emoticons/settings} — 받기 보상 · 하루 상한 · 팩당 개수 · 심사 대기 상한 · 유료 판매 켜기 · 수수료율 · 가격 단계 · 정산일 · 최소 지급액 · 환불 기간(모두 저장 — S2),
     * 상점에 나온 팩이 쓰는 가격 단계(뺄 수 없다) · 상품화 계약 요약(지금 판 · 동의 작가 수).
     * {@code paidSalesAllowed} = 운영 주체가 유료 판매를 받을 수 있는 단계인가(개인사업자 이상 + 통신판매업 신고번호 — false 면 꺼진 스위치를 켤 수 없다, 화면은 잠그고 안내).
     */
    public record SettingsView(int rewardPerAcquire, int rewardDailyCap, int itemMin, int itemMax, int pendingLimit, int feeRatePercent, int settlementDay,
                               int payoutMinimum, int refundDays, boolean paidSalesEnabled, List<Integer> priceTiers, List<Integer> priceTiersInUse,
                               ContractView contract, SettingDefaults defaults, Instant updatedAt, String updatedBy, boolean paidSalesAllowed) {
    }

    /** {@code GET /api/admin/emoticons/creators/{memberNo}} — 작가가 아니면 응답 데이터 null */
    public record CreatorSummary(String memberNo, int packCount, long rewardTotal, long salesAmount) {
    }

    /**
     * {@code POST /api/admin/emoticons/creators/{memberNo}/payout-account/reveal { reason }} — 정산 계좌 원문(S2). 화면 메모리에만 두고 {@code expiresAt} 이 지나면 지운다.
     * {@code bank} 는 은행 코드, {@code type} 은 {@code INDIVIDUAL}·{@code BUSINESS}, 사업자 번호는 사업자만.
     */
    public record PayoutAccountReveal(String type, String bank, String number, String holder, String businessNumber, Instant expiresAt) {
    }
}
