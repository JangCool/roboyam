package com.community.groupbuy.dto;

import java.util.List;

/**
 * 상품 등록·수정 값(관리자 「상품」 화면 — 결정 89). 설명·이미지(요약·사양 포함)는 언제든 고칠 수 있고 진행 중 공구에도 바로 보인다(정보 정정).
 * 이름·정상가·청약철회 제한·옵션 축/값·단품 구성은 그 상품을 쓰는 공구가 모집 중이면 잠금(409 {@code GROUP_BUY_LOCKED_FIELD}).
 *
 * @param categoryId 카테고리(필수)
 * @param axes       옵션 축(0~3개) — 비면 단품 하나(이름 = 상품 이름)
 * @param skus       조합마다의 설정(축 값 이름을 축 차례대로) — 적지 않은 조합은 기본 공급가 없음·판매
 */
public record ProductDraft(Long categoryId, String name, String summary, String spec, String description, Long normalPrice, String normalPriceBasis,
                           String normalPriceSource, String withdrawalRestriction, List<GroupBuyDraft.AxisDraft> axes, List<SkuDraft> skus,
                           List<GroupBuyDraft.ImageRef> images) {

    /** 상품 단품(축 값 조합) 설정 — 기본 공급가(null = 없음, 0 은 명시)·새 공구의 기본 판매 여부 */
    public record SkuDraft(List<String> values, Long defaultSupplyPrice, boolean onSale) {
    }
}
