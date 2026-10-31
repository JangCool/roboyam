package com.community.bizapplication.dto.request;

/**
 * 사업자 핫딜 정보(user-web {@code BizDeal}) — 응답에도 같은 모양으로 돌려준다.
 *
 * @param shippingFee 0 = 무료, null = 모름
 * @param endsOn      딜 마감 날짜(없으면 null)
 * @param stock       {@code PLENTY}·{@code LIMITED}·{@code UNKNOWN}
 * @param placement   {@code NONE}·{@code HOME_TOP}·{@code CATEGORY_TOP}
 */
public record BizDealInfo(String linkUrl, String shopName, Long listPrice, Long dealPrice, Long shippingFee, String endsOn, String stock, String body, String placement) {
}
