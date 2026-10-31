package com.community.bizapplication.dto.request;

/**
 * 사업자 핫딜 심사 신청({@code POST /api/business/applications/deals}, user-web {@code DealApplyRequest}) — 노출을 고르면 같은 신청 건에 묶는다(세션 정리 결정).
 *
 * @param placementStartDate 노출 시작 날짜(노출을 고르면 필수) · {@code placementEndDate} 끝 날짜(포함)
 */
public record BizDealApplyRequest(String title, BizDealInfo deal, String placementStartDate, String placementEndDate, String paymentMethod,
                                  String agreedAdPolicyVersion, String agreedRefundPolicyVersion) {
}
