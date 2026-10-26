package com.community.pointshop.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.pointshop.dto.request.PointShopAvatarPriceRequest;
import com.community.pointshop.dto.request.PointShopAvatarSaleRequest;
import com.community.pointshop.dto.request.PointShopItemSaveRequest;
import com.community.pointshop.dto.request.PointShopRefundRequest;
import com.community.pointshop.dto.request.PointShopSettingsSaveRequest;
import com.community.pointshop.dto.response.PointShopAdminItemListView;
import com.community.pointshop.dto.response.PointShopAdminItemView;
import com.community.pointshop.dto.response.PointShopAvatarBulkResultView;
import com.community.pointshop.dto.response.PointShopAvatarSummaryView;
import com.community.pointshop.dto.response.PointShopOperatorRefundView;
import com.community.pointshop.dto.response.PointShopPurchasePageView;
import com.community.pointshop.dto.response.PointShopRefundDecisionView;
import com.community.pointshop.dto.response.PointShopSettingsView;
import com.community.pointshop.service.PointShopAdminService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 포인트 상점 API — admin-web {@code usePointShopAdminApi.ts} 머리말 그대로(docs/features/commerce-stage1-contract.md 「B」·「1-2 화면 목업」).
 * 권한: 조회(품목·아바타 요약·구매 내역·설정) {@code pointshop.read}, 품목 만들기·고치기·아바타 묶음 가격·판매·설정 저장 {@code pointshop.manage},
 * 구매 예외 환불 {@code pointshop.refund}. 회원·돈 정보가 담긴 응답이라 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/point-shop")
public class PointShopAdminController {

    private final PointShopAdminService service;

    public PointShopAdminController(PointShopAdminService service) {
        this.service = service;
    }

    /** 품목 목록 — 순서 → ID. 「품목」 탭은 {@code excludeKind=PROFILE_AVATAR}, 「아바타」 탭은 {@code kind=PROFILE_AVATAR} */
    @RequirePermission(Permission.POINTSHOP_READ)
    @GetMapping("/items")
    public ResponseEntity<ApiResponse<PointShopAdminItemListView>> items(@RequestParam(required = false) String kind,
                                                                         @RequestParam(required = false) String excludeKind) {
        return noStore(service.items(kind, excludeKind));
    }

    @RequirePermission(Permission.POINTSHOP_READ)
    @GetMapping("/items/{id}")
    public ResponseEntity<ApiResponse<PointShopAdminItemView>> item(@PathVariable String id) {
        return noStore(service.item(id));
    }

    /** 품목 만들기(아바타 제외) — 201 */
    @RequirePermission(Permission.POINTSHOP_MANAGE)
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<PointShopAdminItemView>> create(@RequestBody(required = false) PointShopItemSaveRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.create(actor, body)));
    }

    /** 품목 고치기 — {@code code}·{@code kind}(아바타는 {@code avatarId} 도) 그대로 */
    @RequirePermission(Permission.POINTSHOP_MANAGE)
    @PutMapping("/items/{id}")
    public ResponseEntity<ApiResponse<PointShopAdminItemView>> update(@PathVariable String id, @RequestBody(required = false) PointShopItemSaveRequest body,
                                                                      AdminActor actor) {
        return noStore(service.update(actor, id, body));
    }

    @RequirePermission(Permission.POINTSHOP_READ)
    @GetMapping("/avatars/summary")
    public ResponseEntity<ApiResponse<PointShopAvatarSummaryView>> avatarSummary() {
        return noStore(service.avatarSummary());
    }

    /** 아바타 묶음 가격 — 이미 산 구매의 가격은 그대로 */
    @RequirePermission(Permission.POINTSHOP_MANAGE)
    @PutMapping("/avatars/price")
    public ResponseEntity<ApiResponse<PointShopAvatarBulkResultView>> avatarPrice(@RequestBody(required = false) PointShopAvatarPriceRequest body, AdminActor actor) {
        return noStore(service.avatarPrice(actor, body));
    }

    /** 아바타 묶음 판매 켜기·끄기 */
    @RequirePermission(Permission.POINTSHOP_MANAGE)
    @PostMapping("/avatars/sale")
    public ResponseEntity<ApiResponse<PointShopAvatarBulkResultView>> avatarSale(@RequestBody(required = false) PointShopAvatarSaleRequest body, AdminActor actor) {
        return noStore(service.avatarSale(actor, body));
    }

    /** 구매 내역 한 쪽 — 최신이 위. {@code refundRequest=PENDING} 이면 환불 신청 대기만(줄의 {@code refundRequest}). 시각은 순간(UTC ISO), {@code q} 는 닉네임 또는 회원번호, {@code page} 0부터, {@code size} 1~100(기본 20) */
    @RequirePermission(Permission.POINTSHOP_READ)
    @GetMapping("/purchases")
    public ResponseEntity<ApiResponse<PointShopPurchasePageView>> purchases(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                                                            @RequestParam(required = false) String status, @RequestParam(required = false) String kind,
                                                                            @RequestParam(required = false) String refundRequest, @RequestParam(required = false) String q, @RequestParam(required = false) String page,
                                                                            @RequestParam(required = false) String size) {
        return noStore(service.purchases(from, to, status, kind, refundRequest, q, page, size));
    }

    /** 예외 환불(셀프 환불 조건 밖도) — 사유 5~200자, 감사 돈 관련 */
    @RequirePermission(Permission.POINTSHOP_REFUND)
    @PostMapping("/purchases/{id}/refund")
    public ResponseEntity<ApiResponse<PointShopOperatorRefundView>> refund(@PathVariable String id, @RequestBody(required = false) PointShopRefundRequest body,
                                                                           AdminActor actor) {
        return noStore(service.refund(actor, id, body));
    }

    /** 정지 중 환불 신청 수락 — 조건을 다시 묻지 않고 환불(환불 종류 REQUEST), 감사 돈 관련 · 회원 알림. 이미 결정됐으면 409 {@code POINT_SHOP_REFUND_REQUEST_NOT_PENDING} */
    @RequirePermission(Permission.POINTSHOP_REFUND)
    @PostMapping("/refund-requests/{id}/approve")
    public ResponseEntity<ApiResponse<PointShopRefundDecisionView>> approveRefundRequest(@PathVariable String id, AdminActor actor) {
        return noStore(service.approveRefundRequest(actor, id));
    }

    /** 정지 중 환불 신청 거절 — 사유 5~200자, 아이템은 다시 쓸 수 있다, 감사 돈 관련 · 회원 알림(사유) */
    @RequirePermission(Permission.POINTSHOP_REFUND)
    @PostMapping("/refund-requests/{id}/reject")
    public ResponseEntity<ApiResponse<PointShopRefundDecisionView>> rejectRefundRequest(@PathVariable String id, @RequestBody(required = false) PointShopRefundRequest body,
                                                                                        AdminActor actor) {
        return noStore(service.rejectRefundRequest(actor, id, body));
    }

    @RequirePermission(Permission.POINTSHOP_READ)
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<PointShopSettingsView>> settings() {
        return noStore(service.settings());
    }

    @RequirePermission(Permission.POINTSHOP_MANAGE)
    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<PointShopSettingsView>> saveSettings(@RequestBody(required = false) PointShopSettingsSaveRequest body, AdminActor actor) {
        return noStore(service.saveSettings(actor, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
