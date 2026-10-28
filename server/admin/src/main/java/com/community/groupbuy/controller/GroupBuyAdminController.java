package com.community.groupbuy.controller;

import com.community.groupbuy.dto.request.GroupBuyAdminRequest;
import com.community.groupbuy.dto.response.GroupBuyAdminView;
import com.community.groupbuy.service.GroupBuyAdminFacade;
import com.community.groupbuy.service.GroupBuyProductAdminFacade;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

/**
 * 관리자 공구·수량·카테고리·옵션 종류(admin-web {@code useGroupBuyAdminApi}·{@code useGroupBuyProductApi} 의 카테고리·옵션 종류) — 계약 결정 35:
 * 조회 {@code commerce.product.read} · 등록·수정·모집 시작·조기 마감·추가 차수 {@code commerce.product.manage} · 모집 뒤 공급가 {@code commerce.product.price} ·
 * 중단 {@code commerce.groupbuy.cancel} · 추가 입고·줄이기 {@code commerce.stock.adjust} · 발주·발송 지연 공지 {@code commerce.order.manage} ·
 * 공급 불가 {@code commerce.order.manage} + {@code commerce.refund.approve}. 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/group-buys")
public class GroupBuyAdminController {

    private final GroupBuyAdminFacade facade;
    private final GroupBuyProductAdminFacade products;

    public GroupBuyAdminController(GroupBuyAdminFacade facade, GroupBuyProductAdminFacade products) {
        this.facade = facade;
        this.products = products;
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<GroupBuyAdminView.ListResponse>> list(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
                                                                            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ok(facade.list(status, q, page, size));
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_READ)
    @GetMapping("/form-options")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.FormOptions>> formOptions() {
        return ok(facade.formOptions());
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_READ)
    @GetMapping("/notice-catalog")
    public ResponseEntity<ApiResponse<JsonNode>> noticeCatalog() {
        return ok(facade.noticeCatalog());
    }

    /** 상품 대표 이미지(multipart {@code file}) — JPEG·PNG·WebP 5MB, 다시 인코딩(EXIF 제거)·1600/400px */
    @RequirePermission(Permission.COMMERCE_PRODUCT_MANAGE)
    @PostMapping("/images")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.ImageUpload>> image(@RequestParam(value = "file", required = false) MultipartFile file) {
        return ok(facade.uploadImage(file));
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_READ)
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<GroupBuyAdminView.Category>>> categories() {
        return ok(products.categories());
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_MANAGE)
    @PutMapping("/categories")
    public ResponseEntity<ApiResponse<List<GroupBuyAdminView.Category>>> saveCategories(@RequestBody(required = false) List<GroupBuyAdminRequest.Category> body,
                                                                                         AdminActor actor) {
        return ok(products.saveCategories(actor, body));
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_READ)
    @GetMapping("/option-types")
    public ResponseEntity<ApiResponse<List<GroupBuyAdminView.OptionType>>> optionTypes() {
        return ok(products.optionTypes());
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_MANAGE)
    @PutMapping("/option-types")
    public ResponseEntity<ApiResponse<List<GroupBuyAdminView.OptionType>>> saveOptionTypes(@RequestBody(required = false) List<GroupBuyAdminRequest.OptionType> body,
                                                                                            AdminActor actor) {
        return ok(products.saveOptionTypes(actor, body));
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.Detail>> detail(@PathVariable String id, AdminActor actor) {
        return ok(facade.detail(actor, id));
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<GroupBuyAdminView.Detail>> create(@RequestBody(required = false) GroupBuyAdminRequest.Save body, AdminActor actor) {
        return ok(facade.create(actor, body));
    }

    /** DRAFT 는 전체, 공개 뒤에는 잠긴 칸이 같아야 한다(409 {@code GROUP_BUY_LOCKED_FIELD}) */
    @RequirePermission(Permission.COMMERCE_PRODUCT_MANAGE)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.Detail>> update(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.Save body,
                                                                       AdminActor actor) {
        return ok(facade.update(actor, id, body));
    }

    /** 모집 뒤 공급가(결정 89) */
    @RequirePermission(Permission.COMMERCE_PRODUCT_PRICE)
    @PatchMapping("/{id}/content")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.Detail>> content(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.Content body,
                                                                        AdminActor actor) {
        return ok(facade.content(actor, id, body));
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_READ)
    @GetMapping("/{id}/revisions")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.Page<GroupBuyAdminView.Revision>>> revisions(@PathVariable String id, @RequestParam(defaultValue = "1") int page,
                                                                                                     @RequestParam(defaultValue = "20") int size) {
        return ok(facade.revisions(id, page, size));
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_MANAGE)
    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.Detail>> start(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.Start body,
                                                                      AdminActor actor) {
        return ok(facade.start(actor, id, body));
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_MANAGE)
    @PostMapping("/{id}/close-early")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.Detail>> closeEarly(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.Reason body,
                                                                           AdminActor actor) {
        return ok(facade.closeEarly(actor, id, body));
    }

    @RequirePermission(Permission.COMMERCE_GROUPBUY_CANCEL)
    @GetMapping("/{id}/cancel-preview")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.CancelPreview>> cancelPreview(@PathVariable String id) {
        return ok(facade.cancelPreview(id));
    }

    @RequirePermission(Permission.COMMERCE_GROUPBUY_CANCEL)
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.Detail>> cancel(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.Reason body,
                                                                       AdminActor actor) {
        return ok(facade.cancel(actor, id, body));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PostMapping("/{id}/ordered")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.Detail>> ordered(@PathVariable String id, AdminActor actor) {
        return ok(facade.ordered(actor, id));
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_MANAGE)
    @PostMapping("/{id}/rounds")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.RoundCreated>> round(@PathVariable String id, AdminActor actor) {
        return ok(facade.round(actor, id));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PostMapping("/{id}/delay-notices")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.Detail>> delayNotice(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.DelayNotice body,
                                                                            AdminActor actor) {
        return ok(facade.delayNotice(actor, id, body));
    }

    @RequirePermission({Permission.COMMERCE_ORDER_MANAGE, Permission.COMMERCE_REFUND_APPROVE})
    @GetMapping("/{id}/supply-failure-preview")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.SupplyFailurePreview>> supplyFailurePreview(@PathVariable String id, @RequestParam(required = false) String skuId) {
        return ok(facade.supplyFailurePreview(id, skuId));
    }

    @RequirePermission({Permission.COMMERCE_ORDER_MANAGE, Permission.COMMERCE_REFUND_APPROVE})
    @PostMapping("/{id}/supply-failures")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.Detail>> supplyFailure(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.SupplyFailure body,
                                                                              AdminActor actor) {
        return ok(facade.supplyFailure(actor, id, body));
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_READ)
    @GetMapping("/{id}/stock")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.Stock>> stock(@PathVariable String id, AdminActor actor) {
        return ok(facade.stock(actor, id));
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_READ)
    @GetMapping("/{id}/stock/movements")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.Page<GroupBuyAdminView.Movement>>> movements(@PathVariable String id, @RequestParam(required = false) String kind,
                                                                                                     @RequestParam(defaultValue = "1") int page,
                                                                                                     @RequestParam(defaultValue = "50") int size) {
        return ok(facade.movements(id, kind, page, size));
    }

    @RequirePermission(Permission.COMMERCE_STOCK_ADJUST)
    @PostMapping("/{id}/stock/adjust")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.Stock>> adjust(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.StockAdjust body,
                                                                      AdminActor actor) {
        return ok(facade.adjust(actor, id, body));
    }

    static <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
