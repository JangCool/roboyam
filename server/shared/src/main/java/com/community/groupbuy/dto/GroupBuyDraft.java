package com.community.groupbuy.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 공구 등록·수정 값(관리자). 모집 시작 뒤에는 설명·이미지(요약·사양 포함)만 바꿀 수 있고, 나머지 칸이 다르면 409 {@code GROUP_BUY_LOCKED_FIELD}(10-2).
 * 옵션은 축·값 → 조합(MAIN 단품)마다 추가금·한도·판매 여부, 추가 구성상품은 따로(결정 88).
 *
 * @param shipPlannedAt          발송 예정일(마감 후 발주 공구 필수 — 결정 51)
 * @param shipWithinBusinessDays 결제 후 N영업일 안 발송(즉시 발송 공구 — 없으면 공구 설정 기본값)
 * @param axes                   옵션 축(0~3개) — 비면 MAIN 단품 하나(이름 = 상품 이름)
 * @param skus                   조합마다의 설정(축 값 이름을 축 차례대로) — 적지 않은 조합은 추가금 0·한도 없음·판매. 축이 없으면 값 목록이 빈 줄 하나로 그 단품의 한도를 정한다
 * @param addons                 추가 구성상품
 */
public record GroupBuyDraft(String title, String productName, String summary, String spec, String description, Long normalPrice, String normalPriceBasis,
                            String normalPriceSource, long groupPrice, String shippingPolicyCode, int minQty, int maxQty, Integer maxQtyPerUser, Instant startAt,
                            Instant endAt, String extensionMode, String fulfillment, LocalDate shipPlannedAt, Integer shipWithinBusinessDays,
                            String withdrawalRestriction, String returnPolicy, boolean roundLimitShared, List<AxisDraft> axes, List<SkuDraft> skus,
                            List<AddonDraft> addons, List<ImageRef> images) {

    /** 옵션 축 — 이름(예 색상)과 값(예 블랙·화이트), 차례대로 */
    public record AxisDraft(String name, List<String> values) {
    }

    /** MAIN 단품(축 값 조합) 설정 — {@code values} 는 축 차례대로 값 이름, {@code maxQty} null = 단품 한도 없음 */
    public record SkuDraft(List<String> values, long extraPrice, Integer maxQty, boolean onSale) {
    }

    /** 추가 구성상품(ADDON 단품) — 이름·자기 가격·한도(null = 없음)·이미지 키 */
    public record AddonDraft(String name, long price, Integer maxQty, String imageKey) {
    }

    /** 대표 이미지(저장소 키 — 올리기·다시 인코딩은 관리자 이미지 API, 결정 2) */
    public record ImageRef(String imageKey, String thumbKey) {
    }
}
