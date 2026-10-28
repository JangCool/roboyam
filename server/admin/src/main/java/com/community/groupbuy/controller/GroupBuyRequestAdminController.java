package com.community.groupbuy.controller;

import static com.community.groupbuy.controller.GroupBuyAdminController.ok;

import com.community.groupbuy.dto.request.GroupBuyAdminRequest;
import com.community.groupbuy.dto.response.GroupBuyOrderAdminView;
import com.community.groupbuy.service.GroupBuyRequestAdminFacade;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 관리자 취소·반품 요청 대기열(admin-web {@code useGroupBuyRequestApi}) — 조회 {@code commerce.order.read} · 회수 송장·회수 확인·거절·교환 재발송·환불로 바꾸기 {@code commerce.order.manage} ·
 * 승인 {@code commerce.refund.approve} · 완료(처리 방법 + 환불) 둘 다(결정 35). 보상액이 고액 문턱에 닿으면 {@code commerce.refund.large}(서비스가 403).
 */
@RestController
@RequestMapping("/api/admin/group-buy-requests")
public class GroupBuyRequestAdminController {

    private final GroupBuyRequestAdminFacade facade;

    public GroupBuyRequestAdminController(GroupBuyRequestAdminFacade facade) {
        this.facade = facade;
    }

    @RequirePermission(Permission.COMMERCE_ORDER_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.RequestList>> list(@RequestParam(required = false) String status, @RequestParam(required = false) String type,
                                                                                @RequestParam(required = false) String level, @RequestParam(required = false) String q,
                                                                                @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ok(facade.list(status, type, level, q, page, size));
    }

    /** 거절 근거 사진(multipart {@code file}) — 거절에 붙이기 전 먼저 올린다 */
    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PostMapping("/photos")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.Photo>> photo(@RequestParam(value = "file", required = false) MultipartFile file, AdminActor actor) {
        return ok(facade.photo(actor, file));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.RequestDetail>> detail(@PathVariable String id, AdminActor actor) {
        return ok(facade.detail(actor, id));
    }

    @RequirePermission(Permission.COMMERCE_REFUND_APPROVE)
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.RequestDetail>> approve(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.Approve body,
                                                                                    AdminActor actor) {
        return ok(facade.approve(actor, id, body));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.RequestDetail>> reject(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.Reject body,
                                                                                   AdminActor actor) {
        return ok(facade.reject(actor, id, body));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PostMapping("/{id}/pickup")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.RequestDetail>> pickup(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.Pickup body,
                                                                                   AdminActor actor) {
        return ok(facade.pickup(actor, id, body));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PostMapping("/{id}/receive")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.RequestDetail>> receive(@PathVariable String id, AdminActor actor) {
        return ok(facade.receive(actor, id));
    }

    @RequirePermission({Permission.COMMERCE_ORDER_MANAGE, Permission.COMMERCE_REFUND_APPROVE})
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.RequestDetail>> complete(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.Complete body,
                                                                                     AdminActor actor) {
        return ok(facade.complete(actor, id, body));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PostMapping("/{id}/exchange-ship")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.RequestDetail>> exchangeShip(@PathVariable String id,
                                                                                         @RequestBody(required = false) GroupBuyAdminRequest.ExchangeShip body,
                                                                                         AdminActor actor) {
        return ok(facade.exchangeShip(actor, id, body));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PostMapping("/{id}/convert-to-refund")
    public ResponseEntity<ApiResponse<GroupBuyOrderAdminView.RequestDetail>> convertToRefund(@PathVariable String id, AdminActor actor) {
        return ok(facade.convertToRefund(actor, id));
    }
}
