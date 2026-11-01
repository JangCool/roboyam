package com.community.bizapplication.dto.request;

/**
 * 사업자 핫딜 정보(user-web {@code BizDeal}) — 응답에도 같은 모양으로 돌려준다.
 *
 * @param shippingFee 0 = 무료, null = 모름
 * @param endsOn      딜 마감 날짜(없으면 null)
 * @param stock       {@code IN_STOCK}·{@code LOW}·{@code UNKNOWN}(2차 — 옛 신청 건의 {@code PLENTY}·{@code LIMITED} 는 응답에서만 그대로 읽힌다)
 * @param placement   {@code NONE}·{@code HOME_TOP}·{@code CATEGORY_TOP}
 * @param boardId     받는 핫딜 게시판(2차 — 필수, 옛 건은 null)
 * @param prefixId    그 게시판의 말머리(게시판에 말머리가 있거나 {@code CATEGORY_TOP} 이면 필수)
 * @param imageFileId 상품 이미지({@code POST /api/business/ad-images} 파일 — 선택)
 * @param imageUrl    (응답만) 올린 상품 이미지 주소 — 요청 값은 무시한다
 */
public record BizDealInfo(String linkUrl, String shopName, Long listPrice, Long dealPrice, Long shippingFee, String endsOn, String stock, String body, String placement,
                          String boardId, String prefixId, String imageFileId, String imageUrl) {
}
