package com.community.groupbuy.controller;

import com.community.commerce.sales.BusinessInfo;
import com.community.commerce.sales.CommerceSalesService;
import com.community.zconfiguration.response.ApiResponse;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 커머스 공개 정보({@code GET /api/commerce/info} — 계약 결정 18·56, 전자상거래법 제10조 ①). 로그인 없이. 판매 스위치가 꺼졌거나 필수 칸이 덜 찼으면 {@code business} 는 null
 * (사이트 푸터·공구 화면 하단이 그리지 않는다 — G41). 짧게 캐시해도 되는 공개 값(1분).
 */
@RestController
public class CommerceInfoController {

    /** user-web {@code CommerceInfo} */
    public record Info(boolean salesOpen, boolean ready, Business business) {
    }

    /** user-web {@code CommerceBusiness} — 빈 선택 칸은 null */
    public record Business(String companyName, String ceoName, String businessNo, String mailOrderNo, String mailOrderAuthority, String address, String phone, String email,
                           String hours, String privacyOfficer, String hostingProvider, String policyText) {
    }

    private final CommerceSalesService sales;

    public CommerceInfoController(CommerceSalesService sales) {
        this.sales = sales;
    }

    @GetMapping("/api/commerce/info")
    public ResponseEntity<ApiResponse<Info>> info() {
        CommerceSalesService.PublicInfo info = sales.publicInfo();
        BusinessInfo b = info.business();
        Business business = b == null ? null : new Business(b.companyName(), b.representative(), b.businessNo(), b.mailOrderNo(), blank(b.mailOrderAuthority()), b.address(),
                b.phone(), b.email(), blank(b.businessHours()), blank(b.privacyOfficer()), blank(b.hostingProvider()), blank(b.shippingRefundPolicy()));
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(1))).body(ApiResponse.success(new Info(info.salesOpen(), info.ready(), business)));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
