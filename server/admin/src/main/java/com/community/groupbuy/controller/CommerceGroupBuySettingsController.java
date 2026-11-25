package com.community.groupbuy.controller;

import static com.community.groupbuy.controller.GroupBuyAdminController.ok;

import com.community.groupbuy.dto.request.GroupBuyAdminRequest;
import com.community.groupbuy.dto.response.CommerceGroupBuySettingsView;
import com.community.groupbuy.service.CommerceGroupBuySettingsFacade;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「설정 › 커머스」 공구 구획(admin-web {@code useCommerceSettingsApi}) — 모두 {@code commerce.settings}(판매 켜기는 최고 관리자만 — 서비스가 403), 대시보드 커머스 칸
 * ({@code useCommerceDashboardApi} — 주문·수량·결제 조회 권한 중 하나, 볼 권한이 없는 칸은 null).
 */
@RestController
public class CommerceGroupBuySettingsController {

    private final CommerceGroupBuySettingsFacade facade;

    public CommerceGroupBuySettingsController(CommerceGroupBuySettingsFacade facade) {
        this.facade = facade;
    }

    @RequirePermission(anyOf = {Permission.COMMERCE_ORDER_READ, Permission.COMMERCE_PRODUCT_READ, Permission.COMMERCE_PAYMENT_READ})
    @GetMapping("/api/admin/commerce/dashboard")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.Dashboard>> dashboard(AdminActor actor) {
        return ok(facade.dashboard(actor));
    }

    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @GetMapping("/api/admin/commerce/shipping-policies")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.ShippingPolicies>> shippingPolicies() {
        return ok(facade.shippingPolicies());
    }

    /** 새 줄(예약) — 겹치거나 지난 시각이면 409 {@code SHIPPING_POLICY_CONFLICT} */
    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @PostMapping("/api/admin/commerce/shipping-policies")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.ShippingPolicies>> addShippingPolicy(@RequestBody(required = false) GroupBuyAdminRequest.ShippingPolicy body,
                                                                                                       AdminActor actor) {
        return ok(facade.addShippingPolicy(actor, body));
    }

    /** 아직 시작하지 않은 예약 줄 지우기 — 시작했으면 409 {@code COMMERCE_SHIPPING_POLICY_STARTED} */
    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @DeleteMapping("/api/admin/commerce/shipping-policies/{id}")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.ShippingPolicies>> deleteShippingPolicy(@PathVariable String id, AdminActor actor) {
        return ok(facade.deleteShippingPolicy(actor, id));
    }

    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @GetMapping("/api/admin/commerce/carriers")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.Carriers>> carriers() {
        return ok(facade.carriers());
    }

    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @PutMapping("/api/admin/commerce/carriers")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.Carriers>> saveCarriers(@RequestBody(required = false) GroupBuyAdminRequest.Carriers body, AdminActor actor) {
        return ok(facade.saveCarriers(actor, body));
    }

    /** 배송 조회 설정(결정 90) — API 키 값은 돌려주지 않는다(설정됨 여부만) */
    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @GetMapping("/api/admin/commerce/tracking-settings")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.TrackingSettings>> trackingSettings() {
        return ok(facade.trackingSettings());
    }

    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @PutMapping("/api/admin/commerce/tracking-settings")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.TrackingSettings>> saveTrackingSettings(
            @RequestBody(required = false) GroupBuyAdminRequest.TrackingSettings body, AdminActor actor) {
        return ok(facade.saveTrackingSettings(actor, body));
    }

    /** 스마트택배 API 키 저장(암호문) — 암호화 키가 없으면 503 {@code CRYPTO_KEY_MISSING} */
    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @PutMapping("/api/admin/commerce/tracking-settings/api-key")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.TrackingSettings>> saveTrackingKey(@RequestBody(required = false) GroupBuyAdminRequest.TrackingKey body,
                                                                                                     AdminActor actor) {
        return ok(facade.saveTrackingKey(actor, body));
    }

    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @DeleteMapping("/api/admin/commerce/tracking-settings/api-key")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.TrackingSettings>> deleteTrackingKey(AdminActor actor) {
        return ok(facade.deleteTrackingKey(actor));
    }

    /** 이용권 남은 양 — 스마트택배를 한 번 부른다. 키가 없으면 409 {@code COMMERCE_TRACKING_KEY_MISSING} */
    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @GetMapping("/api/admin/commerce/tracking-settings/usage")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.TrackingUsage>> trackingUsage() {
        return ok(facade.trackingUsage());
    }

    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @GetMapping("/api/admin/commerce/business-info")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.BusinessInfo>> businessInfo() {
        return ok(facade.businessInfo());
    }

    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @PutMapping("/api/admin/commerce/business-info")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.BusinessInfo>> saveBusinessInfo(@RequestBody(required = false) GroupBuyAdminRequest.BusinessInfo body,
                                                                                                  AdminActor actor) {
        return ok(facade.saveBusinessInfo(actor, body));
    }

    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @GetMapping("/api/admin/commerce/sales")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.Sales>> sales() {
        return ok(facade.salesState());
    }

    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @PostMapping("/api/admin/commerce/sales/checklist/{key}")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.Sales>> check(@PathVariable String key, @RequestBody(required = false) GroupBuyAdminRequest.Checklist body,
                                                                                AdminActor actor) {
        return ok(facade.check(actor, key, body));
    }

    /** 켜기(최고 관리자 + 조건 — 409 {@code COMMERCE_SALES_NOT_READY})·끄기(최고 관리자 또는 {@code commerce.settings}), 사유 5~200자 */
    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @PostMapping("/api/admin/commerce/sales")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.Sales>> toggle(@RequestBody(required = false) GroupBuyAdminRequest.SalesToggle body, AdminActor actor) {
        return ok(facade.toggle(actor, body));
    }

    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @GetMapping("/api/admin/commerce/group-buy-settings")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.Settings>> groupBuySettings() {
        return ok(facade.groupBuySettings());
    }

    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @PutMapping("/api/admin/commerce/group-buy-settings")
    public ResponseEntity<ApiResponse<CommerceGroupBuySettingsView.Settings>> saveGroupBuySettings(@RequestBody(required = false) GroupBuyAdminRequest.Settings body,
                                                                                                  AdminActor actor) {
        return ok(facade.saveGroupBuySettings(actor, body));
    }
}
