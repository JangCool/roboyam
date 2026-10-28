package com.community.groupbuy.dto;

import java.util.List;

/**
 * 상품 등록·수정 값(관리자 「상품」 화면 — 결정 89, 2b-1 사용자 결정 2026-10-28: <b>단품 목록이 정본</b>). 운영자가 파는 단품을 한 줄씩 등록하고, 옵션 이름(속성 0~3개 — 색상·사이즈)을
 * 두면 단품마다 값을 적는다. 서버는 단품의 값으로 옵션 축·값({@code axes}·{@code valueIds})을 만든다(회원 상세·공구 쪽은 그대로).
 * 설명·이미지(요약·사양 포함)는 언제든 고칠 수 있고 진행 중 공구에도 바로 보인다(정보 정정). 이름·정상가·청약철회 제한·옵션 이름·단품 구성은 그 상품을 쓰는 공구가 모집 중이면 잠금.
 *
 * @param categoryId  카테고리(필수)
 * @param optionNames 옵션 이름(0~3개, 차례대로)
 * @param skus        단품(1개 이상) — 고칠 때 기존 단품은 {@code skuId} 로 가리킨다(없으면 새 단품, 목록에 없는 기존 단품은 지운다)
 */
public record ProductDraft(Long categoryId, String name, String summary, String spec, String description, Long normalPrice, String normalPriceBasis,
                           String normalPriceSource, String withdrawalRestriction, List<String> optionNames, List<SkuDraft> skus, List<GroupBuyDraft.ImageRef> images) {

    /** 단품 — 이름(상품 안에서 유일)·옵션 값(옵션 이름 차례, 옵션이 있으면 모두 필수)·기본 공급가(null = 없음, 0 은 명시)·새 공구의 기본 판매 여부 */
    public record SkuDraft(Long skuId, String name, List<String> values, Long defaultSupplyPrice, boolean onSale) {
    }
}
