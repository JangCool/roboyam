package com.community.commerce.sales;

import java.time.Instant;

/**
 * 사업자 정보 열두 칸(공구 계약 결정 18 — 평문 저장, 전자상거래법 제10조 ①·제13조 ①). 앞 여덟 칸이 판매 켜기 자동 조건의 필수 칸이다(commerce.md 20절).
 *
 * @param businessNo  사업자등록번호 {@code 000-00-00000}(체크섬은 {@link CommerceSalesService#validBusinessNo})
 * @param updatedBy   마지막으로 저장한 운영자 이름(읽을 때만, 저장 때 무시)
 */
public record BusinessInfo(String companyName, String representative, String businessNo, String mailOrderNo, String mailOrderAuthority, String address, String phone,
                           String email, String businessHours, String privacyOfficer, String hostingProvider, String shippingRefundPolicy, Instant updatedAt,
                           String updatedBy) {

    /** 필수 여덟 칸이 모두 찼고 등록번호가 맞다 */
    public boolean ready() {
        return filled(companyName) && filled(representative) && CommerceSalesService.validBusinessNo(businessNo) && filled(mailOrderNo) && filled(mailOrderAuthority)
                && filled(address) && filled(phone) && filled(email);
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }
}
