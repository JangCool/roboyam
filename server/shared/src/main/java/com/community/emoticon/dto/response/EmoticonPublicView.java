package com.community.emoticon.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 로보티콘 공개 API 의 응답 모양 — user-web {@code useEmoticonApi.types.ts}·{@code useMyEmoticonsApi.types.ts} 와 같은 이름·칸(계약 「화면 확정」 — API 모양의 정본은 목업 타입).
 * 한 API 묶음의 응답이 서로 얽혀 있어 한 파일에 둔다(관리자 {@code AdAdvertiserAdminView} 와 같은 방식). ID 는 문자열이다.
 */
public final class EmoticonPublicView {

    private EmoticonPublicView() {
    }

    /** 로보티콘 한 개 — {@code animated} = 여러 프레임 GIF */
    public record Item(String id, String label, String url, boolean animated) {
    }

    /** 카탈로그 한 줄 */
    public record CatalogItem(String id, String packId, String label, String url, boolean animated) {
    }

    /** {@code GET /api/emoticons/catalog} — 상점에 나왔던 모든 팩(판매 중단 포함)의 상점 판 로보티콘 */
    public record Catalog(List<CatalogItem> items) {
    }

    /** 작가 — 운영 팩은 null */
    public record Creator(String memberId, String nickname) {
    }

    /** 상점 목록의 팩 한 장 */
    public record PackCard(String id, String name, String source, Creator creator, String coverUrl, int count, int price, int acquireCount, boolean acquired,
                           Instant publishedAt) {
    }

    /** {@code GET /api/emoticons/packs} */
    public record PackList(List<PackCard> items, String nextCursor) {
    }

    /**
     * {@code GET /api/emoticons/packs/{id}} — 판매 상태 {@code ON_SALE}·{@code SUSPENDED}. {@code operatorUsable} = 보는 사람이 힘이 살아 있는 운영자 회원 계정이고 판매 중 팩이다
     * (가지지 않아도 쓴다 — {@code acquired} 와 따로, 가진 것으로 치지 않는다. 운영자 회원 계정 연결 2026-11-05). 화면은 구매·받기 단추 대신 「운영자 계정은 모든 팩을 쓸 수 있어요」.
     * {@code suspendKind} = 판매 중단의 종류({@code OPERATOR} · {@code CREATOR_WITHDRAWN} 작가 탈퇴 — 판매 중이면 null, 후속 결정 6). 판매 중단 팩 상세는 가진 사람(과 작가)에게만 보인다.
     */
    public record PackDetail(String id, String name, String source, Creator creator, String coverUrl, int count, int price, int acquireCount, boolean acquired,
                             Instant publishedAt, String description, List<String> tags, String saleState, List<Item> emoticons, boolean operatorUsable,
                             String suspendKind) {
    }

    /** {@code POST /api/emoticons/packs/{id}/acquire} */
    public record AcquireResult(String packId, boolean acquired, int acquireCount) {
    }

    /**
     * {@code GET /api/emoticons/rules} — 팩 규격·보상·유료 설정(제작 가이드·올리기 검사가 같은 값 — 가격 단계·수수료·정산·환불 칸은 관리자 설정의 지금 값, S2). {@code paidSalesEnabled} 는 목업에 없던 칸 — 꺼져 있으면 유료 희망 신청을 받지 않는다.
     */
    public record Rules(int imageSize, int imagePadding, int maxBytes, int recommendedBytes, int maxFrames, int minCount, int maxCount, int coverMaxBytes, int nameMin,
                        int nameMax, int labelMin, int labelMax, int maxTags, int tagMax, int descriptionMax, int displaySize, int displaySizeMobile, int pickerSize,
                        int rewardPerAcquire, int rewardDailyCap, int maxPending, List<Integer> priceTiers, double vatRate, double feeRate, int refundDays,
                        int settlementDay, int settlementMin, double withholdingRate, boolean paidSalesEnabled) {
    }

    /**
     * 가진 팩 한 개 — 고르기 창의 탭 하나. 산 팩이면 {@code price} = 결제 금액 · {@code purchaseId} = 구매 번호(S3), 무료로 받은 팩은 0 · null.
     * {@code mine} = 내가 만든 팩(사지 않아도 가진다 — 후속 결정 1: {@code price} 0 · {@code purchaseId} null · {@code acquiredAt} = 상점에 처음 나온 시각, 목록에서 뺄 수 없다).
     * {@code reward} = 보상 이벤트로 받은 팩(V163 — event-admin-contract, {@code price} 0 · {@code purchaseId} null · {@code acquiredAt} = 받은 시각).
     */
    public record MyPack(String id, String name, String source, String creatorNickname, String coverUrl, Instant acquiredAt, int price, String purchaseId,
                         boolean suspended, List<Item> emoticons, boolean mine, boolean reward) {
    }

    /**
     * {@code GET /api/me/emoticons}. {@code packs} 는 가진 팩만. {@code allPacks} = 힘이 살아 있는 운영자 회원 계정이다(요청마다 판정) — 판매 중 팩 전부를 가지지 않아도 쓴다,
     * 화면은 카탈로그의 판매 중 팩으로 고르기 창을 채운다(운영자 회원 계정 연결 2026-11-05).
     */
    public record MyPacks(List<MyPack> packs, boolean allPacks) {
    }
}
