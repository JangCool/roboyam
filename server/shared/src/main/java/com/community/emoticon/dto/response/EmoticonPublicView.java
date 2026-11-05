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

    /** {@code GET /api/emoticons/packs/{id}} — 판매 상태 {@code ON_SALE}·{@code SUSPENDED} */
    public record PackDetail(String id, String name, String source, Creator creator, String coverUrl, int count, int price, int acquireCount, boolean acquired,
                             Instant publishedAt, String description, List<String> tags, String saleState, List<Item> emoticons) {
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
     */
    public record MyPack(String id, String name, String source, String creatorNickname, String coverUrl, Instant acquiredAt, int price, String purchaseId,
                         boolean suspended, List<Item> emoticons, boolean mine) {
    }

    /** {@code GET /api/me/emoticons} */
    public record MyPacks(List<MyPack> packs) {
    }
}
