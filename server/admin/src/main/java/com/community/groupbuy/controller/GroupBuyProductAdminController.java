package com.community.groupbuy.controller;

import static com.community.groupbuy.controller.GroupBuyAdminController.ok;

import com.community.groupbuy.dto.request.GroupBuyAdminRequest;
import com.community.groupbuy.dto.response.GroupBuyAdminView;
import com.community.groupbuy.service.GroupBuyProductAdminFacade;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
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
 * 관리자 공구 상품(admin-web {@code useGroupBuyProductApi}, 결정 89) — 조회 {@code commerce.product.read}, 저장 {@code commerce.product.manage}. 저장 본문의 고시 양식도 함께 저장한다
 * (상품 → 고시 차례). 설명 HTML 은 서버가 정화한다.
 */
@RestController
@RequestMapping("/api/admin/group-buy-products")
public class GroupBuyProductAdminController {

    private final GroupBuyProductAdminFacade facade;

    public GroupBuyProductAdminController(GroupBuyProductAdminFacade facade) {
        this.facade = facade;
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<GroupBuyAdminView.Page<GroupBuyAdminView.ProductRow>>> list(@RequestParam(required = false) String q,
                                                                                                 @RequestParam(required = false) String categoryId,
                                                                                                 @RequestParam(defaultValue = "1") int page,
                                                                                                 @RequestParam(defaultValue = "20") int size) {
        return ok(facade.list(q, categoryId, page, size));
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_READ)
    @GetMapping("/addon-sources")
    public ResponseEntity<ApiResponse<List<GroupBuyAdminView.AddonSource>>> addonSources(@RequestParam(required = false) String except, AdminActor actor) {
        return ok(facade.addonSources(actor, except));
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.ProductDetail>> detail(@PathVariable String id, AdminActor actor) {
        return ok(facade.detail(actor, id));
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<GroupBuyAdminView.ProductDetail>> create(@RequestBody(required = false) GroupBuyAdminRequest.Product body, AdminActor actor) {
        return ok(facade.create(actor, body));
    }

    @RequirePermission(Permission.COMMERCE_PRODUCT_MANAGE)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupBuyAdminView.ProductDetail>> update(@PathVariable String id, @RequestBody(required = false) GroupBuyAdminRequest.Product body,
                                                                              AdminActor actor) {
        return ok(facade.update(actor, id, body));
    }
}
