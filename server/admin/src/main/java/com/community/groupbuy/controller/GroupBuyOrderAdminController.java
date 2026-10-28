package com.community.groupbuy.controller;

import static com.community.groupbuy.controller.GroupBuyAdminController.ok;

import com.community.groupbuy.dto.request.GroupBuyAdminRequest;
import com.community.groupbuy.dto.response.GroupBuyOrderAdminView;
import com.community.groupbuy.service.GroupBuyOrderAdminFacade;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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
 * 관리자 공구 주문(admin-web {@code useGroupBuyOrderApi}) — 조회 {@code commerce.order.read} · 준비중·발송·배송완료·송장 정정·메모 {@code commerce.order.manage} ·
 * 배송지 열람 {@code commerce.address.reveal}(사유 5~200자) · 운영 환불 {@code commerce.refund.approve}(누계가 고액 문턱이면 {@code commerce.refund.large} — 없으면 승인 대기) ·
 * 배송 명단 {@code commerce.export} + {@code commerce.address.reveal}.
 */
@RestController
@RequestMapping("/api/admin/group-buy-orders")
public class GroupBuyOrderAdminController {

    private final GroupBuyOrderAdminFacade facade;

    public GroupBuyOrderAdminController(GroupBuyOrderAdminFacade facade) {
        this.facade = facade;
    }

    @RequirePermission(Permission.COMMERCE_ORDER_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.ListResponse>> list(@RequestParam(required = false) String status,
                                                                                 @RequestParam(required = false) String groupBuyId,
                                                                                 @RequestParam(required = false) String alert, @RequestParam(required = false) String q,
                                                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                                 @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ok(facade.list(status, groupBuyId, alert, q, from, to, page, size));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_READ)
    @GetMapping("/carriers")
    public ResponseEntity<ApiResponse<List<GroupBuyOrderAdminView.CarrierChoice>>> carriers() {
        return ok(facade.carriers());
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PostMapping("/prepare")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.Bulk>> prepare(@RequestBody(required = false) GroupBuyAdminRequest.OrderIds body, AdminActor actor) {
        return ok(facade.prepare(actor, body));
    }

    /** 발송 일괄(≤ 200 줄, 줄마다 결과 — 결정 47) */
    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PostMapping("/ship")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.Bulk>> ship(@RequestBody(required = false) GroupBuyAdminRequest.Ship body, AdminActor actor) {
        return ok(facade.ship(actor, body));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PostMapping("/deliver")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.Bulk>> deliver(@RequestBody(required = false) GroupBuyAdminRequest.OrderIds body, AdminActor actor) {
        return ok(facade.deliver(actor, body));
    }

    @RequirePermission(Permission.COMMERCE_EXPORT)
    @PostMapping("/export")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.ExportResult>> export(@RequestBody(required = false) GroupBuyAdminRequest.Export body, AdminActor actor) {
        return ok(facade.export(actor, body));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.Detail>> detail(@PathVariable String id, AdminActor actor) {
        return ok(facade.detail(actor, id));
    }

    @RequirePermission(Permission.COMMERCE_ADDRESS_REVEAL)
    @PostMapping("/{id}/address-reveal")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.RevealedAddress>> reveal(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.Reason body,
                                                                                     AdminActor actor) {
        return ok(facade.reveal(actor, id, body));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PutMapping("/{id}/memo")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.Detail>> memo(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.Memo body,
                                                                          AdminActor actor) {
        return ok(facade.memo(actor, id, body));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PostMapping("/{id}/tracking")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.Detail>> tracking(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.Tracking body,
                                                                              AdminActor actor) {
        return ok(facade.tracking(actor, id, body));
    }

    @RequirePermission(Permission.COMMERCE_REFUND_APPROVE)
    @PostMapping("/{id}/refund-preview")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.RefundPreview>> refundPreview(@PathVariable String id,
                                                                                          @RequestBody(required = false) GroupBuyAdminRequest.Refund body, AdminActor actor) {
        return ok(facade.refundPreview(actor, id, body));
    }

    @RequirePermission(Permission.COMMERCE_REFUND_APPROVE)
    @PostMapping("/{id}/refunds")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.RefundResult>> refund(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.Refund body,
                                                                                  AdminActor actor) {
        return ok(facade.refund(actor, id, body));
    }
}
