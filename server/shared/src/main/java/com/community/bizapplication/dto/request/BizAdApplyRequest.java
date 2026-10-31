package com.community.bizapplication.dto.request;

/**
 * 광고 심사 신청({@code POST /api/business/applications/ads}, user-web {@code AdApplyRequest}) — 검사는 서비스(400 {@code fields.*}).
 *
 * @param adType    {@code WIDE}·{@code STRIP}·{@code SQUARE}·{@code SMALL}(배너) · {@code TEXT_LINE}(한 줄)
 * @param startDate 시작 날짜 {@code YYYY-MM-DD}(한국) · {@code endDate} 끝 날짜(포함)
 * @param imageFileId 배너 이미지({@code POST /api/business/ad-images} 가 준 ID) — 배너만
 * @param text      한 줄 문구(40자) — 한 줄만
 */
public record BizAdApplyRequest(String adType, String startDate, String endDate, String imageFileId, String linkUrl, String altText, String text, String paymentMethod,
                                String agreedAdPolicyVersion, String agreedRefundPolicyVersion) {
}
